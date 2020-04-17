// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.DomainCrd;
import oracle.kubernetes.operator.utils.ExecCommand;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.jupiter.api.Assertions;

public class MiiBaseTest extends BaseTest {
  /**
   * Creates a map with customized domain input attributes using suffixCount and prefix
   * to make the namespaces and ports unique for model in image.
   *
   * @param suffixCount unique numeric value
   * @param prefix      prefix for the artifact names
   * @return map with domain input attributes
   */
  public Map<String, Object> createModelInImageMap(
      int suffixCount, String prefix) {
    Map<String, Object> domainMap = createDomainMap(suffixCount, prefix);
    domainMap.put("domainHomeSourceType", "FromModel");
    domainMap.put("domainHomeImageBase",
        getWeblogicImageName() + ":" + getWeblogicImageTag());
    domainMap.put("logHomeOnPV", "true");
    //domainMap.put("wdtDomainType", "WLS");

    // To get unique image name
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Date date = new Date();
    String currentDateTime = dateFormat.format(date) + "-" + System.currentTimeMillis();

    if (prefix != null && !prefix.trim().equals("")) {
      domainMap.put("image", prefix.toLowerCase() + "-modelinimage:" + currentDateTime);
    } else {
      domainMap.put("image", "modelinimage:" + currentDateTime);
    }
    return domainMap;
  }

  /**
   * Create domain using model in image.
   * @param domainUidPrefix domain UID prefix
   * @param domainNS domain namespace
   * @param wdtModelFile file should be under test/resouces/model-in-image dir,
   *                     value can be ./model.wls.yaml
   * @param wdtModelPropertiesFile file should be under test/resouces/model-in-image dir,
   *                               value can be ./model.empty.properties
   * @param cmFile creates configmap from this file or dir
   */
  public Domain createMiiDomainWithConfigMap(String domainUidPrefix,
                                             String domainNS, String wdtModelFile, String wdtModelPropertiesFile,
                                             String cmFile, String wdtDomainType) throws Exception {
    Map<String, Object> domainMap =
        createModelInImageMap(getNewSuffixCount(), domainUidPrefix);
    // config map before deploying domain crd
    String cmName = domainMap.get("domainUID") + "-mii-config-map";

    domainMap.put("namespace", domainNS);
    domainMap.put("wdtModelFile", wdtModelFile);
    domainMap.put("wdtModelPropertiesFile", wdtModelPropertiesFile);
    domainMap.put("wdtDomainType", wdtDomainType);

    domainMap.put("miiConfigMap", cmName);
    domainMap.put("miiConfigMapFileOrDir", cmFile);

    Domain domain = TestUtils.createDomain(domainMap);
    // domain = new Domain(domainMap, true, false);
    domain.verifyDomainCreated();
    return domain;
  }

