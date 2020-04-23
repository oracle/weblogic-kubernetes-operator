// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.util.List;
import java.util.Map;

import static oracle.weblogic.kubernetes.actions.ActionConstants.DEFAULT_MODEL_IMAGE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.DEFAULT_MODEL_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_TAG;

/**
 * Contains the parameters for creating a Docker image using the WebLogic Image Tool.
 *
 */
public class WitParams {
 
  // The name of the Docker image that is used as the base of a new image
  private String baseImageName;
  
  // The tag of the Docker image that is used as the base of a new image
  private String baseImageTag;
  
  // The name of the to be generated Docker image
  private String modelImageName;
  
  // The name of the to be generated Docker image
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
  
  // The env variables that are needed for running WIT
  private Map<String, String> env;

  // Whether the output of the command is redirected to system out
  private boolean redirect;

  /**
   * Generate default WIT parameters.
   * @return WIT parameters
   */
  public WitParams defaults() {
    this.baseImageName(WLS_BASE_IMAGE_NAME)
        .baseImageTag(WLS_BASE_IMAGE_TAG)
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

}
