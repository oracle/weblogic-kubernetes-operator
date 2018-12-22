// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
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
      doBuild("swagger/src/main/resources/operator-swagger.json", "docs/swagger/index.html");
      doDomain(
          "swagger/src/main/resources/domain-swagger.json",
          "docs/domains/Domain.json",
          "docs/domains/index.html");
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  private static void doBuild(String source, String target) throws IOException {
    // Open the source file
    File sourceFile = new File(source);
    String newContent = new String(Files.readAllBytes(Paths.get(sourceFile.getAbsolutePath())));

    doContent(newContent, target);
  }

  private static void doDomain(String source, String domain, String target) throws IOException {
    // Open the source file
    File sourceFile = new File(source);
    String swaggerJson = new String(Files.readAllBytes(Paths.get(sourceFile.getAbsolutePath())));

    Map<String, Map<String, Object>> swaggerMap = fromJson(swaggerJson);
    Map<String, Object> swaggerDefinitions = swaggerMap.get("definitions");

    File domainFile = new File(domain);
    String domainJson = new String(Files.readAllBytes(Paths.get(domainFile.getAbsolutePath())));

    Map<String, Map<String, Object>> domainMap = fromJson(domainJson);
    Map<String, Object> domainDefinitions = domainMap.get("definitions");

    // copy all definitions from Domain.json to swagger
    swaggerDefinitions.putAll(domainDefinitions);

    @SuppressWarnings("unchecked")
    Map<String, Object> domainDef = (Map<String, Object>) swaggerDefinitions.get("Domain");
    domainDef.put("required", domainMap.get("required"));
    domainDef.put("properties", domainMap.get("properties"));

    doContent(new GsonBuilder().setPrettyPrinting().create().toJson(swaggerMap), target);
  }

  @SuppressWarnings("unchecked")
  private static <T, S> Map<T, S> fromJson(String json) {
    return new Gson().fromJson(json, HashMap.class);
  }

  private static void doContent(String newContent, String target) throws IOException {
    // Open the target file
    File targetFile = new File(target);
    targetFile.getParentFile().mkdirs();
    String targetContent = new String(Files.readAllBytes(Paths.get(targetFile.getAbsolutePath())));

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
                + ",\n    // SWAGGER_INSERT_END");

    // write data out to the target file
    fileWriter = new FileWriter(targetFile, false); // false means overwrite existing content
    fileWriter.write(updatedContent);
    fileWriter.close();
  }
}
