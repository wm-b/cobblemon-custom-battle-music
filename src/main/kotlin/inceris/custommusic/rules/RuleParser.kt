package inceris.custommusic.rules

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.Reader

/** Validates user input before it can enter the immutable rule snapshot. */
class RuleParser {
    fun parse(reader: Reader, source: String): ParsedDocument {
        val diagnostics = mutableListOf<Diagnostic>()
        val rules = mutableListOf<MusicRule>()
        val categories = linkedMapOf<String, Set<SpeciesSelector>>()
        try {
            val root = JsonParser.parseReader(reader).objectValue("document")
            root.checkFields(setOf("id", "priority", "rules", "categories"))
            val id = root.requiredString("id")
            val priority = root.optionalInt("priority") ?: 0
            val ruleArray = root.get("rules") ?: error("Missing rules array")
            require(ruleArray.isJsonArray) { "rules must be an array" }
            ruleArray.asJsonArray.forEachIndexed { index, value ->
                try {
                    val rule = value.objectValue("rules[$index]")
                    rule.checkFields(setOf("type", "match", "sound", "priority", "form", "scope", "event", "intro", "low_ally_hp", "low_enemy_hp"))
                    val type = when (rule.requiredString("type")) {
                        "trainer" -> RuleType.TRAINER
                        "trainer_name" -> RuleType.TRAINER_NAME
                        "species" -> RuleType.SPECIES
                        "category" -> RuleType.CATEGORY
                        "wild" -> RuleType.WILD
                        "all" -> RuleType.ALL
                        "player" -> RuleType.PLAYER
                        else -> error("Unknown rule type")
                    }
                    val event = when (rule.optionalString("event") ?: "battle") {
                        "battle" -> RuleEvent.BATTLE
                        "spawn" -> RuleEvent.SPAWN
                        else -> error("event must be battle or spawn")
                    }
                    val match = if (type in setOf(RuleType.WILD, RuleType.ALL, RuleType.PLAYER)) {
                        rule.optionalString("match")?.let(::normalize).orEmpty()
                    } else normalize(rule.requiredString("match"))
                    when (type) {
                        RuleType.TRAINER -> require(TRAINER_ID.matches(match)) { "Invalid trainer selector: $match" }
                        RuleType.SPECIES -> require(RESOURCE_ID.matches(match)) { "Species must be a namespaced identifier: $match" }
                        RuleType.CATEGORY -> require(LABEL.matches(match)) { "Invalid category: $match" }
                        RuleType.TRAINER_NAME -> Unit
                        RuleType.WILD, RuleType.ALL -> require(match.isEmpty()) { "${type.name.lowercase()} rules do not accept match" }
                        RuleType.PLAYER -> require(match.isEmpty() || USERNAME.matches(match) || UUID_PATTERN.matches(match)) {
                            "player match must be a username or canonical UUID"
                        }
                    }
                    val sound = rule.soundList("sound", required = true)
                    val intro = rule.soundList("intro")
                    val lowAllyHp = rule.soundList("low_ally_hp")
                    val lowEnemyHp = rule.soundList("low_enemy_hp")
                    val form = rule.optionalString("form")?.let(::normalize)
                    require(form == null || type == RuleType.SPECIES) { "form is only valid for species rules" }
                    val defaultScope = defaultScope(type, event)
                    val scope = rule.optionalString("scope")?.let {
                        when (it) {
                            "wild" -> RuleScope.WILD
                            "trainer" -> RuleScope.TRAINER
                            "player" -> RuleScope.PLAYER
                            "any" -> RuleScope.ANY
                            else -> error("scope must be wild, trainer, player, or any")
                        }
                    } ?: defaultScope
                    require(type !in setOf(RuleType.TRAINER, RuleType.TRAINER_NAME) || scope == RuleScope.TRAINER) {
                        "Trainer identity rules require trainer scope"
                    }
                    require(type != RuleType.PLAYER || scope == RuleScope.PLAYER) { "Player rules require player scope" }
                    require(type != RuleType.WILD || scope == RuleScope.WILD) { "Wild rules require wild scope" }
                    require(event != RuleEvent.SPAWN || (type in setOf(RuleType.SPECIES, RuleType.CATEGORY, RuleType.WILD, RuleType.ALL) && scope == RuleScope.WILD)) {
                        "Spawn rules require wild scope and species, category, wild, or all type"
                    }
                    require(event != RuleEvent.SPAWN || (intro.isEmpty() && lowAllyHp.isEmpty() && lowEnemyHp.isEmpty())) {
                        "Spawn sounds cannot have intro, low_ally_hp, or low_enemy_hp tracks"
                    }
                    rules += MusicRule(source, id, index, type, match, sound, rule.optionalInt("priority") ?: priority, form, scope, event, intro, lowAllyHp, lowEnemyHp)
                } catch (exception: RuntimeException) {
                    diagnostics += Diagnostic(source, "rules[$index]: ${exception.message}")
                }
            }
            root.get("categories")?.objectValue("categories")?.entrySet()?.forEach { (label, value) ->
                try {
                    val normalizedLabel = normalize(label)
                    require(LABEL.matches(normalizedLabel)) { "Invalid category name" }
                    require(normalizedLabel != "shiny") { "shiny is reserved for per-Pokemon shiny status" }
                    require(value.isJsonArray) { "Category members must be an array" }
                    val members = value.asJsonArray.map { member ->
                        if (member.isJsonPrimitive && member.asJsonPrimitive.isString) SpeciesSelector(normalize(member.asString))
                        else {
                            val obj = member.objectValue("category member")
                            obj.checkFields(setOf("species", "form"))
                            SpeciesSelector(normalize(obj.requiredString("species")), obj.optionalString("form")?.let(::normalize))
                        }
                    }.toSet()
                    require(members.all { RESOURCE_ID.matches(it.species) }) { "Category species must be namespaced identifiers" }
                    categories[normalizedLabel] = members
                } catch (exception: RuntimeException) {
                    diagnostics += Diagnostic(source, "categories.$label: ${exception.message}")
                }
            }
        } catch (exception: RuntimeException) {
            return ParsedDocument(emptyList(), emptyMap(), listOf(Diagnostic(source, exception.message ?: "Invalid JSON")))
        }
        rules.groupBy { listOf(it.type, it.match, it.form, it.scope, it.priority, it.event) }.values.filter { it.size > 1 }.forEach {
            diagnostics += Diagnostic(source, "Equal-priority duplicate selector: ${it.joinToString { rule -> rule.key }}")
        }
        return ParsedDocument(rules.toList(), categories.toMap(), diagnostics.toList())
    }

