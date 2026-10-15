# Prisma battle music tests

For Minecraft 1.21.1 and Cobblemon 1.8.2. The command datapack supplies a clickable test menu and five sample trainers. The companion resource pack supplies 33 rules and single-track Prisma aliases. Rules are loaded only from enabled client resource packs; install the music mod on clients.

## Install and rebuild

```powershell
python scripts/build_prisma_test_packs.py "C:/path/to/Prisma Battle Tracks.zip" --server
```

Outputs in `build/prisma-tests/`:

| Archive | Install location | Contents |
| --- | --- | --- |
| `prisma-battle-music-test-commands.zip` | World `datapacks/` | Commands and sample NPC definitions; no automatic load/tick functions |
| `prisma-battle-music-tests.zip` | Client `resourcepacks/`, above the original Prisma pack | Rules and aliases; requires the original Prisma audio pack |
| `cobblemon-prisma-server.zip` | Offered server resource pack, or client `resourcepacks/` | Self-contained rules, aliases, and audio from the supplied ZIP |

Enable one companion resource pack. A server may offer the self-contained ZIP through Minecraft's resource-pack settings; players must accept it. Update `resource-pack-sha1` when replacing an offered ZIP and reconnect to download it again. The original Prisma ZIP is never modified. There is no server-side music configuration or `/battle-music reload` command.

