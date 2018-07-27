// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import static oracle.kubernetes.operator.helm.MapUtils.addMapEntry;
import static oracle.kubernetes.operator.helm.MapUtils.addStringMapEntry;
import static oracle.kubernetes.operator.helm.MapUtils.loadBooleanFromMap;
import static oracle.kubernetes.operator.helm.MapUtils.loadFromMap;
import static oracle.kubernetes.operator.helm.MapUtils.loadIntegerFromMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import oracle.kubernetes.operator.utils.OperatorValues;
import org.apache.commons.codec.binary.Base64;

class HelmOperatorValues extends OperatorValues {
  HelmOperatorValues() {}

  HelmOperatorValues(Map<String, Object> map) {
    loadFromMap(map, this::setServiceAccount, "operatorServiceAccount");
    loadFromMap(map, this::setWeblogicOperatorImage, "operatorImage");
    loadFromMap(map, this::setJavaLoggingLevel, "javaLoggingLevel");
    loadFromMap(map, this::setNamespace, "operatorNamespace");
    loadFromMap(map, this::setWeblogicOperatorImagePullPolicy, "operatorImagePullPolicy");
    loadFromMap(map, this::setExternalRestOption, "externalRestOption");

    loadBooleanFromMap(map, this::setRemoteDebugNodePortEnabled, "remoteDebugNodePortEnabled");
    loadBooleanFromMap(map, this::setElkIntegrationEnabled, "elkIntegrationEnabled");

    loadIntegerFromMap(map, this::setExternalRestHttpsPort, "externalRestHttpsPort");
    loadIntegerFromMap(map, this::setExternalDebugHttpPort, "externalDebugHttpPort");
    loadIntegerFromMap(map, this::setInternalDebugHttpPort, "internalDebugHttpPort");

    loadDomainsNamespacesFromMap(map);
  }

  private void setRemoteDebugNodePortEnabled(Boolean enabled) {
    if (enabled != null) {
      setRemoteDebugNodePortEnabled(enabled.toString());
    }
  }

  private void setElkIntegrationEnabled(Boolean enabled) {
    if (enabled != null) {
      setElkIntegrationEnabled(enabled.toString());
    }
  }

  @SuppressWarnings("unchecked")
  private void loadDomainsNamespacesFromMap(Map<String, Object> map) {
    Map<String, ?> domainsNamespaces = (Map<String, ?>) map.get("domainsNamespaces");
    if (domainsNamespaces != null) {
      String[] namespaces = domainsNamespaces.keySet().toArray(new String[0]);
      Arrays.sort(namespaces);
      setTargetNamespaces(String.join(",", namespaces));
    }
  }

  Map<String, Object> createMap() {
    HashMap<String, Object> map = new HashMap<>();
    map.put(
        "internalOperatorCert",
        Base64.encodeBase64String(internalOperatorSelfSignedCertPem().getBytes()));
    map.put(
        "internalOperatorKey",
        Base64.encodeBase64String(internalOperatorSelfSignedKeyPem().getBytes()));
    map.put("externalOperatorCert", externalOperatorSelfSignedCertPem());
    map.put("externalOperatorKey", externalOperatorSelfSignedKeyPem());
    addStringMapEntry(map, this::getServiceAccount, "operatorServiceAccount");
    addStringMapEntry(map, this::getWeblogicOperatorImage, "operatorImage");
    addStringMapEntry(map, this::getJavaLoggingLevel, "javaLoggingLevel");
    addStringMapEntry(map, this::getNamespace, "operatorNamespace");
    addStringMapEntry(map, this::getWeblogicOperatorImagePullPolicy, "operatorImagePullPolicy");
    addStringMapEntry(map, this::getExternalRestOption, "externalRestOption");

    addMapEntry(map, this::isRemoteDebugNotPortEnabled, "remoteDebugNodePortEnabled");
    addMapEntry(map, this::isElkIntegrationEnabled, "elkIntegrationEnabled");

    addMapEntry(map, this::getExternalRestHttpsPortNum, "externalRestHttpsPort");
    addMapEntry(map, this::getExternalDebugHttpPortNum, "externalDebugHttpPort");
    addMapEntry(map, this::getInternalDebugHttpPortNum, "internalDebugHttpPort");

    addDomainsNamespaces(map);
    return map;
  }

  private void addDomainsNamespaces(HashMap<String, Object> map) {
    String targetNamespaces = getTargetNamespaces();
    if (targetNamespaces.length() > 0) {
      Map<String, Map> namespaceEntries = new HashMap<>();
      for (String namespace : targetNamespaces.split(",")) {
        namespaceEntries.put(namespace, new HashMap());
      }
      map.put("domainsNamespaces", namespaceEntries);
    }
  }

  private Boolean isRemoteDebugNotPortEnabled() {
    return MapUtils.valueOf(getRemoteDebugNodePortEnabled());
  }

  private Boolean isElkIntegrationEnabled() {
    return MapUtils.valueOf(getElkIntegrationEnabled());
  }

  private Integer getExternalRestHttpsPortNum() {
    return MapUtils.integerValue(getExternalRestHttpsPort());
  }

  private Integer getExternalDebugHttpPortNum() {
    return MapUtils.integerValue(getExternalDebugHttpPort());
  }

  private Integer getInternalDebugHttpPortNum() {
    return MapUtils.integerValue(getInternalDebugHttpPort());
  }
}
