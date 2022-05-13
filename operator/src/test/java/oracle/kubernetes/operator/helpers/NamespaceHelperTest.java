// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.helpers.NamespaceHelper.DEFAULT_NAMESPACE;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.parseNamespaceList;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class NamespaceHelperTest {
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(TuningParametersStub.install());
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void whenGivenEmptyString_parseNamespaceReturnsDefault() {
    assertThat(parseNamespaceList(""), contains(DEFAULT_NAMESPACE));
  }

  @Test
  void whenGivenNonEmptyString_parseNamespaceDoesNotReturnDefault() {
    assertThat(parseNamespaceList("dev-domain"), not(contains(DEFAULT_NAMESPACE)));
  }

  @Test
  void whenGivenSingleTarget_parseNamespaceReturnsIt() {
    assertThat(parseNamespaceList("dev-domain"), contains("dev-domain"));
  }

  @Test
  void whenGivenMultipleTargets_parseNamespaceReturnsAll() {
    assertThat(parseNamespaceList("dev-domain,domain1,test-domain"),
               containsInAnyOrder("dev-domain", "domain1", "test-domain"));
  }

  @Test
  void whenStringContainsLeadingSpaces_removeThem() {
    assertThat(parseNamespaceList(" test-domain, dev-domain"),
               containsInAnyOrder("dev-domain", "test-domain"));
  }

  @Test
  void whenGivenTrailingSpaces_removeThem() {
    assertThat(parseNamespaceList("dev-domain ,test-domain "),
               containsInAnyOrder("dev-domain", "test-domain"));
  }

}