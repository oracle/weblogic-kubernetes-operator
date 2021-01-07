// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static oracle.kubernetes.operator.utils.YamlUtils.newYaml;

public class ScriptGeneratedYamlReader implements YamlReader {
  Path path;

  public ScriptGeneratedYamlReader(Path path) {
    this.path = path;
  }

  public Iterable<Object> getYamlDocuments() throws IOException {
    return newYaml().loadAll(Files.newInputStream(this.path));
  }
}
