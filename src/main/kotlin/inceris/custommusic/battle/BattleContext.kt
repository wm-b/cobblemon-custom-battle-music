package inceris.custommusic.battle

import java.util.UUID

enum class BattleKind { WILD, TRAINER, PLAYER }
data class TrainerIdentity(val id: String?, val name: String)
data class PlayerIdentity(val uuid: UUID, val username: String? = null)
data class OpponentPokemon(val species: String, val form: String? = null, val categories: Set<String> = emptySet())
data class BattleContext(
    val battleId: UUID,
    val kind: BattleKind,
    val trainers: List<TrainerIdentity> = emptyList(),
    val opponents: List<OpponentPokemon> = emptyList(),
    val spectating: Boolean = false,
    val players: List<PlayerIdentity> = emptyList(),
    val lowAllyHealth: Boolean = false,
    val lowEnemyHealth: Boolean = false
)
