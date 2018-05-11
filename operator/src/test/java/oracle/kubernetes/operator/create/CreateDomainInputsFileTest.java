// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import static oracle.kubernetes.operator.VersionConstants.*;
import static oracle.kubernetes.operator.utils.CreateDomainInputs.*;
import static oracle.kubernetes.operator.utils.ExecCreateDomain.*;
import static oracle.kubernetes.operator.utils.ExecResultMatcher.*;
import static oracle.kubernetes.operator.utils.YamlUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import oracle.kubernetes.operator.utils.CreateDomainInputs;
import oracle.kubernetes.operator.utils.DomainFiles;
import oracle.kubernetes.operator.utils.UserProjects;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests that: - the default create-weblogic-domain-inputs.yaml file has the expected contents -
 * create-weblogic-domain.sh with -i uses the specified inputs file
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
        yamlEqualTo(
            (new CreateDomainInputs())
                .version(CREATE_WEBLOGIC_DOMAIN_INPUTS_V1)
                .adminNodePort("30701")
                .adminPort("7001")
                .adminServerName("admin-server")
                .clusterName("cluster-1")
                .clusterType("DYNAMIC")
                .domainName("base_domain")
                .domainUID("")
                .exposeAdminNodePort("false")
                .exposeAdminT3Channel("false")
                .weblogicImagePullSecretName("")
                .javaOptions("-Dweblogic.StdoutDebugEnabled=false")
                .loadBalancer(LOAD_BALANCER_TRAEFIK)
                .loadBalancerDashboardPort("30315")
                .loadBalancerWebPort("30305")
                .loadBalancerVolumePath("")
                .loadBalancerAppPrepath("/")
                .configuredManagedServerCount("2")
                .managedServerNameBase("managed-server")
                .managedServerPort("8001")
                .initialManagedServerReplicas("2")
                .namespace("default")
                .weblogicDomainStorageNFSServer("")
                .weblogicDomainStoragePath("")
                .weblogicDomainStorageReclaimPolicy(STORAGE_RECLAIM_POLICY_RETAIN)
                .weblogicDomainStorageSize("10Gi")
                .weblogicDomainStorageType(STORAGE_TYPE_HOST_PATH)
                .productionModeEnabled("true")
                .weblogicCredentialsSecretName("domain1-weblogic-credentials")
                .startupControl(STARTUP_CONTROL_AUTO)
                .t3ChannelPort("30012")
                .t3PublicAddress("kubernetes")));
  }

  @Test
  public void
      createDomainWithCompletedDefaultsInputsFile_usesSpecifiedInputsFileAndSucceedsAndGeneratesExpectedYamlFiles()
          throws Exception {
    // customize the domain uid and weblogic storage path so that we have a valid inputs file
    CreateDomainInputs inputs =
        readDefaultInputsFile()
            .domainUID("test-domain-uid")
            .weblogicDomainStoragePath("/scratch/k8s_dir/domain1");
    assertThat(execCreateDomain(userProjects.getPath(), inputs), succeedsAndPrints("Completed"));
    assertThatOnlyTheExpectedGeneratedYamlFilesExist(inputs);
  }

  @Test
  public void
      createDomainFromPreCreatedInputsFileInPreCreatedOutputDirectory_usesSpecifiedInputsFileAndSucceedsAndGeneratesExpectedYamlFiles()
          throws Exception {
    // customize the domain uid and weblogic storage path so that we have a valid inputs file
    CreateDomainInputs inputs =
        readDefaultInputsFile()
            .domainUID("test-domain-uid")
            .weblogicDomainStoragePath("/scratch/k8s_dir/domain1");
    // pre-create the output directory and the inputs file in the output directory, then
    // use that inputs file to create the domain
    DomainFiles domainFiles = new DomainFiles(userProjects.getPath(), inputs);
    Files.createDirectories(domainFiles.getWeblogicDomainPath());
    assertThat(
        execCreateDomain(
            userProjects.getPath(), inputs, domainFiles.getCreateWeblogicDomainInputsYamlPath()),
        succeedsAndPrints("Completed"));
    assertThatOnlyTheExpectedGeneratedYamlFilesExist(inputs);
  }

  private void assertThatOnlyTheExpectedGeneratedYamlFilesExist(CreateDomainInputs inputs)
      throws Exception {
    // Make sure the generated directory has the correct list of files
    DomainFiles domainFiles = new DomainFiles(userProjects.getPath(), inputs);
    List<Path> expectedFiles = domainFiles.getExpectedContents(true); // include the directory too
    List<Path> actualFiles = userProjects.getContents(domainFiles.getWeblogicDomainPath());
    assertThat(
        actualFiles, containsInAnyOrder(expectedFiles.toArray(new Path[expectedFiles.size()])));

    // Make sure that the yaml files are regular files
    for (Path path : domainFiles.getExpectedContents(false)) { // don't include the directory too
      assertThat("Expect that " + path + " is a regular file", Files.isRegularFile(path), is(true));
    }
  }
}
