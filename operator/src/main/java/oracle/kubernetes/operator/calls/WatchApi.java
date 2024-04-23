// Copyright (c) 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.Watchable;
import io.kubernetes.client.util.generic.options.ListOptions;

public interface WatchApi<A extends KubernetesObject> {

  /**
   * Create watch.
   * @param listOptions the list options
   * @return the watchable
   * @throws ApiException thrown on failure
   */
  Watchable<A> watch(final ListOptions listOptions) throws ApiException;

  /**
   * Create watch.
   * @param namespace the namespace
   * @param listOptions the list options
   * @return the watchable
   * @throws ApiException thrown on failure
   */
  Watchable<A> watch(String namespace, final ListOptions listOptions) throws ApiException;
}
