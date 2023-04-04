// Copyright (c) 2019, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.text.ChoiceFormat;
import java.text.Format;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.helpers.SecretType;
import oracle.kubernetes.utils.OperatorUtils;

import static oracle.kubernetes.operator.helpers.LegalNames.LEGAL_CONTAINER_PORT_NAME_MAX_LENGTH;
import static oracle.kubernetes.weblogic.domain.model.Model.DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH;
import static oracle.kubernetes.weblogic.domain.model.Validator.ALLOWED_INTROSPECTOR_ENV_VARS;

public class DomainValidationMessages {

  private DomainValidationMessages() {
    // no-op
  }

  /**
   * Returns a validation message indicating that more than one managed server spec has the same effective name
   * after DNS-1123 conversion.
   * @param serverName the duplicate server name
   * @return the localized message
   */
  static String duplicateServerName(@Nonnull String serverName) {
    return getMessage(MessageKeys.DUPLICATE_SERVER_NAME_FOUND, serverName);
  }

  /**
   * Returns a validation message indicating that more than one cluster spec has the same effective name
   * after DNS-1123 conversion.
   * @param clusterName the duplicate cluster name
   * @return the localized message
   */
  static String duplicateClusterName(@Nonnull String clusterName) {
    return getMessage(MessageKeys.DUPLICATE_CLUSTER_NAME_FOUND, clusterName);
  }

  /**
   * Returns a validation message indicating that a specified volume mount's path is not absolute.
   * @param mount the problematic volume mount
   * @return the localized message
   */
  static String badVolumeMountPath(@Nonnull V1VolumeMount mount) {
    return getMessage(MessageKeys.BAD_VOLUME_MOUNT_PATH, mount.getMountPath(), mount.getName());
  }

  /**
   * Returns a validation message indicating that a specified volume mount's path overlaps with another volume.
   * @param mount1 the problematic volume mount
   * @param mount2 the problematic volume mount
   * @return the localized message
   */
  static String overlappingVolumeMountPath(@Nonnull V1VolumeMount mount1, @Nonnull V1VolumeMount mount2) {
    return getMessage(MessageKeys.OVERLAPPING_VOLUME_MOUNT_PATH,
        mount1.getMountPath(), mount1.getName(), mount2.getMountPath(), mount2.getName());
  }

  /**
   * Returns a validation message indicating that none of the additional volume mounts contains a path which
   * includes the log home.
   * @param logHome the log home to be used
   * @return the localized message
   */
  static String logHomeNotMounted(@Nonnull String logHome) {
    return getMessage(MessageKeys.LOG_HOME_NOT_MOUNTED, logHome);
  }

  private static String getMessage(String key, Object... parameters) {
    MessageFormat formatter = new MessageFormat("");
    formatter.applyPattern(getBundleString(key));
    return formatter.format(parameters);
  }

  private static String getBundleString(String key) {
    return ResourceBundle.getBundle("Operator").getString(key);
  }

  static String reservedVariableNames(String prefix, List<String> reservedNames) {
    MessageFormat formatter = new MessageFormat("");
    formatter.applyPattern(getBundleString(MessageKeys.RESERVED_ENVIRONMENT_VARIABLES));
    formatter.setFormats(new Format[]{getEnvNoun(), null, null, getToBe()});
    return formatter.format(new Object[] {
        reservedNames.size(),
        OperatorUtils.joinListGrammatically(reservedNames),
        prefix + ".serverPod.env",
        reservedNames.size()});
  }

  private static ChoiceFormat getEnvNoun() {
    return new ChoiceFormat(new double[] {1, 2},
                            new String[] {getBundleString("oneEnvVar"), getBundleString("multipleEnvVars")});
  }

  private static ChoiceFormat getToBe() {
    return new ChoiceFormat(new double[] {1, 2},
                            new String[] {getBundleString("singularToBe"), getBundleString("pluralToBe")});
  }

  public static String noSuchSecret(String secretName, String namespace, SecretType type) {
    return getMessage(MessageKeys.SECRET_NOT_FOUND, secretName, namespace, type);
  }

  static String missingRequiredSecret(String secret) {
    return getMessage(MessageKeys.SECRET_NOT_SPECIFIED, secret);
  }

  static String missingRequiredOpssSecret(String secret) {
    return getMessage(MessageKeys.OPSS_SECRET_NOT_SPECIFIED, secret);
  }

  static String missingRequiredInitializeDomainOnPVOpssSecret(String secret) {
    return getMessage(MessageKeys.INIT_PV_DOMAIN_OPSS_SECRET_NOT_SPECIFIED, secret);
  }

  static String missingRequiredFluentdSecret(String secret) {
    return getMessage(MessageKeys.MISSING_ELASTIC_SEARCH_SECRET, secret);
  }

  static String illegalSecretNamespace(String namespace) {
    return getMessage(MessageKeys.ILLEGAL_SECRET_NAMESPACE, namespace);
  }

  static String illegalSitConfigForMii(String configOverrides) {
    return getMessage(MessageKeys.ILLEGAL_SIT_CONFIG_MII, configOverrides);
  }

  static String noSuchModelConfigMap(String configMapName, String location, String namespace) {
    return getMessage(MessageKeys.MODEL_CONFIGMAP_NOT_FOUND, configMapName, location, namespace);
  }

  static String cannotExposeDefaultChannelIstio(String channelName) {
    return getMessage(MessageKeys.CANNOT_EXPOSE_DEFAULT_CHANNEL_ISTIO, channelName);
  }

