// Copyright (c) 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.prometheus.client.Collector;
import io.prometheus.client.GaugeMetricFamily;

public class NamespaceCollector extends Collector {
  private static final String WKO_NAMESPACE_COUNT = "wko_namespace_count";
  private static final String WKO_DOMAIN_COUNT = "wko_domain_count";

  private final MainDelegate mainDelegate;

  public NamespaceCollector(MainDelegate mainDelegate) {
    this.mainDelegate = mainDelegate;
  }

  @Override
  public List<MetricFamilySamples> collect() {
    List<MetricFamilySamples> mfs = new ArrayList<>();
    Set<String> namespaces = mainDelegate.getDomainNamespaces().getNamespaces();
    mfs.add(new GaugeMetricFamily(WKO_NAMESPACE_COUNT, "Count of managed namespaces", namespaces.size()));
    GaugeMetricFamily domains = new GaugeMetricFamily(
        WKO_DOMAIN_COUNT, "Count of WebLogic domains", Collections.singletonList("namespace"));
    mfs.add(domains);
    for (String ns : namespaces) {
      int size = Optional.ofNullable(mainDelegate.getResourceCache().findNamespace(ns).getDomainResources())
          .map(Map::size).orElse(0);
      domains.addMetric(Collections.singletonList(ns), size);
    }
    return mfs;
  }
}
