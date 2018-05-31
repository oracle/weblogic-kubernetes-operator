// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;

/**
 * A helper class that is used during the build process to update the Swagger HTML file with the
 * latest version of the REST API documentation.
 */
public class SwaggerBuildHelper {

  private static FileWriter fileWriter;

  /**
   * Update the swagger documentation HTML file with the contents of the swagger JSON file.
   *
   * @param args ignored
   */
  public static void main(String[] args) {

    try {
      // Open the source file
      File sourceFile = new File("swagger/src/main/resources/Operator.swagger");
      String newContent = new String(Files.readAllBytes(Paths.get(sourceFile.getAbsolutePath())));

      // Open the target file
      File targetFile = new File("docs/swagger/index.html");
      String targetContent =
          new String(Files.readAllBytes(Paths.get(targetFile.getAbsolutePath())));

      // replace old content
      // the (?is) in the pattern will match multi-line strings
      String updatedContent =
          targetContent.replaceAll(
              "(?is)SWAGGER_INSERT_START.+?SWAGGER_INSERT_END",
              "SWAGGER_INSERT_START\n  spec: "
                  // need to call quoteReplacement() to make sure the text is correctly quoted as a
                  // regex
                  // otherwise parts of the text might be incorrectly interpreted as a regex group
                  // reference, etc.
                  + Matcher.quoteReplacement(newContent)
                  + ",\n   // SWAGGER_INSERT_END");

      // write data out to the target file
      fileWriter = new FileWriter(targetFile, false); // false means overwrite existing content
      fileWriter.write(updatedContent);
      fileWriter.close();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
