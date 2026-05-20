// Copyright (c) 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Objects;
import java.util.Optional;

import io.kubernetes.client.openapi.models.V1HostPathVolumeSource;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainValidationMessages;
import oracle.kubernetes.weblogic.domain.model.PersistentVolume;
import oracle.kubernetes.weblogic.domain.model.PersistentVolumeSpec;

/**
 * Policy for legacy operator-created PersistentVolumes under initializeDomainOnPV.
 */
public class DomainOnPVLocalDeveloperMode {
  private DomainOnPVLocalDeveloperMode() {
  }

  /**
   * Returns true when operator-created initializeDomainOnPV volumes may use hostPath sources.
   *
   * @return true if local developer mode is enabled
   */
  public static boolean isEnabled() {
    return Optional.ofNullable(TuningParameters.getInstance())
        .map(TuningParameters::isDomainOnPVLocalDeveloperMode)
        .orElse(false);
  }

  public static boolean hasRestrictedPVSource(DomainResource domain) {
    return getHostPath(domain).isPresent();
  }

  public static boolean hasNewOrChangedRestrictedPVSource(DomainResource existingDomain,
                                                          DomainResource proposedDomain) {
    return hasNewOrChangedHostPath(existingDomain, proposedDomain);
  }

  private static boolean hasNewOrChangedHostPath(DomainResource existingDomain, DomainResource proposedDomain) {
    Optional<V1HostPathVolumeSource> proposed = getHostPath(proposedDomain);
    return proposed.isPresent() && !Objects.equals(getHostPath(existingDomain).orElse(null), proposed.get());
  }

  /**
   * Creates the validation failure message for a domain that uses a restricted initializeDomainOnPV PV source.
   *
   * @param domain domain resource
   * @return validation failure message, or null when no restricted PV source is present
   */
  public static String createFailureMessage(DomainResource domain) {
    return getHostPath(domain)
        .map(hostPath -> DomainValidationMessages.persistentVolumeSourceNotAllowed(getPVName(domain), "hostPath"))
        .orElse(null);
  }

  private static String getPVName(DomainResource domain) {
    return Optional.ofNullable(domain)
        .map(DomainResource::getInitPvDomainPersistentVolume)
        .map(PersistentVolume::getMetadata)
        .map(metadata -> metadata.getName())
        .orElse("");
  }

  private static Optional<V1HostPathVolumeSource> getHostPath(DomainResource domain) {
    return getPVSpec(domain).map(PersistentVolumeSpec::getHostPath);
  }

  private static Optional<PersistentVolumeSpec> getPVSpec(DomainResource domain) {
    return Optional.ofNullable(domain)
        .map(DomainResource::getInitPvDomainPersistentVolume)
        .map(PersistentVolume::getSpec);
  }
}
