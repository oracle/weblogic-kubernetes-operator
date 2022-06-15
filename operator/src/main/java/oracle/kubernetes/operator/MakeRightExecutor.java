// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.function.Predicate;

import oracle.kubernetes.operator.helpers.DomainPresenceInfo;

public interface MakeRightExecutor {

  void runMakeRight(MakeRightDomainOperation operation, Predicate<DomainPresenceInfo> shouldProceed);

  void scheduleDomainStatusUpdating(DomainPresenceInfo info);

  void registerDomainPresenceInfo(DomainPresenceInfo info);

  void unregisterDomain(DomainPresenceInfo info);

  void endStatusUpdates(DomainPresenceInfo info);
}
