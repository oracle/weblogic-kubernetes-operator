// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Path;

import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.models.V1Service;

import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import static oracle.kubernetes.operator.create.YamlUtils.newYaml;
import org.apache.commons.codec.binary.Base64;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Parses a generated weblogic-operator.yaml file into a set of typed k8s java objects
 */
public class ParsedWeblogicOperatorYaml {

  private CreateOperatorInputs inputs;
  private V1ConfigMap operatorConfigMap;
  private V1Secret operatorSecrets;
  private ExtensionsV1beta1Deployment operatorDeployment;
  private V1Service externalOperatorService;
  private V1Service internalOperatorService;

  public V1ConfigMap getOperatorConfigMap() { return operatorConfigMap; }
  public V1Secret getOperatorSecrets() { return operatorSecrets; }
  public ExtensionsV1beta1Deployment getOperatorDeployment() { return operatorDeployment; }
  public V1Service getExternalOperatorService() { return externalOperatorService; }
  public V1Service getInternalOperatorService() { return internalOperatorService; }

  public ParsedWeblogicOperatorYaml(Path yamlPath, CreateOperatorInputs inputs) throws Exception {
    this.inputs = inputs;
    ParsedKubernetesYaml parsed = new ParsedKubernetesYaml(yamlPath);
    operatorConfigMap = parsed.getConfigMap("operator-config-map");
    operatorSecrets = parsed.getSecret("operator-secrets");
    operatorDeployment = parsed.getDeployment("weblogic-operator");
    internalOperatorService = parsed.getService("internal-weblogic-operator-service");
    externalOperatorService = parsed.getService("external-weblogic-operator-service");
  }

  // TBD - where should these utils live?
  public V1ConfigMap getExpectedOperatorConfigMap(String externalOperatorCertWant) {
    return
      newConfigMap("operator-config-map", inputs.getNamespace())
        .putDataItem("serviceaccount", inputs.getServiceAccount())
        .putDataItem("targetNamespaces", inputs.getTargetNamespaces())
        .putDataItem("externalOperatorCert", Base64.encodeBase64String(externalOperatorCertWant.getBytes()))
        .putDataItem("internalOperatorCert", Base64.encodeBase64String(inputs.internalOperatorSelfSignedCertPem().getBytes()));
  }

  public static void assertThat_secretsAreEqual(V1Secret have, V1Secret want) {
    // The secret values are stored as byte[], and V1Secret.equal isn't smart
    // enough to compare them byte by byte, therefore equals always fails.
    // However, they get converted to cleartext strings in the yaml.
    // So, just convert the secrets to yaml strings, then compare those.
    assertThat(newYaml().dump(have), equalTo(newYaml().dump(want)));
  }

  public V1Secret getExpectedOperatorSecrets(String externalOperatorKeyWant) {
    return
      newSecret("operator-secrets", inputs.getNamespace())
        .type("Opaque")
        .putDataItem("externalOperatorKey", externalOperatorKeyWant.getBytes())
        .putDataItem("internalOperatorKey", inputs.internalOperatorSelfSignedKeyPem().getBytes());
  }

  public V1Service getExpectedExternalOperatorService(boolean debuggingEnabled, boolean externalRestEnabled) {
    V1Service want =
      newService("external-weblogic-operator-service", inputs.getNamespace());
    want.getSpec()
      .type("NodePort")
      .putSelectorItem("app", "weblogic-operator");
    if (externalRestEnabled) {
      want.getSpec().addPortsItem(newServicePort("rest-https")
        .port(8081)
        .nodePort(Integer.parseInt(inputs.getExternalRestHttpsPort())));
    }
    if (debuggingEnabled) {
      want.getSpec().addPortsItem(newServicePort("debug")
        .port(Integer.parseInt(inputs.getInternalDebugHttpPort()))
        .nodePort(Integer.parseInt(inputs.getExternalDebugHttpPort())));
    }
    return want;
  }

  public ExtensionsV1beta1Deployment getBaseExpectedOperatorDeployment() {
    return
      newDeployment("weblogic-operator", inputs.getNamespace())
        .spec(newDeploymentSpec()
          .replicas(1)
          .template(newPodTemplateSpec()
            .metadata(newObjectMeta()
              .putLabelsItem("app", "weblogic-operator"))
            .spec(newPodSpec()
              .serviceAccountName(inputs.getServiceAccount())
              .addContainersItem(newContainer()
                .name("weblogic-operator")
                .image(inputs.getImage())
                .imagePullPolicy(inputs.getImagePullPolicy())
                .addCommandItem("bash")
                .addArgsItem("/operator/operator.sh")
                .addEnvItem(newEnvVar()
                  .name("OPERATOR_NAMESPACE")
                  .valueFrom(newEnvVarSource()
                    .fieldRef(newObjectFieldSelector()
                      .fieldPath("metadata.namespace"))))
                .addEnvItem(newEnvVar()
                  .name("OPERATOR_VERBOSE")
                  .value("false"))
                .addEnvItem(newEnvVar()
                  .name("JAVA_LOGGING_LEVEL")
                  .value(inputs.getJavaLoggingLevel()))
                .addVolumeMountsItem(newVolumeMount()
                  .name("operator-config-volume")
                  .mountPath("/operator/config"))
                .addVolumeMountsItem(newVolumeMount()
                  .name("operator-secrets-volume")
                  .mountPath("/operator/secrets")
                  .readOnly(true))
                .livenessProbe(newProbe()
                  .initialDelaySeconds(120)
                  .periodSeconds(5)
                  .exec(newExecAction()
                    .addCommandItem("bash")
                    .addCommandItem("/operator/livenessProbe.sh"))))
              .addVolumesItem(newVolume()
                .name("operator-config-volume")
                  .configMap(newConfigMapVolumeSource()
                    .name("operator-config-map")))
              .addVolumesItem(newVolume()
                .name("operator-secrets-volume")
                  .secret(newSecretVolumeSource()
                    .secretName("operator-secrets"))))));
  }
}
