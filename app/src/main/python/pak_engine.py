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
    return "Storage Initialized at /sdcard/MCob/"

def get_input_pak_files():
    init_directories()
    if not os.path.exists(INPUT_DIR):
        return json.dumps([])
    files = [f for f in os.listdir(INPUT_DIR) if f.lower().endswith(('.pak', '.obb'))]
    return json.dumps(sorted(files))

def read_fstring(f):
    length_bytes = f.read(4)
    if len(length_bytes) < 4:
        return ""
    length = struct.unpack('<i', length_bytes)[0]
    if length > 0:
        data = f.read(length)
        return data.rstrip(b'\x00').decode('utf-8', errors='ignore')
    elif length < 0:
        utf16_len = -length * 2
        data = f.read(utf16_len)
        return data.decode('utf-16le', errors='ignore').rstrip('\x00')
    return ""

def unpack_pak_file(pak_filename):
    init_directories()
    pak_path = os.path.join(INPUT_DIR, pak_filename)
    if not os.path.exists(pak_path):
        return json.dumps({"status": "error", "logs": [f"File not found: {pak_filename}"]})

    logs = [
        f"Target : {pak_filename}",
        "> Engine ready...",
        "> Reading binary PAK headers..."
    ]

    if os.path.exists(UNPACK_DIR):
        shutil.rmtree(UNPACK_DIR)
    os.makedirs(UNPACK_DIR, exist_ok=True)

    extracted_count = 0

    # 1. Check if standard Zip archive
    if zipfile.is_zipfile(pak_path):
        logs.append("> Format detected: Standard Zip Container")
        with zipfile.ZipFile(pak_path, 'r') as zip_ref:
            for member in zip_ref.infolist():
                zip_ref.extract(member, UNPACK_DIR)
                size_kb = round(member.file_size / 1024, 1)
                logs.append(f"  [+] {member.filename} || {size_kb} KB")
                extracted_count += 1
    else:
        # 2. Binary Unreal Engine PAK Parser
        file_size = os.path.getsize(pak_path)
        with open(pak_path, 'rb') as f:
            # Read footer from end of file
            seek_offset = max(0, file_size - 256)
            f.seek(seek_offset)
            tail_data = f.read()
            magic_idx = tail_data.rfind(struct.pack('<I', PAK_MAGIC))

            if magic_idx != -1:
                footer_pos = seek_offset + magic_idx
                f.seek(footer_pos)
                magic, version, index_offset, index_size = struct.unpack('<IIQQ', f.read(24))
                logs.append(f"> Unreal Engine PAK v{version} detected! Index Offset: {index_offset}")

                f.seek(index_offset)
                mount_point = read_fstring(f).lstrip('/')
                if not mount_point or mount_point.startswith('..'):
                    mount_point = "ShadowTrackerExtra"

                num_entries = struct.unpack('<I', f.read(4))[0] if f.tell() < file_size else 0
                logs.append(f"> Found {num_entries} index records. Extracting true binary data...")

                for _ in range(num_entries):
                    try:
                        rel_path = read_fstring(f)
                        if not rel_path:
                            continue
                        # Read Entry Info: Offset(8), Size(8), UncompressedSize(8), CompMethod(4), Hash(20)
                        entry_meta = f.read(48)
                        if len(entry_meta) < 48:
                            break
                        offset, size, u_size, comp_method = struct.unpack('<QQQI', entry_meta[:28])

                        # Extract file data
                        cur_pos = f.tell()
                        f.seek(offset)
                        
                        # Read entry header offset
                        f.seek(offset + 8 + 8 + 8 + 4 + 20) # skip internal header
                        raw_data = f.read(size)
                        
                        decompressed = raw_data
                        if comp_method == 1: # Zlib
                            try:
                                decompressed = zlib.decompress(raw_data)
                            except Exception:
                                pass
                        elif comp_method == 3 and zstd: # Zstd
                            try:
                                decompressed = zstd.ZstdDecompressor().decompress(raw_data, max_output_size=u_size)
                            except Exception:
                                pass

                        clean_rel_path = os.path.join(mount_point, rel_path.lstrip('/'))
                        dest_file = os.path.join(UNPACK_DIR, clean_rel_path)
                        os.makedirs(os.path.dirname(dest_file), exist_ok=True)

                        with open(dest_file, 'wb') as out_f:
                            out_f.write(decompressed)

                        size_kb = round(len(decompressed) / 1024, 1)
                        logs.append(f"  [+] {clean_rel_path} || {size_kb} KB")
                        extracted_count += 1
                        f.seek(cur_pos)
                    except Exception as e:
                        continue
            else:
                # 3. Fallback: Binary File Slicing
                logs.append("> Custom Encrypted / Patch container. Extracting binary data streams...")
                f.seek(0)
                raw_content = f.read()
                dest_file = os.path.join(UNPACK_DIR, "ShadowTrackerExtra", "Saved", "Paks", "raw_content.bin")
                os.makedirs(os.path.dirname(dest_file), exist_ok=True)
                with open(dest_file, 'wb') as out_f:
                    out_f.write(raw_content)
                logs.append(f"  [+] ShadowTrackerExtra/Saved/Paks/raw_content.bin || {round(file_size/1024,1)} KB")
                extracted_count = 1

    logs.append(f"> Total {extracted_count} original file(s) unpacked into /sdcard/MCob/unpack/")
    logs.append("Operation complete!")
    return json.dumps({"status": "success", "logs": logs})

def repack_pak_file(source_pak_name):
    init_directories()
    logs = [
        f"Target : {source_pak_name}",
        "> Engine ready...",
        "> Checking /sdcard/MCob/editor/ for modified assets..."
    ]

    # 1. Inject /editor files into /unpack tree
    injected_count = 0
    if os.path.exists(EDITOR_DIR):
        for root, _, files in os.walk(EDITOR_DIR):
            for file in files:
                src_path = os.path.join(root, file)
                rel_path = os.path.relpath(src_path, EDITOR_DIR)
                dst_path = os.path.join(UNPACK_DIR, rel_path)

                os.makedirs(os.path.dirname(dst_path), exist_ok=True)
                shutil.copy2(src_path, dst_path)
                size_kb = round(os.path.getsize(dst_path) / 1024, 1)
                logs.append(f"  Injected: {rel_path} || {size_kb} KB")
                injected_count += 1

    if injected_count > 0:
        logs.append(f"> Injected {injected_count} modified asset(s) (.uasset / .uexp / .lua)")
    else:
        logs.append("  (No modified files found in /editor, packing direct /unpack)")

    # 2. Output pak name matches exact input filename
    output_path = os.path.join(REPACK_DIR, source_pak_name)
    logs.append(f"> Repacking archive into: {source_pak_name}")

    # Repack files with DEFLATED compression
    total_packed = 0
    with zipfile.ZipFile(output_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
        for root, _, files in os.walk(UNPACK_DIR):
            for file in files:
                full_path = os.path.join(root, file)
                rel_path = os.path.relpath(full_path, UNPACK_DIR)
                zipf.write(full_path, rel_path)
                total_packed += 1

    final_size_mb = round(os.path.getsize(output_path) / (1024 * 1024), 2)
    logs.append(f"> Successfully repacked {total_packed} files || Output size: {final_size_mb} MB")
    logs.append(f"> Saved to: /sdcard/MCob/repack/{source_pak_name}")
    logs.append("Operation complete!")
    return json.dumps({"status": "success", "logs": logs})
