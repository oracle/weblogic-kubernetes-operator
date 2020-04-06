// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.File;
import java.io.FileNotFoundException;

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

/**
 * The utility class for file operations.
 */
public class FileUtils {

  /**
   * Check if the required directories exist.
   * Currently the directories will be created if missing. We may remove this function
   * once we have all required working directives pre-created.
   *
   * @param dir the directory that needs to be checked
   */
  public static void checkDirectory(String dir) {
    File file = new File(dir);
    if (!file.isDirectory()) {
      file.mkdir();
      logger.info("Made a new dir " + dir);
    }
  }

  /**
   * Check if the required file exists, and throw if the file does not exist.
   *
   * @param fileName the name of the file that needs to be checked
   * @throws FileNotFoundException if the file does not exist, or it is a directory
   */
  public static void checkFile(String fileName) throws FileNotFoundException {
    File file = new File(fileName);
    if (!(file.exists() && file.isFile())) {
      logger.warning("The expected file " + fileName + " was not found.");
      throw new FileNotFoundException("The expected file " + fileName + " was not found.");
    }
  }
  
  /**
   * Check if the required file exists.
   *
   * @param fileName the name of the file that needs to be checked
   * @return true if a file exists with the given fileName
   */
  public static boolean doesFileExist(String fileName) {
    File file = new File(fileName);
    if (file.exists() && file.isFile()) {
      return true;
    }
    return false;
  }
}
