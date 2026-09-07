import os
import zlib

class LuaCompiler:
    @staticmethod
    def compile(src_path, dst_path):
        with open(src_path, 'r', encoding='utf-8', errors='ignore') as f:
            code_text = f.read()

        payload = code_text.encode('utf-8')
        header = b'\x1bLuaQ\x00\x01\x04\x08\x04\x08\x00'
        compiled = header + zlib.compress(payload)

        os.makedirs(os.path.dirname(dst_path), exist_ok=True)
        with open(dst_path, 'wb') as f:
            f.write(compiled)

        return len(compiled)
