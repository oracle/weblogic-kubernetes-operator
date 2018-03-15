// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
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
        .loadBalancer(LOAD_BALANCER_TRAEFIK)
        .loadBalancerAdminPort("30315")
        .loadBalancerWebPort("30305")
        .managedServerCount("2")
        .managedServerNameBase("managed-server")
        .managedServerPort("8001")
        .managedServerStartCount("2")
        .namespace("default")
        .nfsServer("nfsServer")
        .persistencePath("/scratch/k8s_dir/persistentVolume001")
        .persistenceSize("10Gi")
        .persistenceType(PERSISTENCE_TYPE_HOST_PATH)
        .persistenceVolumeClaimName("pv001-claim")
        .persistenceVolumeName("pv001")
        .productionModeEnabled("true")
        .secretName("domain1-weblogic-credentials")
        .startupControl(STARTUP_CONTROL_AUTO)
        .t3ChannelPort("30012")
        .t3PublicAddress("kubernetes")));
  }

  @Test
  public void createDomain_usesSpecifiedInputsFileAndSucceedsAndGeneratesExpectedYamlFiles() throws Exception {
    // customize the domain uid so that we can tell that it generated the yaml files based on this inputs
    CreateDomainInputs inputs = readDefaultInputsFile().domainUid("test-domain-uid");
    assertThat(execCreateDomain(userProjects.getPath(), inputs), succeedsAndPrints("Completed"));

    // Make sure the generated directory has the correct list of files
    DomainFiles domainFiles = new DomainFiles(userProjects.getPath(), inputs);
    List<Path> expectedFiles = domainFiles.getExpectedContents(true); // include the directory too
    List<Path> actualFiles = userProjects.getContents(domainFiles.getWeblogicDomainPath());
    assertThat(
      actualFiles,
      containsInAnyOrder(expectedFiles.toArray(new Path[expectedFiles.size()])));

    // Make sure that the yaml files are regular files
    for (Path path : domainFiles.getExpectedContents(false)) { // don't include the directory too
      assertThat("Expect that " + path + " is a regular file", Files.isRegularFile(path), is(true));
    }
  }
}
