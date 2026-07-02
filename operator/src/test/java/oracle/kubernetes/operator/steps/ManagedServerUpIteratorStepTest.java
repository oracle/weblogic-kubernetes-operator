// Copyright (c) 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import io.kubernetes.client.extended.controller.reconciler.Result;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.KUBERNETES;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class ManagedServerUpIteratorStepTest {

  @Test
  void whenManagedPodIsMissingAndDomainHasRetryableFailure_dontRequeue() {
    DomainResource domain = new DomainResource().withStatus(
        new DomainStatus().addCondition(new DomainCondition(FAILED).withReason(KUBERNETES)));
    Packet packet = new Packet();
    packet.put(ProcessingConstants.DOMAIN_PRESENCE_INFO, new DomainPresenceInfo(domain));

    Result result = new ManagedServerUpIteratorStep.ManagedPodReadyStep("managed-server1", null).apply(packet);

    assertThat(result.isRequeue(), is(false));
  }
}
