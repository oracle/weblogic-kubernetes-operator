// Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.

package oracle.kubernetes.operator.utils;

import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;

public class LoggerHelper {
  private final static ThreadLocal<Logger> localLogger = new ThreadLocal<Logger>();
  private final static Logger globalLogger = Logger.getLogger("GLOBAL", "OperatorIT");
  
  static {
    globalLogger.addHandler(new ConsoleHandler());
    
  }
  
  public static void closeLocal() {
    // TODO flush the local logger, if it exists, then close it, then finally call initLocal(null)
    initLocal(null);
  }
  
  /**
   * 
   * @param cl
   */
  public static void initLocal(Logger cl) {
    //cl.setUseParentHandlers(false);
    //cl.addHandler(new ConsoleHandler());
    localLogger.set(cl);
    
  }
  
  /**
   * 
   * @return
   */
  public static Logger getLocal() {
    Logger cl = localLogger.get();
    if (cl == null) return globalLogger;
    return cl;
  }
  
  public static Logger getGlobal() {
    return globalLogger;
  }
}
