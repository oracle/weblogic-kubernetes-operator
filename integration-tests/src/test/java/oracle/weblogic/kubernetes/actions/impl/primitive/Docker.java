// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;


import java.util.Base64;

import com.google.gson.JsonObject;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;

import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;

/**
 * Class with calls to Docker CLI.
 */
public class Docker {
  /**
   * Log in to a Docker registry.
   * @param registryName registry name
   * @param username user
   * @param password password
   * @return true if successful
   */
  public static boolean login(String registryName, String username, String password) {
    String cmdToExecute = String.format("docker login %s -u %s -p \"%s\"",
        registryName, username, password);
    return new Command()
        .withParams(new CommandParams()
            .verbose(false)
            .command(cmdToExecute))
        .execute();
  }

  /**
   * Pull an image from a registry.
   * @param image image
   * @return true if successful
   */
  public static boolean pull(String image) {
    String cmdToExecute = String.format("docker pull %s", image);
    return new Command()
        .withParams(new CommandParams()
            .command(cmdToExecute))
        .execute();
  }

  /**
   * Push an image to a registry.
   * @param image image
   * @return true if successful
   */
  public static boolean push(String image) {
    String cmdToExecute = String.format("docker push %s", image);
    return new Command()
        .withParams(new CommandParams()
            .command(cmdToExecute))
        .execute();
  }

  /**
   * Tag an image.
   * @param originalImage original image
   * @param taggedImage tagged image
   * @return true if successful
   */
  public static boolean tag(String originalImage, String taggedImage) {
    String cmdToExecute = String.format("docker tag %s %s", originalImage, taggedImage);
    return new Command()
        .withParams(new CommandParams()
            .command(cmdToExecute))
        .execute();
  }

  /**
   * Delete docker image.
   * @param image image name:image tag
   * @return true if delete image is successful
   */
  public static boolean deleteImage(String image) {
    String cmdToExecute = String.format("docker rmi -f %s", image);
    return new Command()
        .withParams(new CommandParams()
                  .command(cmdToExecute))
        .execute();
  }

  /**
   * Create docker image.
   * @param image image name:image tag
   * @param dockerFileDir path to Dockerfile directory
   * @return true if delete image is successful
   */
  public static boolean createImage(String dockerFileDir, String image) {
    return createImage(dockerFileDir, image, "");
  }

  /**
   * Create docker image.
   * @param image image name:image tag
   * @param dockerFileDir path to Dockerfile directory
   * @param extraArgs extra args to pass
   * @return true if delete image is successful
   */
  public static boolean createImage(String dockerFileDir, String image, String extraArgs) {
    String cmdToExecute = String.format("docker build %s -t %s  %s", dockerFileDir, image, extraArgs);
    return new Command()
        .withParams(new CommandParams()
            .command(cmdToExecute))
        .execute();
  }

  /**
   * Create Docker registry configuration in json object.
   * @param username username for the Docker registry
   * @param password password for the Docker registry
   * @param email email for the Docker registry
   * @param registry Docker registry name
   * @return json object for the Docker registry configuration
   */
  public static JsonObject createDockerConfigJson(String username, String password, String email, String registry) {
    JsonObject authObject = new JsonObject();
    authObject.addProperty("username", username);
    authObject.addProperty("password", password);
    authObject.addProperty("email", email);

    String auth = username + ":" + password;
    String authEncoded = Base64.getEncoder().encodeToString(auth.getBytes());
    authObject.addProperty("auth", authEncoded);

    JsonObject registryObject = new JsonObject();
    registryObject.add(registry, authObject);

    JsonObject configJsonObject = new JsonObject();
    configJsonObject.add("auths", registryObject);
    return configJsonObject;
  }

  /**
   * Get environment variable from docker image.
   * @param imageName image name
   * @param envVarName environment variable name
   * @return environment variable in the image
   */
  public static String getImageEnvVar(String imageName, String envVarName) {
    LoggingFacade logger = getLogger();
    String cmdToExecute = String.format("docker run %s /bin/bash -c 'echo \"$%s\"'", imageName, envVarName);
    logger.info("getImageEnvVar with imageName: {0}, envVarName: {1}", imageName, envVarName);
    ExecResult result = Command.withParams(
                              new CommandParams()
                                .command(cmdToExecute)
                                .saveResults(true)
                                .redirect(true)
                            ).executeAndReturnResult();
    String envVar  = null;
    if (result != null && result.exitValue() == 0) {
      envVar = result.stdout();
    }
    logger.info("getImageEnvVar returns result: {0}, envVar: {1} ", result, envVar);
    return envVar;

  }
}