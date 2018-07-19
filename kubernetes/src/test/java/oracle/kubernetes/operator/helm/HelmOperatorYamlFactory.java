// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;

import java.util.Map;
import oracle.kubernetes.operator.utils.GeneratedOperatorObjects;
import oracle.kubernetes.operator.utils.OperatorValues;
import oracle.kubernetes.operator.utils.OperatorYamlFactory;
import oracle.kubernetes.operator.utils.ParsedWeblogicOperatorSecurityYaml;
import oracle.kubernetes.operator.utils.ParsedWeblogicOperatorYaml;

public class HelmOperatorYamlFactory extends OperatorYamlFactory {
  @Override
  public OperatorValues createDefaultValues() {
    return new HelmOperatorValues().withTestDefaults();
  }

  @Override
  public GeneratedOperatorObjects generate(OperatorValues inputValues) throws Exception {
    Map<String, Object> overrides = ((HelmOperatorValues) inputValues).createMap();
    ProcessedChart chart = new ProcessedChart("weblogic-operator", overrides);

    assertThat(chart.getError(), emptyOrNullString());

    OperatorValues effectiveValues = new HelmOperatorValues(chart.getValues());
    return new GeneratedOperatorObjects(
        new ParsedWeblogicOperatorYaml(chart, effectiveValues),
        new ParsedWeblogicOperatorSecurityYaml(chart, effectiveValues));
  }
}
