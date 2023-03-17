// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@Description("The configuration required to create an empty WebLogic 'Domain on PV' domain, a persistent"
    + " volume and a persistent volume claim, if needed."
    + " These will be one-time operations that happen only if the domain, persistent volume or persistent volume claim"
    + " do not already exist. This is primarily used for a JRF-based domain. For a plain WebLogic domain,"
    + " recommended approach is to use a 'Model In Image' domain home source type."
    + " See https://oracle.github.io/weblogic-kubernetes-operator/managing-domains/choosing-a-model/")
public class InitializeDomainOnPV {

  @Description("Configuration including 'Metadata' and 'Specs' to create a persistent volume, if needed.")
  PersistentVolume persistentVolume;

  @Description("Configuration including 'Metadata' and 'Specs' to create a persistent volume claim, if needed.")
  PersistentVolumeClaim persistentVolumeClaim;

  @Description("Configuration details to create an empty WebLogic 'Domain on PV' domain, if needed.")
  @SerializedName("domain")
  DomainOnPV domain;

  public PersistentVolume getPersistentVolume() {
    return persistentVolume;
  }

  public void setPersistentVolume(PersistentVolume persistentVolume) {
    this.persistentVolume = persistentVolume;
  }

  public PersistentVolumeClaim getPersistentVolumeClaim() {
    return persistentVolumeClaim;
  }

  public void setPersistentVolumeClaim(PersistentVolumeClaim persistentVolumeClaim) {
    this.persistentVolumeClaim = persistentVolumeClaim;
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
    } else if (!(other instanceof DomainOnPV)) {
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
