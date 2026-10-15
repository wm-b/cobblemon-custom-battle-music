"""Build the Prisma command datapack and client rule packs without editing the source ZIP."""

import argparse
import json
from pathlib import Path
import re
from uuid import UUID
from zipfile import ZIP_DEFLATED, ZipFile

ROOT = Path(__file__).resolve().parents[1]
EXAMPLES = ROOT / "examples/prisma-tests"


def encoded(value):
    return (json.dumps(value, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def sources(directory):
    return {path.relative_to(directory).as_posix(): path.read_bytes()
            for path in sorted(directory.rglob("*")) if path.is_file()}


def archive(path, entries):
    # Fixed timestamps/order make rebuilds reproducible, including the server pack SHA-1.
    from zipfile import ZipInfo
    with ZipFile(path, "w", ZIP_DEFLATED) as output:
        for name, data in sorted(entries.items()):
            info = ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            info.compress_type = ZIP_DEFLATED
            output.writestr(info, data)


def commands():
    entries = sources(EXAMPLES / "datapack")
    scenarios = json.loads((EXAMPLES / "scenarios.json").read_text(encoding="utf-8"))
    guard = "execute unless entity @s[type=minecraft:player] run return 0\n"
    sections = dict.fromkeys(scenario["section"] for scenario in scenarios)
    for scenario in scenarios:
        lines = [guard.rstrip(), *scenario.get("commands", [])]
        lines.append("tellraw @s " + json.dumps({"text": scenario["instructions"], "color": "gold"}))
        entries[f'data/prisma_test/function/{scenario["id"]}.mcfunction'] = ("\n".join(lines) + "\n").encode()
    for section in ["help", *sections]:
        lines = [guard.rstrip(), "tellraw @s " + json.dumps({
            "text": "Prisma music tests: finish the current battle before spawning another. See TEST_SCENARIOS.md for prerequisites.",
            "color": "gold"})]
        choices = [("help_" + name, name.replace("_", " ")) for name in sections] if section == "help" else [
            (scenario["id"], scenario["label"]) for scenario in scenarios if scenario["section"] == section]
        for name, label in choices:
            lines.append("tellraw @s " + json.dumps({"text": "[" + label + "]", "color": "aqua", "clickEvent": {
                "action": "run_command", "value": "/function prisma_test:" + name}}))
        name = "help" if section == "help" else "help_" + section
        entries[f"data/prisma_test/function/{name}.mcfunction"] = ("\n".join(lines) + "\n").encode()
    return entries


def build(args):
    if not re.fullmatch(r"[A-Za-z0-9_]{1,16}", args.player_name):
        raise ValueError("--player-name must be an account username")
    player_uuid = str(UUID(args.player_uuid))
    entries = sources(EXAMPLES / "resourcepack")
    rule_path = "assets/prisma_test/battle_music/music.json"
    if args.variant != "complete":
        entries[rule_path] = (EXAMPLES / "variants" / (args.variant + ".json")).read_bytes()
    rules = json.loads(entries[rule_path])
    for rule in rules["rules"]:
        if rule["type"] == "player" and rule.get("match") == "MusicTestPlayer":
            rule["match"] = args.player_name
        elif rule["type"] == "player" and rule.get("match") == "00000000-0000-0000-0000-000000000001":
            rule["match"] = player_uuid
    entries[rule_path] = encoded(rules)
    with ZipFile(args.prisma_zip) as original:
        audio = {name: original.read(name) for name in original.namelist() if name.startswith("assets/") and not name.endswith("/")}
    aliases = json.loads(entries["assets/prisma_test/sounds.json"])
    for event in aliases.values():
        for sound in event["sounds"]:
            if sound.get("type", "file") == "event":
                continue
            namespace, name = sound["name"].split(":", 1)
            path = f"assets/{namespace}/sounds/{name}.ogg"
            if path not in audio:
                raise ValueError(f"Source pack is missing {path}")
    # Intentional missing sounds exercise fallbacks; every other custom event must exist.
    for rule in rules["rules"]:
        for field in ["sound", "intro", "low_ally_hp", "low_enemy_hp"]:
            for sound in rule.get(field, []):
                if sound.startswith("prisma_test:") and not sound.startswith("prisma_test:missing_"):
                    if sound.split(":", 1)[1] not in aliases:
                        raise ValueError(f"Unknown event: {sound}")
    args.output.mkdir(parents=True, exist_ok=True)
    archive(args.output / "prisma-battle-music-test-commands.zip", commands())
    archive(args.output / "prisma-battle-music-tests.zip", entries)
    if args.server:
        archive(args.output / "cobblemon-prisma-server.zip", {**audio, **entries})
    print(f"Built {args.variant} rule examples and command datapack in {args.output}")
    print("Rules are resource-pack-only. Install the music mod on clients; /reload reloads commands, F3+T reloads client packs.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("prisma_zip", type=Path, help="Original Prisma Battle Tracks.zip (left unchanged)")
    parser.add_argument("--server", action="store_true", help="Also bundle the supplied audio into the server resource pack")
    parser.add_argument("--output", type=Path, default=ROOT / "build/prisma-tests")
    parser.add_argument("--player-name", default="MusicTestPlayer")
    parser.add_argument("--player-uuid", default="00000000-0000-0000-0000-000000000001")
    parser.add_argument("--variant", choices=["complete", "no-rules", "spawn-all", "spawn-wild", "player-name"], default="complete")
    build(parser.parse_args())
