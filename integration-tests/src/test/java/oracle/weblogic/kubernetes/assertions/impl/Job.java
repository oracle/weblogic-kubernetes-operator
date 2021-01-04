// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.concurrent.Callable;

public class Job {

  /**
   * Check a given job is completed.
   *
   * @param namespace name of the namespace in which to check for the job status
   * @param labelSelectors labels with which the job is decorated
   * @param jobName name of the job
   * @return true if complete otherwise false
   */
  public static Callable<Boolean> jobCompleted(String namespace, String labelSelectors, String jobName) {
    return () -> {
      return Kubernetes.isJobComplete(namespace, labelSelectors, jobName);
    };
  }

}
