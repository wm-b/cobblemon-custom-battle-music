package inceris.custommusic.rules;

import static inceris.custommusic.rules.RuleStrings.normalize;

import com.google.gson.*;
import java.io.Reader;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Validates user input before it can enter the immutable rule snapshot. */
public final class RuleParser {
  public static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
  private static final Pattern TRAINER_ID =
      Pattern.compile("[a-z0-9_.*-]+(?::[a-z0-9_.*-]+)?(?:/[a-z0-9_.*-]+)*");
  private static final Pattern LABEL = Pattern.compile("[a-z0-9_.-]+");
  private static final Pattern USERNAME = Pattern.compile("[a-z0-9_]{1,16}");
  private static final Pattern UUID_PATTERN =
      Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");

  public ParsedDocument parse(Reader reader, String source) {
    List<Diagnostic> diagnostics = new ArrayList<>();
    List<MusicRule> rules = new ArrayList<>();
    Map<String, Set<SpeciesSelector>> categories = new LinkedHashMap<>();
    try {
      JsonObject root = objectValue(JsonParser.parseReader(reader), "document");
      checkFields(root, Set.of("id", "priority", "rules", "categories"));
      String id = requiredString(root, "id");
      int priority = optionalInt(root, "priority", 0);
      JsonElement array = root.get("rules");
      require(array != null, "Missing rules array");
      require(array.isJsonArray(), "rules must be an array");
      for (int index = 0; index < array.getAsJsonArray().size(); index++) {
        try {
          JsonObject rule = objectValue(array.getAsJsonArray().get(index), "rules[" + index + "]");
          checkFields(
              rule,
              Set.of(
                  "type",
                  "match",
                  "sound",
                  "priority",
                  "form",
                  "scope",
                  "event",
                  "intro",
                  "low_ally_hp",
                  "low_enemy_hp"));
          RuleType type =
              switch (requiredString(rule, "type")) {
                case "trainer" -> RuleType.TRAINER;
                case "trainer_name" -> RuleType.TRAINER_NAME;
                case "species" -> RuleType.SPECIES;
                case "category" -> RuleType.CATEGORY;
                case "wild" -> RuleType.WILD;
                case "all" -> RuleType.ALL;
                case "player" -> RuleType.PLAYER;
                default -> throw new IllegalArgumentException("Unknown rule type");
              };
          RuleEvent event =
              switch (optionalString(rule, "event", "battle")) {
                case "battle" -> RuleEvent.BATTLE;
                case "spawn" -> RuleEvent.SPAWN;
                default -> throw new IllegalArgumentException("event must be battle or spawn");
              };
          String match =
              normalize(
                  switch (type) {
                    case WILD, ALL, PLAYER -> optionalString(rule, "match", "");
                    default -> requiredString(rule, "match");
                  });
          switch (type) {
            case TRAINER ->
                require(TRAINER_ID.matcher(match).matches(), "Invalid trainer selector: " + match);
            case SPECIES ->
                require(
                    RESOURCE_ID.matcher(match).matches(),
                    "Species must be a namespaced identifier: " + match);
            case CATEGORY -> require(LABEL.matcher(match).matches(), "Invalid category: " + match);
            case TRAINER_NAME -> {}
            case WILD, ALL ->
                require(
                    match.isEmpty(),
                    type.name().toLowerCase(Locale.ROOT) + " rules do not accept match");
            case PLAYER ->
                require(
                    match.isEmpty()
                        || USERNAME.matcher(match).matches()
                        || UUID_PATTERN.matcher(match).matches(),
                    "player match must be a username or canonical UUID");
          }
          List<String> sound = soundList(rule, "sound", true);
          List<String> intro = soundList(rule, "intro", false);
          List<String> ally = soundList(rule, "low_ally_hp", false);
          List<String> enemy = soundList(rule, "low_enemy_hp", false);
          String form = optionalString(rule, "form", null);
          if (form != null) form = normalize(form);
          require(form == null || type == RuleType.SPECIES, "form is only valid for species rules");
          String scopeName = optionalString(rule, "scope", null);
          RuleScope scope =
              scopeName == null
                  ? RuleScope.defaultScope(type, event)
                  : switch (scopeName) {
                    case "wild" -> RuleScope.WILD;
                    case "trainer" -> RuleScope.TRAINER;
                    case "player" -> RuleScope.PLAYER;
                    case "any" -> RuleScope.ANY;
                    default ->
                        throw new IllegalArgumentException(
                            "scope must be wild, trainer, player, or any");
                  };
          require(
              (type != RuleType.TRAINER && type != RuleType.TRAINER_NAME)
                  || scope == RuleScope.TRAINER,
              "Trainer identity rules require trainer scope");
          require(
              type != RuleType.PLAYER || scope == RuleScope.PLAYER,
              "Player rules require player scope");
          require(
              type != RuleType.WILD || scope == RuleScope.WILD, "Wild rules require wild scope");
          require(
              event != RuleEvent.SPAWN
                  || (Set.of(RuleType.SPECIES, RuleType.CATEGORY, RuleType.WILD, RuleType.ALL)
                          .contains(type)
                      && scope == RuleScope.WILD),
              "Spawn rules require wild scope and species, category, wild, or all type");
          require(
              event != RuleEvent.SPAWN || (intro.isEmpty() && ally.isEmpty() && enemy.isEmpty()),
              "Spawn sounds cannot have intro, low_ally_hp, or low_enemy_hp tracks");
          rules.add(
              new MusicRule(
                  source,
                  id,
                  index,
                  type,
                  match,
                  sound,
                  optionalInt(rule, "priority", priority),
                  form,
                  scope,
                  event,
                  intro,
                  ally,
                  enemy));
        } catch (RuntimeException exception) {
          diagnostics.add(
              new Diagnostic(source, "rules[" + index + "]: " + exception.getMessage()));
        }
      }
      if (root.has("categories")) {
        for (var entry : objectValue(root.get("categories"), "categories").entrySet()) {
          String label = entry.getKey();
          try {
            String normalized = normalize(label);
            require(LABEL.matcher(normalized).matches(), "Invalid category name");
            require(!normalized.equals("shiny"), "shiny is reserved for per-Pokemon shiny status");
            require(entry.getValue().isJsonArray(), "Category members must be an array");
            Set<SpeciesSelector> members = new LinkedHashSet<>();
            for (JsonElement member : entry.getValue().getAsJsonArray()) {
              if (member.isJsonPrimitive() && member.getAsJsonPrimitive().isString()) {
                members.add(new SpeciesSelector(normalize(member.getAsString())));
              } else {
                JsonObject obj = objectValue(member, "category member");
                checkFields(obj, Set.of("species", "form"));
                String form = optionalString(obj, "form", null);
                members.add(
                    new SpeciesSelector(
                        normalize(requiredString(obj, "species")),
                        form == null ? null : normalize(form)));
              }
            }
            require(
                members.stream()
                    .allMatch(member -> RESOURCE_ID.matcher(member.species()).matches()),
                "Category species must be namespaced identifiers");
            categories.put(normalized, members);
          } catch (RuntimeException exception) {
            diagnostics.add(
                new Diagnostic(source, "categories." + label + ": " + exception.getMessage()));
          }
        }
      }
    } catch (RuntimeException exception) {
      return new ParsedDocument(
          List.of(),
          Map.of(),
          List.of(
              new Diagnostic(
                  source,
                  exception.getMessage() == null ? "Invalid JSON" : exception.getMessage())));
    }
    duplicateSelectors(rules)
        .forEach(
            group ->
                diagnostics.add(
                    new Diagnostic(
                        source,
                        "Equal-priority duplicate selector: "
                            + group.stream()
                                .map(MusicRule::key)
                                .collect(Collectors.joining(", ")))));
    return new ParsedDocument(rules, categories, diagnostics);
  }

