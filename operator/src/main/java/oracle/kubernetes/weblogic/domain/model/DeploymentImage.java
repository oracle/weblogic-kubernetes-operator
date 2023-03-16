// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

public interface DeploymentImage {

  String getImage();

  String getImagePullPolicy();

  String getSourceModelHome();

  String getSourceWDTInstallHome();

  String getSourceWDTInstallHomeOrDefault();
}
