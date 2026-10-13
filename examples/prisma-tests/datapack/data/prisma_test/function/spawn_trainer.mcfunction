# Delay class resolution until invocation; Cobblemon loads NPC classes after function compilation.
execute unless entity @s[type=minecraft:player] run return 0
$spawnnpc $(npc) 10
