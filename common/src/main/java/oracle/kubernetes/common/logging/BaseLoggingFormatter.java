// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.common.logging;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Custom log formatter to format log messages in JSON format. */
public abstract class BaseLoggingFormatter<T> extends Formatter {
  private static final Map<String, List<String>> PLACEHOLDER = new HashMap<>();

  private static final String LOG_LEVEL = "level";
  private static final String TIMESTAMP = "timestamp";
  private static final String THREAD = "thread";
  private static final String FIBER = "fiber";
  private static final String DOMAIN_UID = "domainUID";
  private static final String DOMAIN_NAMESPACE = "namespace";
  private static final String SOURCE_CLASS = "class";
  private static final String SOURCE_METHOD = "method";
  private static final String TIME_IN_MILLIS = "timeInMillis";
  private static final String MESSAGE = "message";
  private static final String EXCEPTION = "exception";

  // For ApiException
  private static final String RESPONSE_CODE = "code";
  private static final String RESPONSE_HEADERS = "headers";
  private static final String RESPONSE_BODY = "body";

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

  protected T fiberObject = null;

  @Override
  public String format(LogRecord logRecord) {
    String sourceClassName = "";
    String sourceMethodName = "";
    if (logRecord.getSourceClassName() != null) {
      sourceClassName = logRecord.getSourceClassName();
      if (logRecord.getSourceMethodName() != null) {
        sourceMethodName = logRecord.getSourceMethodName();
      }
    } else {
      sourceClassName = logRecord.getLoggerName();
    }

    serializeModelObjectsWithJSON(logRecord);

    final String message = formatMessage(logRecord);
    String code = "";
    Map<String, List<String>> headers = PLACEHOLDER;
    String body = "";
    String throwable = "";
    ThrowableProcessing throwableProcessing = new ThrowableProcessing(logRecord, code, headers, body, throwable);
    throwableProcessing.invoke();
    code = throwableProcessing.getCode();
    headers = throwableProcessing.getHeaders();
    body = throwableProcessing.getBody();
    throwable = throwableProcessing.getThrowable();
    String level = logRecord.getLevel().getLocalizedName();
    Map<String, Object> map = new LinkedHashMap<>();
    long rawTime = logRecord.getMillis();
    final String dateString = DATE_FORMAT.format(OffsetDateTime.ofInstant(logRecord.getInstant(),
            ZoneId.systemDefault()));
    long thread = Thread.currentThread().getId();
    fiberObject = getCurrentFiberIfSet();

    map.put(TIMESTAMP, dateString);
    map.put(THREAD, thread);
    Optional.ofNullable(getFiber()).ifPresent(f -> map.put(FIBER, f));
    Optional.ofNullable(getNamespace(fiberObject)).ifPresent(namespace -> map.put(DOMAIN_NAMESPACE, namespace));
    Optional.ofNullable(getDomainUid(fiberObject)).ifPresent(uid -> map.put(DOMAIN_UID, uid));
    map.put(LOG_LEVEL, level);
    map.put(SOURCE_CLASS, sourceClassName);
    map.put(SOURCE_METHOD, sourceMethodName);
    map.put(TIME_IN_MILLIS, rawTime);
    // if message or throwable have new lines in them, we need to replace with JSON newline control
    // character \n
    map.put(MESSAGE, message != null ? message.replace("\n", "\\\n") : "");
    map.put(EXCEPTION, throwable.replace("\n", "\\\n"));
    map.put(RESPONSE_CODE, code);
    map.put(RESPONSE_HEADERS, headers);
    map.put(RESPONSE_BODY, body.replace("\n", "\\\n"));
    String json = "";
    try {
      ObjectMapper mapper = new ObjectMapper();
      json = mapper.writeValueAsString(map);

    } catch (JsonProcessingException e) {
      String tmp =
          "{\"@timestamp\":%1$s,\"level\":%2$s, \"class\":%3$s, \"method\":\"format\", \"timeInMillis\":%4$d, "
              + "\"@message\":\"Exception while preparing json object\",\"exception\":%5$s}\n";
      return String.format(
          tmp,
          dateString,
          level,
          BaseLoggingFormatter.class.getName(),
          rawTime,
          e.getLocalizedMessage());
    }
    return json + "\n";
  }

  protected abstract void serializeModelObjectsWithJSON(LogRecord logRecord);

  protected abstract T getCurrentFiberIfSet();

  protected abstract String getFiber();

  protected abstract String getNamespace(T fiber);

  protected abstract String getDomainUid(T fiber);

  protected abstract void processThrowable(LogRecord logRecord, ThrowableProcessing throwableProcessing);

  protected class ThrowableProcessing {
    private LogRecord logRecord;
    private String code;
    private Map<String, List<String>> headers;
    private String body;
    private String throwable;

    private ThrowableProcessing(LogRecord logRecord, String code, Map<String, List<String>> headers, String body,
                               String throwable) {
      this.logRecord = logRecord;
      this.code = code;
      this.headers = headers;
      this.body = body;
      this.throwable = throwable;
    }

    private String getCode() {
      return code;
    }

    public void setCode(String code) {
      this.code = code;
    }

    private Map<String, List<String>> getHeaders() {
      return headers;
    }

    public void setHeaders(Map<String, List<String>> headers) {
      this.headers = headers;
    }

    private String getBody() {
      return body;
    }

    public void setBody(String body) {
      this.body = body;
    }

    private String getThrowable() {
      return throwable;
    }

    public void setThrowable(String throwable) {
      this.throwable = throwable;
    }

    private void invoke() {
      processThrowable(logRecord, this);
    }

  }
}
