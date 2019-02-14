// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static oracle.kubernetes.operator.helm.MapUtils.addMapEntry;
import static oracle.kubernetes.operator.helm.MapUtils.addStringMapEntry;
import static oracle.kubernetes.operator.helm.MapUtils.loadBooleanFromMap;
import static oracle.kubernetes.operator.helm.MapUtils.loadFromMap;
import static oracle.kubernetes.operator.helm.MapUtils.loadIntegerFromMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.operator.utils.OperatorValues;

class HelmOperatorValues extends OperatorValues {
  HelmOperatorValues() {}

  HelmOperatorValues(Map<String, Object> map) {
    loadFromMap(map, this::setServiceAccount, "serviceAccount");
    loadFromMap(map, this::setWeblogicOperatorImage, "image");
    loadFromMap(map, this::setJavaLoggingLevel, "javaLoggingLevel");
    loadFromMap(map, this::setNamespace, "operatorNamespace");
    loadFromMap(map, this::setWeblogicOperatorImagePullPolicy, "imagePullPolicy");
    loadFromMap(map, this::setExternalOperatorSecret, "externalOperatorSecret");
    loadFromMap(map, this::setExternalOperatorCert, "externalOperatorCert");
    loadFromMap(map, this::setExternalOperatorKey, "externalOperatorKey");
    loadFromMap(map, this::setLogStashImage, "logStashImage");
    loadFromMap(map, this::setElasticSearchHost, "elasticSearchHost");

    loadBooleanFromMap(map, this::setExternalRestEnabled, "externalRestEnabled");
    loadBooleanFromMap(map, this::setRemoteDebugNodePortEnabled, "remoteDebugNodePortEnabled");
    loadBooleanFromMap(map, this::setElkIntegrationEnabled, "elkIntegrationEnabled");

    loadIntegerFromMap(map, this::setExternalRestHttpsPort, "externalRestHttpsPort");
    loadIntegerFromMap(map, this::setExternalDebugHttpPort, "externalDebugHttpPort");
    loadIntegerFromMap(map, this::setInternalDebugHttpPort, "internalDebugHttpPort");
    loadIntegerFromMap(map, this::setElasticSearchPort, "elasticSearchPort");

    loadDomainNamespacesFromMap(map);
    loadImagePullSecretsFromMap(map);
  }

  private void setExternalRestEnabled(Boolean enabled) {
    if (enabled != null) {
      setExternalRestEnabled(enabled.toString());
    }
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
  private void loadDomainNamespacesFromMap(Map<String, Object> map) {
    List<String> domainNamespaces = (List<String>) map.get("domainNamespaces");
    if (domainNamespaces != null) {
      String[] namespaces = domainNamespaces.toArray(new String[0]);
      Arrays.sort(namespaces);
      setTargetNamespaces(String.join(",", namespaces));
    }
  }

  @SuppressWarnings("unchecked")
  private void loadImagePullSecretsFromMap(Map<String, Object> map) {
    List<Map<String, String>> imagePullSecrets =
        (List<Map<String, String>>) map.get("imagePullSecrets");
    if (imagePullSecrets != null) {
      // TBD - enhance OperatorValues to have an array of image pull secrets, instead of just one
      String secretName = (String) imagePullSecrets.get(0).get("name");
      if (secretName != null) {
        setWeblogicOperatorImagePullSecretName(secretName);
      }
    }
  }

  Map<String, Object> createMap() {
    HashMap<String, Object> map = new HashMap<>();

    addStringMapEntry(map, this::getServiceAccount, "serviceAccount");
    addStringMapEntry(map, this::getWeblogicOperatorImage, "image");
    addStringMapEntry(map, this::getJavaLoggingLevel, "javaLoggingLevel");
    addStringMapEntry(map, this::getNamespace, "operatorNamespace");
    addStringMapEntry(map, this::getWeblogicOperatorImagePullPolicy, "imagePullPolicy");
    addStringMapEntry(map, this::getExternalOperatorSecret, "externalOperatorSecret");
    addStringMapEntry(map, this::getExternalOperatorCert, "externalOperatorCert");
    addStringMapEntry(map, this::getExternalOperatorKey, "externalOperatorKey");
    addStringMapEntry(map, this::getLogStashImage, "logStashImage");
    addStringMapEntry(map, this::getElasticSearchHost, "elasticSearchHost");

    addMapEntry(map, this::isExternalRestEnabled, "externalRestEnabled");
    addMapEntry(map, this::isRemoteDebugNotPortEnabled, "remoteDebugNodePortEnabled");
    addMapEntry(map, this::isElkIntegrationEnabled, "elkIntegrationEnabled");

    addMapEntry(map, this::getExternalRestHttpsPortNum, "externalRestHttpsPort");
    addMapEntry(map, this::getExternalDebugHttpPortNum, "externalDebugHttpPort");
    addMapEntry(map, this::getInternalDebugHttpPortNum, "internalDebugHttpPort");
    addMapEntry(map, this::getElasticSearchPortNum, "elasticSearchPort");

    addDomainNamespaces(map);
    addImagePullSecrets(map);
    return map;
  }

  private void addDomainNamespaces(HashMap<String, Object> map) {
    String targetNamespaces = getTargetNamespaces();
    if (targetNamespaces.length() > 0) {
      List<String> namespaces = new ArrayList<>();
      for (String namespace : targetNamespaces.split(",")) {
        namespaces.add(namespace);
      }
      map.put("domainNamespaces", namespaces);
    }
  }

  private void addImagePullSecrets(HashMap<String, Object> map) {
    String secretName = getWeblogicOperatorImagePullSecretName();
    if (!isNullOrEmpty(secretName)) {
      map.put("imagePullSecrets", singletonList(singletonMap("name", secretName)));
    }
  }

  private Boolean isExternalRestEnabled() {
    return MapUtils.valueOf(getExternalRestEnabled());
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

  private Integer getElasticSearchPortNum() {
    return MapUtils.integerValue(getElasticSearchPort());
  }
}
