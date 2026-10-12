package inceris.custommusic.rules

import inceris.custommusic.battle.BattleContext
import inceris.custommusic.battle.OpponentPokemon

data class MatchRank(val priority: Int, val specificity: Int, val prefixLength: Int = 0)
data class RuleMatch(val rule: MusicRule, val rank: MatchRank)
data class RuleSelection(val winner: RuleMatch?, val tied: List<RuleMatch>)

/** Pure resolver. Stable source keys break ties independently of load/opponent order. */
class RuleMatcher {
    fun select(context: BattleContext, snapshot: RuleSnapshot): RuleSelection {
        if (context.spectating) return RuleSelection(null, emptyList())
        val matches = snapshot.rules.mapNotNull { rule ->
            if (!rule.scope.includes(context.kind)) return@mapNotNull null
            rank(rule, context, snapshot)?.let { RuleMatch(rule, it) }
        }.sortedWith(compareByDescending<RuleMatch> { it.rank.priority }
            .thenByDescending { it.rank.specificity }.thenByDescending { it.rank.prefixLength }.thenBy { it.rule.key })
        val winner = matches.firstOrNull()
        return RuleSelection(winner, if (winner == null) emptyList() else matches.filter { it.rank == winner.rank })
    }

    private fun rank(rule: MusicRule, context: BattleContext, snapshot: RuleSnapshot): MatchRank? {
        val selector = normalize(rule.match)
        val specificity = when (rule.type) {
            RuleType.TRAINER -> {
                val ids = context.trainers.mapNotNull { it.id?.let(::normalize) }
                when {
                    selector in ids -> return MatchRank(rule.priority, 6)
                    ids.any { matchesTrainerPrefix(selector, it) } -> return MatchRank(rule.priority, 5, selector.length)
                    else -> return null
                }
            }
            RuleType.TRAINER_NAME -> if (context.trainers.any { normalize(it.name) == selector }) 6 else return null
            RuleType.SPECIES -> if (context.opponents.any { matchesSpecies(SpeciesSelector(selector, rule.form), it) }) {
                if (rule.form == null) 3 else 4
            } else return null
            RuleType.CATEGORY -> if (context.opponents.any { pokemon ->
                pokemon.categories.any { normalize(it) == selector } ||
                    snapshot.categories[selector].orEmpty().any { matchesSpecies(it, pokemon) }
            }) 2 else return null
        }
        return MatchRank(rule.priority, specificity)
    }

    companion object {
        fun matchesTrainerPrefix(selector: String, identity: String): Boolean {
            val prefix = normalize(selector)
            val id = normalize(identity)
            if (prefix.isEmpty()) return false
            return id == prefix || (id.startsWith(prefix) && id.getOrNull(prefix.length) in listOf(':', '/'))
        }
        fun matchesSpecies(selector: SpeciesSelector, pokemon: OpponentPokemon): Boolean =
            normalize(selector.species) == normalize(pokemon.species) &&
                (selector.form == null || normalize(selector.form) == pokemon.form?.let(::normalize))
    }
}
