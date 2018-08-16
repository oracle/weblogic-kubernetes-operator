package oracle.kubernetes.weblogic.domain.v1;

import io.kubernetes.client.models.V1EnvVar;
import java.util.List;

public interface ServerSpec {

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
}
