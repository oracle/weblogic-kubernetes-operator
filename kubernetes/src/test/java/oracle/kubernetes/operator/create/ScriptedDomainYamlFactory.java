// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import static oracle.kubernetes.operator.utils.ExecCreateDomain.execCreateDomain;
import static oracle.kubernetes.operator.utils.ExecResultMatcher.succeedsAndPrints;
import static oracle.kubernetes.operator.utils.UserProjects.createUserProjectsDirectory;
import static org.hamcrest.MatcherAssert.assertThat;

import oracle.kubernetes.operator.utils.CreateDomainInputs;
import oracle.kubernetes.operator.utils.DomainFiles;
import oracle.kubernetes.operator.utils.DomainValues;
import oracle.kubernetes.operator.utils.DomainYamlFactory;
import oracle.kubernetes.operator.utils.GeneratedDomainYamlFiles;
import oracle.kubernetes.operator.utils.ParsedApacheSecurityYaml;
import oracle.kubernetes.operator.utils.ParsedApacheYaml;
import oracle.kubernetes.operator.utils.ParsedCreateWeblogicDomainJobYaml;
import oracle.kubernetes.operator.utils.ParsedDeleteWeblogicDomainJobYaml;
import oracle.kubernetes.operator.utils.ParsedDomainCustomResourceYaml;
import oracle.kubernetes.operator.utils.ParsedTraefikSecurityYaml;
import oracle.kubernetes.operator.utils.ParsedTraefikYaml;
import oracle.kubernetes.operator.utils.ParsedVoyagerIngressYaml;
import oracle.kubernetes.operator.utils.ParsedVoyagerOperatorSecurityYaml;
import oracle.kubernetes.operator.utils.ParsedVoyagerOperatorYaml;
import oracle.kubernetes.operator.utils.ParsedWeblogicDomainPersistentVolumeClaimYaml;
import oracle.kubernetes.operator.utils.ParsedWeblogicDomainPersistentVolumeYaml;
import oracle.kubernetes.operator.utils.UserProjects;

public class ScriptedDomainYamlFactory extends DomainYamlFactory {

  @Override
  public CreateDomainInputs createDefaultValues() throws Exception {
    return CreateDomainInputs.readDefaultInputsFile();
  }

  @Override
  public GeneratedDomainYamlFiles generate(DomainValues values) throws Exception {
    return new YamlGenerator(values).getGeneratedDomainYamlFiles();
  }

  static class YamlGenerator {
    private final DomainValues values;
    private DomainFiles domainFiles;

    YamlGenerator(DomainValues values) throws Exception {
      this.values = values;
      UserProjects userProjects = createUserProjectsDirectory();
      domainFiles = new DomainFiles(userProjects.getPath(), values);

      assertThat(execCreateDomain(userProjects.getPath(), values), succeedsAndPrints("Completed"));
    }

    GeneratedDomainYamlFiles getGeneratedDomainYamlFiles() throws Exception {
      GeneratedDomainYamlFiles files =
          new GeneratedDomainYamlFiles(
              new ParsedCreateWeblogicDomainJobYaml(
                  domainFiles.getCreateWeblogicDomainJobYamlPath(), values),
              new ParsedDeleteWeblogicDomainJobYaml(
                  domainFiles.getDeleteWeblogicDomainJobYamlPath(), values),
              new ParsedDomainCustomResourceYaml(
                  domainFiles.getDomainCustomResourceYamlPath(), values));

      definePersistentVolumeYaml(files);
      defineLoadBalancer(files);
      return files;
    }

    private void definePersistentVolumeYaml(GeneratedDomainYamlFiles files) throws Exception {
      files.definePersistentVolumeYaml(
          new ParsedWeblogicDomainPersistentVolumeYaml(
              domainFiles.getWeblogicDomainPersistentVolumeYamlPath(), values),
          new ParsedWeblogicDomainPersistentVolumeClaimYaml(
              domainFiles.getWeblogicDomainPersistentVolumeClaimYamlPath(), values));
    }

    private void defineLoadBalancer(GeneratedDomainYamlFiles files) throws Exception {
      switch (this.values.getLoadBalancer()) {
        case DomainValues.LOAD_BALANCER_TRAEFIK:
          defineTraefikYaml(files);
          break;
        case DomainValues.LOAD_BALANCER_APACHE:
          defineApacheYaml(files);
          break;
        case DomainValues.LOAD_BALANCER_VOYAGER:
          defineYoyagerYaml(files);
          break;
      }
    }

    private void defineTraefikYaml(GeneratedDomainYamlFiles files) throws Exception {
      files.defineTraefikYaml(
          new ParsedTraefikYaml(domainFiles.getTraefikYamlPath(), values),
          new ParsedTraefikSecurityYaml(domainFiles.getTraefikSecurityYamlPath(), values));
    }

    private void defineApacheYaml(GeneratedDomainYamlFiles files) throws Exception {
      files.defineApacheYaml(
          new ParsedApacheYaml(domainFiles.getApacheYamlPath(), values),
          new ParsedApacheSecurityYaml(domainFiles.getApacheSecurityYamlPath(), values));
    }

    private void defineYoyagerYaml(GeneratedDomainYamlFiles files) throws Exception {
      files.defineYoyagerYaml(
          new ParsedVoyagerOperatorYaml(domainFiles.getVoyagerOperatorYamlPath(), values),
          new ParsedVoyagerOperatorSecurityYaml(domainFiles.getVoyagerOperatorSecurityYamlPath()),
          new ParsedVoyagerIngressYaml(domainFiles.getVoyagerIngressYamlPath(), values));
    }
  }
}
