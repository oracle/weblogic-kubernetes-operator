// Copyright (c) 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import oracle.kubernetes.operator.TuningParameters;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.helpers.LegalNames.DNS_1123_FIELDS_PARAM;
import static oracle.kubernetes.operator.helpers.LegalNames.toClusterServiceName;
import static oracle.kubernetes.operator.helpers.LegalNames.toServerServiceName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class LegalNamesTest {

  private List<Memento> mementos = new ArrayList<>();

  @Before
  public void setup() throws Exception {
    mementos.add(TuningParametersStub.install());
  }

  @After
  public void tearDown() {
    for (Memento memento : mementos) memento.revert();
    LegalNames.dns1123Fields = null;
  }


  @Test
  public void createValidServerServiceNames() throws Exception {
    assertThat(toServerServiceName("abc", "cls1"), equalTo("abc-cls1"));
    assertThat(toServerServiceName("Abc", "cLs1"), equalTo("abc-cls1"));
    assertThat(toServerServiceName("Abc", "cls_1"), equalTo("abc-cls-1"));
  }

  @Test
  public void createValidClusterServiceNames() throws Exception {
    assertThat(toClusterServiceName("abc", "cls1"), equalTo("abc-cluster-cls1"));
    assertThat(toClusterServiceName("Abc", "cLs1"), equalTo("abc-cluster-cls1"));
    assertThat(toClusterServiceName("Abc", "cls_1"), equalTo("abc-cluster-cls-1"));
  }

  @Test
  public void verify_requiresDNS1123Names_returnsTrue_for_names_in_defaultList() {
    assertThat(LegalNames.isDNS1123Required("ClaimName"), is(true));
    assertThat(LegalNames.isDNS1123Required("ClusterName"), is(true));
    assertThat(LegalNames.isDNS1123Required("ContainerName"), is(true));
    assertThat(LegalNames.isDNS1123Required("ExternalName"), is(true));
    assertThat(LegalNames.isDNS1123Required("GenerateName"), is(true));
    assertThat(LegalNames.isDNS1123Required("MetricName"), is(true));
    assertThat(LegalNames.isDNS1123Required("Name"), is(true));
    assertThat(LegalNames.isDNS1123Required("NodeName"), is(true));
    assertThat(LegalNames.isDNS1123Required("PersistentVolumeName"), is(true));
    assertThat(LegalNames.isDNS1123Required("PriorityClassName"), is(true));
    assertThat(LegalNames.isDNS1123Required("RuntimeClassName"), is(true));
    assertThat(LegalNames.isDNS1123Required("SchedulerName"), is(true));
    assertThat(LegalNames.isDNS1123Required("ScopeName"), is(true));
    assertThat(LegalNames.isDNS1123Required("ServiceAccountName"), is(true));
    assertThat(LegalNames.isDNS1123Required("SecretName"), is(true));
    assertThat(LegalNames.isDNS1123Required("ServiceName"), is(true));
    assertThat(LegalNames.isDNS1123Required("SingularName"), is(true));
    assertThat(LegalNames.isDNS1123Required("StorageClassName"), is(true));
    assertThat(LegalNames.isDNS1123Required("VolumeName"), is(true));
  }

  @Test
  public void verify_requiresDNS1123Names_returnFalse_for_names_not_in_list() {
    assertThat(LegalNames.isDNS1123Required("DatasetName"), is(false));
    assertThat(LegalNames.isDNS1123Required("DiskName"), is(false));
    assertThat(LegalNames.isDNS1123Required("InitiatorName"), is(false));
    assertThat(LegalNames.isDNS1123Required("NominatedNodeName"), is(false));
    assertThat(LegalNames.isDNS1123Required("PdName"), is(false));
    assertThat(LegalNames.isDNS1123Required("ShareName"), is(false));
    assertThat(LegalNames.isDNS1123Required("StoragePolicyName"), is(false));
  }

  @Test
  public void verify_requiresDNS1123Names_returnFalse_for_invalidValues() {
    assertThat(LegalNames.isDNS1123Required((String)null), is(false));
    assertThat(LegalNames.isDNS1123Required(""), is(false));
  }

  @Test
  public void verify_requiresDNS1123Names_with_customList() {
    String customList = "diskName, claimName";
    TuningParameters.getInstance().put(DNS_1123_FIELDS_PARAM, customList);

    assertThat(LegalNames.isDNS1123Required("DiskName"), is(true));
    assertThat(LegalNames.isDNS1123Required("ClaimName"), is(true));

    assertThat(LegalNames.isDNS1123Required("DatabaseName"), is(false));
    assertThat(LegalNames.isDNS1123Required("SecretName"), is(false));
  }

  @Test
  public void verify_requiresDNS1123Names_return_false_with_emptyStringCustomList() {
    String customList = "";
    TuningParameters.getInstance().put(DNS_1123_FIELDS_PARAM, customList);

    assertThat(LegalNames.isDNS1123Required("ClaimName"), is(false));
    assertThat(LegalNames.isDNS1123Required("SecretName"), is(false));
  }

  @Test
  public void verify_requiresDNS1123Names_return_false_with_singleSpaceCustomList() {
    String customList = " ";
    TuningParameters.getInstance().put(DNS_1123_FIELDS_PARAM, customList);

    assertThat(LegalNames.isDNS1123Required("ClaimName"), is(false));
    assertThat(LegalNames.isDNS1123Required("SecretName"), is(false));
  }
}
