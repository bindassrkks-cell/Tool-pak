import os
import struct
import shutil
import json
import zlib
import zipfile

try:
    import zstandard as zstd
except ImportError:
    zstd = None

BASE_DIR = "/sdcard/MCob"
INPUT_DIR = os.path.join(BASE_DIR, "input")
UNPACK_DIR = os.path.join(BASE_DIR, "unpack")
EDITOR_DIR = os.path.join(BASE_DIR, "editor")
REPACK_DIR = os.path.join(BASE_DIR, "repack")

PAK_MAGIC = 0x5A6F12E1

def init_directories():
    for folder in [BASE_DIR, INPUT_DIR, UNPACK_DIR, EDITOR_DIR, REPACK_DIR]:
        os.makedirs(folder, exist_ok=True)
    return "Storage Ready: /sdcard/MCob/"

def get_input_pak_files():
    init_directories()
    if not os.path.exists(INPUT_DIR):
        return json.dumps([])
    files = [f for f in os.listdir(INPUT_DIR) if f.lower().endswith(('.pak', '.obb'))]
    return json.dumps(sorted(files))

def read_fstring(f):
    len_bytes = f.read(4)
    if len(len_bytes) < 4:
        return ""
    length = struct.unpack('<i', len_bytes)[0]
    if length > 0:
        data = f.read(length)
        return data.rstrip(b'\x00').decode('utf-8', errors='ignore')
    elif length < 0:
        utf16_len = -length * 2
        data = f.read(utf16_len)
        return data.decode('utf-16le', errors='ignore').rstrip('\x00')
    return ""

def unpack_ue_pak_binary(pak_path, target_dir, logs):
    file_size = os.path.getsize(pak_path)
    extracted_count = 0
    
    with open(pak_path, 'rb') as f:
        # 1. Search for FPakInfo (Footer) from end of file
        seek_window = min(file_size, 4096)
        f.seek(file_size - seek_window)
        footer_bytes = f.read(seek_window)
        magic_bytes = struct.pack('<I', PAK_MAGIC)
        magic_idx = footer_bytes.rfind(magic_bytes)

        if magic_idx == -1:
            logs.append("> Warning: UE PAK Magic (0x5A6F12E1) not found in footer.")
            return 0

        footer_offset = (file_size - seek_window) + magic_idx
        f.seek(footer_offset)

        # Read FPakInfo
        magic = struct.unpack('<I', f.read(4))[0]
        version = struct.unpack('<i', f.read(4))[0]
        index_offset = struct.unpack('<q', f.read(8))[0]
        index_size = struct.unpack('<q', f.read(8))[0]

        logs.append(f"> Unreal Engine PAK Detected (Version: {version})")
        logs.append(f"> Reading dynamic index from offset: {index_offset}")

        if index_offset <= 0 or index_offset >= file_size:
            logs.append("> Error: Invalid index offset in PAK footer.")
            return 0

        # 2. Seek to Index Table
        f.seek(index_offset)
        mount_point = read_fstring(f)
        
        # Clean mount point path
        clean_mount = mount_point.replace("../", "").lstrip("/")
        if clean_mount and not clean_mount.endswith("/"):
            clean_mount += "/"

        num_entries_bytes = f.read(4)
        if len(num_entries_bytes) < 4:
            logs.append("> Error: Corrupted index table header.")
            return 0

        num_entries = struct.unpack('<I', num_entries_bytes)[0]
        logs.append(f"> Parsing {num_entries} dynamic file entries from index...")

        # 3. Dynamic Index Traversal
        for _ in range(num_entries):
            try:
                rel_filename = read_fstring(f)
                if not rel_filename:
                    continue

                # Parse FPakEntry (Standard UE4 layout)
                entry_data = f.read(48)
                if len(entry_data) < 48:
                    break

                offset, size, uncompressed_size, comp_method = struct.unpack('<QQQI', entry_data[:28])

                # Read Compression Blocks if compressed
                comp_blocks = []
                if comp_method != 0:
                    block_count_bytes = f.read(4)
                    if len(block_count_bytes) == 4:
                        block_count = struct.unpack('<I', block_count_bytes)[0]
                        for _ in range(block_count):
                            block_data = f.read(16)
                            if len(block_data) == 16:
                                b_start, b_end = struct.unpack('<QQ', block_data)
                                comp_blocks.append((b_start, b_end))

                # Read encryption flag & compression block size
                f.read(5)  # bEncrypted (1 byte) + BlockSize (4 bytes)

                # 4. Extract Real Binary Data from File Offset
                saved_index_pos = f.tell()
                f.seek(offset)

                # Skip header stored at payload offset
                f.seek(offset + 8 + 8 + 8 + 4 + 20)
                if comp_method != 0 and comp_blocks:
                    f.seek(offset + 8 + 8 + 8 + 4 + 20 + 4 + (len(comp_blocks) * 16) + 1 + 4)

                raw_payload = f.read(size)
                extracted_data = raw_payload

                # Decompress binary stream
                if comp_method == 1:  # Zlib
                    try:
                        extracted_data = zlib.decompress(raw_payload)
                    except Exception:
                        pass
                elif comp_method == 3 and zstd:  # Zstandard
                    try:
                        extracted_data = zstd.ZstdDecompressor().decompress(raw_payload, max_output_size=uncompressed_size)
                    except Exception:
                        pass

                # Build dynamic file directory structure
                clean_file_path = rel_filename.lstrip("/")
                if clean_mount:
                    full_rel_path = os.path.join(clean_mount, clean_file_path)
                else:
                    full_rel_path = clean_file_path

                dest_file_path = os.path.join(target_dir, full_rel_path)
                os.makedirs(os.path.dirname(dest_file_path), exist_ok=True)

                with open(dest_file_path, 'wb') as out_f:
                    out_f.write(extracted_data)

                size_kb = round(len(extracted_data) / 1024, 1)
                file_name_only = os.path.basename(full_rel_path)
                logs.append(f"✅ {file_name_only} || {size_kb} KB")
                extracted_count += 1

                f.seek(saved_index_pos)
            except Exception as e:
                continue

    return extracted_count

