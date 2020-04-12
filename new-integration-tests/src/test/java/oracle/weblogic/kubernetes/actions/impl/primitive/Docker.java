// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;


public class Docker {

  /**
   * Delete docker image.
   * @param image image name:image tag
   * @return true if delete image is successful
   */
  public static boolean deleteImage(String image) {
    String cmdToExecute = String.format("docker rmi -f %s", image);
    return new Command()
        .withParams(new CommandParams()
                  .command(cmdToExecute))
        .executeAndVerify();
  }


}