import os
import struct

class HexEditorEngine:
    @staticmethod
    def patch_headshot(file_path, out_path, multiplier=2.5):
        with open(file_path, 'rb') as f:
            data = f.read()

        float_bytes = struct.pack('<f', float(multiplier))
        marker = b'HeadshotMultiplier'

        if marker in data:
            pos = data.find(marker) + len(marker) + 4
            data = data[:pos] + float_bytes + data[pos+4:]
        else:
            data = data + b'\x00HeadshotMultiplier\x00\x00\x00\x00' + float_bytes

        os.makedirs(os.path.dirname(out_path), exist_ok=True)
        with open(out_path, 'wb') as f:
            f.write(data)

        return len(data)
