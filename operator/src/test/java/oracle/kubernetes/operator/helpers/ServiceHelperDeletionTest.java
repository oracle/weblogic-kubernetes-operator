// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import oracle.kubernetes.operator.calls.FailureStatusSourceException;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.SERVICE;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class ServiceHelperDeletionTest extends ServiceHelperTestBase {
  private static final String UID = "uid1";
  private static final String SERVER_NAME = "server1";
  private static final String SERVICE_NAME = LegalNames.toServerServiceName(UID, SERVER_NAME);

  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final V1Service service = createMinimalService();

  private V1Service createMinimalService() {
    return new V1Service().metadata(new V1ObjectMeta().name(SERVICE_NAME).namespace(NS));
  }

  @BeforeEach
  public void setUpDeletionTest() {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());

    domainPresenceInfo.setServerService(SERVER_NAME, service);
    testSupport.addDomainPresenceInfo(domainPresenceInfo);
  }

  @Test
  public void afterDeleteServiceStepRun_serviceRemovedFromKubernetes() {
    testSupport.defineResources(service);

    testSupport.runSteps(ServiceHelper.deleteServicesStep(SERVER_NAME, null));

    assertThat(testSupport.getResources(SERVICE), empty());
  }

  @Test
  public void afterDeleteServiceStepRun_removeServiceFromSko() {
    testSupport.defineResources(service);

    testSupport.runSteps(ServiceHelper.deleteServicesStep(SERVER_NAME, null));

    assertThat(domainPresenceInfo.getServerService(SERVER_NAME), nullValue());
  }

  @Test
  public void whenServiceNotFound_removeServiceFromSko() {
    testSupport.runSteps(ServiceHelper.deleteServicesStep(SERVER_NAME, null));

    assertThat(domainPresenceInfo.getServerService(SERVER_NAME), nullValue());
  }

  @Test
  public void whenDeleteFails_reportCompletionFailure() {
    testSupport.failOnResource(SERVICE, SERVICE_NAME, NS, HTTP_BAD_REQUEST);

    testSupport.runSteps(ServiceHelper.deleteServicesStep(SERVER_NAME, null));

    testSupport.verifyCompletionThrowable(FailureStatusSourceException.class);
  }

  @Test
  public void whenDeleteServiceStepRunWithNoService_doNotSendDeleteCall() {
    testSupport.runSteps(ServiceHelper.deleteServicesStep(SERVER_NAME, null));

    assertThat(domainPresenceInfo.getServerService(SERVER_NAME), nullValue());
  }

  @Test
  public void afterDeleteServiceStepRun_runSpecifiedNextStep() {
    TerminalStep terminalStep = new TerminalStep();

    testSupport.runSteps(ServiceHelper.deleteServicesStep(SERVER_NAME, terminalStep));

    assertThat(terminalStep.wasRun(), is(true));
  }
}
