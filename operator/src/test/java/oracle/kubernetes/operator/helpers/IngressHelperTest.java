// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.KubernetesConstants.CLASS_INGRESS;
import static oracle.kubernetes.operator.KubernetesConstants.CLASS_INGRESS_VALUE;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.LabelConstants.RESOURCE_VERSION_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.CLUSTER_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.PORT;
import static oracle.kubernetes.operator.helpers.CallBuilder.NOT_FOUND;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1beta1HTTPIngressPath;
import io.kubernetes.client.models.V1beta1HTTPIngressRuleValue;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.models.V1beta1IngressBackend;
import io.kubernetes.client.models.V1beta1IngressRule;
import io.kubernetes.client.models.V1beta1IngressSpec;
import java.util.ArrayList;
import java.util.List;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainSpec;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class IngressHelperTest {

  private static final String NS = "namespace";
  private static final int TEST_PORT = 7000;
  private static final String TEST_CLUSTER = "cluster-1";
  private static final String DOMAIN_NAME = "domain1";
  private static final String UID = "uid1";
  private static final String BAD_VERSION = "bad-version";
  private static final int BAD_PORT = 9999;
  private static final V1beta1Ingress INGRESS_RESOURCE = createIngressResource();

  private DomainPresenceInfo domainPresenceInfo = createPresenceInfo();
  private AsyncCallTestSupport testSupport = new AsyncCallTestSupport();
  private List<Memento> mementos = new ArrayList<>();

  private static V1beta1Ingress createIngressResource() {
    return new V1beta1Ingress()
        .apiVersion(KubernetesConstants.EXTENSIONS_API_VERSION)
        .kind(KubernetesConstants.KIND_INGRESS)
        .metadata(
            new V1ObjectMeta()
                .name(LegalNames.toIngressName(UID, TEST_CLUSTER))
                .namespace(NS)
                .putAnnotationsItem(CLASS_INGRESS, CLASS_INGRESS_VALUE)
                .putLabelsItem(RESOURCE_VERSION_LABEL, VersionConstants.DEFAULT_DOMAIN_VERSION)
                .putLabelsItem(DOMAINUID_LABEL, UID)
                .putLabelsItem(DOMAINNAME_LABEL, DOMAIN_NAME)
                .putLabelsItem(CLUSTERNAME_LABEL, TEST_CLUSTER)
                .putLabelsItem(CREATEDBYOPERATOR_LABEL, "true"))
        .spec(createIngressSpec());
  }

  private static V1beta1IngressSpec createIngressSpec() {
    String serviceName = LegalNames.toClusterServiceName(UID, TEST_CLUSTER);
    return new V1beta1IngressSpec()
        .addRulesItem(
            new V1beta1IngressRule()
                .http(
                    new V1beta1HTTPIngressRuleValue()
                        .addPathsItem(
                            new V1beta1HTTPIngressPath()
                                .path("/")
                                .backend(
                                    new V1beta1IngressBackend()
                                        .serviceName(serviceName)
                                        .servicePort(new IntOrString(TEST_PORT))))));
  }

  private DomainPresenceInfo createPresenceInfo() {
    Domain domain =
        new Domain()
            .withMetadata(new V1ObjectMeta().namespace(NS))
            .withSpec(new DomainSpec().withDomainName(DOMAIN_NAME).withDomainUID(UID));
    return new DomainPresenceInfo(domain);
  }

  @Before
  public void setUp() throws Exception {
    mementos.add(testSupport.installRequestStepFactory());
    mementos.add(TestUtils.silenceOperatorLogger());

    testSupport
        .addToPacket(CLUSTER_NAME, TEST_CLUSTER)
        .addToPacket(PORT, TEST_PORT)
        .addDomainPresenceInfo(domainPresenceInfo);
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) memento.revert();
    testSupport.throwOnCompletionFailure();
    testSupport.verifyAllDefinedResponsesInvoked();
  }

  @Test
  public void whenPortNotInPacket_stepDoesNothing() {
    testSupport.removeFromPacket(PORT);

    runCreateClusterStep();
  }

  private void runCreateClusterStep() {
    testSupport.runSteps(IngressHelper.createClusterStep(null));
  }

  @Test
  public void whenClusterNameNotInPacket_stepDoesNothing() {
    testSupport.removeFromPacket(CLUSTER_NAME);

    runCreateClusterStep();
  }

  @Test
  public void whenNoMatchingIngressExists_createOne() {
    createCannedReadResponse();
    testSupport
        .createCannedResponse("createIngress")
        .withNamespace(NS)
        .withBody(INGRESS_RESOURCE)
        .returning(INGRESS_RESOURCE);

    runCreateClusterStep();

    assertThat(getExistingIngress(), sameInstance(INGRESS_RESOURCE));
  }

  private void createCannedReadResponse() {
    CallTestSupport.CannedResponse cannedResponse =
        testSupport
            .createCannedResponse("readIngress")
            .withNamespace(NS)
            .withName(LegalNames.toIngressName(UID, TEST_CLUSTER));

    V1beta1Ingress ingress = getExistingIngress();
    if (ingress == null) {
      cannedResponse.failingWithStatus(NOT_FOUND);
    } else {
      cannedResponse.returning(ingress);
    }
  }

  private V1beta1Ingress getExistingIngress() {
    return domainPresenceInfo.getIngresses().get(TEST_CLUSTER);
  }

  @Test
  public void whenMatchingIngressExists_leaveItAlone() {
    setExistingIngress(INGRESS_RESOURCE);
    createCannedReadResponse();

    runCreateClusterStep();

    assertThat(getExistingIngress(), sameInstance(INGRESS_RESOURCE));
  }

  private void setExistingIngress(V1beta1Ingress ingressResource) {
    domainPresenceInfo.getIngresses().put(TEST_CLUSTER, ingressResource);
  }

  @Test
  public void whenMatchingIngressExistsButHasDifferentVersion_replaceIt() {
    setExistingIngress(ingressWithDifferentResourceVersion());
    createCannedReadResponse();
    testSupport
        .createCannedResponse("replaceIngress")
        .withName(LegalNames.toIngressName(UID, TEST_CLUSTER))
        .withNamespace(NS)
        .withBody(INGRESS_RESOURCE)
        .returning(INGRESS_RESOURCE);

    runCreateClusterStep();

    assertThat(getExistingIngress(), sameInstance(INGRESS_RESOURCE));
  }

  private V1beta1Ingress ingressWithDifferentResourceVersion() {
    V1beta1Ingress ingress = createIngressResource();
    ingress.getMetadata().putLabelsItem(RESOURCE_VERSION_LABEL, BAD_VERSION);
    return ingress;
  }

  @SuppressWarnings("unchecked")
  @Test
  public void whenMatchingIngressExistsButHasDifferentSpec_replaceIt() {
    setExistingIngress(ingressWithDifferentSpec());
    createCannedReadResponse();
    testSupport
        .createCannedResponse("replaceIngress")
        .withName(LegalNames.toIngressName(UID, TEST_CLUSTER))
        .withNamespace(NS)
        .withBody(INGRESS_RESOURCE)
        .returning(INGRESS_RESOURCE);

    runCreateClusterStep();

    assertThat(getExistingIngress(), sameInstance(INGRESS_RESOURCE));
  }

  private V1beta1Ingress ingressWithDifferentSpec() {
    V1beta1Ingress ingress = createIngressResource();
    ingress
        .getSpec()
        .setBackend(new V1beta1IngressBackend().servicePort(new IntOrString(BAD_PORT)));
    return ingress;
  }
}
