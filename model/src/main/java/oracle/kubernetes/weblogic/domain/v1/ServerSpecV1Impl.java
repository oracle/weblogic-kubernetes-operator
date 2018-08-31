package oracle.kubernetes.weblogic.domain.v1;

import static oracle.kubernetes.operator.KubernetesConstants.ALWAYS_IMAGEPULLPOLICY;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_IMAGE;
import static oracle.kubernetes.operator.KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;
import static oracle.kubernetes.operator.StartupControlConstants.ALL_STARTUPCONTROL;
import static oracle.kubernetes.operator.StartupControlConstants.AUTO_STARTUPCONTROL;
import static oracle.kubernetes.operator.StartupControlConstants.SPECIFIED_STARTUPCONTROL;

import io.kubernetes.client.models.V1EnvVar;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import oracle.kubernetes.operator.KubernetesConstants;

public class ServerSpecV1Impl implements ServerSpec {
  private static final String ADMIN_MODE_FLAG = "-Dweblogic.management.startupMode=ADMIN";
  private DomainSpec domainSpec;
  private final String clusterName;
  private ServerStartup serverStartup;
  private ClusterStartup clusterStartup;

  ServerSpecV1Impl(DomainSpec domainSpec, String serverName, String clusterName) {
    this.domainSpec = domainSpec;
    this.clusterName = clusterName;
    serverStartup = domainSpec.getServerStartup(serverName);
    clusterStartup = domainSpec.getClusterStartup(clusterName);
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
      var.setValue(prepend(var.getValue(), ADMIN_MODE_FLAG));
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
    return value == null ? prefix : value.contains(prefix) ? value : prefix + ' ' + value;
  }

  @Override
  public String getDesiredState() {
    return Optional.ofNullable(getConfiguredDesiredState()).orElse("RUNNING");
  }

  @Override
  public Integer getNodePort() {
    return serverStartup == null ? null : serverStartup.getNodePort();
  }

  private String getConfiguredDesiredState() {
    if (serverStartup != null) return serverStartup.getDesiredState();
    return clusterStartup == null ? null : clusterStartup.getDesiredState();
  }

  @Override
  public boolean shouldStart(int currentReplicas) {
    switch (domainSpec.getEffectiveStartupControl()) {
      case ALL_STARTUPCONTROL:
        return true;
      case AUTO_STARTUPCONTROL:
        if (clusterName != null) return currentReplicas < getReplicaCount();
      case SPECIFIED_STARTUPCONTROL:
        return isSpecified() && currentReplicas < getReplicaCount();
      default:
        return false;
    }
  }

  private int getReplicaCount() {
    return domainSpec.getReplicaCount(clusterStartup);
  }

  private boolean isSpecified() {
    return serverStartup != null || clusterStartup != null;
  }
}
