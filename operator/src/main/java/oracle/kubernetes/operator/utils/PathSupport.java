// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

/**
 * A class that provides Path access to the file system.
 */
public class PathSupport {

  @SuppressWarnings("FieldMayBeFinal")
  private static Function<URI, Path> uriToPath = Paths::get;
  @SuppressWarnings("FieldMayBeFinal")
  private static Function<String, Path> stringToPath = Paths::get;

  private PathSupport() {
  }

  public static Path getPath(URI uri) {
    return uriToPath.apply(uri);
  }

  public static Path getPath(File file) {
    return stringToPath.apply(file.getPath());
  }

}
