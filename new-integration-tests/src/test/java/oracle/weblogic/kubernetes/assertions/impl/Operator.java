package oracle.weblogic.kubernetes.assertions.impl;

import java.util.Random;
import java.util.concurrent.Callable;

public class Operator {

    public static Callable<Boolean> isRunning() {
        // this uses a rand, to simulate that this opertion can take
        // variable amounts of time to complete
        return () -> new Random().nextInt(3) == 1 ? true : false;
    }

}
