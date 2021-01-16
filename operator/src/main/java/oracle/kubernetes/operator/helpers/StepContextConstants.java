// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

public interface StepContextConstants {

  String OPSS_KEYPASSPHRASE_VOLUME = "opss-keypass-volume";
  String OPSS_WALLETFILE_VOLUME = "opss-walletfile-volume";
  String SECRETS_VOLUME = "weblogic-credentials-volume";
  String SCRIPTS_VOLUME = "weblogic-scripts-cm-volume";
  String DEBUG_CM_VOLUME = "weblogic-domain-debug-cm-volume";
  String INTROSPECTOR_VOLUME = "weblogic-domain-introspect-cm-volume";
  String RUNTIME_ENCRYPTION_SECRET_VOLUME = "weblogic-domain-runtime-encryption-volume";
  String SECRETS_MOUNT_PATH = "/weblogic-operator/secrets";
  String OPSS_KEY_MOUNT_PATH = "/weblogic-operator/opss-walletkey-secret";
  String RUNTIME_ENCRYPTION_SECRET_MOUNT_PATH = "/weblogic-operator/model-runtime-secret";
  String SCRIPTS_MOUNTS_PATH = "/weblogic-operator/scripts";
  String OVERRIDE_SECRETS_MOUNT_PATH = "/weblogic-operator/config-overrides-secrets";
  String OVERRIDES_CM_MOUNT_PATH = "/weblogic-operator/config-overrides";
  String WDTCONFIGMAP_MOUNT_PATH = "/weblogic-operator/wdt-config-map";
  String OPSS_WALLETFILE_MOUNT_PATH = "/weblogic-operator/opss-walletfile-secret";
  String DEBUG_CM_MOUNTS_PATH = "/weblogic-operator/debug";
  String NODEMGR_HOME = "/u01/nodemanager";
  int FAILURE_THRESHOLD = 1;

  @SuppressWarnings("OctalInteger")
  int ALL_READ_AND_EXECUTE = 0555;
}
