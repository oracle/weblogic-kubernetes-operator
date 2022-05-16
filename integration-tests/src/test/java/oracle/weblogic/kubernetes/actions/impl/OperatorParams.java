// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.kubernetes.client.openapi.models.V1Container;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;

// All parameters needed to install Operator from test

public class OperatorParams {

  private static final String DOMAIN_NAMESPACES = "domainNamespaces";
  private static final String IMAGE = "image";
  private static final String SERVICE_ACCOUNT = "serviceAccount";
  private static final String EXTERNAL_REST_ENABLED = "externalRestEnabled";
  private static final String EXTERNAL_REST_IDENTITY_SECRET = "externalRestIdentitySecret";
  private static final String EXTERNAL_REST_HTTPS_PORT = "externalRestHttpsPort";
  private static final String IMAGE_PULL_POLICY = "imagePullPolicy";
  private static final String IMAGE_PULL_SECRETS = "imagePullSecrets";
  private static final String ELK_INTEGRATION_ENABLED = "elkIntegrationEnabled";
  private static final String ELASTICSEARCH_HOST = "elasticSearchHost";
  private static final String ELASTICSEARCH_PORT = "elasticSearchPort";
  private static final String JAVA_LOGGING_LEVEL = "javaLoggingLevel";
  private static final String LOGSTASH_IMAGE = "logStashImage";
  private static final String DOMAIN_NS_SELECTOR_STRATEGY = "domainNamespaceSelectionStrategy";
  private static final String DOMAIN_NS_LABEL_SELECTOR = "domainNamespaceLabelSelector";
  private static final String DOMAIN_NS_REG_EXP = "domainNamespaceRegExp";
  private static final String ENABLE_CLUSTER_ROLE_BINDING = "enableClusterRoleBinding";
  private static final String DOMAIN_PRESENCE_FAILURE_RETRY_MAX_COUNT = "domainPresenceFailureRetryMaxCount";
  private static final String DOMAIN_PRESENCE_FAILURE_RETRY_SECONDS = "domainPresenceFailureRetrySeconds";
  private static final String FEATURE_GATES = "featureGates";
  private static final String KUBERNETES_PLATFORM = "kubernetesPlatform";
  private static final String CREATE_LOGSTASH_CONFIGMAP = "createLogStashConfigMap";
  private static final String WEBHOOK_ONLY = "webhookOnly";

  // Adding some of the most commonly used params for now
  private List<String> domainNamespaces;
  private String image;
  private String serviceAccount;
  private boolean externalRestEnabled;
  private String externalRestIdentitySecret;
  private int externalRestHttpsPort = 0;
  private V1Container.ImagePullPolicyEnum imagePullPolicy;
  private Map<String, Object> imagePullSecrets;
  private HelmParams helmParams;
  private boolean elkIntegrationEnabled;
  private boolean enableClusterRoleBinding = false;
  private String elasticSearchHost;
  private int elasticSearchPort;
  private String javaLoggingLevel;
  private String logStashImage;
  private String domainNamespaceSelectionStrategy;
  private String domainNamespaceLabelSelector;
  private String domainNamespaceRegExp;
  private int domainPresenceFailureRetryMaxCount = 5;
  private int domainPresenceFailureRetrySeconds = 10;
  private String featureGates;
  private String kubernetesPlatform;
  private boolean createLogStashConfigMap = true;
  private boolean webhookOnly;

  public OperatorParams domainNamespaces(List<String> domainNamespaces) {
    this.domainNamespaces = domainNamespaces;
    return this;
  }

  public OperatorParams image(String image) {
    this.image = image;
    return this;
  }

  public OperatorParams serviceAccount(String serviceAccount) {
    this.serviceAccount = serviceAccount;
    return this;
  }

  public OperatorParams externalRestEnabled(boolean externalRestEnabled) {
    this.externalRestEnabled = externalRestEnabled;
    return this;
  }

  public OperatorParams externalRestHttpsPort(int externalRestHttpsPort) {
    this.externalRestHttpsPort = externalRestHttpsPort;
    return this;
  }

  public OperatorParams imagePullPolicy(V1Container.ImagePullPolicyEnum imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
    return this;
  }

  public OperatorParams enableClusterRoleBinding(boolean enableClusterRoleBinding) {
    this.enableClusterRoleBinding = enableClusterRoleBinding;
    return this;
  }

  public OperatorParams imagePullSecrets(Map<String, Object> imagePullSecrets) {
    this.imagePullSecrets = imagePullSecrets;
    return this;
  }

  public OperatorParams externalRestIdentitySecret(String externalRestIdentitySecret) {
    this.externalRestIdentitySecret = externalRestIdentitySecret;
    return this;
  }

  public OperatorParams helmParams(HelmParams helmParams) {
    this.helmParams = helmParams;
    return this;
  }

  public OperatorParams elkIntegrationEnabled(boolean elkIntegrationEnabled) {
    this.elkIntegrationEnabled = elkIntegrationEnabled;
    return this;
  }

  public OperatorParams elasticSearchHost(String elasticSearchHost) {
    this.elasticSearchHost = elasticSearchHost;
    return this;
  }

  public OperatorParams elasticSearchPort(int elasticSearchPort) {
    this.elasticSearchPort = elasticSearchPort;
    return this;
  }

