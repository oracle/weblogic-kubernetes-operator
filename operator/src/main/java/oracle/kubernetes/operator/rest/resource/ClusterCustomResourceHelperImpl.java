// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.resource;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.internal.LinkedTreeMap;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.common.ClusterCustomResourceHelper;
import oracle.kubernetes.common.utils.SchemaConversionUtils;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.GsonOffsetDateTime;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.weblogic.domain.model.Cluster;
import oracle.kubernetes.weblogic.domain.model.ClusterList;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;

public class ClusterCustomResourceHelperImpl implements ClusterCustomResourceHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");

  @Override
  public void createClusterResource(Map<String, Object> clusterSpec, Map<String, Object> domain)
      throws ApiException {
    String namespace = SchemaConversionUtils.getNamespace(domain);
    String domainUid = SchemaConversionUtils.getDomainUid(domain);
    ClusterSpec clSpec = convertToClusterSpec(clusterSpec);

    Cluster c = new Cluster()
        .withApiVersion(KubernetesConstants.API_VERSION_CLUSTER_WEBLOGIC_ORACLE)
        .withKind(KubernetesConstants.CLUSTER)
        .withMetadata(new V1ObjectMeta()
            .name(qualifyClusterResourceName(domainUid, clSpec.getClusterName()))
            .namespace(namespace)
            .putLabelsItem("weblogic.domainUID", domainUid))
        .spec(clSpec);

    new CallBuilder().createClusterCustomResource(c);
  }

  @Override
  public Map<String, Object> listClusterResources(String namespace, String domainUid) {
    Map<String, Object> result = new LinkedTreeMap<>();
    Map<String, Cluster> clusters = getClusterResources(namespace);
    for (Entry<String, Cluster> entry : clusters.entrySet()) {
      Map<String, Object> cluster = convertClusterToMap(entry.getValue());
      String clusterName = deQualifyClusterResourceName(entry.getKey(), domainUid);
      if (clusterName != null) {
        setName(cluster, clusterName);
        result.put(clusterName, cluster);
      }
    }
    return result;
  }

  @Override
  public List<String> namesOfClusterResources(String namespace, String domainUid) {
    List<String> result = new ArrayList<>();
    Map<String, Cluster> clusters = getClusterResources(namespace);
    if (!clusters.isEmpty()) {
      for (Entry<String, Cluster> entry : clusters.entrySet()) {
        String clusterName = deQualifyClusterResourceName(entry.getKey(), domainUid);
        if (clusterName != null) {
          result.add(clusterName);
        }
      }
    }

    return result;
  }

  private Map<String, Cluster> getClusterResources(String namespace) {
    Map<String, Cluster> result = new LinkedTreeMap<>();
    try {
      ClusterList clusters = new CallBuilder().listCluster(namespace);
      for (Cluster cluster : clusters.getItems()) {
        result.put(cluster.getClusterName(), cluster);
      }
    } catch (ApiException e) {
      LOGGER.warning("Exception when attempting to list Cluster Resources", e);
    }
    return result;
  }

  private ClusterSpec convertToClusterSpec(Map<String, Object> clusterSpec) {
    String str = getGsonBuilder().toJson(clusterSpec);
    return getGsonBuilder().fromJson(str, ClusterSpec.class);
  }

  private static String setName(Map<String, Object> resource, String name) {
    Map<String, Object> metadata = (Map<String, Object>) resource.get("metadata");
    return (String) metadata.put(V1ObjectMeta.SERIALIZED_NAME_NAME, name);
  }


  private static String qualifyClusterResourceName(String domainUid, String clusterName) {
    return domainUid + "-" + clusterName;
  }

  private static String deQualifyClusterResourceName(String qualifiedResourceName, String domainUid) {
    return Optional.ofNullable(qualifiedResourceName).map(q ->
        deQualifyClusterName(q, domainUid)).orElse(qualifiedResourceName);
  }

  private static String deQualifyClusterName(String clusterName, String domainUid) {
    if (clusterName.startsWith(domainUid)) {
      return clusterName.substring((domainUid + "-").length());
    }
    return clusterName;
  }

  private Map<String, Object> convertClusterToMap(Cluster cluster) {
    String json = getGsonBuilder().toJson(cluster,  Cluster.class);
    return getGsonBuilder().fromJson(json, LinkedTreeMap.class);
  }

  private Gson getGsonBuilder() {
    return new GsonBuilder()
        .registerTypeAdapter(OffsetDateTime.class, new GsonOffsetDateTime())
        .create();
  }

}
