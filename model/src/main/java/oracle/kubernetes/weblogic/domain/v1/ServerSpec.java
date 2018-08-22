package oracle.kubernetes.weblogic.domain.v1;

import io.kubernetes.client.models.V1EnvVar;
import java.util.List;

public interface ServerSpec {

  /**
   * Temporary: to enable refactoring only. Provides a means to obtain the old implementation object
   * from the new one.
   *
   * @deprecated should be removed once the refactoring is done.
   */
  @Deprecated
  ServerStartup getServerStartup();

  /**
   * The WebLogic Docker image.
   *
   * @return image
   */
  String getImage();

  /**
   * The image pull policy for the WebLogic Docker image. Legal values are Always, Never and
   * IfNotPresent.
   *
   * <p>Defaults to Always if image ends in :latest, IfNotPresent otherwise.
   *
   * <p>More info: https://kubernetes.io/docs/concepts/containers/images#updating-images
   *
   * @return image pull policy
   */
  String getImagePullPolicy();

  /**
   * Returns the environment variables to be defined for this server.
   *
   * @return a list of environment variables
   */
  List<V1EnvVar> getEnvironmentVariables();

  /**
   * Desired startup state. Legal values are RUNNING or ADMIN.
   *
   * @return desired state
   */
  String getDesiredState();

  /**
   * Returns true if this server is specified in the domain spec.
   *
   * @return whether configuration exists for the server
   */
  boolean isSpecified();
}
