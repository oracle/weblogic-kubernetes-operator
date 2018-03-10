// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests that the all artifacts in the yaml files that create-domain-operator.sh
 * creates are correct when the admin node port is disabled and t3 channel is disable,
 * and there is no image pull secret.
 */
public class CreateDomainGeneratedFilesOptionalFeaturesDisabledTest {

  private static CreateDomainInputs inputs;
  private static GeneratedDomainYamlFiles generatedFiles;

  @BeforeClass
  public static void setup() throws Exception {
    inputs = CreateDomainInputs.newInputs(); // defaults to admin node port off, t3 channel off
    generatedFiles = GeneratedDomainYamlFiles.generateDomainYamlFiles(inputs);
  }

  @AfterClass
  public static void tearDown() throws Exception {
    if (generatedFiles != null) {
      generatedFiles.remove();
    }
  }

  @Test
  public void generatesCorrect_createWeblogicDomainYaml_createWeblogicDomainJob() throws Exception {
//System.out.println("MOREAUT_DEBUG create domain job =\n" + YamlUtils.newYaml().dump(generatedFiles.getCreateWeblogicDomainJobYaml().getCreateWeblogicDomainJob()));
    // TBD
  }

  @Test
  public void generatesCorrect_createWeblogicDomainYaml_createWeblogicDomainConfigMap() throws Exception {
//System.out.println("MOREAUT_DEBUG create domain config map =\n" + YamlUtils.newYaml().dump(generatedFiles.getCreateWeblogicDomainJobYaml().getCreateWeblogicDomainConfigMap()));
    // TBD
  }

  @Test
  public void generatesCorrect_domainCustomResourceYaml_domain() throws Exception {
//System.out.println("MOREAUT_DEBUG domain custom resource =\n" + YamlUtils.newYaml().dump(generatedFiles.getDomainCustomResourceYaml().getDomain()));
    // TBD
  }

  @Test
  public void generatesCorrect_traefikYaml_traefikServiceAccount() throws Exception {
//System.out.println("MOREAUT_DEBUG traefik service account =\n" + YamlUtils.newYaml().dump(generatedFiles.getTraefikYaml().getTraefikServiceAccount()));
    // TBD
  }

  @Test
  public void generatesCorrect_traefikYaml_traefikDeployment() throws Exception {
//System.out.println("MOREAUT_DEBUG traefik deployment =\n" + YamlUtils.newYaml().dump(generatedFiles.getTraefikYaml().getTraefikDeployment()));
    // TBD
  }

  @Test
  public void generatesCorrect_traefikYaml_traefikConfigMap() throws Exception {
//System.out.println("MOREAUT_DEBUG traefik config map =\n" + YamlUtils.newYaml().dump(generatedFiles.getTraefikYaml().getTraefikConfigMap()));
    // TBD
  }

  @Test
  public void generatesCorrect_traefikYaml_traefikService() throws Exception {
//System.out.println("MOREAUT_DEBUG traefik service =\n" + YamlUtils.newYaml().dump(generatedFiles.getTraefikYaml().getTraefikService()));
    // TBD
  }

  @Test
  public void generatesCorrect_traefikYaml_traefikDashboardService() throws Exception {
//System.out.println("MOREAUT_DEBUG traefik dashboard service =\n" + YamlUtils.newYaml().dump(generatedFiles.getTraefikYaml().getTraefikDashboardService()));
    // TBD
  }

  @Test
  public void generatesCorrect_traefikSecurityYaml_traefikClusterRole() throws Exception {
//System.out.println("MOREAUT_DEBUG traefik cluster role =\n" + YamlUtils.newYaml().dump(generatedFiles.getTraefikSecurityYaml().getTraefikClusterRole()));
    // TBD
  }

  @Test
  public void generatesCorrect_traefikSecurityYaml_traefikDashboardClusterRoleBinding() throws Exception {
//System.out.println("MOREAUT_DEBUG traefik dashboard cluster role binding =\n" + YamlUtils.newYaml().dump(generatedFiles.getTraefikSecurityYaml().getTraefikDashboardClusterRoleBinding()));
    // TBD
  }

  @Test
  public void generatesCorrect_weblogicDomainPersistentVolumeYaml_weblogicDomainPersistentVolume() throws Exception {
//System.out.println("MOREAUT_DEBUG weblogic domain persistent volume =\n" + YamlUtils.newYaml().dump(generatedFiles.getWeblogicDomainPersistentVolumeYaml().getWeblogicDomainPersistentVolume()));
    // TBD
  }

  @Test
  public void generatesCorrect_weblogicDomainPersistentVolumeClaimYaml_weblogicDomainPersistentVolumeClaim() throws Exception {
//System.out.println("MOREAUT_DEBUG weblogic domain persistent volume claim =\n" + YamlUtils.newYaml().dump(generatedFiles.getWeblogicDomainPersistentVolumeClaimYaml().getWeblogicDomainPersistentVolumeClaim()));
    // TBD
  }
}
