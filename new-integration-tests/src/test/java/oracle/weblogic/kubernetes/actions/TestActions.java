package oracle.weblogic.kubernetes.actions;

import oracle.weblogic.kubernetes.actions.impl.Operator;

// this class essentially delegates to the impl classes, and "hides" all of the
// detail impl classes - tests would only ever call methods in here, never
// directly call the methods in the impl classes
public class TestActions {

    public static boolean installOperator() {
        return Operator.install();
    }

}
