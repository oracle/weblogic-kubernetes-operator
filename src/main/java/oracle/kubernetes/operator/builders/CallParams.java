package oracle.kubernetes.operator.builders;

import io.kubernetes.client.ProgressRequestBody;
import io.kubernetes.client.ProgressResponseBody;

public class CallParams {
    private static final int DEFAULT_LIMIT = 500;
    private static final int DEFAULT_TIMEOUT = 30;

    private Boolean includeUninitialized;
    private Integer limit = CallParams.DEFAULT_LIMIT;
    private Integer timeoutSeconds = CallParams.DEFAULT_TIMEOUT;
    private String fieldSelector;
    private String labelSelector;
    private String pretty;
    private String resourceVersion;
    private ProgressResponseBody.ProgressListener progressListener;
    private ProgressRequestBody.ProgressRequestListener progressRequestListener;

    public Boolean getIncludeUninitialized() {
        return includeUninitialized;
    }

    public Integer getLimit() {
        return limit;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public String getFieldSelector() {
        return fieldSelector;
    }

    public String getLabelSelector() {
        return labelSelector;
    }

    public String getPretty() {
        return pretty;
    }

    public String getResourceVersion() {
        return resourceVersion;
    }

    public ProgressResponseBody.ProgressListener getProgressListener() {
        return progressListener;
    }

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

    void setProgressRequestListener(ProgressRequestBody.ProgressRequestListener progressRequestListener) {
        this.progressRequestListener = progressRequestListener;
    }
}
