// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.resource;

import java.util.Optional;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.rest.model.ConversionRequest;
import oracle.kubernetes.operator.rest.model.ConversionResponse;
import oracle.kubernetes.operator.rest.model.ConversionReviewModel;
import oracle.kubernetes.operator.rest.model.Result;
import oracle.kubernetes.operator.utils.DomainUpgradeUtils;

/**
 * ConversionWebhookResource is a jaxrs resource that implements the REST api for the /webhook
 * path. It is used as an endpoint for conversion webhook defined in domain CRD, the API server will invoke
 * this endpoint to convert domain resource domain from one API version to another.
 */
@Path("webhook")
public class ConversionWebhookResource extends BaseResource {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");
  public static final String FAILED_STATUS = "Failed";
  private final DomainUpgradeUtils conversionUtils = new DomainUpgradeUtils();

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
      conversionReview = conversionUtils.readConversionReview(body);
      conversionResponse = conversionUtils.createConversionResponse(conversionReview.getRequest());
    } catch (Throwable t) {
      conversionResponse = new ConversionResponse()
              .uid(getUid(conversionReview))
              .result(new Result().status(FAILED_STATUS)
                      .message("Exception: " + t.getMessage()));
    }
    LOGGER.exiting(conversionResponse);
    return conversionUtils.writeConversionReview(new ConversionReviewModel()
            .apiVersion(Optional.ofNullable(conversionReview).map(ConversionReviewModel::getApiVersion).orElse(null))
            .kind(Optional.ofNullable(conversionReview).map(ConversionReviewModel::getKind).orElse(null))
            .response(conversionResponse));
  }

  private String getUid(ConversionReviewModel conversionReview) {
    return Optional.ofNullable(conversionReview).map(ConversionReviewModel::getRequest)
            .map(ConversionRequest::getUid).orElse(null);
  }
}