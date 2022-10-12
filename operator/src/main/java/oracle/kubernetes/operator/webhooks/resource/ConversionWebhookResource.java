// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import oracle.kubernetes.common.utils.SchemaConversionUtils;
import oracle.kubernetes.operator.helpers.EventHelper;
import oracle.kubernetes.operator.http.rest.RestConfig;
import oracle.kubernetes.operator.http.rest.backend.RestBackend;
import oracle.kubernetes.operator.http.rest.resource.BaseResource;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.webhooks.model.ConversionRequest;
import oracle.kubernetes.operator.webhooks.model.ConversionResponse;
import oracle.kubernetes.operator.webhooks.model.ConversionReviewModel;
import oracle.kubernetes.operator.webhooks.model.Result;
import org.glassfish.jersey.server.ResourceConfig;

import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_CONVERSION_FAILED;
import static oracle.kubernetes.operator.EventConstants.OPERATOR_WEBHOOK_COMPONENT;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.CONVERSION_WEBHOOK_FAILED;
import static oracle.kubernetes.operator.helpers.EventHelper.createConversionWebhookEvent;
import static oracle.kubernetes.operator.webhooks.utils.GsonBuilderUtils.readConversionReview;
import static oracle.kubernetes.operator.webhooks.utils.GsonBuilderUtils.writeConversionReview;

/**
 * ConversionWebhookResource is a jaxrs resource that implements the REST api for the /webhook
 * path. It is used as an endpoint for conversion webhook defined in domain CRD, the API server will invoke
 * this endpoint to convert domain resource domain from one API version to another.
 */
@Path("webhook")
public class ConversionWebhookResource extends BaseResource {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");
  public static final String FAILED_STATUS = "Failed";

  @Context
  private Application application;

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
    if (LOGGER.isFineEnabled()) {
      LOGGER.fine("Conversion webhook request: " + body);
    }

    try {
      conversionReview = readConversionReview(body);

      ResourceConfig rc = (ResourceConfig) application;
      RestConfig r = (RestConfig) rc.getProperty(RestConfig.REST_CONFIG_PROPERTY);
      RestBackend be = r.getBackend(null);

      conversionResponse = createConversionResponse(conversionReview.getRequest(), be);
    } catch (Exception e) {
      LOGGER.severe(DOMAIN_CONVERSION_FAILED, e.getMessage(), getConversionRequest(conversionReview));
      conversionResponse = new ConversionResponse()
          .uid(getUid(conversionReview)).result(new Result().status(FAILED_STATUS).message("Exception: " + e));
      generateFailedEvent(e, getConversionRequest(conversionReview));
    }
    ConversionReviewModel conversionReviewModel = new ConversionReviewModel()
        .apiVersion(Optional.ofNullable(conversionReview).map(ConversionReviewModel::getApiVersion).orElse(null))
        .kind(Optional.ofNullable(conversionReview).map(ConversionReviewModel::getKind).orElse(null))
        .response(conversionResponse);

    String response = writeConversionReview(conversionReviewModel);
    if (LOGGER.isFineEnabled()) {
      LOGGER.fine("Conversion webhook response: " + response);
    }
    return response;
  }

  private String getConversionRequest(ConversionReviewModel conversionReview) {
    return Optional.ofNullable(conversionReview).map(ConversionReviewModel::getRequest)
        .map(ConversionRequest::toString).orElse(null);
  }

  private void generateFailedEvent(Exception exception, String conversionRequest) {
    EventHelper.EventData eventData = new EventHelper.EventData(CONVERSION_WEBHOOK_FAILED, exception.getMessage())
        .resourceName(OPERATOR_WEBHOOK_COMPONENT).additionalMessage(conversionRequest);
    createConversionWebhookEvent(eventData);
  }

  private String getUid(ConversionReviewModel conversionReview) {
    return Optional.ofNullable(conversionReview).map(ConversionReviewModel::getRequest)
            .map(ConversionRequest::getUid).orElse(null);
  }

  /**
   * Create the conversion review response.
   * @param conversionRequest The request to be converted.
   * @param be REST backend
   * @return ConversionResponse The response to the conversion request.
   */
  @SuppressWarnings("unchecked")
  private ConversionResponse createConversionResponse(ConversionRequest conversionRequest,
                                                      RestBackend be) {
    SchemaConversionUtils schemaConversionUtils = new SchemaConversionUtils(conversionRequest.getDesiredAPIVersion());

    List<SchemaConversionUtils.Resources> convertedResources = conversionRequest.getDomains().stream()
          .map(d -> schemaConversionUtils.convertDomainSchema(d, () -> {
            String namespace = Optional.ofNullable((Map<String, Object>) d.get("metadata"))
                .map(m -> (String) m.get("namespace")).orElse("default");
            return be.listClusters(namespace);
          }))
          .collect(Collectors.toList());

    List<Object> convertedDomains = new ArrayList<>();
    for (SchemaConversionUtils.Resources cr : convertedResources) {
      convertedDomains.add(cr.domain);
      cr.clusters.forEach(be::createOrReplaceCluster);
    }

    return new ConversionResponse()
            .uid(conversionRequest.getUid())
            .result(new Result().status("Success"))
            .convertedObjects(convertedDomains);
  }
}