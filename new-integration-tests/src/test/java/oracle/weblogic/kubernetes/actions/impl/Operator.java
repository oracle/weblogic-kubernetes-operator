// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.Optional;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Helm;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.logging.LoggingFactory;

import static oracle.weblogic.kubernetes.TestConstants.BRANCH_NAME_FROM_JENKINS;
import static oracle.weblogic.kubernetes.TestConstants.BUILD_ID;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_NAME_OPERATOR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_DOCKER_BUILD_SCRIPT;
import static oracle.weblogic.kubernetes.TestConstants.REPO_NAME;

/**
 * Action class with implementation methods for Operator.
 */
public class Operator {
  private static final LoggingFacade logger = LoggingFactory.getLogger(Operator.class);

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
    if (!REPO_NAME.isEmpty()) {
      imageName = REPO_NAME + imageName;
    }
    String branchName = "";
    if (!BUILD_ID.isEmpty()) {
      branchName = BRANCH_NAME_FROM_JENKINS;
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
}
