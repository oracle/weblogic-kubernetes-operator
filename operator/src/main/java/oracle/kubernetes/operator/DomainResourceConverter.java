// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.io.IOException;

import oracle.kubernetes.operator.utils.MigrationUtils;

public class DomainResourceConverter {

  private static final MigrationUtils migrationUtils = new MigrationUtils();

  /**
   * Entry point of the DomainResourceConverter.
   * @param args The arguments for domain resource converter.
   *
   */
  public static void main(String[] args) throws IOException {
    String outputDir = null;
    String outputFileName = null;

    if (args.length < 1) {
      System.out.println("No input file provided. Please provide an input file.\n"
              + "Usage: java DomainResourceConverter ${input_file_name} \n"
              + "Exiting.");
      return;
    } else if (args.length == 1) {
      outputDir = new File(args[0]).getParent();
      outputFileName = "Generated_" + new File(args[0]).getName();
    } else if (args.length > 1) {
      outputDir = args[1];
      outputFileName = "Generated_" + new File(args[0]).getName();
    } else if (args.length > 2) {
      outputDir = args[1];
      outputFileName = args[2];
    }

    migrationUtils.writeDomain(migrationUtils.convertDomain(migrationUtils.readDomain(args[0])),
            outputDir + "/" + outputFileName);
  }
}