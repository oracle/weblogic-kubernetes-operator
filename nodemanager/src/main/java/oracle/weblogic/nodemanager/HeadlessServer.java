// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.weblogic.nodemanager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.util.logging.Level;
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
    nmLog.entering(HeadlessServer.class.toString(), "start");

    // Log start Message
    nmLog.info("Headless Node Manager for WebLogic on Kubernetes Started");
    
    String domainName = System.getenv("DOMAIN_NAME");
    String serverName = System.getenv("SERVER_NAME");

    PipedInputStream inputPipeIn = new PipedInputStream();
    PipedOutputStream inputPipeOut = new PipedOutputStream(inputPipeIn);

    PipedInputStream outputPipeIn = new PipedInputStream();
    PipedOutputStream outputPipeOut = new PipedOutputStream(outputPipeIn);

    InternalNMCommandHandler handler = new InternalNMCommandHandler(nmServer, outputPipeOut, inputPipeIn);
    
    Thread in = new Thread(() -> {
      nmLog.entering(HeadlessServer.class.toString(), "inputReader");
      
      try (PrintWriter pw = new PrintWriter(inputPipeOut)) {
        nmLog.info("Injecting command DOMAIN " + domainName);
        pw.write("DOMAIN ");
        pw.write(domainName);
        pw.flush();
        handler.runCommand();

        nmLog.info("Injecting command SERVER " + serverName);
        pw.write("SERVER ");
        pw.write(serverName);
        pw.flush();
        handler.runCommand();

        nmLog.info("Injecting command START");
        pw.write("START");
        pw.flush();
        handler.runCommand();
        
        nmLog.info("Injecting command DONE");
        pw.write("DONE");
        pw.flush();
        handler.runCommand();
      } finally {
        nmLog.exiting(HeadlessServer.class.toString(), "inputReader");
      }
    });
    in.setDaemon(true);
    in.start();
    
    Thread out = new Thread(() -> {
      nmLog.entering(HeadlessServer.class.toString(), "outputReader");
      
      try (BufferedReader br = new BufferedReader(new InputStreamReader(outputPipeIn))) {
        String line = null;
        while ((line = br.readLine()) != null) {
          nmLog.info("Command result: " + line);
        }
       
      } catch (IOException io) {
        nmLog.log(Level.SEVERE, "Exception reading command response", io);
      } finally {
        nmLog.exiting(HeadlessServer.class.toString(), "outputReader");
      }
    }) ;
    out.setDaemon(true);
    out.start();
    
    // Block this thread forever
    try {
      Thread.currentThread().join();
    } catch (InterruptedException e) {
    }
    
    nmLog.exiting(HeadlessServer.class.toString(), "start");
  }

  @Override
  public String supportedMode() {
    return "REST";
  }
}
