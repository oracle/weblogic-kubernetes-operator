package oracle.kubernetes.operator.create;

import oracle.kubernetes.operator.utils.ScriptedOperatorYamlFactory;
import org.junit.BeforeClass;

/**
 * Tests that the artifacts in the yaml files that create-weblogic-operator.sh creates are correct
 * when the remote debugging port is enabled and all other optional features are disabled.
 */
public class CreateOperatorGeneratedFilesDebugEnabledTest
    extends CreateOperatorGeneratedFilesDebugEnabledTestBase {

  @BeforeClass
  public static void setup() throws Exception {
    defineOperatorYamlFactory(new ScriptedOperatorYamlFactory());
  }
}
