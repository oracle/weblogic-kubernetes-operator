// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecCommand;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.TestUtils;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Base class which contains common methods to test config update
 * using model in image. ItMiiConfigUpdate tests can extend this class.
 */
public class MiiConfigUpdateBaseTest extends MiiBaseTest {
  protected static final String configMapSuffix = "-mii-config-map";
  protected static final String dsName = "MyDataSource";
  protected static final String jndiName = "jdbc/generic1";
  // values for property oracle.jdbc.ReadTimeout set in model files
  // It defines Read timeout while reading from the socket.
  protected static final String readTimeout_1 = "30001";
  protected static final String readTimeout_2 = "30002";

  /**
   * Create domain using model in image.
   * @param createDS a boolean value to determine whether or not
   *                 to config a JDBC DS when creating domain
   * @param domainNS domain namespace name
   * @param testClassName test class name that calls this method
   */
  protected Domain createDomainUsingMii(boolean createDS, String domainNS, String testClassName) {
    final String cmFile = "model.empty.properties";
    String wdtModelFile = "model.wls.yaml";
    String wdtModelPropFile = "model.properties";
    Domain domain = null;

    if (createDS) {
      wdtModelFile = "model.jdbc.image.yaml";
      wdtModelPropFile = "model.jdbc.image.properties";
    }

    StringBuffer paramBuff = new StringBuffer("Creating a Domain with: ");
    paramBuff
        .append("testClassName=")
        .append(testClassName)
        .append(", domainNS=")
        .append(domainNS)
        .append(", wdtModelFile=")
        .append(wdtModelFile)
        .append(", wdtModelPropFile=")
        .append(wdtModelPropFile)
        .append(", cmFile=")
        .append(cmFile)
        .append(", WdtDomainType=")
        .append(WdtDomainType.WLS.geWdtDomainType());

    LoggerHelper.getLocal().log(Level.INFO, "Params used to create domain: " + paramBuff);

    try {
      domain = createMiiDomainWithConfigMap(
        testClassName,
        domainNS,
        wdtModelFile,
        wdtModelPropFile,
        cmFile,
        WdtDomainType.WLS.geWdtDomainType());
    } catch (Exception ex) {
      LoggerHelper.getLocal().log(Level.INFO, "FAILURE: command: "
          + paramBuff
          + " failed \n"
          + ex.getMessage());

      ex.printStackTrace();
    }

    return domain;
  }

  /**
   * Re-create cm to update config and patch domain to change domain-level restart version.
   * @param domain the Domain object where to patch domain and update domain config values
   * @param destDir destination directory name to copy model files to
   * @param modelFiles names of model files to copy
   */
  protected void createCmAndPatchDomain(Domain domain, String destDir, String[] modelFiles) {
    // copy model files that contains JDBC DS to a dir to re-create cm
    copyTestModelFiles(destDir, modelFiles);

    // re-create cm to update config and verify cm is created successfully
    wdtConfigUpdateCm(destDir, domain);

    // patch domain to change domain-level restart version and verify domain restarted successfully
    modifyDomainYamlWithRestartVersion(domain);
  }

  /**
   * Create image to update config and patch domain to change domain-level restart version.
   * @param domain the Domain object where to patch domain and update domain config values
   * @param destDir destination directory name to copy model files to
   * @param modelFiles names of model files to copy
   */
  protected void createImageAndPatchDomain(Domain domain,
                                           String imageName,
                                           String wdtModelFile,
                                           String wdtModelPropFile) {
    Map<String, Object> domainMap = domain.getDomainMap();
    wdtConfigUpdateImage(domainMap, imageName, wdtModelFile, wdtModelPropFile);

    // push the image to docker repository
    if (BaseTest.SHARED_CLUSTER) {
      try {
        TestUtils.loginAndPushImageToOcir(imageName);
      } catch (Exception ex) {
        ex.printStackTrace();
        fail("Failed to push image <" + imageName + "> to Docker repo");
      }
    }

    // patch to change image tag and verify domain restarted successfully
    modifyDomainYamlWithImageTag(domain, imageName);
  }

