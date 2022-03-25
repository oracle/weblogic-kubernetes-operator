// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.common.logging;

import java.util.logging.Formatter;
import java.util.logging.Logger;

/** Centralized common logging. */
public class CommonLoggingFacade extends BaseLoggingFacade {

  /**
   * Construct logging facade.
   *
   * @param logger logger
   */
  public CommonLoggingFacade(Logger logger) {
    super(logger);
  }

  @Override
  protected Formatter getLoggingFormatter() {
    return new CommonLoggingFormatter();
  }
}
