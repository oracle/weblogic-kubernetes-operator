// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MainTest {

  private static final String NS = "default";
  private static final String DOMAIN_UID = "domain-uid-for-testing";
  private Method getTargetNamespaces;

  @After
  public void tearDown() {
  }

  @Test
  public void getTargetNamespaces_withEmptyValue_should_return_default()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Collection<String> namespaces = invoke_getTargetNamespaces("", NS);
    assertTrue(namespaces.contains("default"));
  }

  @Test
  public void getTargetNamespaces_withNonEmptyValue_should_not_return_default()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Collection<String> namespaces = invoke_getTargetNamespaces("dev-domain", NS);
    assertFalse(namespaces.contains("default"));
  }

  @Test
  public void getTargetNamespaces_with_single_target_should_return_it()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Collection<String> namespaces = invoke_getTargetNamespaces("dev-domain", NS);
    assertTrue(namespaces.contains("dev-domain"));
  }

  @Test
  public void getTargetNamespaces_with_multiple_targets_should_include_all()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Collection<String> namespaces =
        invoke_getTargetNamespaces("dev-domain,domain1,test-domain", NS);
    assertTrue(namespaces.contains("dev-domain"));
    assertTrue(namespaces.contains("domain1"));
    assertTrue(namespaces.contains("test-domain"));
  }

  @Test
  public void getTargetNamespaces_should_remove_leading_spaces()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Collection<String> namespaces = invoke_getTargetNamespaces(" test-domain, dev-domain", NS);
    assertTrue(namespaces.contains("dev-domain"));
    assertTrue(namespaces.contains("test-domain"));
  }

  @Test
  public void getTargetNamespaces_should_remove_trailing_spaces()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Collection<String> namespaces = invoke_getTargetNamespaces("dev-domain ,test-domain ", NS);
    assertTrue(namespaces.contains("dev-domain"));
    assertTrue(namespaces.contains("test-domain"));
  }

  private V1ObjectMeta createMetadata(DateTime creationTimestamp) {
    return new V1ObjectMeta()
        .name(DOMAIN_UID)
        .namespace(NS)
        .creationTimestamp(creationTimestamp)
        .resourceVersion("1");
  }

  @Test
  public void deleteDomainPresenceWithTimeCheck_delete_with_same_DateTime() {
    DateTime creationDatetime = DateTime.now();
    V1ObjectMeta domainMeta = createMetadata(creationDatetime);

    V1ObjectMeta domain2Meta = createMetadata(creationDatetime);

    assertFalse(KubernetesUtils.isFirstNewer(domainMeta, domain2Meta));
  }

  @Test
  public void deleteDomainPresenceWithTimeCheck_delete_with_newer_DateTime() {
    DateTime creationDatetime = DateTime.now();
    V1ObjectMeta domainMeta = createMetadata(creationDatetime);

    DateTime deleteDatetime = creationDatetime.plusMinutes(1);
    V1ObjectMeta domain2Meta = createMetadata(deleteDatetime);

    assertFalse(KubernetesUtils.isFirstNewer(domainMeta, domain2Meta));
  }

  @Test
  public void deleteDomainPresenceWithTimeCheck_doNotDelete_with_older_DateTime() {
    DateTime creationDatetime = DateTime.now();
    V1ObjectMeta domainMeta = createMetadata(creationDatetime);

    DateTime deleteDatetime = creationDatetime.minusMinutes(1);
    V1ObjectMeta domain2Meta = createMetadata(deleteDatetime);

    assertTrue(KubernetesUtils.isFirstNewer(domainMeta, domain2Meta));
  }

  @SuppressWarnings({"unchecked", "SameParameterValue"})
  private Collection<String> invoke_getTargetNamespaces(String tnValue, String namespace)
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    if (getTargetNamespaces == null) {
      getTargetNamespaces =
          Main.class.getDeclaredMethod("getTargetNamespaces", String.class, String.class);
      getTargetNamespaces.setAccessible(true);
    }
    return (Collection<String>) getTargetNamespaces.invoke(null, tnValue, namespace);
  }
}
