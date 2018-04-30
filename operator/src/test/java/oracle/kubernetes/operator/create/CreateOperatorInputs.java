// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import org.apache.commons.codec.binary.Base64;

import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static oracle.kubernetes.operator.create.YamlUtils.newYaml;

/**
 * Class that mirrors create-weblogic-operator-inputs.yaml
 *
 * Used to parse create-weblogic-operator-inputs.yaml into java
 * and convert java to create-weblogic-operator-inputs.yaml
 *
 * Note: use strings to represent params that must be ints or booleans at runtime
 * so that we can test more invalid input options (e.g. missing value, not int value)
 *
 * Note: initialize to empty strings and change nulls to empty strings
 * so that when this is written out to a yaml file, the files don't
 * include the literal "null" string. 
 */
public class CreateOperatorInputs {

  private static final String DEFAULT_INPUTS = "../kubernetes/create-weblogic-operator-inputs.yaml";

  public static final String EXTERNAL_REST_OPTION_NONE = "NONE";
  public static final String EXTERNAL_REST_OPTION_CUSTOM_CERT = "CUSTOM_CERT";
  public static final String EXTERNAL_REST_OPTION_SELF_SIGNED_CERT = "SELF_SIGNED_CERT";

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

  public static CreateOperatorInputs newInputs() throws Exception {
    return
      readDefaultInputsFile()
        .namespace("test-operator-namespace")
        .serviceAccount("test-operator-service-account")
        .targetNamespaces("test-target-namespace1,test-target-namespace2")
        .weblogicOperatorImage("test-operator-image")
        .weblogicOperatorImagePullPolicy("Never")
        .javaLoggingLevel("FINEST");
  }

  public static CreateOperatorInputs readDefaultInputsFile() throws Exception {
    return readInputsYamlFile(defaultInputsPath());
  }

  public static CreateOperatorInputs readInputsYamlFile(Path path) throws Exception {
    Reader r = Files.newBufferedReader(path, Charset.forName("UTF-8"));
    return (CreateOperatorInputs)newYaml().loadAs(r, CreateOperatorInputs.class);
  }

  private static Path defaultInputsPath() {
    return FileSystems.getDefault().getPath(DEFAULT_INPUTS);
  }

  public CreateOperatorInputs enableDebugging() {
    return
      this
        .remoteDebugNodePortEnabled("true")
        .internalDebugHttpPort("9090")
        .externalDebugHttpPort("30090");
  }

  public CreateOperatorInputs setupExternalRestSelfSignedCert() {
    return
      this
        .externalRestHttpsPort("30070")
        .externalRestOption(EXTERNAL_REST_OPTION_SELF_SIGNED_CERT)
        .externalSans("DNS:localhost");
  }

  public CreateOperatorInputs setupExternalRestCustomCert() {
    return
      this
        .externalRestHttpsPort("30070")
        .externalRestOption(EXTERNAL_REST_OPTION_CUSTOM_CERT)
        .externalOperatorCert(
          Base64.encodeBase64String(CUSTOM_CERT_PEM.getBytes())
        )
        .externalOperatorKey(
          Base64.encodeBase64String(CUSTOM_KEY_PEM.getBytes())
        );
  }

  private static final String CUSTOM_CERT_PEM = "test-custom-certificate-pem";
  private static final String CUSTOM_KEY_PEM = "test-custom-private-key-pem";

