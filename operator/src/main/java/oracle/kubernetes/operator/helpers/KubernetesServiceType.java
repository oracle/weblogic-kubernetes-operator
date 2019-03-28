// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.models.V1Service;
import java.util.Optional;

public enum KubernetesServiceType {
  SERVER {
    @Override
    void addToPresence(DomainPresenceInfo presenceInfo, V1Service service) {
      presenceInfo.setServerService(ServiceHelper.getServerName(service), service);
    }

    @Override
    void updateFromEvent(DomainPresenceInfo presenceInfo, V1Service event) {
      presenceInfo.setServerServiceFromEvent(ServiceHelper.getServerName(event), event);
    }

    @Override
    boolean matches(V1Service service) {
      return ServiceHelper.getServerName(service) != null;
    }

    @Override
    boolean deleteFromEvent(DomainPresenceInfo info, V1Service event) {
      return info.deleteServerServiceFromEvent(ServiceHelper.getServerName(event), event);
    }

    @Override
    V1Service[] getServices(DomainPresenceInfo presenceInfo) {
      return presenceInfo.getServiceServices();
    }
  },
  EXTERNAL {
    @Override
    void addToPresence(DomainPresenceInfo presenceInfo, V1Service service) {
      presenceInfo.setExternalService(ServiceHelper.getServerName(service), service);
    }

    @Override
    void updateFromEvent(DomainPresenceInfo presenceInfo, V1Service service) {
      presenceInfo.setExternalServiceFromEvent(ServiceHelper.getServerName(service), service);
    }

    @Override
    boolean deleteFromEvent(DomainPresenceInfo info, V1Service event) {
      return info.deleteExternalServiceFromEvent(ServiceHelper.getServerName(event), event);
    }
  },
  CLUSTER {
    @Override
    boolean matches(V1Service service) {
      return ServiceHelper.getClusterName(service) != null;
    }

    @Override
    void addToPresence(DomainPresenceInfo presenceInfo, V1Service service) {
      presenceInfo.setClusterService(ServiceHelper.getClusterName(service), service);
    }

    @Override
    void updateFromEvent(DomainPresenceInfo presenceInfo, V1Service service) {
      presenceInfo.setClusterServiceFromEvent(ServiceHelper.getClusterName(service), service);
    }

    @Override
    boolean deleteFromEvent(DomainPresenceInfo info, V1Service event) {
      return info.deleteClusterServiceFromEvent(ServiceHelper.getClusterName(event), event);
    }
  },
  UNKNOWN {
    @Override
    boolean matches(V1Service service) {
      return true;
    }
  };

  private static final String SERVICE_TYPE = "serviceType";

  static KubernetesServiceType getType(V1Service service) {
    if (!KubernetesUtils.isOperatorCreated(service.getMetadata())) return EXTERNAL;
    String type = ServiceHelper.getLabelValue(service, SERVICE_TYPE);
    if (type != null) return KubernetesServiceType.valueOf(type);

    for (KubernetesServiceType serviceType : KubernetesServiceType.values())
      if (serviceType.matches(service)) return serviceType;

    return UNKNOWN;
  }

  boolean matches(V1Service service) {
    return false;
  }

  void addToPresence(DomainPresenceInfo presenceInfo, V1Service service) {}

  void updateFromEvent(DomainPresenceInfo presenceInfo, V1Service service) {}

  public V1Service withTypeLabel(V1Service service) {
    Optional.ofNullable(service)
        .map(V1Service::getMetadata)
        .ifPresent(meta -> meta.putLabelsItem(SERVICE_TYPE, toString()));
    return service;
  }

  boolean deleteFromEvent(DomainPresenceInfo info, V1Service service) {
    return false;
  }

  V1Service[] getServices(DomainPresenceInfo info) {
    return new V1Service[0];
  }
}
