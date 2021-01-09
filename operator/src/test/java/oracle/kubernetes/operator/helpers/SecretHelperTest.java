// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import oracle.kubernetes.utils.TestUtils;
import org.junit.After;
import org.junit.Before;

public class SecretHelperTest {
  final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  final List<Memento> mementos = new ArrayList<>();

  @Before
  public void setUp() {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
  }

  @After
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }
}