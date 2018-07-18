// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import static oracle.kubernetes.operator.utils.ExecCreateDomain.*;
import static oracle.kubernetes.operator.utils.ExecResultMatcher.succeedsAndPrints;
import static oracle.kubernetes.operator.utils.UserProjects.createUserProjectsDirectory;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.file.Path;

/**
 * Generates the domain yaml files for a set of valid domain input params. Creates and managed the
 * user projects directory that the files are stored in. Parses the generated yaml files into typed
 * java objects.
 */
public class GeneratedDomainYamlFiles {

  private UserProjects userProjects;
  private DomainFiles domainFiles;
  private ParsedCreateWeblogicDomainJobYaml createWeblogicDomainJobYaml;
  private ParsedDeleteWeblogicDomainJobYaml deleteWeblogicDomainJobYaml;
  private ParsedDomainCustomResourceYaml domainCustomResourceYaml;
  private ParsedTraefikYaml traefikYaml;
  private ParsedTraefikSecurityYaml traefikSecurityYaml;
  private ParsedApacheYaml apacheYaml;
  private ParsedApacheSecurityYaml apacheSecurityYaml;
  private ParsedVoyagerOperatorYaml voyagerOperatorYaml;
  private ParsedVoyagerOperatorSecurityYaml voyagerOperatorSecurityYaml;
  private ParsedVoyagerIngressYaml voyagerIngressYaml;
  private ParsedWeblogicDomainPersistentVolumeYaml weblogicDomainPersistentVolumeYaml;
  private ParsedWeblogicDomainPersistentVolumeClaimYaml weblogicDomainPersistentVolumeClaimYaml;

  public static GeneratedDomainYamlFiles generateDomainYamlFiles(CreateDomainInputs inputs)
      throws Exception {
    return new GeneratedDomainYamlFiles(inputs);
  }

  private GeneratedDomainYamlFiles(CreateDomainInputs inputs) throws Exception {
    userProjects = createUserProjectsDirectory();
    boolean ok = false;
    try {
      domainFiles = new DomainFiles(userProjects.getPath(), inputs);
      assertThat(execCreateDomain(userProjects.getPath(), inputs), succeedsAndPrints("Completed"));
      createWeblogicDomainJobYaml =
          new ParsedCreateWeblogicDomainJobYaml(
              domainFiles.getCreateWeblogicDomainJobYamlPath(), inputs);
      deleteWeblogicDomainJobYaml =
          new ParsedDeleteWeblogicDomainJobYaml(
              domainFiles.getDeleteWeblogicDomainJobYamlPath(), inputs);
      domainCustomResourceYaml =
          new ParsedDomainCustomResourceYaml(domainFiles.getDomainCustomResourceYamlPath(), inputs);
      if (CreateDomainInputs.LOAD_BALANCER_TRAEFIK.equals(inputs.getLoadBalancer())) {
        traefikYaml = new ParsedTraefikYaml(domainFiles.getTraefikYamlPath(), inputs);
        traefikSecurityYaml =
            new ParsedTraefikSecurityYaml(domainFiles.getTraefikSecurityYamlPath(), inputs);
      } else if (CreateDomainInputs.LOAD_BALANCER_APACHE.equals(inputs.getLoadBalancer())) {
        apacheYaml = new ParsedApacheYaml(domainFiles.getApacheYamlPath(), inputs);
        apacheSecurityYaml =
            new ParsedApacheSecurityYaml(domainFiles.getApacheSecurityYamlPath(), inputs);
      } else if (CreateDomainInputs.LOAD_BALANCER_VOYAGER.equals(inputs.getLoadBalancer())) {
        voyagerOperatorYaml =
            new ParsedVoyagerOperatorYaml(domainFiles.getVoyagerOperatorYamlPath(), inputs);
        voyagerOperatorSecurityYaml =
            new ParsedVoyagerOperatorSecurityYaml(
                domainFiles.getVoyagerOperatorSecurityYamlPath(), inputs);
        voyagerIngressYaml =
            new ParsedVoyagerIngressYaml(domainFiles.getVoyagerIngressYamlPath(), inputs);
      }
      weblogicDomainPersistentVolumeYaml =
          new ParsedWeblogicDomainPersistentVolumeYaml(
              domainFiles.getWeblogicDomainPersistentVolumeYamlPath(), inputs);
      weblogicDomainPersistentVolumeClaimYaml =
          new ParsedWeblogicDomainPersistentVolumeClaimYaml(
              domainFiles.getWeblogicDomainPersistentVolumeClaimYamlPath(), inputs);
      ok = true;
    } finally {
      if (!ok) {
        remove();
      }
    }
  }

  public Path getInputsYamlPath() {
    return ExecCreateDomain.getInputsYamlPath(userProjects.getPath());
  }

  public DomainFiles getDomainFiles() {
    return domainFiles;
  }

  public ParsedCreateWeblogicDomainJobYaml getCreateWeblogicDomainJobYaml() {
    return createWeblogicDomainJobYaml;
  }

  public ParsedDeleteWeblogicDomainJobYaml getDeleteWeblogicDomainJobYaml() {
    return deleteWeblogicDomainJobYaml;
  }

  public ParsedDomainCustomResourceYaml getDomainCustomResourceYaml() {
    return domainCustomResourceYaml;
  }

  public ParsedTraefikYaml getTraefikYaml() {
    return traefikYaml;
  }

  public ParsedTraefikSecurityYaml getTraefikSecurityYaml() {
    return traefikSecurityYaml;
  }

  public ParsedApacheYaml getApacheYaml() {
    return apacheYaml;
  }

  public ParsedApacheSecurityYaml getApacheSecurityYaml() {
    return apacheSecurityYaml;
  }

  public ParsedVoyagerOperatorYaml getVoyagerOperatorYaml() {
    return voyagerOperatorYaml;
  }

  public ParsedVoyagerOperatorSecurityYaml getVoyagerOperatorSecurityYaml() {
    return voyagerOperatorSecurityYaml;
  }

  public ParsedVoyagerIngressYaml getVoyagerIngressYaml() {
    return voyagerIngressYaml;
  }

  public ParsedWeblogicDomainPersistentVolumeYaml getWeblogicDomainPersistentVolumeYaml() {
    return weblogicDomainPersistentVolumeYaml;
  }

  public ParsedWeblogicDomainPersistentVolumeClaimYaml
      getWeblogicDomainPersistentVolumeClaimYaml() {
    return weblogicDomainPersistentVolumeClaimYaml;
  }

  public void remove() throws Exception {
    userProjects.remove();
  }
}
