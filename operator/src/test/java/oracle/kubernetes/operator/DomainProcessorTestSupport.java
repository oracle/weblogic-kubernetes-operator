// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;

/**
 * Test support to preserve the static maps in DomainProcessorImpl so that tests do not affect one another.
 */
class DomainProcessorTestSupport implements Memento {

  private final List<Memento> mementos = new ArrayList<>();
  private final Map<String, Map<String, DomainPresenceInfo>> presenceInfoMap = new HashMap<>();

  Memento install() throws NoSuchFieldException {
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "domains", presenceInfoMap));
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "domainEventK8SObjects", new HashMap<>()));
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "namespaceEventK8SObjects", new HashMap<>()));
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "makeRightFiberGates", new HashMap<>()));
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "statusFiberGates", new HashMap<>()));
    return this;
  }

  @SuppressWarnings("SameParameterValue")
  DomainPresenceInfo getCachedPresenceInfo(String namespace, String uid) {
    return presenceInfoMap.get(namespace).get(uid);
  }

  @Override
  public void revert() {
    mementos.forEach(Memento::revert);
  }

  @Override
  public <T> T getOriginalValue() {
    throw new UnsupportedOperationException();
  }
}
