// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

@SuppressWarnings("UnusedReturnValue")
public interface ClusterConfigurator {
  ClusterConfigurator withReplicas(int replicas);

  ClusterConfigurator withDesiredState(String state);

  ClusterConfigurator withEnvironmentVariable(String name, String value);
}
