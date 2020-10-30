// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.SemanticVersion;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.work.Engine;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/**
 * Definition of an interface that returns values that the Main class requires.
 */
interface MainDelegate {

  void logStartup(LoggingFacade loggingFacade);

  SemanticVersion getProductVersion();

  String getServiceAccountName();

  String getPrincipal();

  Engine getEngine();

  default void runSteps(Step firstStep) {
    runSteps(new Packet(), firstStep, null);
  }

  void runSteps(Packet packet, Step firstStep, Runnable completionAction);

  DomainProcessor getDomainProcessor();

  DomainNamespaces getDomainNamespaces();

  KubernetesVersion getKubernetesVersion();
}
