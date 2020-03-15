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
