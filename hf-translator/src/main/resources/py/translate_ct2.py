import base64
import sys
import json
import ctranslate2
from transformers import MarianTokenizer
from transformers import logging as hf_logging

# Suppress transformers warnings/progress bars
hf_logging.set_verbosity_error()

# Force UTF8 Outputs in print()
sys.stdout.reconfigure(encoding='utf-8')

if len(sys.argv) < 5:
    sys.exit(1)

model_dir, langIN, langOUT, text_arg = sys.argv[1:5]

translator = ctranslate2.Translator(model_dir)
tokenizer = MarianTokenizer.from_pretrained(f"Helsinki-NLP/opus-mt-{langIN}-{langOUT}")


def translate_one(text: str) -> str:
    """
    For ctranslate2, we need to use:
    1. tokenizer.prepare_seq2seq_batch() or tokenizer() with Marian-specific handling
    2. Get the tokens in the format ctranslate2 expects
    """
    # Encode the text
    inputs = tokenizer(text, return_tensors="pt")
    
    # Convert to tokens (THIS is the key step)
    # Marian tokenizer needs special handling for ctranslate2
    tokens = tokenizer.convert_ids_to_tokens(inputs["input_ids"][0])
    
    # Translate
    results = translator.translate_batch([tokens])
    
    # Decode
    translated_tokens = results[0].hypotheses[0]
    translated_text = tokenizer.convert_tokens_to_string(translated_tokens)
    
    return translated_text


def translate_batch(texts: list[str]) -> list[str]:
    """
    Batch translation with proper token handling
    """
    # Encode all texts
    encoded = tokenizer(texts, padding=True, truncation=True, return_tensors="pt")
    
    # Convert to tokens for each text
    token_batches = []
    for i in range(len(texts)):
        # Get token IDs for this example
        input_ids = encoded["input_ids"][i]
        # Remove padding tokens
        non_padding_mask = input_ids != tokenizer.pad_token_id
        valid_ids = input_ids[non_padding_mask]
        # Convert to tokens
        tokens = tokenizer.convert_ids_to_tokens(valid_ids)
        token_batches.append(tokens)
    
    # Translate
    results = translator.translate_batch(token_batches)
    
    # Decode results
    outputs = []
    for result in results:
        translated_tokens = result.hypotheses[0]
        translated_text = tokenizer.convert_tokens_to_string(translated_tokens)
        outputs.append(translated_text)
    
    return outputs


try:
    texts = json.loads(text_arg)
    if isinstance(texts, str) or isinstance(texts, (int, float)):
        outputs = [translate_one(str(texts))]
except Exception:
    # fallback to second attempt if first fails
    try:
        # Second attempt: base64-encoded JSON list
        decoded = base64.b64decode(text_arg).decode("utf-8")
        texts = json.loads(decoded)
        if isinstance(texts, list):
            outputs = translate_batch(texts)
        else:
            outputs = [translate_one(str(texts))]
    except Exception:
        # final fallback: treat as plain string
        outputs = [translate_one(str(text_arg))]

# Only JSON to stdout
print(json.dumps(outputs, ensure_ascii=False))
