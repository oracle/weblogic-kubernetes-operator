// Copyright (c) 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;

import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;

/**
 * Patch an OKE LoadBalancer Service with OCI freeform tags ONLY if the annotation does not already exist.
 */
public class OciLbServiceTagger {

  private static final String FREEFORM_TAG_KEY
      = "oci.oraclecloud.com/freeform-tags";

  /**
   * Patch an OKE LoadBalancer Service with OCI freeform tags ONLY if the annotation does not already exist.
   */
  public static void patchServiceIfMissing(
      String namespace,
      String serviceName,
      String clusterName
  ) throws IOException, InterruptedException   {

    // 1. Read service JSON
    String svcJson = exec(
        KUBERNETES_CLI + " get service " + serviceName + " -n " + namespace + "-o json"
    );

    // 2. Check if annotation already exists
    if (svcJson.contains(FREEFORM_TAG_KEY)) {
      System.out.println("[DEBUG] Service already contains freeform-tags annotation, skipping patch");
      return;
    }

    // 3. Build JSON annotation value
    String freeformTagsJson = String.format(
        "{ \"oke.cluster.name\": \"%s\" }",
        clusterName
    );

    // 4. Patch service
    String patchPayload = String.format(
        "{ \"metadata\": { \"annotations\": { \"%s\": \"%s\" } } }",
        FREEFORM_TAG_KEY,
        freeformTagsJson.replace("\"", "\\\"")
    );

    System.out.println("[DEBUG] Patching service " + serviceName
        + " in namespace " + namespace);

    exec(
        KUBERNETES_CLI + " patch service "
        + serviceName
        + " -n "
        + namespace
        + " --type=merge -p "
        + patchPayload
    );

    System.out.println("[INFO] Freeform tags applied successfully");
  }

  /**
   * Execute a shell command and return stdout. Throws exception on non-zero exit.
   */
  private static String exec(String command) throws IOException, InterruptedException {
    return ExecCommand.exec(command, true).stdout();
  }
}
