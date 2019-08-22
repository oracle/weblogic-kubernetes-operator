// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.IntStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.meterware.simplestub.Memento;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1SecretReference;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.weblogic.domain.model.Cluster;
import oracle.kubernetes.weblogic.domain.model.ConfigurationConstants;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.LogMatcher.containsInfo;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.logging.MessageKeys.JOB_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.JOB_DELETED;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@SuppressWarnings({"SameParameterValue"})
public class DomainIntrospectorJobTest {
  private static final String NODEMGR_HOME = "/u01/nodemanager";
  private static final String OVERRIDES_CM = "overrides-config-map";
  private static final String OVERRIDE_SECRET_1 = "override-secret-1";
  private static final String OVERRIDE_SECRET_2 = "override-secret-2";
  private static final String LOG_HOME = "/shared/logs/" + UID;
  private static final String CREDENTIALS_SECRET_NAME = "webLogicCredentialsSecretName";
  private static final String LATEST_IMAGE = "image:latest";
  private static final String ADMIN_NAME = "admin";
  private static final int MAX_SERVERS = 2;
  private static final String MS_PREFIX = "managed-server";
  private static final String[] MANAGED_SERVER_NAMES =
      IntStream.rangeClosed(1, MAX_SERVERS).mapToObj(n -> MS_PREFIX + n).toArray(String[]::new);
  static final String ERROR_1 = "something bad happened";

  private final TerminalStep terminalStep = new TerminalStep();
  private final Domain domain = createDomain();
  private final DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo(domain);
  private KubernetesTestSupport testSupport = new KubernetesTestSupport();
  protected List<Memento> mementos = new ArrayList<>();
  protected List<LogRecord> logRecords = new ArrayList<>();
  private RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);

  public DomainIntrospectorJobTest() {
  }

  private static String getJobName() {
    return LegalNames.toJobIntrospectorName(UID);
  }

  private static Matcher<Iterable<? super V1EnvVar>> hasEnvVar(String name, String value) {
    return hasItem(new V1EnvVar().name(name).value(value));
  }

  @Before
  public void setUp() throws Exception {
    mementos.add(
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, getMessageKeys())
            .withLogLevel(Level.INFO));
    mementos.add(TuningParametersStub.install());
    mementos.add(testSupport.install());
    testSupport.addDomainPresenceInfo(domainPresenceInfo);
  }

  private String[] getMessageKeys() {
    return new String[] {getJobCreatedMessageKey(), getJobDeletedMessageKey()};
  }

  @After
  public void tearDown() {
    for (Memento memento : mementos) memento.revert();
  }

  private Domain createDomain() {
    return new Domain()
        .withMetadata(new V1ObjectMeta().name(UID).namespace(NS))
        .withSpec(createDomainSpec());
  }

  private DomainPresenceInfo createDomainPresenceInfo(Domain domain) {
    return new DomainPresenceInfo(domain);
  }

  private DomainSpec createDomainSpec() {
    Cluster cluster = new Cluster();
    cluster.setClusterName("cluster-1");
    cluster.setReplicas(1);
    cluster.setServerStartPolicy(ConfigurationConstants.START_IF_NEEDED);
    DomainSpec spec =
        new DomainSpec()
            .withDomainUid(UID)
            .withWebLogicCredentialsSecret(new V1SecretReference().name(CREDENTIALS_SECRET_NAME))
            .withConfigOverrides(OVERRIDES_CM)
            .withCluster(cluster)
            .withImage(LATEST_IMAGE)
            .withDomainHomeInImage(false);
    spec.setServerStartPolicy(ConfigurationConstants.START_IF_NEEDED);

    List<String> overrideSecrets = new ArrayList<>();
    overrideSecrets.add(OVERRIDE_SECRET_1);
    overrideSecrets.add(OVERRIDE_SECRET_2);
    spec.setConfigOverrideSecrets(overrideSecrets);

    return spec;
  }

  private String getJobCreatedMessageKey() {
    return JOB_CREATED;
  }

  private String getJobDeletedMessageKey() {
    return JOB_DELETED;
  }

  @Test
  public void whenNoJob_createIt() throws JsonProcessingException {
    new DomainProcessorTestSetup(testSupport).defineKubernetesResources(createDomainConfig());
    testSupport.defineResources(
        new V1ConfigMap()
            .metadata(
                new V1ObjectMeta()
                    .namespace(NS)
                    .name(ConfigMapHelper.SitConfigMapContext.getConfigMapName(UID))));

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, containsInfo(getJobCreatedMessageKey()));
    assertThat(logRecords, containsInfo(getJobDeletedMessageKey()));
  }

  private static WlsDomainConfig createDomainConfig() {
    WlsClusterConfig clusterConfig = new WlsClusterConfig("cluster-1");
    for (String serverName : MANAGED_SERVER_NAMES) {
      clusterConfig.addServerConfig(new WlsServerConfig(serverName, "domain1-" + serverName, 8001));
    }
    return new WlsDomainConfig("base_domain")
        .withAdminServer(ADMIN_NAME, "domain1-admin-server", 7001)
        .withCluster(clusterConfig);
  }

  private FiberTestSupport.StepFactory getStepFactory() {
    return JobHelper::createDomainIntrospectorJobStep;
  }

  @Test
  public void whenNoJob_retryOnFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnResource(KubernetesTestSupport.JOB, getJobName(), NS, 401);

    testSupport.runSteps(getStepFactory(), terminalStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  public void whenJobCreated_specHasOneContainer() {
    testSupport.runSteps(getStepFactory(), terminalStep);
    logRecords.clear();

    List<V1Job> jobs = testSupport.getResources(KubernetesTestSupport.JOB);
    assertThat(getPodTemplateContainers(jobs.get(0)), hasSize(1));
  }

  private List<V1Container> getPodTemplateContainers(V1Job v1Job) {
    return v1Job.getSpec().getTemplate().getSpec().getContainers();
  }

  @Test
  public void whenJobCreated_hasPredefinedEnvVariables() {
    testSupport.runSteps(getStepFactory(), terminalStep);
    logRecords.clear();

    List<V1Job> jobs = testSupport.getResources(KubernetesTestSupport.JOB);
    List<V1Container> podTemplateContainers = getPodTemplateContainers(jobs.get(0));
    assertThat(
        podTemplateContainers.get(0).getEnv(),
        allOf(
            hasEnvVar("NAMESPACE", NS),
            hasEnvVar("DOMAIN_UID", UID),
            hasEnvVar("DOMAIN_HOME", getDomainHome()),
            hasEnvVar("NODEMGR_HOME", NODEMGR_HOME),
            hasEnvVar("LOG_HOME", LOG_HOME),
            hasEnvVar("INTROSPECT_HOME", getDomainHome()),
            hasEnvVar("SERVER_OUT_IN_POD_LOG", "true"),
            hasEnvVar("CREDENTIALS_SECRET_NAME", CREDENTIALS_SECRET_NAME)));
  }

  private String getDomainHome() {
    return "/shared/domains/" + UID;
  }
}
