// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package cohapp;

import static java.lang.System.exit;

public class Main {

  /**
   * main.
   * @param args arguments
   */
  public static void main(String[] args) {

    try {
      if (args.length == 1) {
        String arg = args[0];

        CacheClient client = new CacheClient();

        if (arg.compareToIgnoreCase("load") == 0) {
          client.loadCache();
          exit(0);
        } else if (arg.compareToIgnoreCase("validate") == 0) {
          client.validateCache();
          exit(0);
        }
      }
      System.out.println("Param must be load or validate ");
      exit(1);

    } catch (Exception e) {
      System.out.println("Error executing cache test: " + e.getMessage());
      exit(1);
    }
  }
}
