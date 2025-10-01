// Copyright (c) 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.extensions;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayNameGenerator;

public class CustomReportGenerator extends DisplayNameGenerator.Standard {

  @Override
  public String generateDisplayNameForClass(Class<?> testClass) {
    // Always return the canonical class name, ignore @DisplayName on the class
    return testClass.getCanonicalName();
  }

  @Override
  public String generateDisplayNameForNestedClass(Class<?> nestedClass) {
    // Always return the canonical nested class name
    return nestedClass.getCanonicalName();
  }

  @Override
  public String generateDisplayNameForMethod(Class<?> testClass, Method testMethod) {
    // Use the standard generator (methodName[param...]) without @DisplayName
    return super.generateDisplayNameForMethod(testClass, testMethod);
  }
}
