// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.weblogic.nodemanager;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.util.logging.Logger;

import weblogic.nodemanager.server.InternalNMCommandHandler;
import weblogic.nodemanager.server.NMServer;
import weblogic.nodemanager.server.Server;

public class HeadlessServer implements Server {
  public static final Logger nmLog = Logger.getLogger("weblogic.nodemanager");

  @Override
  public void init(NMServer nmServer) throws IOException {
  }

  @Override
  public void start(NMServer nmServer) throws IOException {
    // Log start Message
    nmLog.info("Headless Node Manager for WebLogic on Kubernetes Started");
    
    String domainName = System.getenv("DOMAIN_NAME");
    String serverName = System.getenv("SERVER_NAME");

    PipedInputStream pipeIn = new PipedInputStream();
    PipedOutputStream pipeOut = new PipedOutputStream(pipeIn);

    InternalNMCommandHandler handler = new InternalNMCommandHandler(nmServer, System.out, pipeIn);
    
    Thread in = new Thread(() -> {
      try (PrintWriter pw = new PrintWriter(pipeOut)) {
        pw.write("DOMAIN ");
        pw.write(domainName);
        pw.flush();
        handler.runCommand();

        pw.write("SERVER ");
        pw.write(serverName);
        pw.flush();
        handler.runCommand();

        pw.write("START");
        pw.flush();
        handler.runCommand();
}
    });
    in.setDaemon(true);
    in.start();
    
    // Block this thread forever
    try {
      Thread.currentThread().join();
    } catch (InterruptedException e) {
    }
  }

  @Override
  public String supportedMode() {
    return "REST";
  }
}
