// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.junit.After;

public class ServiceHelperTestBase {
  static final String DOMAIN_NAME = "domain1";
  static final String NS = "namespace";
  static final String UID = "uid1";
  List<Memento> mementos = new ArrayList<>();
  DomainPresenceInfo domainPresenceInfo = createPresenceInfo();

  /**
   * Tear down test.
   */
  @After
  public void tearDown() {
    for (Memento memento : mementos) {
      memento.revert();
    }
  }

  private DomainPresenceInfo createPresenceInfo() {
    return new DomainPresenceInfo(
        new Domain()
            .withMetadata(new V1ObjectMeta().namespace(NS).name(DOMAIN_NAME))
            .withSpec(createDomainSpec()));
  }

  private DomainSpec createDomainSpec() {
    return new DomainSpec().withDomainUid(UID);
  }
}
