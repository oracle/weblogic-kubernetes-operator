// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

public class PathUtils {
  public static File getModuleDir(Class<?> aaClass) throws URISyntaxException {
    return getTargetDir(aaClass).getParentFile();
  }

  private static File getTargetDir(Class<?> aaClass) throws URISyntaxException {
    File dir = getPackageDir(aaClass);
    while (dir.getParent() != null && !dir.getName().equals("target")) {
      dir = dir.getParentFile();
    }
    return dir;
  }

  private static File getPackageDir(Class<?> aaClass) throws URISyntaxException {
    URL url = aaClass.getResource(aaClass.getSimpleName() + ".class");
    return Paths.get(url.toURI()).toFile().getParentFile();
  }
}
