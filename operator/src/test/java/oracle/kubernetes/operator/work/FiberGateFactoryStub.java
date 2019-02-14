// Copyright  2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import oracle.kubernetes.operator.DomainProcessorImpl;

public class FiberGateFactoryStub implements FiberGateFactory {
  public static Memento install(FiberTestSupport testSupport) throws NoSuchFieldException {
    return StaticStubSupport.install(
        DomainProcessorImpl.class, "FACTORY", new FiberGateFactoryStub(testSupport));
  }

  private final FiberTestSupport testSupport;

  public FiberGateFactoryStub(FiberTestSupport testSupport) {
    this.testSupport = testSupport;
  }

  @Override
  public FiberGate get() {
    return testSupport.createFiberGateStub();
  }
}
