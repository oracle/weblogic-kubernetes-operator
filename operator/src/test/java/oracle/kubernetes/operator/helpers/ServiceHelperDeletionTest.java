// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.SERVICE;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Service;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.work.TerminalStep;
import org.junit.Before;
import org.junit.Test;

public class ServiceHelperDeletionTest extends ServiceHelperTestBase {
  private static final String SERVICE_NAME = "service1";

  private KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private V1Service service = createMinimalService();
  private ServerKubernetesObjects sko;

  private V1Service createMinimalService() {
    return new V1Service().metadata(new V1ObjectMeta().name(SERVICE_NAME).namespace(NS));
  }

  @Before
  public void setUpDeletionTest() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());

    sko = createSko(service);
    testSupport.addDomainPresenceInfo(domainPresenceInfo);
  }

  private static ServerKubernetesObjects createSko(V1Service service) {
    ServerKubernetesObjects sko = new ServerKubernetesObjects();
    sko.getService().set(service);
    return sko;
  }

  @Test
  public void afterDeleteServiceStepRun_serviceRemovedFromKubernetes() {
    testSupport.defineResources(service);

    testSupport.runSteps(ServiceHelper.deleteServicesStep(sko, null));

    assertThat(testSupport.getResources(SERVICE), empty());
  }

  @Test
  public void afterDeleteServiceStepRun_removeServiceFromSko() {
    testSupport.defineResources(service);

    testSupport.runSteps(ServiceHelper.deleteServicesStep(sko, null));

    assertThat(sko.getService().get(), nullValue());
  }

  @Test
  public void whenServiceNotFound_removeServiceFromSko() {
    testSupport.runSteps(ServiceHelper.deleteServicesStep(sko, null));

    assertThat(sko.getService().get(), nullValue());
  }

  @Test
  public void whenDeleteFails_reportCompletionFailure() {
    testSupport.failOnResource(SERVICE, SERVICE_NAME, NS, HTTP_BAD_REQUEST);

    testSupport.runSteps(ServiceHelper.deleteServicesStep(sko, null));

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  public void whenDeleteServiceStepRunWithNoService_doNotSendDeleteCall() {
    ServerKubernetesObjects sko = createSko(null);

    testSupport.runSteps(ServiceHelper.deleteServicesStep(sko, null));

    assertThat(sko.getService().get(), nullValue());
  }

  @Test
  public void afterDeleteServiceStepRun_runSpecifiedNextStep() {
    TerminalStep terminalStep = new TerminalStep();
    ServerKubernetesObjects sko = createSko(null);

    testSupport.runSteps(ServiceHelper.deleteServicesStep(sko, terminalStep));

    assertThat(terminalStep.wasRun(), is(true));
  }
}
