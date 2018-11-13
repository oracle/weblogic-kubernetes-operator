// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.models.V1HostPathVolumeSource;
import io.kubernetes.client.models.V1NFSVolumeSource;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PersistentVolume;
import io.kubernetes.client.models.V1PersistentVolumeClaim;
import io.kubernetes.client.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.models.V1PersistentVolumeSpec;
import io.kubernetes.client.models.V1ResourceRequirements;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** Defines the persistent storage for a domain. */
@SuppressWarnings("WeakerAccess")
public class DomainStorage {
  private static final String PV_NAME_PATTERN = "%s-weblogic-domain-pv";
  private static final String PVC_NAME_PATTERN = "%s-weblogic-domain-pvc";
  private static final String STORAGE_CLASS_PATTERN = "%s-weblogic-domain-storage-class";
  private static final String DEFAULT_STORAGE_SIZE = "10Gi";
  private static final String DEFAULT_RECLAIM_POLICY = "Retain";

  /** Option: predefined storage. May not be nonnull if another option is defined. */
  @SerializedName("predefined")
  @Expose
  private PredefinedStorage predefinedStorage;

  /** Option: host/path storage. May not be nonnull if another option is defined. */
  @SerializedName("generated")
  @Expose
  private OperatorDefinedStorage operatorDefinedStorage;

  public DomainStorage(PredefinedStorage predefinedStorage) {
    this.predefinedStorage = predefinedStorage;
  }

  public DomainStorage(OperatorDefinedStorage operatorDefinedStorage) {
    this.operatorDefinedStorage = operatorDefinedStorage;
  }

  public static DomainStorage createPredefinedClaim(String claimName) {
    return new DomainStorage(new PredefinedStorage(claimName));
  }

  public static DomainStorage createHostPathStorage(String path) {
    return new DomainStorage(new OperatorDefinedStorage(new HostPathStorage(path)));
  }

  public static DomainStorage createNfsStorage(String server, String path) {
    return new DomainStorage(new OperatorDefinedStorage(new NfsStorage(server, path)));
  }

  /**
   * Returns the name of the persistent volume claim for the logs and PV-based domain.
   *
   * @return volume claim
   */
  String getPersistentVolumeClaimName() {
    if (predefinedStorage != null) return predefinedStorage.getClaimName();
    return null;
  }

  public void setStorageSize(String size) {
    getOperatorDefinedStorage().setStorageSize(size);
  }

  private OperatorDefinedStorage getOperatorDefinedStorage() {
    return operatorDefinedStorage;
  }

  public void setStorageReclaimPolicy(String policy) {
    getOperatorDefinedStorage().setStorageReclaimPolicy(policy);
  }

  public V1PersistentVolume getRequiredPersistentVolume(String domainUid) {
    if (getOperatorDefinedStorage() == null) return null;

    V1PersistentVolume pv =
        new V1PersistentVolume().metadata(new V1ObjectMeta()).spec(new V1PersistentVolumeSpec());
    getOperatorDefinedStorage().configure(pv, domainUid);
    return pv;
  }

  public V1PersistentVolumeClaim getRequiredPersistentVolumeClaim(String uid, String namespace) {
    if (getOperatorDefinedStorage() == null) return null;

    V1PersistentVolumeClaim pvc =
        new V1PersistentVolumeClaim()
            .metadata(new V1ObjectMeta().namespace(namespace))
            .spec(new V1PersistentVolumeClaimSpec());
    getOperatorDefinedStorage().configure(pvc, uid);
    return pvc;
  }

  public static class PredefinedStorage {
    /** Persistent volume claim name (Required) */
    @SerializedName("claim")
    @Expose
    @NotNull
    private String claimName;

    public PredefinedStorage(String claimName) {
      this.claimName = claimName;
    }

    public String getClaimName() {
      return claimName;
    }

