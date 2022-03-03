// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.rest.model.ConversionResponse;
import oracle.kubernetes.operator.rest.model.ConversionReviewModel;
import oracle.kubernetes.operator.utils.DomainUpgradeUtils;

/**
 * ConversionWebhookResource is a jaxrs resource that implements the REST api for the /webhook
 * path. It is used as an endpoint for conversion webhook defined in domain CRD, the API server will invoke
 * this endpoint to convert domain resource domain from one API version to another.
 */
@Path("webhook")
public class ConversionWebhookResource extends BaseResource {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");
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
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public String post(String body) {
    LOGGER.entering(href());
    ConversionReviewModel conversionReview = conversionUtils.readConversionReview(body);
    ConversionResponse conversionResponse = conversionUtils.createConversionResponse(conversionReview.getRequest());
    LOGGER.exiting(conversionResponse);
    return conversionUtils.writeConversionReview(new ConversionReviewModel()
            .apiVersion(conversionReview.getApiVersion())
            .kind(conversionReview.getKind())
            .response(conversionResponse));
  }
}