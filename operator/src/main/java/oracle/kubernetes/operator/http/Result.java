// Copyright 2018, 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http;

/**
 * Holder of response received from REST requests invoked using methods in {@link HttpClient} class.
 */
public class Result {

  final String response;
  final int status;
  final boolean successful;

  public Result(String response, int status, boolean successful) {
    this.response = response;
    this.status = status;
    this.successful = successful;
  }

  /** @return The String response received from the REST request */
  public String getResponse() {
    return response;
  }

  /** @return HTTP status code from the REST request */
  public int getStatus() {
    return status;
  }

  /**
   * @return True if the REST request returns a status code that indicates successful request, false
   *     otherwise
   */
  public boolean isSuccessful() {
    return successful;
  }

  /**
   * @return true if the HTTP status code from the REST request indicates that the server may be
   *     overloaded
   */
  public boolean isServerOverloaded() {
    return status == 500 || status == 503;
  }

  @Override
  public String toString() {
    return "Result{"
        + "response='"
        + response
        + '\''
        + ", status="
        + status
        + ", successful="
        + successful
        + '}';
  }
}
