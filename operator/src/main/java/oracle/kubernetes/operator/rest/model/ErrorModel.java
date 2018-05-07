// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

/** ErrorModel describes an error response from a WebLogic operator REST resource. */
public class ErrorModel extends BaseModel {

  /** Construct an empty ErrorModel. */
  public ErrorModel() {
    setType("http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.1");
    setTitle("FAILURE");
  }

  /**
   * Construct a populated ErrorModel.
   *
   * @param status - the error's the HTTP status code.
   * @param detail - details describing the error.
   */
  public ErrorModel(int status, String detail) {
    this();
    setStatus(status);
    setDetail(detail);
  }

  private int status;

  /**
   * Get the error's HTTP status code.
   *
   * @return the error's HTTP status code.
   */
  public int getStatus() {
    return status;
  }

  /**
   * Set the error's HTTP status code.
   *
   * @param status - the error's HTTP status code.
   */
  public void setStatus(int status) {
    this.status = status;
  }

  private String detail;

  /**
   * Get a detailed description of the error.
   *
   * @return a detailed description of the error.
   */
  public String getDetail() {
    return detail;
  }

  /**
   * Set the details describing the error.
   *
   * @param details - details describing the error.
   */
  public void setDetail(String details) {
    this.detail = details;
  }

  private String type;

  /**
   * Get the error's type.
   *
   * @return the error's type.
   */
  public String getType() {
    return type;
  }

  /**
   * Set the error's type.
   *
   * @param type - the error's type.
   */
  public void setType(String type) {
    this.type = type;
  }

  private String title;

  /**
   * Get the error's title.
   *
   * @return the error's title.
   */
  public String getTitle() {
    return title;
  }

  /**
   * Set the error's title.
   *
   * @param title - the error's title.
   */
  public void setTitle(String title) {
    this.title = title;
  }

  @Override
  protected String propertiesToString() {
    return "status="
        + getStatus()
        + ", title="
        + getTitle()
        + ", detail="
        + getDetail()
        + ", type="
        + getType();
  }
}
