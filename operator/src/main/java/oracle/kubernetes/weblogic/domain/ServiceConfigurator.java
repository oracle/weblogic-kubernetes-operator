// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

@SuppressWarnings("UnusedReturnValue")
public interface ServiceConfigurator {
  ServiceConfigurator withServiceLabel(String name, String value);

  ServiceConfigurator withServiceAnnotation(String name, String value);
}
