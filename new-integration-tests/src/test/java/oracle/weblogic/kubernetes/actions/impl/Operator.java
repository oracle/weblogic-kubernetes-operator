// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.actions.impl.primitive.Helm;
import oracle.weblogic.kubernetes.extensions.LoggedTest;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Operator implements LoggedTest {

  /**
   * The URL of the Operator's Helm Repository.
   */
  private static String OPERATOR_HELM_REPO_URL = "https://oracle.github.io/weblogic-kubernetes-operator/charts";

  /**
   * The name of the Operator's Helm Chart (in the repository).
   */
  private static String OPERATOR_CHART_NAME = "weblogic-operator/weblogic-operator";

  /**
   * Install WebLogic Kubernetes Operator.
   *
   * @param params operator parameters for helm values
   * @return true if the operator is successfully installed, false otherwise.
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static boolean install(OperatorParams params) throws ApiException {

    String namespace = params.getNamespace();
    String serviceAccount = params.getServiceAccount();

    // assertions for required parameters
    assertNotNull(namespace, "Operator namespace cannot be null");
    assertNotNull(params.getReleaseName(), "Operator release name cannot be null");
    assertNotEquals("",namespace.trim(), "Operator namespace cannot be empty string");
    assertNotEquals("",params.getReleaseName().trim(), "Operator release name cannot be empty string");

    //delete the namespace if it exists
    if (Namespace.exists(namespace)) {
      try {
        Namespace.delete(namespace);
      } catch (Exception ex) {
        // TODO: Fix as there is a known bug that delete can return either the object
        //  just deleted or a status.  We can workaround by either retrying or using
        //  the general GenericKubernetesApi client class and doing our own type checks
      }
    }

    // create namespace
    logger.info(String.format("Creating namespace %s", namespace));
    Namespace ns = new Namespace().name(namespace);

    // create service account
    if (serviceAccount != null && !serviceAccount.equals("default")) {
      logger.info(String.format("Creating service account %s", serviceAccount));
      // Kubernetes.createServiceAccount(params.getServiceAccount(), namespace);
    }

    // create domain namespaces here?

    boolean success = false;
    if (new Helm.HelmBuilder(OPERATOR_HELM_REPO_URL).build().addRepo()) {
      logger.info(String.format("Installing Operator in namespace %s", namespace));
      success = new Helm.HelmBuilder(OPERATOR_CHART_NAME, params.getReleaseName())
          .namespace(namespace)
          .values(params.values())
          .build().install();
    }
    return success;
  }

  public static boolean upgrade(OperatorParams params) {
    return true;
  }

  public static boolean scaleDomain(String domainUID, String clusterName, int numOfServers) {
    return true;
  }

  public static boolean delete(String releaseName, String namespace) {
    return true;
  }
}
