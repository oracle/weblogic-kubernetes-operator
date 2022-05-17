// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.time.OffsetDateTime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import oracle.kubernetes.operator.helpers.GsonOffsetDateTime;
import oracle.kubernetes.operator.rest.model.AdmissionReview;
import oracle.kubernetes.operator.rest.model.ConversionReviewModel;

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

  private static Gson getGsonBuilder() {
    return new GsonBuilder()
        .registerTypeAdapter(OffsetDateTime.class, new GsonOffsetDateTime())
        .create();
  }
}
