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
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import oracle.kubernetes.common.ClusterCustomResourceHelper;
import oracle.kubernetes.common.utils.SchemaConversionUtils;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.EventHelper;
import oracle.kubernetes.operator.helpers.GsonOffsetDateTime;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.rest.model.ConversionRequest;
import oracle.kubernetes.operator.rest.model.ConversionResponse;
import oracle.kubernetes.operator.rest.model.ConversionReviewModel;
import oracle.kubernetes.operator.rest.model.Result;
import oracle.kubernetes.weblogic.domain.model.Cluster;
import oracle.kubernetes.weblogic.domain.model.ClusterList;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;

import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_CONVERSION_FAILED;
import static oracle.kubernetes.operator.EventConstants.CONVERSION_WEBHOOK_COMPONENT;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.CONVERSION_WEBHOOK_FAILED;
import static oracle.kubernetes.operator.helpers.EventHelper.createConversionWebhookEvent;

/**
 * ConversionWebhookResource is a jaxrs resource that implements the REST api for the /webhook
 * path. It is used as an endpoint for conversion webhook defined in domain CRD, the API server will invoke
 * this endpoint to convert domain resource domain from one API version to another.
 */
@Path("webhook")
public class ConversionWebhookResource extends BaseResource implements ClusterCustomResourceHelper {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");
  public static final String FAILED_STATUS = "Failed";

  /** Construct a ConversionWebhookResource. */
  public ConversionWebhookResource() {
    super(null, "webhook");
  }

  /**
   * Convert the request in ConversionReview to the desired version.
   *
   * @param body - a String representation of JSON document describing the ConversionReview with conversion request.
   *
   * @return a String representation of JSON document describing the ConversionReview with conversion response.
   *
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public String post(String body) {
    ConversionReviewModel conversionReview = null;
    ConversionResponse conversionResponse;
    LOGGER.entering(href());

    try {
      conversionReview = readConversionReview(body);
      conversionResponse = createConversionResponse(conversionReview.getRequest());
    } catch (Exception e) {
      LOGGER.severe(DOMAIN_CONVERSION_FAILED, e.getMessage(), getConversionRequest(conversionReview));
      conversionResponse = new ConversionResponse()
          .uid(getUid(conversionReview))
          .result(new Result().status(FAILED_STATUS)
              .message("Exception: " + e.toString()));
      generateFailedEvent(e, getConversionRequest(conversionReview));
    }
    LOGGER.exiting(conversionResponse);
    return writeConversionReview(new ConversionReviewModel()
        .apiVersion(Optional.ofNullable(conversionReview).map(ConversionReviewModel::getApiVersion).orElse(null))
        .kind(Optional.ofNullable(conversionReview).map(ConversionReviewModel::getKind).orElse(null))
        .response(conversionResponse));
  }

  private String getConversionRequest(ConversionReviewModel conversionReview) {
    return Optional.ofNullable(conversionReview).map(c -> c.getRequest()).map(cr -> cr.toString()).orElse(null);
  }

  private void generateFailedEvent(Exception exception, String conversionRequest) {
    EventHelper.EventData eventData = new EventHelper.EventData(CONVERSION_WEBHOOK_FAILED, exception.getMessage())
        .resourceName(CONVERSION_WEBHOOK_COMPONENT).additionalMessage(conversionRequest);
    createConversionWebhookEvent(eventData);
  }

  private String getUid(ConversionReviewModel conversionReview) {
    return Optional.ofNullable(conversionReview).map(ConversionReviewModel::getRequest)
            .map(ConversionRequest::getUid).orElse(null);
  }

  private ConversionReviewModel readConversionReview(String resourceName) {
    return getGsonBuilder().fromJson(resourceName, ConversionReviewModel.class);
  }

  /**
   * Create the conversion review response.
   * @param conversionRequest The request to be converted.
   * @return ConversionResponse The response to the conversion request.
   */
  private ConversionResponse createConversionResponse(ConversionRequest conversionRequest) {
    List<Object> convertedDomains = new ArrayList<>();
    SchemaConversionUtils schemaConversionUtils = new SchemaConversionUtils();

    conversionRequest.getObjects()
            .forEach(domain -> convertedDomains.add(
                    schemaConversionUtils.convertDomainSchema(this,
                            (Map<String,Object>) domain, conversionRequest.getDesiredAPIVersion())));


    return new ConversionResponse()
            .uid(conversionRequest.getUid())
            .result(new Result().status("Success"))
            .convertedObjects(convertedDomains);
  }

  private String setName(Map<String, Object> resource, String name) {
    Map<String, Object> metadata = (Map<String, Object>) resource.get("metadata");
    return (String) metadata.put(V1ObjectMeta.SERIALIZED_NAME_NAME, name);
  }

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

    new CallBuilder().createClusterCustomResource(c, KubernetesConstants.CLUSTER_VERSION);
  }

  private ClusterSpec convertToClusterSpec(Map<String, Object> clusterSpec) {
    String str = getGsonBuilder().toJson(clusterSpec);
    return getGsonBuilder().fromJson(str, ClusterSpec.class);
  }

  private String qualifyClusterResourceName(String domainUid, String clusterName) {
    return domainUid + "-" + clusterName;
  }

  private String deQualifyClusterResourceName(String qualifiedResourceName, String domainUid) {
    return Optional.ofNullable(qualifiedResourceName).map(q ->
        deQualifyClusterName(q, domainUid)).orElse(null);
  }

  private String deQualifyClusterName(String clusterName, String domainUid) {
    if (clusterName.startsWith(domainUid)) {
      return clusterName.substring((domainUid + "-").length());
    }
    return clusterName;
  }

  private Map<String, Cluster> getClusterResources(String namespace) {
    Map<String, Cluster> result = new LinkedTreeMap<>();
    try {
      ClusterList clusters = new CallBuilder().listCluster(namespace);
      for (Cluster cluster : clusters.getItems()) {
        result.put(cluster.getClusterName(), cluster);
      }
    } catch (ApiException e) {
      e.printStackTrace();
    }
    return result;
  }

  @Override
  public Map<String, Object> getDeployedClusterResources(String namespace, String domainUid) {
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

  private Map<String, Object> convertClusterToMap(Cluster cluster) {
    String json = getGsonBuilder().toJson(cluster,  Cluster.class);
    return getGsonBuilder().fromJson(json, LinkedTreeMap.class);
  }

  @Override
  public List<String> getNamesOfDeployedClusterResources(String namespace, String domainUid) {
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

  private String writeConversionReview(ConversionReviewModel conversionReviewModel) {
    return getGsonBuilder().toJson(conversionReviewModel, ConversionReviewModel.class);
  }

  private Gson getGsonBuilder() {
    return new GsonBuilder()
            .registerTypeAdapter(OffsetDateTime.class, new GsonOffsetDateTime())
            .create();
  }
}