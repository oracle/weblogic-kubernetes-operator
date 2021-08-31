// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;

/**
 * All parameters needed to install Voyager ingress controller.
 */
public class VoyagerParams {

  // params used when installing Voyager
  private static final String CLOUD_PROVIDER = "cloudProvider";
  private static final String ENABLE_VALIDATING_WEBHOOK = "apiserver.enableValidatingWebhook";

  // Adding some of the most commonly used params for now
  private String cloudProvider;
  private boolean enableValidatingWebhook;
  private HelmParams helmParams;

  public VoyagerParams cloudProvider(String cloudProvider) {
    this.cloudProvider = cloudProvider;
    return this;
  }

  public VoyagerParams enableValidatingWebhook(boolean enableValidatingWebhook) {
    this.enableValidatingWebhook = enableValidatingWebhook;
    return this;
  }

  public VoyagerParams helmParams(HelmParams helmParams) {
    this.helmParams = helmParams;
    return this;
  }

  public HelmParams getHelmParams() {
    return helmParams;
  }

  /**
   * Loads Helm values into a value map.
   *
   * @return Map of values
   */
  public Map<String, Object> getValues() {
    Map<String, Object> values = new HashMap<>();

    if (cloudProvider != null && !cloudProvider.isEmpty()) {
      values.put(CLOUD_PROVIDER, cloudProvider);
    }

    values.put(ENABLE_VALIDATING_WEBHOOK, enableValidatingWebhook);

    values.values().removeIf(Objects::isNull);
    return values;
  }
}
