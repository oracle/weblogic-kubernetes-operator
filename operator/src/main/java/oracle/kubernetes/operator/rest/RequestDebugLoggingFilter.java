// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.OffsetDateTime;
import javax.annotation.Priority;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import org.glassfish.jersey.message.MessageUtils;

/** RequestDebugLoggingFilter debug logs all the REST Requests. */
@Provider
@Priority(FilterPriorities.REQUEST_DEBUG_LOGGING_FILTER_PRIORITY)
public class RequestDebugLoggingFilter extends BaseDebugLoggingFilter
    implements ContainerRequestFilter {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /** Construct a RequestDebugLoggingFilter. */
  public RequestDebugLoggingFilter() {
    // nothing to do
  }

  @Override
  public void filter(ContainerRequestContext req) {
    LOGGER.entering();
    if (!LOGGER.isFineEnabled()) {
      return; // don't waste time doing all the calculations
    }
    try {
      // cache the start time and request body so that the response filter
      // can log them too
      OffsetDateTime start = OffsetDateTime.now();
      req.setProperty(FILTER_REQUEST_START_TIME, start);
      Object reqEntity = getRequestEntity(req);
      if (reqEntity != null) {
        req.setProperty(FILTER_REQUEST_ENTITY, reqEntity);
      }
      String reqHeaders = getLoggableHeaders(req);
      // Always debug log the request even though the response logger logs it too
      // in case the method hangs and we never get a response.
      LOGGER.fine("uri=" + req.getUriInfo().getRequestUri().toString());
      LOGGER.fine("method=" + req.getMethod());
      LOGGER.fine("start=" + formatTime(start));
      LOGGER.fine("request headers=" + reqHeaders);
      LOGGER.fine("request hasEntity=" + req.hasEntity());
      LOGGER.fine("request body=" + reqEntity);
    } catch (Throwable t) {
      // never want this filter to stop the request
      LOGGER.fine("Unexpected throwable ", t);
    }
    LOGGER.exiting();
  }

  private Object getRequestEntity(ContainerRequestContext req) throws Exception {
    // TBD - is it ever safe to debug log the request body since
    // it might contain cleartext passwords and we can't tell at this level?
    String entityAsString = readEntityAsString(req);
    return formatEntity(req.getMediaType(), entityAsString);
  }

  private String readEntityAsString(ContainerRequestContext req) throws Exception {
    LOGGER.entering();
    // Read the entire input stream into a String
    // This should be OK since JSON input shouldn't be monstrously big
    try (BufferedReader ir = new BufferedReader(new InputStreamReader(req.getEntityStream()))) {
      StringBuilder sb = new StringBuilder();
      String line = null;
      Charset cs = MessageUtils.getCharset(req.getMediaType());
      // TBD - is all the Charset handling correct?
      do {
        line = ir.readLine();
        if (line != null) {
          sb.append(line);
        }
      } while (line != null);
      ir.close();
      String entity = sb.toString();

      // Set the request input stream to a clone of the original input stream
      // so that it can be read again
      req.setEntityStream(new ByteArrayInputStream(entity.getBytes(cs)));
      LOGGER.exiting(entity);
      return entity;
    }
  }
}
