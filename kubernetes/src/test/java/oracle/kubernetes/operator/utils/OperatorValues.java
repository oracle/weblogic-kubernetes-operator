// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.Objects;
import org.apache.commons.codec.binary.Base64;

public class OperatorValues {
  public static final String EXTERNAL_REST_OPTION_NONE = "NONE";
  public static final String EXTERNAL_REST_OPTION_CUSTOM_CERT = "CUSTOM_CERT";
  public static final String EXTERNAL_REST_OPTION_SELF_SIGNED_CERT = "SELF_SIGNED_CERT";
  public static final String INTERNAL_REST_OPTION_CUSTOM_CERT = "CUSTOM_CERT";
  public static final String INTERNAL_REST_OPTION_SELF_SIGNED_CERT = "SELF_SIGNED_CERT";
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
  private static final String INTERNAL_CUSTOM_CERT_PEM = "test-internal-custom-certificate-pem";
  private static final String INTERNAL_CUSTOM_KEY_PEM = "test-internal-custom-private-key-pem";
  private String version = "";
  private String serviceAccount = "";
  private String namespace = "";
  private String targetNamespaces = "";
  private String weblogicOperatorImage = "";
  private String weblogicOperatorImagePullPolicy = "";
  private String weblogicOperatorImagePullSecretName = "";
  private String internalRestOption = "";
  private String internalOperatorCert = "";
  private String internalOperatorKey = "";
  private String externalRestOption = "";
  private String externalRestHttpsPort = "";
  private String externalSans = "";
  private String externalOperatorCert = "";
  private String externalOperatorKey = "";
  private String remoteDebugNodePortEnabled = "";
  private String internalDebugHttpPort = "";
  private String externalDebugHttpPort = "";
  private String javaLoggingLevel = "";
  private String elkIntegrationEnabled = "";

  public OperatorValues withTestDefaults() {
    return this.namespace("test-operator-namespace")
        .serviceAccount("test-operator-service-account")
        .targetNamespaces("test-target-namespace1,test-target-namespace2")
        .weblogicOperatorImage("test-operator-image")
        .weblogicOperatorImagePullPolicy("Never")
        .javaLoggingLevel("FINEST")
        .setupInternalRestSelfSignedCert();
  }

  public OperatorValues enableDebugging() {
    return this.remoteDebugNodePortEnabled("true")
        .internalDebugHttpPort("9090")
        .externalDebugHttpPort("30090");
  }

  public OperatorValues setupExternalRestSelfSignedCert() {
    return this.externalRestHttpsPort("30070")
        .externalRestOption(EXTERNAL_REST_OPTION_SELF_SIGNED_CERT)
        .externalSans("DNS:localhost");
  }

  public OperatorValues setupExternalRestCustomCert() {
    return this.externalRestHttpsPort("30070")
        .externalRestOption(EXTERNAL_REST_OPTION_CUSTOM_CERT)
        .externalOperatorCert(toBase64(externalOperatorCustomCertPem()))
        .externalOperatorKey(toBase64(externalOperatorCustomKeyPem()));
  }

  public OperatorValues setupInternalRestSelfSignedCert() {
    return this.internalRestOption(INTERNAL_REST_OPTION_SELF_SIGNED_CERT);
  }

  public OperatorValues setupInternalRestCustomCert() {
    return this.internalRestOption(INTERNAL_REST_OPTION_CUSTOM_CERT)
        .internalOperatorCert(toBase64(internalOperatorCustomCertPem()))
        .internalOperatorKey(toBase64(internalOperatorCustomKeyPem()));
  }

  public String externalOperatorCustomCertPem() {
    return EXTERNAL_CUSTOM_CERT_PEM;
  }

  public String externalOperatorCustomKeyPem() {
    return EXTERNAL_CUSTOM_KEY_PEM;
  }

  public String internalOperatorCustomCertPem() {
    return INTERNAL_CUSTOM_CERT_PEM;
  }

  public String internalOperatorCustomKeyPem() {
    return INTERNAL_CUSTOM_KEY_PEM;
  }

  public String externalOperatorSelfSignedCertPem() {
    return selfSignedCertPem(getExternalSans());
  }

  public String externalOperatorSelfSignedKeyPem() {
    return selfSignedKeyPem(getExternalSans());
  }

  public String internalOperatorSelfSignedCertPem() {
    return selfSignedCertPem(internalSans());
  }

  public String internalOperatorSelfSignedKeyPem() {
    return selfSignedKeyPem(internalSans());
  }

  private String selfSignedCertPem(String sans) {
    // Must match computation in src/tests/scripts/unit-test-generate-weblogic-operator-cert.sh
    return "unit test mock cert pem for sans:" + sans + "\n";
  }

  private String selfSignedKeyPem(String sans) {
    // Must match computation in src/tests/scripts/unit-test-generate-weblogic-operator-cert.sh
    return "unit test mock key pem for sans:" + sans + "\n";
  }

  private String internalSans() {
    // Must match internal sans computation in kubernetes/internal/create-weblogic-operator.sh
    String host = "internal-weblogic-operator-svc";
    String ns = getNamespace();
    StringBuilder sb = new StringBuilder();
    sb.append("DNS:")
        .append(host)
        .append(",DNS:")
        .append(host)
        .append(".")
        .append(ns)
        .append(",DNS:")
        .append(host)
        .append(".")
        .append(ns)
        .append(".svc")
        .append(",DNS:")
        .append(host)
        .append(".")
        .append(ns)
        .append(".svc.cluster.local");
    return sb.toString();
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

  public String getTargetNamespaces() {
    return targetNamespaces;
  }

  public void setTargetNamespaces(String val) {
    targetNamespaces = convertNullToEmptyString(val);
  }

  public OperatorValues targetNamespaces(String val) {
    setTargetNamespaces(val);
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

  public String getExternalRestOption() {
    return externalRestOption;
  }

  public void setExternalRestOption(String val) {
    externalRestOption = convertNullToEmptyString(val);
  }

  public OperatorValues externalRestOption(String val) {
    setExternalRestOption(val);
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

  public String getExternalSans() {
    return externalSans;
  }

  public void setExternalSans(String val) {
    externalSans = convertNullToEmptyString(val);
  }

  public OperatorValues externalSans(String val) {
    setExternalSans(val);
    return this;
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

  public String getInternalRestOption() {
    return internalRestOption;
  }

  public void setInternalRestOption(String val) {
    internalRestOption = convertNullToEmptyString(val);
  }

  public OperatorValues internalRestOption(String val) {
    setInternalRestOption(val);
    return this;
  }

  public String getInternalOperatorCert() {
    return internalOperatorCert;
  }

  public void setInternalOperatorCert(String val) {
    internalOperatorCert = convertNullToEmptyString(val);
  }

  public OperatorValues internalOperatorCert(String val) {
    setInternalOperatorCert(val);
    return this;
  }

  public String getInternalOperatorKey() {
    return internalOperatorKey;
  }

  public void setInternalOperatorKey(String val) {
    internalOperatorKey = convertNullToEmptyString(val);
  }

  public OperatorValues internalOperatorKey(String val) {
    setInternalOperatorKey(val);
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

  // Note: don't allow null strings since, if you use snakeyaml to write out the instance
  // to a yaml file, the nulls are written out as "null".  Use "" instead.
  private String convertNullToEmptyString(String val) {
    return Objects.toString(val, "");
  }

  protected String toBase64(String val) {
    return Base64.encodeBase64String(val.getBytes());
  }
}
