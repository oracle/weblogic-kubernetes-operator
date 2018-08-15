package oracle.kubernetes.weblogic.domain.v1;

import io.kubernetes.client.models.V1EnvVar;
import java.util.Collections;
import java.util.List;

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
  public String getDomainUID() {
    return domainSpec.getDomainUID();
  }

  @Override
  public String getDomainName() {
    return domainSpec.getDomainName();
  }

  @Override
  public String getImage() {
    return domainSpec.getImage();
  }

  @Override
  public String getImagePullPolicy() {
    return domainSpec.getImagePullPolicy();
  }

  @Override
  public List<V1EnvVar> getEnvironmentVariables() {
    return serverStartup == null ? Collections.emptyList() : serverStartup.getEnv();
  }

  @Override
  public String getDesiredState() {
    String state = getConfiguredDesiredState();
    return state != null ? state : "RUNNING";
  }

  private String getConfiguredDesiredState() {
    if (serverStartup != null) return serverStartup.getDesiredState();
    return clusterStartup == null ? null : clusterStartup.getDesiredState();
  }
}
