// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.utils;

import java.time.OffsetDateTime;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import oracle.kubernetes.operator.helpers.GsonOffsetDateTime;
import oracle.kubernetes.operator.webhooks.model.AdmissionReview;
import oracle.kubernetes.operator.webhooks.model.ConversionReviewModel;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

public class GsonBuilderUtils {

  private GsonBuilderUtils() {
    // no-op
  }

  public static ConversionReviewModel readConversionReview(String resourceName) {
    return getGsonBuilder().fromJson(resourceName, ConversionReviewModel.class);
  }

  public static String writeConversionReview(ConversionReviewModel conversionReviewModel) {
    return getGsonBuilder().toJson(conversionReviewModel, ConversionReviewModel.class);
  }

  public static AdmissionReview readAdmissionReview(String resourceName) {
    return getGsonBuilder().fromJson(resourceName, AdmissionReview.class);
  }

  public static String writeAdmissionReview(AdmissionReview admissionReview) {
    return getGsonBuilder().toJson(admissionReview, AdmissionReview.class);
  }

  public static DomainResource readDomain(String resourceName) {
    return getGsonBuilder().fromJson(resourceName, DomainResource.class);
  }

  public static String writeDomain(DomainResource domain) {
    return getGsonBuilder().toJson(domain,DomainResource.class);
  }

  public static Map<String, Object> writeDomainToMap(DomainResource domain) {
    return readMap(getGsonBuilder().toJson(domain, DomainResource.class));
  }

  public static String writeMap(Map<String, Object> map) {
    return getGsonBuilder().toJson(map, Map.class);
  }

  public static Map<String, Object> readMap(String map) {
    return getGsonBuilder().fromJson(map, Map.class);
  }

  private static Gson getGsonBuilder() {
    return new GsonBuilder()
        .registerTypeAdapter(OffsetDateTime.class, new GsonOffsetDateTime())
        .create();
  }
}
