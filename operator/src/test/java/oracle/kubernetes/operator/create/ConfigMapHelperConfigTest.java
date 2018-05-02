// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import io.kubernetes.client.models.V1ConfigMap;
import static oracle.kubernetes.operator.KubernetesConstants.*;
import static oracle.kubernetes.operator.LabelConstants.*;
import static oracle.kubernetes.operator.VersionConstants.*;
import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import static oracle.kubernetes.operator.create.YamlUtils.*;
import static oracle.kubernetes.operator.helpers.AnnotationHelper.*;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import org.junit.Test;

/**
 * Test that ConfigMapHelper computes the correct domain config map
 */
public class ConfigMapHelperConfigTest {

  private static final String OPERATOR_NAMESPACE = "test-operator-namespace";
  private static final String DOMAIN_NAMESPACE = "test-domain-namespace";
  private static final String PROPERTY_LIVENESS_PROBE_SH = "livenessProbe.sh";
  private static final String PROPERTY_READINESS_PROBE_SH = "readinessProbe.sh";
  private static final String PROPERTY_READ_STATE_SH = "readState.sh";
  private static final String PROPERTY_START_SERVER_SH = "startServer.sh";
  private static final String PROPERTY_START_SERVER_PY = "start-server.py";
  private static final String PROPERTY_STOP_SERVER_SH = "stopServer.sh";
  private static final String PROPERTY_STOP_SERVER_PY = "stop-server.py";

  @Test
  public void computedDomainConfigMap_isCorrect() throws Exception {
    // The config map contains several properties that contain shell scripts
    // that we don't want to duplicate in the test.  However, we want to check
    // the properties are present.
    // So, for each one, make sure it's present and empty its value
    // so that we can then compare the actual and desired config maps as yaml strings.
    V1ConfigMap actual = getActualDomainConfigMap();
    assertThat(getThenEmptyConfigMapDataValue(actual, PROPERTY_LIVENESS_PROBE_SH), not(isEmptyOrNullString()));
    assertThat(getThenEmptyConfigMapDataValue(actual, PROPERTY_READINESS_PROBE_SH), not(isEmptyOrNullString()));
    assertThat(getThenEmptyConfigMapDataValue(actual, PROPERTY_READ_STATE_SH), not(isEmptyOrNullString()));
    assertThat(getThenEmptyConfigMapDataValue(actual, PROPERTY_START_SERVER_SH), not(isEmptyOrNullString()));
    assertThat(getThenEmptyConfigMapDataValue(actual, PROPERTY_START_SERVER_PY), not(isEmptyOrNullString()));
    assertThat(getThenEmptyConfigMapDataValue(actual, PROPERTY_STOP_SERVER_SH), not(isEmptyOrNullString()));
    assertThat(getThenEmptyConfigMapDataValue(actual, PROPERTY_STOP_SERVER_PY), not(isEmptyOrNullString()));
    assertThat(
      actual,
      yamlEqualTo(getDesiredDomainConfigMap()));
  }

  private V1ConfigMap getDesiredDomainConfigMap() {
    return
      newConfigMap()
        .metadata(newObjectMeta()
          .name(DOMAIN_CONFIG_MAP_NAME)
          .namespace(DOMAIN_NAMESPACE)
          .putLabelsItem(RESOURCE_VERSION_LABEL, DOMAIN_V1)
          .putLabelsItem(OPERATORNAME_LABEL, OPERATOR_NAMESPACE)
          .putLabelsItem(CREATEDBYOPERATOR_LABEL, "true"))
        .putDataItem(PROPERTY_LIVENESS_PROBE_SH, "")
        .putDataItem(PROPERTY_READINESS_PROBE_SH, "")
        .putDataItem(PROPERTY_READ_STATE_SH, "")
        .putDataItem(PROPERTY_START_SERVER_SH, "")
        .putDataItem(PROPERTY_START_SERVER_PY, "")
        .putDataItem(PROPERTY_STOP_SERVER_SH, "")
        .putDataItem(PROPERTY_STOP_SERVER_PY, "")
        .putDataItem(PROPERTY_READ_STATE_SH, "");
  }

  private V1ConfigMap getActualDomainConfigMap() throws Exception {
    return (new TestScriptConfigMapStep()).computeDomainConfigMap();
  }

  private static class TestScriptConfigMapStep extends ConfigMapHelper.ScriptConfigMapStep {
    public TestScriptConfigMapStep() {
      super(OPERATOR_NAMESPACE, DOMAIN_NAMESPACE, null);
    }
    @Override public V1ConfigMap computeDomainConfigMap() {
      return super.computeDomainConfigMap();
    }
  }
}
