// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.mojo.shunit2;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import javax.annotation.Nonnull;

/**
 * A wrapper for ProcessBuilder, in order to allow unit testing.
 */
class BashProcessBuilder {

  private final String commands;
  private final Map<String,String> environmentVariables = new HashMap<>();
  private final BiFunction<String,Map<String,String>,Process> processBiFunction;

  /**
   * Constructs a builder.
   * @param commands the commands to be issued to a new bash process
   */
  BashProcessBuilder(String commands) {
    this(BashProcessBuilder::createProcess, commands);
  }

  BashProcessBuilder(BiFunction<String, Map<String, String>, Process> processBiFunction, String commands) {
    this.commands = commands;
    this.processBiFunction = processBiFunction;
  }

  /**
   * Updates the builder by adding an environment variable to be set in the process.
   * @param name the environment variable name
   * @param value the environment variable value
   */
  void addEnvironmentVariable(String name, String value) {
    environmentVariables.put(name, value);
  }

  /**
   * Starts the specified process and returns a Process object to control it.
   */
  public Process build() {
    return processBiFunction.apply(commands, environmentVariables);
  }

  @Nonnull
  protected static Process createProcess(String command, Map<String, String> environmentVariables) {
    try {
      ProcessBuilder pb = new ProcessBuilder("bash",  command);
      pb.environment().putAll(environmentVariables);
      return pb.start();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
