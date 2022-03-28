// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.operator.ServerStartPolicy;

public interface ConfigurationConstants {
  String START_ADMIN_ONLY = ServerStartPolicy.ADMIN_ONLY.label();
  String START_NEVER = ServerStartPolicy.NEVER.label();
  String START_ALWAYS = ServerStartPolicy.ALWAYS.label();
  String START_IF_NEEDED = ServerStartPolicy.IF_NEEDED.label();
}
