// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

/** LinkModel describes a link to a WebLogic operator REST resource. */
public class LinkModel extends BaseModel {

  private String rel;
  private String title;
  private String href;

  /** Construct an empty LinkModel. */
  public LinkModel() {
  }

  /**
   * Construct a populated untitled LinkModel.
   *
   * @param rel - the link's relationship.
   * @param href - the link's hypertext reference.
   */
  public LinkModel(String rel, String href) {
    this(rel, "", href);
  }

  /**
   * Construct a populated LinkModel.
   *
   * @param rel - the link's relationship.
   * @param title -the link's title.
   * @param href - the link's hypertext reference.
   */
  public LinkModel(String rel, String title, String href) {
    setRel(rel);
    setTitle(title);
    setHref(href);
  }

  /**
   * Get the link's relationship.
   *
   * @return the link's relationship.
   */
  public String getRel() {
    return rel;
  }

  /**
   * Set the link's relationship.
   *
   * @param rel - the link's relationship.
   */
  public void setRel(String rel) {
    this.rel = rel;
  }

  /**
   * Get the link's title.
   *
   * @return the link's title.
   */
  public String getTitle() {
    return title;
  }

  /**
   * Set the link's title.
   *
   * @param title - the link's title.
   */
  public void setTitle(String title) {
    this.title = title;
  }

  /**
   * Get the link's hypertext reference.
   *
   * @return the link's hypertext reference.
   */
  public String getHref() {
    return href;
  }

  /**
   * Set the link's hypertext reference.
   *
   * @param href - the link's hypertext reference.
   */
  public void setHref(String href) {
    this.href = href;
  }

  @Override
  protected String propertiesToString() {
    return "rel=" + getRel() + ", title=" + getTitle() + ", href=" + getHref();
  }
}
