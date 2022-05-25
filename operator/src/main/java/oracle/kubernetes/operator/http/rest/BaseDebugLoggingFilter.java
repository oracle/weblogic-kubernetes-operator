// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.rest;

import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

/**
 * BaseDebugLoggingFilter provides utilities shared by RequestDebugLoggingFilter and
 * ResponseDebugLoggingFilter.
 */
public abstract class BaseDebugLoggingFilter {

  protected static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  protected static final String FILTER_REQUEST_START_TIME = "FILTER_REQUEST_START_TIME";
  protected static final String FILTER_REQUEST_ENTITY = "FILTER_REQUEST_ENTITY";
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

  protected static String formatTime(TemporalAccessor time) {
    return DATE_FORMAT.format(time);
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
        new MultivaluedHashMap<>(req.getHeaders());

    // Authorization headers contain credentials.  These credentials should not be
    // debug logged since they contain sensitive data.

    // Note: the header names are case-insensitive
    final String atz = HttpHeaders.AUTHORIZATION.toLowerCase();
    for (Map.Entry<String, List<String>> entry : loggableHeaders.entrySet()) {
      if (atz.equals(entry.getKey().toLowerCase())) {
        // make a copy of all the atz header values
        List<String> vals = new ArrayList<>(entry.getValue());
        // hide the sensitive data in the atz header values
        vals.replaceAll(ignored ->
            // By definition, the value of an Authorization header should be in the form
            // "<type> <credential>".  Ideally, we'd change the <credential> part of the
            // value to something like "*****" so that the log would at least show what
            // types of Authorization headers are in the request.  But then we'd need to
            // worry about malformed Authorization headers.
            // For now, keep it simple and set the each value to "*****".
            "*****"
        );
        // replace the original atz header values with ones that hide the credentials
        loggableHeaders.put(entry.getKey(), vals);
      }
    }

    String result = loggableHeaders.toString();
    LOGGER.exiting(result);
    return result;
  }
}
