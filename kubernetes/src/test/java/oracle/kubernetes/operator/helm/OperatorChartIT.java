// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasSize;

import java.util.Map;
import org.junit.Test;

@SuppressWarnings("SameParameterValue")
public class OperatorChartIT extends ChartITBase {

  private static final NullUpdate NULL_UPDATE = new NullUpdate();
  private static final AddCertificates ADD_CERTIFICATES = new AddCertificates();
  private static final String OPERATOR_CHART = "weblogic-operator";

  @Test
  public void whenNoCertificateSpecified_helmReportsFailure() throws Exception {
    ProcessedChart chart = getChart(OPERATOR_CHART, NULL_UPDATE);

    assertThat(chart.getError(), containsString("property internalOperatorCert must be specified"));
  }

  @Test
  public void whenCertificateSpecified_noErrorOccurs() throws Exception {
    ProcessedChart chart = getChart(OPERATOR_CHART, ADD_CERTIFICATES);

    assertThat(chart.getError(), emptyOrNullString());
  }

  @Test
  public void whenChartsGenerated_haveOneRoleBinding() throws Exception {
    ProcessedChart chart = getChart(OPERATOR_CHART, ADD_CERTIFICATES);

    assertThat(chart.getDocuments("RoleBinding"), hasSize(1));
  }

  private static class NullUpdate implements UpdateValues {
    @Override
    public void update(Map<String, String> values) {}
  }

  private static class AddCertificates implements UpdateValues {
    @Override
    public void update(Map<String, String> values) {
      values.put("internalOperatorCert", "dummy.cert");
      values.put("internalOperatorKey", "dummy.key");
    }
  }
}
