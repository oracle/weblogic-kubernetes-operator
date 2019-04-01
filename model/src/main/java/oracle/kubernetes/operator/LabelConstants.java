// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

public interface LabelConstants {

  String RESOURCE_VERSION_LABEL = "weblogic.resourceVersion";
  String DOMAINUID_LABEL = "weblogic.domainUID";
  String DOMAINNAME_LABEL = "weblogic.domainName";
  String DOMAINHOME_LABEL = "weblogic.domainHome";
  String SERVERNAME_LABEL = "weblogic.serverName";
  String CLUSTERNAME_LABEL = "weblogic.clusterName";
  String CREATEDBYOPERATOR_LABEL = "weblogic.createdByOperator";
  String OPERATORNAME_LABEL = "weblogic.operatorName";
  String JOBNAME_LABEL = "job-name";
  String APP_LABEL = "app";
  String DOMAINRESTARTVERSION_LABEL = "weblogic.domainRestartVersion";
  String CLUSTERRESTARTVERSION_LABEL = "weblogic.clusterRestartVersion";
  String SERVERRESTARTVERSION_LABEL = "weblogic.serverRestartVersion";

  static String forDomainUidSelector(String uid) {
    return String.format("%s=%s", DOMAINUID_LABEL, uid);
  }

  static String getCreatedbyOperatorSelector() {
    return String.format("%s=%s", CREATEDBYOPERATOR_LABEL, "true");
  }
}