  public OperatorParams javaLoggingLevel(String javaLoggingLevel) {
    this.javaLoggingLevel = javaLoggingLevel;
    return this;
  }

  public OperatorParams domainNamespaceLabelSelector(String domainNamespaceLabelSelector) {
    this.domainNamespaceLabelSelector = domainNamespaceLabelSelector;
    return this;
  }

  public OperatorParams domainNamespaceSelectionStrategy(String domainNamespaceSelectionStrategy) {
    this.domainNamespaceSelectionStrategy = domainNamespaceSelectionStrategy;
    return this;
  }

  public OperatorParams domainNamespaceRegExp(String domainNamespaceRegExp) {
    this.domainNamespaceRegExp = domainNamespaceRegExp;
    return this;
  }

  public OperatorParams logStashImage(String logStashImage) {
    this.logStashImage = logStashImage;
    return this;
  }

  public OperatorParams domainPresenceFailureRetryMaxCount(int domainPresenceFailureRetryMaxCount) {
    this.domainPresenceFailureRetryMaxCount = domainPresenceFailureRetryMaxCount;
    return this;
  }

  public OperatorParams domainPresenceFailureRetrySeconds(int domainPresenceFailureRetrySeconds) {
    this.domainPresenceFailureRetrySeconds = domainPresenceFailureRetrySeconds;
    return this;
  }

  public OperatorParams featureGates(String featureGates) {
    this.featureGates = featureGates;
    return this;
  }

  public OperatorParams kubernetesPlatform(String kubernetesPlatform) {
    this.kubernetesPlatform = kubernetesPlatform;
    return this;
  }

  public OperatorParams createLogStashConfigMap(boolean createLogStashConfigMap) {
    this.createLogStashConfigMap = createLogStashConfigMap;
    return this;
  }

  public String getServiceAccount() {
    return serviceAccount;
  }

  public HelmParams getHelmParams() {
    return helmParams;
  }

  public String getFeatureGates() {
    return featureGates;
  }

  public String getKubernetesPlatform() {
    return kubernetesPlatform;
  }
  
  public OperatorParams webHookOnly(boolean webhookOnly) {
    this.webhookOnly = webhookOnly;
    return this;
  }

  /**
   * Loads Helm values into a value map.
   * @return Map of values
   */
  public Map<String, Object> getValues() {
    Map<String, Object> values = new HashMap<>();
    values.put(DOMAIN_NAMESPACES, domainNamespaces);
    values.put(IMAGE, image);
    values.put(SERVICE_ACCOUNT, serviceAccount);

    values.put(EXTERNAL_REST_ENABLED, Boolean.valueOf(externalRestEnabled));
    values.put(EXTERNAL_REST_IDENTITY_SECRET, externalRestIdentitySecret);

    if (externalRestHttpsPort >= 0) {
      values.put(EXTERNAL_REST_HTTPS_PORT, Integer.valueOf(externalRestHttpsPort));
    }

    values.put(IMAGE_PULL_POLICY, Optional.ofNullable(imagePullPolicy).map(Object::toString).orElse(null));
    values.put(IMAGE_PULL_SECRETS, imagePullSecrets);
    values.put(ELK_INTEGRATION_ENABLED, Boolean.valueOf(elkIntegrationEnabled));
    values.put(ENABLE_CLUSTER_ROLE_BINDING, Boolean.valueOf(enableClusterRoleBinding));
    values.put(FEATURE_GATES, featureGates);

    if (elasticSearchHost != null) {
      values.put(ELASTICSEARCH_HOST, elasticSearchHost);
    }
    if (elasticSearchPort > 0) {
      values.put(ELASTICSEARCH_PORT, Integer.valueOf(elasticSearchPort));
    }
    if (javaLoggingLevel != null) {
      values.put(JAVA_LOGGING_LEVEL, javaLoggingLevel);
    }
    if (logStashImage != null) {
      values.put(LOGSTASH_IMAGE, logStashImage);
    }

    if (domainNamespaceLabelSelector != null) {
      values.put(DOMAIN_NS_LABEL_SELECTOR, domainNamespaceLabelSelector);
    }
    if (domainNamespaceSelectionStrategy != null) {
      values.put(DOMAIN_NS_SELECTOR_STRATEGY, domainNamespaceSelectionStrategy);
    }
    if (domainNamespaceRegExp != null) {
      values.put(DOMAIN_NS_REG_EXP, domainNamespaceRegExp);
    }
    if (domainPresenceFailureRetryMaxCount >= 0) {
      values.put(DOMAIN_PRESENCE_FAILURE_RETRY_MAX_COUNT,  domainPresenceFailureRetryMaxCount);
    }
    if (domainPresenceFailureRetrySeconds > 0) {
      values.put(DOMAIN_PRESENCE_FAILURE_RETRY_SECONDS, domainPresenceFailureRetrySeconds);
    }
    if (kubernetesPlatform != null) {
      values.put(KUBERNETES_PLATFORM, kubernetesPlatform);
    }
    if (webhookOnly) {
      values.put(WEBHOOK_ONLY, webhookOnly);
    }    

    values.put(CREATE_LOGSTASH_CONFIGMAP, createLogStashConfigMap);

    values.values().removeIf(Objects::isNull);
    return values;
  }
}
