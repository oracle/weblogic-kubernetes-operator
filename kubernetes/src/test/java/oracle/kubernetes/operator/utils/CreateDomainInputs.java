// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import static oracle.kubernetes.operator.utils.YamlUtils.newYaml;

import java.io.File;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Class that mirrors create-weblogic-domain-inputs.yaml
 *
 * <p>Used to parse create-weblogic-domain-inputs.yaml into java and convert java to
 * create-weblogic-domain-inputs.yaml
 *
 * <p>Note: use strings to represent params that must be ints or booleans at runtime so that we can
 * test more invalid input options (e.g. missing value, not int value)
 *
 * <p>Note: initialize to empty strings and change nulls to empty strings so that when this is
 * written out to a yaml file, the files don't include the literal "null" string.
 */
public class CreateDomainInputs extends DomainValues {

  private static final String DEFAULT_INPUTS = "create-weblogic-domain-inputs.yaml";

  public static DomainValues newInputs() throws Exception {
    CreateDomainInputs createDomainInputs = readDefaultInputsFile();
    return createDomainInputs.withTestDefaults();
  }

  public static CreateDomainInputs readDefaultInputsFile() throws Exception {
    return readInputsYamlFile(defaultInputsPath());
  }

  public static CreateDomainInputs readInputsYamlFile(Path path) throws Exception {
    Reader r = Files.newBufferedReader(path, Charset.forName("UTF-8"));
    return newYaml().loadAs(r, CreateDomainInputs.class);
  }

  private static Path defaultInputsPath() throws URISyntaxException {
    return new File(PathUtils.getModuleDir(CreateDomainInputs.class), DEFAULT_INPUTS).toPath();
  }
}
