// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.Objects;

import org.apache.commons.codec.binary.Base64;

public class OperatorValues {
  public static final String JAVA_LOGGING_LEVEL_SEVERE = "SEVERE";
  public static final String JAVA_LOGGING_LEVEL_WARNING = "WARNING";
  public static final String JAVA_LOGGING_LEVEL_INFO = "INFO";
  public static final String JAVA_LOGGING_LEVEL_CONFIG = "CONFIG";
  public static final String JAVA_LOGGING_LEVEL_FINE = "FINE";
  public static final String JAVA_LOGGING_LEVEL_FINER = "FINER";
  public static final String JAVA_LOGGING_LEVEL_FINEST = "FINEST";
  public static final String IMAGE_PULL_POLICY_IF_NOT_PRESENT = "IfNotPresent";
  public static final String IMAGE_PULL_POLICY_ALWAYS = "Always";
  public static final String IMAGE_PULL_POLICY_NEVER = "Never";
  private static final String EXTERNAL_CUSTOM_CERT_PEM = "test-external-custom-certificate-pem";
  private static final String EXTERNAL_CUSTOM_KEY_PEM = "test-external-custom-private-key-pem";
  private String version = "";
  private String dedicated = "";
  private String serviceAccount = "";
  private String namespace = "";
  private String domainNamespaceSelectionStrategy = "";
  private String domainNamespaces = "";
  private String domainNamespaceLabelSelector = "";
  private String domainNamespaceRegExp = "";
  private String weblogicOperatorImage = "";
  private String weblogicOperatorImagePullPolicy = "";
  private String weblogicOperatorImagePullSecretName = "";
  private String externalRestEnabled = "";
  private String externalRestHttpsPort = "";
  private String externalOperatorCert = "";
  private String externalOperatorSecret = "";
  private String externalOperatorKey = "";
  private String remoteDebugNodePortEnabled = "";
  private String suspendOnDebugStartup = "";
  private String internalDebugHttpPort = "";
  private String externalDebugHttpPort = "";
  private String javaLoggingLevel = "";
  private String elkIntegrationEnabled = "";
  private String logStashImage = "";
  private String elasticSearchHost = "";
  private String elasticSearchPort = "";

  /**
   * build with test defaults.
   * @return values
   */
  public OperatorValues withTestDefaults() {
    return this.namespace("test-operator-namespace")
        .dedicated("false")
        .serviceAccount("test-operator-service-account")
        .domainNamespaceSelectionStrategy("List")
        .domainNamespaces("test-domain-namespace1,test-domain-namespace2")
        .weblogicOperatorImage("test-operator-image")
        .weblogicOperatorImagePullPolicy("Never")
        .javaLoggingLevel("FINEST")
        .logStashImage("test-logstash-image")
        .elasticSearchHost("test-elastic-search_host")
        .elasticSearchPort("9200");
  }

  /**
   * enable debugging.
   * @return values
   */
  public OperatorValues enableDebugging() {
    return this.remoteDebugNodePortEnabled("true")
        .internalDebugHttpPort("9090")
        .externalDebugHttpPort("30090");
  }

  /**
   * setup external REST.
   * @return values
   */
  public OperatorValues setupExternalRestEnabled() {
    return this.externalRestHttpsPort("30070")
        .externalRestEnabled("true")
        .externalOperatorCert(toBase64(externalOperatorCustomCertPem()))
        .externalOperatorKey(toBase64(externalOperatorCustomKeyPem()));
  }

  public String externalOperatorCustomCertPem() {
    return EXTERNAL_CUSTOM_CERT_PEM;
  }

  public String externalOperatorCustomKeyPem() {
    return EXTERNAL_CUSTOM_KEY_PEM;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String val) {
    version = convertNullToEmptyString(val);
  }

  public OperatorValues version(String val) {
    setVersion(val);
    return this;
  }

  public String getServiceAccount() {
    return serviceAccount;
  }

  public void setServiceAccount(String val) {
    serviceAccount = convertNullToEmptyString(val);
  }

