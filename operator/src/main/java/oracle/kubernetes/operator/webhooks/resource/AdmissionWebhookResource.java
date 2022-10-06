// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.resource;

import java.util.Optional;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import oracle.kubernetes.operator.http.rest.resource.BaseResource;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.webhooks.model.AdmissionRequest;
import oracle.kubernetes.operator.webhooks.model.AdmissionResponse;
import oracle.kubernetes.operator.webhooks.model.AdmissionResponseStatus;
import oracle.kubernetes.operator.webhooks.model.AdmissionReview;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static oracle.kubernetes.common.logging.MessageKeys.VALIDATION_FAILED;
import static oracle.kubernetes.operator.webhooks.utils.GsonBuilderUtils.readAdmissionReview;
import static oracle.kubernetes.operator.webhooks.utils.GsonBuilderUtils.writeAdmissionReview;

/**
 * AdmissionWebhookResource is a jaxrs resource that implements the REST api for the /admission
 * path. It is used as an endpoint for admission webhook, the API server will invoke
 * this endpoint to validate a change request to a domain resource or cluster resource.
 */
@Path("admission")
public class AdmissionWebhookResource extends BaseResource {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");

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
    LOGGER.fine("Validating webhook is invoked");

    AdmissionReview admissionReview = null;
    AdmissionRequest admissionRequest = null;
    AdmissionResponse admissionResponse;

    try {
      admissionReview = readAdmissionReview(body);
      admissionRequest = getAdmissionRequest(admissionReview);
      admissionResponse = createAdmissionResponse(admissionRequest);
    } catch (Exception e) {
      LOGGER.severe(VALIDATION_FAILED, e.getMessage(), getAdmissionRequestAsString(admissionReview));
      admissionResponse = createResponseWithException(admissionRequest, e);
    }

    return writeAdmissionReview(createResponseAdmissionReview(admissionReview, admissionResponse));
  }

  private AdmissionResponse createResponseWithException(AdmissionRequest admissionRequest, Exception e) {
    return new AdmissionResponse()
        .uid(getUid(admissionRequest))
        .status(new AdmissionResponseStatus().message("Exception: " + e));
  }

  private AdmissionReview createResponseAdmissionReview(AdmissionReview admissionReview,
                                                        AdmissionResponse admissionResponse) {
    return new AdmissionReview()
        .apiVersion(getRequestApiVersion(admissionReview))
        .kind(getRequestKind(admissionReview))
        .response(admissionResponse);
  }

  @Nullable
  private String getRequestKind(AdmissionReview admissionReview) {
    return Optional.ofNullable(admissionReview).map(AdmissionReview::getKind).orElse(null);
  }

  @Nullable
  private String getRequestApiVersion(AdmissionReview admissionReview) {
    return Optional.ofNullable(admissionReview).map(AdmissionReview::getApiVersion).orElse(null);
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
        .orElse("");
  }

  private String getUid(AdmissionRequest request) {
    return Optional.ofNullable(request).map(AdmissionRequest::getUid).orElse(null);
  }

  private AdmissionResponse createAdmissionResponse(AdmissionRequest request) {
    if (request == null || request.getObject() == null) {
      return new AdmissionResponse().uid(getUid(request)).allowed(true);
    }

    return validate(request);
  }

  private AdmissionResponse validate(@NotNull AdmissionRequest request) {
    LOGGER.fine("validating " +  request.getObject() + " against " + request.getOldObject()
        + " Kind = " + request.getKind() + " uid = " + request.getUid() + " resource = " + request.getResource()
        + " subResource = " + request.getSubResource());
    return getAdmissionChecker(request).validate().uid(getUid(request));
  }

  @NotNull
  private AdmissionChecker getAdmissionChecker(@NotNull AdmissionRequest request) {
    if (request.isCluster()) {
      return getClusterAdmissionChecker(request);
    }
    return getDomainAdmissionChecker(request);
  }

  @NotNull
  private AdmissionChecker getDomainAdmissionChecker(@NotNull AdmissionRequest request) {
    DomainResource existing = (DomainResource) request.getExistingResource();
    DomainResource proposed = (DomainResource) request.getProposedResource();
    return isNewResource(request)
        ? new DomainCreateAdmissionChecker(proposed)
        : new DomainUpdateAdmissionChecker(existing, proposed);
  }

  private boolean isNewResource(@NotNull AdmissionRequest request) {
    return request.getOldObject() == null;
  }

  @NotNull
  private AdmissionChecker getClusterAdmissionChecker(@NotNull AdmissionRequest request) {
    ClusterResource existing = (ClusterResource) request.getExistingResource();
    ClusterResource proposed = (ClusterResource) request.getProposedResource();
    return isNewResource(request)
        ? new ClusterCreateAdmissionChecker(proposed)
        : new ClusterUpdateAdmissionChecker(existing, proposed);
  }

}
