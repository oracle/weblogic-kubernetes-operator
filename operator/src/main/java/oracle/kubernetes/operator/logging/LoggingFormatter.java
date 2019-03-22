// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.JSON;
import io.swagger.annotations.ApiModel;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import oracle.kubernetes.operator.work.Fiber;

/** Custom log formatter to format log messages in JSON format. */
public class LoggingFormatter extends Formatter {
  private static final Map<String, List<String>> PLACEHOLDER = new HashMap<String, List<String>>();

  private static final String LOG_LEVEL = "level";
  private static final String TIMESTAMP = "timestamp";
  private static final String THREAD = "thread";
  private static final String FIBER = "fiber";
  private static final String SOURCE_CLASS = "class";
  private static final String SOURCE_METHOD = "method";
  private static final String TIME_IN_MILLIS = "timeInMillis";
  private static final String MESSAGE = "message";
  private static final String EXCEPTION = "exception";
  private static final String DATE_FORMAT = "MM-dd-yyyy'T'HH:mm:ss.SSSZZ";

  // For ApiException
  private static final String RESPONSE_CODE = "code";
  private static final String RESPONSE_HEADERS = "headers";
  private static final String RESPONSE_BODY = "body";

  private final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);

  @Override
  public String format(LogRecord record) {
    String sourceClassName = "";
    String sourceMethodName = "";
    if (record.getSourceClassName() != null) {
      sourceClassName = record.getSourceClassName();
      if (record.getSourceMethodName() != null) {
        sourceMethodName = record.getSourceMethodName();
      }
    } else {
      sourceClassName = record.getLoggerName();
    }

    // the toString() format for the model classes is inappropriate for our logs
    // so, replace with the JSON serialization
    JSON j = LoggingFactory.getJSON();
    if (j != null) {
      Object[] parameters = record.getParameters();
      if (parameters != null) {
        for (int i = 0; i < parameters.length; i++) {
          Object pi = parameters[i];
          if (pi != null) {
            if (pi.getClass().getAnnotation(ApiModel.class) != null
                || pi.getClass().getName().startsWith("oracle.kubernetes.weblogic.domain.")) {
              // this is a model object
              parameters[i] = j.serialize(pi);
            }
          }
        }
      }
    }

    String message = formatMessage(record);
    String code = "";
    Map<String, List<String>> headers = PLACEHOLDER;
    String body = "";
    String throwable = "";
    if (record.getThrown() != null) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      pw.println();
      record.getThrown().printStackTrace(pw);
      pw.close();
      throwable = sw.toString();
      if (record.getThrown() instanceof ApiException) {
        ApiException ae = (ApiException) record.getThrown();
        code = String.valueOf(ae.getCode());
        if (ae.getResponseHeaders() != null) {
          headers = ae.getResponseHeaders();
        }
        String rb = ae.getResponseBody();
        if (rb != null) {
          body = rb;
        }
      }
    }
    String level = record.getLevel().getLocalizedName();
    Map<String, Object> map = new LinkedHashMap<>();
    long rawTime = record.getMillis();
    final String dateString = dateFormat.format(new Date(rawTime));
    long thread = Thread.currentThread().getId();
    Fiber fiber = Fiber.getCurrentIfSet();

    map.put(TIMESTAMP, dateString);
    map.put(THREAD, thread);
    map.put(FIBER, fiber != null ? fiber.toString() : "");
    map.put(LOG_LEVEL, level);
    map.put(SOURCE_CLASS, sourceClassName);
    map.put(SOURCE_METHOD, sourceMethodName);
    map.put(TIME_IN_MILLIS, rawTime);
    // if message or throwable have new lines in them, we need to replace with JSON newline control
    // character \n
    map.put(MESSAGE, message != null ? message.replaceAll("\n", "\\\n") : "");
    map.put(EXCEPTION, throwable.replaceAll("\n", "\\\n"));
    map.put(RESPONSE_CODE, code);
    map.put(RESPONSE_HEADERS, headers);
    map.put(RESPONSE_BODY, body.replaceAll("\n", "\\\n"));
    String json = "";
    try {
      ObjectMapper mapper = new ObjectMapper();
      json = mapper.writeValueAsString(map);

    } catch (JsonProcessingException e) {
      String tmp =
          "{\"@timestamp\":%1$s,\"level\":%2$s, \"class\":%3$s, \"method\":\"format\", \"timeInMillis\":%4$d, \"@message\":\"Exception while preparing json object\",\"exception\":%5$s}\n";
      return String.format(
          tmp,
          dateString,
          level,
          LoggingFormatter.class.getName(),
          rawTime,
          e.getLocalizedMessage());
    }
    return json + "\n";
  }
}
