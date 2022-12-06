// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

import java.util.Optional;

import oracle.kubernetes.operator.helpers.ClusterPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;

public class ThreadLoggingContext extends LoggingContext implements AutoCloseable {

  private static final ThreadLocal<ThreadLoggingContext> currentContext = new ThreadLocal<>();

  @Override
  public ThreadLoggingContext namespace(String namespace) {
    this.namespace = namespace;
    return this;
  }

  @Override
  public ThreadLoggingContext domainUid(String domainUid) {
    this.domainUid = domainUid;
    return this;
  }

  public ThreadLoggingContext presenceInfo(DomainPresenceInfo info) {
    return namespace(info.getNamespace()).domainUid(info.getDomainUid());
  }

  public ThreadLoggingContext presenceInfo(ClusterPresenceInfo info) {
    return namespace(info.getNamespace());
  }

  /**
   * Creates a new LoggingContext and sets it as the current thread context. Returns the created context
   * in order to append parameters.
   * @return a new logging context
   */
  public static ThreadLoggingContext setThreadContext() {
    ThreadLoggingContext loggingContext = new ThreadLoggingContext();
    currentContext.set(loggingContext);
    return loggingContext;
  }

  /**
   * Gets the current logging context on the thread.
   *
   * @return current logging context
   */
  public static Optional<ThreadLoggingContext> optionalContext() {
    return Optional.ofNullable(currentContext.get());
  }

  @Override
  public void close() {
    currentContext.remove();
  }
}
