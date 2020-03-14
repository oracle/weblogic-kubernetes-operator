package oracle.weblogic.kubernetes.assertions;

import oracle.weblogic.kubernetes.assertions.impl.Operator;

import java.util.concurrent.Callable;

// as in the actions, it is intended tests only use these assertaions and do
// not go direct to the impl classes
public class TestAssertions {

    public static Callable<Boolean> operatorIsRunning() {
        return Operator.isRunning();
    }
}
