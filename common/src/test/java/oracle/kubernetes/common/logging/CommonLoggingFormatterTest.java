// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.common.logging;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class CommonLoggingFormatterTest {

  private final LogRecord logRecord = new LogRecord(Level.INFO, "A simple one");
  private final CommonLoggingFormatter formatter = new CommonLoggingFormatter();

  @Test
  void formatLogRecordWithParameters() throws JsonProcessingException {
    logRecord.setMessage("Insert {0} and {1}");
    logRecord.setParameters(new Object[]{"here", "there"});

    assertThat(getFormattedMessage().get("message"), equalTo("Insert here and there"));
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> getFormattedMessage() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.readValue(formatter.format(logRecord), Map.class);
  }

  @Test
  void extractLogLevel() throws JsonProcessingException {
    logRecord.setLevel(Level.FINER);
    assertThat(getFormattedMessage().get("level"), equalTo("FINER"));

    logRecord.setLevel(Level.WARNING);
    assertThat(getFormattedMessage().get("level"), equalTo("WARNING"));
  }

  @Test
  void extractSourceIndicators() throws JsonProcessingException {
    logRecord.setSourceClassName("theClass");
    logRecord.setSourceMethodName("itsMethod");

    assertThat(getFormattedMessage().get("class"), equalTo("theClass"));
    assertThat(getFormattedMessage().get("method"), equalTo("itsMethod"));
  }

  @Test
  void whenThrowableNotApiException_extractItsName() throws JsonProcessingException {
    logRecord.setThrown(new RuntimeException("in the test"));

    assertThat(getFormattedMessage().get("exception"), containsString("java.lang.RuntimeException: in the test"));
  }
}
