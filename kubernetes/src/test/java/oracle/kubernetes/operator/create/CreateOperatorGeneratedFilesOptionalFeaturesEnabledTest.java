package oracle.kubernetes.operator.create;

import oracle.kubernetes.operator.utils.ScriptedOperatorYamlFactory;
import org.junit.BeforeClass;

public class CreateOperatorGeneratedFilesOptionalFeaturesEnabledTest
    extends CreateOperatorGeneratedFilesOptionalFeaturesEnabledTestBase {
  @BeforeClass
  public static void setup() throws Exception {
    defineOperatorYamlFactory(new ScriptedOperatorYamlFactory());
  }
}
