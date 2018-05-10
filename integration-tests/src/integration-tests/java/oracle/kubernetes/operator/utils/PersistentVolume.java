package oracle.kubernetes.operator.utils;

import java.util.logging.Logger;

public class PersistentVolume {

  private String dirPath;

  private static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");

  public PersistentVolume(String dirPath) {
    this.dirPath = dirPath;

    String cmdResult =
        TestUtils.executeCommand(
            new String[] {
              "/bin/sh", "-c", "../src/integration-tests/bash/job.sh \"mkdir -p " + dirPath + "\""
            });
    //logger.info("job.sh result "+cmdResult);
    //check if cmd executed successfully
    if (!cmdResult.contains("Exiting with status 0")) {
      throw new RuntimeException("FAILURE: Couldn't create domain PV directory " + cmdResult);
    }
  }

  public String getDirPath() {
    return dirPath;
  }
}
