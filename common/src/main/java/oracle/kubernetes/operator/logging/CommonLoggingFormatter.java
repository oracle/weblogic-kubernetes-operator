// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

import java.util.logging.LogRecord;

/** Common log formatter to format log messages in JSON format. */
public class CommonLoggingFormatter extends BaseLoggingFormatter<Object> {

  @Override
  Object getCurrentFiberIfSet() {
    return null;
  }

  @Override
  String getFiber() {
    return null;
  }

  @Override
  String getDomainUid(Object fiber) {
    return null;
  }

  @Override
  void processThrowable(LogRecord logRecord, ThrowableProcessing throwableProcessing) {
    // no op
  }

  @Override
  String getNamespace(Object fiber) {
    return null;
  }

  @Override
  void serializeModelObjectsWithJSON(LogRecord logRecord) {
    // no op
  }
}
