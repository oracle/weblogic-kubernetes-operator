// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

public interface LabelConstants {

  String OPERATOR_VERSION = "weblogic.operatorVersion";
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
  String MODEL_IN_IMAGE_MODEL_SECRETS_HASH = "weblogic.modelInImageModelSecretsHash";
  String MODEL_IN_IMAGE_DOMAINZIP_HASH = "weblogic.modelInImageDomainZipHash";
  String INTROSPECTION_STATE_LABEL = "weblogic.introspectVersion";
  String MII_UPDATED_RESTART_REQUIRED_LABEL = "weblogic.configChangesPendingRestart";

  static String forDomainUidSelector(String uid) {
    return String.format("%s=%s", DOMAINUID_LABEL, uid);
  }

  static String getCreatedByOperatorSelector() {
    return String.format("%s=%s", CREATEDBYOPERATOR_LABEL, "true");
  }
}