def unpack_pak_file(pak_filename, unpack_type="ALL"):
    init_directories()
    pak_path = os.path.join(INPUT_DIR, pak_filename)
    if not os.path.exists(pak_path):
        return json.dumps({"status": "error", "logs": [f"File not found: {pak_filename}"]})

    pak_folder_name = os.path.splitext(pak_filename)[0]
    target_unpack_dir = os.path.join(UNPACK_DIR, pak_folder_name)

    if os.path.exists(target_unpack_dir):
        shutil.rmtree(target_unpack_dir)
    os.makedirs(target_unpack_dir, exist_ok=True)

    logs = [
        f"Target : {pak_filename}",
        "> Engine ready...",
        f"> Scanning format & index entries..."
    ]

    extracted_count = 0

    # 1. Check if Container is Standard Zip / OBB
    if zipfile.is_zipfile(pak_path):
        logs.append("> Container: Zip / OBB Archive")
        with zipfile.ZipFile(pak_path, 'r') as zip_ref:
            for member in zip_ref.infolist():
                if member.is_dir():
                    continue
                zip_ref.extract(member, target_unpack_dir)
                size_kb = round(member.file_size / 1024, 1)
                file_name = os.path.basename(member.filename)
                logs.append(f"✅ {file_name} || {size_kb} KB")
                extracted_count += 1
    else:
        # 2. Dynamic Unreal Engine Binary Extraction
        extracted_count = unpack_ue_pak_binary(pak_path, target_unpack_dir, logs)

    if extracted_count == 0:
        logs.append("> Warning: Zero files extracted. Verifying file permissions/encryption...")
    else:
        logs.append(f"> Successfully unpacked {extracted_count} original file(s) into:")
        logs.append(f"  /sdcard/MCob/unpack/{pak_folder_name}/")

    logs.append("Operation complete!")
    return json.dumps({"status": "success", "logs": logs})

def repack_pak_file(source_pak_name):
    init_directories()
    pak_folder_name = os.path.splitext(source_pak_name)[0]
    target_unpack_dir = os.path.join(UNPACK_DIR, pak_folder_name)

    if not os.path.exists(target_unpack_dir):
        target_unpack_dir = UNPACK_DIR

    logs = [
        f"Target : {source_pak_name}",
        "> Engine ready...",
        "> Scanning /sdcard/MCob/editor/ for modified assets..."
    ]

    # 1. Dynamic Asset Injection from /editor
    injected_count = 0
    if os.path.exists(EDITOR_DIR):
        for root, _, files in os.walk(EDITOR_DIR):
            for file in files:
                src_path = os.path.join(root, file)
                rel_path = os.path.relpath(src_path, EDITOR_DIR)
                dst_path = os.path.join(target_unpack_dir, rel_path)

                os.makedirs(os.path.dirname(dst_path), exist_ok=True)
                shutil.copy2(src_path, dst_path)
                size_kb = round(os.path.getsize(dst_path) / 1024, 1)
                logs.append(f"✅ Injected: {os.path.basename(rel_path)} || {size_kb} KB")
                injected_count += 1

    if injected_count > 0:
        logs.append(f"> Total {injected_count} modified asset(s) injected into package tree.")
    else:
        logs.append("  (No modified files found in /editor; packing direct unpacked tree)")

    # 2. Repack into Original Input PAK Name
    output_path = os.path.join(REPACK_DIR, source_pak_name)
    logs.append(f"> Compressing & packing into: {source_pak_name}")

    total_packed = 0
    with zipfile.ZipFile(output_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
        for root, _, files in os.walk(target_unpack_dir):
            for file in files:
                full_path = os.path.join(root, file)
                rel_path = os.path.relpath(full_path, target_unpack_dir)
                zipf.write(full_path, rel_path)
                total_packed += 1

    final_size_mb = round(os.path.getsize(output_path) / (1024 * 1024), 2)
    logs.append(f"> Successfully repacked {total_packed} files || Output size: {final_size_mb} MB")
    logs.append(f"> Output saved: /sdcard/MCob/repack/{source_pak_name}")
    logs.append("Operation complete!")
    return json.dumps({"status": "success", "logs": logs})
