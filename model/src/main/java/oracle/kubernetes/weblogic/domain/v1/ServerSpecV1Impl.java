package oracle.kubernetes.weblogic.domain.v1;

import static oracle.kubernetes.operator.KubernetesConstants.ALWAYS_IMAGEPULLPOLICY;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_IMAGE;
import static oracle.kubernetes.operator.KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;

import io.kubernetes.client.models.V1EnvVar;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import oracle.kubernetes.operator.KubernetesConstants;

public class ServerSpecV1Impl implements ServerSpec {
  private DomainSpec domainSpec;
  private ServerStartup serverStartup;
  private ClusterStartup clusterStartup;

  ServerSpecV1Impl(DomainSpec domainSpec) {
    this.domainSpec = domainSpec;
  }

  /**
   * Sets the server startup. If null, is ignored.
   *
   * @param serverStartup the startup definition for this server.
   * @return the original object
   */
  ServerSpecV1Impl withServerStartup(ServerStartup serverStartup) {
    if (serverStartup == null) return this;

    this.serverStartup = serverStartup;
    return this;
  }

  /**
   * Sets the cluster startup. If null, is ignored.
   *
   * @param clusterStartup the startup definition for this cluster.
   * @return the original object
   */
  ServerSpecV1Impl withClusterStartup(ClusterStartup clusterStartup) {
    if (clusterStartup == null) return this;

    this.clusterStartup = clusterStartup;
    return this;
  }

  @Override
  public String getImage() {
    return Optional.ofNullable(domainSpec.getImage()).orElse(DEFAULT_IMAGE);
  }

  @Override
  public String getImagePullPolicy() {
    return Optional.ofNullable(domainSpec.getImagePullPolicy()).orElse(getInferredPullPolicy());
  }

  private boolean useLatestImage() {
    return getImage().endsWith(KubernetesConstants.LATEST_IMAGE_SUFFIX);
  }

  private String getInferredPullPolicy() {
    return useLatestImage() ? ALWAYS_IMAGEPULLPOLICY : IFNOTPRESENT_IMAGEPULLPOLICY;
  }

  @Override
  public List<V1EnvVar> getEnvironmentVariables() {
    return serverStartup == null ? Collections.emptyList() : serverStartup.getEnv();
  }

  @Override
  public String getDesiredState() {
    return Optional.ofNullable(getConfiguredDesiredState()).orElse("RUNNING");
  }

  private String getConfiguredDesiredState() {
    if (serverStartup != null) return serverStartup.getDesiredState();
    return clusterStartup == null ? null : clusterStartup.getDesiredState();
  }
}
