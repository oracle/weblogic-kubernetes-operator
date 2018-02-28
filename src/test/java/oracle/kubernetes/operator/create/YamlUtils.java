// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Yaml utilities for the create script tests
 */
public class YamlUtils {

  public static Yaml newYaml() {
    // always make a new yaml object since it appears to be stateful
    // so there are problems if you try to use the same one to
    // parse different yamls at the same time
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setPrettyFlow(true);
    return new Yaml(new Constructor(), new Representer(), options);
  }
}