  public OperatorValues serviceAccount(String val) {
    setServiceAccount(val);
    return this;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String val) {
    namespace = convertNullToEmptyString(val);
  }

  public OperatorValues namespace(String val) {
    setNamespace(val);
    return this;
  }

  public String getDomainNamespaceSelectionStrategy() {
    return domainNamespaceSelectionStrategy;
  }

  public void setDomainNamespaceSelectionStrategy(String domainNamespaceSelectionStrategy) {
    this.domainNamespaceSelectionStrategy = domainNamespaceSelectionStrategy;
  }

  public OperatorValues domainNamespaceSelectionStrategy(String domainNamespaceSelectionStrategy) {
    this.domainNamespaceSelectionStrategy = domainNamespaceSelectionStrategy;
    return this;
  }

  public String getDomainNamespaces() {
    return domainNamespaces;
  }

  public void setDomainNamespaces(String val) {
    domainNamespaces = convertNullToEmptyString(val);
  }

  public OperatorValues domainNamespaces(String val) {
    setDomainNamespaces(val);
    return this;
  }

  public String getDomainNamespaceLabelSelector() {
    return domainNamespaceLabelSelector;
  }

  public void setDomainNamespaceLabelSelector(String domainNamespaceLabelSelector) {
    this.domainNamespaceLabelSelector = domainNamespaceLabelSelector;
  }

  public OperatorValues domainNamespaceLabelSelector(String domainNamespaceLabelSelector) {
    this.domainNamespaceLabelSelector = domainNamespaceLabelSelector;
    return this;
  }

  public String getDomainNamespaceRegExp() {
    return domainNamespaceRegExp;
  }

  public void setDomainNamespaceRegExp(String domainNamespaceRegExp) {
    this.domainNamespaceRegExp = domainNamespaceRegExp;
  }

  public OperatorValues domainNamespaceRegExp(String domainNamespaceRegExp) {
    this.domainNamespaceRegExp = domainNamespaceRegExp;
    return this;
  }

  public String getWeblogicOperatorImage() {
    return weblogicOperatorImage;
  }

  public void setWeblogicOperatorImage(String val) {
    weblogicOperatorImage = convertNullToEmptyString(val);
  }

  public OperatorValues weblogicOperatorImage(String val) {
    setWeblogicOperatorImage(val);
    return this;
  }

  public String getWeblogicOperatorImagePullPolicy() {
    return weblogicOperatorImagePullPolicy;
  }

  public void setWeblogicOperatorImagePullPolicy(String val) {
    weblogicOperatorImagePullPolicy = convertNullToEmptyString(val);
  }

  public OperatorValues weblogicOperatorImagePullPolicy(String val) {
    setWeblogicOperatorImagePullPolicy(val);
    return this;
  }

  public String getWeblogicOperatorImagePullSecretName() {
    return weblogicOperatorImagePullSecretName;
  }

  public void setWeblogicOperatorImagePullSecretName(String val) {
    weblogicOperatorImagePullSecretName = convertNullToEmptyString(val);
  }

  public OperatorValues weblogicOperatorImagePullSecretName(String val) {
    setWeblogicOperatorImagePullSecretName(val);
    return this;
  }

  public String getExternalRestEnabled() {
    return externalRestEnabled;
  }

  public void setExternalRestEnabled(String val) {
    externalRestEnabled = convertNullToEmptyString(val);
  }

  public OperatorValues externalRestEnabled(String val) {
    setExternalRestEnabled(val);
    return this;
  }

  public String getExternalRestHttpsPort() {
    return externalRestHttpsPort;
  }

  public void setExternalRestHttpsPort(String val) {
    externalRestHttpsPort = convertNullToEmptyString(val);
  }

  public OperatorValues externalRestHttpsPort(String val) {
    setExternalRestHttpsPort(val);
    return this;
  }

  public String getExternalOperatorSecret() {
    return externalOperatorSecret;
  }

  public void setExternalOperatorSecret(String val) {
    externalOperatorSecret = convertNullToEmptyString(val);
  }

  public String getExternalOperatorCert() {
    return externalOperatorCert;
  }

