// Copyright (c) 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import oracle.kubernetes.operator.LabelConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class ServicePresenceReconcilerTest {
  private static final String DOMAIN_UID = "domain1";
  private static final String SERVER = "admin";
  private static final String CLUSTER = "cluster1";
  private final DomainPresenceInfo info = new DomainPresenceInfo("namespace", DOMAIN_UID);
  private final Map<String, DomainPresenceInfo> infos = new HashMap<>(Map.of(DOMAIN_UID, info));
  private final ServicePresenceReconciler reconciler =
      new ServicePresenceReconciler(infos::values, infos::get);

  @ParameterizedTest
  @EnumSource(value = OperatorServiceType.class, names = {"SERVER", "EXTERNAL", "CLUSTER"})
  void whenServiceMissingFromCompletedList_removeCachedEntry(OperatorServiceType type) {
    info.compareAndSetService(null, service(type, "original", "1"));
    reconciler.prepare();

    reconciler.accept(list());
    reconciler.complete();

    assertThat(cached(info, type), nullValue());
  }

  @ParameterizedTest
  @EnumSource(value = OperatorServiceType.class, names = {"SERVER", "EXTERNAL", "CLUSTER"})
  void whenServiceFoundOnLaterPage_preserveUntilListCompletes(OperatorServiceType type) {
    V1Service existing = service(type, "original", "1");
    info.compareAndSetService(null, existing);
    reconciler.prepare();
    reconciler.accept(list());
    assertThat(cached(info, type), sameInstance(existing));
    V1Service listed = service(type, "original", "2");

    reconciler.accept(list(listed));
    reconciler.complete();

    assertThat(cached(info, type), sameInstance(listed));
  }

  @ParameterizedTest
  @EnumSource(value = OperatorServiceType.class, names = {"SERVER", "EXTERNAL", "CLUSTER"})
  void whenMissingServiceRecreatedDuringList_preserveReplacement(OperatorServiceType type) {
    V1Service original = service(type, "original", "1");
    V1Service replacement = service(type, "replacement", "2");
    info.compareAndSetService(null, original);
    reconciler.prepare();
    info.compareAndSetService(original, replacement);

    reconciler.accept(list());
    reconciler.complete();

    assertThat(cached(info, type), sameInstance(replacement));
  }

  @ParameterizedTest
  @EnumSource(value = OperatorServiceType.class, names = {"SERVER", "EXTERNAL", "CLUSTER"})
  void whenServiceUpdatedDuringList_doNotOverwriteWithOlderListResult(OperatorServiceType type) {
    V1Service original = service(type, "original", "1");
    V1Service updated = service(type, "original", "3");
    info.compareAndSetService(null, original);
    reconciler.prepare();
    info.compareAndSetService(original, updated);

    reconciler.accept(list(service(type, "original", "2")));
    reconciler.complete();

    assertThat(cached(info, type), sameInstance(updated));
  }

  @ParameterizedTest
  @EnumSource(value = OperatorServiceType.class, names = {"SERVER", "EXTERNAL", "CLUSTER"})
  void whenServiceDeletedDuringList_doNotRestoreOldListResult(OperatorServiceType type) {
    V1Service original = service(type, "original", "1");
    info.compareAndSetService(null, original);
    reconciler.prepare();
    info.compareAndSetService(original, null);

    reconciler.accept(list(service(type, "original", "1")));
    reconciler.complete();

    assertThat(cached(info, type), nullValue());
  }

  @Test
  void whenServiceCreatedAfterListStarts_preserveNewEntry() {
    reconciler.prepare();
    V1Service created = service(OperatorServiceType.SERVER, "created", "2");
    info.compareAndSetService(null, created);

    reconciler.accept(list());
    reconciler.complete();

    assertThat(info.getServerService(SERVER), sameInstance(created));
  }

  @Test
  void whenCachedObjectModifiedInPlaceDuringList_preserveIt() {
    V1Service original = service(OperatorServiceType.SERVER, "original", "1");
    info.compareAndSetService(null, original);
    reconciler.prepare();
    original.getMetadata().setResourceVersion("2");

    reconciler.accept(list());
    reconciler.complete();

    assertThat(info.getServerService(SERVER), sameInstance(original));
  }

  @Test
  void whenDomainPresenceReplacedDuringList_preserveNewPresence() {
    info.compareAndSetService(null, service(OperatorServiceType.SERVER, "original", "1"));
    reconciler.prepare();
    DomainPresenceInfo replacement = new DomainPresenceInfo("namespace", DOMAIN_UID);
    V1Service newer = service(OperatorServiceType.SERVER, "replacement", "2");
    replacement.compareAndSetService(null, newer);
    infos.put(DOMAIN_UID, replacement);

    reconciler.accept(list(service(OperatorServiceType.SERVER, "original", "1")));
    reconciler.complete();

    assertThat(replacement.getServerService(SERVER), sameInstance(newer));
  }

  private V1ServiceList list(V1Service... services) {
    return new V1ServiceList().items(List.of(services));
  }

  private V1Service service(OperatorServiceType type, String uid, String version) {
    V1ObjectMeta metadata = new V1ObjectMeta().name("service-" + type).namespace("namespace")
        .uid(uid).resourceVersion(version)
        .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true")
        .putLabelsItem(LabelConstants.DOMAINUID_LABEL, DOMAIN_UID);
    if (type == OperatorServiceType.CLUSTER) {
      metadata.putLabelsItem(LabelConstants.CLUSTERNAME_LABEL, CLUSTER);
    } else {
      metadata.putLabelsItem(LabelConstants.SERVERNAME_LABEL, SERVER);
    }
    return type.withTypeLabel(new V1Service().metadata(metadata));
  }

  private V1Service cached(DomainPresenceInfo presence, OperatorServiceType type) {
    return switch (type) {
      case SERVER -> presence.getServerService(SERVER);
      case EXTERNAL -> presence.getExternalService(SERVER);
      case CLUSTER -> presence.getClusterService(CLUSTER);
      default -> throw new IllegalArgumentException(type.name());
    };
  }
}
