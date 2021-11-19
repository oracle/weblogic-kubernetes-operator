// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import java.util.Arrays;

import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainList;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.weblogic.domain.model.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Available;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Failed;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Progressing;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class CallResponseTest {

  @Test
  void listDomainsAsync_returnsUpgrade() {
    Domain domain1 = new Domain();
    DomainStatus domainStatus1 = new DomainStatus().withStartTime(null);
    domain1.setStatus(domainStatus1);

    domainStatus1.getConditions().add(new DomainCondition(Progressing).withLastTransitionTime(null));
    domainStatus1.getConditions().add(new DomainCondition(Available).withLastTransitionTime(null));

    Domain domain2 = new Domain();
    DomainStatus domainStatus2 = new DomainStatus().withStartTime(null);
    domain2.setStatus(domainStatus2);

    domainStatus2.getConditions().add(new DomainCondition(Progressing).withLastTransitionTime(null));
    domainStatus2.getConditions().add(new DomainCondition(Failed).withLastTransitionTime(null));

    DomainList list = new DomainList().withItems(Arrays.asList(domain1, domain2));

    CallResponse response = CallResponse.createSuccess(null, list, 200);

    DomainList received = (DomainList) response.getResult();
    assertThat(received.getItems(), hasSize(2));
    assertThat(received.getItems().get(0).getStatus(), not(hasCondition(Progressing)));
    assertThat(received.getItems().get(1).getStatus(), not(hasCondition(Progressing)));
  }

}
