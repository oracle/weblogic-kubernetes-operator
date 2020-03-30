// Copyright 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Presents all parameters for WIT commands

public class WITParams {

    // TODO we'll start with these parameters, and will add as needed.
    private String baseName;
    private String tag;
    private List<String> modelFiles;
    private List<String> modelVariableFiles;
    private List<String> modelArchiveFiles;
    private String wdtVersion;
    private String domainType;
        
    public WITParams baseName(String baseName) {
    	this.baseName = baseName;
    	return this;
    }

    public WITParams tag(String tag) {
    	this.tag = tag;
    	return this;
    }

    public WITParams wdtVersion(String wdtVersion) {
    	this.wdtVersion = wdtVersion;
    	return this;
    }

    public WITParams domainType(String baseName) {
    	this.baseName = baseName;
    	return this;
    }

    public WITParams modelFiles(List<String> modelFiles) {
    	this.modelFiles = modelFiles;
    	return this;
    }

    public WITParams modelVariableFiles(List<String> modelVeriableFiles) {
    	this.modelVariableFiles = modelVariableFiles;
    	return this;
    }

    public WITParams modelArchiveFiles(List<String> modelArchiveFiles) {
    	this.modelArchiveFiles = modelArchiveFiles;
    	return this;
    }
   
}
