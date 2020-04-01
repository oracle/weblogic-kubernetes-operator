// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.util.List;

/**
 * Contains the parameters for creating a Docker image using the WebLogic Image Tool.
 *
 */

public class WITParams {
  public static final String WLS = "WLS";
  public static final String JRF = "JRF";
  public static final String RJRF = "RestrictedJRF";
  public static final String WLS_BASE_IMAGE_NAME = "container-registry.oracle.com/middleware/weblogic";
  public static final String JRF_BASE_IMAGE_NAME = "container-registry.oracle.com/middleware/fmw-infrastructure";
  public static final String WLS_BASE_IMAGE_TAG = "12.2.1.4";

  public static final String MODEL_IMAGE_NAME = "test-mii-image";
  public static final String MODEL_IMAGE_TAG  = "v1";
 
  // TODO we start with these parameters, and will add as needed.

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
  
  // Whether the output of the command is redirected to system out
  private boolean redirect;
  
  public WITParams defaults() {
    this.baseImageName(WLS_BASE_IMAGE_NAME)
        .baseImageTag(WLS_BASE_IMAGE_TAG)
        .modelImageName(MODEL_IMAGE_NAME)
        .modelImageTag(MODEL_IMAGE_TAG)
        .domainType(WLS);
    return this;
  }

  public WITParams baseImageName(String baseImageName) {
    this.baseImageName = baseImageName;
    return this;
  }
  
  public String baseImageName() {
    return baseImageName;
  }
  
  public WITParams baseImageTag(String baseImageTag) {
    this.baseImageTag = baseImageTag;
    return this;
  }
  
  public String baseImageTag() {
    return baseImageTag;
  }
  
  public WITParams modelImageName(String modelImageName) {
    this.modelImageName = modelImageName;
    return this;
  }

  public String modelImageName() {
    return modelImageName;
  }
  
  public WITParams modelImageTag(String modelImageTag) {
    this.modelImageTag = modelImageTag;
    return this;
  }
    
  public String modelImageTag() {
    return modelImageTag;
  }
     
  public WITParams wdtVersion(String wdtVersion) {
    this.wdtVersion = wdtVersion;
    return this;
  }
      
  public String wdtVersion() {
    return wdtVersion;
  }

  public WITParams domainType(String domainType) {
    this.domainType = domainType;
    return this;
  }

  public String domainType() {
    return domainType;
  }

  public WITParams modelFiles(List<String> modelFiles) {
    this.modelFiles = modelFiles;
    return this;
  }

  public List<String> modelFiles() {
    return modelFiles;
  }

  public WITParams modelVariableFiles(List<String> modelVariableFiles) {
    this.modelVariableFiles = modelVariableFiles;
    return this;
  }

  public List<String> modelVariableFiles() {
    return modelVariableFiles;
  }

  public WITParams modelArchiveFiles(List<String> modelArchiveFiles) {
    this.modelArchiveFiles = modelArchiveFiles;
    return this;
  }
  
  public List<String> modelArchiveFiles() {
    return modelArchiveFiles;
  }

  public String generatedImageName() {
    return modelImageName + ":" + modelImageTag;
  }

  public WITParams redirect(boolean redirect) {
    this.redirect = redirect;
    return this;
  }

  public boolean redirect() {
    return redirect;
  }

}
