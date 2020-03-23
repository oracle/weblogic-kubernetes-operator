// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.utils;

import java.util.List;

public class OperatorUtils {
  /**
   * Converts a list of strings to a comma-separated list, using "and" for the last item.
   *
   * @param list the list to convert
   * @return the resultant string
   */
  public static String joinListGrammatically(final List<String> list) {
    return list.size() > 1
        ? String.join(", ", list.subList(0, list.size() - 1))
            .concat(String.format("%s and ", list.size() > 2 ? "," : ""))
            .concat(list.get(list.size() - 1))
        : list.get(0);
  }

  /**
   * Compare the 'numero lexi sorting name' as defined in {@link #getSortingString(String)} of the
   * given 2 Strings.
   *
   * @param str1 First string for comparison
   * @param str2 Second string for comparison
   * @return a negative integer, zero, or a positive integer as the sorting name of str1 * is less
   *     than, equal to, or greater than the sorting name of str2.
   */
  public static int compareSortingStrings(String str1, String str2) {
    if (str1 == null || str2 == null) {
      if (str2 != null) {
        return -1;
      } else if (str1 != null) {
        return 1;
      }
      return 0;
    }
    return getSortingString(str1).compareTo(getSortingString(str2));
  }

  /**
   * The 'numero lexi sorting name' is a munged version of a
   * string that 'zero fills' its numeric portions. This can
   * be used to ensure "member2foo" sorts before "member12foo"
   * as these would munge to "member000002foo" and "member000012foo".
   *
   * <p>Handles up to 6 digits... - otherwise it doesn't zero fill...
   */
  public static String getSortingString(String orig) {
    String ret = "";
    String word = "";
    char lastCh = 0;
    for (char ch : orig.toCharArray()) {
      if (word.length() != 0
          && Character.isDigit(ch) ^ Character.isDigit(lastCh)) {
        ret += getSortingWord(word);
        word = "";
      }
      word += ch;
      lastCh = ch;
    }
    ret += getSortingWord(word);
    return ret;
  }

  private static String getSortingWord(String word) {
    if (word.length() == 0) {
      return word;
    }
    if (Character.isDigit(word.charAt(0))) {
      for (int i = word.length(); i < 6; i++) {
        word = '0' + word;
      }
    }
    return word;
  }
}
