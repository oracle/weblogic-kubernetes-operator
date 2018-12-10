// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

import oracle.kubernetes.weblogic.domain.v2.ExportedNetworkAccessPoint;

@SuppressWarnings("UnusedReturnValue")
public interface AdminServerConfigurator extends ServerConfigurator {

  AdminServerConfigurator withNodePort(int nodePort);

  AdminServerConfigurator withExportedNetworkAccessPoints(String... names);

  ExportedNetworkAccessPoint configureExportedNetworkAccessPoint(String channelName);

  AdminServerConfigurator withNodePortLabel(String name, String value);

  AdminServerConfigurator withNodePortAnnotation(String name, String value);
}
