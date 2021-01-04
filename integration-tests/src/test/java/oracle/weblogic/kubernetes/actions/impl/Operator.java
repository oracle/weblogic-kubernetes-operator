// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.Optional;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Helm;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;

import static oracle.weblogic.kubernetes.TestConstants.BRANCH_NAME_FROM_JENKINS;
import static oracle.weblogic.kubernetes.TestConstants.BUILD_ID;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_NAME_OPERATOR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_DOCKER_BUILD_SCRIPT;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.getContainerImage;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.patchDeployment;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;

/**
 * Action class with implementation methods for Operator.
 */
public class Operator {

  /**
   * install helm chart.
   * @param params the helm parameters like namespace, release name, repo url or chart dir,
   *               chart name and chart values to override
   * @return true on success, false otherwise
   */
  public static boolean install(OperatorParams params) {
    return Helm.install(params.getHelmParams(), params.getValues());
  }

  /**
   * Upgrade a helm release.
   * @param params the helm parameters like namespace, release name, repo url or chart dir,
   *               chart name and chart values to override
   * @return true on success, false otherwise
   */
  public static boolean upgrade(OperatorParams params) {
    return Helm.upgrade(params.getHelmParams(), params.getValues());
  }

  /**
   * Uninstall a helm release.
   * @param params the parameters to helm uninstall command, release name and namespace
   * @return true on success, false otherwise
   */
  public static boolean uninstall(HelmParams params) {
    return Helm.uninstall(params);
  }


  /**
   * Image Name for the Operator. Uses branch name for tag in local runs
   * and branch name, build id for tag in Jenkins runs.
   * @return image name
   */
  public static String getImageName() {
    String image = "";
    String imageName = Optional.ofNullable(System.getenv("IMAGE_NAME_OPERATOR"))
        .orElse(IMAGE_NAME_OPERATOR);
    // use branch name and build id for Jenkins runs in image tag
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      imageName = DOMAIN_IMAGES_REPO + imageName;
    }
    String branchName = "";
    if (!BUILD_ID.isEmpty()) {
      branchName = BRANCH_NAME_FROM_JENKINS;
      // Ensure that the branch name can be used as a part of Docker tag by replacing illegal characters
      branchName = branchName.codePoints()
          .map(cp -> Character.isLetterOrDigit(cp) ? cp : '-')
          .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
          .toString();
    } else  {
      CommandParams params = Command.defaultCommandParams()
          .command("git branch | grep \\* | cut -d ' ' -f2-")
          .saveResults(true)
          .redirect(false);

      if (Command.withParams(params)
          .execute()) {
        branchName = params.stdout();
      }
    }
    String imageTag = Optional.ofNullable(System.getenv("IMAGE_TAG_OPERATOR"))
        .orElse(branchName + BUILD_ID);
    image = imageName + ":" + imageTag;
    return image;
  }

  /**
   * Builds a Docker Image for the Oracle WebLogic Kubernetes Operator.
   * @param image image name and tag in 'name:tag' format
   * @return true on success
   */
  public static boolean buildImage(String image) {
    String command = String.format("%s -t %s", OPERATOR_DOCKER_BUILD_SCRIPT, image);
    return new Command()
        .withParams(new CommandParams()
            .command(command))
        .execute();
  }

  /**
   * Get the container's image in the pod.
   * @param namespace name of the pod's namespace
   * @return image used for the container
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String getOperatorContainerImage(String namespace) throws ApiException {
    return getContainerImage(namespace, "weblogic-operator-",
        String.format("weblogic.operatorName in (%s)", namespace), null);
  }

  /**
   * Stop operator by changing the replica in the operator deployment to 0.
   * @param namespace namespace of the operator
   * @return true on success
   */
  public static boolean stop(String namespace) {
    // change the /spec/replicas to 0 to stop the operator
    return patchReplicas(0, namespace);
  }

  /**
   * Start operator by changing the replica in the operator deployment to 1.
   * @param namespace namespace of the operator
   * @return true on success
   */
  public static boolean start(String namespace) {
    // change the /spec/replicas to 1 to start the operator
    return patchReplicas(1, namespace);
  }

  private static boolean patchReplicas(int replicaCount, String namespace) {
    StringBuffer patchStr = new StringBuffer("[{")
        .append("\"op\": \"replace\", ")
        .append("\"path\": \"/spec/replicas\", ")
        .append("\"value\": ")
        .append(replicaCount)
        .append("}]");

    getLogger().info("Stop/Start Operator in namespace {0} using patch string: {1}",
        namespace, patchStr.toString());

    V1Patch patch = new V1Patch(new String(patchStr));
    return patchDeployment(OPERATOR_RELEASE_NAME, namespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
  }

}