To install into the existing Modrinth test profile after rebuilding:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/install_prisma_test_packs.ps1 -ProfileOnly
```

This updates the profile's companion resource pack, stages the command ZIP in its `datapacks/` folder, verifies the mod JAR, and writes `PRISMA_TEST_SCENARIOS.md`. Enable the original Prisma pack and the companion above it in Minecraft's Resource Packs menu. The top-level `datapacks/` folder is a staging location: add its ZIP through the Data Packs menu when creating a world, or copy it into an existing world's `datapacks/` folder and reopen that world. The default installer also updates the named test network.

Restart the backend once after installing the new NPC definitions: Cobblemon loads its data registries only at startup. Run `/reload` for later command-only updates, then `/function prisma_test:help` as an operator **in game**. Commands run at the invoking player's location. Finish or flee each battle before spawning another opponent. Use your own party; `/function prisma_test:prepare_false_swipe` adds a Scyther with False Swipe and Roost without clearing existing Pokemon. `/healpokemon` is useful between battles.

The editable [rule document](resourcepack/assets/prisma_test/battle_music/music.json), [sound aliases](resourcepack/assets/prisma_test/sounds.json), [scenario manifest](scenarios.json), and [NPC definitions](datapack/data/) are the source of the ZIPs. Rebuild after editing them. F3+T reloads enabled client packs, including during battle; it does not refresh the server's hosted ZIP from disk. Enable DEBUG on the `cobblemon-custom-battle-music` logger to inspect selected rule sources, sound events, and stages.

## Battle selectors

Use `/function prisma_test:<helper>`. The original nine helper names are retained.

| Helper | Expected battle music | Scenario |
| --- | --- | --- |
| `eevee` | Battle11Wild / Battle14Wild / Battle22Wild | Equal random choice, retained within a battle; repeats across battles are allowed |
| `pikachu` | Battle13Rival | Single-track species match |
| `mewtwo` | Battle4Cynthia | Species beats legendary at equal priority |
| `articuno` | Battle6LegendaryFadeout / Battle10Legendary / Battle23Legendary | Legendary category |
| `mew` | Battle24Colress | Mythical category |
| `nihilego` | Battle26LegendaryFadeout | Ultra Beast category |
| `vulpix` / `vulpix_alola` | Battle1 / Battle21GymFadeout | Species versus form-specific matching |
| `rattata` | Battle1 | Wild default; this replaces the older no-match expectation |
| `shiny_rattata` | Battle24Colress | Individual shiny status, with `scope: any` |
| `custom_category` | Battle26LegendaryFadeout | Custom `boss` category, using Snorlax |
| `priority_override` | Battle26LegendaryFadeout | Higher-priority category beats Bulbasaur's species rule |

## Intro and low HP

| Helper | Setup and expected behavior |
| --- | --- |
| `intro` | Battle the spawned Magikarp: a short toast sound plays once, then Battle11Wild loops |
| `low_ally_hp` | Reduce a living active ally to 20% HP or less: Battle4Cynthia overrides intro/main |
| `low_enemy_hp` | Reduce Magikarp to 20% HP or less, using False Swipe if needed: Battle24Colress overrides |
| `both_low_hp` | Both sides low: ally track wins. Recover the last low ally: enemy track plays. Recover the enemy: the same main track restarts without the intro |
| `ally_only` | Caterpie configures only ally music; low enemy HP has no effect |
| `enemy_only` | Weedle configures only enemy music; low ally HP has no effect |
| `missing_optional` | Ditto's missing intro/ally tracks are skipped; enemy music still plays when the enemy is low, including when both sides are low |
| `missing_main` | Pidgey's missing main restores the native Prisma wild pool; its missing appearance event is silent |
| `teammate_low_hp` | Instructions for a multi battle: living active teammates count as allies; benched and fainted Pokemon do not trigger either stage |

HP changes should not reroll tracks. Test low HP before the intro ends and at battle start, healing, switching, and fainting the last qualifying Pokemon. Unchanged F3+T and duplicate music requests must not replay a consumed intro. Multi/doubles checks require a compatible battle setup and additional participants; the helper prints a checklist rather than starting such a battle. Check both active slots and enemy fractional HP at the red-bar boundary.

## Trainers and players

Trainer helpers spawn level 10 NPCs. Interact with the NPC and accept its challenge. These NPCs use Cobblemon's standard renderer/dialogue and their own class IDs; spawning one does not start a battle automatically.

| Helper | Expected music | Scenario |
| --- | --- | --- |
| `trainer_exact` | Battle4Cynthia | `prisma_gym:brock` exact ID beats the namespace prefix |
| `trainer_prefix` | Battle10Legendary | `prisma_gym` namespace prefix matches Falkner's class |
| `trainer_name` | Battle13Rival | Exact `Prisma Rival` display name |
| `trainer_species_scope` | Battle22Wild | Trainer-scoped Eevee, distinct from its wild rule |
| `trainer_default` | Battle13Rival | `all` default catches an otherwise unmatched NPC battle |
| `player_default` | Battle4Cynthia | PvP against an untargeted opponent with a non-Eevee lead |
| `player_species_scope` | Battle14Wild | PvP against an untargeted opponent with Eevee active |
| `player_name` | Battle24Colress | Exact account username; isolate with the `player-name` variant |
| `player_uuid` | Battle21GymFadeout | UUID beats an equal-priority username rule for the same opponent |
| `spectator` | Native behavior | `/spectateBattle ACCOUNT` must not activate custom participating-battle rules |

Player helpers print instructions; challenge a second player through Cobblemon's battle interface. Set the actual opponent's identity when building:

```powershell
python scripts/build_prisma_test_packs.py "C:/path/to/Prisma Battle Tracks.zip" --server --player-name ACCOUNT --player-uuid UUID
```

Without these options, the sample targets are `MusicTestPlayer` and `00000000-0000-0000-0000-000000000001`. They are examples, not an actual account. Username matching needs the opponent's synchronized account profile; UUID matching survives name changes. Swap active Pokemon in trainer/PvP battles to verify scopes and shiny matching. Test trainer class prefixes against an unrelated class to confirm boundary handling.

## Appearance sounds

These one-shots trigger on client tracking, before battling. Music rules and appearance rules are independent. Music volume controls battle music; neutral-category volume controls alerts.

| Helper | Expected one-shot | Scenario |
| --- | --- | --- |
| `spawn_species` | Player level-up | Pikachu species selector |
| `spawn_shiny` | Note-block pling | Shiny Rattata; shiny priority wins over other appearance matches |
| `spawn_category` | Firework twinkle | Snorlax custom category |
| `spawn_form` | Item pickup | Alolan Vulpix form selector |
| `spawn_default` | Experience-orb pickup | Wild default |
| `spawn_all` / `spawn_wild` | Experience-orb pickup | Instructions for isolated `all` / `wild` variants |
| `spawn_once` | Experience-orb pickup, once | Same UUID stays silent across tracking loss, unloads, dimensions, F3+T, reconnects, restarts, and rule changes |
| `owned_exclusion` | Silence on send-out | Adds an owned shiny Rattata; NPC send-outs must also stay silent |

Natural spawns and existing wild Pokemon encountered while travelling also qualify; on-screen visibility is not required. Rerunning a spawn helper creates a **new UUID** and may alert again. To test persistence, spawn once and re-encounter that same Pokemon. Histories are per listener account and server address/local world path. Keep `.cobblemon-custom-battle-music/appearance_history/`; do not erase real histories to reset tests. A different address/world path or listener has separate history. Playback is requested at most once; muted audio or disconnecting during saving can consume a record without an audible alert.

`history_failure` prints an additional checklist for unavailable audio and unwritable/corrupt history in a disposable client copy. Failed saves must produce no alert. Upgrade histories should migrate; conflicting old/new folders must suppress appearance playback.

## Isolated variants and reload checks

The complete pack includes broad defaults. Use a variant when those defaults would obscure a test:

| Builder option | Rule set |
| --- | --- |
| `--variant no-rules` | Empty rules: tests native music with no match |
| `--variant spawn-all` | Only an `all` appearance rule |
| `--variant spawn-wild` | Only a `wild` appearance rule |
| `--variant player-name` | Username targeting plus PvP default, without the UUID rule |

Each variant replaces the complete rule document in the generated resource pack. Other helper expectations in this guide refer to the complete pack. Do not enable another rule pack during isolated checks. Rebuild without `--variant` to restore the complete suite.

The `reload`, `no_match`, and `help_setup` helpers print these checks in game. Verify a changed pack during an active battle, unchanged reload stability, missing sounds, battle-end fading, disconnect during the intro, and transfer to a backend that unloads this pack. All resource-pack rules compete by priority; unrelated enabled packs can override test expectations. Local configuration JSON is ignored.
