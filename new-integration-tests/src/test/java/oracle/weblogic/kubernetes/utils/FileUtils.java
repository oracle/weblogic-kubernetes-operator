// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static org.assertj.core.api.Assertions.assertThat;

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
    if (!(file.exists() && file.isDirectory())) {
      file.mkdirs();
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
  
  /**
   * Remove the given directory and its contents.
   *
   * @param dir the directory to be cleaned up
   */
  public static void cleanupDirectory(String dir) throws IOException {
    File file = new File(dir);
    logger.info("Cleaning up directory " + dir);
    if (!file.exists()) {
      // nothing to do
      return;
    }

    assertThat(file.isDirectory())
        .as("Make sure the given name is a directory")
        .withFailMessage("Cannot clean up something that is not a directory")
        .isTrue();

    Files.walk(Paths.get(dir))
        .sorted(Comparator.reverseOrder())
        .map(Path::toFile)
        .forEach(File::delete);

  }
  
  /**
   * Copy files from source directory to destination directory.
   *
   * @param srcDir source directory
   * @param destDir target directory
   * @throws IOException if the operation encounters an issue
   */
  public static void copyFolder(String srcDir, String destDir) throws IOException {
    Path srcPath = Paths.get(srcDir);
    Path destPath = Paths.get(destDir);
    try (Stream<Path> stream = Files.walk(srcPath)) {
      stream.forEach(source -> {
        try {
          copy(source, destPath.resolve(srcPath.relativize(source)));
        } catch (IOException e) {
          // cannot throw non runtime exception. the caller checks throwable
          throw new RuntimeException("Failed to copy file " + source);
        }
      });
    }
  }
  
  private static void copy(Path source, Path dest) throws IOException {
    Files.copy(source, dest, REPLACE_EXISTING);
  }
}
