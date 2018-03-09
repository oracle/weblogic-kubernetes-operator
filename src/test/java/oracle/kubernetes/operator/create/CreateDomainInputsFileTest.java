// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;

import static oracle.kubernetes.operator.create.CreateDomainInputs.*;
import static oracle.kubernetes.operator.create.ExecCreateDomain.execCreateDomain;
import static oracle.kubernetes.operator.create.ExecResultMatcher.succeedsAndPrints;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Tests that:
 * - the default create-weblogic-domain-inputs.yaml file has the expected contents
 * - create-weblogic-domain.sh with -i uses the specified inputs file
 */
public class CreateDomainInputsFileTest {

  private UserProjects userProjects;

  @Before
  public void setup() throws Exception {
    userProjects = UserProjects.createUserProjectsDirectory();
  }

  @After
  public void tearDown() throws Exception {
    if (userProjects != null) {
      userProjects.remove();
    }
  }

  @Test
  public void defaultInputsFile_hasCorrectContents() throws Exception {
    CreateDomainInputs i = readDefaultInputsFile();
    assertThat(i.getAdminNodePort(), equalTo("30701"));
    assertThat(i.getAdminPort(), equalTo("7001"));
    assertThat(i.getAdminServerName(), equalTo("admin-server"));
    assertThat(i.getClusterName(), equalTo("cluster-1"));
    assertThat(i.getCreateDomainScript(), equalTo("/u01/weblogic/create-domain-script.sh"));
    assertThat(i.getDomainName(), equalTo("base_domain"));
    assertThat(i.getDomainUid(), equalTo("domain1"));
    assertThat(i.getExposeAdminNodePort(), equalTo("false"));
    assertThat(i.getExposeAdminT3Channel(), equalTo("false"));
    assertThat(i.getImagePullSecretName(), equalTo(""));
    assertThat(i.getJavaOptions(), equalTo("-Dweblogic.StdoutDebugEnabled=false"));
    assertThat(i.getLoadBalancer(), equalTo("traefik"));
    assertThat(i.getLoadBalancerAdminPort(), equalTo("30315"));
    assertThat(i.getLoadBalancerWebPort(), equalTo("30305"));
    assertThat(i.getManagedServerCount(), equalTo("2"));
    assertThat(i.getManagedServerNameBase(), equalTo("managed-server"));
    assertThat(i.getManagedServerPort(), equalTo("8001"));
    assertThat(i.getManagedServerStartCount(), equalTo("2"));
    assertThat(i.getNamespace(), equalTo("default"));
    assertThat(i.getPersistencePath(), equalTo("/scratch/k8s_dir/persistentVolume001"));
    assertThat(i.getPersistenceSize(), equalTo("10Gi"));
    assertThat(i.getPersistenceVolumeClaimName(), equalTo("pv001-claim"));
    assertThat(i.getPersistenceVolumeName(), equalTo("pv001"));
    assertThat(i.getProductionModeEnabled(), equalTo("true"));
    assertThat(i.getSecretName(), equalTo("domain1-weblogic-credentials"));
    assertThat(i.getStartupControl(), equalTo("AUTO"));
    assertThat(i.getT3ChannelPort(), equalTo("30012"));
  }

  @Test
  public void createDomain_usesSpecifiedInputsFileAndSucceedsAndGeneratesExpectedYamlFiles() throws Exception {
    // customize the domain uid so that we can tell that it generated the yaml files based on this inputs
    CreateDomainInputs inputs = readDefaultInputsFile().domainUid("test-domain-uid");
    assertThat(execCreateDomain(userProjects.getPath(), inputs), succeedsAndPrints("Completed"));
    DomainFiles domainFiles = new DomainFiles(userProjects.getPath(), inputs);
    assertThat(Files.isRegularFile(domainFiles.getCreateWeblogicDomainJobYamlPath()), is(true));
    assertThat(Files.isRegularFile(domainFiles.getDomainCustomResourceYamlPath()), is(true));
    assertThat(Files.isRegularFile(domainFiles.getTraefikYamlPath()), is(true));
    assertThat(Files.isRegularFile(domainFiles.getTraefikSecurityYamlPath()), is(true));
    assertThat(Files.isRegularFile(domainFiles.getWeblogicDomainPersistentVolumeYamlPath()), is(true));
    assertThat(Files.isRegularFile(domainFiles.getWeblogicDomainPersistentVolumeClaimYamlPath()), is(true));
  }
}
