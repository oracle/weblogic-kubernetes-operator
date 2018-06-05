// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import org.junit.Test;

public class MainTest {

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
