import json
from pathlib import Path

print(Path("data.json").read_text(encoding="utf-8") if Path("data.json").exists() else json.dumps({"metrics": []}))
