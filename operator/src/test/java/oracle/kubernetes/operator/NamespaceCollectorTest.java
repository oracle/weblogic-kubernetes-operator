// Copyright (c) 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.meterware.simplestub.Stub;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import io.prometheus.client.Collector.MetricFamilySamples;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.SemanticVersion;
import oracle.kubernetes.operator.work.Cancellable;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class NamespaceCollectorTest {

  @Test
  void reportsOnlyActivelyManagedNamespacesAndDomains() {
    DomainNamespaces domainNamespaces = new DomainNamespaces(SemanticVersion.TEST_VERSION);
    domainNamespaces.isStopping("ns-a");
    domainNamespaces.isStopping("ns-b");
    domainNamespaces.isStopping("ns-stopping").set(true);

    DomainProcessorStub domainProcessor = createStrictStub(DomainProcessorStub.class);
    domainProcessor.setDomains(Map.of(
        "ns-a", Map.of("domain1", new DomainPresenceInfo(new DomainResource())),
        "ns-b", Map.of(
            "domain2", new DomainPresenceInfo(new DomainResource()),
            "domain3", new DomainPresenceInfo(new DomainResource())),
        "ns-stopping", Map.of("domain4", new DomainPresenceInfo(new DomainResource())),
        "ns-unmanaged", Map.of("domain5", new DomainPresenceInfo(new DomainResource()))));

    NamespaceCollector collector = new NamespaceCollector(
        createStrictStub(MainDelegateStub.class, domainProcessor, domainNamespaces));

    List<MetricFamilySamples> samples = collector.collect();

    assertThat(samples, hasSize(4));
    assertThat(getSampleValue(samples, "wko_managed_namespace_count"), is(2.0));
    assertThat(getSampleValue(samples, "wko_managed_domain_count"), is(3.0));
    assertThat(getSamplesByName(samples, "wko_managed_namespace_info").keySet(),
        containsInAnyOrder("namespace=ns-a", "namespace=ns-b"));
    assertThat(getSamplesByName(samples, "wko_managed_domain_info").keySet(),
        containsInAnyOrder(
            "domain_uid=domain1,namespace=ns-a",
            "domain_uid=domain2,namespace=ns-b",
            "domain_uid=domain3,namespace=ns-b"));
  }

  private double getSampleValue(List<MetricFamilySamples> samples, String metricName) {
    MetricFamilySamples family = samples.stream()
        .filter(mfs -> metricName.equals(mfs.name))
        .findFirst()
        .orElseThrow();
    assertThat(family.samples, hasSize(1));
    return family.samples.get(0).value;
  }

  private Map<String, Double> getSamplesByName(List<MetricFamilySamples> samples, String metricName) {
    MetricFamilySamples family = samples.stream()
        .filter(mfs -> metricName.equals(mfs.name))
        .findFirst()
        .orElseThrow();

    Map<String, Double> values = new TreeMap<>();
    family.samples.forEach(sample -> {
      assertThat(sample, notNullValue());
      values.put(formatLabels(sample.labelNames, sample.labelValues), sample.value);
      assertThat(sample.value, equalTo(1.0));
    });
    return values;
  }

  private String formatLabels(List<String> labelNames, List<String> labelValues) {
    Map<String, String> labels = new TreeMap<>();
    for (int i = 0; i < labelNames.size(); i++) {
      labels.put(labelNames.get(i), labelValues.get(i));
    }
    return labels.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).reduce((a, b) -> a + "," + b)
        .orElse("");
  }

  abstract static class DomainProcessorStub implements DomainProcessor {
    private Map<String, Map<String, DomainPresenceInfo>> domains = Map.of();

    void setDomains(Map<String, Map<String, DomainPresenceInfo>> domains) {
      this.domains = domains;
    }

    @Override
    public Map<String, Map<String, DomainPresenceInfo>> getDomainPresenceInfoMap() {
      return domains;
    }
  }

  abstract static class MainDelegateStub implements MainDelegate {
    private final DomainProcessor domainProcessor;
    private final DomainNamespaces domainNamespaces;

    MainDelegateStub(DomainProcessor domainProcessor, DomainNamespaces domainNamespaces) {
      this.domainProcessor = domainProcessor;
      this.domainNamespaces = domainNamespaces;
    }

    @Override
    public DomainProcessor getDomainProcessor() {
      return domainProcessor;
    }

    @Override
    public DomainNamespaces getDomainNamespaces() {
      return domainNamespaces;
    }

    @Override
    public String getPrincipal() {
      return "principal";
    }

    @Override
    public AtomicReference<V1CustomResourceDefinition> getCrdReference() {
      return new AtomicReference<>();
    }

    @Override
    public SemanticVersion getProductVersion() {
      return SemanticVersion.TEST_VERSION;
    }

    @Override
    public KubernetesVersion getKubernetesVersion() {
      return Stub.createNiceStub(KubernetesVersion.class);
    }

    @Override
    public String getDomainCrdResourceVersion() {
      return null;
    }

    @Override
    public void setDomainCrdResourceVersion(String resourceVersion) {
    }

    @Override
    public String getClusterCrdResourceVersion() {
      return null;
    }

    @Override
    public void setClusterCrdResourceVersion(String resourceVersion) {
    }

    @Override
    public File getDeploymentHome() {
      return new File(".");
    }

    @Override
    public void runStepsInternal(Packet packet, Step firstStep, Runnable completionAction) {
    }

    @Override
    public Cancellable schedule(Runnable command, long delay, TimeUnit unit) {
      return null;
    }

    @Override
    public Cancellable scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
      return null;
    }
  }
}
