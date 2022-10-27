// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.utils;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import oracle.kubernetes.operator.helpers.GsonOffsetDateTime;
import oracle.kubernetes.operator.webhooks.model.AdmissionReview;
import oracle.kubernetes.operator.webhooks.model.ConversionReviewModel;
import oracle.kubernetes.operator.webhooks.model.Scale;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
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

  public static Map<String, Object> writeDomainToMap(DomainResource domain) {
    return readMap(getGsonBuilder().toJson(domain, DomainResource.class));
  }

  public static ClusterResource readCluster(String resourceName) {
    return getGsonBuilder().fromJson(resourceName, ClusterResource.class);
  }

  public static Map<String, Object> writeClusterToMap(ClusterResource cluster) {
    return readMap(getGsonBuilder().toJson(cluster, ClusterResource.class));
  }

  public static Scale readScale(String resourceName) {
    return getGsonBuilder().fromJson(resourceName, Scale.class);
  }

  public static Map<String, Object> writeScaleToMap(Scale scale) {
    return readMap(getGsonBuilder().toJson(scale, Scale.class));
  }

  public static String writeMap(Map<String, Object> map) {
    return getGsonBuilder().toJson(map, Map.class);
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> readMap(String map) {
    return getGsonBuilder().fromJson(map, Map.class);
  }

  private static Gson getGsonBuilder() {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE);
    gsonBuilder.registerTypeAdapter(Double.class, new SimpleNumberTypeAdapter());
    gsonBuilder.registerTypeAdapter(OffsetDateTime.class, new GsonOffsetDateTime());
    return gsonBuilder.create();
  }

  private static class SimpleNumberTypeAdapter extends TypeAdapter<Double> {
    @Override
    public void write(JsonWriter out, Double value) throws IOException {
      if (value != null && value.equals(Math.rint(value))) {
        out.value(value.longValue());
      } else {
        out.value(value);
      }
    }

    @Override
    public Double read(JsonReader in) throws IOException {
      return Double.valueOf(in.nextString());
    }
  }
}
