// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

import oracle.kubernetes.weblogic.domain.model.Introspector;

@SuppressWarnings("UnusedReturnValue")
public interface IntrospectorConfigurator extends IntrospectorJobPodConfigurator {

  Introspector getIntrospector();
}
