// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import java.util.Map;

@SuppressWarnings("SameParameterValue")
class OperatorChartITBase extends ChartITBase {

  private static final String OPERATOR_CHART = "weblogic-operator";
  private static final String OPERATOR_RELEASE = "weblogic-operator";
  private static final String OPERATOR_NAMESPACE = "weblogic-operator";

  static InstallArgs newInstallArgs(Map<String, Object> valueOverrides) {
    return new InstallArgs(OPERATOR_CHART, OPERATOR_RELEASE, OPERATOR_NAMESPACE, valueOverrides);
  }
}