  static List<List<MusicRule>> duplicateSelectors(List<MusicRule> rules) {
    // Arrays.asList allows the nullable form; List.of does not.
    Map<List<?>, List<MusicRule>> groups = new LinkedHashMap<>();
    for (MusicRule rule : rules)
      groups
          .computeIfAbsent(
              Arrays.asList(
                  rule.type(),
                  rule.match(),
                  rule.form(),
                  rule.scope(),
                  rule.priority(),
                  rule.event()),
              key -> new ArrayList<>())
          .add(rule);
    return groups.values().stream().filter(group -> group.size() > 1).toList();
  }

  private static JsonObject objectValue(JsonElement value, String label) {
    require(value.isJsonObject(), label + " must be an object");
    return value.getAsJsonObject();
  }

  private static void checkFields(JsonObject obj, Set<String> allowed) {
    Set<String> unknown = new LinkedHashSet<>(obj.keySet());
    unknown.removeAll(allowed);
    require(unknown.isEmpty(), "Unknown fields: " + unknown);
  }

  private static String requiredString(JsonObject obj, String key) {
    String value = optionalString(obj, key, null);
    require(value != null, "Missing " + key);
    return value;
  }

  private static String optionalString(JsonObject obj, String key, String fallback) {
    JsonElement value = obj.get(key);
    if (value == null) return fallback;
    require(
        value.isJsonPrimitive()
            && value.getAsJsonPrimitive().isString()
            && !RuleStrings.trim(value.getAsString()).isEmpty(),
        key + " must be a nonempty string");
    return RuleStrings.trim(value.getAsString());
  }

  private static int optionalInt(JsonObject obj, String key, int fallback) {
    JsonElement value = obj.get(key);
    if (value == null) return fallback;
    require(
        value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(),
        key + " must be an integer");
    try {
      return Integer.parseInt(value.getAsString());
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(key + " must be a 32-bit integer");
    }
  }

  private static List<String> soundList(JsonObject obj, String key, boolean required) {
    JsonElement array = obj.get(key);
    if (array == null) {
      require(!required, "Missing " + key);
      return List.of();
    }
    require(
        array.isJsonArray() && !array.getAsJsonArray().isEmpty(),
        key + " must be a nonempty array of sound identifiers");
    List<String> sounds = new ArrayList<>();
    int index = 0;
    for (JsonElement entry : array.getAsJsonArray()) {
      require(
          entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString(),
          key + "[" + index + "] must be a string");
      String identifier = RuleStrings.trim(entry.getAsString());
      require(
          RESOURCE_ID.matcher(identifier).matches(),
          "Invalid sound identifier at " + key + "[" + index + "]: " + identifier);
      sounds.add(identifier);
      index++;
    }
    return sounds;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }
}
