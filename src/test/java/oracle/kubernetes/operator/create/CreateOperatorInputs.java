package oracle.kubernetes.operator.create;

/**
 * Class that mirrors create-weblogic-operator-inputs.yaml
 *
 * Used to parse create-weblogic-operator-inputs.yaml into java
 * and convert java to create-weblogic-operator-inputs.yaml
 */
public class CreateOperatorInputs {
  public String serviceAccount;
  public String namespace;
  public String targetNamespaces;
  public String image;
  public String imagePullPolicy;
  public String imagePullSecretName;
  public String externalRestOption;
  public int externalRestHttpsPort;
  public String externalSans;
  public String externalOperatorCert;
  public String externalOperatorKey;
  public boolean remoteDebugNodePortEnabled;
  public int internalDebugHttpPort;
  public int externalDebugHttpPort;
  public String javaLoggingLevel;
  public boolean elkIntegrationEnabled;
}
