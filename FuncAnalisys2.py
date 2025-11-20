import os
import sys
import json
from huggingface_hub import InferenceClient
from dotenv import load_dotenv

load_dotenv()

api_token = os.getenv("HF_TOKEN")

if not api_token:
    print(json.dumps({"error": "HF_TOKEN not set"}))
    sys.exit(1)

client = InferenceClient(
    provider="auto",
    api_key=api_token,
)

def main():
    func_list = [line.strip() for line in sys.stdin if line.strip()]

    if not func_list:
        print(json.dumps({"error": "No functions received"}))
        return

    n = len(func_list)
    matrix = [[0.0 for _ in range(n)] for _ in range(n)]

    try:
        for i in range(n):
            scores = client.sentence_similarity(
                func_list[i],
                func_list,
                model="sentence-transformers/all-MiniLM-L6-v2",
            )

            for j in range(n):
                matrix[i][j] = float(scores[j])

        result = {
            "functions": func_list,
            "matrix": matrix
        }

        print(json.dumps(result))

    except Exception as e:
        print(json.dumps({"error": str(e)}))

if __name__ == "__main__":
    main()
