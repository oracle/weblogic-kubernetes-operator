// Copyright 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions;

import oracle.weblogic.kubernetes.assertions.impl.Domain;
import oracle.weblogic.kubernetes.assertions.impl.Operator;

import java.util.concurrent.Callable;

// as in the actions, it is intended tests only use these assertaions and do
// not go direct to the impl classes
public class TestAssertions {

    public static Callable<Boolean> operatorIsRunning() {
        return Operator.isRunning();
    }

    public static Callable<Boolean> domainExists(String domainUID, String namespace) {
        return Domain.exists(domainUID, namespace);
    }

}
