// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1EventList;
import io.kubernetes.client.models.V1PersistentVolumeList;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.models.V1SelfSubjectRulesReview;
import io.kubernetes.client.models.V1ServiceList;
import io.kubernetes.client.models.V1SubjectRulesReviewStatus;
import io.kubernetes.client.models.V1beta1IngressList;
import io.kubernetes.client.models.VersionInfo;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.SynchronousCallFactory;
import oracle.kubernetes.operator.work.AsyncCallTestSupport;
import oracle.kubernetes.weblogic.domain.v1.DomainList;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static com.meterware.simplestub.Stub.createStub;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class DomainPresenceTest {

  private List<Memento> mementos = new ArrayList<>();
  private AsyncCallTestSupport testSupport = new AsyncCallTestSupport();

  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(StubWatchFactory.install());
    mementos.add(testSupport.installRequestStepFactory());
    mementos.add(StaticStubSupport.install(CallBuilder.class, "CALL_FACTORY", createStub(SynchronousCallFactoryStub.class)));
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) memento.revert();
  }

  @SuppressWarnings("unchecked")
  @Test
  @Ignore
  public void watchPresenceWithNoPreexistingData_doesNothing() throws Exception {
    testSupport.createCannedResponse("listDomain").withNamespace("default").returning(new DomainList());
    testSupport.createCannedResponse("listIngress").withNamespace("default").returning(new V1beta1IngressList());
    testSupport.createCannedResponse("listService").withNamespace("default").returning(new V1ServiceList());
    testSupport.createCannedResponse("listEvent").withNamespace("default").returning(new V1EventList());
    testSupport.createCannedResponse("listPod").withNamespace("default").returning(new V1PodList());
    Main.begin();
  }

  @Test
  public void afterCancelDomainStatusUpdating_statusUpdaterIsNull() throws Exception {
    DomainPresenceInfo info = new DomainPresenceInfo("namespace");
    info.getStatusUpdater().getAndSet(createStub(ScheduledFuture.class));

    DomainPresenceControl.cancelDomainStatusUpdating(info);

    assertThat(info.getStatusUpdater().get(), nullValue());
  }

  static abstract class SynchronousCallFactoryStub implements SynchronousCallFactory {
    @Override
    public VersionInfo getVersionCode(ApiClient client) throws ApiException {
      return new VersionInfo().major("1").minor("8");
    }

    @Override
    public DomainList getDomainList(ApiClient client, String namespace, String pretty, String _continue, String fieldSelector, Boolean includeUninitialized, String labelSelector, Integer limit, String resourceVersion, Integer timeoutSeconds, Boolean watch) throws ApiException {
      return new DomainList();
    }

    @Override
    public V1PersistentVolumeList listPersistentVolumes(ApiClient client, String pretty, String _continue, String fieldSelector, Boolean includeUninitialized, String labelSelector, Integer limit, String resourceVersion, Integer timeoutSeconds, Boolean watch) throws ApiException {
      return new V1PersistentVolumeList();
    }

    @Override
    public V1SelfSubjectRulesReview createSelfSubjectRulesReview(ApiClient client, V1SelfSubjectRulesReview body, String pretty) throws ApiException {
      return new V1SelfSubjectRulesReview().status(new V1SubjectRulesReviewStatus());
    }
  }
}