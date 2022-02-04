// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.io.IOException;

import oracle.kubernetes.operator.utils.DomainResourceMigrationUtils;

public class DomainResourceConverter {

  private static final DomainResourceMigrationUtils sct = new DomainResourceMigrationUtils();

  /**
   * Entry point of the DomainResourceConverter.
   * @param args The arguments for domain resource converter.
   * 
   */
  public static void main(String[] args) throws IOException {
    if (args.length < 1) {
      System.out.println("No input file provided. Please provide an input file.");
      return;
    }

    sct.writeDomain(sct.convertDomain(sct.readDomain(args[0])),
            new File(args[0]).getParent() + "/converted.yaml");
  }

}