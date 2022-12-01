// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.util.List;
import java.util.Map;

import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.DEFAULT_MODEL_IMAGE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.DEFAULT_MODEL_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS;


/**
 * Contains the parameters for creating an image using the WebLogic Image Tool.
 *
 */
public class WitParams {

  // The name of the image that is used as the base of a new image
  private String baseImageName;

  // The tag of the image that is used as the base of a new image
  private String baseImageTag;

  // The name of the to be generated image
  private String modelImageName;

  // The name of the to be generated image
  private String modelImageTag;

  // A comma separated list of the names of the WDT model yaml files
  private List<String> modelFiles;

  // A comma separated list of the names of the WDT model properties files
  private List<String> modelVariableFiles;

  // A comma separated list of the names of the WDT model achieve files
  private List<String> modelArchiveFiles;

  // The version of WDT
  private String wdtVersion;

  // The type of the WebLogic domain. The valid values are "WLS, "JRF", and "Restricted JRF"
  private String domainType;

  // The Path to the domain_home for WDT
  private String domainHome;

  // WDT operation for "update" command. Supported values are "CREATE", "UPDATE", "DEPLOY"
  private String wdtOperation;

  //Install WDT and copy the models to the image, but do not create the domain
  private boolean wdtModelOnly;

  // Custom WDT model home
  private String wdtModelHome;

  // Custom WDT model home
  private String target;

  // The env variables that are needed for running WIT
  private Map<String, String> env;

  // Whether the output of the command is redirected to system out
  private boolean redirect;

  // Path to a file with additional build commands
  private String additionalBuildCommands;

  // Additional files that are required by additionalBuildCommands
  private String additionalBuildFiles;

  // Target folder in the auxiliary image for the WDT install and models
  private String wdtHome;

  // userid:groupid for JDK/Middleware installs and patches
  private String useridGroupid;

  // Executable to process the Dockerfile. Use the full path of the executable if not on your path
  private String builder;

  // Networking mode for the RUN instructions during the image build. See --network for build command
  private String buildNetwork;

  // Skip build execution and print the Dockerfile to stdout
  private boolean dryRun;

  // Proxy for the HTTP protocol. Example: http://myproxy:80
  private String httpProxyUrl;

  // Proxy for the HTTPS protocol. Example: https://myproxy:80
  private String httpsProxyUrl;

  // Override the default package manager for the base image's operating system.
  // Supported values: APK, APTGET, NONE, YUM, ZYPPER
  private String packageManager;

  // Whether to always attempt to pull a newer version of base images during the build
  private boolean pull;

  // Do not delete the build context folder, intermediate images, and failed build containers. For debugging purposes.
  private boolean skipCleanup;

  /**
   * Generate default WIT parameters.
   * @return WIT parameters
   */
  public WitParams defaults() {
    this.baseImageName(WEBLOGIC_IMAGE_NAME)
        .baseImageTag(WEBLOGIC_IMAGE_TAG)
        .modelImageName(DEFAULT_MODEL_IMAGE_NAME)
        .modelImageTag(DEFAULT_MODEL_IMAGE_TAG)
        .domainType(WLS);
    return this;
  }

  public WitParams baseImageName(String baseImageName) {
    this.baseImageName = baseImageName;
    return this;
  }

  public String baseImageName() {
    return baseImageName;
  }

  public WitParams baseImageTag(String baseImageTag) {
    this.baseImageTag = baseImageTag;
    return this;
  }

  public String baseImageTag() {
    return baseImageTag;
  }

  public WitParams modelImageName(String modelImageName) {
    this.modelImageName = modelImageName;
    return this;
  }

  public String modelImageName() {
    return modelImageName;
  }

  public WitParams modelImageTag(String modelImageTag) {
    this.modelImageTag = modelImageTag;
    return this;
  }

  public String modelImageTag() {
    return modelImageTag;
  }

  public WitParams wdtVersion(String wdtVersion) {
    this.wdtVersion = wdtVersion;
    return this;
  }

  public String wdtVersion() {
    return wdtVersion;
  }

  public WitParams domainType(String domainType) {
    this.domainType = domainType;
    return this;
  }

  public String domainType() {
    return domainType;
  }

