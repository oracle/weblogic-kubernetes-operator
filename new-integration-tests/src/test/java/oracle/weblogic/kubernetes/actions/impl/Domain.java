// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

import java.util.List;

public class Domain {

    public static boolean createDomainCustomResource(String domainUID, String namespace, String domainYAML) {
        return true;
    }

    public static List<String> listDomainCustomResources(String namespace) throws ApiException {
        return Kubernetes.listDomains(namespace);
    }
}
