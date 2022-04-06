// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.util.Yaml;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static io.kubernetes.client.util.Yaml.dump;
import static oracle.weblogic.kubernetes.actions.TestActions.now;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;

/**
 * A utility class to collect logs about services and running processes when a port in use error occur. It spawns a
 * thread from ImageBuilders beforeAll method to start watching all events in all namespaces used by the integration
 * tests and will collect services logs and processes logs using lsof command. The thread runs every 10 seconds to look
 * for the port in use error and writes logs only when the error is thrown.
 *
 */
public class PortInuseEventWatcher extends Thread {

  static LoggingFacade logger = getLogger();
  OffsetDateTime timestamp;
  OffsetDateTime begin;
  OffsetDateTime end;
  int secondsBetweenCollection = 10;
  int collectionDelay = 0;
  String regex = "(?s).*\\bprovided\\b.*\\bport\\b.*\\bis\\b.*\\balready\\b.*\\ballocated\\b";

  @Override
  public void run() {
    logger.info("Starting port in use debugger thread");
    timestamp = now();
    while (!this.isInterrupted()) {
      timestamp = now().minusSeconds(collectionDelay);
      try {
        begin = now();
        List<String> ns = Kubernetes.listNamespaces();
        for (String n : ns) {
          if (n.startsWith("ns-") || n.equals("default")) {
            List<CoreV1Event> listNamespacedEvents = Kubernetes.listNamespacedEvents(n);
            for (CoreV1Event event : listNamespacedEvents) {
              if (event != null && event.getLastTimestamp() != null
                  && (event.getLastTimestamp().isEqual(timestamp)
                  || event.getLastTimestamp().isAfter(timestamp))
                  && event.getMessage() != null
                  && event.getMessage().matches(regex)) {
                logger.info("Port in use issue found in namespace {0}, "
                    + "collecting services objects across all namespaces....", n);
                logger.info(Yaml.dump(event));
                collectLogs(ns);
              }
            }
          }
        }
        end = now();
      } catch (ApiException ex) {
        Logger.getLogger(PortInuseEventWatcher.class.getName()).log(Level.SEVERE, null, ex);
      }
      try {
        TimeUnit.SECONDS.sleep(secondsBetweenCollection);
        collectionDelay = (int) (secondsBetweenCollection + Duration.between(begin, end).getSeconds());
      } catch (InterruptedException ex) {
        logger.info("Thread interrupted");
      }
    }
    logger.info("port in use debugger thread ended");
  }

  private void collectLogs(List<String> namespaces) {
    try {
      Path dest = Files.createDirectories(Paths.get(TestConstants.LOGS_DIR, "portinuselogs", now().toString()));
      //grab all processes using lsof
      ExecResult exec = ExecCommand.exec("sudo lsof -i");
      if (exec.exitValue() == 0 && exec.stdout() != null) {
        writeToFile(exec.stderr(), dest.toString(), "lsof", false);
      } else {
        logger.warning("Failed to collect lsof logs");
      }
      for (String ns : namespaces) {
        if (ns.startsWith("ns-") || ns.equals("default")) {
          writeToFile(Kubernetes.listServices(ns), dest.toString(), ns + ".list.services.log", true);
          writeToFile(Kubernetes.listNamespacedEvents(ns), dest.toString(), ns + ".list.events.log", true);
        }
      }
    } catch (IOException | InterruptedException | ApiException ex) {
      logger.warning(ex.getMessage());
    }
  }

  private static void writeToFile(Object obj, String resultDir, String fileName, boolean asYaml)
      throws IOException {
    logger.info("Generating {0}", Paths.get(resultDir, fileName));
    if (obj != null) {
      Files.createDirectories(Paths.get(resultDir));
      Files.write(Paths.get(resultDir, fileName),
          (asYaml ? dump(obj).getBytes(StandardCharsets.UTF_8) : ((String) obj).getBytes(StandardCharsets.UTF_8))
      );
    } else {
      logger.info("Nothing to write in {0} list is empty", Paths.get(resultDir, fileName));
    }
  }
}
