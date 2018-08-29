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
    loadFromMap(map, this::setServiceAccount, "operatorServiceAccount");
    loadFromMap(map, this::setWeblogicOperatorImage, "operatorImage");
    loadFromMap(map, this::setJavaLoggingLevel, "javaLoggingLevel");
    loadFromMap(map, this::setNamespace, "operatorNamespace");
    loadFromMap(map, this::setWeblogicOperatorImagePullPolicy, "operatorImagePullPolicy");
    loadFromMap(map, this::setExternalRestOption, "externalRestOption");
    loadFromMap(map, this::setExternalSans, "externalOperatorCertSans");
    loadFromMap(map, this::setExternalOperatorCert, "externalOperatorCert");
    loadFromMap(map, this::setExternalOperatorKey, "externalOperatorKey");
    loadFromMap(map, this::setInternalRestOption, "internalRestOption");
    loadFromMap(map, this::setInternalOperatorCert, "internalOperatorCert");
    loadFromMap(map, this::setInternalOperatorKey, "internalOperatorKey");

    loadBooleanFromMap(map, this::setRemoteDebugNodePortEnabled, "remoteDebugNodePortEnabled");
    loadBooleanFromMap(map, this::setElkIntegrationEnabled, "elkIntegrationEnabled");

    loadIntegerFromMap(map, this::setExternalRestHttpsPort, "externalRestHttpsPort");
    loadIntegerFromMap(map, this::setExternalDebugHttpPort, "externalDebugHttpPort");
    loadIntegerFromMap(map, this::setInternalDebugHttpPort, "internalDebugHttpPort");

    loadDomainNamespacesFromMap(map);
    loadOperatorImagePullSecretsFromMap(map);
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
  private void loadOperatorImagePullSecretsFromMap(Map<String, Object> map) {
    List<Map<String, String>> operatorImagePullSecrets =
        (List<Map<String, String>>) map.get("operatorImagePullSecrets");
    if (operatorImagePullSecrets != null) {
      // TBD - enhance OperatorValues to have an array of image pull secrets, instead of just one
      String secretName = (String) operatorImagePullSecrets.get(0).get("name");
      if (secretName != null) {
        setWeblogicOperatorImagePullSecretName(secretName);
      }
    }
  }

  Map<String, Object> createMap() {
    HashMap<String, Object> map = new HashMap<>();

    addStringMapEntry(map, this::getServiceAccount, "operatorServiceAccount");
    addStringMapEntry(map, this::getWeblogicOperatorImage, "operatorImage");
    addStringMapEntry(map, this::getJavaLoggingLevel, "javaLoggingLevel");
    addStringMapEntry(map, this::getNamespace, "operatorNamespace");
    addStringMapEntry(map, this::getWeblogicOperatorImagePullPolicy, "operatorImagePullPolicy");
    addStringMapEntry(map, this::getExternalRestOption, "externalRestOption");
    addStringMapEntry(map, this::getExternalSans, "externalOperatorCertSans");
    addStringMapEntry(map, this::getExternalOperatorCert, "externalOperatorCert");
    addStringMapEntry(map, this::getExternalOperatorKey, "externalOperatorKey");
    addStringMapEntry(map, this::getInternalRestOption, "internalRestOption");
    addStringMapEntry(map, this::getInternalOperatorCert, "internalOperatorCert");
    addStringMapEntry(map, this::getInternalOperatorKey, "internalOperatorKey");

    addMapEntry(map, this::isRemoteDebugNotPortEnabled, "remoteDebugNodePortEnabled");
    addMapEntry(map, this::isElkIntegrationEnabled, "elkIntegrationEnabled");

    addMapEntry(map, this::getExternalRestHttpsPortNum, "externalRestHttpsPort");
    addMapEntry(map, this::getExternalDebugHttpPortNum, "externalDebugHttpPort");
    addMapEntry(map, this::getInternalDebugHttpPortNum, "internalDebugHttpPort");

    addDomainNamespaces(map);
    addOperatorImagePullSecrets(map);
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

  private void addOperatorImagePullSecrets(HashMap<String, Object> map) {
    String secretName = getWeblogicOperatorImagePullSecretName();
    if (!isNullOrEmpty(secretName)) {
      map.put("operatorImagePullSecrets", singletonList(singletonMap("name", secretName)));
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

  // Eventually in 2.0 the operator runtime will create the self signed certs.
  // For now, they must be in the values:

  @Override
  public OperatorValues setupExternalRestSelfSignedCert() {
    return super.setupExternalRestSelfSignedCert()
        .externalOperatorCert(toBase64(externalOperatorSelfSignedCertPem()))
        .externalOperatorKey(toBase64(externalOperatorSelfSignedKeyPem()));
  }

  @Override
  public OperatorValues setupInternalRestSelfSignedCert() {
    return super.setupInternalRestSelfSignedCert()
        .internalOperatorCert(toBase64(internalOperatorSelfSignedCertPem()))
        .internalOperatorKey(toBase64(internalOperatorSelfSignedKeyPem()));
  }
}
