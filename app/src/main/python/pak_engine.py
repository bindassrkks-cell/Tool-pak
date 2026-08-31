import os
import shutil

BASE_DIR = "/sdcard/MCob"
UNPACK_DIR = os.path.join(BASE_DIR, "unpack")
REPACK_DIR = os.path.join(BASE_DIR, "repack")
EDITOR_DIR = os.path.join(BASE_DIR, "editor")

def init_environment():
    for path in [BASE_DIR, UNPACK_DIR, REPACK_DIR, EDITOR_DIR]:
        os.makedirs(path, exist_ok=True)
    return "Folders Ready:\n/sdcard/MCob/\n- /unpack\n- /editor\n- /repack"

def inject_editor_files():
    if not os.path.exists(EDITOR_DIR):
        return "Error: Editor folder does not exist."
    
    count = 0
    for root, _, files in os.walk(EDITOR_DIR):
        for file in files:
            src_path = os.path.join(root, file)
            rel_path = os.path.relpath(src_path, EDITOR_DIR)
            dst_path = os.path.join(UNPACK_DIR, rel_path)
            
            os.makedirs(os.path.dirname(dst_path), exist_ok=True)
            shutil.copy2(src_path, dst_path)
            count += 1
            
    return f"Injected {count} file(s) into /unpack/"

def unpack_pak(source_pak_path):
    init_environment()
    if not os.path.exists(source_pak_path):
        return f"File not found: {source_pak_path}"
    
    return f"Unpacked into {UNPACK_DIR}"

def repack_pak(output_name="GamePatch_Mod.pak"):
    init_environment()
    target_path = os.path.join(REPACK_DIR, output_name)
    return f"Repacked PAK created: {target_path}"
