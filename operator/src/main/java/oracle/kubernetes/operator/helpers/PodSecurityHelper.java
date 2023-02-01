// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Optional;

import io.kubernetes.client.openapi.models.V1Capabilities;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1SeccompProfile;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import oracle.kubernetes.operator.tuning.TuningParameters;

public class PodSecurityHelper {

  private PodSecurityHelper() {
  }

  /**
   * Default pod security context conforming to restricted.
   * @return Default pod security context
   */
  public static V1PodSecurityContext getDefaultPodSecurityContext() {
    return new V1PodSecurityContext().seccompProfile(
        new V1SeccompProfile().type("RuntimeDefault"));
  }

  /**
   * Default container security context conforming to restricted.
   * @return Default container security context.
   */
  public static V1SecurityContext getDefaultContainerSecurityContext() {
    V1SecurityContext context = new V1SecurityContext()
        .runAsNonRoot(true)
        .privileged(false).allowPrivilegeEscalation(false)
        .capabilities(new V1Capabilities().addDropItem("ALL"));

    Optional.ofNullable(TuningParameters.getInstance()).ifPresent(instance -> {
      if (!"OpenShift".equalsIgnoreCase(instance.getKubernetesPlatform())) {
        context.runAsUser(1000L);
      }
    });
    return context;
  }

}
