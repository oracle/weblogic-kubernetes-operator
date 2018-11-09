// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

/** An interface for an object to configure a server in a test. */
@SuppressWarnings("UnusedReturnValue")
public interface ServerConfigurator {
  ServerConfigurator withDesiredState(String desiredState);

  ServerConfigurator withEnvironmentVariable(String name, String value);

  ServerConfigurator withServerStartState(String state);

  ServerConfigurator withServerStartPolicy(String startNever);

  ServerConfigurator withLivenessProbeSettings(
      Integer initialDelay, Integer timeout, Integer period);

  ServerConfigurator withReadinessProbeSettings(
      Integer initialDelay, Integer timeout, Integer period);

  ServerConfigurator withAdditionalVolume(String name, String path);

  ServerConfigurator withAdditionalVolumeMount(String name, String path);
}