  /**
   * Copy model files from source dir to test dir.
   * @param destDir destination directory name to copy model files to
   * @param modelFiles names of model files to copy
   */
  protected void copyTestModelFiles(String destDir, String[] modelFiles) {
    LoggerHelper.getLocal().log(Level.INFO, "Creating configMap");
    final String origDir = BaseTest.getProjectRoot()
        + "/integration-tests/src/test/resources/model-in-image";

    try {
      Files.deleteIfExists(Paths.get(destDir));
      Files.createDirectories(Paths.get(destDir));

      for (String modelFile : modelFiles) {
        TestUtils.copyFile(origDir + "/" + modelFile, destDir + "/" + modelFile);
        LoggerHelper.getLocal().log(Level.INFO, "Copied <" + origDir
            + "/" + modelFile + "> to <" + destDir + "/" + modelFile + ">");
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      fail("Failed to copy model files", ex.getCause());
    }
  }

  /**
   * Update an existing cm.
   * @param fileOrDirPath a directory path where to get model files
   * @param domain the Domain object where to update domain config values
   */
  protected void wdtConfigUpdateCm(String fileOrDirPath, Domain domain) {
    LoggerHelper.getLocal().log(Level.INFO, "Creating configMap...");
    // get domain UID and domain namespace name
    final String domainUid = domain.getDomainUid();
    final String domainNS = domain.getDomainNs();
    final String cmName = domainUid + configMapSuffix;
    final String label = "weblogic.domainUID=" + domainUid;

    try {
      // Re-create config map after deploying domain crd
      TestUtils.createConfigMap(cmName, fileOrDirPath, domainNS, label);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail("Failed to create cm.\n", ex.getCause());
    }
  }

  /**
   * Create a new or update an existing image using model in image.
   * @param domainMap a Java Map object that storing key and value pairs used to create the Domain
   * @param imageName image name to be created or updated
   * @param modelFile a model to describe a WebLogic Server domain configuration.
   * @param modelPropFile a file to specify values of variable tokens declared in model file(s) at runtime
   */
  protected void wdtConfigUpdateImage(Map<String, Object> domainMap, String imageName,
                                      String modelFile, String modelPropFile) {
    // get domain base image, domain UID and domain name
    final String domainBaseImageName = (String) domainMap.get("domainHomeImageBase");
    final String domainUid = (String) domainMap.get("domainUID");
    final String domainName = (String) domainMap.get("domainName");
    ExecResult result = null;

    // Get the map of any additional environment vars, or null
    Map<String, String> additionalEnvMap = (Map<String, String>) domainMap.get("additionalEnvMap");
    final String resultsDir = (String) domainMap.get("resultDir");
    StringBuffer createDomainImageScriptCmd = new StringBuffer("export WDT_VERSION=");

    createDomainImageScriptCmd.append(BaseTest.WDT_VERSION).append(" && ")
      .append(getUserProjectsDir())
      .append("/weblogic-domains/")
      .append(domainName)
      .append("/miiWorkDir/")
      .append("imagetool/bin/imagetool.sh update")
      .append(" --tag ")
      .append(imageName)
      .append(" --fromImage ")
      .append(domainBaseImageName)
      .append(" --wdtModel ")
      .append(resultsDir)
      .append("/samples/model-in-image/")
      .append(modelFile)
      .append(" --wdtVariables ")
      .append(resultsDir)
      .append("/samples/model-in-image/")
      .append(modelPropFile)
      .append(" --wdtArchive ")
      .append(getUserProjectsDir())
      .append("/weblogic-domains/")
      .append(domainName)
      .append("/miiWorkDir/models/archive.zip")
      .append(" --wdtModelOnly ")
      .append(" --wdtDomainType ")
      .append(WdtDomainType.WLS.geWdtDomainType());

    try {
      // creating a new or updating an existing image
      LoggerHelper.getLocal().log(Level.INFO, "Command to create domain image: "
          + createDomainImageScriptCmd);
      result = ExecCommand.exec(createDomainImageScriptCmd.toString(), true, additionalEnvMap);
    } catch (Exception ex) {
      ex.printStackTrace();
      StringBuffer errorMsg = new StringBuffer("FAILURE: command: ");
      errorMsg
          .append(createDomainImageScriptCmd)
          .append(" failed, returned ")
          .append(result.stdout())
          .append("\n")
          .append(result.stderr());

      fail(errorMsg.toString(), ex.getCause());
    }
  }

  /**
   * Patch the domain to add reference to image and verify the domain restarted.
   * @param domain the Domain where to update the image
   * @param imageName image name to be updated in the Domain
   */
  protected void modifyDomainYamlWithImageTag(Domain domain, String imageName) {
    // get domain namespace name and domain UID
    final String domainNS = domain.getDomainNs();
    final String domainUid = domain.getDomainUid();
    ExecResult result = null;

    StringBuffer patchDomainCmd = new StringBuffer("kubectl -n ");
    patchDomainCmd
        .append(domainNS)
        .append(" patch domain ")
        .append(domainUid)
        .append(" --type='json' ")
        .append(" -p='[{\"op\": \"replace\", \"path\": \"/spec/image\", \"value\": \"'")
        .append(imageName)
        .append("'\" }]'");

    try {
      // patch the domain
      LoggerHelper.getLocal().log(Level.INFO, "Command to patch domain: " + patchDomainCmd);
      result = TestUtils.exec(patchDomainCmd.toString());
      LoggerHelper.getLocal().log(Level.INFO, "Domain patch result: " + result.stdout());

      // verify the domain restarted
      domain.verifyAdminServerRestarted();
      domain.verifyManagedServersRestarted();
    } catch (Exception ex) {
      ex.printStackTrace();
      StringBuffer errorMsg = new StringBuffer("FAILURE: command: ");
      errorMsg
          .append(patchDomainCmd)
          .append(" failed, returned ")
          .append(result.stdout())
          .append("\n")
          .append(result.stderr());

      fail(errorMsg.toString(), ex.getCause());
    }
  }

  /**
   * Retrieve JNDI name from server pod.
   * @param domain the Domain object where to get domain config values
   */
  protected String getJndiName(Domain domain) {
    // get domain name and domain namespace name
    Map<String, Object> domainMap = domain.getDomainMap();
    final String domainName = (String) domainMap.get("domainName");
    final String domainNS = domain.getDomainNs();
    ExecResult result = null;
    String jdbcDsStr = "";

    // check JDBC DS update
    StringBuffer cmdStrBuff = new StringBuffer("kubectl -n ");
    cmdStrBuff
        .append(domainNS)
        .append(" exec -it ")
        .append(domain.getDomainUid())
        .append("-")
        .append(domain.getAdminServerName())
        .append(" -- bash -c 'cd /u01/oracle/user_projects/domains/")
        .append(domainName)
        .append("/config/jdbc/")
        .append(" && grep -R ")
        .append(jndiName)
        .append("'");

    try {
      LoggerHelper.getLocal().log(Level.INFO, "Command to exec: " + cmdStrBuff);
      result = TestUtils.exec(cmdStrBuff.toString());
      LoggerHelper.getLocal().log(Level.INFO, "JDBC DS info from server pod: " + result.stdout());
      jdbcDsStr  = result.stdout();
    } catch (Exception ex) {
      StringBuffer errorMsg = new StringBuffer("FAILURE: command: ");
      errorMsg
          .append(cmdStrBuff)
          .append(" failed, returned ")
          .append(result.stdout())
          .append("\n")
          .append(result.stderr());

      LoggerHelper.getLocal().log(Level.INFO, errorMsg + "\n" + ex.getMessage());
      ex.printStackTrace();
    }

    return jdbcDsStr;
  }

  /**
   * Retrieve JDBC DS prop values from server pod via WLST.
   * @param destDir destination directory name to test python file to
   * @param domain the Domain object where to get domain config values
   */
  protected String getJdbcResources(String destDir, Domain domain) {
    // get domain name and domain namespace name
    Map<String, Object> domainMap = domain.getDomainMap();
    String domainName = (String) domainMap.get("domainName");
    final String domainNS = domain.getDomainNs();
    ExecResult result = null;
    String jdbcDsStr = "";

    try {
      // copy verification file to test dir
      final String origDir = BaseTest.getProjectRoot()
          + "/integration-tests/src/test/resources/model-in-image/scripts";
      final String pyFileName = "verify-jdbc-resource.py";
      Files.createDirectories(Paths.get(destDir));
      TestUtils.copyFile(origDir + "/" + pyFileName, destDir + "/" + pyFileName);

      // replace var in verification file
      String tempDir = getResultDir() + "/configupdatetemp-" + domainNS;
      Files.createDirectories(Paths.get(tempDir));
      String content =
          new String(Files.readAllBytes(Paths.get(destDir + "/" + pyFileName)), StandardCharsets.UTF_8);
      content = content.replaceAll("DOMAINNAME", domainName);
      Files.write(
          Paths.get(tempDir, pyFileName),
          content.getBytes(StandardCharsets.UTF_8));

      // get server pod name
      final String adminPodName =
          domain.getDomainUid() + "-" + domain.getAdminServerName();

      // copy verification file to the pod
      StringBuffer cmdStrBuff = new StringBuffer("kubectl -n ");
      cmdStrBuff
          .append(domainNS)
          .append(" exec -it ")
          .append(adminPodName)
          .append(" -- bash -c 'mkdir -p ")
          .append(BaseTest.getAppLocationInPod())
          .append("'");
      LoggerHelper.getLocal().log(Level.INFO, "Command to exec: " + cmdStrBuff);
      TestUtils.exec(cmdStrBuff.toString(), true);

      TestUtils.copyFileViaCat(
          Paths.get(tempDir, pyFileName).toString(),
          BaseTest.getAppLocationInPod() + "/" + pyFileName,
          adminPodName,
          domainNS);

      cmdStrBuff = new StringBuffer("kubectl -n ");
      cmdStrBuff
          .append(domainNS)
          .append(" exec -it ")
          .append(adminPodName)
          .append(" -- bash -c 'wlst.sh ")
          .append(BaseTest.getAppLocationInPod())
          .append("/")
          .append(pyFileName)
          .append("'");
      LoggerHelper.getLocal().log(Level.INFO, "Command to exec: " + cmdStrBuff);
      result = TestUtils.exec(cmdStrBuff.toString(), true);
      jdbcDsStr  = result.stdout();
      //clean up
      LoggerHelper.getLocal().log(Level.INFO, "Deleting: " + destDir + "/" + pyFileName);
      Files.deleteIfExists(Paths.get(destDir + "/" + pyFileName));
    } catch (Exception ex) {
      LoggerHelper.getLocal().log(Level.INFO, "Failed to get DS prop values.\n" + ex.getMessage());
      ex.printStackTrace();
    }

    return jdbcDsStr;
  }
}
