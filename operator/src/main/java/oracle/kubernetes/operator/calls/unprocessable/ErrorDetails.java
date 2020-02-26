// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls.unprocessable;

import java.util.Arrays;

class ErrorDetails {
  private String name;
  private String kind;
  private Cause[] causes = new Cause[0];

  Cause[] getCauses() {
    return causes;
  }

  void addCause(Cause cause) {
    int oldLength = causes.length;
    causes = Arrays.copyOf(causes, oldLength + 1);
    causes[oldLength] = cause;
  }
}
