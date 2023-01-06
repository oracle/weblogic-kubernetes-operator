// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

public interface PodAwaiterStepFactory {
  /**
   * Waits until the Pod is Ready.
   *
   * @param pod Pod to watch
   * @param next Next processing step once Pod is ready
   * @return Asynchronous step
   */
  Step waitForReady(V1Pod pod, Step next);

  /**
   * Waits until the Pod with given name is Ready.
   *
   * @param podName Name of the Pod to watch
   * @param next Next processing step once Pod is ready
   * @return Asynchronous step
   */
  Step waitForReady(String podName, Step next);

  /**
   * Waits until the Pod is deleted.
   *
   * @param pod Pod to watch
   * @param next Next processing step once Pod is deleted
   * @return Asynchronous step
   */
  Step waitForDelete(V1Pod pod, Step next);

  /**
   * Waits until the Pod server is shut down.
   *
   * @param serverName Pod Server name to watch
   * @param domain Domain resource
   * @param next Next processing step once Pod is shut down
   * @return Asynchronous step
   */
  Step waitForServerShutdown(String serverName, DomainResource domain, Step next);
}
