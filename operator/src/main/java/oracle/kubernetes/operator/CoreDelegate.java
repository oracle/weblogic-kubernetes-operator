// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.SemanticVersion;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.PacketComponent;
import oracle.kubernetes.operator.work.Step;

import static oracle.kubernetes.operator.ProcessingConstants.DELEGATE_COMPONENT_NAME;

public interface CoreDelegate extends PacketComponent {

  SemanticVersion getProductVersion();

  KubernetesVersion getKubernetesVersion();

  String getDomainCrdResourceVersion();

  void setDomainCrdResourceVersion(String resourceVersion);

  String getClusterCrdResourceVersion();

  void setClusterCrdResourceVersion(String resourceVersion);

  File getDeploymentHome();

  File getProbesHome();

  default void runSteps(Step firstStep) {
    runSteps(new Packet(), firstStep, null);
  }

  default void runSteps(Packet packet, Step firstStep, Runnable completionAction) {
    runStepsInternal(packet.with(this), firstStep, completionAction);
  }

  void runStepsInternal(Packet packet, Step firstStep, Runnable completionAction);

  default void addToPacket(Packet packet) {
    packet.getComponents().put(DELEGATE_COMPONENT_NAME, Component.createFor(CoreDelegate.class, this));
  }

  ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);

}
