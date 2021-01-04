// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.io.IOException;

/** This task creates the "readiness" indicator so that Kubernetes knows the Operator is ready. */
public class OperatorReady {

  private static final File readinessFile = new File("/operator/.ready");

  /**
   * Create the Operator readiness indicator.
   * @throws IOException if the readiness file does not exist
   */
  public static void create() throws IOException {
    if (!readinessFile.exists()) {
      readinessFile.createNewFile();
    }
  }
}
