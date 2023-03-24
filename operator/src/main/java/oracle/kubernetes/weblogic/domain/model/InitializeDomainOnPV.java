// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class InitializeDomainOnPV {

  @Description("Describes the configuration for creating a PersistentVolume for `Domain in PV``. The operator will"
      + " not recreate or update the PersistentVolume if it already exists. More info:"
      + " https://kubernetes.io/docs/concepts/storage/persistent-volumes")
  PersistentVolume persistentVolume;

  @Description("Describes the configuration for creating a PersistentVolumeClaim for `Domain in PV`."
      + " PersistentVolumeClaim is a user's request for and claim to a persistent volume. The operator will"
      + " not recreate or update the PersistentVolumeClaim if it already exists. More info:"
      + " https://kubernetes.io/docs/concepts/storage/persistent-volumes")
  PersistentVolumeClaim persistentVolumeClaim;

  @Description("Describes the configuration for creating an initial WebLogic Domain in persistent volume"
      + " (`Domain in PV`). The operator will not recreate or update the domain if it already exists.")
  @SerializedName("domain")
  DomainOnPV domain;

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

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("persistentVolume", persistentVolume)
            .append("persistentVolumeClaim", persistentVolumeClaim)
            .append("domain", domain);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
        .append(persistentVolume)
        .append(persistentVolumeClaim)
        .append(domain);

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
            .append(domain, rhs.domain);

    return builder.isEquals();
  }
}
