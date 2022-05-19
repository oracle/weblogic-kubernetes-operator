// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.resource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import oracle.kubernetes.common.utils.SchemaConversionUtils;
import oracle.kubernetes.operator.helpers.EventHelper;
import oracle.kubernetes.operator.helpers.GsonOffsetDateTime;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.rest.model.ConversionRequest;
import oracle.kubernetes.operator.rest.model.ConversionResponse;
import oracle.kubernetes.operator.rest.model.ConversionReviewModel;
import oracle.kubernetes.operator.rest.model.Result;

import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_CONVERSION_FAILED;
import static oracle.kubernetes.operator.EventConstants.CONVERSION_WEBHOOK_COMPONENT;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.CONVERSION_WEBHOOK_FAILED;
import static oracle.kubernetes.operator.helpers.EventHelper.createConversionWebhookEvent;
import static oracle.kubernetes.operator.utils.GsonBuilderUtils.readConversionReview;

/**
 * ConversionWebhookResource is a jaxrs resource that implements the REST api for the /webhook
 * path. It is used as an endpoint for conversion webhook defined in domain CRD, the API server will invoke
 * this endpoint to convert domain resource domain from one API version to another.
 */
@Path("webhook")
public class ConversionWebhookResource extends BaseResource {

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
          .uid(getUid(conversionReview)).result(new Result().status(FAILED_STATUS).message("Exception: " + e));
      generateFailedEvent(e, getConversionRequest(conversionReview));
    }
    LOGGER.exiting(conversionResponse);
    return writeConversionReview(new ConversionReviewModel()
        .apiVersion(Optional.ofNullable(conversionReview).map(ConversionReviewModel::getApiVersion).orElse(null))
        .kind(Optional.ofNullable(conversionReview).map(ConversionReviewModel::getKind).orElse(null))
        .response(conversionResponse));
  }

  private String getConversionRequest(ConversionReviewModel conversionReview) {
    return Optional.ofNullable(conversionReview).map(ConversionReviewModel::getRequest)
        .map(ConversionRequest::toString).orElse(null);
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

  /**
   * Create the conversion review response.
   * @param conversionRequest The request to be converted.
   * @return ConversionResponse The response to the conversion request.
   */
  private ConversionResponse createConversionResponse(ConversionRequest conversionRequest) {
    SchemaConversionUtils schemaConversionUtils = new SchemaConversionUtils(conversionRequest.getDesiredAPIVersion(),
        new ClusterCustomResourceHelperImpl());

    List<Object> convertedDomains = conversionRequest.getDomains().stream()
          .map(schemaConversionUtils::convertDomainSchema)
          .collect(Collectors.toList());


    return new ConversionResponse()
            .uid(conversionRequest.getUid())
            .result(new Result().status("Success"))
            .convertedObjects(convertedDomains);
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