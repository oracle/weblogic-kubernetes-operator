// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.SemanticVersion;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/**
 * Definition of an interface that returns values that the Main class requires.
 */
interface MainDelegate {

  SemanticVersion getProductVersion();

  String getPrincipal();

  default void runSteps(Step firstStep) {
    runSteps(new Packet(), firstStep, null);
  }

  void runSteps(Packet packet, Step firstStep, Runnable completionAction);

  DomainProcessor getDomainProcessor();

  DomainNamespaces getDomainNamespaces();

  KubernetesVersion getKubernetesVersion();

  ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);
}
