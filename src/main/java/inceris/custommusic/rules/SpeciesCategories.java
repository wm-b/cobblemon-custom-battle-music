package inceris.custommusic.rules;

import static inceris.custommusic.rules.RuleStrings.normalize;

import com.google.gson.*;
import inceris.custommusic.CustomBattleMusic;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Cobblemon does not transmit species/form labels. This catalog describes its pinned vanilla data.
 */
public final class SpeciesCategories {
  private SpeciesCategories() {}

  private static final class Catalog {
    private static final JsonObject DATA = load();

    private static JsonObject load() {
      var stream =
          Objects.requireNonNull(
              SpeciesCategories.class.getResourceAsStream(
                  "/assets/" + CustomBattleMusic.ASSET_NAMESPACE + "/species_categories.json"));
      try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        return JsonParser.parseReader(reader).getAsJsonObject();
      } catch (IOException exception) {
        throw new UncheckedIOException(exception);
      }
    }
  }

  public static Set<String> labels(String species, String form) {
    JsonObject entry = Catalog.DATA.getAsJsonObject(normalize(species));
    if (entry == null) return Set.of();
    JsonArray labels = form == null ? null : entry.getAsJsonArray(normalize(form));
    if (labels == null) labels = entry.getAsJsonArray("normal");
    if (labels == null) return Set.of();
    Set<String> result = new LinkedHashSet<>();
    labels.forEach(label -> result.add(label.getAsString()));
    return Set.copyOf(result);
  }
}
