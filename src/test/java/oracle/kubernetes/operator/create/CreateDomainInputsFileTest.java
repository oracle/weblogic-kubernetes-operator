// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;

import static oracle.kubernetes.operator.create.CreateDomainInputs.*;
import static oracle.kubernetes.operator.create.ExecCreateDomain.*;
import static oracle.kubernetes.operator.create.ExecResultMatcher.*;
import static oracle.kubernetes.operator.create.YamlUtils.*;
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
    assertThat(
      readDefaultInputsFile(),
      yamlEqualTo((new CreateDomainInputs())
        .adminNodePort("30701")
        .adminPort("7001")
        .adminServerName("admin-server")
        .clusterName("cluster-1")
        .createDomainScript("/u01/weblogic/create-domain-script.sh")
        .domainName("base_domain")
        .domainUid("domain1")
        .exposeAdminNodePort("false")
        .exposeAdminT3Channel("false")
        .imagePullSecretName("")
        .javaOptions("-Dweblogic.StdoutDebugEnabled=false")
        .loadBalancer("traefik")
        .loadBalancerAdminPort("30315")
        .loadBalancerWebPort("30305")
        .managedServerCount("2")
        .managedServerNameBase("managed-server")
        .managedServerPort("8001")
        .managedServerStartCount("2")
        .namespace("default")
        .persistencePath("/scratch/k8s_dir/persistentVolume001")
        .persistenceSize("10Gi")
        .persistenceVolumeClaimName("pv001-claim")
        .persistenceVolumeName("pv001")
        .productionModeEnabled("true")
        .secretName("domain1-weblogic-credentials")
        .startupControl("AUTO")
        .t3ChannelPort("30012")
        .t3PublicAddress("kubernetes")));
  }

  @Test
  public void createDomain_usesSpecifiedInputsFileAndSucceedsAndGeneratesExpectedYamlFiles() throws Exception {
    // customize the domain uid so that we can tell that it generated the yaml files based on this inputs
    CreateDomainInputs inputs = readDefaultInputsFile().domainUid("test-domain-uid");
    assertThat(execCreateDomain(userProjects.getPath(), inputs), succeedsAndPrints("Completed"));
    DomainFiles domainFiles = new DomainFiles(userProjects.getPath(), inputs);
    assertThat(Files.isRegularFile(domainFiles.getCreateWeblogicDomainInputsYamlPath()), is(true));
    assertThat(Files.isRegularFile(domainFiles.getCreateWeblogicDomainJobYamlPath()), is(true));
    assertThat(Files.isRegularFile(domainFiles.getDomainCustomResourceYamlPath()), is(true));
    assertThat(Files.isRegularFile(domainFiles.getTraefikYamlPath()), is(true));
    assertThat(Files.isRegularFile(domainFiles.getTraefikSecurityYamlPath()), is(true));
    assertThat(Files.isRegularFile(domainFiles.getWeblogicDomainPersistentVolumeYamlPath()), is(true));
    assertThat(Files.isRegularFile(domainFiles.getWeblogicDomainPersistentVolumeClaimYamlPath()), is(true));
    // TBD - assert that the generated per-domain directory doesn't contain any extra files?
    // TBD - assert that the copy of the inputs in generated per-domain directory matches the origin one
  }
}
