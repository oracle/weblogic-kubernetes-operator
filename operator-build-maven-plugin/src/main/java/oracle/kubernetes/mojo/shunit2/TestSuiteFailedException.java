// Copyright (c) 2020, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.mojo.shunit2;

import java.io.Serial;

class TestSuiteFailedException extends RuntimeException {
  @Serial
  private static final long serialVersionUID  = 1L;

  public TestSuiteFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