  public static String exceedMaxIntrospectorJobName(String domainUid, String result, int limit) {
    return getMessage(MessageKeys.ILLEGAL_INTROSPECTOR_JOB_NAME_LENGTH, domainUid, result, limit);
  }

  public static String mountPathForAuxiliaryImageAlreadyInUse() {
    return getMessage(MessageKeys.MOUNT_PATH_FOR_AUXILIARY_IMAGE_ALREADY_IN_USE, DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH);
  }

  public static String moreThanOneAuxiliaryImageConfiguredWDTInstallHome() {
    return getMessage(MessageKeys.MORE_THAN_ONE_AUXILIARY_IMAGE_CONFIGURED_WDT_INSTALL_HOME);
  }

  public static String moreThanOneDomainCreationImageConfiguredWDTInstallHome() {
    return getMessage(MessageKeys.MORE_THAN_ONE_DOMAIN_CREATION_IMAGE_CONFIGURED_WDT_INSTALL_HOME);
  }

  public static String persistentVolumeNameNotSpecified() {
    return getMessage(MessageKeys.INIT_PV_DOMAIN_PV_NAME_NOT_SPECIFIED);
  }

  public static String persistentVolumeCapacityNotSpecified(String name) {
    return getMessage(MessageKeys.INIT_PV_DOMAIN_PV_CAPACITY_NOT_SPECIFIED, name);
  }

  public static String persistentVolumeStorageClassNotSpecified(String name) {
    return getMessage(MessageKeys.INIT_PV_DOMAIN_PV_STORAGE_CLASS_NOT_SPECIFIED, name);
  }

  public static String persistentVolumeClaimNameNotSpecified() {
    return getMessage(MessageKeys.INIT_PV_DOMAIN_PVC_NAME_NOT_SPECIFIED);
  }

  public static String persistentVolumeClaimResourcesNotSpecified(String name) {
    return getMessage(MessageKeys.INIT_PV_DOMAIN_PVC_RESOURCES_NOT_SPECIFIED, name);
  }

  public static String persistentVolumeClaimStorageClassNotSpecified(String name) {
    return getMessage(MessageKeys.INIT_PV_DOMAIN_PVC_STORAGE_CLASS_NOT_SPECIFIED, name);
  }

  public static String invalidLivenessProbeSuccessThresholdValue(int value, String prefix) {
    return getMessage(MessageKeys.INVALID_LIVENESS_PROBE_SUCCESS_THRESHOLD_VALUE, value, prefix);
  }

  public static String reservedContainerName(String name, String prefix) {
    return getMessage(MessageKeys.RESERVED_CONTAINER_NAME, name, prefix);
  }

  public static String exceedMaxContainerPortName(String domainUid, String containerName, String portName) {
    return getMessage(MessageKeys.ILLEGAL_CONTAINER_PORT_NAME_LENGTH, domainUid, containerName, portName,
            LEGAL_CONTAINER_PORT_NAME_MAX_LENGTH);
  }

  public static String invalidWdtInstallHome(String wdtInstallHome, String modelHome) {
    return getMessage(MessageKeys.INVALID_WDT_INSTALL_HOME, wdtInstallHome, modelHome);
  }

  public static String invalidModelHome(String wdtInstallHome, String modelHome) {
    return getMessage(MessageKeys.INVALID_MODEL_HOME, wdtInstallHome, modelHome);
  }

  public static String clusterInUse(String clusterName, String otherDomain) {
    return getMessage(MessageKeys.CLUSTER_IN_USE, clusterName, otherDomain);
  }

  public static String missingClusterResource(String clusterName, String namespace) {
    return getMessage(MessageKeys.CLUSTER_RESOURCE_NOT_FOUND, clusterName, namespace);
  }

  public static String introspectorEnvVariableNotSupported(List<String> unsupportedEnvVars) {
    return getMessage(MessageKeys.UNSUPPORTED_INTRO_ENV_VARIABLES, unsupportedEnvVars,
        Arrays.asList(ALLOWED_INTROSPECTOR_ENV_VARS));
  }

  public static String conflictOpssSecrets(String initPvDomainOpss, String miiOpss) {
    return getMessage(MessageKeys.CONFLICT_OPSS_SECRETS, initPvDomainOpss, miiOpss);
  }

  public static String conflictModelConfiguration(String model, String initializeDomainOnPV) {
    return getMessage(MessageKeys.CONFLICT_MODEL_INITIALIZE_DOMAIN_ON_PV, model, initializeDomainOnPV);
  }

  /**
   * Returns a validation message indicating that none of the volume mounts contains a path which includes
   * the domain home.
   * @param domainHome the domain home to be used
   * @return the localized message
   */
  static String domainHomeNotMounted(@Nonnull String domainHome) {
    return getMessage(MessageKeys.DOMAIN_HOME_NOT_MOUNTED, domainHome);
  }

  /**
   * Returns a validation message indicating that the domainOnPVType and createIfNotExists settings are incompatible.
   * @return the localized message
   */
  static String mismatchDomainTypeAndCreateIfNoeExists() {
    return getMessage(MessageKeys.MISMATCH_DOMAIN_TYPE_CREATE_IF_NOT_EXISTS);
  }

  static String noMatchVolumeWithPVC(String pvcName) {
    return getMessage(MessageKeys.NO_MATCH_VOLUME_WITH_PVC, pvcName);
  }

  static String noVolumeWithPVC() {
    return getMessage(MessageKeys.NO_VOLUME_WITH_PVC);
  }

  public static String noWalletPasswordInSecret(String configuration, String secretName) {
    return getMessage(MessageKeys.WALLET_KEY_NOT_FOUND, configuration, secretName);
  }

}
