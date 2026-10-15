# Battle music configuration for resource pack authors

For Minecraft 1.21.1 and Cobblemon 1.8.2. Players need this mod installed and your resource pack enabled. All rule configuration and sound definitions come exclusively from enabled resource packs.

1. Add your OGG tracks, for example `assets/pack/sounds/battle/track1.ogg` and `track2.ogg`.
2. Register each track as its own sound event in `assets/pack/sounds.json`:

```json
{
  "battle.track1": {
    "sounds": [{ "name": "pack:battle/track1", "stream": true }]
  },
  "battle.track2": {
    "sounds": [{ "name": "pack:battle/track2", "stream": true }]
  }
}
```

3. Create `assets/pack/battle_music/music.json`:

```json
{
  "id": "pack_music",
  "priority": 100,
  "rules": [
    {
      "type": "species",
      "match": "cobblemon:eevee",
      "sound": ["pack:battle.track1", "pack:battle.track2"]
    }
  ]
}
```

Replace `pack` with your namespace. `sound` is a nonempty array of sound **event IDs**, not audio paths. One entry is chosen with equal probability per battle and kept while the winning rule and track list stay unchanged.

Each object inside `rules` accepts these fields:

| Field          | JSON type        | Required / default          | Purpose and restrictions                                                                                                                                                                                                                                                             |
| -------------- | ---------------- | --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `type`         | String           | Required                    | Selector: `species`, `category`, `trainer`, `trainer_name`, `player`, `wild`, or `all`. See the selector table below.                                                                                                                                                                |
| `match`        | String           | Depends on `type`           | Required for `species`, `category`, `trainer`, and `trainer_name`; optional for `player`; omit for `wild` and `all`.                                                                                                                                                                 |
| `sound`        | Array of strings | Required                    | Nonempty array of lowercase, namespaced sound event IDs. One is selected for the looping main track, or a one-shot for spawn rules.                                                                                                                                                  |
| `priority`     | Integer          | Document priority, or `0`   | Overrides document priority; higher values win. Must be a signed 32-bit integer.                                                                                                                                                                                                     |
| `form`         | String           | Any form                    | Restricts a `species` rule to one form, e.g. `"alola"`. Unsupported for other selectors.                                                                                                                                                                                             |
| `scope`        | String           | Depends on `type` / `event` | `wild`, `trainer`, `player`, or `any`. Battle defaults: `wild` for species/category/wild, `trainer` for trainer/name, `player` for player, and `any` for all. Trainer/name, player, and wild selectors require their corresponding scope. Spawn rules default to and require `wild`. |
| `event`        | String           | `"battle"`                  | `battle` selects battle music; `spawn` selects appearance sounds and supports only species/category/wild/all selectors.                                                                                                                                                              |
| `intro`        | Array of strings | Omitted                     | Nonempty array of sound event IDs; one intro plays before the main track. Battle rules only.                                                                                                                                                                                         |
| `low_ally_hp`  | Array of strings | Omitted                     | Nonempty array of sound event IDs; overrides music at ≤20% HP for any living active ally, including teammates. Takes priority over enemy music when available. Battle rules only.                                                                                                    |
| `low_enemy_hp` | Array of strings | Omitted                     | Nonempty array of sound event IDs; overrides music at ≤20% HP for any living active opponent. Battle rules only.                                                                                                                                                                     |

All sound arrays use lowercase, namespaced sound event IDs. Omit optional arrays instead of leaving them empty.

`type` determines what `match` identifies:

| `type`         | What `match` identifies                                                                   |
| -------------- | ----------------------------------------------------------------------------------------- |
| `species`      | Species ID, e.g. `cobblemon:eevee`                                                        |
| `category`     | `legendary`, `mythical`, `ultra_beast`, or individual `shiny` status                      |
| `trainer`      | NPC class ID, prefix, or wildcard, e.g. `gym:kanto/brock`, `gym`, or `cobblemon:*leader*` |
| `trainer_name` | Exact trainer display name                                                                |
| `player`       | Optional opponent UUID or exact account username; omit for all PvP                        |
| `wild`         | Omit `match`; every wild battle                                                           |
| `all`          | Omit `match`; every participating battle                                                  |

See [match strings](match-strings.md) for selector examples and prefix behavior.

Species/category rules default to wild battles. Add `"scope": "trainer"`, `"scope": "player"`, or `"scope": "any"` for other battles. Species rules can also specify a form, e.g. `"form": "alola"`.

Battle rules accept nonempty `intro`, `low_ally_hp`, and `low_enemy_hp` arrays of sound event IDs. The intro plays once, then `sound` loops. Each array has its own random selection, retained while the rule and that track list stay unchanged.

- `low_ally_hp` overrides when any living active Pokemon on your side, including teammates, reaches 20% HP or less.
- `low_enemy_hp` overrides when any living active opposing Pokemon reaches 20% HP or less.
  Ally music takes priority when both sides trigger and both tracks are available. Missing optional tracks are skipped, allowing the other side's track to play.

When neither available low-HP stage applies, the same main track restarts without replaying the intro. Define each event with a single audio file, and use short intro clips.

Appearance rules set `"event": "spawn"` and use species/category/wild/all types with wild scope and play `sound` once.

Add this object to `rules` to play a one-shot when a shiny wild Pokémon first appears to the client:

```json
{
  "event": "spawn",
  "type": "category",
  "match": "shiny",
  "sound": ["pack:battle.track1"]
}
```

Higher `priority` wins; it defaults to 0. A rule's priority overrides the document's. At equal priority, more specific matches win. Optional document-level `categories` define custom groups, e.g. `"categories": {"boss": ["cobblemon:mewtwo"]}`.

Press **F3+T** to reload and test. Missing sounds or unmatched rules use Cobblemon's normal music. Invalid rules are logged and skipped. See the [JSON schema](../schemas/music-rules.schema.json) for all fields.

Servers can offer your pack through Minecraft's normal server resource-pack settings.
