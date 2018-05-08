// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

/** DomainModel describes a WebLogic domain that has been registered with the WebLogic operator. */
public class DomainModel extends ItemModel {

  /** Construct an empty DomainModel. */
  public DomainModel() {}

  /**
   * Construct a populated DomainModel.
   *
   * @param domainUID - the unique identifier assigned to the WebLogic domain that contains this
   *     cluster.
   */
  public DomainModel(String domainUID) {
    setDomainUID(domainUID);
  }

  private String domainUID;

  /**
   * Get the unique identifier that has been assigned to this WebLogic domain.
   *
   * @return the domain's unique identifier.
   */
  public String getDomainUID() {
    return domainUID;
  }

  /**
   * Set the unique identifier that has been assigned to this WebLogic domain.
   *
   * @param domainUID - the domain's unique identifier.
   */
  public void setDomainUID(String domainUID) {
    this.domainUID = domainUID;
  }

  @Override
  protected String propertiesToString() {
    return "domainUID=" + getDomainUID() + ", " + super.propertiesToString();
  }
}
