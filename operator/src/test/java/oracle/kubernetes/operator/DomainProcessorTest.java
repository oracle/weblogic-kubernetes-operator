// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.operator.KubernetesConstants.INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.LabelConstants.RESOURCE_VERSION_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD_NAME;
import static oracle.kubernetes.operator.VersionConstants.DEFAULT_DOMAIN_VERSION;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.google.common.collect.ImmutableMap;
import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1JobCondition;
import io.kubernetes.client.models.V1JobStatus;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodSpec;
import io.kubernetes.client.models.V1SecretReference;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.helpers.AnnotationHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.helpers.TuningParametersStub;
import oracle.kubernetes.operator.helpers.UnitTestHash;
import oracle.kubernetes.operator.utils.InMemoryCertificates;
import oracle.kubernetes.operator.work.FiberGate;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DomainProcessorTest {
  private static final String UID = "test-domain";
  private static final String NS = "namespace";
  private static final String ADMIN_SERVER_NAME = "admin";
  private static final String INTROSPECTION_JOB = "jobPod";
  private static final String CLUSTER = "cluster";
  private static final int MAX_SERVERS = 5;
  private static final String MS_PREFIX = "managed-server";
  private static final int MIN_REPLICAS = 2;
  private static final int NUM_ADMIN_SERVERS = 1;
  private static final int NUM_JOB_PODS = 1;

  private List<Memento> mementos = new ArrayList<>();
  private KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private DomainConfigurator domainConfigurator;
  private String[] managedServerNames =
      IntStream.rangeClosed(1, MAX_SERVERS).mapToObj(n -> MS_PREFIX + n).toArray(String[]::new);
  private Map<String, DomainPresenceInfo> presenceInfoMap = new HashMap<>();
  private DomainProcessorDelegateStub delegate =
      createStrictStub(DomainProcessorDelegateStub.class, testSupport);
  private DomainProcessorImpl processor = new DomainProcessorImpl(delegate);
  private Domain domain =
      new Domain()
          .withMetadata(withTimestamps(new V1ObjectMeta().name(UID).namespace(NS)))
          .withSpec(
              new DomainSpec()
                  .withWebLogicCredentialsSecret(new V1SecretReference().name("secret-name")));

  private static V1ObjectMeta withTimestamps(V1ObjectMeta meta) {
    return meta.creationTimestamp(DateTime.now()).resourceVersion("1");
  }

  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "DOMAINS", presenceInfoMap));
    mementos.add(TuningParametersStub.install());
    mementos.add(InMemoryCertificates.install());
    mementos.add(UnitTestHash.install());

    domainConfigurator = DomainConfiguratorFactory.forDomain(domain);
    testSupport.addToPacket(JOB_POD_NAME, INTROSPECTION_JOB);
    testSupport.doOnCreate(
        KubernetesTestSupport.JOB,
        job ->
            ((V1Job) job)
                .setStatus(
                    new V1JobStatus()
                        .addConditionsItem(new V1JobCondition().type("Complete").status("True"))));
    testSupport.definePodLog(LegalNames.toJobIntrospectorName(UID), NS, INTROSPECT_RESULT);
    testSupport.defineResources(
        new V1Pod()
            .metadata(
                new V1ObjectMeta()
                    .putLabelsItem("job-name", "")
                    .name(LegalNames.toJobIntrospectorName(UID))
                    .namespace(NS)),
        new V1ConfigMap()
            .metadata(
                new V1ObjectMeta().name(UID + INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX).namespace(NS))
            .data(new HashMap<>(ImmutableMap.of("topology.yaml", TOPOLOGY_YAML))),
        new V1Job()
            .metadata(
                new V1ObjectMeta().name(LegalNames.toJobIntrospectorName(UID)).namespace(NS)));
  }

  @After
  public void tearDown() {
    for (Memento memento : mementos) memento.revert();
  }

  @Test
  public void whenDomainConfiguredForMaxServers_establishMatchingPresence() {
    domainConfigurator.configureCluster(CLUSTER).withReplicas(MAX_SERVERS);

    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    processor.makeRightDomainPresence(info, true, false, false);

    assertServerPodAndServicePresent(info, ADMIN_SERVER_NAME);
    for (String serverName : managedServerNames) assertServerPodAndServicePresent(info, serverName);

    assertThat(info.getClusterService(CLUSTER), notNullValue());
  }

  @Test
  public void whenDomainScaledDown_removeExcessPodsAndServices() {
    defineServerResources(ADMIN_SERVER_NAME);
    Arrays.stream(managedServerNames).forEach(this::defineServerResources);

    domainConfigurator.configureCluster(CLUSTER).withReplicas(MIN_REPLICAS);
    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    processor.makeRightDomainPresence(info, true, false, false);

    assertThat((int) getServerServices().count(), equalTo(MIN_REPLICAS + NUM_ADMIN_SERVERS));
    assertThat(getRunningPods().size(), equalTo(MIN_REPLICAS + NUM_ADMIN_SERVERS + NUM_JOB_PODS));
  }

  @Test
  public void whenDomainShutDown_removeAllPodsAndServices() {
    defineServerResources(ADMIN_SERVER_NAME);
    Arrays.stream(managedServerNames).forEach(this::defineServerResources);

    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    processor.makeRightDomainPresence(info, true, true, true);

    assertThat(getRunningServices(), empty());
    assertThat(getRunningPods(), empty());
  }

  @Test
  public void whenDomainShutDown_ignoreNonOperatorServices() {
    defineServerResources(ADMIN_SERVER_NAME);
    Arrays.stream(managedServerNames).forEach(this::defineServerResources);
    testSupport.defineResources(createNonOperatorService());

    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    processor.makeRightDomainPresence(info, true, true, true);

    assertThat(getRunningServices(), contains(createNonOperatorService()));
    assertThat(getRunningPods(), empty());
  }

  private V1Service createNonOperatorService() {
    return new V1Service()
        .metadata(
            new V1ObjectMeta()
                .name("do-not-delete-service")
                .namespace(NS)
                .putLabelsItem("serviceType", "SERVER")
                .putLabelsItem(CREATEDBYOPERATOR_LABEL, "false")
                .putLabelsItem(DOMAINNAME_LABEL, UID)
                .putLabelsItem(DOMAINUID_LABEL, UID)
                .putLabelsItem(RESOURCE_VERSION_LABEL, DEFAULT_DOMAIN_VERSION)
                .putLabelsItem(SERVERNAME_LABEL, ADMIN_SERVER_NAME))
        .spec(new V1ServiceSpec().type("ClusterIP"));
  }

  private Stream<V1Service> getServerServices() {
    return getRunningServices().stream().filter(ServiceHelper::isServerService);
  }

  private List<V1Service> getRunningServices() {
    return testSupport.getResources(KubernetesTestSupport.SERVICE);
  }

  private List<V1Pod> getRunningPods() {
    return testSupport.getResources(KubernetesTestSupport.POD);
  }

  private void defineServerResources(String serverName) {
    testSupport.defineResources(createServerPod(serverName), createServerService(serverName));
  }

  private V1Pod createServerPod(String serverName) {
    return AnnotationHelper.withSha256Hash(
        new V1Pod()
            .metadata(
                withServerLabels(
                    new V1ObjectMeta().name(LegalNames.toServerName(UID, serverName)).namespace(NS),
                    serverName))
            .spec(new V1PodSpec()));
  }

  private V1ObjectMeta withServerLabels(V1ObjectMeta meta, String serverName) {
    return KubernetesUtils.withOperatorLabels(meta, UID)
        .putLabelsItem(SERVERNAME_LABEL, serverName);
  }

  private V1Service createServerService(String serverName) {
    return AnnotationHelper.withSha256Hash(
        new V1Service()
            .metadata(
                withServerLabels(
                    new V1ObjectMeta()
                        .name(LegalNames.toServerServiceName(UID, serverName))
                        .namespace(NS),
                    serverName)));
  }

  private void assertServerPodAndServicePresent(DomainPresenceInfo info, String serverName) {
    assertThat(serverName + " server service", info.getServerService(serverName), notNullValue());
    assertThat(serverName + " pod", info.getServerPod(serverName), notNullValue());
  }

  abstract static class DomainProcessorDelegateStub implements DomainProcessorDelegate {
    private FiberTestSupport testSupport;

    public DomainProcessorDelegateStub(FiberTestSupport testSupport) {
      this.testSupport = testSupport;
    }

    @Override
    public boolean isNamespaceRunning(String namespace) {
      return true;
    }

    @Override
    public PodAwaiterStepFactory getPodAwaiterStepFactory(String namespace) {
      return (pod, next) -> next;
    }

    @Override
    public KubernetesVersion getVersion() {
      return KubernetesVersion.TEST_VERSION;
    }

    @Override
    public FiberGate createFiberGate() {
      return testSupport.createFiberGate();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable command, long initialDelay, long delay, TimeUnit unit) {
      return testSupport.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }
  }

  private static final String TOPOLOGY_YAML =
      "domainValid: true\n"
          + "domain:\n"
          + "  name: \"base_domain\"\n"
          + "  adminServerName: \""
          + ADMIN_SERVER_NAME
          + "\"\n"
          + "  configuredClusters:\n"
          + "    - name: \""
          + CLUSTER
          + "\"\n"
          + "      servers:\n"
          + "        - name: \""
          + MS_PREFIX
          + "1\"\n"
          + "          listenPort: 8001\n"
          + "          listenAddress: \"domain1-managed-server1\"\n"
          + "        - name: \""
          + MS_PREFIX
          + "2\"\n"
          + "          listenPort: 8001\n"
          + "          listenAddress: \"domain1-managed-server2\"\n"
          + "        - name: \""
          + MS_PREFIX
          + "3\"\n"
          + "          listenPort: 8001\n"
          + "          listenAddress: \"domain1-managed-server3\"\n"
          + "        - name: \""
          + MS_PREFIX
          + "4\"\n"
          + "          listenPort: 8001\n"
          + "          listenAddress: \"domain1-managed-server4\"\n"
          + "        - name: \""
          + MS_PREFIX
          + "5\"\n"
          + "          listenPort: 8001\n"
          + "          listenAddress: \"domain1-managed-server5\"\n"
          + "  servers:\n"
          + "    - name: \""
          + ADMIN_SERVER_NAME
          + "\"\n"
          + "      listenPort: 7001\n"
          + "      listenAddress: \"domain1-admin-server\"\n";

  private static final String INTROSPECT_RESULT =
      ">>>  /u01/introspect/domain1/userConfigNodeManager.secure\n"
          + "#WebLogic User Configuration File; 2\n"
          + "#Thu Oct 04 21:07:06 GMT 2018\n"
          + "weblogic.management.username={AES}fq11xKVoE927O07IUKhQ00d4A8QY598Dvd+KSnHNTEA\\=\n"
          + "weblogic.management.password={AES}LIxVY+aqI8KBkmlBTwkvAnQYQs4PS0FX3Ili4uLBggo\\=\n"
          + "\n"
          + ">>> EOF\n"
          + "\n"
          + "@[2018-10-04T21:07:06.864 UTC][introspectDomain.py:105] Printing file /u01/introspect/domain1/userKeyNodeManager.secure\n"
          + "\n"
          + ">>>  /u01/introspect/domain1/userKeyNodeManager.secure\n"
          + "BPtNabkCIIc2IJp/TzZ9TzbUHG7O3xboteDytDO3XnwNhumdSpaUGKmcbusdmbOUY+4J2kteu6xJPWTzmNRAtg==\n"
          + "\n"
          + ">>> EOF\n"
          + "\n"
          + "@[2018-10-04T21:07:06.867 UTC][introspectDomain.py:105] Printing file /u01/introspect/domain1/topology.yaml\n"
          + "\n"
          + ">>>  /u01/introspect/domain1/topology.yaml\n"
          + TOPOLOGY_YAML
          + "\n"
          + ">>> EOF";
}
