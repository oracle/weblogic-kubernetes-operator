// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

import java.util.Optional;

import oracle.kubernetes.operator.work.Packet;

/** Necessary additional context information for Operator log messages. */
public class LoggingContext {
  public static final String LOGGING_CONTEXT_KEY = "LoggingContextComponent";

  protected String namespace;
  protected String domainUid;

  public static Optional<LoggingContext> fromPacket(Packet packet) {
    return Optional.ofNullable(packet.getSpi(LoggingContext.class));
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
}
