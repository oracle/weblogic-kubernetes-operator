// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Path;

/**
 * Manages the input and generated files for a domain
 */
public class DomainFiles {

  public static final String CREATE_SCRIPT = "src/test/scripts/unit-test-create-weblogic-domain.sh";
  private static final String DOMAIN_CUSTOM_RESOURCE_YAML = "domain-custom-resource.yaml";

  private Path userProjectsPath;
  private CreateDomainInputs inputs;

  public DomainFiles(Path userProjectsPath, CreateDomainInputs inputs) {
    this.userProjectsPath = userProjectsPath;
    this.inputs = inputs;
  }

  public Path userProjectsPath() { return userProjectsPath; }

  public Path getDomainCustomResourceYamlPath() {
    return getWeblogicDomainPath().resolve(DOMAIN_CUSTOM_RESOURCE_YAML);
  }

  public Path getWeblogicDomainPath() {
    return userProjectsPath().resolve("weblogic-domains").resolve(inputs.getDomainUid());
  }

/*
  TBD
    create-weblogic-domain-domain-job.yaml
    traefik-security.yaml
    traefik.yaml
    weblogic-domain-persistent-volume-claim.yaml
    weblogic-domain-persistent-volume.yaml
*/
}
