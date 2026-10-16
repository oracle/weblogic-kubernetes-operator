// Copyright (c) 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

/** Repairs the Service cache using a complete list while preserving concurrent cache changes. */
public class ServicePresenceReconciler implements Consumer<V1ServiceList> {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private final Supplier<Collection<DomainPresenceInfo>> presenceInfos;
  private final Function<String, DomainPresenceInfo> lookup;
  private final Map<String, Snapshot> snapshots = new HashMap<>();

  public ServicePresenceReconciler(Supplier<Collection<DomainPresenceInfo>> presenceInfos,
                                  Function<String, DomainPresenceInfo> lookup) {
    this.presenceInfos = presenceInfos;
    this.lookup = lookup;
  }

  /** Capture cached entries before the first list request, rather than between list pages. */
  public void prepare() {
    snapshots.clear();
    presenceInfos.get().forEach(info -> snapshots.put(info.getDomainUid(), new Snapshot(info)));
  }

  @Override
  public void accept(V1ServiceList list) {
    list.getItems().forEach(this::recordService);
  }

  private void recordService(V1Service service) {
    String domainUid = ServiceHelper.getServiceDomainUid(service);
    if (domainUid != null) {
      DomainPresenceInfo info = lookup.apply(domainUid);
      Snapshot snapshot = snapshots.computeIfAbsent(domainUid, key -> new Snapshot(info, Map.of()));
      snapshot.seen.add(service.getMetadata().getName());
      CachedService cached = snapshot.services.get(service.getMetadata().getName());
      if (snapshot.info == info && (cached == null || cached.isUnchanged())) {
        info.compareAndSetService(cached == null ? null : cached.service(), service);
      }
    }
  }

  /** Remove absent entries only after the final successful page. */
  public void complete() {
    snapshots.values().forEach(snapshot -> snapshot.services.forEach((name, cached) -> {
      if (!snapshot.seen.contains(name) && cached.isUnchanged()
          && snapshot.info.compareAndSetService(cached.service(), null)) {
        snapshot.missingServices = true;
        LOGGER.fine("Removed missing Service {0} from the cache for domain {1}", name, snapshot.info.getDomainUid());
      }
    }));
  }

  /** Whether this list removed missing entries from the specified presence. */
  public boolean hasMissingServices(DomainPresenceInfo info) {
    Snapshot snapshot = snapshots.get(info.getDomainUid());
    return snapshot != null && snapshot.info == info && snapshot.missingServices;
  }

  private static class Snapshot {
    private final DomainPresenceInfo info;
    private final Map<String, CachedService> services;
    private final Set<String> seen = new HashSet<>();
    private boolean missingServices;

    Snapshot(DomainPresenceInfo info) {
      this(info, new HashMap<>());
      info.getCachedServices().forEach(service -> services.put(service.getMetadata().getName(),
          new CachedService(service, service.getMetadata().getUid(), service.getMetadata().getResourceVersion())));
    }

    Snapshot(DomainPresenceInfo info, Map<String, CachedService> services) {
      this.info = info;
      this.services = services;
    }
  }

  private record CachedService(V1Service service, String uid, String resourceVersion) {
    boolean isUnchanged() {
      return Objects.equals(uid, service.getMetadata().getUid())
          && Objects.equals(resourceVersion, service.getMetadata().getResourceVersion());
    }
  }
}
