// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.utils;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Array;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class OperatorUtils {

  // 2K chars (4K bytes)
  private static final int DEFAULT_BUF_SIZE = 0x800;

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
   * Create a Map using the elements from the given map, with their keys sorted
   * using the sorted name as returned by {@link #getSortingString(String)}.
   *
   * @param map Map containing elements to be sorted by the keys
   * @param <T> Type of map entries
   * @return A sorted Map containing the elements from the give map
   */
  public static <T> Map<String, T> createSortedMap(Map<String, T> map) {
    if (map == null) {
      return Collections.emptyMap();
    }
    return map.entrySet()
        .stream()
        .sorted(Comparator
            .comparing((Entry<String, T> entry) -> getSortingString(entry.getKey())))
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            Map.Entry::getValue,
            (oldValue, newValue) -> oldValue, LinkedHashMap::new));
  }

  /**
   * Compare the given 2 Strings using the sorted name as returned by {@link #getSortingString(String)}.
   *
   * @param str1 First string for comparison
   * @param str2 Second string for comparison
   * @return a negative integer, zero, or a positive integer as the sorting name of str1 
   *     is less than, equal to, or greater than the sorting name of str2.
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
   * <p>Handles up to 20 digits... - otherwise it doesn't zero fill...
   * @param orig original value
   * @return munged value
   */
  public static String getSortingString(String orig) {
    StringBuilder ret = new StringBuilder();
    StringBuilder word = new StringBuilder();
    char lastCh = 0;
    for (char ch : orig.toCharArray()) {
      if (word.length() != 0
          && Character.isDigit(ch) ^ Character.isDigit(lastCh)) {
        ret.append(getSortingWord(word.toString()));
        word = new StringBuilder();
      }
      word.append(ch);
      lastCh = ch;
    }
    ret.append(getSortingWord(word.toString()));
    return ret.toString();
  }

  private static String getSortingWord(String word) {
    if (word.length() == 0) {
      return word;
    }
    if (Character.isDigit(word.charAt(0))) {
      StringBuilder wordBuilder = new StringBuilder(word);
      for (int i = wordBuilder.length(); i < 20; i++) {
        wordBuilder.insert(0, '0');
      }
      word = wordBuilder.toString();
    }
    return word;
  }

  /**
   * Checks if String is null or empty.
   * @param str String
   * @return true, if the String is null or empty
   */
  public static boolean isNullOrEmpty(String str) {
    return str == null || str.isEmpty();
  }

  /**
   * Checks if array is null or empty.
   * @param array Array
   * @return true, if the array is null or empty
   */
  public static boolean isNullOrEmpty(Object[] array) {
    return array == null || Array.getLength(array) == 0;
  }

  /**
   * Checks if map is null or empty.
   * @param map Map
   * @return true, if the map is null or empty
   */
  public static boolean isNullOrEmpty(Map<?,?> map) {
    return map == null || map.isEmpty();
  }

  /**
   * Converts empty String to null.
   * @param str String
   * @return null, if the String is empty or null; otherwise, returns the original value
   */
  public static String emptyToNull(String str) {
    if (str != null && !str.isEmpty()) {
      return str;
    }
    return null;
  }

  /**
   * Copies characters from a reader stream to a string and does not close the stream.
   *
   * @param from the reader stream
   * @return string containing all the characters
   * @throws IOException if an I/O error occurs
   */
  public static String toString(Reader from) throws IOException {
    return toStringBuilder(from).toString();
  }

  private static StringBuilder toStringBuilder(Reader from) throws IOException {
    StringBuilder sb = new StringBuilder();
    char[] buf = new char[DEFAULT_BUF_SIZE];
    int numRead;
    while ((numRead = from.read(buf)) != -1) {
      sb.append(buf, 0, numRead);
    }
    return sb;
  }

}
