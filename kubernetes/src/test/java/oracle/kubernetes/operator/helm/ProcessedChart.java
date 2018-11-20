// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.operator.utils.PathUtils;
import oracle.kubernetes.operator.utils.YamlReader;
import org.yaml.snakeyaml.Yaml;

/**
 * An encapsulation of a helm chart, along with the processing that must be done to make it usable.
 */
@SuppressWarnings({"unchecked", "SameParameterValue"})
public class ProcessedChart implements YamlReader {
  private final InstallArgs installArgs;
  private String error;
  private List<Object> documents;
  private Process process;
  private Map<String, Object> values;

  ProcessedChart(InstallArgs installArgs) {
    this.installArgs = installArgs;
  }

  boolean matches(InstallArgs installArgs) {
    return this.installArgs.equals(installArgs);
  }

  /**
   * Returns the contents of the error stream. May be empty if no error has occurred.
   *
   * @return an error string.
   * @throws Exception if an error occurs during processing.
   */
  String getError() throws Exception {
    if (error == null) {
      error = dump(getProcess().getErrorStream());
    }

    return error;
  }

  private String dump(InputStream in) throws IOException {
    StringBuilder sb = new StringBuilder();

    String line;
    BufferedReader br = new BufferedReader(new InputStreamReader(in));

    while (((line = br.readLine()) != null)) sb.append(line).append(System.lineSeparator());

    return sb.toString();
  }

  /**
   * Returns a list containing a number of maps loaded from yaml documents whose Kind is as
   * specified.
   *
   * @param kind the specified kind of document to return
   * @return a list of yaml documents
   * @throws Exception if an error occurs
   */
  List<Map<String, String>> getDocuments(String kind) throws Exception {
    List<Map<String, String>> matches = new ArrayList<>();
    for (Object object : getYamlDocuments()) {
      Map document = (Map) object;
      if (document.get("kind").equals(kind)) {
        matches.add(document);
      }
    }

    return matches;
  }

  /**
   * Returns a list containing a maps loaded from yaml documents.
   *
   * @return a list of yaml documents
   * @throws Exception if an error occurs
   */
  public Iterable<Object> getYamlDocuments() throws Exception {
    if (documents == null) {
      List<Object> documents = new ArrayList<>();
      new Yaml()
          .loadAll(getProcess().getInputStream())
          .forEach(
              (document) -> {
                if (document != null) documents.add(document);
              });

      this.documents = documents;
    }

    return documents;
  }

  /**
   * Returns the values used to render the chart.
   *
   * @return a map of values
   */
  Map<String, Object> getValues() throws Exception {
    getYamlDocuments();

    return values;
  }

  private Process getProcess() throws Exception {
    if (process == null) {
      process = processChart();
    }
    return process;
  }

  private Process processChart() throws Exception {
    File chartsDir = getChartDir(installArgs.getChartName());
    File baseValuesFile = new File(chartsDir, "values.yaml");
    values = new Yaml().load(new FileReader(baseValuesFile));
    applyOverrides(installArgs.getValueOverrides());

    Path valuesFile = writeValuesOverride(installArgs.getValueOverrides());

    ProcessBuilder pb = new ProcessBuilder(createCommandLine(chartsDir, valuesFile));
    Process p = pb.start();
    p.waitFor();
    return p;
  }

  private void applyOverrides(Map<String, Object> valueOverrides) {
    values.putAll(valueOverrides);
  }

  private String[] createCommandLine(File chart, Path valuesPath) {
    return new String[] {
      "helm",
      "template",
      chart.getAbsolutePath(),
      "-f",
      valuesPath.toString(),
      "--name",
      installArgs.getReleaseName(),
      "--namespace",
      installArgs.getNamespace()
    };
  }

  private Path writeValuesOverride(Map<String, Object> values) throws IOException {
    Path valuesFile = Files.createTempFile("Value", ".yaml");
    try (BufferedWriter writer = Files.newBufferedWriter(valuesFile, Charset.forName("UTF-8"))) {
      new Yaml().dump(values, writer);
    }
    return valuesFile;
  }

  private File getChartDir(String chartName) throws URISyntaxException {
    return new File(getChartsParentDir(), chartName);
  }

  private File getChartsParentDir() throws URISyntaxException {
    return new File(PathUtils.getModuleDir(getClass()), "charts");
  }
}
