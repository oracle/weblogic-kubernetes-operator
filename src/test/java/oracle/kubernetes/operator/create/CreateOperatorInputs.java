package oracle.kubernetes.operator.create;

/**
 * Class that mirrors create-weblogic-operator-inputs.yaml
 *
 * Used to parse create-weblogic-operator-inputs.yaml into java
 * and convert java to create-weblogic-operator-inputs.yaml
 *
 * Use a string to represent params that must be ints or booleans at runtime
 * so that we can test more invalid input options (e.g. missing value, not int value)
 *
 */
public class CreateOperatorInputs {

  private String serviceAccount;
  public String getServiceAccount() { return serviceAccount; }
  public void setServiceAccount(String val) { serviceAccount = val; }
  public CreateOperatorInputs serviceAccount(String val) { setServiceAccount(val); return this; }

  private String namespace;
  public String getNamespace() { return namespace; }
  public void setNamespace(String val) { namespace = val; }
  public CreateOperatorInputs namespace(String val) { setNamespace(val); return this; }

  private String targetNamespaces;
  public String getTargetNamespaces() { return targetNamespaces; }
  public void setTargetNamespaces(String val) { targetNamespaces = val; }
  public CreateOperatorInputs targetNamespaces(String val) { setTargetNamespaces(val); return this; }

  private String image;
  public String getImage() { return image; }
  public void setImage(String val) { image = val; }
  public CreateOperatorInputs image(String val) { setImage(val); return this; }

  private String imagePullPolicy;
  public String getImagePullPolicy() { return imagePullPolicy; }
  public void setImagePullPolicy(String val) { imagePullPolicy = val; }
  public CreateOperatorInputs imagePullPolicy(String val) { setImagePullPolicy(val); return this; }

  private String imagePullSecretName;
  public String getImagePullSecretName() { return imagePullSecretName; }
  public void setImagePullSecretName(String val) { imagePullSecretName = val; }
  public CreateOperatorInputs imagePullSecretName(String val) { setImagePullSecretName(val); return this; }

  private String externalRestOption;
  public String getExternalRestOption() { return externalRestOption; }
  public void setExternalRestOption(String val) { externalRestOption = val; }
  public CreateOperatorInputs externalRestOption(String val) { setExternalRestOption(val); return this; }

  private String externalRestHttpsPort;
  public String getExternalRestHttpsPort() { return externalRestHttpsPort; }
  public void setExternalRestHttpsPort(String val) { externalRestHttpsPort = val; }
  public CreateOperatorInputs externalRestHttpsPort(String val) { setExternalRestHttpsPort(val); return this; }

  private String externalSans;
  public String getExternalSans() { return externalSans; }
  public void setExternalSans(String val) { externalSans = val; }
  public CreateOperatorInputs externalSans(String val) { setExternalSans(val); return this; }

  private String externalOperatorCert;
  public String getExternalOperatorCert() { return externalOperatorCert; }
  public void setExternalOperatorCert(String val) { externalOperatorCert = val; }
  public CreateOperatorInputs externalOperatorCert(String val) { setExternalOperatorCert(val); return this; }

  private String externalOperatorKey;
  public String getExternalOperatorKey() { return externalOperatorKey; }
  public void setExternalOperatorKey(String val) { externalOperatorKey = val; }
  public CreateOperatorInputs externalOperatorKey(String val) { setExternalOperatorKey(val); return this; }

  private String remoteDebugNodePortEnabled;
  public String getRemoteDebugNodePortEnabled() { return remoteDebugNodePortEnabled; }
  public void setRemoteDebugNodePortEnabled(String val) { remoteDebugNodePortEnabled = val; }
  public CreateOperatorInputs remoteDebugNodePortEnabled(String val) { setRemoteDebugNodePortEnabled(val); return this; }

  private String internalDebugHttpPort;
  public String getInternalDebugHttpPort() { return internalDebugHttpPort; }
  public void setInternalDebugHttpPort(String val) { internalDebugHttpPort = val; }
  public CreateOperatorInputs internalDebugHttpPort(String val) { setInternalDebugHttpPort(val); return this; }

  private String externalDebugHttpPort;
  public String getExternalDebugHttpPort() { return externalDebugHttpPort; }
  public void setExternalDebugHttpPort(String val) { externalDebugHttpPort = val; }
  public CreateOperatorInputs externalDebugHttpPort(String val) { setExternalDebugHttpPort(val); return this; }

  private String javaLoggingLevel;
  public String getJavaLoggingLevel() { return javaLoggingLevel; }
  public void setJavaLoggingLevel(String val) { javaLoggingLevel = val; }
  public CreateOperatorInputs javaLoggingLevel(String val) { setJavaLoggingLevel(val); return this; }

  private String elkIntegrationEnabled;
  public String getElkIntegrationEnabled() { return elkIntegrationEnabled; }
  public void setElkIntegrationEnabled(String val) { elkIntegrationEnabled = val; }
  public CreateOperatorInputs elkIntegrationEnabled(String val) { setElkIntegrationEnabled(val); return this; }
}
