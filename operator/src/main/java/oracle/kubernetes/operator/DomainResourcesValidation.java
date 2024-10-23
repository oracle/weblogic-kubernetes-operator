// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.CoreV1EventList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudgetList;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import oracle.kubernetes.operator.helpers.ClusterPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.helpers.EventHelper.EventItem;
import oracle.kubernetes.operator.helpers.PodDisruptionBudgetHelper;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.work.FiberGate;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.weblogic.domain.model.ClusterList;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainList;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.jetbrains.annotations.NotNull;

import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.CLUSTER_CHANGED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.CLUSTER_CREATED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.CLUSTER_DELETED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_CHANGED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_CREATED;

/**
 * A class to handle coordinating the operator with the actual resources in Kubernetes. This reviews lists
 * of pods and services to detect those which have been stranded by the deletion of a Domain, and ensures
 * that any domains which are found have the proper pods and services.
 */
class DomainResourcesValidation {
  private final String namespace;
  private final DomainProcessor processor;
  private ClusterList activeClusterResources;
  private final Set<String> modifiedClusterNames = new HashSet<>();
  private final Set<String> newClusterNames = new HashSet<>();
  private final Set<String> modifiedDomainNames = new HashSet<>();
  private final Set<String> newDomainNames = new HashSet<>();

  DomainResourcesValidation(String namespace, DomainProcessor processor) {
    this.namespace = namespace;
    this.processor = processor;
  }

  Processors getProcessors() {
    return new Processors() {
      @Override
      public Consumer<V1PodList> getPodListProcessing() {
        return DomainResourcesValidation.this::addPodList;
      }

      @Override
      public Consumer<V1ServiceList> getServiceListProcessing() {
        return DomainResourcesValidation.this::addServiceList;
      }

      @Override
      public Consumer<CoreV1EventList> getOperatorEventListProcessing() {
        return DomainResourcesValidation.this::addOperatorEventList;
      }

      @Override
      public Consumer<V1PodDisruptionBudgetList> getPodDisruptionBudgetListProcessing() {
        return DomainResourcesValidation.this::addPodDisruptionBudgetList;
      }

      @Override
      public Consumer<DomainList> getDomainListProcessing() {
        return DomainResourcesValidation.this::addDomainList;
      }

      @Override
      public Consumer<ClusterList> getClusterListProcessing() {
        return DomainResourcesValidation.this::addClusterList;
      }

      @Override
      public void completeProcessing(Packet packet) {
        DomainProcessor dp = Optional.ofNullable(packet.getSpi(DomainProcessor.class)).orElse(processor);
        getStrandedDomainPresenceInfos(dp).forEach(info -> removeStrandedDomainPresenceInfo(dp, info));
        Optional.ofNullable(activeClusterResources).ifPresent(c -> getActiveDomainPresenceInfos()
            .forEach(info -> adjustClusterResources(c, info)));
        executeMakeRightForClusterEvents(dp);
        getActiveDomainPresenceInfos().forEach(info -> activateDomain(dp, info));
        getDomainPresenceInfoMap().values().forEach(DomainResourcesValidation.this::removeDeletedPodsFromDPI);
        getDomainPresenceInfoMap().values().forEach(DomainPresenceInfo::clearServerPodNamesFromList);
      }
    };
  }

  private void executeMakeRightForClusterEvents(DomainProcessor dp) {
    List<String> clusterNamesFromList =
        getActiveClusterResources().stream().map(ClusterResource::getMetadata).map(V1ObjectMeta::getName)
        .toList();
    getClusterPresenceInfoMap().values().stream()
        .filter(cpi -> !clusterNamesFromList.contains(cpi.getResourceName())).toList()
        .forEach(info -> updateCluster(dp, info.getCluster(), CLUSTER_DELETED));
    getActiveClusterResources().forEach(cluster -> updateCluster(dp, cluster, getEventItem(cluster)));
  }

  @NotNull
  private List<ClusterResource> getActiveClusterResources() {
    return Optional.ofNullable(activeClusterResources).map(ClusterList::getItems).orElse(new ArrayList<>());
  }

  private void adjustClusterResources(ClusterList clusters, DomainPresenceInfo info) {
    List<ClusterResource> resources = clusters.getItems().stream()
        .filter(c -> isForDomain(c, info)).toList();
    info.adjustClusterResources(resources);
  }

  private boolean isForDomain(ClusterResource clusterResource, DomainPresenceInfo info) {
    return info.doesReferenceCluster(clusterResource.getMetadata().getName());
  }

