// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

public interface ServerConfigurator {
  ServerConfigurator withNodePort(int nodePort);

  ServerConfigurator withDesiredState(String desiredState);

  ServerConfigurator withEnvironmentVariable(String name, String value);
}
