// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

import java.util.List;

/**
 * UpgradeApplicationsModel describes the "applications" input data to the WebLogic application upgrade operation. It's a Map object with its value of a list of application info.
 *
 * The input data looks like this:
 *
 *  {"applications":[
 *   {"applicationName": "app1",
 *    "patchedLocation": "/shared/applications/myApp1v2.ear",
 *    "backupLocation": "/shared/applications/myApp1v1.ear"},
 *   {"applicationName": "app2",
 *    "patchedLocation": "/shared/applications/myApp2v2.ear",
 *    "backupLocation": "/shared/applications/myApp2v1.ear"}
 *   ]}
 *
 */
public class UpgradeApplicationsModel extends LinkContainerModel {

  private List<UpgradeApplicationModel> applications;


  public List<UpgradeApplicationModel> getApplications() {
    return applications;
  }

  public void setApplications(List<UpgradeApplicationModel> applications) {
    this.applications = applications;
  }

  //@Override
  //protected String propertiesToString() {
  //  return super.propertiesToString() + "applicationName=" + getApplicationName() + ",patchLocation=" + getPatchLocation(); // super has no properties
  //}

}