  private void addPodList(V1PodList list) {
    list.getItems().forEach(this::addPod);
  }

  private void removeDeletedPodsFromDPI(DomainPresenceInfo dpi) {
    dpi.getServerNames().stream().filter(s -> !dpi.getServerNamesFromPodList().contains(s)).toList()
        .forEach(name -> dpi.deleteServerPodFromEvent(name, null));
  }

  private void addEvent(CoreV1Event event) {
    DomainProcessorImpl.updateEventK8SObjects(event);
  }

  private void addOperatorEventList(CoreV1EventList list) {
    list.getItems().forEach(this::addEvent);
  }

  private void addPod(V1Pod pod) {
    String domainUid = PodHelper.getPodDomainUid(pod);
    String serverName = PodHelper.getPodServerName(pod);
    DomainPresenceInfo info = getOrComputeDomainPresenceInfo(domainUid);
    Optional.ofNullable(info).ifPresent(i -> i.addServerNameFromPodList(serverName));

    if (domainUid != null && serverName != null) {
      setServerPodFromEvent(info, serverName, pod);
    }
    if (PodHelper.getPodLabel(pod, LabelConstants.JOBNAME_LABEL) != null) {
      processor.updateDomainStatus(pod, info);
    }
  }

  private void setServerPodFromEvent(DomainPresenceInfo info, String serverName, V1Pod pod) {
    Optional.ofNullable(info).ifPresent(i -> i.setServerPodFromEvent(serverName, pod));
  }

  private DomainPresenceInfo getOrComputeDomainPresenceInfo(String domainUid) {
    return getDomainPresenceInfoMap().computeIfAbsent(domainUid, k -> new DomainPresenceInfo(namespace, domainUid));
  }

  private Map<String, DomainPresenceInfo> getDomainPresenceInfoMap() {
    return processor.getDomainPresenceInfoMap().computeIfAbsent(namespace, k -> new ConcurrentHashMap<>());
  }

  private Map<String, ClusterPresenceInfo> getClusterPresenceInfoMap() {
    return processor.getClusterPresenceInfoMap().computeIfAbsent(namespace, k -> new ConcurrentHashMap<>());
  }

  private void addServiceList(V1ServiceList list) {
    list.getItems().forEach(this::addService);
  }

  private void addService(V1Service service) {
    String domainUid = ServiceHelper.getServiceDomainUid(service);
    if (domainUid != null) {
      ServiceHelper.addToPresence(getOrComputeDomainPresenceInfo(domainUid), service);
    }
  }

  private void addPodDisruptionBudgetList(V1PodDisruptionBudgetList list) {
    list.getItems().forEach(this::addPodDisruptionBudget);
  }

  private void addPodDisruptionBudget(V1PodDisruptionBudget pdb) {
    String domainUid = PodDisruptionBudgetHelper.getDomainUid(pdb);
    if (domainUid != null) {
      PodDisruptionBudgetHelper.addToPresence(getOrComputeDomainPresenceInfo(domainUid), pdb);
    }
  }

  private void addDomainList(DomainList list) {
    getDomainPresenceInfoMap().values().forEach(dpi -> updateDeletedDomainsInDPI(list));
    list.getItems().forEach(this::addDomain);
  }

  private void updateDeletedDomainsInDPI(DomainList list) {
    Collection<String> domainNamesFromList = list.getItems().stream()
        .map(DomainResource::getDomainUid).toList();

    getDomainPresenceInfoMap().values().stream()
        .filter(dpi -> !domainNamesFromList.contains(dpi.getDomainUid()))
        .filter(dpi -> isNotBeingProcessed(dpi.getNamespace(), dpi.getDomainUid()))
        .toList()
        .forEach(i -> i.setDomain(null));
  }

  private boolean isNotBeingProcessed(String namespace, String domainUid) {
    return Optional.ofNullable(processor)
            .map(DomainProcessor::getMakeRightFiberGateMap)
            .map(m -> m.get(namespace))
            .map(FiberGate::getCurrentFibers)
            .map(f -> f.get(domainUid))
            .isEmpty();
  }

  private void addDomain(DomainResource domain) {
    DomainPresenceInfo cachedInfo = getDomainPresenceInfoMap().get(domain.getDomainUid());
    if (domain.getStatus() == null) {
      newDomainNames.add(domain.getDomainUid());
    } else if (cachedInfo != null && cachedInfo.getDomain() != null
        && domain.isGenerationChanged(cachedInfo.getDomain())) {
      modifiedDomainNames.add(domain.getDomainUid());
    }
    getOrComputeDomainPresenceInfo(domain.getDomainUid()).setDomain(domain);
  }

