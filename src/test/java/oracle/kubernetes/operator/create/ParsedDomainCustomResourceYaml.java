// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Path;

import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import oracle.kubernetes.weblogic.domain.v1.Domain;

/**
 * Parses a generated domain-custom-resource.yaml file into a set of typed k8s java objects
 */
public class ParsedDomainCustomResourceYaml {

  private CreateDomainInputs inputs;
  private ParsedKubernetesYaml parsedYaml;

  public ParsedDomainCustomResourceYaml(Path yamlPath, CreateDomainInputs inputs) throws Exception {
    this.inputs = inputs;
    parsedYaml = new ParsedKubernetesYaml(yamlPath);
  }

  public Domain getDomain() {
    return parsedYaml.getDomains().find(inputs.getDomainUid());
  }

  public Domain getBaseExpectedDomain() {
    return
      newDomain()
        .withMetadata(
          newObjectMeta()
            .name(inputs.getDomainUid())
            .namespace(inputs.getNamespace())
            .putLabelsItem("weblogic.domainUID", inputs.getDomainUid()))
        .withSpec(newDomainSpec()
          .withDomainUID(inputs.getDomainUid())
          .withDomainName(inputs.getDomainName())
          .withImage("store/oracle/weblogic:12.2.1.3")
          .withImagePullPolicy("IfNotPresent")
          .withAdminSecret(newSecretReference()
            .name(inputs.getSecretName()))
          .withAsName(inputs.getAdminServerName())
          .withAsPort(Integer.parseInt(inputs.getAdminPort()))
          .withStartupControl(inputs.getStartupControl())
          .withServerStartup(newServerStartupList()
            .addElement(newServerStartup()
          .withDesiredState("RUNNING")
          .withServerName(inputs.getAdminServerName())
          .withEnv(newEnvVarList()
            .addElement(newEnvVar()
              .name("JAVA_OPTIONS")
              .value(inputs.getJavaOptions()))
            .addElement(newEnvVar()
              .name("USER_MEM_ARGS")
              .value("-Xms64m -Xmx256m ")))))
          .withClusterStartup(newClusterStartupList()
            .addElement(newClusterStartup()
              .withDesiredState("RUNNING")
              .withClusterName(inputs.getClusterName())
              .withReplicas(Integer.parseInt(inputs.getManagedServerStartCount()))
              .withEnv(newEnvVarList()
                .addElement(newEnvVar()
                  .name("JAVA_OPTIONS")
                  .value(inputs.getJavaOptions()))
                .addElement(newEnvVar()
                  .name("USER_MEM_ARGS")
                  .value("-Xms64m -Xmx256m "))))));
  }
}
