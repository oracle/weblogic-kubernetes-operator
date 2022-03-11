// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/**
 * Definition of an interface that returns values that the Webhook Main class requires.
 */
interface ConversionWebhookMainDelegate extends CoreDelegate {

  default void runSteps(Step firstStep) {
    runSteps(new Packet(), firstStep, null);
  }

  void runSteps(Packet packet, Step firstStep, Runnable completionAction);

}