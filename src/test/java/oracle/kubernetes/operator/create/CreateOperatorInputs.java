package oracle.kubernetes.operator.create;

import java.io.*;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import org.apache.commons.codec.binary.Base64;

import static oracle.kubernetes.operator.create.YamlUtils.newYaml;

/**
 * Class that mirrors create-weblogic-operator-inputs.yaml
 *
 * Used to parse create-weblogic-operator-inputs.yaml into java
 * and convert java to create-weblogic-operator-inputs.yaml
 *
 * Also includes methods to configure common combinations of inputs.
 *
 * Note use strings to represent params that must be ints or booleans at runtime
 * so that we can test more invalid input options (e.g. missing value, not int value)
 *
 */
public class CreateOperatorInputs {

  private static final String DEFAULT_INPUTS = "kubernetes/create-weblogic-operator-inputs.yaml";

  public static CreateOperatorInputs newInputs() throws Exception {
    return
      readDefaultInputsFile()
        .namespace("test-operator-namespace")
        .serviceAccount("test-operator-service-account")
        .targetNamespaces("test-target-namespace1,test-target-namespace2")
        .image("test-operator-image")
        .imagePullPolicy("Never")
        .javaLoggingLevel("FINEST");
  }

  public static CreateOperatorInputs readDefaultInputsFile() throws IOException {
    Reader r = Files.newBufferedReader(defaultInputsPath(), Charset.forName("UTF-8"));
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
        .externalRestOption("self-signed-cert")
        .externalSans("DNS:localhost");
  }

  public CreateOperatorInputs setupExternalRestCustomCert() {
    return
      this
        .externalRestHttpsPort("30070")
        .externalRestOption("custom-cert")
        .externalOperatorCert("test-custom-certificate-pem")
        .externalOperatorKey(
          Base64.encodeBase64String("test-custom-private-key-pem".getBytes())
        );
  }

  // Note: don't allow null strings since, if you use snakeyaml to write out the instance
  // to a yaml file, the nulls are written out as "null".  Use "" instead.

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

  private String image = "";
  public String getImage() { return image; }
  public void setImage(String val) { image = convertNullToEmptyString(val); }
  public CreateOperatorInputs image(String val) { setImage(val); return this; }

  private String imagePullPolicy = "";
  public String getImagePullPolicy() { return imagePullPolicy; }
  public void setImagePullPolicy(String val) { imagePullPolicy = convertNullToEmptyString(val); }
  public CreateOperatorInputs imagePullPolicy(String val) { setImagePullPolicy(val); return this; }

  private String imagePullSecretName = "";
  public String getImagePullSecretName() { return imagePullSecretName; }
  public void setImagePullSecretName(String val) { imagePullSecretName = convertNullToEmptyString(val); }
  public CreateOperatorInputs imagePullSecretName(String val) { setImagePullSecretName(val); return this; }

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

  private String convertNullToEmptyString(String val) { return (val != null) ? val : ""; }
}
