// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import java.util.logging.LogRecord;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.JSON;
import io.swagger.annotations.ApiModel;
import oracle.kubernetes.common.logging.BaseLoggingFormatter;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Packet;

/** Custom log formatter to format log messages in JSON format. */
public class OperatorLoggingFormatter extends BaseLoggingFormatter<Fiber> {

  @Override
  protected Fiber getCurrentFiberIfSet() {
    return Fiber.getCurrentIfSet();
  }

  @Override
  protected String getFiber() {
    return fiberObject != null ? fiberObject.toString() : "";
  }

  /**
   * Get the domain UID associated with the current log message.
   * Check the fiber that is currently being used to execute the step that initiates the log.
   * If there is no fiber associated with this log, check the ThreadLocal.
   *
   * @return the domain UID or empty string
   */
  @Override
  protected String getDomainUid(Fiber fiber) {
    return Optional.ofNullable(fiber)
            .map(Fiber::getPacket)
            .map(this::getDomainPresenceInfo)
            .map(DomainPresenceInfo::getDomainUid)
            .orElse(getDomainUidFromLoggingContext(fiber));
  }

  private String getDomainUidFromLoggingContext(Fiber fiber) {
    return Optional.ofNullable(fiber)
            .map(Fiber::getPacket)
            .map(p -> p.getSpi(LoggingContext.class))
            .map(LoggingContext::domainUid)
            .orElse(getDomainUidFromThreadContext());
  }

  private String getDomainUidFromThreadContext() {
    return ThreadLoggingContext.optionalContext().map(LoggingContext::domainUid).orElse("");
  }

  private DomainPresenceInfo getDomainPresenceInfo(Packet packet) {
    return packet.getSpi(DomainPresenceInfo.class);
  }

  /**
   * Get the namespace associated with the current log message.
   * Check the fiber that is currently being used to execute the step that initiate the log.
   * If there is no fiber associated with this log, check the ThreadLocal.
   *
   * @return the namespace or empty string
   */
  @Override
  protected String getNamespace(Fiber fiber) {
    return Optional.ofNullable(fiber)
            .map(Fiber::getPacket)
            .map(this::getDomainPresenceInfo)
            .map(DomainPresenceInfo::getNamespace)
            .orElse(getNamespaceFromLoggingContext(fiber));
  }

  private String getNamespaceFromLoggingContext(Fiber fiber) {
    return Optional.ofNullable(fiber)
            .map(Fiber::getPacket)
            .map(p -> p.getSpi(LoggingContext.class))
            .or(ThreadLoggingContext::optionalContext)
            .map(LoggingContext::namespace)
            .orElse("");
  }

  @Override
  protected void serializeModelObjectsWithJSON(LogRecord logRecord) {
    // the toString() format for the model classes is inappropriate for our logs
    // so, replace with the JSON serialization
    JSON j = LoggingFactory.getJson();
    if (j != null) {
      Object[] parameters = logRecord.getParameters();
      if (parameters != null) {
        for (int i = 0; i < parameters.length; i++) {
          Object pi = parameters[i];
          if ((pi != null) && (isModelObject(pi))) {
            // this is a model object
            parameters[i] = j.serialize(pi);
          }
        }
      }
    }
  }

  private boolean isModelObject(Object pi) {
    return pi.getClass().getAnnotation(ApiModel.class) != null
            || pi.getClass().getName().startsWith("oracle.kubernetes.weblogic.domain.");
  }

  @Override
  protected void processThrowable(LogRecord logRecord, ThrowableProcessing throwableProcessing) {
    if (logRecord.getThrown() != null) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      pw.println();
      logRecord.getThrown().printStackTrace(pw);
      pw.close();
      throwableProcessing.setThrowable(sw.toString());
      if (logRecord.getThrown() instanceof ApiException) {
        ApiException ae = (ApiException) logRecord.getThrown();
        throwableProcessing.setCode(String.valueOf(ae.getCode()));
        if (ae.getResponseHeaders() != null) {
          throwableProcessing.setHeaders(ae.getResponseHeaders());
        }
        String rb = ae.getResponseBody();
        if (rb != null) {
          throwableProcessing.setBody(rb);
        }
      }
    }
  }
}
