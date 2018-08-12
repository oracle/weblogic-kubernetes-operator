// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import static oracle.kubernetes.operator.utils.YamlUtils.newYaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ScriptGeneratedYamlReader implements YamlReader {
  Path path;

  public ScriptGeneratedYamlReader(Path path) {
    this.path = path;
  }

  public Iterable<Object> getYamlDocuments() throws IOException {
    return newYaml().loadAll(Files.newInputStream(this.path));
  }
}
