// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.gson.annotations.Expose;

public class ConversionResponse {

  @Expose
  private String uid;
  @Expose
  private List<Object> convertedObjects = new ArrayList<>();
  @Expose
  private Result result;

  public String getUid() {
    return uid;
  }

  public ConversionResponse uid(String uid) {
    this.uid = uid;
    return this;
  }

  public List<Object> getConvertedObjects() {
    return convertedObjects;
  }

  public ConversionResponse convertedObjects(List<Object> convertedObjects) {
    this.convertedObjects = convertedObjects;
    return this;
  }

  public Result getResult() {
    return result;
  }


  public ConversionResponse result(Result result) {
    this.result = result;
    return this;
  }

  @Override
  public String toString() {
    return "ConversionResponse{"
            + "uid='" + uid + '\''
            + ", convertedObjects=" + convertedObjects
            + ", result=" + result
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
    ConversionResponse that = (ConversionResponse) o;
    return Objects.equals(uid, that.uid)
            && Objects.equals(convertedObjects, that.convertedObjects)
            && Objects.equals(result, that.result);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uid, convertedObjects, result);
  }
}
