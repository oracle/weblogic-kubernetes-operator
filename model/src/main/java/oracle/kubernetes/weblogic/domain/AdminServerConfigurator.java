// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

import oracle.kubernetes.weblogic.domain.v1.ExportedNetworkAccessPoint;

public interface AdminServerConfigurator extends ServerConfigurator {

  AdminServerConfigurator withPort(int port);

  AdminServerConfigurator withExportedNetworkAccessPoints(String... names);

  ExportedNetworkAccessPoint configureExportedNetworkAccessPoint(String channelName);
}
