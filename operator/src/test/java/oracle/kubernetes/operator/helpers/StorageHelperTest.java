// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;

import com.google.gson.GsonBuilder;
import com.jayway.jsonpath.JsonPath;
import com.meterware.simplestub.Memento;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PersistentVolume;
import io.kubernetes.client.models.V1PersistentVolumeClaim;
import java.util.ArrayList;
import java.util.List;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainSpec;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class StorageHelperTest {
  private static String NS = "namespace";
  private static String UID = "uid";
  private static String PATH = "/my/path";
  private Domain domain = new Domain().withMetadata(createMetadata()).withSpec(createSpec());
  private DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain);
  private TerminalStep terminalStep = new TerminalStep();
  private DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private AsyncCallTestSupport testSupport = new AsyncCallTestSupport();
  private List<Memento> mementos = new ArrayList<>();

  private V1ObjectMeta createMetadata() {
    return new V1ObjectMeta().namespace(NS);
  }

  private DomainSpec createSpec() {
    return new DomainSpec().withDomainUID(UID);
  }

  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.installRequestStepFactory());

    testSupport.addDomainPresenceInfo(info);
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) memento.revert();

    testSupport.throwOnCompletionFailure();
    testSupport.verifyAllDefinedResponsesInvoked();
  }

  @Test
  public void whenStorageNotConfigured_addNoSteps() {
    assertThat(StorageHelper.insertStorageSteps(domain, terminalStep), sameInstance(terminalStep));
  }

  @Test
  public void whenExternalStorageConfigured_addNoSteps() {
    configurator.withPredefinedClaim("external");

    assertThat(StorageHelper.insertStorageSteps(domain, terminalStep), sameInstance(terminalStep));
  }

  @Test
  public void whenHostPathStorageConfigured_runCreatesStorage() {
    configurator.withHostPathStorage(PATH);
    testSupport
        .createCannedResponse("createPersistentVolume")
        .withBody((BodyMatcher) this::isExpectedHostPathPV)
        .returning(new V1PersistentVolume());
    testSupport
        .createCannedResponse("createPersistentVolumeClaim")
        .withNamespace(NS)
        .withBody((BodyMatcher) this::isExpectedHostPathPVC)
        .returning(new V1PersistentVolumeClaim());

    testSupport.runSteps(StorageHelper.insertStorageSteps(domain, terminalStep));
  }

  private boolean isExpectedHostPathPV(Object pv) {
    String actualPath = JsonPath.read(toJson(pv), "$.spec.hostPath.path");
    return actualPath.equals(PATH);
  }

  private boolean isExpectedHostPathPVC(Object pvc) {
    String domainUidLabel = JsonPath.read(toJson(pvc), "$.metadata.labels.['weblogic.domainUID']");
    return domainUidLabel.equals(UID);
  }

  private String toJson(Object object) {
    return new GsonBuilder().create().toJson(object);
  }
}
