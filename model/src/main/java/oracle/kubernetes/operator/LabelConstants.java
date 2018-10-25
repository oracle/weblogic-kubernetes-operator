// Copyright 2017,2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

public interface LabelConstants {

  String RESOURCE_VERSION_LABEL = "weblogic.resourceVersion";
  String DOMAINUID_LABEL = "weblogic.domainUID";
  String DOMAINNAME_LABEL = "weblogic.domainName";
  String SERVERNAME_LABEL = "weblogic.serverName";
  String CHANNELNAME_LABEL = "weblogic.channelName";
  String CLUSTERNAME_LABEL = "weblogic.clusterName";
  String CREATEDBYOPERATOR_LABEL = "weblogic.createdByOperator";
  String OPERATORNAME_LABEL = "weblogic.operatorName";
  String APP_LABEL = "app";

  static String forDomainUid(String uid) {
    return String.format("%s=%s", DOMAINUID_LABEL, uid);
  }
}
