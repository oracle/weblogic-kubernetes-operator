// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Path;

import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceSpec;

import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import static oracle.kubernetes.operator.create.YamlUtils.newYaml;
import org.apache.commons.codec.binary.Base64;

/**
 * Parses a generated weblogic-operator.yaml file into a set of typed k8s java objects
 */
public class ParsedWeblogicOperatorYaml {

  private CreateOperatorInputs inputs;
  private ParsedKubernetesYaml parsedYaml;

  public ParsedWeblogicOperatorYaml(Path yamlPath, CreateOperatorInputs inputs) throws Exception {
    this.inputs = inputs;
    parsedYaml = new ParsedKubernetesYaml(yamlPath);
  }

  public V1ConfigMap getOperatorConfigMap() {
    return parsedYaml.getConfigMaps().find("operator-config-map");
  }

  public V1Secret getOperatorSecrets() {
    return parsedYaml.getSecrets().find("operator-secrets");
  }

  public ExtensionsV1beta1Deployment getOperatorDeployment() {
    return parsedYaml.getDeployments().find("weblogic-operator");
  }

  public V1Service getExternalOperatorService() {
    return parsedYaml.getServices().find("external-weblogic-operator-service");
  }

  public V1Service getInternalOperatorService() {
    return parsedYaml.getServices().find("internal-weblogic-operator-service");
  }

  // TBD - where should these utils live?
  public V1ConfigMap getExpectedOperatorConfigMap(String externalOperatorCertWant) {
    return
      newConfigMap()
        .metadata(newObjectMeta()
          .name("operator-config-map")
          .namespace(inputs.getNamespace()))
        .putDataItem("serviceaccount", inputs.getServiceAccount())
        .putDataItem("targetNamespaces", inputs.getTargetNamespaces())
        .putDataItem("externalOperatorCert", Base64.encodeBase64String(externalOperatorCertWant.getBytes()))
        .putDataItem("internalOperatorCert", Base64.encodeBase64String(inputs.internalOperatorSelfSignedCertPem().getBytes()));
  }

  public V1Secret getExpectedOperatorSecrets(String externalOperatorKeyWant) {
    return
      newSecret()
        .metadata(newObjectMeta()
          .name("operator-secrets")
          .namespace(inputs.getNamespace()))
        .type("Opaque")
        .putDataItem("externalOperatorKey", externalOperatorKeyWant.getBytes())
        .putDataItem("internalOperatorKey", inputs.internalOperatorSelfSignedKeyPem().getBytes());
  }

  public V1Service getExpectedExternalOperatorService(boolean debuggingEnabled, boolean externalRestEnabled) {
    V1ServiceSpec spec =
      newServiceSpec()
        .type("NodePort")
        .putSelectorItem("app", "weblogic-operator");
    if (externalRestEnabled) {
      spec.addPortsItem(newServicePort()
        .name("rest-https")
        .port(8081)
        .nodePort(Integer.parseInt(inputs.getExternalRestHttpsPort())));
    }
    if (debuggingEnabled) {
      spec.addPortsItem(newServicePort()
        .name("debug")
        .port(Integer.parseInt(inputs.getInternalDebugHttpPort()))
        .nodePort(Integer.parseInt(inputs.getExternalDebugHttpPort())));
    }
    return newService()
      .metadata(newObjectMeta()
        .name("external-weblogic-operator-service")
        .namespace(inputs.getNamespace()))
      .spec(spec);
  }

  public ExtensionsV1beta1Deployment getBaseExpectedOperatorDeployment() {
    return
      newDeployment()
        .metadata(newObjectMeta()
          .name("weblogic-operator")
          .namespace(inputs.getNamespace()))
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
