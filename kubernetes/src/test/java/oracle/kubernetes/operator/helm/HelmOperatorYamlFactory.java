// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import java.util.Map;

import oracle.kubernetes.operator.utils.GeneratedOperatorObjects;
import oracle.kubernetes.operator.utils.OperatorValues;
import oracle.kubernetes.operator.utils.OperatorYamlFactory;
import oracle.kubernetes.operator.utils.ParsedWeblogicOperatorSecurityYaml;
import oracle.kubernetes.operator.utils.ParsedWeblogicOperatorYaml;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;

public class HelmOperatorYamlFactory extends OperatorYamlFactory {

  private static final String OPERATOR_CHART = "weblogic-operator";
  private static final String OPERATOR_RELEASE = "weblogic-operator";

  @Override
  public OperatorValues createDefaultValues() {
    return new HelmOperatorValues().withTestDefaults();
  }

  @Override
  public GeneratedOperatorObjects generate(OperatorValues inputValues) throws Exception {
    Map<String, Object> overrides = ((HelmOperatorValues) inputValues).createMap();
    InstallArgs installArgs =
        new InstallArgs(OPERATOR_CHART, OPERATOR_RELEASE, inputValues.getNamespace(), overrides);
    ProcessedChart chart = new ProcessedChart(installArgs);

    assertThat(chart.getError(), emptyOrNullString());

    OperatorValues effectiveValues = new HelmOperatorValues(chart.getValues());
    return new GeneratedOperatorObjects(
        new ParsedWeblogicOperatorYaml(chart, effectiveValues),
        new ParsedWeblogicOperatorSecurityYaml(chart, effectiveValues));
  }
}
