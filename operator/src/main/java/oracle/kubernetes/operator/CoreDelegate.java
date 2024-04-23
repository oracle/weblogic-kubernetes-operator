// Copyright (c) 2022, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.SemanticVersion;
import oracle.kubernetes.operator.http.metrics.MetricsServer;
import oracle.kubernetes.operator.work.Cancellable;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import static oracle.kubernetes.operator.ProcessingConstants.DELEGATE_COMPONENT_NAME;

public interface CoreDelegate {

  String SHUTDOWN_MARKER_NAME = "marker.shutdown";

  SemanticVersion getProductVersion();

  KubernetesVersion getKubernetesVersion();

  String getDomainCrdResourceVersion();

  void setDomainCrdResourceVersion(String resourceVersion);

  String getClusterCrdResourceVersion();

  void setClusterCrdResourceVersion(String resourceVersion);

  File getDeploymentHome();

  default File getShutdownMarker() {
    return new File(getDeploymentHome(), SHUTDOWN_MARKER_NAME);
  }

  File getProbesHome();

  default boolean createNewFile(File file) throws IOException {
    return file.createNewFile();
  }

  default int getMetricsPort() {
    return MetricsServer.DEFAULT_METRICS_PORT;
  }

  default void runSteps(Step firstStep) {
    runSteps(new Packet(), firstStep, null);
  }

  default void runSteps(Packet packet, Step firstStep, Runnable completionAction) {
    packet.put(DELEGATE_COMPONENT_NAME, this);
    runStepsInternal(packet, firstStep, completionAction);
  }

  void runStepsInternal(Packet packet, Step firstStep, Runnable completionAction);

  Cancellable schedule(Runnable command, long delay, TimeUnit unit);

  Cancellable scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);

}
