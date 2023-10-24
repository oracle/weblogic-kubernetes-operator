// Copyright (c) 2020, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;


import java.util.Base64;

import com.google.gson.JsonObject;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;

import static oracle.weblogic.kubernetes.TestConstants.ARM;
import static oracle.weblogic.kubernetes.TestConstants.KIND_NODE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;

/**
 * Class with calls to Image Builder CLI. (Should be renamed ImageBuilder or similar.)
 */
public class Image {
  /**
   * Log in to a image registry.
   * @param registryName registry name
   * @param username user
   * @param password password
   * @return true if successful
   */
  public static boolean login(String registryName, String username, String password) {
    String cmdToExecute = String.format(WLSIMG_BUILDER + " login %s -u %s -p '%s'",
        registryName, username, password);
    return Command
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
    String cmdToExecute = String.format(WLSIMG_BUILDER + " pull %s", image);
    return Command
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
    String cmdToExecute = String.format(WLSIMG_BUILDER + " push %s", image);
    if (KIND_REPO != null) {
      cmdToExecute = String.format("kind load docker-image %s --name %s", image, KIND_NODE_NAME);
    }
    return Command
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
    String cmdToExecute = String.format(WLSIMG_BUILDER + " tag %s %s", originalImage, taggedImage);
    return Command
        .withParams(new CommandParams()
            .command(cmdToExecute))
        .execute();
  }

  /**
   * Delete image.
   * @param image image name:image tag
   * @return true if delete image is successful
   */
  public static boolean deleteImage(String image) {
    String cmdToExecute = String.format(WLSIMG_BUILDER + " rmi -f %s", image);
    return Command
        .withParams(new CommandParams()
                  .command(cmdToExecute))
        .execute();
  }

  /**
   * Create image.
   * @param image image name:image tag
   * @param imageBuildDir path to Dockerfile directory
   * @return true if delete image is successful
   */
  public static boolean createImage(String imageBuildDir, String image) {
    return createImage(imageBuildDir, image, "");
  }

  /**
   * Create image.
   * @param image image name:image tag
   * @param imageBuildDir path to Dockerfile directory
   * @param extraArgs extra args to pass
   * @return true if delete image is successful
   */
  public static boolean createImage(String imageBuildDir, String image, String extraArgs) {
    if (ARM) {
      String cmdToExecute = String.format(
          WLSIMG_BUILDER
              + " buildx create --use --name buildx_instance");
      Command
          .withParams(new CommandParams()
              .command(cmdToExecute))
          .execute();
      cmdToExecute = String.format(
          WLSIMG_BUILDER
              + " buildx build --pull  --platform linux/amd64,linux/arm64 %s -t %s  %s",
          imageBuildDir, image, extraArgs);
      boolean result = Command
          .withParams(new CommandParams()
              .command(cmdToExecute))
          .execute();
      cmdToExecute = String.format(
          WLSIMG_BUILDER
              + " buildx build --load  %s -t %s  %s ",
          imageBuildDir, image, extraArgs);
      Command
          .withParams(new CommandParams()
              .command(cmdToExecute))
          .execute();
      return result;
    } else {
      String cmdToExecute = String.format(WLSIMG_BUILDER + " build %s -t %s  %s", imageBuildDir, image, extraArgs);
      return Command.withParams(new CommandParams()
              .command(cmdToExecute))
          .execute();
    }
  }

  /**
   * Create image registry configuration in json object.
   * @param username username for the image registry
   * @param password password for the image registry
   * @param email email for the image registry
   * @param registry image registry name
   * @return json object for the image registry configuration
   */
  public static JsonObject createImageBuilderConfigJson(
      String username,
      String password,
      String email,
      String registry
  ) {
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
   * Get environment variable from image.
   * @param imageName image name
   * @param envVarName environment variable name
   * @return environment variable in the image
   */
  public static String getImageEnvVar(String imageName, String envVarName) {
    LoggingFacade logger = getLogger();
    String cmdToExecute = String.format(WLSIMG_BUILDER + " run %s /bin/bash -c 'echo \"$%s\"'", imageName, envVarName);
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
