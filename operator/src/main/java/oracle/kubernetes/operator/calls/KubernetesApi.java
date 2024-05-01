// Copyright (c) 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import java.util.function.Function;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.options.CreateOptions;
import io.kubernetes.client.util.generic.options.DeleteOptions;
import io.kubernetes.client.util.generic.options.GetOptions;
import io.kubernetes.client.util.generic.options.ListOptions;
import io.kubernetes.client.util.generic.options.PatchOptions;
import io.kubernetes.client.util.generic.options.UpdateOptions;

public interface KubernetesApi<A extends KubernetesObject, L extends KubernetesListObject> {

  /**
   * Get Kubernetes API response.
   *
   * @param name the name
   * @return the Kubernetes API response
   */
  default KubernetesApiResponse<A> get(String name) {
    return get(name, new GetOptions());
  }

  /**
   * Get Kubernetes API response.
   *
   * @param name the name
   * @param getOptions the get options
   * @return the Kubernetes API response
   */
  KubernetesApiResponse<A> get(String name, final GetOptions getOptions);

  /**
   * Get Kubernetes API response under the namespace.
   *
   * @param namespace the namespace
   * @param name the name
   * @return the Kubernetes API response
   */
  default KubernetesApiResponse<A> get(String namespace, String name) {
    return get(namespace, name, new GetOptions());
  }

  /**
   * Get Kubernetes API response.
   *
   * @param namespace the namespace
   * @param name the name
   * @param getOptions the get options
   * @return the Kubernetes API response
   */
  KubernetesApiResponse<A> get(String namespace, String name, final GetOptions getOptions);

  /**
   * List Kubernetes API response cluster-scoped.
   *
   * @return the Kubernetes API response
   */
  default KubernetesApiResponse<L> list() {
    return list(new ListOptions());
  }

  /**
   * List Kubernetes API response.
   *
   * @param listOptions the list options
   * @return the Kubernetes API response
   */
  KubernetesApiResponse<L> list(final ListOptions listOptions);

  /**
   * List Kubernetes API response under the namespace.
   *
   * @param namespace the namespace
   * @return the Kubernetes API response
   */
  default KubernetesApiResponse<L> list(String namespace) {
    return list(namespace, new ListOptions());
  }

  /**
   * List Kubernetes API response.
   *
   * @param namespace the namespace
   * @param listOptions the list options
   * @return the Kubernetes API response
   */
  KubernetesApiResponse<L> list(String namespace, final ListOptions listOptions);

  /**
   * Create Kubernetes API response, if the namespace in the object is present, it will send a
   * namespace-scoped requests, vice versa.
   *
   * @param object the object
   * @return the Kubernetes API response
   */
  default KubernetesApiResponse<A> create(A object) {
    return create(object, new CreateOptions());
  }

  /**
   * Create Kubernetes API response.
   *
   * @param object the object
   * @param createOptions the create options
   * @return the Kubernetes API response
   */
  KubernetesApiResponse<A> create(A object, final CreateOptions createOptions);

  /**
   * Create Kubernetes API response.
   *
   * @param namespace the namespace
   * @param object the object
   * @param createOptions the create options
   * @return the Kubernetes API response
   */
  KubernetesApiResponse<A> create(
      String namespace, A object, final CreateOptions createOptions);

  /**
   * Create Kubernetes API response, if the namespace in the object is present, it will send a
   * namespace-scoped requests, vice versa.
   *
   * @param object the object
   * @return the Kubernetes API response
   */
  default KubernetesApiResponse<A> update(A object) {
    return update(object, new UpdateOptions());
  }

  /**
   * Update Kubernetes API response.
   *
   * @param object the object
   * @param updateOptions the update options
   * @return the Kubernetes API response
   */
  KubernetesApiResponse<A> update(A object, final UpdateOptions updateOptions);