  /**
   * Modify the domain yaml to change domain-level restart version.
   * @param domainNS the domain namespace
   * @param domainUid the domain UID
   * @param versionNo restartVersion number of domain
   */
  protected void createDomainImage(Map<String, Object> domainMap, String imageName,
                                   String modelFile, String modelPropFile) {
    String domainBaseImageName = (String) domainMap.get("domainHomeImageBase");
    String domainUid = (String) domainMap.get("domainUID");
    String domainName = (String) domainMap.get("domainName");
    ExecResult result = null;

    // Get the map of any additional environment vars, or null
    Map<String, String> additionalEnvMap = (Map<String, String>) domainMap.get("additionalEnvMap");
    String resultsDir = (String) domainMap.get("resultDir");
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
      // creating image
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

      Assertions.fail(errorMsg.toString(), ex.getCause());
    }
  }

  /**
   * Modify the domain yaml to change image name.
   * @param domainNS the domain namespace
   * @param domainUid the domain UID
   * @param imageName image name
   */
  protected void modifyDomainYamlWithImageName(
      Domain domain, String domainNS, String imageName) {
    ExecResult result = null;
    String versionNo = getRestartVersion(domainNS, domain.getDomainUid());
    StringBuffer patchDomainCmd = new StringBuffer("kubectl -n ");
    patchDomainCmd
        .append(domainNS)
        .append(" patch domain ")
        .append(domain.getDomainUid())
        .append(" --type='json' ")
        .append(" -p='[{\"op\": \"replace\", \"path\": \"/spec/image\", \"value\": \"'")
        .append(imageName)
        .append("'\" }]'");

    try {
      // patching the domain
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

      Assertions.fail(errorMsg.toString(), ex.getCause());
    }
  }

  /**
   * Modify the domain yaml to add reference to config map and change domain-level restart version.
   * @param cmName Config map name
   * @param domain the domain
   * @throws Exception on failure
   */
  public void modifyDomainYamlWithNewConfigMapAndDomainRestartVersion(
      String cmName, Domain domain) throws Exception {
    String originalYaml =
        getUserProjectsDir()
            + "/weblogic-domains/"
            + domain.getDomainUid()
            + "/domain.yaml";

    // Modify the original domain yaml to include restartVersion in admin server node
    DomainCrd crd = new DomainCrd(originalYaml);
    Map<String, String> objectNode = new HashMap<>();
    objectNode.put("restartVersion", "v1.1");
    crd.addObjectNodeToDomain(objectNode);
    String modYaml = crd.getYamlTree();
    LoggerHelper.getLocal().log(Level.INFO, modYaml);

    //change config map name to new config map
    modYaml.replaceAll((String)domain.getDomainMap().get("miiConfigMap"), cmName);

    // Write the modified yaml to a new file
    Path path = Paths.get(getUserProjectsDir()
        + "/weblogic-domains/"
        + domain.getDomainUid(), "modified.domain.yaml");
    LoggerHelper.getLocal().log(Level.INFO, "Path of the modified domain.yaml :{0}", path.toString());
    Charset charset = StandardCharsets.UTF_8;
    Files.write(path, modYaml.getBytes(charset));

    // Apply the new yaml to update the domain crd
    LoggerHelper.getLocal().log(Level.INFO, "kubectl apply -f {0}", path.toString());
    ExecResult exec = TestUtils.exec("kubectl apply -f " + path.toString());
    LoggerHelper.getLocal().log(Level.INFO, exec.stdout());

  }

  /**
   * Modify the domain yaml to change domain-level restart version.
   * @param domainNS the domain namespace
   * @param domainUid the domain UID
   * @param versionNo restartVersion number of domain
   */
  protected void modifyDomainYamlWithRestartVersion(Domain domain, String domainNS) {
    ExecResult result = null;
    String versionNo = getRestartVersion(domainNS, domain.getDomainUid());
    StringBuffer patchDomainCmd = new StringBuffer("kubectl -n ");
    patchDomainCmd
        .append(domainNS)
        .append(" patch domain ")
        .append(domain.getDomainUid())
        .append(" --type='json' ")
        .append(" -p='[{\"op\": \"replace\", \"path\": \"/spec/restartVersion\", \"value\": \"'")
        .append(versionNo)
        .append("'\" }]'");

    try {
      // patching the domain
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

      Assertions.fail(errorMsg.toString(), ex.getCause());
    }
  }

  private String getRestartVersion(String domainNS, String domainUid) {
    String versionNo = "1";
    StringBuffer getVersionCmd = new StringBuffer("kubectl -n ");
    getVersionCmd
        .append(domainNS)
        .append(" get domain ")
        .append(domainUid)
        .append("-o=jsonpath='{.spec.restartVersion}'");

    LoggerHelper.getLocal().log(Level.INFO, "Command to get restartVersion: " + getVersionCmd);
    try {
      ExecResult result = TestUtils.exec(getVersionCmd.toString());
      String existinVersion = result.stdout();
      LoggerHelper.getLocal().log(Level.INFO, "Existing restartVersion is: " + existinVersion);

      // check restartVersion number is digit
      if (existinVersion.matches("-?(0|[1-9]\\d*)")) {
        int number = Integer.parseInt(existinVersion);
        // if restartVersion is a digit, increase it by 1
        versionNo = String.valueOf(Integer.parseInt(versionNo) + number);
      } else {
        // if restartVersion is not a digit, append 1 to it
        versionNo = existinVersion + versionNo;
      }
    } catch (Exception ex) {
      if (ex.getMessage().contains("not found")) {
        LoggerHelper.getLocal().log(Level.INFO, "Not Version num found. Set the restartVersion the first time");
      }
    }

    LoggerHelper.getLocal().log(Level.INFO, "New restartVersion is: " + versionNo);

    return versionNo;
  }

  enum WdtDomainType {
    WLS("WLS"), JRF("JRF"), RestrictedJRF("RestrictedJRF");

    private String type;

    WdtDomainType(String type) {
      this.type = type;
    }

    public String geWdtDomainType() {
      return type;
    }
  }
}
