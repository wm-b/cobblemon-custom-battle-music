# Match strings

The rule's `type` determines what its `match` string means:

| `type` | Example `match` | Matches |
| --- | --- | --- |
| `species` | `cobblemon:eevee` | An opposing Eevee, any form |
| `category` | `legendary` | An opposing Pokémon in that category; also supports `mythical`, `ultra_beast`, and custom categories |
| `trainer` | `gym:kanto/brock` | An NPC class ID, exactly or as a prefix |
| `trainer_name` | `Brock` | The opposing trainer's full display name |
| `player` | `Ash` or `00000000-0000-0000-0000-000000000001` | An opposing player by account username or UUID; omit `match` for all PvP |
| `wild` | Omit | Every wild battle |
| `all` | Omit | Every participating battle |

- Matching ignores case and surrounding whitespace.
- Species IDs require a namespace (`cobblemon:`). Add `"form": "alola"` alongside `match` to restrict a species rule to that form.
- Trainer prefixes stop at `:` or `/`: `gym` matches `gym:kanto/brock`, but not `gymnasium`. Use the class ID from your NPC configuration; the example ID above is illustrative.
- Trainer names must match the whole plain-text name. Minecraft color/style codes (`§6`, `§l`, `§r`, etc.) are stripped from both the trainer name and rule before matching.
- Custom category names come from the document's `categories` object, e.g. `"categories": {"boss": ["cobblemon:mewtwo"]}` allows `"match": "boss"`.

Species/category rules default to wild battles; use `"scope": "trainer"`, `"scope": "player"`, or `"scope": "any"` for other battles. Trainer ID/name rules apply only to trainer battles. Player rules require player scope, wild rules require wild scope, and all rules default to any scope. Player names match profile usernames, not decorated display names; UUIDs work even when the profile is unavailable.

The `shiny` category describes each visible opposing Pokemon, not its species. It cannot be redefined in document categories. Add `"event": "spawn"` to species/category/wild/all rules for independent one-shot appearance sounds; these always require wild scope.

See [resource pack configuration](resource-pack-authors.md) for a complete rule example.

See [match categories](match-categories.md) for all species match categories.