  public String externalOperatorCustomCertPem() { return CUSTOM_CERT_PEM; }
  public String externalOperatorCustomKeyPem() { return CUSTOM_KEY_PEM; }

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
    StringBuilder sb  = new StringBuilder();
    sb
      .append("DNS:").append(host)
      .append(",DNS:").append(host).append(".").append(ns)
      .append(",DNS:").append(host).append(".").append(ns).append(".svc")
      .append(",DNS:").append(host).append(".").append(ns).append(".svc.cluster.local");
    return sb.toString();
  }

  // Note: don't allow null strings since, if you use snakeyaml to write out the instance
  // to a yaml file, the nulls are written out as "null".  Use "" instead.

  private String version = "";
  public String getVersion() { return version; }
  public void setVersion(String val) { version = convertNullToEmptyString(val); }
  public CreateOperatorInputs version(String val) { setVersion(val); return this; }

  private String serviceAccount = "";
  public String getServiceAccount() { return serviceAccount; }
  public void setServiceAccount(String val) { serviceAccount = convertNullToEmptyString(val); }
  public CreateOperatorInputs serviceAccount(String val) { setServiceAccount(val); return this; }

  private String namespace = "";
  public String getNamespace() { return namespace; }
  public void setNamespace(String val) { namespace = convertNullToEmptyString(val); }
  public CreateOperatorInputs namespace(String val) { setNamespace(val); return this; }

  private String targetNamespaces = "";
  public String getTargetNamespaces() { return targetNamespaces; }
  public void setTargetNamespaces(String val) { targetNamespaces = convertNullToEmptyString(val); }
  public CreateOperatorInputs targetNamespaces(String val) { setTargetNamespaces(val); return this; }

  private String weblogicOperatorImage = "";
  public String getWeblogicOperatorImage() { return weblogicOperatorImage; }
  public void setWeblogicOperatorImage(String val) { weblogicOperatorImage = convertNullToEmptyString(val); }
  public CreateOperatorInputs weblogicOperatorImage(String val) { setWeblogicOperatorImage(val); return this; }

  private String weblogicOperatorImagePullPolicy = "";
  public String getWeblogicOperatorImagePullPolicy() { return weblogicOperatorImagePullPolicy; }
  public void setWeblogicOperatorImagePullPolicy(String val) { weblogicOperatorImagePullPolicy = convertNullToEmptyString(val); }
  public CreateOperatorInputs weblogicOperatorImagePullPolicy(String val) { setWeblogicOperatorImagePullPolicy(val); return this; }

  private String weblogicOperatorImagePullSecretName = "";
  public String getWeblogicOperatorImagePullSecretName() { return weblogicOperatorImagePullSecretName; }
  public void setWeblogicOperatorImagePullSecretName(String val) { weblogicOperatorImagePullSecretName = convertNullToEmptyString(val); }
  public CreateOperatorInputs weblogicOperatorImagePullSecretName(String val) { setWeblogicOperatorImagePullSecretName(val); return this; }

  private String externalRestOption = "";
  public String getExternalRestOption() { return externalRestOption; }
  public void setExternalRestOption(String val) { externalRestOption = convertNullToEmptyString(val); }
  public CreateOperatorInputs externalRestOption(String val) { setExternalRestOption(val); return this; }

  private String externalRestHttpsPort = "";
  public String getExternalRestHttpsPort() { return externalRestHttpsPort; }
  public void setExternalRestHttpsPort(String val) { externalRestHttpsPort = convertNullToEmptyString(val); }
  public CreateOperatorInputs externalRestHttpsPort(String val) { setExternalRestHttpsPort(val); return this; }

  private String externalSans = "";
  public String getExternalSans() { return externalSans; }
  public void setExternalSans(String val) { externalSans = convertNullToEmptyString(val); }
  public CreateOperatorInputs externalSans(String val) { setExternalSans(val); return this; }

  private String externalOperatorCert = "";
  public String getExternalOperatorCert() { return externalOperatorCert; }
  public void setExternalOperatorCert(String val) { externalOperatorCert = convertNullToEmptyString(val); }
  public CreateOperatorInputs externalOperatorCert(String val) { setExternalOperatorCert(val); return this; }

  private String externalOperatorKey = "";
  public String getExternalOperatorKey() { return externalOperatorKey; }
  public void setExternalOperatorKey(String val) { externalOperatorKey = convertNullToEmptyString(val); }
  public CreateOperatorInputs externalOperatorKey(String val) { setExternalOperatorKey(val); return this; }

  private String remoteDebugNodePortEnabled = "";
  public String getRemoteDebugNodePortEnabled() { return remoteDebugNodePortEnabled; }
  public void setRemoteDebugNodePortEnabled(String val) { remoteDebugNodePortEnabled = convertNullToEmptyString(val); }
  public CreateOperatorInputs remoteDebugNodePortEnabled(String val) { setRemoteDebugNodePortEnabled(val); return this; }

  private String internalDebugHttpPort = "";
  public String getInternalDebugHttpPort() { return internalDebugHttpPort; }
  public void setInternalDebugHttpPort(String val) { internalDebugHttpPort = convertNullToEmptyString(val); }
  public CreateOperatorInputs internalDebugHttpPort(String val) { setInternalDebugHttpPort(val); return this; }

  private String externalDebugHttpPort = "";
  public String getExternalDebugHttpPort() { return externalDebugHttpPort; }
  public void setExternalDebugHttpPort(String val) { externalDebugHttpPort = convertNullToEmptyString(val); }
  public CreateOperatorInputs externalDebugHttpPort(String val) { setExternalDebugHttpPort(val); return this; }

  private String javaLoggingLevel = "";
  public String getJavaLoggingLevel() { return javaLoggingLevel; }
  public void setJavaLoggingLevel(String val) { javaLoggingLevel = convertNullToEmptyString(val); }
  public CreateOperatorInputs javaLoggingLevel(String val) { setJavaLoggingLevel(val); return this; }

  private String elkIntegrationEnabled = "";
  public String getElkIntegrationEnabled() { return elkIntegrationEnabled; }
  public void setElkIntegrationEnabled(String val) { elkIntegrationEnabled = convertNullToEmptyString(val); }
  public CreateOperatorInputs elkIntegrationEnabled(String val) { setElkIntegrationEnabled(val); return this; }

  private String convertNullToEmptyString(String val) {
    return Objects.toString(val, "");
  }
}
