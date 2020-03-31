// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.util.List;

/**
 * Presents all parameters for downloading a tool.
 *
 */

public class WITParams {
 
  // TODO we start with these parameters, and will add as needed.
  private String baseImageName;
  private String baseImageTag;
  private String modelImageName;
  private String modelImageTag;
  private List<String> modelFiles;
  private List<String> modelVariableFiles;
  private List<String> modelArchiveFiles;
  private String wdtVersion;
  private String domainType;
  
  // Whether the output of the command is redirected to system out
  private boolean redirect;

  public WITParams baseImageName(String baseImageName) {
    this.baseImageName = baseImageName;
    return this;
  }
  
  public String getBaseImageName() {
    return baseImageName;
  }
  
  public WITParams baseImageTag(String baseImageTag) {
    this.baseImageTag = baseImageTag;
    return this;
  }
  
  public String getBaseImageTag() {
    return baseImageTag;
  }
  
  public WITParams modelImageName(String modelImageName) {
    this.modelImageName = modelImageName;
    return this;
  }

  public String getModelImageName() {
    return modelImageName;
  }
  
  public WITParams modelImageTag(String modelImageTag) {
    this.modelImageTag = modelImageTag;
    return this;
  }
    
  public String getModelImageTag() {
    return modelImageTag;
  }
     
  public WITParams wdtVersion(String wdtVersion) {
    this.wdtVersion = wdtVersion;
    return this;
  }
      
  public String getWdtVersion() {
    return wdtVersion;
  }

  public WITParams domainType(String domainType) {
    this.domainType = domainType;
    return this;
  }

  public String getDomainType() {
    return domainType;
  }

  public WITParams modelFiles(List<String> modelFiles) {
    this.modelFiles = modelFiles;
    return this;
  }

  public List<String> getModelFiles() {
    return modelFiles;
  }

  public WITParams modelVariableFiles(List<String> modelVariableFiles) {
    this.modelVariableFiles = modelVariableFiles;
    return this;
  }

  public List<String> getModelVariableFiles() {
    return modelVariableFiles;
  }

  public WITParams modelArchiveFiles(List<String> modelArchiveFiles) {
    this.modelArchiveFiles = modelArchiveFiles;
    return this;
  }
  
  public List<String> getModelArchiveFiles() {
    return modelArchiveFiles;
  }

  public String getGeneratedImageName() {
    return modelImageName + ":" + modelImageTag;
  }

  public WITParams redirect(boolean redirect) {
    this.redirect = redirect;
    return this;
  }

  public boolean isRedirect() {
    return redirect;
  }

}
