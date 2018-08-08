package oracle.kubernetes.operator.helpers;

import oracle.kubernetes.job.CreateDomainTest;
import oracle.kubernetes.operator.ConfigMapWatcherTest;
import oracle.kubernetes.operator.DomainNormalizationTest;
import oracle.kubernetes.operator.DomainPresenceTest;
import oracle.kubernetes.operator.DomainWatcherTest;
import oracle.kubernetes.operator.HealthCheckHelperTest;
import oracle.kubernetes.operator.IngressWatcherTest;
import oracle.kubernetes.operator.PodWatcherTest;
import oracle.kubernetes.operator.SecretHelperTest;
import oracle.kubernetes.operator.ServiceWatcherTest;
import oracle.kubernetes.operator.builders.WatchBuilderTest;
import oracle.kubernetes.operator.calls.AsyncRequestStepTest;
import oracle.kubernetes.operator.http.HttpClientTest;
import oracle.kubernetes.operator.steps.DeleteIngressListStepTest;
import oracle.kubernetes.operator.work.InMemoryDatabaseTest;
import oracle.kubernetes.operator.work.StepChainTest;
import oracle.kubernetes.operator.work.StepTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({CreateDomainTest.class, AsyncRequestStepTest.class, ConfigMapWatcherTest.class,
    ServiceWatcherTest.class, WatchBuilderTest.class, SecretHelperTest.class, PodWatcherTest.class,
    DeleteIngressListStepTest.class, IngressWatcherTest.class, HttpClientTest.class, StepChainTest.class,
    StepTest.class, InMemoryDatabaseTest.class, HealthCheckHelperTest.class, DomainNormalizationTest.class,
    DomainWatcherTest.class, ServiceHelperTest.class, PodHelperConfigTest.class, ServerKubernetesObjectsLookupTest.class,
    DomainConfigBuilderV1Dot1Test.class, IngressHelperTest.class, LifeCycleHelperTest.class, ConfigMapHelperTest.class,
    PodHelperTest.class, AdminPodHelperTest.class, LegalNamesTest.class, CallBuilderTest.class,
    DomainConfigBuilderV1Test.class, DomainConfigBuilderTest.class, FileGroupReaderTest.class, VersionHelperTest.class,
    
    ManagedPodHelperTest.class, DomainPresenceTest.class})
public class ManagedSuite {
}
