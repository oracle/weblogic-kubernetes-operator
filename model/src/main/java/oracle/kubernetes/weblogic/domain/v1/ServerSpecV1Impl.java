package oracle.kubernetes.weblogic.domain.v1;

import static oracle.kubernetes.operator.KubernetesConstants.ALWAYS_IMAGEPULLPOLICY;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_IMAGE;
import static oracle.kubernetes.operator.KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;

import io.kubernetes.client.models.V1EnvVar;
import java.util.ArrayList;
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
  public ServerStartup getServerStartup() {
    return serverStartup;
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
    List<V1EnvVar> vars =
        serverStartup != null
            ? serverStartup.getEnv()
            : clusterStartup != null ? clusterStartup.getEnv() : Collections.emptyList();
    return withStateAdjustments(vars);
  }

  private List<V1EnvVar> withStateAdjustments(List<V1EnvVar> env) {
    if (!getDesiredState().equals("ADMIN")) {
      return env;
    } else {
      List<V1EnvVar> adjustedEnv = new ArrayList<>(env);
      V1EnvVar var = getOrCreateVar(adjustedEnv, "JAVA_OPTIONS");
      var.setValue(prepend(var.getValue(), "-Dweblogic.management.startupMode=ADMIN"));
      return adjustedEnv;
    }
  }

  @SuppressWarnings("SameParameterValue")
  private V1EnvVar getOrCreateVar(List<V1EnvVar> env, String name) {
    for (V1EnvVar var : env) {
      if (var.getName().equals(name)) return var;
    }
    V1EnvVar var = new V1EnvVar().name(name);
    env.add(var);
    return var;
  }

  @SuppressWarnings("SameParameterValue")
  private String prepend(String value, String prefix) {
    return value == null ? prefix : prefix + ' ' + value;
  }

  @Override
  public String getDesiredState() {
    return Optional.ofNullable(getConfiguredDesiredState()).orElse("RUNNING");
  }

  @Override
  public boolean isSpecified() {
    return serverStartup != null || clusterStartup != null;
  }

  private String getConfiguredDesiredState() {
    if (serverStartup != null) return serverStartup.getDesiredState();
    return clusterStartup == null ? null : clusterStartup.getDesiredState();
  }
}
