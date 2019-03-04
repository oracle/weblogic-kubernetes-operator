// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.utils;

import java.util.List;

public class TestUtils {

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
}
