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
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * An encapsulation of a helm chart, along with the processing that must be done to make it usable.
 */
@SuppressWarnings({"unchecked", "SameParameterValue"})
class ProcessedChart {
  private final String chartName;
  private final UpdateValues updateValues;
  private String error;
  private List<Map<String, String>> documents;
  private Process process;

  ProcessedChart(String chartName, UpdateValues updateValues) {
    this.chartName = chartName;
    this.updateValues = updateValues;
  }

  boolean matches(String chartName, UpdateValues updater) {
    return this.chartName.equals(chartName) && this.updateValues == updater;
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
    if (documents == null) {
      documents = getDocuments();
    }

    List<Map<String, String>> matches = new ArrayList<>();
    for (Map<String, String> document : documents) {
      if (document.get("kind").equals(kind)) {
        matches.add(document);
      }
    }

    return matches;
  }

  private List<Map<String, String>> getDocuments() throws Exception {
    List<Map<String, String>> charts = new ArrayList<>();
    new Yaml().loadAll(getProcess().getInputStream()).forEach(
        (document) -> {
          if (document != null) charts.add((Map<String, String>) document);
        });

    return charts;
  }

  private Process getProcess() throws Exception {
    if (process == null) {
      process = processChart(chartName, updateValues);
    }
    return process;
  }

  private Process processChart(String chartName, UpdateValues updateValues) throws Exception {
    File chartsDir = getChartsDir(chartName);
    File baseValuesFile = new File(chartsDir, "values.yaml");
    Map<String, String> values = new Yaml().load(new FileReader(baseValuesFile));

    Path valuesFile = createUpdatedValuesFile(updateValues, values);

    ProcessBuilder pb = new ProcessBuilder(createCommandLine(chartsDir, valuesFile));
    return pb.start();
  }

  private String[] createCommandLine(File chart, Path valuesPath) {
    return new String[] {"helm", "template", chart.getAbsolutePath(), "-f", valuesPath.toString()};
  }

  private Path createUpdatedValuesFile(UpdateValues updateValues, Map<String, String> values)
      throws IOException {
    updateValues.update(values);
    Path valuesFile = Files.createTempFile("Value", ".yaml");
    try (BufferedWriter writer = Files.newBufferedWriter(valuesFile, Charset.forName("UTF-8"))) {
      new Yaml().dump(values, writer);
    }
    return valuesFile;
  }

  private File getChartsDir(String chartName) throws URISyntaxException {
    return new File(getTargetDir(getClass()).getParentFile(), chartName);
  }

  private File getTargetDir(Class<?> aClass) throws URISyntaxException {
    File dir = getPackageDir(aClass);
    while (dir.getParent() != null && !dir.getName().equals("target")) {
      dir = dir.getParentFile();
    }
    return dir;
  }

  private File getPackageDir(Class<?> aClass) throws URISyntaxException {
    URL url = aClass.getResource(aClass.getSimpleName() + ".class");
    return Paths.get(url.toURI()).toFile().getParentFile();
  }
}
