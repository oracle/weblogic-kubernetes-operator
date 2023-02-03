// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

import io.kubernetes.client.openapi.models.V1EnvVar;

/** An interface for an object to configure a introspector job pod in a test. */
@SuppressWarnings("UnusedReturnValue")
public interface IntrospectorJobPodConfigurator {
  IntrospectorJobPodConfigurator withEnvironmentVariable(String name, String value);

  IntrospectorJobPodConfigurator withEnvironmentVariable(V1EnvVar envVar);

  /**
   * Add a resource requirement at server level. The requests for memory are measured in bytes. You
   * can express memory as a plain integer or as a fixed-point integer using one of these suffixes:
   * E, P, T, G, M, K. You can also use the power-of-two equivalents: Ei, Pi, Ti, Gi, Mi, Ki. The
   * requests for cpu are measured in cpu units and can be expressed in millicores i.e. 100m is the
   * same as 0.1
   *
   * @param resource the resource to be added as requirement cpu or memory
   * @param quantity the quantity required for the resource
   * @return this object
   */
  IntrospectorJobPodConfigurator withRequestRequirement(String resource, String quantity);

  /**
   * Add a resource limit at server level, the requests for memory are measured in bytes. You can
   * express memory as a plain integer or as a fixed-point integer using one of these suffixes: E,
   * P, T, G, M, K. You can also use the power-of-two equivalents: Ei, Pi, Ti, Gi, Mi, Ki. The
   * requests for cpu are measured in cpu units and can be expressed in millicores i.e. 100m is the
   * same as 0.1
   *
   * @param resource the resource to be added as requirement cpu or memory
   * @param quantity the quantity required for the resource
   * @return this object
   */
  IntrospectorJobPodConfigurator withLimitRequirement(String resource, String quantity);
}
