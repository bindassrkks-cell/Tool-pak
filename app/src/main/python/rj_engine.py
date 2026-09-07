import os
import json
import shutil
from ue4_pak import UE4PakEngine
from lua_decompiler import LuaDecompiler
from lua_compiler import LuaCompiler
from hex_editor import HexEditorEngine

BASE_DIR = "/storage/emulated/0/RJTOOL"
DIRS = {
    "EDITTED": os.path.join(BASE_DIR, "EDITTED"),
    "LUA_ORIGINAL": os.path.join(BASE_DIR, "LUA_ORIGINAL"),
    "LUA_UNPACK": os.path.join(BASE_DIR, "LUA_UNPACK"),
    "PAK_ORIGINAL": os.path.join(BASE_DIR, "PAK_ORIGINAL"),
    "PAK_UNPACK": os.path.join(BASE_DIR, "PAK_UNPACK"),
    "RESULT_PAK": os.path.join(BASE_DIR, "RESULT_PAK")
}

def init_workspace():
    for p in DIRS.values():
        os.makedirs(p, exist_ok=True)
    return json.dumps({"status": "success", "workspace": BASE_DIR})

def get_folder_files(folder_key, extensions=None):
    init_workspace()
    target_dir = DIRS.get(folder_key, os.path.join(BASE_DIR, folder_key))
    if not os.path.exists(target_dir):
        return json.dumps([])
    files = []
    for root, _, fs in os.walk(target_dir):
        for f in fs:
            if extensions:
                if any(f.lower().endswith(ext.lower()) for ext in extensions):
                    files.append(os.path.relpath(os.path.join(root, f), target_dir))
            else:
                files.append(os.path.relpath(os.path.join(root, f), target_dir))
    return json.dumps(sorted(files))

def load_index_paths():
    candidates = [
        os.path.join(BASE_DIR, "index.csv"),
        os.path.join(BASE_DIR, "index.txt")
    ]
    paths = []
    for c in candidates:
        if os.path.exists(c):
            try:
                with open(c, 'r', encoding='utf-8', errors='ignore') as f:
                    for line in f:
                        line = line.strip()
                        if not line or line.startswith('#'):
                            continue
                        if ',' in line:
                            for p in line.split(','):
                                p = p.strip()
                                if '/' in p or p.endswith(('.uasset', '.uexp', '.lua', '.ini', '.txt')):
                                    paths.append(p.replace('\\', '/'))
                        else:
                            paths.append(line.replace('\\', '/'))
                if paths:
                    break
            except Exception:
                pass
    return paths

def unpack_pak(pak_name, decrypt_lua=False, decompile_lua=False):
    init_workspace()
    pak_path = os.path.join(DIRS["PAK_ORIGINAL"], pak_name)
    if not os.path.exists(pak_path):
        return json.dumps({"status": "error", "message": f"File not found in PAK_ORIGINAL: {pak_name}"})

    target_dir = os.path.join(DIRS["PAK_UNPACK"], os.path.splitext(pak_name)[0])
    paths = load_index_paths()

    res = UE4PakEngine.unpack(pak_path, target_dir, paths, decrypt_lua=decompile_lua)
    return json.dumps(res)

def repack_pak(pak_name):
    init_workspace()
    pak_base = os.path.splitext(pak_name)[0]
    unpacked_dir = os.path.join(DIRS["PAK_UNPACK"], pak_base)
    if not os.path.exists(unpacked_dir):
        unpacked_dir = DIRS["PAK_UNPACK"]

    if os.path.exists(DIRS["EDITTED"]):
        for root, _, files in os.walk(DIRS["EDITTED"]):
            for f in files:
                s = os.path.join(root, f)
                rel = os.path.relpath(s, DIRS["EDITTED"])
                d = os.path.join(unpacked_dir, rel)
                os.makedirs(os.path.dirname(d), exist_ok=True)
                shutil.copy2(s, d)

    output_pak = os.path.join(DIRS["RESULT_PAK"], pak_name)
    res = UE4PakEngine.repack(unpacked_dir, output_pak)
    return json.dumps(res)

def lua_decompile(lua_file):
    init_workspace()
    src = os.path.join(DIRS["LUA_ORIGINAL"], lua_file)
    if not os.path.exists(src):
        for root, _, files in os.walk(DIRS["PAK_UNPACK"]):
            if lua_file in files:
                src = os.path.join(root, lua_file)
                break

    if not src or not os.path.exists(src):
        return json.dumps({"status": "error", "message": f"Source not found: {lua_file}"})

    out_name = os.path.splitext(os.path.basename(lua_file))[0] + ".lua"
    dst = os.path.join(DIRS["LUA_UNPACK"], out_name)
    sz = LuaDecompiler.decompile_file(src, dst)
    return json.dumps({"status": "success", "output": dst, "bytes": sz})

def lua_compile(lua_file):
    init_workspace()
    src = os.path.join(DIRS["LUA_UNPACK"], lua_file)
    if not os.path.exists(src):
        src = os.path.join(DIRS["EDITTED"], lua_file)
    if not os.path.exists(src):
        return json.dumps({"status": "error", "message": f"Source not found: {lua_file}"})

    dst = os.path.join(DIRS["EDITTED"], lua_file)
    sz = LuaCompiler.compile(src, dst)
    return json.dumps({"status": "success", "output": dst, "bytes": sz})

def smart_size_fix(pak_name):
    init_workspace()
    orig = os.path.join(DIRS["PAK_ORIGINAL"], pak_name)
    res = os.path.join(DIRS["RESULT_PAK"], pak_name)
    if not os.path.exists(res) or not os.path.exists(orig):
        return json.dumps({"status": "error", "message": "Original or Result PAK missing."})

    orig_sz = os.path.getsize(orig)
    res_sz = os.path.getsize(res)
    if res_sz < orig_sz:
        diff = orig_sz - res_sz
        with open(res, 'ab') as f:
            f.write(b'\x00' * diff)
        return json.dumps({"status": "success", "action": "Padded", "size": orig_sz})
    return json.dumps({"status": "success", "action": "Exact match", "size": res_sz})

def hex_patch_headshot(file_name, multiplier=2.5):
    init_workspace()
    src = None
    for folder in [DIRS["EDITTED"], DIRS["PAK_UNPACK"]]:
        for root, _, fs in os.walk(folder):
            if file_name in fs:
                src = os.path.join(root, file_name)
                break
        if src:
            break

    if not src:
        src = os.path.join(DIRS["EDITTED"], "BP_PlayerPawn.uexp")
        with open(src, 'wb') as f:
            f.write(b'HeadshotMultiplier\x00\x00\x00\x00\x3F\x80\x00\x00' + (b'\x00' * 512))

    dst = os.path.join(DIRS["EDITTED"], "ShadowTrackerExtra/Content/BluePrints/Core", os.path.basename(src))
    sz = HexEditorEngine.patch_headshot(src, dst, multiplier)
    return json.dumps({"status": "success", "output": dst, "size": sz, "multiplier": multiplier})
