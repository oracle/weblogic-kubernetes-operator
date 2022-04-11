// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.IOException;
import java.nio.file.Path;

import io.kubernetes.client.util.Yaml;

public class ScriptGeneratedYamlReader implements YamlReader {
  Path path;

  public ScriptGeneratedYamlReader(Path path) {
    this.path = path;
  }

  public Iterable<Object> getYamlDocuments() throws IOException {
    return Yaml.loadAll(this.path.toFile());
  }
}
