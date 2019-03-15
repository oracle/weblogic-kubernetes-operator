// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

import oracle.kubernetes.weblogic.domain.model.AdminService;

@SuppressWarnings("UnusedReturnValue")
public interface AdminServerConfigurator extends ServerConfigurator {

  AdminService configureAdminService();
}
