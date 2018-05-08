// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.builders;

import io.kubernetes.client.ProgressRequestBody;
import io.kubernetes.client.ProgressResponseBody;

/** An object which encapsulates common parameters for Kubernetes API calls. */
class CallParamsImpl implements CallParams {
  private static final int DEFAULT_LIMIT = 500;
  private static final int DEFAULT_TIMEOUT = 30;

  private Boolean includeUninitialized;
  private Integer limit = CallParamsImpl.DEFAULT_LIMIT;
  private Integer timeoutSeconds = CallParamsImpl.DEFAULT_TIMEOUT;
  private String fieldSelector;
  private String labelSelector;
  private String pretty;
  private String resourceVersion;
  private ProgressResponseBody.ProgressListener progressListener;
  private ProgressRequestBody.ProgressRequestListener progressRequestListener;

  @Override
  public Boolean getIncludeUninitialized() {
    return includeUninitialized;
  }

  @Override
  public Integer getLimit() {
    return limit;
  }

  @Override
  public Integer getTimeoutSeconds() {
    return timeoutSeconds;
  }

  @Override
  public String getFieldSelector() {
    return fieldSelector;
  }

  @Override
  public String getLabelSelector() {
    return labelSelector;
  }

  @Override
  public String getPretty() {
    return pretty;
  }

  @Override
  public String getResourceVersion() {
    return resourceVersion;
  }

  @Override
  public ProgressResponseBody.ProgressListener getProgressListener() {
    return progressListener;
  }

  @Override
  public ProgressRequestBody.ProgressRequestListener getProgressRequestListener() {
    return progressRequestListener;
  }

  void setIncludeUninitialized(Boolean includeUninitialized) {
    this.includeUninitialized = includeUninitialized;
  }

  void setLimit(Integer limit) {
    this.limit = limit;
  }

  void setTimeoutSeconds(Integer timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
  }

  void setFieldSelector(String fieldSelector) {
    this.fieldSelector = fieldSelector;
  }

  void setLabelSelector(String labelSelector) {
    this.labelSelector = labelSelector;
  }

  void setPretty(String pretty) {
    this.pretty = pretty;
  }

  void setResourceVersion(String resourceVersion) {
    this.resourceVersion = resourceVersion;
  }

  void setProgressListener(ProgressResponseBody.ProgressListener progressListener) {
    this.progressListener = progressListener;
  }

  void setProgressRequestListener(
      ProgressRequestBody.ProgressRequestListener progressRequestListener) {
    this.progressRequestListener = progressRequestListener;
  }
}
