package inceris.custommusic.rules

import inceris.custommusic.battle.BattleKind
import java.util.Locale

fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)
enum class RuleType { TRAINER, TRAINER_NAME, SPECIES, CATEGORY, WILD, ALL, PLAYER }
enum class RuleEvent { BATTLE, SPAWN }
enum class RuleScope {
    WILD, TRAINER, PLAYER, ANY;
    fun includes(kind: BattleKind): Boolean = when (this) {
        WILD -> kind == BattleKind.WILD
        TRAINER -> kind == BattleKind.TRAINER
        PLAYER -> kind == BattleKind.PLAYER
        ANY -> true
    }
}

fun defaultScope(type: RuleType, event: RuleEvent = RuleEvent.BATTLE): RuleScope = when {
    event == RuleEvent.SPAWN -> RuleScope.WILD
    type in setOf(RuleType.TRAINER, RuleType.TRAINER_NAME) -> RuleScope.TRAINER
    type == RuleType.PLAYER -> RuleScope.PLAYER
    type == RuleType.ALL -> RuleScope.ANY
    else -> RuleScope.WILD
}

data class MusicRule(
    val source: String,
    val documentId: String,
    val index: Int,
    val type: RuleType,
    val match: String,
    val sound: List<String>,
    val priority: Int = 0,
    val form: String? = null,
    val scope: RuleScope = defaultScope(type),
    val event: RuleEvent = RuleEvent.BATTLE,
    val intro: List<String> = emptyList(),
    val lowAllyHp: List<String> = emptyList(),
    val lowEnemyHp: List<String> = emptyList()
) {
    init { require(sound.isNotEmpty()) { "A music rule must contain at least one sound" } }
    val key: String get() = "$source#$documentId[$index]"
}

data class SpeciesSelector(val species: String, val form: String? = null)
data class RuleSnapshot(val rules: List<MusicRule> = emptyList(), val categories: Map<String, Set<SpeciesSelector>> = emptyMap())
data class Diagnostic(val source: String, val message: String)
data class ParsedDocument(val rules: List<MusicRule>, val categories: Map<String, Set<SpeciesSelector>>, val diagnostics: List<Diagnostic>)
