// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

public interface StepContextConstants {

  static final String OPSS_KEYPASSPHRASE_VOLUME = "opss-keypass-volume";
  static final String OPSS_WALLETFILE_VOLUME = "opss-walletfile-volume";
  static final String WDT_ENCRYPT_PASSPHRASE_VOLUME = "wdt-encrypt-keypass-volume";
  static final String SECRETS_VOLUME = "weblogic-credentials-volume";
  static final String SCRIPTS_VOLUME = "weblogic-scripts-cm-volume";
  static final String DEBUG_CM_VOLUME = "weblogic-domain-debug-cm-volume";
  static final String INTROSPECTOR_VOLUME = "weblogic-domain-introspect-cm-volume";
  static final String WDT_CONFIG_MAP_VOLUME = "weblogic-domain-introspect-wdt-cm-volume";
  static final String RUNTIME_ENCRYPTION_SECRET_VOLUME = "weblogic-domain-runtime-encryption-volume";
  static final String STORAGE_VOLUME = "weblogic-domain-storage-volume";
  static final String SECRETS_MOUNT_PATH = "/weblogic-operator/secrets";
  static final String OPSS_KEY_MOUNT_PATH = "/weblogic-operator/opss-walletkey-secret";
  static final String WDT_ENCRYPT_KEY_MOUNT_PATH = "/weblogic-operator/wdt-encrypt-key-passphrase";
  static final String RUNTIME_ENCRYPTION_SECRET_MOUNT_PATH = "/weblogic-operator/model-runtime-secret";
  static final String SCRIPTS_MOUNTS_PATH = "/weblogic-operator/scripts";
  static final String OVERRIDE_SECRETS_MOUNT_PATH = "/weblogic-operator/config-overrides-secrets";
  static final String OVERRIDES_CM_MOUNT_PATH = "/weblogic-operator/config-overrides";
  static final String WDTCONFIGMAP_MOUNT_PATH = "/weblogic-operator/wdt-config-map";
  static final String OPSS_WALLETFILE_MOUNT_PATH = "/weblogic-operator/opss-walletfile-secret";
  static final String DEBUG_CM_MOUNTS_PATH = "/weblogic-operator/debug";
  static final String NODEMGR_HOME = "/u01/nodemanager";
  static final int FAILURE_THRESHOLD = 1;

  @SuppressWarnings("OctalInteger")
  static final int ALL_READ_AND_EXECUTE = 0555;
}
