// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import static oracle.kubernetes.operator.helm.MapUtils.addMapEntry;
import static oracle.kubernetes.operator.helm.MapUtils.addStringMapEntry;
import static oracle.kubernetes.operator.helm.MapUtils.integerValue;
import static oracle.kubernetes.operator.helm.MapUtils.loadBooleanFromMap;
import static oracle.kubernetes.operator.helm.MapUtils.loadFromMap;
import static oracle.kubernetes.operator.helm.MapUtils.loadIntegerFromMap;

import java.util.HashMap;
import java.util.Map;
import oracle.kubernetes.operator.utils.DomainValues;

class HelmDomainValues extends DomainValues {
  HelmDomainValues() {}

  HelmDomainValues(Map<String, Object> map) {
    loadFromMap(map, this::setAdminServerName, "adminServerName");
    loadFromMap(map, this::setDomainName, "domainName");
    loadFromMap(map, this::setDomainUID, "domainUID");
    loadFromMap(map, this::setClusterName, "clusterName");
    loadFromMap(map, this::setClusterType, "clusterType");
    loadFromMap(map, this::setStartupControl, "startupControl");
    loadFromMap(map, this::setManagedServerNameBase, "managedServerNameBase");
    loadFromMap(map, this::setWeblogicImage, "weblogicImage");
    loadFromMap(map, this::setWeblogicDomainStorageType, "weblogicDomainStorageType");
    loadFromMap(
        map, this::setWeblogicDomainStorageReclaimPolicy, "weblogicDomainStorageReclaimPolicy");
    loadFromMap(map, this::setWeblogicDomainStorageSize, "weblogicDomainStorageSize");
    loadFromMap(map, this::setWeblogicCredentialsSecretName, "weblogicCredentialsSecretName");
    loadFromMap(map, this::setT3PublicAddress, "t3PublicAddress");
    loadFromMap(map, this::setNamespace, "namespace");
    loadFromMap(map, this::setLoadBalancer, "loadBalancer");
    loadFromMap(map, this::setLoadBalancerAppPrepath, "loadBalancerAppPrepath");
    loadFromMap(map, this::setLoadBalancerVolumePath, "loadBalancerVolumePath");
    loadFromMap(map, this::setJavaOptions, "javaOptions");

    loadBooleanFromMap(map, this::setProductionModeEnabled, "productionModeEnabled");
    loadBooleanFromMap(map, this::setExposeAdminT3Channel, "exposeAdminT3Channel");
    loadBooleanFromMap(map, this::setExposeAdminNodePort, "exposeAdminNodePort");
    loadBooleanFromMap(map, this::setLoadBalancerExposeAdminPort, "loadBalancerExposeAdminPort");

    loadIntegerFromMap(map, this::setAdminPort, "adminPort");
    loadIntegerFromMap(map, this::setConfiguredManagedServerCount, "configuredManagedServerCount");
    loadIntegerFromMap(map, this::setInitialManagedServerReplicas, "initialManagedServerReplicas");
    loadIntegerFromMap(map, this::setManagedServerPort, "managedServerPort");
    loadIntegerFromMap(map, this::setT3ChannelPort, "t3ChannelPort");
    loadIntegerFromMap(map, this::setAdminNodePort, "adminNodePort");
    loadIntegerFromMap(map, this::setLoadBalancerWebPort, "loadBalancerWebPort");
    loadIntegerFromMap(map, this::setLoadBalancerDashboardPort, "loadBalancerDashboardPort");
  }

  private void setProductionModeEnabled(Boolean enabled) {
    if (enabled != null) {
      setProductionModeEnabled(enabled.toString());
    }
  }

  private void setExposeAdminT3Channel(Boolean enabled) {
    if (enabled != null) {
      setExposeAdminT3Channel(enabled.toString());
    }
  }

  private void setExposeAdminNodePort(Boolean enabled) {
    if (enabled != null) {
      setExposeAdminNodePort(enabled.toString());
    }
  }

  private void setLoadBalancerExposeAdminPort(Boolean enabled) {
    if (enabled != null) {
      setLoadBalancerExposeAdminPort(enabled.toString());
    }
  }

