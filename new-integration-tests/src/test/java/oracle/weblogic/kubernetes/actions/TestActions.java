// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.actions.impl.Domain;
import oracle.weblogic.kubernetes.actions.impl.Namespace;
import oracle.weblogic.kubernetes.actions.impl.Operator;

import java.util.List;

// this class essentially delegates to the impl classes, and "hides" all of the
// detail impl classes - tests would only ever call methods in here, never
// directly call the methods in the impl classes
public class TestActions {

    // ----------------------   operator  ---------------------------------

    public static boolean installOperator() {
        return Operator.install();
    }

    // ----------------------   domain  -----------------------------------

    public static boolean createDomainCustomResource(String domainUID, String namespace, String domainYAML) {
        return Domain.createDomainCustomResource(domainUID, namespace, domainYAML);
    }

    public static List<String> listDomainCustomResources(String namespace) throws ApiException {
        return Domain.listDomainCustomResources(namespace);
    }

    // ------------------------   ingress controller ----------------------



    // -------------------------  namespaces -------------------------------

    public static boolean createNamespace(String name) throws ApiException {
        return Namespace.createNamespace(name);
    }

    public static String createUniqueNamespace() throws ApiException {
        return Namespace.createUniqueNamespace();
    }

    public static List<String> listNamespaces() throws ApiException {
        return Namespace.listNamespaces();
    }

    public static boolean deleteNamespace(String name) throws ApiException {
        return Namespace.deleteNamespace(name);
    }

    // etc...

}
