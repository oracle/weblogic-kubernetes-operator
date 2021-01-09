// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.builders;

/** An object which encapsulates common parameters for Kubernetes API calls. */
public class CallParamsImpl implements CallParams {
  private static final int DEFAULT_LIMIT = 50;
  private static final int DEFAULT_TIMEOUT = 30;

  private Integer limit = CallParamsImpl.DEFAULT_LIMIT;
  private Integer timeoutSeconds = CallParamsImpl.DEFAULT_TIMEOUT;
  private String fieldSelector;
  private String labelSelector;
  private String pretty;
  private String resourceVersion;

  @Override
  public Integer getLimit() {
    return limit;
  }

  public void setLimit(Integer limit) {
    this.limit = limit;
  }

  @Override
  public Integer getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public void setTimeoutSeconds(Integer timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
  }

  @Override
  public String getFieldSelector() {
    return fieldSelector;
  }

  void setFieldSelector(String fieldSelector) {
    this.fieldSelector = fieldSelector;
  }

  @Override
  public String getLabelSelector() {
    return labelSelector;
  }

  void setLabelSelector(String labelSelector) {
    this.labelSelector = labelSelector;
  }

  @Override
  public String getPretty() {
    return pretty;
  }

  @Override
  public String getResourceVersion() {
    return resourceVersion;
  }

  void setResourceVersion(String resourceVersion) {
    this.resourceVersion = resourceVersion;
  }
}
