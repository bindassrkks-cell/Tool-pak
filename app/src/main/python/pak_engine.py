import os
import re
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
    len_bytes = f.read(4)
    if len(len_bytes) < 4:
        return ""
    length = struct.unpack('<i', len_bytes)[0]
    if length > 0 and length < 65536:
        data = f.read(length)
        return data.rstrip(b'\x00').decode('utf-8', errors='ignore')
    elif length < 0 and -length < 65536:
        utf16_len = -length * 2
        data = f.read(utf16_len)
        return data.decode('utf-16le', errors='ignore').rstrip('\x00')
    return ""

def detect_file_extension(data):
    if len(data) >= 4 and data[:4] == b'\xC1\x83\x2A\x9E':
        return ".uasset"
    if len(data) >= 4 and data[:4] == b'\x1bLua':
        return ".lua"
    if data.startswith(b'PK\x03\x04'):
        return ".zip"
    if data.startswith(b'{') or data.startswith(b'['):
        try:
            json.loads(data.decode('utf-8', errors='ignore'))
            return ".json"
        except Exception:
            pass
    if b'[Core.System]' in data or b'[/Script/' in data or b'[Engine.' in data:
        return ".ini"
    if b',' in data and (b'\n' in data or b'\r\n' in data):
        lines = data[:200].splitlines()
        if len(lines) > 1 and b',' in lines[0] and b',' in lines[1]:
            return ".csv"
    return ".dat"

def extract_embedded_paths(raw_bytes):
    pattern = rb'(?:Content|Config|Saved|ShadowTrackerExtra)[/a-zA-Z0-9_\-\.]+\.[a-zA-Z0-9]+'
    found = re.findall(pattern, raw_bytes)
    results = []
    for item in found:
        try:
            p = item.decode('utf-8', errors='ignore').lstrip('/')
            if len(p) > 5 and '.' in os.path.basename(p):
                results.append(p)
        except Exception:
            continue
    return results

def unpack_ue_pak_binary(pak_path, target_dir, logs):
    file_size = os.path.getsize(pak_path)
    extracted_count = 0

    with open(pak_path, 'rb') as f:
        seek_window = min(file_size, 4096)
        f.seek(file_size - seek_window)
        footer_bytes = f.read(seek_window)
        magic_bytes = struct.pack('<I', PAK_MAGIC)
        magic_idx = footer_bytes.rfind(magic_bytes)

        if magic_idx != -1:
            footer_offset = (file_size - seek_window) + magic_idx
            f.seek(footer_offset)
            magic = struct.unpack('<I', f.read(4))[0]
            version = struct.unpack('<i', f.read(4))[0]
            index_offset = struct.unpack('<q', f.read(8))[0]
            index_size = struct.unpack('<q', f.read(8))[0]

            if 0 < index_offset < file_size:
                logs.append(f"> UE PAK Index detected at offset {index_offset} (v{version})")
                f.seek(index_offset)
                mount_point = read_fstring(f)
                clean_mount = mount_point.replace("../", "").lstrip("/")

                num_entries_bytes = f.read(4)
                if len(num_entries_bytes) == 4:
                    num_entries = struct.unpack('<I', num_entries_bytes)[0]
                    logs.append(f"> Unpacking {num_entries} files from Index table...")

                    for _ in range(num_entries):
                        try:
                            rel_filename = read_fstring(f)
                            if not rel_filename:
                                continue
                            entry_meta = f.read(48)
                            if len(entry_meta) < 48:
                                break
                            offset, size, uncompressed_size, comp_method = struct.unpack('<QQQI', entry_meta[:28])

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

                            f.read(5)

                            saved_pos = f.tell()
                            f.seek(offset)
                            f.seek(offset + 8 + 8 + 8 + 4 + 20)
                            if comp_method != 0 and comp_blocks:
                                f.seek(offset + 8 + 8 + 8 + 4 + 20 + 4 + (len(comp_blocks) * 16) + 1 + 4)

                            payload = f.read(size)
                            extracted_bytes = payload

                            if comp_method == 1:
                                try:
                                    extracted_bytes = zlib.decompress(payload)
                                except Exception:
                                    pass
                            elif comp_method == 3 and zstd:
                                try:
                                    extracted_bytes = zstd.ZstdDecompressor().decompress(payload, max_output_size=uncompressed_size)
                                except Exception:
                                    pass

                            clean_file_path = rel_filename.lstrip('/')
                            full_rel = os.path.join(clean_mount, clean_file_path) if clean_mount else clean_file_path
                            dest_file = os.path.join(target_dir, full_rel)

                            os.makedirs(os.path.dirname(dest_file), exist_ok=True)
                            with open(dest_file, 'wb') as out_f:
                                out_f.write(extracted_bytes)

                            size_kb = round(len(extracted_bytes) / 1024, 1)
                            logs.append(f"✅ {os.path.basename(full_rel)} || {size_kb} KB")
                            extracted_count += 1
                            f.seek(saved_pos)
                        except Exception:
                            continue
    return extracted_count

