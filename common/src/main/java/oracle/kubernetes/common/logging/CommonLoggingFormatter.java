// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.common.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.LogRecord;

/** Common log formatter to format log messages in JSON format. */
public class CommonLoggingFormatter extends BaseLoggingFormatter<Object> {

  @Override
  protected Object getCurrentFiberIfSet() {
    return null;
  }

  @Override
  protected String getFiber() {
    return null;
  }

  @Override
  protected String getDomainUid(Object fiber) {
    return null;
  }

  @Override
  protected void processThrowable(LogRecord logRecord, ThrowableProcessing throwableProcessing) {
    if (logRecord.getThrown() != null) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      pw.println();
      logRecord.getThrown().printStackTrace(pw);
      pw.close();
      throwableProcessing.setThrowable(sw.toString());
    }
  }

  @Override
  protected String getNamespace(Object fiber) {
    return null;
  }

  @Override
  protected void serializeModelObjectsWithJSON(LogRecord logRecord) {
    // no op
  }
}
