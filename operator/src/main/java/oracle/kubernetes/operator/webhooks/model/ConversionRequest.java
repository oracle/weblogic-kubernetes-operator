// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.annotations.Expose;

public class ConversionRequest {

  @Expose
  private String uid;

  @Expose
  private String desiredAPIVersion;

  @Expose
  // The domains to be converted. Note that the field name 'objects' is required by the conversion API.
  private List<Map<String,Object>> objects = new ArrayList<>();

  public String getUid() {
    return uid;
  }

  public void setUid(String uid) {
    this.uid = uid;
  }

  public String getDesiredAPIVersion() {
    return desiredAPIVersion;
  }

  /**
   * Returns a list domains to be converted by the webhook.
   */
  public List<Map<String,Object>> getDomains() {
    return objects;
  }

  @Override
  public String toString() {
    return "ConversionRequest{"
              + "uid='" + uid + '\''
              + ", desiredAPIVersion='" + desiredAPIVersion + '\''
              + ", objects=" + objects
              + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ConversionRequest that = (ConversionRequest) o;
    return Objects.equals(uid, that.uid)
              && Objects.equals(desiredAPIVersion, that.desiredAPIVersion)
              && Objects.equals(objects, that.objects);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uid, desiredAPIVersion, objects);
  }


}
