// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.actions.impl.primitive.Helm;
import oracle.weblogic.kubernetes.extensions.LoggedTest;

import static org.assertj.core.api.Assertions.assertThat;

public class Operator implements LoggedTest {

  /**
   * The URL of the Operator's Helm Repository.
   */
  //Question - is this URL correct?
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
    assertThat(namespace)
        .as("make sure namespace is not empty or null")
        .isNotNull()
        .isNotEmpty();

    assertThat(params.getReleaseName())
        .as("make sure releaseName is not empty or null")
        .isNotNull()
        .isNotEmpty();

    boolean success = false;
    if (new Helm().chartName(OPERATOR_CHART_NAME).repoUrl(OPERATOR_HELM_REPO_URL).addRepo()) {
      logger.info(String.format("Installing Operator in namespace %s", namespace));
      success = new Helm().chartName(OPERATOR_CHART_NAME)
                        .releaseName(params.getReleaseName())
                        .namespace(namespace)
                        .values(params.getValues())
                        .install();
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
