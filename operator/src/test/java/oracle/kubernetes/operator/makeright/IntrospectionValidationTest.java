// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.makeright;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.IntrospectorConfigMapConstants;
import oracle.kubernetes.operator.JobAwaiterStepFactory;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainTopology;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainFailureReason;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.cluster1;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.cluster2;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECTION_COMPLETE;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECTOR_LOG_RESULT;
import static oracle.kubernetes.operator.ProcessingConstants.JOBWATCHER_COMPONENT_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD;
import static oracle.kubernetes.operator.makeright.IntrospectionValidationTest.DomainType.ONE_CLUSTER_REF;
import static oracle.kubernetes.operator.makeright.IntrospectionValidationTest.DomainType.TWO_CLUSTER_REFS;
import static oracle.kubernetes.operator.makeright.IntrospectionValidationTest.TopologyType.ONE_CLUSTER;
import static oracle.kubernetes.operator.makeright.IntrospectionValidationTest.TopologyType.TWO_CLUSTERS;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

class IntrospectionValidationTest {
  private final String jobPodName = LegalNames.toJobIntrospectorName(UID);
  private final DomainResource domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final ClusterResource[] clusters = new ClusterResource[] {cluster1, cluster2};

  @BeforeEach
  void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(TuningParametersStub.install());

    testSupport.addDomainPresenceInfo(info);
    testSupport.addComponent(JOBWATCHER_COMPONENT_NAME, JobAwaiterStepFactory.class, new JobAwaiterStepFactoryStub());
    testSupport.addToPacket(JOB_POD, new V1Pod().metadata(new V1ObjectMeta().name(jobPodName)));
    testSupport.defineResources(domain);
    DomainProcessorTestSetup.setupCluster(domain, clusters);
    testSupport.defineResources(clusters);
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @ParameterizedTest
  @EnumSource(Scenario.class)
  void introspectionRespondsToNewConditions(Scenario scenario) throws JsonProcessingException {
    info.setServerPod("admin", new V1Pod());
    scenario.initializeScenario(info, testSupport);

    testSupport.runSteps(MakeRightDomainOperationImpl.domainIntrospectionSteps());

    final DomainResource result = testSupport.<DomainResource>getResources(KubernetesTestSupport.DOMAIN).get(0);
    if (scenario.finalDomain.isCompatibleWith(scenario.finalTopology)) {
      assertThat(result, not(hasCondition(FAILED)));
    } else {
      assertThat(result, hasCondition(FAILED).withReason(DomainFailureReason.TOPOLOGY_MISMATCH));
    }

  }

  enum TopologyType {
    ONE_CLUSTER,
    TWO_CLUSTERS {
      @Override
      WlsDomainConfigSupport createBuilder() {
        return super.createBuilder().withWlsCluster("cluster-2", "ms3", "ms4");
      }
    };

    String createIntrospectionResult() throws JsonProcessingException {
      final ObjectMapper yamlWriter = new ObjectMapper(new YAMLFactory());
      return ">>>  /u01/introspect/domain1/" + IntrospectorConfigMapConstants.TOPOLOGY_YAML + '\n'
          + yamlWriter.writeValueAsString(new DomainTopology(createBuilder().createDomainConfig()))
          + ">>> EOF" + '\n'
          + DOMAIN_INTROSPECTION_COMPLETE;
    }

    WlsDomainConfigSupport createBuilder() {
      return new WlsDomainConfigSupport("base_domain")
          .withAdminServerName("admin")
          .withWlsServer("admin", 8001)
          .withWlsCluster("cluster-1", "ms1", "ms2");
    }
  }

  enum DomainType {
    ONE_CLUSTER_REF {
      @Override
      boolean isCompatibleWith(TopologyType topologyType) {
        return true;
      }
    },
    TWO_CLUSTER_REFS {
      @Override
      boolean isCompatibleWith(TopologyType topologyType) {
        return topologyType == TWO_CLUSTERS;
      }

      @Override
      DomainConfigurator configureDomain(DomainPresenceInfo info, DomainResource domainResource) {
        final DomainConfigurator domainConfigurator = super.configureDomain(info, domainResource);
        domainConfigurator.configureCluster(info,"cluster-2");
        return domainConfigurator;
      }
    };

