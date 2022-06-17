// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Installer;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.DOWNLOAD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_DOWNLOAD_FILENAME_DEFAULT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Installer.installWdtParams;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.cleanDirectory;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
      getLogger().fine("Made a new directory {0}.", dir);
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
      getLogger().warning("The expected file {0} was not found.", fileName);
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
   * Remove the contents of the given directory.
   *
   * @param dir the directory to be cleaned up
   * @throws IOException if the operation fails
   */
  public static void cleanupDirectory(String dir) throws IOException {
    File file = new File(dir);
    getLogger().info("Cleaning up directory {0}.", dir);
    if (!file.exists()) {
      // nothing to do
      return;
    }

    cleanDirectory(file);

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
          String msg = String.format("Failed to copy file %s to %s", source, destDir);
          getLogger().severe(msg, e);
          // cannot throw non runtime exception. the caller checks throwable
          throw new RuntimeException(msg);
        }
      });
    }
  }

  /**
   * Copy a file to a pod in specified namespace.
   * @param namespace namespace in which the pod exists
   * @param pod name of pod where the file will be copied to
   * @param container name of the container inside of the pod
   * @param srcPath source location of the file
   * @param destPath destination location of the file
   * @return true if copy succeeds, false otherwise
   * @throws IOException when copy fails
   * @throws ApiException when pod interaction fails
   */
  public static boolean copyFileToPod(String namespace,
                                   String pod,
                                   String container,
                                   Path srcPath,
                                   Path destPath) throws IOException, ApiException {
    return Kubernetes.copyFileToPod(namespace, pod, container, srcPath, destPath);
  }

  /**
   * Copy a file to a pod in specified namespace.
   * @param namespace namespace in which the pod exists
   * @param pod name of pod where the file will be copied to
   * @param container name of the container inside of the pod
   * @param srcPath source location of the file
   * @param destPath destination location of the file
   */
  public static Callable<Boolean> checkCopyFileToPod(String namespace,
                                                     String pod,
                                                     String container,
                                                     Path srcPath,
                                                     Path destPath) {
    return () -> copyFileToPod(namespace, pod, container, srcPath, destPath);
  }

  /**
   * Copy a file from Kubernetes pod to local filesystem.
   * @param namespace namespace of the pod
   * @param pod name of the pod where the file is copied from
   * @param container name of the container
   * @param srcPath source file location on the pod
   * @param destPath destination file location on local filesystem
   * @throws IOException when copy fails
   * @throws ApiException when pod interaction fails
   */
  public static void copyFileFromPod(String namespace, String pod, String container, String srcPath, Path destPath)
      throws IOException, ApiException {
    Kubernetes.copyFileFromPod(namespace, pod, container, srcPath, destPath);
  }

  /**
   * Copy a file from Kubernetes pod to local filesystem.
   * @param namespace namespace of the pod
   * @param pod name of the pod where the file is copied from
   * @param container name of the container
   * @param srcPath source file location on the pod
   * @param destPath destination file location on local filesystem
   * @throws IOException when copy fails
   * @throws InterruptedException if the process was interrupted
   */
  public static void copyFileFromPodUsingK8sExec(String namespace,
                                                 String pod,
                                                 String container,
                                                 String srcPath,
                                                 Path destPath) throws IOException, InterruptedException {
    LoggingFacade logger = getLogger();
    StringBuffer copyFileCmd = new StringBuffer("kubectl exec ");
    copyFileCmd.append(" -n ");
    copyFileCmd.append(namespace);
    copyFileCmd.append(" pod/");
    copyFileCmd.append(pod);
    copyFileCmd.append(" -c ");
    copyFileCmd.append(container);
    copyFileCmd.append(" -- cat ");
    copyFileCmd.append(srcPath);
    copyFileCmd.append(" > ");
    copyFileCmd.append(destPath);

    // copy a file from pod to local using kubectl exec
    logger.info("kubectl copy command is {0}", copyFileCmd.toString());
    ExecResult result = assertDoesNotThrow(() -> exec(new String(copyFileCmd), true));

    logger.info("kubectl copy returned {0}", result.toString());
  }

  /**
   * Copy a directory to a pod in specified namespace.
   * @param namespace namespace in which the pod exists
   * @param pod name of pod where the file will be copied to
   * @param container name of the container inside of the pod
   * @param srcPath source location of the directory
   * @param destPath destination location of the directory
   * @throws ApiException if Kubernetes API client call fails
   * @throws IOException if copy fails
   */
  public static void copyFolderToPod(String namespace,
                                     String pod,
                                     String container,
                                     Path srcPath,
                                     Path destPath) throws ApiException, IOException {

    Stream<Path> walk = Files.walk(srcPath);
    // find only regular files
    List<String> result = walk.filter(Files::isRegularFile)
        .map(x -> x.toString()).collect(Collectors.toList());

    result.forEach(fileOnHost -> {
      // resolve the given path against this path.
      Path fileInPod = destPath.resolve(srcPath.relativize(Paths.get(fileOnHost)));
      getLogger().info("Copying {0} to {1} ", fileOnHost, fileInPod);

      try {
        // copy each file to the pod.
        Kubernetes.copyFileToPod(namespace, pod, container, Paths.get(fileOnHost), fileInPod);
        getLogger().info("File {0} copied to {1} in Pod {2} in namespace {3} ",
            fileOnHost, fileInPod, pod, namespace);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
  }

  /**
   * Create a directory in a pod in specified namespace.
   * @param namespace The Kubernetes namespace that the pod is in
   * @param pod The name of the Kubernetes pod where the command is expected to run
   * @param container The container in the Pod where the command is to be run. If no
   *                         container name is provided than the first container in the Pod is used.
   * @param redirectToStdout copy process output to stdout
   * @param directoryToCreate namespace in which the pod exists
   */
  public static void makeDirectories(String namespace,
                                     String pod,
                                     String container,
                                     boolean redirectToStdout,
                                     List<String> directoryToCreate) {
    //Create directories.
    directoryToCreate.forEach(newDir -> {
      String mkCmd = "mkdir -p " + newDir;
      getLogger().info("Newdir to make {0} ", mkCmd);

      try {
        ExecResult execResult = execCommand(namespace,
            pod, container, redirectToStdout,"/bin/sh", "-c", mkCmd);
        getLogger().info("Directory created " + execResult.stdout());
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
  }

  /**
   * Copy file from source directory to destination directory.
   *
   * @param source path of source file
   * @param dest path of target file
   * @throws IOException if the operation encounters an issue
   */
  public static void copy(Path source, Path dest) throws IOException {
    getLogger().finest("Copying {0} to {1} source.fileName = {2}", source, dest, source.getFileName());
    if (!dest.toFile().isDirectory()) {
      Files.copy(source, dest, REPLACE_EXISTING);
    }
  }

  /**
   * Create a zip file from a folder.
   *
   * @param dirPath folder to zip
   * @return path of the zipfile
   */
  public static String createZipFile(Path dirPath) {
    String zipFileName = dirPath.toString().concat(".zip");
    try {
      final ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(zipFileName));
      Files.walkFileTree(dirPath, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
          try {
            Path targetFile = dirPath.relativize(file);
            outputStream.putNextEntry(new ZipEntry(Paths.get(targetFile.toString()).toString()));
            byte[] bytes = Files.readAllBytes(file);
            outputStream.write(bytes, 0, bytes.length);
            outputStream.closeEntry();
          } catch (IOException e) {
            e.printStackTrace();
          }
          return FileVisitResult.CONTINUE;
        }
      });
      outputStream.close();
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
    return zipFileName;
  }

  /**
   * Replaces each substring in the file that matches the given regular
   * expression with the given replacement.
   * @param filePath file in which a string has to be replaced
   * @param regex the regular expression to which this string is to be matched
   * @param replacement the string to be substituted for each match
   * @throws IOException if an IO error occurs while reading from the file
   */
  public static void replaceStringInFile(String filePath, String regex, String replacement)
      throws IOException {
    LoggingFacade logger = getLogger();
    Path src = Paths.get(filePath);
    logger.info("Replacing {0} in {1}", regex, src.toString());
    Charset charset = StandardCharsets.UTF_8;
    String content = new String(Files.readAllBytes(src), charset);
    String newcontent = content.replaceAll(regex, replacement);
    logger.info("with {0}", replacement);
    if (content.equals(newcontent)) {
      logger.info("search string {0} not found to replace with {1}", regex, replacement);
    }
    Files.write(src, newcontent.getBytes(charset));
  }

  /**
   * Check whether a file exists in a pod in the given namespace.
   *
   * @param namespace the Kubernetes namespace that the pod is in
   * @param podName the name of the Kubernetes pod in which the command is expected to run
   * @param filename the filename to check
   * @return true if the file exists, otherwise return false
   * @throws IOException if an I/O error occurs.
   * @throws ApiException if Kubernetes client API call fails
   * @throws InterruptedException if any thread has interrupted the current thread
   */
  public static boolean doesFileExistInPod(String namespace, String podName, String filename)
      throws IOException, ApiException, InterruptedException {

    ExecResult result = execCommand(namespace, podName, null, true,
        "/bin/sh", "-c", "find " + filename);

    if (result.stdout().contains(filename)) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Download and unzip the WDT installation files.
   * @param unzipLocation location to unzip the files
   */
  public static void unzipWDTInstallationFile(String unzipLocation) {
    unzipWDTInstallationFile(unzipLocation, WDT_DOWNLOAD_URL, DOWNLOAD_DIR);
  }

  /**
   * Download and unzip the WDT installation files.
   * @param unzipLocation location to unzip the files
   * @param downloadDir location to download wdt zip file
   */
  public static void unzipWDTInstallationFile(String unzipLocation, String locationURL, String downloadDir) {
    Path wlDeployZipFile = Paths.get(downloadDir, WDT_DOWNLOAD_FILENAME_DEFAULT);

    if (!Files.exists(wlDeployZipFile)) {
      assertTrue(Installer.withParams(
          installWdtParams(locationURL))
          .download(downloadDir), "WDT download failed");
    }
    String cmdToExecute = String.format("unzip -o %s -d %s", wlDeployZipFile, unzipLocation);
    assertTrue(Command
        .withParams(new CommandParams()
            .command(cmdToExecute))
        .execute(), String.format("Failed to unzip %s", wlDeployZipFile));
  }

  /**
   * Generate a text file in RESULTS_ROOT directory by replacing template value.
   * @param inputTemplateFile input template file
   * @param outputFile output file to be generated. This file will be copied to RESULTS_ROOT. If outputFile contains
   *                   a directory, then the directory will created if it does not exist.
   *                   example - crossdomxaction/istio-cdt-http-srvice.yaml
   * @param templateMap map containing template variable(s) to be replaced
   * @return path of the generated file - will be under RESULTS_ROOT
   */
  public static Path generateFileFromTemplate(
      String inputTemplateFile, String outputFile,
      Map<String, String> templateMap) throws IOException {

    LoggingFacade logger = getLogger();

    Path targetFileParent = Paths.get(outputFile).getParent();
    if (targetFileParent != null) {
      checkDirectory(targetFileParent.toString());
    }
    Path srcFile = Paths.get(inputTemplateFile);
    Path targetFile = Paths.get(RESULTS_ROOT, outputFile);
    logger.info("Copying  source file {0} to target file {1}", inputTemplateFile, targetFile.toString());

    // Add the parent directory for the target file
    Path parentDir = targetFile.getParent();
    Files.createDirectories(parentDir);
    Files.copy(srcFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
    String out = targetFile.toString();
    for (Map.Entry<String, String> entry : templateMap.entrySet()) {
      logger.info("Replacing String {0} with the value {1}", entry.getKey(), entry.getValue());
      FileUtils.replaceStringInFile(out, entry.getKey(), entry.getValue());
    }
    return targetFile;
  }

  /**
   * Check if the required file ls empty.
   *
   * @param fileName the name of the file that needs to be checked
   * @return true if a file is not empty with the given fileName
   */
  public static Callable<Boolean> isFileExistAndNotEmpty(String fileName) {
    File file = new File(fileName);
    return () -> {
      boolean fileReady = (file.exists() && file.length() != 0);
      return fileReady;
    };
  }

  /**
   * search a given string/work in file.
   *
   * @param fileName the name of the file
   * @param searchString string to search
   * @return true if the string found in the given fileName, otherwise return false
   */
  public static boolean searchStringInFile(String fileName, String searchString) throws IOException {
    LoggingFacade logger = getLogger();
    List<String> lines = Files.readAllLines(Paths.get(fileName));
    for (String line : lines) {
      if (line.contains(searchString)) {
        logger.info("Found string {0} in the file {1}", searchString, fileName);
        return true;
      }
    }

    logger.info("Failed to find string {0} in the file {1}", searchString, fileName);
    return false;
  }
}
