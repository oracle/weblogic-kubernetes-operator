// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1NamespaceSpec;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.utils.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.helpers.NamespaceHelper.DEFAULT_NAMESPACE;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.parseNamespaceList;
import static oracle.kubernetes.operator.helpers.TuningParametersStub.REQUEST_LIMIT;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class NamespaceHelperTest {
  private final ListResponseStep responseStep = new ListResponseStep();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();

  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(TuningParametersStub.install());
  }

  @After
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  public void whenGivenEmptyString_parseNamespaceReturnsDefault() {
    assertThat(parseNamespaceList(""), contains(DEFAULT_NAMESPACE));
  }

  @Test
  public void whenGivenNonEmptyString_parseNamespaceDoesNotReturnDefault() {
    assertThat(parseNamespaceList("dev-domain"), not(contains(DEFAULT_NAMESPACE)));
  }

  @Test
  public void whenGivenSingleTarget_parseNamespaceReturnsIt() {
    assertThat(parseNamespaceList("dev-domain"), contains("dev-domain"));
  }

  @Test
  public void whenGivenMultipleTargets_parseNamespaceReturnsAll() {
    assertThat(parseNamespaceList("dev-domain,domain1,test-domain"),
               containsInAnyOrder("dev-domain", "domain1", "test-domain"));
  }

  @Test
  public void whenStringContainsLeadingSpaces_removeThem() {
    assertThat(parseNamespaceList(" test-domain, dev-domain"),
               containsInAnyOrder("dev-domain", "test-domain"));
  }

  @Test
  public void whenGivenTrailingSpaces_removeThem() {
    assertThat(parseNamespaceList("dev-domain ,test-domain "),
               containsInAnyOrder("dev-domain", "test-domain"));
  }

  @Test
  public void whenLabelSelectorSpecified_retrieveOnlySelectedNamespaces() {
    testSupport.defineResources(
          withLabelSelector("ABCD", createNamespace(1)),
          withLabelSelector("BCDE", createNamespace(2)),
          withLabelSelector("ABCD", createNamespace(3)),
          createNamespace(4)
    );

    testSupport.runSteps(NamespaceHelper.createNamespaceListStep(responseStep, "ABCD"));

    assertThat(responseStep.getItems(), hasSize(2));
  }

  V1Namespace withLabelSelector(String selector, V1Namespace namespace) {
    Objects.requireNonNull(namespace.getMetadata()).putLabelsItem(selector, "");
    return namespace;
  }

  @Test
  public void whenNoMoreNamespacesThanLimit_retrieveNamespaces() {
    testSupport.defineResources(createNamespaces(REQUEST_LIMIT));

    testSupport.runSteps(NamespaceHelper.createNamespaceListStep(responseStep, null));

    assertThat(responseStep.getItems(), hasSize(REQUEST_LIMIT));
  }

  @Test
  public void whenMoreNamespacesThanLimit_retrieveNamespaces() {
    testSupport.defineResources(createNamespaces(2 * REQUEST_LIMIT));

    testSupport.runSteps(NamespaceHelper.createNamespaceListStep(responseStep, null));

    assertThat(responseStep.getItems(), hasSize(2 * REQUEST_LIMIT));
  }

  @Test
  public void whenNamespacesAddedDuringList_retrieveFinalSetOfNamespaces() {
    testSupport.defineResources(createNamespaces(3 * REQUEST_LIMIT - 1));
    testSupport.doOnList(KubernetesTestSupport.NAMESPACE, new DoResourceUpdate());

    testSupport.runSteps(NamespaceHelper.createNamespaceListStep(responseStep, null));

    assertThat(responseStep.getItems(), hasSize(3 * REQUEST_LIMIT));
  }

  // Adds an addition namespace after the first list request has received two chunks.
  class DoResourceUpdate implements Runnable {
    private int count;

    @Override
    public void run() {
      if (count++ == 2) {
        testSupport.defineResources(createNamespace(5 * REQUEST_LIMIT));
      }
    }
  }

  @Nonnull
  public V1Namespace[] createNamespaces(int numNamespaces) {
    return IntStream.rangeClosed(1, numNamespaces).boxed().map(this::createNamespace).toArray(V1Namespace[]::new);
  }

  private V1Namespace createNamespace(int i) {
    return new V1Namespace().metadata(createNamespaceMetaData(i)).spec(new V1NamespaceSpec());
  }

  private V1ObjectMeta createNamespaceMetaData(int i) {
    return new V1ObjectMeta().name("ns" + i);
  }

  static class ListResponseStep extends ResponseStep<V1NamespaceList> {
    private CallResponse<V1NamespaceList> response;

    List<V1Namespace> getItems() {
      return response.getResult().getItems();
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1NamespaceList> callResponse) {
      response = callResponse;
      return doEnd(packet);
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1NamespaceList> callResponse) {
      response = callResponse;
      return doEnd(packet);
    }
  }
}