    public void setClaimName(String claimName) {
      this.claimName = claimName;
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this).append("claimName", claimName).toString();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder().append(claimName).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
      if (other == this) {
        return true;
      }
      if (!(other instanceof PredefinedStorage)) {
        return false;
      }
      PredefinedStorage rhs = ((PredefinedStorage) other);
      return new EqualsBuilder().append(claimName, rhs.claimName).isEquals();
    }
  }

  static class OperatorDefinedStorage {

    /** Storage size */
    @SerializedName("storageSize")
    @Expose
    private String storageSize;

    /** Storage reclaim policy */
    @SerializedName("reclaimPolicy")
    @Expose
    private String storageReclaimPolicy;

    /** Option: host/path storage. May not be nonnull if another option is defined. */
    @SerializedName("hostPath")
    @Expose
    private HostPathStorage hostPathStorage;

    /** Option: nfs storage. May not be nonnull if another option is defined. */
    @SerializedName("nfs")
    @Expose
    private NfsStorage nfsStorage;

    OperatorDefinedStorage(HostPathStorage hostPathStorage) {
      this.hostPathStorage = hostPathStorage;
    }

    OperatorDefinedStorage(NfsStorage nfsStorage) {
      this.nfsStorage = nfsStorage;
    }

    public void setStorageSize(String storageSize) {
      this.storageSize = storageSize;
    }

    private String getStorageSize() {
      return Optional.ofNullable(storageSize).orElse(DEFAULT_STORAGE_SIZE);
    }

    public void setStorageReclaimPolicy(String storageReclaimPolicy) {
      this.storageReclaimPolicy = storageReclaimPolicy;
    }

    private String getStorageReclaimPolicy() {
      return Optional.ofNullable(storageReclaimPolicy).orElse(DEFAULT_RECLAIM_POLICY);
    }

    void configure(V1PersistentVolume pv, String domainUid) {
      pv.getMetadata()
          .name(String.format(PV_NAME_PATTERN, domainUid))
          .putLabelsItem("weblogic.createdByOperator", "true")
          .putLabelsItem("weblogic.domainUID", domainUid);
      pv.getSpec()
          .storageClassName(String.format(STORAGE_CLASS_PATTERN, domainUid))
          .putCapacityItem("storage", new Quantity(getStorageSize()))
          .addAccessModesItem("ReadWriteMany")
          .persistentVolumeReclaimPolicy(getStorageReclaimPolicy());

      if (hostPathStorage != null) hostPathStorage.configure(pv);
      else nfsStorage.configure(pv);
    }

    void configure(V1PersistentVolumeClaim pvc, String domainUid) {
      pvc.getMetadata()
          .name(String.format(PVC_NAME_PATTERN, domainUid))
          .putLabelsItem("weblogic.createdByOperator", "true")
          .putLabelsItem("weblogic.domainUID", domainUid);
      pvc.getSpec()
          .storageClassName(String.format(STORAGE_CLASS_PATTERN, domainUid))
          .addAccessModesItem("ReadWriteMany")
          .resources(
              new V1ResourceRequirements()
                  .putRequestsItem("storage", new Quantity(getStorageSize())));
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this)
          .append("storageSize", storageSize)
          .append("storageReclaimPolicy", storageReclaimPolicy)
          .append("hostPathStorage", hostPathStorage)
          .append("nfsStorage", nfsStorage)
          .toString();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder()
          .append(storageSize)
          .append(storageReclaimPolicy)
          .append(hostPathStorage)
          .append(nfsStorage)
          .toHashCode();
    }

    @Override
    public boolean equals(Object other) {
      if (other == this) {
        return true;
      }
      if (!(other instanceof OperatorDefinedStorage)) {
        return false;
      }
      OperatorDefinedStorage rhs = ((OperatorDefinedStorage) other);
      return new EqualsBuilder()
          .append(storageSize, rhs.storageSize)
          .append(storageReclaimPolicy, rhs.storageReclaimPolicy)
          .append(hostPathStorage, rhs.hostPathStorage)
          .append(nfsStorage, rhs.nfsStorage)
          .isEquals();
    }
  }

  static class HostPathStorage {
    /** Storage path (Required) */
    @SerializedName("path")
    @Expose
    @NotNull
    private String path;

    public HostPathStorage(String path) {
      this.path = path;
    }

    void configure(V1PersistentVolume pv) {
      pv.getSpec().hostPath(new V1HostPathVolumeSource().path(path));
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this).append("path", path).toString();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder().append(path).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
      if (other == this) {
        return true;
      }
      if (!(other instanceof HostPathStorage)) {
        return false;
      }
      HostPathStorage rhs = ((HostPathStorage) other);
      return new EqualsBuilder().append(path, rhs.path).isEquals();
    }
  }

  static class NfsStorage {
    /** Storage path (Required) */
    @SerializedName("path")
    @Expose
    @NotNull
    private String path;

    /** Storage server (required) */
    @SerializedName("server")
    @Expose
    @NotNull
    private final String server;

    public NfsStorage(String server, String path) {
      this.server = server;
      this.path = path;
    }

    void configure(V1PersistentVolume pv) {
      pv.getSpec().nfs(new V1NFSVolumeSource().server(server).path(path));
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this).append("path", path).append("server", server).toString();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder().append(path).append(server).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
      if (other == this) {
        return true;
      }
      if (!(other instanceof NfsStorage)) {
        return false;
      }
      NfsStorage rhs = ((NfsStorage) other);
      return new EqualsBuilder().append(path, rhs.path).append(server, rhs.server).isEquals();
    }
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("predefinedStorage", predefinedStorage)
        .append("operatorDefinedStorage", operatorDefinedStorage)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(predefinedStorage)
        .append(operatorDefinedStorage)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof DomainStorage)) {
      return false;
    }
    DomainStorage rhs = ((DomainStorage) other);
    return new EqualsBuilder()
        .append(predefinedStorage, rhs.predefinedStorage)
        .append(operatorDefinedStorage, rhs.operatorDefinedStorage)
        .isEquals();
  }
}
