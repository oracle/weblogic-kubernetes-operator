// Copyright (c) 2020, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;

public class JakartaRefactorUtil {

  // Map of old package prefix → new package prefix
  private static final Map<String, String> PACKAGE_MAP = new LinkedHashMap<>();

  static {
    PACKAGE_MAP.put("javax.servlet", "jakarta.servlet");
    PACKAGE_MAP.put("javax.jms", "jakarta.jms");
    PACKAGE_MAP.put("javax.ejb", "jakarta.ejb");
    PACKAGE_MAP.put("javax.transaction", "jakarta.transaction");
    // add more here if needed
  }

  /**
   * Copy application directory and replace javax packages with jakarta packages.
   *
   * @param sourceDir applications source directory
   * @param targetDir application destination directory
   * @throws IOException throws exception when cannot be copied
   */
  public static void copyAndRefactorDirectory(Path sourceDir, Path targetDir) throws IOException {
    if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
      throw new IllegalArgumentException("Source must be a directory: " + sourceDir);
    }

    Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        Path targetPath = targetDir.resolve(sourceDir.relativize(dir));
        Files.createDirectories(targetPath);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Path targetPath = targetDir.resolve(sourceDir.relativize(file));

        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".java") || name.endsWith(".xml")
            || name.endsWith(".jsp") || name.endsWith(".tag")) {
          refactorFile(file, targetPath);
        } else {
          Files.copy(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return FileVisitResult.CONTINUE;
      }
    });
  }

  private static void refactorFile(Path sourceFile, Path targetFile) throws IOException {
    String content = Files.readString(sourceFile, StandardCharsets.UTF_8);
    String updated = content;

    // Replace only specific prefixes
    for (Map.Entry<String, String> entry : PACKAGE_MAP.entrySet()) {
      String oldPkg = entry.getKey();
      String newPkg = entry.getValue();

      // Replace in import or fully-qualified names. Use regex to match word boundaries.
      updated = updated.replaceAll("\\b" + oldPkg, newPkg);
    }

    Files.writeString(targetFile, updated, StandardCharsets.UTF_8,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }
}
