// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

import java.util.Optional;

import oracle.kubernetes.operator.helpers.DomainPresenceInfo;

/** Necessary additional context information for Operator log messages. */
public class LoggingContext implements AutoCloseable {
  public static final String LOGGING_CONTEXT_KEY = "LoggingContextComponent";

  private static final ThreadLocal<LoggingContext> currentContext = new ThreadLocal<>();

  private String namespace;
  private String domainUid;

  /**
   * Creates a new LoggingContext and sets it as the current thread context. Returns the created context
   * in order to append parameters.
   * @return a new logging context
   */
  public static LoggingContext setThreadContext() {
    LoggingContext loggingContext = new LoggingContext();
    currentContext.set(loggingContext);
    return loggingContext;
  }
  
  /**
   * Gets the current logging context on the thread.
   *
   * @return current logging context
   */
  public static Optional<LoggingContext> optionalContext() {
    return Optional.ofNullable(currentContext.get());
  }

  public LoggingContext namespace(String namespace) {
    this.namespace = namespace;
    return this;
  }
  
  public String namespace() {
    return namespace;
  }
  
  public LoggingContext domainUid(String domainUid) {
    this.domainUid = domainUid;
    return this;
  }

  public String domainUid() {
    return domainUid;
  }

  public LoggingContext presenceInfo(DomainPresenceInfo info) {
    return namespace(info.getNamespace()).domainUid(info.getDomainUid());
  }

  public void close() {
    currentContext.remove();
  }
}