  private void addClusterList(ClusterList list) {
    activeClusterResources = list;
    list.getItems().forEach(this::addCluster);
  }

  private void addCluster(ClusterResource cluster) {
    ClusterPresenceInfo cachedInfo = getClusterPresenceInfoMap().get(getClusterName(cluster));
    if (cluster.getStatus() == null) {
      newClusterNames.add(getClusterName(cluster));
    } else if (cachedInfo != null && cluster.isGenerationChanged(cachedInfo.getCluster())) {
      modifiedClusterNames.add(getClusterName(cluster));
    }

    getClusterPresenceInfoMap().put(getClusterName(cluster), new ClusterPresenceInfo(cluster));
  }

  private Stream<DomainPresenceInfo> getStrandedDomainPresenceInfos(DomainProcessor dp) {
    return Stream.concat(
        getDomainPresenceInfoMap().values().stream().filter(this::isStranded),
        findStrandedDomainPresenceInfos(dp, namespace, getDomainPresenceInfoMap().keySet()));
  }

  private Stream<DomainPresenceInfo> findStrandedDomainPresenceInfos(
      DomainProcessor dp, String namespace, Set<String> domainUids) {
    return Optional.ofNullable(dp.getDomainPresenceInfoMapForNS(namespace)).orElse(Collections.emptyMap())
        .entrySet().stream().filter(e -> !domainUids.contains(e.getKey())).map(Map.Entry::getValue);
  }

  private boolean isStranded(DomainPresenceInfo dpi) {
    return dpi.getDomain() == null;
  }

  private static void removeStrandedDomainPresenceInfo(DomainProcessor dp, DomainPresenceInfo info) {
    info.setDeleting(true);
    info.setPopulated(true);
    dp.createMakeRightOperation(info).withExplicitRecheck().forDeletion().execute();
  }

  private Stream<DomainPresenceInfo> getActiveDomainPresenceInfos() {
    return getDomainPresenceInfoMap().values().stream().filter(this::isActive);
  }

  private boolean isActive(DomainPresenceInfo dpi) {
    return dpi.getDomain() != null;
  }

  private void activateDomain(DomainProcessor dp, DomainPresenceInfo info) {
    info.setPopulated(true);
    EventItem eventItem = getEventItem(info);
    MakeRightDomainOperation makeRight = dp.createMakeRightOperation(info).withExplicitRecheck();
    if (eventItem != null) {
      makeRight.withEventData(new EventData(eventItem)).interrupt().execute();
    } else if (!info.hasRetryableFailure()) {
      makeRight.execute();
    }
  }

  private EventItem getEventItem(DomainPresenceInfo info) {
    if (newDomainNames.contains(info.getDomainUid())) {
      return DOMAIN_CREATED;
    }
    if (modifiedDomainNames.contains(info.getDomainUid())) {
      return DOMAIN_CHANGED;
    }
    return null;
  }

  private EventItem getEventItem(ClusterResource cluster) {
    if (newClusterNames.contains(getClusterName(cluster))) {
      return CLUSTER_CREATED;
    }
    if (modifiedClusterNames.contains(getClusterName(cluster))) {
      return CLUSTER_CHANGED;
    }
    return null;
  }

  private String getClusterName(ClusterResource cluster) {
    return cluster.getMetadata().getName();
  }

  private void updateCluster(DomainProcessor dp, ClusterResource cluster, EventItem eventItem) {
    List<DomainPresenceInfo> list =
        dp.getExistingDomainPresenceInfoForCluster(cluster.getNamespace(), getClusterName(cluster));
    if (list.isEmpty()) {
      createAndExecuteMakeRightOperation(dp, cluster, eventItem, null);
    } else {
      for (DomainPresenceInfo info : list) {
        createAndExecuteMakeRightOperation(dp, cluster, eventItem, info.getDomainUid());
      }
    }
  }

  private void createAndExecuteMakeRightOperation(
      DomainProcessor dp, ClusterResource cluster, EventItem eventItem, String domainUid) {
    MakeRightClusterOperation makeRight = dp.createMakeRightOperationForClusterEvent(
        eventItem, cluster, domainUid).withExplicitRecheck();
    if (eventItem != null) {
      makeRight.interrupt();
    }
    makeRight.execute();
  }
}
