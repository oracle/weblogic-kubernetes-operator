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
import oracle.kubernetes.operator.helpers.GsonOffsetDateTime;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.rest.model.AdmissionRequest;
import oracle.kubernetes.operator.rest.model.AdmissionResponse;
import oracle.kubernetes.operator.rest.model.AdmissionReview;
import oracle.kubernetes.operator.rest.model.Status;

import static oracle.kubernetes.common.logging.MessageKeys.VALIDATION_FAILED;

/**
 * AdmissionWebhookResource is a jaxrs resource that implements the REST api for the /admission
 * path. It is used as an endpoint for admission webhook, the API server will invoke
 * this endpoint to validate a change request to a domain resource or cluster resource.
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
   * Validate a change request to a domain or cluster resource.
   *
   * @param body - a String representation of JSON document describing the AdmissionReview with admission request.
   *
   * @return a String representation of JSON document describing the AdmissionReview with admission response.
   *
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public String post(String body) {
    LOGGER.info("Validating webhook is invoked");
    LOGGER.entering(href());

    AdmissionReview admissionReview = null;
    AdmissionRequest admissionRequest = null;
    AdmissionResponse admissionResponse;

    try {
      admissionReview = readAdmissionReview(body);
      admissionRequest = getAdmissionRequest(admissionReview);
      admissionResponse = createAdmissionResponse(admissionRequest);
    } catch (Exception e) {
      LOGGER.severe(VALIDATION_FAILED, e.getMessage(), getAdmissionRequestAsString(admissionReview));
      admissionResponse = new AdmissionResponse()
          .uid(getUid(admissionRequest))
          .status(new Status().code(FAILED_STATUS)
              .message("Exception: " + e));
    }
    LOGGER.exiting(admissionResponse);
    return writeAdmissionReview(new AdmissionReview()
        .apiVersion(Optional.ofNullable(admissionReview).map(AdmissionReview::getApiVersion).orElse(null))
        .kind(Optional.ofNullable(admissionReview).map(AdmissionReview::getKind).orElse(null))
        .response(admissionResponse));
  }

  private AdmissionRequest getAdmissionRequest(AdmissionReview admissionReview) {
    return Optional.ofNullable(admissionReview)
        .map(AdmissionReview::getRequest)
        .orElse(null);
  }

  private String getAdmissionRequestAsString(AdmissionReview admissionReview) {
    return Optional.ofNullable(admissionReview)
        .map(AdmissionReview::getRequest)
        .map(AdmissionRequest::toString)
        .orElse(null);
  }

  private String getUid(AdmissionRequest request) {
    return Optional.ofNullable(request).map(AdmissionRequest::getUid).orElse(null);
  }

  private AdmissionReview readAdmissionReview(String resourceName) {
    return getGsonBuilder().fromJson(resourceName, AdmissionReview.class);
  }

  private AdmissionResponse createAdmissionResponse(AdmissionRequest request) {
    return new AdmissionResponse()
        .uid(request.getUid())
        .allowed(request == null || validate(request.getOldObject(), request.getObject()))
        .status((new Status().message("Success")));
  }

  private String writeAdmissionReview(AdmissionReview admissionReview) {
    return getGsonBuilder().toJson(admissionReview, AdmissionReview.class);
  }

  private Gson getGsonBuilder() {
    return new GsonBuilder()
            .registerTypeAdapter(OffsetDateTime.class, new GsonOffsetDateTime())
            .create();
  }

  private boolean validate(Object oldObject, Object object) {
    LOGGER.fine("validating " + object + " against " + oldObject);
    // TODO add implementation of the validation in the next PR
    return true;
  }
}
