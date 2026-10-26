# Cobblemon Custom Battle Music

A client-side Fabric mod for Cobblemon battle music, intros, low-HP themes, and wild Pokemon appearance sounds, configured through resource packs. No audio is bundled.

## Install

Requires Minecraft **1.21.1**, Java **21**, Cobblemon **1.8.x (Fabric)**, Fabric Loader **0.17.2+**, and Fabric API **0.116.6+1.21.1+**. Builds against Cobblemon **1.8.3**; the runtime dependency accepts `>=1.8.0 <1.9.0`.

Build with `./gradlew build` (Windows: `.\gradlew.bat build`). Put `build/libs/CobblemonCustomBattleMusic_1-0-0.jar` in the client's `mods/` folder.

Automated checks cover rule behavior and integration hooks on Cobblemon **1.8.0 through 1.8.3**. Prisma example-pack checks run only when the optional fixtures are installed under `examples/prisma-tests`.

## Add rules

Include a rules JSON file at `assets/mypack/battle_music/music.json` in a resource pack.

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

See the [resource pack author guide](docs/resource-pack-authors.md) for audio setup, rule files, reloading, and server distribution.

## Matching basics

See [match strings](docs/match-strings.md), [match categories](docs/match-categories.md), and the [JSON schema](schemas/music-rules.schema.json).

Equal priorities rank by UUID, exact trainer/name/username, longest trainer prefix, trainer wildcard, species with form, species, category, wild/player default, then all. Trainer ID patterns support `*` for any sequence of characters, e.g. `cobblemon:*leader*`. Wildcard ties favor more literal characters. Remaining ties use the smallest source/document/index key lexicographically.

## Intro and low-HP music

Intro leads into the main track. `low_ally_hp` and `low_enemy_hp` override music when their side is low on HP; ally music takes priority when both trigger.

```json
{
  "id": "battle_stages",
  "rules": [
    {
      "type": "wild",
      "sound": ["cobblemon:battle.pvw.default"],
      "intro": ["cobblemon:battle.pvn.default"],
      "low_ally_hp": ["cobblemon:battle.pvp.default"],
      "low_enemy_hp": ["cobblemon:battle.pvn.default"]
    },
    {
      "type": "player",
      "match": "00000000-0000-0000-0000-000000000001",
      "sound": ["cobblemon:battle.pvp.default"]
    },
    {
      "type": "all",
      "sound": ["cobblemon:battle.pvn.default"]
    }
  ]
}
```

See the [author guide](docs/resource-pack-authors.md) more detail.

## Wild appearance sounds

Play a sound when a wild Pokémon appears.

```json
{
  "id": "wild_appearances",
  "rules": [
    {
      "event": "spawn",
      "type": "category",
      "match": "shiny",
      "sound": ["cobblemon:battle.pvw.default"]
    }
  ]
}
```

See the [author guide](docs/resource-pack-authors.md) for detailed spawn rules.

## Code formatting

Java sources use Spotless with google-java-format (2-space block indentation).
Run `./gradlew spotlessApply` to format and `./gradlew spotlessCheck` to check formatting.
On Windows, use `.\gradlew.bat spotlessApply` and `.\gradlew.bat spotlessCheck`.
Formatting is also checked by `./gradlew check` and `./gradlew build`.
