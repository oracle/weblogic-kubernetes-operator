// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.util.Config;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientPool extends Pool<ApiClient> {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final ClientPool SINGLETON = new ClientPool();

  private final AtomicBoolean first = new AtomicBoolean(true);

  public static ClientPool getInstance() {
    return SINGLETON;
  }

  private ClientPool() {
  }

  @Override
  protected ApiClient create() {
    return getApiClient();
  }

  private ApiClient getApiClient() {
    LOGGER.entering();

    ApiClient client = null;
    LOGGER.fine(MessageKeys.CREATING_API_CLIENT);
    try {
      client = Config.defaultClient();
      if (first.getAndSet(false)) {
        Configuration.setDefaultApiClient(client);
      }
    } catch (Throwable e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }
    LOGGER.info(MessageKeys.K8S_MASTER_URL, client != null ? client.getBasePath() : null);

    // Temporarily set a custom Gson for secret support
    // TODO:
    SecretHelper.addCustomGsonToClient(client);

    // TEST
    client.getHttpClient().setReadTimeout(5, TimeUnit.MINUTES);
    
    LOGGER.exiting(client);
    return client;
  }

}