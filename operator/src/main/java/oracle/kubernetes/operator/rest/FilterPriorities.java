// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import javax.ws.rs.Priorities;

/** FilterPriorities orders the WebLogic operator REST api's jaxrs filters. */
public class FilterPriorities {

  /** The authentication filter's priority. */
  public static final int AUTHENTICATION_FILTER_PRIORITY = Priorities.AUTHENTICATION;

  /** The CSRF protection filter's priority. */
  public static final int CSRF_PROTECTION_FILTER_PRIORITY = Priorities.AUTHENTICATION + 100;

  /** The error filter's priority. */
  public static final int ERROR_FILTER_PRIORITY =
      Priorities.ENTITY_CODER; // after entities are converted to json

  /** The request debug logging filter's priority. */
  public static final int REQUEST_DEBUG_LOGGING_FILTER_PRIORITY =
      CSRF_PROTECTION_FILTER_PRIORITY + 200; // after the CSRF filter

  /** The response debug logging filter's priority. */
  public static final int RESPONSE_DEBUG_LOGGING_FILTER_PRIORITY =
      ERROR_FILTER_PRIORITY + 200; // after the error filter

  private FilterPriorities() {
    // hiding implicit public constructor
  }
}