  Map<String, Object> createMap() {
    HashMap<String, Object> map = new HashMap<>();

    addStringMapEntry(map, this::getAdminServerName, "adminServerName");
    addStringMapEntry(map, this::getDomainName, "domainName");
    addStringMapEntry(map, this::getDomainUID, "domainUID");
    addStringMapEntry(map, this::getClusterName, "clusterName");
    addStringMapEntry(map, this::getClusterType, "clusterType");
    addStringMapEntry(map, this::getStartupControl, "startupControl");
    addStringMapEntry(map, this::getManagedServerNameBase, "managedServerNameBase");
    addStringMapEntry(map, this::getWeblogicImage, "weblogicImage");
    addStringMapEntry(map, this::getWeblogicDomainStorageType, "weblogicDomainStorageType");
    addStringMapEntry(
        map, this::getWeblogicDomainStorageReclaimPolicy, "weblogicDomainStorageReclaimPolicy");
    addStringMapEntry(map, this::getWeblogicDomainStorageSize, "weblogicDomainStorageSize");
    addStringMapEntry(map, this::getWeblogicCredentialsSecretName, "weblogicCredentialsSecretName");
    addStringMapEntry(map, this::getT3PublicAddress, "t3PublicAddress");
    addStringMapEntry(map, this::getNamespace, "namespace");
    addStringMapEntry(map, this::getLoadBalancer, "loadBalancer");
    addStringMapEntry(map, this::getLoadBalancerAppPrepath, "loadBalancerAppPrepath");
    addStringMapEntry(map, this::getLoadBalancerVolumePath, "loadBalancerVolumePath");
    addStringMapEntry(map, this::getJavaOptions, "javaOptions");

    addMapEntry(map, this::isProductionModeEnabled, "productionModeEnabled");
    addMapEntry(map, this::isExposeAdminT3Channel, "exposeAdminT3Channel");
    addMapEntry(map, this::isExposeAdminNodePort, "exposeAdminNodePort");
    addMapEntry(map, this::isLoadBalancerExposeAdminPort, "loadBalancerExposeAdminPort");

    addMapEntry(map, this::getAdminPortNum, "adminPort");
    addMapEntry(map, this::getConfiguredManagedServerCountValue, "configuredManagedServerCount");
    addMapEntry(map, this::getInitialManagedServerReplicasNum, "initialManagedServerReplicas");
    addMapEntry(map, this::getManagedServerPortNum, "managedServerPort");
    addMapEntry(map, this::getT3ChannelPortNum, "t3ChannelPort");
    addMapEntry(map, this::getAdminNodePortNum, "adminNodePort");
    addMapEntry(map, this::getLoadBalancerDashboardPortNum, "loadBalancerDashboardPort");
    addMapEntry(map, this::getLoadBalancerWebPortNum, "loadBalancerWebPort");

    return map;
  }

  private Boolean isProductionModeEnabled() {
    return MapUtils.valueOf(getProductionModeEnabled());
  }

  private Boolean isExposeAdminT3Channel() {
    return MapUtils.valueOf(getExposeAdminT3Channel());
  }

  private Boolean isExposeAdminNodePort() {
    return MapUtils.valueOf(getExposeAdminNodePort());
  }

  private Boolean isLoadBalancerExposeAdminPort() {
    return MapUtils.valueOf(getLoadBalancerExposeAdminPort());
  }

  private Integer getAdminPortNum() {
    return integerValue(getAdminPort());
  }

  private Integer getConfiguredManagedServerCountValue() {
    return integerValue(getConfiguredManagedServerCount());
  }

  private Integer getInitialManagedServerReplicasNum() {
    return integerValue(getInitialManagedServerReplicas());
  }

  private Integer getManagedServerPortNum() {
    return integerValue(getManagedServerPort());
  }

  private Integer getT3ChannelPortNum() {
    return integerValue(getT3ChannelPort());
  }

  private Integer getAdminNodePortNum() {
    return integerValue(getAdminNodePort());
  }

  private Integer getLoadBalancerWebPortNum() {
    return integerValue(getLoadBalancerWebPort());
  }

  private Integer getLoadBalancerDashboardPortNum() {
    return integerValue(getLoadBalancerDashboardPort());
  }
}
