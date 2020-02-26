// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("SameParameterValue")
class ChartItBase {
  private static List<ProcessedChart> charts = new ArrayList<>();

  ProcessedChart getChart(InstallArgs installArgs) {
    for (ProcessedChart chart : charts) {
      if (chart.matches(installArgs)) {
        return chart;
      }
    }

    ProcessedChart chart = new ProcessedChart(installArgs);
    charts.add(chart);
    return chart;
  }
}
