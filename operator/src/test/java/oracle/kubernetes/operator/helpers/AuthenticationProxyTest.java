// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Scope;
import oracle.kubernetes.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class AuthenticationProxyTest {

  private final List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final AuthorizationProxyStub authorizationProxyStub = new AuthorizationProxyStub();

  /**
   * Setup test.
   * @throws Exception on failure
   */
  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(
        StaticStubSupport.install(AuthenticationProxy.class, "authorizationProxy", authorizationProxyStub));
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  public void verify_authorizationScope_isCluster_whenNamespaceIsNull() {
    AuthenticationProxy authorizationProxy = new AuthenticationProxy();
    authorizationProxy.check("", "", null);
    assertThat(authorizationProxyStub.scope, equalTo(Scope.cluster));
  }

  @Test
  public void verify_authorizationScope_isNamespace_whenNamespaceIsDefined() {
    AuthenticationProxy authorizationProxy = new AuthenticationProxy();
    authorizationProxy.check("", "", "NS");
    assertThat(authorizationProxyStub.scope, equalTo(Scope.namespace));
  }

  private static class AuthorizationProxyStub extends AuthorizationProxy {
    Scope scope;

    public boolean check(
        String principal,
        Operation operation,
        Resource resource,
        String resourceName,
        Scope scope,
        String namespaceName) {
      this.scope = scope;
      return true;
    }
  }
}
