// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Optional;

import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class InitializeDomainOnPV {

  @Description("An optional field that describes the configuration to create a PersistentVolume for `Domain on PV`"
      + " domain. Omit this section if you have manually created a persistent volume. The operator will"
      + " perform this one-time create operation only if the persistent volume does not already exist. The operator"
      + " will not recreate or update the PersistentVolume when it exists. More info:"
      + " https://oracle.github.io/weblogic-kubernetes-operator/managing-domains/domain-on-pv-initialization#pv")
  PersistentVolume persistentVolume;

  @Description("An optional field that describes the configuration for creating a PersistentVolumeClaim for"
      + " `Domain on PV`. PersistentVolumeClaim is a user's request for and claim to a persistent volume. The operator"
      + " will perform this one-time create operation only if the persistent volume claim does not already exist. Omit"
      + " this section if you have manually created a persistent volume claim."
      + " If specified, the name must match one of the volumes under `serverPod.volumes` and"
      + " the domain home must reside in the mount path of the volume using this claim. More info:"
      + " https://oracle.github.io/weblogic-kubernetes-operator/managing-domains/domain-on-pv-initialization#pvc")
  PersistentVolumeClaim persistentVolumeClaim;

  @Description("Describes the configuration for creating an initial WebLogic Domain in persistent volume"
      + " (`Domain in PV`). The operator will not recreate or update the domain if it already exists. Required.")
  @SerializedName("domain")
  DomainOnPV domain;

  /** Whether to wait for PVC to be bound before proceeding to create the domain. Default is true. */
  @Description("Specifies whether the operator will wait for the PersistentVolumeClaim to be bound before proceeding"
      + " with the domain creation. Defaults to true.")
  Boolean waitForPvcToBind;

  public PersistentVolume getPersistentVolume() {
    return persistentVolume;
  }

  public InitializeDomainOnPV persistentVolume(PersistentVolume persistentVolume) {
    this.persistentVolume = persistentVolume;
    return this;
  }

  public PersistentVolumeClaim getPersistentVolumeClaim() {
    return persistentVolumeClaim;
  }

  public InitializeDomainOnPV persistentVolumeClaim(PersistentVolumeClaim persistentVolumeClaim) {
    this.persistentVolumeClaim = persistentVolumeClaim;
    return this;
  }

  public DomainOnPV getDomain() {
    return domain;
  }

  public InitializeDomainOnPV domain(DomainOnPV domain) {
    this.domain = domain;
    return this;
  }

  public Boolean getWaitForPvcToBind() {
    return Optional.ofNullable(waitForPvcToBind).orElse(true);
  }

  public InitializeDomainOnPV waitForPvcToBind(Boolean waitForPvcToBind) {
    this.waitForPvcToBind = waitForPvcToBind;
    return this;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("persistentVolume", persistentVolume)
            .append("persistentVolumeClaim", persistentVolumeClaim)
            .append("domain", domain)
            .append("waitForPvcToBind", waitForPvcToBind);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
        .append(persistentVolume)
        .append(persistentVolumeClaim)
        .append(domain)
        .append(waitForPvcToBind);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    } else if (!(other instanceof InitializeDomainOnPV)) {
      return false;
    }

    InitializeDomainOnPV rhs = ((InitializeDomainOnPV) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(persistentVolume, rhs.persistentVolume)
            .append(persistentVolumeClaim, rhs.persistentVolumeClaim)
            .append(domain, rhs.domain)
            .append(waitForPvcToBind, rhs.waitForPvcToBind);

    return builder.isEquals();
  }
}
