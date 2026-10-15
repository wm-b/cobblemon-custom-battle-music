package inceris.custommusic.rules;

import java.util.Locale;

public final class RuleStrings {
  private RuleStrings() {}

  public static String normalize(String value) {
    return trim(value).toLowerCase(Locale.ROOT);
  }

  /** Preserve the rule format's Unicode whitespace handling, including nonbreaking spaces. */
  public static String trim(String value) {
    int start = 0, end = value.length();
    while (start < end && isWhitespace(value.charAt(start))) start++;
    while (end > start && isWhitespace(value.charAt(end - 1))) end--;
    return value.substring(start, end);
  }

  private static boolean isWhitespace(char value) {
    return Character.isWhitespace(value) || Character.isSpaceChar(value);
  }
}
