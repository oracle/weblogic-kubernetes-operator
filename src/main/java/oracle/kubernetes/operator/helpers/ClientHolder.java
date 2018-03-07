// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.apis.ApiextensionsV1beta1Api;
import io.kubernetes.client.apis.AuthenticationV1Api;
import io.kubernetes.client.apis.AuthorizationV1Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.apis.ExtensionsV1beta1Api;
import io.kubernetes.client.apis.VersionApi;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

public class ClientHolder {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final ClientHelper helper;

  private final ApiClient client;
  private ApiextensionsV1beta1Api apiextensionsApi = null;
  private CoreV1Api coreApi = null;
  private AuthorizationV1Api authzApi = null;
  private AuthenticationV1Api authApi = null;
  private VersionApi verApi = null;
  private CustomObjectsApi customApi = null;
  private ExtensionsV1beta1Api extensionsV1beta1Api = null;

  public ClientHolder(ClientHelper helper, ApiClient client) {
    this.helper = helper;
    this.client = client;
  }
  
  public CallBuilder callBuilder() {
    return new CallBuilder(this);
  }

  public ClientHelper getHelper() {
    return helper;
  }

  public ApiClient getApiClient() {
    return client;
  }

  public ApiextensionsV1beta1Api getApiExtensionClient() {
    if (apiextensionsApi == null) {
      LOGGER.fine(MessageKeys.CREATING_API_EXTENSION_CLIENT);
      apiextensionsApi = new ApiextensionsV1beta1Api(client);
    }
    return apiextensionsApi;
  }

  public CoreV1Api getCoreApiClient() {
    if (coreApi == null) {
      LOGGER.fine(MessageKeys.CREATING_COREAPI_CLIENT);
      coreApi = new CoreV1Api(client);
    }
    return coreApi;
  }

  public AuthorizationV1Api getAuthorizationApiClient() {
    if (authzApi == null) {
      authzApi = new AuthorizationV1Api(client);
    }
    return authzApi;
  }

  public AuthenticationV1Api getAuthenticationApiClient() {
    if (authApi == null) {
      authApi = new AuthenticationV1Api(client);
    }
    return authApi;
  }

  public VersionApi getVersionApiClient() {
    if (verApi == null) {
      verApi = new VersionApi(client);
    }
    return verApi;
  }

  public CustomObjectsApi getCustomObjectsApiClient() {
    if (customApi == null) {
      customApi = new CustomObjectsApi(client);
    }
    return customApi;
  }
  
  public ExtensionsV1beta1Api getExtensionsV1beta1ApiClient() {
    if (extensionsV1beta1Api == null) {
      extensionsV1beta1Api = new ExtensionsV1beta1Api(client);
    }
    return extensionsV1beta1Api;
    
  }
}
