// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** DomainModel describes a WebLogic domain that has been registered with the WebLogic operator. */
public class DomainModel extends ItemModel {

  @JsonProperty("domainUID")
  private String domainUid;

  /** Construct an empty DomainModel. */
  public DomainModel() {
  }

  /**
   * Construct a populated DomainModel.
   *
   * @param domainUid - the unique identifier assigned to the WebLogic domain that contains this
   *     cluster.
   */
  public DomainModel(String domainUid) {
    setDomainUid(domainUid);
  }

  /**
   * Get the unique identifier that has been assigned to this WebLogic domain.
   *
   * @return the domain's unique identifier.
   */
  public String getDomainUid() {
    return domainUid;
  }

  /**
   * Set the unique identifier that has been assigned to this WebLogic domain.
   *
   * @param domainUid - the domain's unique identifier.
   */
  public void setDomainUid(String domainUid) {
    this.domainUid = domainUid;
  }

  @Override
  protected String propertiesToString() {
    return "domainUID=" + getDomainUid() + ", " + super.propertiesToString();
  }
}
