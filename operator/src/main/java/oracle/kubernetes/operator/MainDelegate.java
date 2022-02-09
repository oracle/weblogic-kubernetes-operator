// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.SemanticVersion;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/**
 * Definition of an interface that returns values that the Main class requires.
 */
public interface MainDelegate {

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

  AtomicReference<V1CustomResourceDefinition> getCrdReference();
}
