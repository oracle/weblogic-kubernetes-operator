// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.operator.ServerStartPolicy;

public interface ConfigurationConstants {
  String START_ADMIN_ONLY = ServerStartPolicy.ADMIN_ONLY.name();
  String START_NEVER = ServerStartPolicy.NEVER.name();
  String START_ALWAYS = ServerStartPolicy.ALWAYS.name();
  String START_IF_NEEDED = ServerStartPolicy.IF_NEEDED.name();
}
