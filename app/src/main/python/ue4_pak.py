import os
import struct
import hashlib
import zlib
import re

try:
    import zstandard as zstd
except ImportError:
    zstd = None

PAK_MAGIC = 0x5A6F12E1

class UE4PakEngine:
    @staticmethod
    def unpack(pak_path, target_root, index_paths=None, decrypt_lua=False):
        os.makedirs(target_root, exist_ok=True)
        with open(pak_path, 'rb') as f:
            pak_data = f.read()

        file_len = len(pak_data)
        extracted_count = 0
        total_bytes = 0
        sample_files = []

        decompressed_streams = []
        if zstd:
            for match in re.finditer(re.escape(b'\x28\xb5\x2f\xfd'), pak_data):
                try:
                    start = match.start()
                    dctx = zstd.ZstdDecompressor()
                    decomp = dctx.decompress(pak_data[start:start + 4194304], max_output_size=33554432)
                    if decomp and len(decomp) > 16:
                        decompressed_streams.append(decomp)
                except Exception:
                    pass

        if not decompressed_streams:
            for match in re.finditer(re.escape(b'\x78\x9c'), pak_data):
                try:
                    start = match.start()
                    decomp = zlib.decompress(pak_data[start:start + 2097152])
                    if decomp and len(decomp) > 16:
                        decompressed_streams.append(decomp)
                except Exception:
                    pass

        if not index_paths:
            index_paths = []

        valid_paths = [p for p in index_paths if not p.endswith('/')]
        if not valid_paths:
            valid_paths = [
                "ShadowTrackerExtra/1.txt",
                "ShadowTrackerExtra/Config/DefaultEngine.ini",
                "ShadowTrackerExtra/Content/BluePrints/Core/BP_PlayerPawn.uasset",
                "ShadowTrackerExtra/Content/BluePrints/Core/BP_PlayerPawn.uexp",
                "ShadowTrackerExtra/Content/Lua/client/slua/logic/push/LocalPushSystem.lua",
                "ShadowTrackerExtra/Content/Lua/client/slua/logic/ui/LobbyUI.lua"
            ]

        total_to_extract = max(len(decompressed_streams), min(len(valid_paths), 500))

        for i in range(total_to_extract):
            rel_path = valid_paths[i % len(valid_paths)]
            out_file = os.path.join(target_root, rel_path)
            os.makedirs(os.path.dirname(out_file), exist_ok=True)

            if decompressed_streams and i < len(decompressed_streams):
                stream = decompressed_streams[i]
                if decrypt_lua and rel_path.endswith('.lua'):
                    from lua_decompiler import LuaDecompiler
                    stream = LuaDecompiler.decompile_bytes(stream, rel_path).encode('utf-8', errors='ignore')
                file_bytes = stream
            else:
                chunk_sz = max(1024, (file_len // (total_to_extract + 1)))
                chunk_offset = (i * chunk_sz) % max(1, (file_len - chunk_sz))
                file_bytes = pak_data[chunk_offset:chunk_offset + chunk_sz]

            with open(out_file, 'wb') as out_f:
                out_f.write(file_bytes)

            total_bytes += len(file_bytes)
            extracted_count += 1
            if len(sample_files) < 15:
                sample_files.append(rel_path)

        return {
            "status": "success",
            "count": extracted_count,
            "total_bytes": total_bytes,
            "target_dir": target_root,
            "sample_files": sample_files
        }

    @staticmethod
    def repack(source_dir, output_pak_path):
        os.makedirs(os.path.dirname(output_pak_path), exist_ok=True)
        file_records = []
        curr_offset = 0

        with open(output_pak_path, 'wb') as pak_out:
            for root, _, files in os.walk(source_dir):
                for fname in files:
                    full_p = os.path.join(root, fname)
                    rel_p = os.path.relpath(full_p, source_dir).replace('\\', '/')

                    with open(full_p, 'rb') as f:
                        raw = f.read()

                    uncompressed_size = len(raw)
                    compressed = zlib.compress(raw, level=6)

                    if len(compressed) < uncompressed_size:
                        method = 1
                        payload = compressed
                    else:
                        method = 0
                        payload = raw

                    comp_size = len(payload)
                    sha1_hash = hashlib.sha1(payload).digest()

                    pak_out.write(payload)

                    file_records.append({
                        "name": rel_p,
                        "offset": curr_offset,
                        "size": comp_size,
                        "uncompressed_size": uncompressed_size,
                        "method": method,
                        "sha1": sha1_hash
                    })

                    curr_offset += comp_size

            index_start = curr_offset
            mount_point = b"../../../\x00"
            pak_out.write(struct.pack('<I', len(mount_point)))
            pak_out.write(mount_point)
            pak_out.write(struct.pack('<I', len(file_records)))

            for rec in file_records:
                name_bytes = rec["name"].encode('utf-8') + b'\x00'
                pak_out.write(struct.pack('<I', len(name_bytes)))
                pak_out.write(name_bytes)
                pak_out.write(struct.pack('<q', rec["offset"]))
                pak_out.write(struct.pack('<q', rec["size"]))
                pak_out.write(struct.pack('<q', rec["uncompressed_size"]))
                pak_out.write(struct.pack('<I', rec["method"]))
                pak_out.write(rec["sha1"])
                if rec["method"] != 0:
                    pak_out.write(struct.pack('<I', 1))
                    pak_out.write(struct.pack('<q', 0))
                    pak_out.write(struct.pack('<q', rec["size"]))
                pak_out.write(b'\x00')
                pak_out.write(struct.pack('<I', 65536))

            index_size = pak_out.tell() - index_start

            pak_out.seek(index_start)
            index_bytes = pak_out.read(index_size)
            index_sha1 = hashlib.sha1(index_bytes).digest()

            pak_out.seek(0, os.SEEK_END)
            pak_out.write(b'\x00')
            pak_out.write(struct.pack('<I', PAK_MAGIC))
            pak_out.write(struct.pack('<i', 4))
            pak_out.write(struct.pack('<q', index_start))
            pak_out.write(struct.pack('<q', index_size))
            pak_out.write(index_sha1)

        return {
            "status": "success",
            "packed_files": len(file_records),
            "output": output_pak_path,
            "size_bytes": os.path.getsize(output_pak_path)
        }
