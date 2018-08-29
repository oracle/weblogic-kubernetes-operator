// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasSize;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;

@SuppressWarnings("SameParameterValue")
public class OperatorChartIT extends OperatorChartITBase {

  private static final Map<String, Object> CERTIFICATES =
      ImmutableMap.<String, Object>builder()
          .put("internalOperatorCert", "dummy.cert")
          .put("internalOperatorKey", "dummy.key")
          .build();

  private static final InstallArgs NO_VALUES_INSTALL_ARGS = newInstallArgs(Collections.emptyMap());
  private static final InstallArgs CERTIFICATES_INSTALL_ARGS = newInstallArgs(CERTIFICATES);

  @Test
  public void whenNoCertificateSpecified_helmReportsFailure() throws Exception {
    ProcessedChart chart = getChart(NO_VALUES_INSTALL_ARGS);

    assertThat(chart.getError(), containsString("string internalOperatorCert must be specified"));
  }

  @Test
  public void whenCertificateSpecified_noErrorOccurs() throws Exception {
    ProcessedChart chart = getChart(CERTIFICATES_INSTALL_ARGS);

    assertThat(chart.getError(), emptyOrNullString());
  }

  @Test
  public void whenChartsGenerated_haveOneRoleBinding() throws Exception {
    ProcessedChart chart = getChart(CERTIFICATES_INSTALL_ARGS);

    assertThat(chart.getDocuments("RoleBinding"), hasSize(1));
  }
}