  public String domainHome() {
    return domainHome;
  }

  public WitParams domainHome(String domainHome) {
    this.domainHome = domainHome;
    return this;
  }

  public String wdtOperation() {
    return wdtOperation;
  }

  public WitParams wdtOperation(String wdtOperation) {
    this.wdtOperation = wdtOperation;
    return this;
  }

  public boolean wdtModelOnly() {
    return wdtModelOnly;
  }

  public WitParams wdtModelOnly(boolean wdtModelOnly) {
    this.wdtModelOnly = wdtModelOnly;
    return this;
  }

  public String useridGroupid() {
    return useridGroupid;
  }

  public WitParams useridGroupid(String useridGroupid) {
    this.useridGroupid = useridGroupid;
    return this;
  }

  public String wdtHome() {
    return wdtHome;
  }

  public WitParams wdtHome(String wdtHome) {
    this.wdtHome = wdtHome;
    return this;
  }

  public String wdtModelHome() {
    return wdtModelHome;
  }

  public WitParams wdtModelHome(String wdtModelHome) {
    this.wdtModelHome = wdtModelHome;
    return this;
  }

  public WitParams modelFiles(List<String> modelFiles) {
    this.modelFiles = modelFiles;
    return this;
  }

  public List<String> modelFiles() {
    return modelFiles;
  }

  public WitParams modelVariableFiles(List<String> modelVariableFiles) {
    this.modelVariableFiles = modelVariableFiles;
    return this;
  }

  public List<String> modelVariableFiles() {
    return modelVariableFiles;
  }

  public WitParams modelArchiveFiles(List<String> modelArchiveFiles) {
    this.modelArchiveFiles = modelArchiveFiles;
    return this;
  }

  public List<String> modelArchiveFiles() {
    return modelArchiveFiles;
  }

  public String generatedImageName() {
    return modelImageName + ":" + modelImageTag;
  }

  public WitParams env(Map<String, String> env) {
    this.env = env;
    return this;
  }

  public Map<String, String> env() {
    return env;
  }

  public WitParams redirect(boolean redirect) {
    this.redirect = redirect;
    return this;
  }

  public boolean redirect() {
    return redirect;
  }

  public WitParams additionalBuildCommands(String additionalBuildCommands) {
    this.additionalBuildCommands = additionalBuildCommands;
    return this;
  }

  public String additionalBuildCommands() {
    return additionalBuildCommands;
  }

  public WitParams additionalBuildFiles(String additionalBuildFiles) {
    this.additionalBuildFiles = additionalBuildFiles;
    return this;
  }

  public String additionalBuildFiles() {
    return additionalBuildFiles;
  }

  public WitParams target(String target) {
    this.target = target;
    return this;
  }

  public String target() {
    return target;
  }

  public WitParams builder(String builder) {
    this.builder = builder;
    return this;
  }

  public String builder() {
    return builder;
  }

  public WitParams buildNetwork(String buildNetwork) {
    this.buildNetwork = buildNetwork;
    return this;
  }

  public String buildNetwork() {
    return buildNetwork;
  }

  public WitParams dryRun(Boolean dryRun) {
    this.dryRun = dryRun;
    return this;
  }

  public boolean dryRun() {
    return dryRun;
  }

  public WitParams httpProxyUrl(String httpProxyUrl) {
    this.httpProxyUrl = httpProxyUrl;
    return this;
  }

  public String httpProxyUrl() {
    return httpProxyUrl;
  }

  public WitParams httpsProxyUrl(String httpsProxyUrl) {
    this.httpsProxyUrl = httpsProxyUrl;
    return this;
  }

  public String httpsProxyUrl() {
    return httpsProxyUrl;
  }

  public WitParams packageManager(String packageManager) {
    this.packageManager = packageManager;
    return this;
  }

  public String packageManager() {
    return packageManager;
  }

  public WitParams pull(boolean pull) {
    this.pull = pull;
    return this;
  }

  public boolean pull() {
    return pull;
  }

  public WitParams skipCleanup(boolean skipCleanup) {
    this.skipCleanup = skipCleanup;
    return this;
  }

  public boolean skipCleanup() {
    return skipCleanup;
  }
}