    private fun JsonElement.objectValue(label: String): JsonObject {
        require(isJsonObject) { "$label must be an object" }
        return asJsonObject
    }
    private fun JsonObject.checkFields(allowed: Set<String>) {
        require(keySet().all { it in allowed }) { "Unknown fields: ${keySet() - allowed}" }
    }
    private fun JsonObject.requiredString(key: String): String = optionalString(key) ?: error("Missing $key")
    private fun JsonObject.soundList(key: String, required: Boolean = false): List<String> {
        val array = get(key) ?: return if (required) error("Missing $key") else emptyList()
        require(array.isJsonArray && !array.asJsonArray.isEmpty) { "$key must be a nonempty array of sound identifiers" }
        return array.asJsonArray.mapIndexed { index, entry ->
            require(entry.isJsonPrimitive && entry.asJsonPrimitive.isString) { "$key[$index] must be a string" }
            val identifier = entry.asString.trim()
            require(RESOURCE_ID.matches(identifier)) { "Invalid sound identifier at $key[$index]: $identifier" }
            identifier
        }
    }
    private fun JsonObject.optionalString(key: String): String? {
        val value = get(key) ?: return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString && value.asString.isNotBlank()) { "$key must be a nonempty string" }
        return value.asString.trim()
    }
    private fun JsonObject.optionalInt(key: String): Int? {
        val value = get(key) ?: return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "$key must be an integer" }
        return value.asString.toIntOrNull() ?: error("$key must be a 32-bit integer")
    }
    companion object {
        val RESOURCE_ID = Regex("[a-z0-9_.-]+:[a-z0-9/._-]+")
        private val TRAINER_ID = Regex("[a-z0-9_.*-]+(?::[a-z0-9_.*-]+)?(?:/[a-z0-9_.*-]+)*")
        private val LABEL = Regex("[a-z0-9_.-]+")
        private val USERNAME = Regex("[a-z0-9_]{1,16}")
        private val UUID_PATTERN = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
    }
}
