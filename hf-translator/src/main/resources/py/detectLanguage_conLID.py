# detect_lang.py
import os
import sys
import json
import subprocess

# ---------- Dependency check & auto‑install ----------
try:
    from huggingface_hub import snapshot_download
    from model import ConLID
except ImportError as e:
    print("📦 Installing required packages...", file=sys.stderr)
    req_file = os.path.join(os.path.dirname(__file__), "requirements.txt")
    if not os.path.exists(req_file):
        subprocess.run([sys.executable, "-m", "pip", "install", "torch", "transformers", "huggingface_hub"], check=True)
    else:
        subprocess.run([sys.executable, "-m", "pip", "install", "-r", req_file], check=True)
    from huggingface_hub import snapshot_download
    from model import ConLID

sys.stdout.reconfigure(encoding='utf-8')
# Get the absolute path of the folder containing THIS script
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

# Build the model directory relative to the script, not the CWD
MODEL_DIR = os.path.join(SCRIPT_DIR, "checkpoints")


# ---------- Load model (once, global) ----------
def load_model():
    if not os.path.exists(MODEL_DIR):
        sys.stderr.write("📥 Downloading ConLID model (first time only)...\n")
        snapshot_download(repo_id="epfl-nlp/ConLID", local_dir=MODEL_DIR)
        sys.stderr.write("✅ Model ready.\n")
    return ConLID.from_pretrained(dir=MODEL_DIR)


# ---------- Single prediction ----------
def predict(text, model):
    codes, scores = model.predict(text, k=3)
    return [{"language": code, "confidence": round(score, 4)} for code, score in zip(codes, scores)]


def main():
    if len(sys.argv) < 3:
        sys.exit(1)

    # Server mode?
    if sys.argv[1] == "true":
        model = load_model()
        sys.stderr.write("🚀 Server mode active. Reading text lines from stdin...\n")
        for line in sys.stdin:
            text = line.strip()
            if not text:
                continue  # skip empty lines
            result = predict(text, model)
            print(json.dumps(result))
            sys.stdout.flush()
        return

    # Non-server mode: use command-line text
    text = sys.argv[2].strip()
    if not text:
        text = "Testing testing, is this thing working ?"
        sys.stderr.write("⚠️ No input text provided, using default.\n")

    model = load_model()
    result = predict(text, model)
    print(json.dumps(result))


if __name__ == "__main__":
    main()
