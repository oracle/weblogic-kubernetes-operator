// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.Random;

public class UniqueName {
  public static Random random = new Random(System.currentTimeMillis());

  /**
   * Generates a "unique" name by choosing a random name from
   * 26^6 possible combinations.
   *
   * @param prefix Name prefix
   * @return name
   */
  public static String uniqueName(String prefix) {
    char[] nsName = new char[6];
    for (int i = 0; i < nsName.length; i++) {
      nsName[i] = (char) (random.nextInt(25) + (int) 'a');
    }
    return prefix + new String(nsName);
  }

}
