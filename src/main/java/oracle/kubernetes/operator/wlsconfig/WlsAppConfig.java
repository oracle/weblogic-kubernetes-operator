// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contains configuration of an Java EE application deployed in WebLogic Server environment
 * <p>
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 */
public class WlsAppConfig {
  String appName;
  String sourcePath;
  String patchedLocation;
  String backupLocation;
  Map<String, List<String>> targets = new HashMap<String, List<String>>();

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  static WlsAppConfig create() {
    return new WlsAppConfig();
  }

  public String getAppName() {
    return appName;
  }

  public void setAppName(String appName) {
    this.appName = appName;
  }

  public String getSourcePath() {
    return sourcePath;
  }

  public void setSourcePath(String sourcePath) {
    this.sourcePath = sourcePath;
  }

  public String getPatchedLocation() {
    return patchedLocation;
  }

  public void setPatchedLocation(String patchedLocation) {
    this.patchedLocation = patchedLocation;
  }

  public String getBackupLocation() {
    return backupLocation;
  }

  public void setBackupLocation(String backupLocation) {
    this.backupLocation = backupLocation;
  }

  public Map<String, List<String>> getTargets() {
    return targets;
  }

  public void setTargets(Map<String, List<String>> targets) {
    this.targets = targets;
  }

  @Override
  public String toString() {
    return "WlsAppConfig{" +
            "appName='" + appName + '\'' +
            "sourcePath='" + sourcePath + '\'' +
            "patchingLocation='" + patchedLocation + '\'' +
            "backupLocation='" + backupLocation + '\'' +
            "targets='" + convertTargetsMapToString(targets) + "}";

  }

  private String convertTargetsMapToString(Map<String, List<String>> targets) {
    StringBuffer sb = new StringBuffer();
    for (String key : targets.keySet()) {
      sb.append(key + "=" + targets.get(key) + ",");
    }
    return sb.toString();
  }

  public static String getRetrieveServersSearchUrl() {
     // The jsonResult looks like this:
     // {"items": [{
     //    "sourcePath": "\/shared\/applications\/simpleApp.war",
     //    "name": "simpleApp",
     //    "targets": [{"identity": [
     //        "clusters",
     //        "cluster-1"
     //    ]}]
     //}]}
   return "/management/weblogic/latest/serverConfig/appDeployments?fields=name,targets,sourcePath&links=none";
  }

}
