// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

/**
 * This class can load a group of files under a specified classpath directory into a map. It handles
 * both files on the file system and in a JAR.
 */
class FileGroupReader {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private String pathToGroup;

  /**
   * Creates a reader for a specific file location.
   *
   * @param pathToGroup the top-level directory containing the files, relative to the classpath.
   */
  FileGroupReader(String pathToGroup) {
    this.pathToGroup = pathToGroup;
  }

  /**
   * Loads the files at the defined location within the classpath into a map.
   *
   * @return a map of file paths to string contents.
   */
  Map<String, String> loadFilesFromClasspath() {
    synchronized (FileGroupReader.class) {
      try {
        try (ScriptPath scriptPath = getScriptPath()) {
          return loadContents(scriptPath.getScriptsDir());
        }
      } catch (Exception e) {
        LOGGER.warning(MessageKeys.EXCEPTION, e);
        throw new RuntimeException(e);
      }
    }
  }

  private ScriptPath getScriptPath() throws URISyntaxException, IOException {
    URI uri = getClass().getResource(pathToGroup).toURI();
    return isJar(uri) ? new JarScriptPath(uri) : new FileScriptPath(uri);
  }

  private boolean isJar(URI uri) {
    return "jar".equals(uri.getScheme());
  }

  /**
   * Given a file path, loads the contents of the files into a map.
   *
   * @param rootDir the path to the top-level directory
   * @return a map of file paths to string contents.
   * @throws IOException if an error occurs during the read
   */
  static Map<String, String> loadContents(Path rootDir) throws IOException {
    try (Stream<Path> walk = Files.walk(rootDir, 1)) {
      return walk.filter(path -> !Files.isDirectory(path))
          .collect(Collectors.toMap(FileGroupReader::asString, FileGroupReader::readContents));
    }
  }

  private static String asString(Path path) {
    return path.getFileName().toString();
  }

  private static String readContents(Path path) {
    try {
      return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    } catch (IOException io) {
      LOGGER.warning(MessageKeys.EXCEPTION, io);
      return "";
    }
  }

  interface ScriptPath extends AutoCloseable {
    Path getScriptsDir();
  }

  class FileScriptPath implements ScriptPath {
    private URI uri;

    FileScriptPath(URI uri) {
      this.uri = uri;
    }

    @Override
    public Path getScriptsDir() {
      return Paths.get(uri);
    }

    @Override
    public void close() {}
  }

  class JarScriptPath implements ScriptPath {
    private FileSystem fileSystem;

    JarScriptPath(URI uri) throws IOException {
      fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
    }

    @Override
    public Path getScriptsDir() {
      return fileSystem.getPath(pathToGroup);
    }

    @Override
    public void close() throws Exception {
      fileSystem.close();
    }
  }
}
