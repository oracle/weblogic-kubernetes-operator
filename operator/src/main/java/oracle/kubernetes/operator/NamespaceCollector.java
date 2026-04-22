// Copyright (c) 2025, 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.prometheus.client.Collector;
import io.prometheus.client.GaugeMetricFamily;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;

public class NamespaceCollector extends Collector {
  private static final String WKO_MANAGED_NAMESPACE_COUNT = "wko_managed_namespace_count";
  private static final String WKO_MANAGED_NAMESPACE_INFO = "wko_managed_namespace_info";
  private static final String WKO_MANAGED_DOMAIN_COUNT = "wko_managed_domain_count";
  private static final String WKO_MANAGED_DOMAIN_INFO = "wko_managed_domain_info";

  private final MainDelegate mainDelegate;

  public NamespaceCollector(MainDelegate mainDelegate) {
    this.mainDelegate = mainDelegate;
  }

  @Override
  public List<MetricFamilySamples> collect() {
    List<MetricFamilySamples> mfs = new ArrayList<>();
    Set<String> namespaces = getActiveNamespaces();
    mfs.add(new GaugeMetricFamily(
        WKO_MANAGED_NAMESPACE_COUNT, "Count of actively managed namespaces", namespaces.size()));

    GaugeMetricFamily managedNamespaces = new GaugeMetricFamily(
        WKO_MANAGED_NAMESPACE_INFO, "Actively managed namespace presence", Collections.singletonList("namespace"));
    mfs.add(managedNamespaces);
    namespaces.forEach(ns -> managedNamespaces.addMetric(Collections.singletonList(ns), 1));

    Map<String, Map<String, DomainPresenceInfo>> dps = mainDelegate.getDomainProcessor().getDomainPresenceInfoMap();
    int domainCount = namespaces.stream()
        .map(dps::get)
        .mapToInt(dp -> Optional.ofNullable(dp).map(Map::size).orElse(0))
        .sum();
    mfs.add(new GaugeMetricFamily(
        WKO_MANAGED_DOMAIN_COUNT, "Count of actively managed WebLogic domains", domainCount));

    GaugeMetricFamily domains = new GaugeMetricFamily(WKO_MANAGED_DOMAIN_INFO,
        "Actively managed WebLogic domain presence", List.of("namespace", "domain_uid"));
    mfs.add(domains);
    for (String ns : namespaces) {
      Map<String, DomainPresenceInfo> dp = dps.get(ns);
      Optional.ofNullable(dp).orElse(Collections.emptyMap())
          .keySet()
          .forEach(domainUid -> domains.addMetric(List.of(ns, domainUid), 1));
    }
    return mfs;
  }

  private Set<String> getActiveNamespaces() {
    return mainDelegate.getDomainNamespaces().getNamespaces().stream()
        .filter(ns -> Optional.ofNullable(mainDelegate.getDomainNamespaces().getStopping(ns))
            .map(stopping -> !stopping.get())
            .orElse(false))
        .collect(Collectors.toSet());
  }
}
