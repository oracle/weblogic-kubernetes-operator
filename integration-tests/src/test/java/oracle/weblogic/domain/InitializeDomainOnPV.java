// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import java.util.Optional;

import com.google.gson.annotations.SerializedName;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class InitializeDomainOnPV {

  @ApiModelProperty("An optional field that describes the configuration to create a PersistentVolume for `Domain on PV`"
      + " domain. Omit this section if you have manually created a persistent volume. The operator will"
      + " perform this one-time create operation only if the persistent volume does not already exist. The operator"
      + " will not recreate or update the PersistentVolume when it exists. More info:"
      + " https://oracle.github.io/weblogic-kubernetes-operator/managing-domains/domain-on-pv-initialization#pv")
  public PersistentVolume persistentVolume;

  @ApiModelProperty("An optional field that describes the configuration for creating a PersistentVolumeClaim for"
      + " `Domain on PV`. PersistentVolumeClaim is a user's request for and claim to a persistent volume. The operator"
      + " will perform this one-time create operation only if the persistent volume claim does not already exist. Omit"
      + " this section if you have manually created a persistent volume claim."
      + " If specified, the name must match one of the volumes under `serverPod.volumes` and"
      + " the domain home must reside in the mount path of the volume using this claim. More info:"
      + " https://oracle.github.io/weblogic-kubernetes-operator/managing-domains/domain-on-pv-initialization#pvc")
  public PersistentVolumeClaim persistentVolumeClaim;

  @ApiModelProperty("Describes the configuration for creating an initial WebLogic Domain in persistent volume"
      + " (`Domain in PV`). The operator will not recreate or update the domain if it already exists. Required.")
  @SerializedName("domain")
  public DomainOnPV domain;

  /** Whether to wait for PVC to be bound before proceeding to create the domain. Default is true. */
  @ApiModelProperty("Specifies whether the operator will wait for the PersistentVolumeClaim to be bound before"
      + " proceeding with the domain creation. Defaults to true.")
  public Boolean waitForPvcToBind;

  /** Whether to run the domain initialization init container in the introspector job as root. Default is false. */
  @ApiModelProperty("Specifies whether the operator will run the domain initialization init container in the "
      + "introspector job as root. This may be needed in some environments to create the domain home directory on PV."
      + " Defaults to false.")
  public Boolean runDomainInitContainerAsRoot;

  /** Whether to set the default 'fsGroup' in pod security context. Default is true. */
  @ApiModelProperty("Specifies whether the operator will set the default 'fsGroup' in the introspector job pod"
      + " security context. This is needed to create the domain home directory on PV in some environments."
      + " If the 'fsGroup' is specified as part of 'spec.introspector.serverPod.podSecurityContext', then the operator"
      + " will use that 'fsGroup' instead of the default 'fsGroup'. Defaults to true.")
  public Boolean setDefaultSecurityContextFsGroup;
  
  @ApiModelProperty("Specifies the secret name of the WebLogic Deployment Tool encryption passphrase if the WDT models "
      + "provided in the 'domainCreationImages' or 'domainCreationConfigMap' are encrypted using the "
      + "WebLogic Deployment Tool 'encryptModel' command. "
      + "The secret must use the key 'passphrase' containing the actual passphrase for encryption.")
  public String modelEncryptionPassphraseSecret;
  

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

  public Boolean getRunDomainInitContainerAsRoot() {
    return Optional.ofNullable(runDomainInitContainerAsRoot).orElse(false);
  }

  public InitializeDomainOnPV runInitContainerAsRoot(Boolean runInitContainerAsRoot) {
    this.runDomainInitContainerAsRoot = runInitContainerAsRoot;
    return this;
  }

  public Boolean getSetDefaultSecurityContextFsGroup() {
    return Optional.ofNullable(setDefaultSecurityContextFsGroup).orElse(true);
  }

  public InitializeDomainOnPV setDefaultFsGroup(Boolean setDefaultFsGroup) {
    this.setDefaultSecurityContextFsGroup = setDefaultFsGroup;
    return this;
  }
  
  public String getModelEncryptionPassphraseSecret() {
    return modelEncryptionPassphraseSecret;
  }

  public InitializeDomainOnPV modelEncryptionPassphraseSecret(String modelEncryptionPassphraseSecret) {
    this.modelEncryptionPassphraseSecret = modelEncryptionPassphraseSecret;
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
    EqualsBuilder builder
        = new EqualsBuilder()
            .append(persistentVolume, rhs.persistentVolume)
            .append(persistentVolumeClaim, rhs.persistentVolumeClaim)
            .append(domain, rhs.domain)
            .append(waitForPvcToBind, rhs.waitForPvcToBind)
            .append(modelEncryptionPassphraseSecret, rhs.modelEncryptionPassphraseSecret);

    return builder.isEquals();
  }
}
