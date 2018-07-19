// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import static oracle.kubernetes.operator.utils.YamlUtils.newYaml;

import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Class that mirrors create-weblogic-operator-inputs.yaml
 *
 * <p>Used to parse create-weblogic-operator-inputs.yaml into java and convert java to
 * create-weblogic-operator-inputs.yaml
 *
 * <p>Note: use strings to represent params that must be ints or booleans at runtime so that we can
 * test more invalid input options (e.g. missing value, not int value)
 *
 * <p>Note: initialize to empty strings and change nulls to empty strings so that when this is
 * written out to a yaml file, the files don't include the literal "null" string.
 */
public class CreateOperatorInputs extends OperatorValues {

  private static final String DEFAULT_INPUTS = "../kubernetes/create-weblogic-operator-inputs.yaml";

  public static OperatorValues readDefaultInputsFile() throws Exception {
    return readInputsYamlFile(defaultInputsPath());
  }

  private static OperatorValues readInputsYamlFile(Path path) throws Exception {
    Reader r = Files.newBufferedReader(path, Charset.forName("UTF-8"));
    return newYaml().loadAs(r, CreateOperatorInputs.class);
  }

  private static Path defaultInputsPath() {
    return FileSystems.getDefault().getPath(DEFAULT_INPUTS);
  }
}
