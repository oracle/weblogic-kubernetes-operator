// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Optional;

import io.kubernetes.client.openapi.models.V1Service;

/** Describes the service types supported by the operator. */
public enum OperatorServiceType {
  SERVER {
    @Override
    boolean matches(V1Service service) {
      return ServiceHelper.getServerName(service) != null && !ServiceHelper.isNodePortType(service);
    }
  },
  EXTERNAL {
    @Override
    boolean matches(V1Service service) {
      return ServiceHelper.getServerName(service) != null && ServiceHelper.isNodePortType(service);
    }
  },
  CLUSTER {
    @Override
    boolean matches(V1Service service) {
      return ServiceHelper.getClusterName(service) != null;
    }
  },
  UNKNOWN {
    @Override
    boolean matches(V1Service service) {
      return true;
    }
  };

  private static final String SERVICE_TYPE = "serviceType";

  static OperatorServiceType getType(V1Service service) {
    if (!KubernetesUtils.isOperatorCreated(service.getMetadata())) {
      return UNKNOWN;
    }
    String type = ServiceHelper.getLabelValue(service, SERVICE_TYPE);
    if (type != null) {
      return OperatorServiceType.valueOf(type);
    }

    for (OperatorServiceType serviceType : OperatorServiceType.values()) {
      if (serviceType.matches(service)) {
        return serviceType;
      }
    }

    return UNKNOWN;
  }

  boolean matches(V1Service service) {
    return false;
  }

  /**
   * build with type label.
   * @param service service
   * @return service
   */
  public V1Service withTypeLabel(V1Service service) {
    Optional.ofNullable(service)
        .map(V1Service::getMetadata)
        .ifPresent(meta -> meta.putLabelsItem(SERVICE_TYPE, toString()));
    return service;
  }
}