    abstract boolean isCompatibleWith(TopologyType topologyType);

    DomainConfigurator configureDomain(DomainPresenceInfo info, DomainResource domainResource) {
      final DomainConfigurator domainConfigurator = DomainConfiguratorFactory.forDomain(domainResource);
      domainConfigurator.configureCluster(info,"cluster-1");
      return domainConfigurator;
    }
  }

  enum Scenario {
    INITIAL_TOPOLOGY_PASS(ONE_CLUSTER_REF, ONE_CLUSTER_REF, null, ONE_CLUSTER),
    INITIAL_TOPOLOGY_FAIL(ONE_CLUSTER_REF, ONE_CLUSTER_REF, null, TWO_CLUSTERS),
    NO_CHANGE_FAIL_AGAIN(TWO_CLUSTER_REFS, TWO_CLUSTER_REFS, ONE_CLUSTER, ONE_CLUSTER),
    NEW_DOMAIN_RECOVER(TWO_CLUSTER_REFS, ONE_CLUSTER_REF, ONE_CLUSTER, ONE_CLUSTER),
    NEW_DOMAIN_FAIL(ONE_CLUSTER_REF, TWO_CLUSTER_REFS, ONE_CLUSTER, ONE_CLUSTER),
    NEW_INTROSPECTION_RECOVER(TWO_CLUSTER_REFS, TWO_CLUSTER_REFS, ONE_CLUSTER, TWO_CLUSTERS),
    NEW_INTROSPECTION_FAIL(TWO_CLUSTER_REFS, TWO_CLUSTER_REFS, TWO_CLUSTERS, ONE_CLUSTER);

    private final DomainType initialDomain;
    private final DomainType finalDomain;
    private final TopologyType initialTopology;
    private final TopologyType finalTopology;

    Scenario(
        DomainType initialDomain, DomainType finalDomain, TopologyType initialTopology, TopologyType finalTopology) {
      this.initialDomain = initialDomain;
      this.finalDomain = finalDomain;
      this.initialTopology = initialTopology;
      this.finalTopology = finalTopology;
    }

    private void initializeScenario(DomainPresenceInfo info, KubernetesTestSupport testSupport)
        throws JsonProcessingException {
      if (initialTopology != null) {
        testSupport.addToPacket(DOMAIN_INTROSPECTOR_LOG_RESULT, initialTopology.createIntrospectionResult());
        testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(null));
      }
      if (initialTopology != finalTopology) {
        getDomain(testSupport).getSpec().setIntrospectVersion("1");
      }
      testSupport.definePodLog(getJobPodName(testSupport), NS, finalTopology.createIntrospectionResult());
      if (!initialDomain.isCompatibleWith(initialTopology)) {
        getDomain(testSupport).getStatus().addCondition(
            new DomainCondition(FAILED)
                .withReason(DomainFailureReason.TOPOLOGY_MISMATCH)
                .withMessage("preset for test"));
      }
      finalDomain.configureDomain(info, getDomain(testSupport));
    }

    private DomainResource getDomain(KubernetesTestSupport testSupport) {
      return DomainPresenceInfo.fromPacket(testSupport.getPacket()).map(DomainPresenceInfo::getDomain).orElseThrow();
    }

    private String getJobPodName(KubernetesTestSupport testSupport) {
      V1Pod jobPod = testSupport.getPacket().getValue(JOB_POD);
      return jobPod.getMetadata().getName();
    }
  }

  private static class JobAwaiterStepFactoryStub implements JobAwaiterStepFactory {
    @Override
    public Step waitForReady(V1Job job, Step next) {
      return next;
    }
  }
}
