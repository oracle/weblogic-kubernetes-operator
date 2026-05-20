// Copyright (c) 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.common.utils;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

/** Utilities for loading YAML without allowing arbitrary Java object construction. */
public final class SafeYamlUtils {

  private SafeYamlUtils() {
  }

  public static Yaml createYaml() {
    return createYaml(new DumperOptions());
  }

  /**
   * Creates a YAML parser/emitter using SnakeYAML's SafeConstructor.
   *
   * @param dumperOptions options for YAML output
   * @return a YAML parser/emitter instance
   */
  public static Yaml createYaml(DumperOptions dumperOptions) {
    LoaderOptions loaderOptions = new LoaderOptions();
    loaderOptions.setEnumCaseSensitive(false);
    return new Yaml(new SafeConstructor(loaderOptions), new Representer(dumperOptions), dumperOptions);
  }

  public static Object load(String yaml) {
    return createYaml().load(yaml);
  }
}
