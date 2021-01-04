// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.mojo.shunit2;

class TestSuiteFailedException extends RuntimeException {

  public TestSuiteFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
