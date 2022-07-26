// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.CoreV1EventList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudgetList;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.helpers.PodDisruptionBudgetHelper;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.weblogic.domain.model.ClusterList;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainList;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_CREATED;

/**
 * A class to handle coordinating the operator with the actual resources in Kubernetes. This reviews lists
 * of pods and services to detect those which have been stranded by the deletion of a Domain, and ensures
 * that any domains which are found have the proper pods and services.
 */
class DomainResourcesValidation {
  private final String namespace;
  private final DomainProcessor processor;
  private final Map<String, DomainPresenceInfo> domainPresenceInfoMap = new ConcurrentHashMap<>();
  private ClusterList activeClusterResources;

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
        getActiveDomainPresenceInfos().forEach(info -> activateDomain(dp, info));
      }
    };
  }

  private void adjustClusterResources(ClusterList clusters, DomainPresenceInfo info) {
    List<ClusterResource> resources = clusters.getItems().stream()
        .filter(c -> isForDomain(c, info)).collect(Collectors.toList());
    info.adjustClusterResources(resources);
  }

  private boolean isForDomain(ClusterResource clusterResource, DomainPresenceInfo info) {
    return info.doesReferenceCluster(clusterResource.getMetadata().getName());
  }

  private void addPodList(V1PodList list) {
    list.getItems().forEach(this::addPod);
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
    if (domainUid != null && serverName != null) {
      getDomainPresenceInfo(domainUid).setServerPod(serverName, pod);
    }
  }

  private DomainPresenceInfo getDomainPresenceInfo(String domainUid) {
    return domainPresenceInfoMap.computeIfAbsent(domainUid, k -> new DomainPresenceInfo(namespace, domainUid));
  }

  private void addServiceList(V1ServiceList list) {
    list.getItems().forEach(this::addService);
  }

  private void addService(V1Service service) {
    String domainUid = ServiceHelper.getServiceDomainUid(service);
    if (domainUid != null) {
      ServiceHelper.addToPresence(getDomainPresenceInfo(domainUid), service);
    }
  }

  private void addPodDisruptionBudgetList(V1PodDisruptionBudgetList list) {
    list.getItems().forEach(this::addPodDisruptionBudget);
  }

  private void addPodDisruptionBudget(V1PodDisruptionBudget pdb) {
    String domainUid = PodDisruptionBudgetHelper.getDomainUid(pdb);
    if (domainUid != null) {
      PodDisruptionBudgetHelper.addToPresence(getDomainPresenceInfo(domainUid), pdb);
    }
  }

  private void addDomainList(DomainList list) {
    list.getItems().forEach(this::addDomain);
  }

  private void addDomain(DomainResource domain) {
    getDomainPresenceInfo(domain.getDomainUid()).setDomain(domain);
  }

  private void addClusterList(ClusterList list) {
    activeClusterResources = list;
  }

  private Stream<DomainPresenceInfo> getStrandedDomainPresenceInfos(DomainProcessor dp) {
    return Stream.concat(
        domainPresenceInfoMap.values().stream().filter(this::isStranded),
        dp.findStrandedDomainPresenceInfos(namespace, domainPresenceInfoMap.keySet()));
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
    return domainPresenceInfoMap.values().stream().filter(this::isActive);
  }

  private boolean isActive(DomainPresenceInfo dpi) {
    return dpi.getDomain() != null;
  }

  private static void activateDomain(DomainProcessor dp, DomainPresenceInfo info) {
    info.setPopulated(true);
    MakeRightDomainOperation makeRight = dp.createMakeRightOperation(info).withExplicitRecheck();
    if (info.getDomain().getStatus() == null) {
      makeRight = makeRight.withEventData(new EventData(DOMAIN_CREATED)).interrupt();
    }
    makeRight.execute();
  }

}
