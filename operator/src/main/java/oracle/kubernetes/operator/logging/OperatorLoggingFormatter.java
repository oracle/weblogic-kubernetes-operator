// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

import java.util.Optional;

import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Packet;

/** Custom log formatter to format log messages in JSON format. */
public class OperatorLoggingFormatter extends BaseLoggingFormatter<Fiber> {

  @Override
  Fiber getCurrentFiberIfSet() {
    return Fiber.getCurrentIfSet();
  }

  @Override
  String getFiber() {
    return fiber != null ? fiber.toString() : "";
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
  String getNamespace(Fiber fiber) {
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
}
