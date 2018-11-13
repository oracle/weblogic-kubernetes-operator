// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.kubernetes.client.models.V1ObjectMeta;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfoManager;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainSpec;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Test;

public class MainTest {

  final String DOMAIN_UID = "domain-uid-for-testing";

  @After
  public void tearDown() {
    DomainPresenceInfoManager.remove(DOMAIN_UID);
  }

  @Test
  public void getTargetNamespaces_withEmptyValue_should_return_default()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Collection<String> namespaces = invoke_getTargetNamespaces("", "default");
    assertTrue(namespaces.contains("default"));
  }

  @Test
  public void getTargetNamespaces_withNonEmptyValue_should_not_return_default()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Collection<String> namespaces = invoke_getTargetNamespaces("dev-domain", "default");
    assertFalse(namespaces.contains("default"));
  }

  @Test
  public void getTargetNamespaces_with_single_target_should_return_it()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Collection<String> namespaces = invoke_getTargetNamespaces("dev-domain", "default");
    assertTrue(namespaces.contains("dev-domain"));
  }

  @Test
  public void getTargetNamespaces_with_multiple_targets_should_include_all()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Collection<String> namespaces =
        invoke_getTargetNamespaces("dev-domain,domain1,test-domain", "default");
    assertTrue(namespaces.contains("dev-domain"));
    assertTrue(namespaces.contains("domain1"));
    assertTrue(namespaces.contains("test-domain"));
  }

  @Test
  public void getTargetNamespaces_should_remove_leading_spaces()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Collection<String> namespaces =
        invoke_getTargetNamespaces(" test-domain, dev-domain", "default");
    assertTrue(namespaces.contains("dev-domain"));
    assertTrue(namespaces.contains("test-domain"));
  }

  @Test
  public void getTargetNamespaces_should_remove_trailing_spaces()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Collection<String> namespaces =
        invoke_getTargetNamespaces("dev-domain ,test-domain ", "default");
    assertTrue(namespaces.contains("dev-domain"));
    assertTrue(namespaces.contains("test-domain"));
  }

  @Test
  public void deleteDomainPresenceWithTimeCheck_delete_with_same_DateTime() {
    Domain domain = new Domain();
    DomainSpec domainSpec = new DomainSpec();
    domainSpec.setDomainUID(DOMAIN_UID);
    DateTime CREATION_DATETIME = DateTime.now();
    V1ObjectMeta domainMeta = new V1ObjectMeta().creationTimestamp(CREATION_DATETIME);
    domain.setMetadata(domainMeta);
    domain.setSpec(domainSpec);

    DomainPresenceInfo info = DomainPresenceInfoManager.getOrCreate(domain);

    DomainPresenceInfo result =
        Main.deleteDomainPresenceWithTimeCheck(DOMAIN_UID, CREATION_DATETIME);

    assertEquals(info, result);
    assertNull(DomainPresenceInfoManager.lookup(DOMAIN_UID));
  }

  @Test
  public void deleteDomainPresenceWithTimeCheck_delete_with_newer_DateTime() {
    Domain domain = new Domain();
    DomainSpec domainSpec = new DomainSpec();
    domainSpec.setDomainUID(DOMAIN_UID);
    DateTime CREATION_DATETIME = DateTime.now();
    DateTime DELETE_DATETIME = CREATION_DATETIME.plusMinutes(1);
    V1ObjectMeta domainMeta = new V1ObjectMeta().creationTimestamp(CREATION_DATETIME);
    domain.setMetadata(domainMeta);
    domain.setSpec(domainSpec);

    DomainPresenceInfo info = DomainPresenceInfoManager.getOrCreate(domain);

    DomainPresenceInfo result = Main.deleteDomainPresenceWithTimeCheck(DOMAIN_UID, DELETE_DATETIME);

    assertEquals(info, result);
    assertNull(DomainPresenceInfoManager.lookup(DOMAIN_UID));
  }

  @Test
  public void deleteDomainPresenceWithTimeCheck_delete_with_null_DateTime() {
    Domain domain = new Domain();
    DomainSpec domainSpec = new DomainSpec();
    domainSpec.setDomainUID(DOMAIN_UID);
    DateTime CREATION_DATETIME = DateTime.now();
    V1ObjectMeta domainMeta = new V1ObjectMeta().creationTimestamp(CREATION_DATETIME);
    domain.setMetadata(domainMeta);
    domain.setSpec(domainSpec);

    DomainPresenceInfo info = DomainPresenceInfoManager.getOrCreate(domain);

    DomainPresenceInfo result = Main.deleteDomainPresenceWithTimeCheck(DOMAIN_UID, null);

    assertEquals(info, result);
    assertNull(DomainPresenceInfoManager.lookup(DOMAIN_UID));
  }

  @Test
  public void deleteDomainPresenceWithTimeCheck_delete_without_domain_creationDateTime() {
    Domain domain = new Domain();
    DomainSpec domainSpec = new DomainSpec();
    domainSpec.setDomainUID(DOMAIN_UID);
    DateTime DELETE_DATETIME = DateTime.now();
    domain.setMetadata(new V1ObjectMeta());
    domain.setSpec(domainSpec);

    DomainPresenceInfo info = DomainPresenceInfoManager.getOrCreate(domain);

    DomainPresenceInfo result = Main.deleteDomainPresenceWithTimeCheck(DOMAIN_UID, DELETE_DATETIME);

    assertEquals(info, result);
    assertNull(DomainPresenceInfoManager.lookup(DOMAIN_UID));
  }

  @Test
  public void
      deleteDomainPresenceWithTimeCheck_delete_without_domain_creationDateTime_null_dateTime() {
    Domain domain = new Domain();
    DomainSpec domainSpec = new DomainSpec();
    domainSpec.setDomainUID(DOMAIN_UID);
    domain.setMetadata(new V1ObjectMeta());
    domain.setSpec(domainSpec);

    DomainPresenceInfo info = DomainPresenceInfoManager.getOrCreate(domain);

    DomainPresenceInfo result = Main.deleteDomainPresenceWithTimeCheck(DOMAIN_UID, null);

    assertEquals(info, result);
    assertNull(DomainPresenceInfoManager.lookup(DOMAIN_UID));
  }

  @Test
  public void deleteDomainPresenceWithTimeCheck_doNotDelete_with_older_DateTime() {
    Domain domain = new Domain();
    DomainSpec domainSpec = new DomainSpec();
    domainSpec.setDomainUID(DOMAIN_UID);
    DateTime CREATION_DATETIME = DateTime.now();
    DateTime DELETE_DATETIME = CREATION_DATETIME.minusMinutes(1);
    V1ObjectMeta domainMeta = new V1ObjectMeta().creationTimestamp(CREATION_DATETIME);
    domain.setMetadata(domainMeta);
    domain.setSpec(domainSpec);

    DomainPresenceInfo info = DomainPresenceInfoManager.getOrCreate(domain);

    DomainPresenceInfo result = Main.deleteDomainPresenceWithTimeCheck(DOMAIN_UID, DELETE_DATETIME);

    assertNull(result);
    assertNotNull(DomainPresenceInfoManager.lookup(DOMAIN_UID));
  }

  @Test
  public void deleteDomainPresenceWithTimeCheck_doNotDelete_with_different_domainUID() {
    Domain domain = new Domain();
    DomainSpec domainSpec = new DomainSpec();
    domainSpec.setDomainUID(DOMAIN_UID);
    DateTime CREATION_DATETIME = DateTime.now();
    V1ObjectMeta domainMeta = new V1ObjectMeta().creationTimestamp(CREATION_DATETIME);
    domain.setMetadata(domainMeta);
    domain.setSpec(domainSpec);

    DomainPresenceInfo info = DomainPresenceInfoManager.getOrCreate(domain);

    DomainPresenceInfo result =
        Main.deleteDomainPresenceWithTimeCheck("DifferentDomainUID", CREATION_DATETIME);

    assertNull(result);
    assertNotNull(DomainPresenceInfoManager.lookup(DOMAIN_UID));
  }

  Method getTargetNamespaces;

  Collection<String> invoke_getTargetNamespaces(String tnValue, String namespace)
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    if (getTargetNamespaces == null) {
      getTargetNamespaces =
          Main.class.getDeclaredMethod("getTargetNamespaces", String.class, String.class);
      getTargetNamespaces.setAccessible(true);
    }
    return (Collection<String>) getTargetNamespaces.invoke(null, tnValue, namespace);
  }
}
