// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.resource;

import java.time.OffsetDateTime;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import oracle.kubernetes.operator.helpers.EventHelper;
import oracle.kubernetes.operator.helpers.GsonOffsetDateTime;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.rest.model.AdmissionRequest;
import oracle.kubernetes.operator.rest.model.AdmissionResponse;
import oracle.kubernetes.operator.rest.model.AdmissionReviewModel;
import oracle.kubernetes.operator.rest.model.Status;
import oracle.kubernetes.weblogic.domain.model.Domain;

import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_CONVERSION_FAILED;
import static oracle.kubernetes.operator.EventConstants.CONVERSION_WEBHOOK_COMPONENT;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.CONVERSION_WEBHOOK_FAILED;

/**
 * AdmissionWebhookResource is a jaxrs resource that implements the REST api for the /admission
 * path. It is used as an endpoint for admission webhook, the API server will invoke
 * this endpoint to validate a change re  quest to a domain resource or cluster resource.
 */
@Path("admission")
public class AdmissionWebhookResource extends BaseResource {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");
  public static final String FAILED_STATUS = "Failed";

  /** Construct a AdmissionWebhookResource. */
  public AdmissionWebhookResource() {
    super(null, "admission");
  }

  /**
   * Convert the request in AdmissionReview to the desired version.
   *
   * @param body - a String representation of JSON document describing the AdmissionReview with admission request.
   *
   * @return a String representation of JSON document describing the AdmissionReview with admission response.
   *
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public String post(String body) {
    LOGGER.entering(href());
    AdmissionReviewModel admissionReview = null;
    AdmissionResponse admissionResponse;

    try {
      admissionReview = readAdmissionReview(body);
      admissionResponse = createAdmissionResponse(getAdmissionRequest(admissionReview));
    } catch (Exception e) {
      LOGGER.severe(DOMAIN_CONVERSION_FAILED, e.getMessage(), getAdmissionRequestAsString(admissionReview));
      admissionResponse = new AdmissionResponse()
          .uid(getUid(admissionReview))
          .result(new Status().code(FAILED_STATUS)
              .message("Exception: " + e));
      generateFailedEvent(e, getAdmissionRequestAsString(admissionReview));
    }
    LOGGER.exiting(admissionResponse);
    return writeAdmissionReview(new AdmissionReviewModel()
        .apiVersion(Optional.ofNullable(admissionReview).map(AdmissionReviewModel::getApiVersion).orElse(null))
        .kind(Optional.ofNullable(admissionReview).map(AdmissionReviewModel::getKind).orElse(null))
        .response(admissionResponse));
  }

  private AdmissionRequest getAdmissionRequest(AdmissionReviewModel admissionReview) {
    return Optional.ofNullable(admissionReview)
        .map(AdmissionReviewModel::getRequest)
        .orElse(null);
  }

  private String getAdmissionRequestAsString(AdmissionReviewModel admissionReview) {
    return Optional.ofNullable(admissionReview)
        .map(AdmissionReviewModel::getRequest)
        .map(AdmissionRequest::toString)
        .orElse(null);
  }

  private void generateFailedEvent(Exception exception, String admissionRequest) {
    EventHelper.EventData eventData = new EventHelper.EventData(CONVERSION_WEBHOOK_FAILED, exception.getMessage())
        .resourceName(CONVERSION_WEBHOOK_COMPONENT).additionalMessage(admissionRequest);
    //createAdmissionWebhookEvent(eventData);
  }

  private String getUid(AdmissionReviewModel admissionReview) {
    return Optional.ofNullable(admissionReview).map(AdmissionReviewModel::getRequest)
            .map(AdmissionRequest::getUid).orElse(null);
  }

  private AdmissionReviewModel readAdmissionReview(String resourceName) {
    return getGsonBuilder().fromJson(resourceName, AdmissionReviewModel.class);
  }

  /**
   * Create the admission review response.
   * @param admissionRequest The request to be converted.
   * @return AdmissionResponse The response to the admission request.
   */
  private AdmissionResponse createAdmissionResponse(AdmissionRequest admissionRequest) {

    boolean accept = validate(admissionRequest.getOldObject(), admissionRequest.getObject());

    return new AdmissionResponse()
        .uid(admissionRequest.getUid())
        .allowed(accept)
        .status((new Status().message("Success")));
  }

  private String writeAdmissionReview(AdmissionReviewModel admissionReviewModel) {
    return getGsonBuilder().toJson(admissionReviewModel, AdmissionReviewModel.class);
  }

  private Gson getGsonBuilder() {
    return new GsonBuilder()
            .registerTypeAdapter(OffsetDateTime.class, new GsonOffsetDateTime())
            .create();
  }


  /**
   * Convert the domain schema to desired API version.
   * @param oldObject object that needs to be validated against.
   * @param object object that needs to be validated.
   * @return true if valid, otherwise false
   */
  private boolean validate(Object oldObject, Object object) {
    LOGGER.fine("validate domain " + object + " against " + oldObject);
    LOGGER.info("Validating webhook is invoked and returns true");

    if (oldObject instanceof Domain) {
      return validateDomain((Domain) oldObject, (Domain) object);
    }

    return true;
  }

  private boolean validateDomain(Domain oldDomain, Domain proposedDomain) {
    return true;
  }
}