  public void setExternalOperatorCert(String val) {
    externalOperatorCert = convertNullToEmptyString(val);
  }

  public OperatorValues externalOperatorCert(String val) {
    setExternalOperatorCert(val);
    return this;
  }

  public String getExternalOperatorKey() {
    return externalOperatorKey;
  }

  public void setExternalOperatorKey(String val) {
    externalOperatorKey = convertNullToEmptyString(val);
  }

  public OperatorValues externalOperatorKey(String val) {
    setExternalOperatorKey(val);
    return this;
  }

  public String getRemoteDebugNodePortEnabled() {
    return remoteDebugNodePortEnabled;
  }

  public void setRemoteDebugNodePortEnabled(String val) {
    remoteDebugNodePortEnabled = convertNullToEmptyString(val);
  }

  public OperatorValues remoteDebugNodePortEnabled(String val) {
    setRemoteDebugNodePortEnabled(val);
    return this;
  }

  public String getSuspendOnDebugStartup() {
    return suspendOnDebugStartup;
  }

  protected void setSuspendOnDebugStartup(String val) {
    suspendOnDebugStartup = convertNullToEmptyString(val);
  }

  public OperatorValues suspendOnDebugStartup(String val) {
    setSuspendOnDebugStartup(val);
    return this;
  }

  public String getInternalDebugHttpPort() {
    return internalDebugHttpPort;
  }

  public void setInternalDebugHttpPort(String val) {
    internalDebugHttpPort = convertNullToEmptyString(val);
  }

  public OperatorValues internalDebugHttpPort(String val) {
    setInternalDebugHttpPort(val);
    return this;
  }

  public String getExternalDebugHttpPort() {
    return externalDebugHttpPort;
  }

  public void setExternalDebugHttpPort(String val) {
    externalDebugHttpPort = convertNullToEmptyString(val);
  }

  public OperatorValues externalDebugHttpPort(String val) {
    setExternalDebugHttpPort(val);
    return this;
  }

  public String getJavaLoggingLevel() {
    return javaLoggingLevel;
  }

  public void setJavaLoggingLevel(String val) {
    javaLoggingLevel = convertNullToEmptyString(val);
  }

  public OperatorValues javaLoggingLevel(String val) {
    setJavaLoggingLevel(val);
    return this;
  }

  public String getElkIntegrationEnabled() {
    return elkIntegrationEnabled;
  }

  public void setElkIntegrationEnabled(String val) {
    elkIntegrationEnabled = convertNullToEmptyString(val);
  }

  public OperatorValues elkIntegrationEnabled(String val) {
    setElkIntegrationEnabled(val);
    return this;
  }

  public String getDedicated() {
    return dedicated;
  }

  public void setDedicated(String val) {
    dedicated = convertNullToEmptyString(val);
  }

  public OperatorValues dedicated(String val) {
    setDedicated(val);
    return this;
  }

  public String getLogStashImage() {
    return logStashImage;
  }

  public void setLogStashImage(String val) {
    logStashImage = convertNullToEmptyString(val);
  }

  public OperatorValues logStashImage(String val) {
    setLogStashImage(val);
    return this;
  }

  public String getElasticSearchHost() {
    return elasticSearchHost;
  }

  public void setElasticSearchHost(String val) {
    elasticSearchHost = convertNullToEmptyString(val);
  }

  public OperatorValues elasticSearchHost(String val) {
    setElasticSearchHost(val);
    return this;
  }

  public String getElasticSearchPort() {
    return elasticSearchPort;
  }

  public void setElasticSearchPort(String val) {
    elasticSearchPort = convertNullToEmptyString(val);
  }

  public OperatorValues elasticSearchPort(String val) {
    setElasticSearchPort(val);
    return this;
  }

  // Note: don't allow null strings since, if you use snakeyaml to write out the instance
  // to a yaml file, the nulls are written out as "null".  Use "" instead.
  private String convertNullToEmptyString(String val) {
    return Objects.toString(val, "");
  }

  protected String toBase64(String val) {
    return Base64.encodeBase64String(val.getBytes());
  }
}