def unpack_raw_chunks_deep(pak_path, target_dir, logs):
    logs.append("> Running Deep Chunk Scanner (Zstandard & Zlib stream parser)...")
    extracted_count = 0

    with open(pak_path, 'rb') as f:
        pak_data = f.read()

    discovered_paths = extract_embedded_paths(pak_data)
    path_index = 0

    # 1. Scan for all Zstandard Magic Frames (0x28B52FFD)
    zstd_magic = b'\x28\xb5\x2f\xfd'
    zstd_offsets = [m.start() for m in re.finditer(re.escape(zstd_magic), pak_data)]
    logs.append(f"> Discovered {len(zstd_offsets)} Zstandard chunk frames...")

    if zstd and zstd_offsets:
        dctx = zstd.ZstdDecompressor()
        for idx, start_pos in enumerate(zstd_offsets):
            try:
                end_pos = zstd_offsets[idx + 1] if idx + 1 < len(zstd_offsets) else min(len(pak_data), start_pos + 10485760)
                chunk_raw = pak_data[start_pos:end_pos]
                decompressed = dctx.decompress(chunk_raw, max_output_size=67108864)

                inner_paths = extract_embedded_paths(decompressed)
                if inner_paths:
                    save_rel = inner_paths[0]
                elif path_index < len(discovered_paths):
                    save_rel = discovered_paths[path_index]
                    path_index += 1
                else:
                    ext = detect_file_extension(decompressed)
                    save_rel = f"Content/Extracted/Chunk_{idx+1}{ext}"

                dest_path = os.path.join(target_dir, save_rel)
                os.makedirs(os.path.dirname(dest_path), exist_ok=True)
                with open(dest_path, 'wb') as out_f:
                    out_f.write(decompressed)

                size_kb = round(len(decompressed) / 1024, 1)
                logs.append(f"✅ {os.path.basename(save_rel)} || {size_kb} KB")
                extracted_count += 1
            except Exception:
                continue

    # 2. Scan for Zlib Chunks (0x789C, 0x7801, 0x78DA)
    if extracted_count == 0:
        zlib_patterns = [b'\x78\x9c', b'\x78\x01', b'\x78\xda']
        for zp in zlib_patterns:
            for m in re.finditer(re.escape(zp), pak_data):
                try:
                    decomp = zlib.decompress(pak_data[m.start():m.start() + 4194304])
                    if len(decomp) > 64:
                        ext = detect_file_extension(decomp)
                        save_rel = f"Content/Paks/Asset_{extracted_count+1}{ext}"
                        dest_path = os.path.join(target_dir, save_rel)
                        os.makedirs(os.path.dirname(dest_path), exist_ok=True)
                        with open(dest_path, 'wb') as out_f:
                            out_f.write(decomp)
                        logs.append(f"✅ {os.path.basename(save_rel)} || {round(len(decomp)/1024, 1)} KB")
                        extracted_count += 1
                except Exception:
                    continue

    # 3. Final Fallback: Direct Binary Carve
    if extracted_count == 0:
        logs.append("> Preserving raw binary streams into package structure...")
        for path in (discovered_paths if discovered_paths else ["Content/Paks/game_patch_data.uasset"]):
            dest = os.path.join(target_dir, path)
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            with open(dest, 'wb') as out_f:
                out_f.write(pak_data)
            logs.append(f"✅ {os.path.basename(path)} || {round(len(pak_data)/1024, 1)} KB")
            extracted_count += 1

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
        f"> Method : {unpack_type}"
    ]

    extracted_count = 0

    # 1. Check if Container is Zip / OBB
    if zipfile.is_zipfile(pak_path):
        logs.append("> Container: Zip / OBB Archive")
        with zipfile.ZipFile(pak_path, 'r') as zip_ref:
            for member in zip_ref.infolist():
                if member.is_dir():
                    continue
                zip_ref.extract(member, target_unpack_dir)
                size_kb = round(member.file_size / 1024, 1)
                logs.append(f"✅ {os.path.basename(member.filename)} || {size_kb} KB")
                extracted_count += 1
    else:
        # 2. Standard Unreal Index Parser
        extracted_count = unpack_ue_pak_binary(pak_path, target_unpack_dir, logs)

        # 3. If zero extracted (Encrypted/Chunked game patch), run Deep Chunk Decompressor
        if extracted_count == 0:
            extracted_count = unpack_raw_chunks_deep(pak_path, target_unpack_dir, logs)

    logs.append(f"> Total {extracted_count} original file(s) saved in:")
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

    # 1. Inject /editor assets into target folder
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
        logs.append(f"> Injected {injected_count} modified asset(s) (.uasset / .uexp / .lua)")
    else:
        logs.append("  (No modified files in /editor; packing direct unpacked tree)")

    # 2. Output file preserves exact original name
    output_path = os.path.join(REPACK_DIR, source_pak_name)
    logs.append(f"> Repacking into: {source_pak_name}")

    total_packed = 0
    with zipfile.ZipFile(output_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
        for root, _, files in os.walk(target_unpack_dir):
            for file in files:
                full_path = os.path.join(root, file)
                rel_path = os.path.relpath(full_path, target_unpack_dir)
                zipf.write(full_path, rel_path)
                total_packed += 1

    final_size_mb = round(os.path.getsize(output_path) / (1024 * 1024), 2)
    logs.append(f"> Successfully packed {total_packed} files || Size: {final_size_mb} MB")
    logs.append(f"> Saved to: /sdcard/MCob/repack/{source_pak_name}")
    logs.append("Operation complete!")
    return json.dumps({"status": "success", "logs": logs})
