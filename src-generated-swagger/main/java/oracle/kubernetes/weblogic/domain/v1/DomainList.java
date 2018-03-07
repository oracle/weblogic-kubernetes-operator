// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v1;

import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.models.V1ListMeta;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;


/**
 * DomainList is a list of Domains.
 * 
 */
public class DomainList {

    /**
     * APIVersion defines the versioned schema of this representation of an object. Servers should convert recognized schemas to the latest internal value, and may reject unrecognized values. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#resources
     * 
     */
    @SerializedName("apiVersion")
    @Expose
    private String apiVersion;
    /**
     * List of domains. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md
     * (Required)
     * 
     */
    @SerializedName("items")
    @Expose
    @Valid
    @NotNull
    private List<Domain> items = new ArrayList<Domain>();
    /**
     * Kind is a string value representing the REST resource this object represents. Servers may infer this from the endpoint the client submits requests to. Cannot be updated. In CamelCase. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
     * 
     */
    @SerializedName("kind")
    @Expose
    private String kind;
    /**
     * Standard list metadata. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
     * 
     */
    @SerializedName("metadata")
    @Expose
    @Valid
    private V1ListMeta metadata;

    /**
     * APIVersion defines the versioned schema of this representation of an object. Servers should convert recognized schemas to the latest internal value, and may reject unrecognized values. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#resources
     * 
     */
    public String getApiVersion() {
        return apiVersion;
    }

    /**
     * APIVersion defines the versioned schema of this representation of an object. Servers should convert recognized schemas to the latest internal value, and may reject unrecognized values. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#resources
     * 
     */
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public DomainList withApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
        return this;
    }

    /**
     * List of domains. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md
     * (Required)
     * 
     */
    public List<Domain> getItems() {
        return items;
    }

    /**
     * List of domains. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md
     * (Required)
     * 
     */
    public void setItems(List<Domain> items) {
        this.items = items;
    }

    public DomainList withItems(List<Domain> items) {
        this.items = items;
        return this;
    }

    /**
     * Kind is a string value representing the REST resource this object represents. Servers may infer this from the endpoint the client submits requests to. Cannot be updated. In CamelCase. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
     * 
     */
    public String getKind() {
        return kind;
    }

    /**
     * Kind is a string value representing the REST resource this object represents. Servers may infer this from the endpoint the client submits requests to. Cannot be updated. In CamelCase. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
     * 
     */
    public void setKind(String kind) {
        this.kind = kind;
    }

    public DomainList withKind(String kind) {
        this.kind = kind;
        return this;
    }

    /**
     * Standard list metadata. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
     * 
     */
    public V1ListMeta getMetadata() {
        return metadata;
    }

    /**
     * Standard list metadata. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
     * 
     */
    public void setMetadata(V1ListMeta metadata) {
        this.metadata = metadata;
    }

    public DomainList withMetadata(V1ListMeta metadata) {
        this.metadata = metadata;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("apiVersion", apiVersion).append("items", items).append("kind", kind).append("metadata", metadata).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(metadata).append(apiVersion).append(items).append(kind).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof DomainList) == false) {
            return false;
        }
        DomainList rhs = ((DomainList) other);
        return new EqualsBuilder().append(metadata, rhs.metadata).append(apiVersion, rhs.apiVersion).append(items, rhs.items).append(kind, rhs.kind).isEquals();
    }

}
