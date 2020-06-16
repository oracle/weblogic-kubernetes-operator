// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

/** Necessary additional context information for Operator log messages. */
public class LoggingContext implements AutoCloseable {
  public static final String LOGGING_CONTEXT_KEY = "LoggingContextComponent";

  private static final ThreadLocal<LoggingContext> currentContext = new ThreadLocal<>();

  private String namespace;
  private String domainUid;
  
  /**
   * Gets the current logging context on the thread.
   *
   * @return current logging context
   */
  public static LoggingContext context() {
    return currentContext.get();
  }

  /**
   * Set the current LoggingContext on the thread.
   *
   * @param loggingContext instance of {@link LoggingContext}
   */
  public static LoggingContext context(LoggingContext loggingContext) {
    currentContext.set(loggingContext);
    return loggingContext;
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

  public void close() {
    currentContext.remove();
  }
}