  /**
   * Create Kubernetes API response, if the namespace in the object is present, it will send a
   * namespace-scoped requests, vice versa.
   *
   * @param object the object
   * @param status function to extract the status from the object
   * @return the Kubernetes API response
   */
  default KubernetesApiResponse<A> updateStatus(
      A object, Function<A, Object> status) {
    return updateStatus(object, status, new UpdateOptions());
  }

  /**
   * Update status of Kubernetes API response.
   *
   * @param object the object
   * @param status function to extract the status from the object
   * @param updateOptions the update options
   * @return the Kubernetes API response
   */
  KubernetesApiResponse<A> updateStatus(
      A object, Function<A, Object> status, final UpdateOptions updateOptions);

  /**
   * Patch Kubernetes API response.
   *
   * @param name the name
   * @param patchType the patch type, supported values defined in V1Patch
   * @param patch the string patch content
   * @return the Kubernetes API response
   */
  default KubernetesApiResponse<A> patch(String name, String patchType, V1Patch patch) {
    return patch(name, patchType, patch, new PatchOptions());
  }

  /**
   * Patch Kubernetes API response.
   *
   * @param name the name
   * @param patchType the patch type
   * @param patch the patch
   * @param patchOptions the patch options
   * @return the Kubernetes API response
   */
  KubernetesApiResponse<A> patch(
      String name, String patchType, V1Patch patch, final PatchOptions patchOptions);

  /**
   * Patch Kubernetes API response under the namespace.
   *
   * @param namespace the namespace
   * @param name the name
   * @param patchType the patch type, supported values defined in V1Patch
   * @param patch the string patch content
   * @return the Kubernetes API response
   */
  default KubernetesApiResponse<A> patch(
      String namespace, String name, String patchType, V1Patch patch) {
    return patch(namespace, name, patchType, patch, new PatchOptions());
  }

  /**
   * Patch Kubernetes API response.
   *
   * @param namespace the namespace
   * @param name the name
   * @param patchType the patch type
   * @param patch the patch
   * @param patchOptions the patch options
   * @return the Kubernetes API response
   */
  KubernetesApiResponse<A> patch(
      String namespace,
      String name,
      String patchType,
      V1Patch patch,
      final PatchOptions patchOptions);

  /**
   * Delete Kubernetes API response.
   *
   * @param name the name
   * @return the Kubernetes API response
   */
  default KubernetesApiResponse<A> delete(String name) {
    return delete(name, new DeleteOptions());
  }

  /**
   * Delete Kubernetes API response.
   *
   * @param name the name
   * @param deleteOptions the delete options
   * @return the Kubernetes API response
   */
  KubernetesApiResponse<A> delete(String name, final DeleteOptions deleteOptions);

  /**
   * Delete Kubernetes API response under the namespace.
   *
   * @param namespace the namespace
   * @param name the name
   * @return the Kubernetes API response
   */
  default KubernetesApiResponse<A> delete(String namespace, String name) {
    return delete(namespace, name, new DeleteOptions());
  }

  /**
   * Delete Kubernetes API response.
   *
   * @param namespace the namespace
   * @param name the name
   * @param deleteOptions the delete options
   * @return the Kubernetes API response
   */
  KubernetesApiResponse<A> delete(
      String namespace, String name, final DeleteOptions deleteOptions);

  /**
   * Delete collection Kubernetes API response.
   *
   * @param namespace the namespace
   * @param listOptions the list options
   * @param deleteOptions the delete options
   * @return the Kubernetes API response
   */
  KubernetesApiResponse<RequestBuilder.V1StatusObject> deleteCollection(
      String namespace, final ListOptions listOptions, final DeleteOptions deleteOptions);

  /**
   * Pod logs Kubernetes API response.
   * @param namespace the namespace
   * @param name the pod name
   * @param container the container name
   * @return the Kubernetes API response
   */
  KubernetesApiResponse<RequestBuilder.StringObject> logs(String namespace, String name, String container);

  /**
   * Version code Kubernetes API response.
   * @return the Kubernetes API response
   */
  KubernetesApiResponse<RequestBuilder.VersionInfoObject> getVersionCode();
}
