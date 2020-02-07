// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import oracle.kubernetes.operator.work.Step;

/**
 * Each ConfigUpdate contains a suggested WebLogic configuration update that is necessary to make
 * the WebLogic configuration to be compatible with the DomainSpec configuration.
 */
public interface ConfigUpdate {

  /**
   * Create a Step to perform the suggested WebLogic configuration update.
   *
   * @param next Next Step to be performed after the WebLogic configuration update
   * @return Step to perform the suggested WebLogic configuration update
   */
  Step createStep(Step next);
}
