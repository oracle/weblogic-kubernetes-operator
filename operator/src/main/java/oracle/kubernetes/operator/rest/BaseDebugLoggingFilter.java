// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

/**
 * BaseDebugLoggingFilter provides utilities shared by RequestDebugLoggingFilter and
 * ResponseDebugLoggingFilter.
 */
public abstract class BaseDebugLoggingFilter {

  protected static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String DATE_FORMAT =
      "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"; // ISO 8610, includes time zone

  protected static final String FILTER_REQUEST_START_TIME = "FILTER_REQUEST_START_TIME";
  protected static final String FILTER_REQUEST_ENTITY = "FILTER_REQUEST_ENTITY";

  protected static String formatTime(long time) {
    // construct a new SimpleDataFormat each time since it is not thread safe:
    return new SimpleDateFormat(DATE_FORMAT).format(new Date(time));
  }

  protected String formatEntity(MediaType type, String entityAsString) {
    String result = entityAsString;
    if (!MediaType.APPLICATION_JSON_TYPE.isCompatible(type)) {
      // TODO - convert to pretty printed json
    }
    return result;
  }

  protected String getLoggableHeaders(ContainerRequestContext req) {
    LOGGER.entering();

    // Make a copy of all of the request headers
    MultivaluedHashMap<String, String> loggableHeaders =
        new MultivaluedHashMap<String, String>(req.getHeaders());

    // Authorization headers contain credentials.  These credentials should not be
    // debug logged since they contain sensitive data.

    // Note: the header names are case-insensitive
    final String atz = HttpHeaders.AUTHORIZATION.toLowerCase();
    for (String key : loggableHeaders.keySet()) {
      if (atz.equals(key.toLowerCase())) {
        // make a copy of all the atz header values
        List<String> vals = new ArrayList<>(loggableHeaders.get(key));
        // hide the sensitive data in the atz header values
        for (int i = 0; i < vals.size(); i++) {
          // By definition, the value of an Authorization header should be in the form
          // "<type> <credential>".  Ideally, we'd change the <credential> part of the
          // value to something like "*****" so that the log would at least show what
          // types of Authorization headers are in the request.  But then we'd need to
          // worry about malformed Authorization headers.
          // For now, keep it simple and set the each value to "*****".
          vals.set(i, "*****");
        }
        // replace the original atz header values with ones that hide the credentials
        loggableHeaders.put(key, vals);
      }
    }

    String result = loggableHeaders.toString();
    LOGGER.exiting(result);
    return result;
  }
}
