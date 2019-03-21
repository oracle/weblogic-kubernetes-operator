// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.models.V1ObjectMeta;
import java.util.ArrayList;
import java.util.List;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.junit.After;

public class ServiceHelperTestBase {
  protected static final String NS = "namespace";
  protected static final String UID = "uid1";
  protected List<Memento> mementos = new ArrayList<>();
  protected DomainPresenceInfo domainPresenceInfo = createPresenceInfo();

  @After
  public void tearDown() {
    for (Memento memento : mementos) memento.revert();
  }

  private DomainPresenceInfo createPresenceInfo() {
    return new DomainPresenceInfo(
        new Domain().withMetadata(new V1ObjectMeta().namespace(NS)).withSpec(createDomainSpec()));
  }

  private DomainSpec createDomainSpec() {
    return new DomainSpec().withDomainUID(UID);
  }
}
