// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import com.meterware.simplestub.Memento;
import java.util.ArrayList;
import java.util.List;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.work.AsyncCallTestSupport;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import org.junit.After;
import org.junit.Before;

public class ManagedServersUpStepTest {

  private AsyncCallTestSupport testSupport = new AsyncCallTestSupport();
  private List<Memento> mementos = new ArrayList<>();
  private DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

  private DomainPresenceInfo createDomainPresenceInfo() {
    return new DomainPresenceInfo(new Domain());
  }

  @Before
  public void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.installRequestStepFactory());
    testSupport.addDomainPresenceInfo(domainPresenceInfo);
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) memento.revert();

    testSupport.throwOnCompletionFailure();
    testSupport.verifyAllDefinedResponsesInvoked();
  }
}
