import sys

try:
    import ctranslate2
    from ctranslate2.converters import TransformersConverter
except ImportError:
    print("Installing ctranslate2...")
    subprocess.check_call([sys.executable, "-m", "pip", "install", "ctranslate2"])
    import ctranslate2
    from ctranslate2.converters import TransformersConverter

if len(sys.argv) < 5:
    print("Usage: python generate_ct2.py <model_dir> <langIN> <langOUT> <out_dir>")
    sys.exit(1)

model_dir, langIN, langOUT, out_dir = sys.argv[1:]

print(f"Converting {langIN}-{langOUT} model at {model_dir} to CTranslate2 format...")

converter = TransformersConverter(model_dir) 
converter.convert(output_dir=out_dir, force=True)

print(f"Conversion complete. Model saved at {out_dir}")
