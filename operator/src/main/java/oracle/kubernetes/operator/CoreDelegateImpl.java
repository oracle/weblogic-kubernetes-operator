// Copyright (c) 2022, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

import oracle.kubernetes.operator.helpers.HealthCheckHelper;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.SemanticVersion;
import oracle.kubernetes.operator.work.Cancellable;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import static oracle.kubernetes.operator.BaseMain.GIT_BRANCH_KEY;
import static oracle.kubernetes.operator.BaseMain.GIT_BUILD_TIME_KEY;
import static oracle.kubernetes.operator.BaseMain.GIT_BUILD_VERSION_KEY;
import static oracle.kubernetes.operator.BaseMain.GIT_COMMIT_KEY;
import static oracle.kubernetes.operator.BaseMain.deploymentHome;
import static oracle.kubernetes.operator.BaseMain.probesHome;
import static oracle.kubernetes.operator.work.Cancellable.createCancellable;

public class CoreDelegateImpl implements CoreDelegate {

  protected final String buildVersion;
  protected final SemanticVersion productVersion;
  protected final KubernetesVersion kubernetesVersion;
  protected final ScheduledExecutorService scheduledExecutorService;
  protected final String deploymentImpl;
  protected final String deploymentBuildTime;
  protected String domainCrdResourceVersion;
  protected String clusterCrdResourceVersion;

  CoreDelegateImpl(Properties buildProps, ScheduledExecutorService scheduledExecutorService) {
    buildVersion = getBuildVersion(buildProps);
    deploymentImpl = getBranch(buildProps) + "." + getCommit(buildProps);
    deploymentBuildTime = getBuildTime(buildProps);

    productVersion = new SemanticVersion(buildVersion);
    kubernetesVersion = HealthCheckHelper.performK8sVersionCheck();

    this.scheduledExecutorService = scheduledExecutorService;

    PodHelper.setProductVersion(productVersion.toString());
  }

  protected static String getBuildVersion(Properties buildProps) {
    return Optional.ofNullable(buildProps.getProperty(GIT_BUILD_VERSION_KEY)).orElse("1.0");
  }

  protected static String getBranch(Properties buildProps) {
    return getBuildProperty(buildProps, GIT_BRANCH_KEY);
  }

  protected static String getCommit(Properties buildProps) {
    return getBuildProperty(buildProps, GIT_COMMIT_KEY);
  }

  protected static String getBuildTime(Properties buildProps) {
    return getBuildProperty(buildProps, GIT_BUILD_TIME_KEY);
  }

  protected static String getBuildProperty(Properties buildProps, String key) {
    return Optional.ofNullable(buildProps.getProperty(key)).orElse("unknown");
  }

  @Override
  public @Nonnull
  SemanticVersion getProductVersion() {
    return productVersion;
  }

  @Override
  public KubernetesVersion getKubernetesVersion() {
    return kubernetesVersion;
  }

  @Override
  public String getDomainCrdResourceVersion() {
    return domainCrdResourceVersion;
  }

  @Override
  public void setDomainCrdResourceVersion(String resourceVersion) {
    this.domainCrdResourceVersion = resourceVersion;
  }

  @Override
  public String getClusterCrdResourceVersion() {
    return clusterCrdResourceVersion;
  }

  @Override
  public void setClusterCrdResourceVersion(String resourceVersion) {
    this.clusterCrdResourceVersion = resourceVersion;
  }

  @Override
  public File getDeploymentHome() {
    return deploymentHome;
  }

  @Override
  public File getProbesHome() {
    return probesHome;
  }

  @Override
  public void runStepsInternal(Packet packet, Step firstStep, Runnable completionAction) {
    Fiber f = new Fiber(scheduledExecutorService, firstStep, packet, andThenDo(completionAction));
    f.start();
  }

  private static BaseMain.NullCompletionCallback andThenDo(Runnable completionAction) {
    return new BaseMain.NullCompletionCallback(completionAction);
  }

  @Override
  public Cancellable schedule(Runnable command, long delay, TimeUnit unit) {
    ScheduledFuture<?> future = scheduledExecutorService.schedule(command, delay, unit);
    return createCancellable(future);
  }

  @Override
  public Cancellable scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
    ScheduledFuture<?> future = scheduledExecutorService.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    return createCancellable(future);
  }
}
