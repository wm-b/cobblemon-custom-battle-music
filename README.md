# Cobblemon Custom Battle Music API

A client-side Fabric mod that selects Cobblemon battle music from resource pack or local JSON rules. Cobblemon handles playback and fading; unmatched rules or unavailable sounds use its normal theme. No audio is bundled.

## Install

Requires Minecraft **1.21.1**, Java **21**, Cobblemon **1.8.2 (Fabric)**, Fabric Loader **0.17.2+**, Fabric API **0.116.6+1.21.1+**, and Fabric Language Kotlin **1.13.6+kotlin.2.2.20+** (also supplied by Cobblemon).

Build with `./gradlew build` (Windows: `.\gradlew.bat build`). Put `build/libs/cobblemon-custom-battle-music-api-0.1.0.jar` in the client's `mods/` folder.

## Add rules

Save JSON in either location:

- Client config: `config/cobblemon-custom-battle-music-api/battle_music/<file>.json`
- Resource pack: `assets/<namespace>/battle_music/<file>.json`

This example uses sounds already included in Cobblemon:

```json
{
  "id": "battle_music",
  "priority": 100,
  "rules": [
    {
      "type": "trainer",
      "match": "gym:kanto/brock",
      "sound": ["cobblemon:battle.pvn.default"]
    },
    {
      "type": "species",
      "match": "cobblemon:mewtwo",
      "sound": ["cobblemon:battle.pvw.default", "cobblemon:battle.pvp.default"]
    },
    {
      "type": "category",
      "match": "legendary",
      "sound": ["cobblemon:battle.pvp.default"]
    }
  ]
}
```

Press **F3+T** to reload client rules and resource packs, including during a battle. Custom sounds need an enabled resource pack defining them in `assets/<namespace>/sounds.json` with the referenced audio.

## Matching basics

- `trainer` matches an NPC class ID or a boundary-aware prefix: `gym` matches `gym:kanto/brock`, but not `gymnasium`. `trainer_name` matches an exact display name.
- `species` matches a namespaced species ID, optionally with `"form": "megax"`. Species and category rules default to wild battles; set `"scope": "trainer"` or `"scope": "any"` to include other battles.
- Built-in categories include `legendary`, `mythical`, and `ultra_beast`. Define custom membership with a document-level `"categories": {"boss": ["cobblemon:mewtwo"]}`.
- Higher `priority` wins; a rule's priority overrides its document's. At equal priority: exact trainer ID/name, longest trainer prefix, species with form, species, then category. Equal-ranked ties use the lexicographically smallest source/document/index key.
- `sound` must be a nonempty array of lowercase, namespaced sound IDs. One entry is chosen randomly and retained while the rule and track list stay unchanged in that battle.

See the [JSON schema](schemas/music-rules.schema.json) for all fields. Invalid rules are logged and skipped.

Resource pack authors: see the [short configuration guide](docs/resource-pack-authors.md).

## Server resource packs

Servers can distribute sounds and rule JSON together through Minecraft's normal server resource-pack settings. Include rules at `assets/<namespace>/battle_music/<file>.json` in the offered pack.

Players need the mod on their client and must accept the pack. Rules from enabled packs and client config are matched together using the normal priority rules. No server-side mod or music config is required.
