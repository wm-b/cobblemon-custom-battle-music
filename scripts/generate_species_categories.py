"""Extract the pinned Cobblemon label metadata; no textures, sounds, or species definitions are copied.

Usage: python scripts/generate_species_categories.py path/to/Cobblemon-fabric-1.8.2+1.21.1.jar
"""
import hashlib
import json
from pathlib import Path
import re
import sys
import zipfile

EXPECTED_SHA512 = "fe0090aa00a010a2353b4da43693ef70b675d5255756541cd679d2cf1df077ffedb790e9de66b1bd69cc9f6336d58763aeb15f146d07420d466daaeb3d992386"

def main():
    jar_path = Path(sys.argv[1])
    if hashlib.sha512(jar_path.read_bytes()).hexdigest() != EXPECTED_SHA512:
        raise SystemExit("Expected the official Cobblemon 1.8.2 Fabric jar (SHA-512 mismatch)")
    catalog = {}
    with zipfile.ZipFile(jar_path) as jar:
        for path in sorted(jar.namelist()):
            if not path.startswith("data/cobblemon/species/") or not path.endswith(".json"):
                continue
            species = json.loads(jar.read(path))
            base_labels = sorted(species.get("labels", []))
            forms = {"normal": base_labels}
            for form in species.get("forms", []):
                key = re.sub(r"[^a-z0-9]", "", form.get("name", "Normal").lower())
                forms[key] = sorted(form.get("labels", base_labels))
            catalog["cobblemon:" + Path(path).stem] = forms
    destination = Path(__file__).resolve().parents[1] / "src/main/resources/assets/cobblemoncustombattlemusicapi/species_categories.json"
    destination.write_text(json.dumps(catalog, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(f"Generated category metadata for {len(catalog)} species")

if __name__ == "__main__":
    main()
