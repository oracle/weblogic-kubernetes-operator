// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;
import oracle.kubernetes.operator.utils.PathUtils;

/** Gets a helm chart's default values */
@SuppressWarnings({"unchecked", "SameParameterValue"})
public class ChartDefaultValues {

  private String chartName;

  ChartDefaultValues(String chartName) {
    this.chartName = chartName;
  }

  /**
   * Returns the default values that the chart creates.
   *
   * @return a yaml string
   */
  String getDefaultValuesAsYaml() throws Exception {
    processChart();
    return getDefaultValuesFromDebugProcessStdout(processChart());
  }

  private String getDefaultValuesFromDebugProcessStdout(String stdout) throws Exception {
    String BEGIN_MARKER = "\nCOMPUTED VALUES:\n";
    int begin = stdout.indexOf(BEGIN_MARKER);
    if (begin == -1) {
      reportProcessError("stdout does not contain " + BEGIN_MARKER + "\nstdout:\n" + stdout);
    }
    begin = begin + BEGIN_MARKER.length();
    String END_MARKER = "\nHOOKS:\n";
    int end = stdout.indexOf(END_MARKER, begin);
    if (end == -1) {
      reportProcessError(
          "stdout does not contain \""
              + END_MARKER
              + " after "
              + BEGIN_MARKER
              + ".\nstdout:\n"
              + stdout);
    }
    return stdout.substring(begin, end);
  }

  private String processChart() throws Exception {
    ProcessBuilder pb = new ProcessBuilder(createCommandLine());
    Process p = pb.start();
    p.waitFor();
    String stdout = read(p.getInputStream());
    if (p.exitValue() != 0) {
      String stderr = read(p.getErrorStream());
      reportProcessError(
          "Non-zero exit value.\nexit value: "
              + p.exitValue()
              + "\nstdout:\n"
              + stdout
              + "\nstderr:\n"
              + stderr);
    }
    return stdout;
  }

  private void reportProcessError(String msg) throws Exception {
    String cmd = String.join(" ", createCommandLine());
    throw new Exception(cmd + ": " + msg);
  }

  private String read(InputStream is) throws Exception {
    return new BufferedReader(new InputStreamReader(is)).lines().collect(Collectors.joining("\n"));
  }

  private String[] createCommandLine() throws Exception {
    File chartDir = getChartDir(this.chartName);
    return new String[] {"helm", "template", chartDir.getAbsolutePath(), "--debug"};
  }

  private File getChartDir(String chartName) throws Exception {
    return new File(getChartsParentDir(), chartName);
  }

  private File getChartsParentDir() throws Exception {
    return new File(PathUtils.getModuleDir(getClass()), "charts");
  }
}
