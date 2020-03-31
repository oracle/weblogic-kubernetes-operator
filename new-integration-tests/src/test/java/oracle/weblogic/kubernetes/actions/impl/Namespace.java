// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class Namespace {

  public static String createUniqueNamespace() throws ApiException {
    return Kubernetes.createUniqueNamespace();
  }

  public static boolean createNamespace(String name) throws ApiException {
    return Kubernetes.createNamespace(name);
  }

  public static List<String> listNamespaces() throws ApiException {
    return Kubernetes.listNamespaces();
  }

  public static boolean deleteNamespace(String name) throws ApiException {
    return Kubernetes.deleteNamespace(name);
  }
}
