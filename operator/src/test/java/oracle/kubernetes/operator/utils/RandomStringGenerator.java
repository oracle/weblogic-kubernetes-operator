// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.ArrayList;
import java.util.List;

/** A class which generates random strings, guaranteeing that no two will be equal. */
public class RandomStringGenerator {
  private List<String> previousStrings = new ArrayList<>();

  public String getUniqueString() {
    String result;

    do {
      result = Double.toString(Math.random());
    } while (previousStrings.contains(result));

    previousStrings.add(result);
    return result;
  }
}
