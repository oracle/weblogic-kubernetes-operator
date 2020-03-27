// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.Random;
import java.util.concurrent.Callable;

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

public class Domain {

    public static Callable<Boolean> exists(String domainUID, String namespace) {
        return () -> {
            int outcome = new Random(System.currentTimeMillis()).nextInt(3);
            if (outcome == 1) {
                logger.info(String.format("Domain %s exists in namespace %s", domainUID, namespace));
                return true;
            } else {
                logger.info(String.format("Domain %s does not exist in namespace %s", domainUID, namespace));
                return false;
            }
        };
    }

}
