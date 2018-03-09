// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
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

  // Most k8s artifacts have an 'equals' implementation that
  // works well across instances.
  // A few of the, e.g. Secrets where the secret values are printed
  // out as byte array addresses, don't.
  // For there artifacts, you have to conver them to yaml strings
  // then comare those.
  //
  // TBD - rewrite as a matcher?
  public static <T> void assertThat_yamlIsEqual(T have, T want) {
    // The secret values are stored as byte[], and V1Secret.equal isn't smart
    // enough to compare them byte by byte, therefore equals always fails.
    // However, they get converted to cleartext strings in the yaml.
    // So, just convert the secrets to yaml strings, then compare those.
    assertThat(newYaml().dump(have), equalTo(newYaml().dump(want)));
  }
}
