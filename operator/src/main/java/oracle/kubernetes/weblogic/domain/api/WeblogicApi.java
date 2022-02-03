// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.api;

import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiCallback;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import okhttp3.Call;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainList;

import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_GROUP;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_PLURAL;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_VERSION;

public class WeblogicApi extends CustomObjectsApi {

  public WeblogicApi(ApiClient apiClient) {
    super(apiClient);
  }

  /**
   * Asynchronously read domain.
   *
   * @param name      name
   * @param namespace namespace
   * @param callback  callback
   * @return call
   * @throws ApiException on failure
   */
  public Call getNamespacedDomainAsync(String name, String namespace, ApiCallback<Domain> callback)
      throws ApiException {

    // TEST
    System.out.println("TEST: entering getNamespacedDomainAsync");

    return getNamespacedCustomObjectAsync(DOMAIN_GROUP, DOMAIN_VERSION, namespace,
        DOMAIN_PLURAL, name, new DomainApiCallbackWrapper(callback));
  }

  /**
   * Generate call to list domains.
   *
   * @param namespace       namespace
   * @param pretty          pretty flag
   * @param cont            continuation
   * @param fieldSelector   field selector
   * @param labelSelector   label selector
   * @param limit           limit
   * @param resourceVersion resource version
   * @param timeoutSeconds  timeout
   * @param watch           if watch
   * @param callback        callback
   * @return call
   * @throws ApiException on failure
   */
  public Call listNamespacedDomainCall(
      String namespace,
      String pretty,
      String cont,
      String fieldSelector,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch,
      ApiCallback<DomainList> callback)
      throws ApiException {

    // TEST
    System.out.println("TEST: entering listNamespacedDomainCall");

    return listNamespacedCustomObjectCall(DOMAIN_GROUP, DOMAIN_VERSION, namespace, DOMAIN_PLURAL,
        pretty, null, cont, fieldSelector, labelSelector, limit, resourceVersion, null,
        timeoutSeconds, watch, new DomainListApiCallbackWrapper(callback));
  }

  /**
   * List domains.
   *
   * @param namespace       namespace
   * @param pretty          pretty flag
   * @param cont            continuation
   * @param fieldSelector   field selector
   * @param labelSelector   label selector
   * @param limit           limit
   * @param resourceVersion resource version
   * @param timeoutSeconds  timeout
   * @param watch           if watch
   * @return domain list
   * @throws ApiException on failure
   */
  public DomainList listNamespacedDomain(
      String namespace,
      String pretty,
      String cont,
      String fieldSelector,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch)
      throws ApiException {

    // TEST
    System.out.println("TEST: entering listNamespacedDomain");

    return toDomainList(listNamespacedCustomObject(DOMAIN_GROUP, DOMAIN_VERSION, namespace, DOMAIN_PLURAL, pretty,
        null, cont, fieldSelector, labelSelector, limit, resourceVersion, null,
        timeoutSeconds, watch));
  }

  /**
   * Asynchronously list domains.
   *
   * @param namespace       namespace
   * @param pretty          pretty flag
   * @param cont            continuation
   * @param fieldSelector   field selector
   * @param labelSelector   label selector
   * @param limit           limit
   * @param resourceVersion resource version
   * @param timeoutSeconds  timeout
   * @param watch           watch
   * @param callback        callback
   * @return call
   * @throws ApiException on failure
   */
  public Call listNamespacedDomainAsync(
      String namespace,
      String pretty,
      String cont,
      String fieldSelector,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch,
      ApiCallback<DomainList> callback)
      throws ApiException {

    // TEST
    System.out.println("TEST: entering listNamespacedDomainAsync");

    return listNamespacedCustomObjectCall(DOMAIN_GROUP, DOMAIN_VERSION, namespace, DOMAIN_PLURAL,
        pretty, null, cont, fieldSelector, labelSelector, limit, resourceVersion, null,
        timeoutSeconds, watch, new DomainListApiCallbackWrapper(callback));
  }

  /**
   * Patch domain.
   *
   * @param name      name
   * @param namespace namespace
   * @param body      patch
   * @return domain
   * @throws ApiException on failure
   */
  public Domain patchNamespacedDomain(String name, String namespace, V1Patch body)
      throws ApiException {

    // TEST
    System.out.println("TEST: entering patchNamespacedDomain");

    return toDomain(patchNamespacedCustomObject(DOMAIN_GROUP, DOMAIN_VERSION, namespace, DOMAIN_PLURAL,
        name, body, null, null, null));
  }

  /**
   * Asynchronously patch domain.
   *
   * @param name      name
   * @param namespace namespace
   * @param body      patch
   * @param callback  callback
   * @return call
   * @throws ApiException on failure
   */
  public Call patchNamespacedDomainAsync(
      String name, String namespace, V1Patch body, ApiCallback<Domain> callback)
      throws ApiException {

    // TEST
    System.out.println("TEST: entering patchNamespacedDomainAsync");

    return patchNamespacedCustomObjectAsync(DOMAIN_GROUP, DOMAIN_VERSION, namespace, DOMAIN_PLURAL,
        name, body, null, null, null, new DomainApiCallbackWrapper(callback));
  }

  /**
   * Replace domain.
   *
   * @param name      name
   * @param namespace namespace
   * @param body      domain
   * @return domain
   * @throws ApiException on failure
   */
  public Domain replaceNamespacedDomain(String name, String namespace, Domain body)
      throws ApiException {

    // TEST
    System.out.println("TEST: entering replaceNamespacedDomain");

    return toDomain(replaceNamespacedCustomObject(DOMAIN_GROUP, DOMAIN_VERSION, namespace, DOMAIN_PLURAL,
        name, body, null, null));
  }

