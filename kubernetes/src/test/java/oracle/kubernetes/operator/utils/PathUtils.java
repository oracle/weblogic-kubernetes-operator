// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

public class PathUtils {
  public static File getModuleDir(Class<?> aClass) throws URISyntaxException {
    return getTargetDir(aClass).getParentFile();
  }

  private static File getTargetDir(Class<?> aClass) throws URISyntaxException {
    File dir = getPackageDir(aClass);
    while (dir.getParent() != null && !dir.getName().equals("target")) {
      dir = dir.getParentFile();
    }
    return dir;
  }

  private static File getPackageDir(Class<?> aClass) throws URISyntaxException {
    URL url = aClass.getResource(aClass.getSimpleName() + ".class");
    return Paths.get(url.toURI()).toFile().getParentFile();
  }
}
