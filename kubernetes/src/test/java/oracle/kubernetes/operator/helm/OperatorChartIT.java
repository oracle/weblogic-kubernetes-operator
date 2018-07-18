// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasSize;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.Test;

@SuppressWarnings("SameParameterValue")
public class OperatorChartIT extends ChartITBase {

  private static final String OPERATOR_CHART = "weblogic-operator";
  private static final Map<String, Object> CERTIFICATES =
      ImmutableMap.<String, Object>builder()
          .put("internalOperatorCert", "dummy.cert")
          .put("internalOperatorKey", "dummy.key")
          .build();

  @Test
  public void whenNoCertificateSpecified_helmReportsFailure() throws Exception {
    ProcessedChart chart = getChart(OPERATOR_CHART);

    assertThat(chart.getError(), containsString("property internalOperatorCert must be specified"));
  }

  @Test
  public void whenCertificateSpecified_noErrorOccurs() throws Exception {
    ProcessedChart chart = getChart(OPERATOR_CHART, CERTIFICATES);

    assertThat(chart.getError(), emptyOrNullString());
  }

  @Test
  public void whenChartsGenerated_haveOneRoleBinding() throws Exception {
    ProcessedChart chart = getChart(OPERATOR_CHART, CERTIFICATES);

    assertThat(chart.getDocuments("RoleBinding"), hasSize(1));
  }

}