  /**
   * Asynchronously replace domain.
   *
   * @param name      name
   * @param namespace namespace
   * @param body      domain
   * @param callback  callback
   * @return call
   * @throws ApiException on failure
   */
  public Call replaceNamespacedDomainAsync(
      String name, String namespace, Domain body, ApiCallback<Domain> callback)
      throws ApiException {

    // TEST
    System.out.println("TEST: entering replaceNamespacedDomainAsync");

    return replaceNamespacedCustomObjectAsync(DOMAIN_GROUP, DOMAIN_VERSION, namespace, DOMAIN_PLURAL,
        name, body, null, null, new DomainApiCallbackWrapper(callback));
  }

  /**
   * Replace domain status.
   *
   * @param name      name
   * @param namespace namespace
   * @param body      domain
   * @return domain
   * @throws ApiException on failure
   */
  public Domain replaceNamespacedDomainStatus(String name, String namespace, Domain body)
      throws ApiException {

    // TEST
    System.out.println("TEST: entering replaceNamespacedDomainStatus");

    return toDomain(replaceNamespacedCustomObjectStatus(DOMAIN_GROUP, DOMAIN_VERSION, namespace, DOMAIN_PLURAL,
        name, body, null, null));
  }

  /**
   * Asynchronously replace domain status.
   *
   * @param name      name
   * @param namespace namespace
   * @param body      domain
   * @param callback  callback
   * @return call
   * @throws ApiException on failure
   */
  public Call replaceNamespacedDomainStatusAsync(
      String name, String namespace, Domain body, ApiCallback<Domain> callback)
      throws ApiException {

    // TEST
    System.out.println("TEST: entering replaceNamespacedDomainStatusAsync");

    return replaceNamespacedCustomObjectStatusAsync(DOMAIN_GROUP, DOMAIN_VERSION, namespace, DOMAIN_PLURAL,
        name, body, null, null, new DomainApiCallbackWrapper(callback));
  }

  private class DomainApiCallbackWrapper implements ApiCallback<Object> {
    private final ApiCallback<Domain> domainApiCallback;

    public DomainApiCallbackWrapper(ApiCallback<Domain> domainApiCallback) {
      this.domainApiCallback = domainApiCallback;
    }

    @Override
    public void onFailure(ApiException e, int i, Map<String, List<String>> map) {

      // TEST
      System.out.println("TEST: Domain onFailure");
      e.printStackTrace();

      domainApiCallback.onFailure(e, i, map);
    }

    @Override
    public void onSuccess(Object o, int i, Map<String, List<String>> map) {

      // TEST
      System.out.println("TEST: onSuccess, o class: " + o.getClass() + ", o: " + o.toString());

      domainApiCallback.onSuccess(toDomain(o), i, map);
    }

    @Override
    public void onUploadProgress(long l, long l1, boolean b) {
      domainApiCallback.onUploadProgress(l, l1, b);
    }

    @Override
    public void onDownloadProgress(long l, long l1, boolean b) {
      domainApiCallback.onDownloadProgress(l, l1, b);
    }
  }

  private class DomainListApiCallbackWrapper implements ApiCallback<Object> {
    private final ApiCallback<DomainList> domainListApiCallback;

    public DomainListApiCallbackWrapper(ApiCallback<DomainList> domainListApiCallback) {
      this.domainListApiCallback = domainListApiCallback;
    }

    @Override
    public void onFailure(ApiException e, int i, Map<String, List<String>> map) {

      // TEST
      System.out.println("TEST: DomainList onFailure");
      e.printStackTrace();

      domainListApiCallback.onFailure(e, i, map);
    }

    @Override
    public void onSuccess(Object o, int i, Map<String, List<String>> map) {

      // TEST
      System.out.println("TEST: onSuccess, o class: " + o.getClass() + ", o: " + o.toString());

      domainListApiCallback.onSuccess(toDomainList(o), i, map);
    }

    @Override
    public void onUploadProgress(long l, long l1, boolean b) {
      domainListApiCallback.onUploadProgress(l, l1, b);
    }

    @Override
    public void onDownloadProgress(long l, long l1, boolean b) {
      domainListApiCallback.onDownloadProgress(l, l1, b);
    }
  }

  private JsonElement convertToJson(Object o) {
    Gson gson = getApiClient().getJSON().getGson();
    return gson.toJsonTree(o);
  }

  private Domain toDomain(Object o) {
    if (o == null) {
      return null;
    }

    // TEST
    System.out.println("TEST: toDomain, o class: " + o.getClass() + ", o: " + o.toString());


    Domain d = null;
    try {
      d = getApiClient().getJSON().getGson().fromJson(convertToJson(o), Domain.class);
    } catch (Throwable t) {

      // TEST
      System.out.println("TEST: failed in toDomain");
      t.printStackTrace();

    }

    return d;
  }

  private DomainList toDomainList(Object o) {
    if (o == null) {
      return null;
    }

    // TEST
    System.out.println("TEST: toDomainList, o class: " + o.getClass() + ", o: " + o.toString());

    DomainList dl = null;
    try {
      dl = getApiClient().getJSON().getGson().fromJson(convertToJson(o), DomainList.class);
    } catch (Throwable t) {

      // TEST
      System.out.println("TEST: failed in toDomainList");
      t.printStackTrace();

    }

    return dl;
  }
}
