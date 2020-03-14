package oracle.weblogic.kubernetes.actions.impl;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubectl.kubectl;

public class Operator {

    // it is intended that methods in these impl classes are only ever called by
    // TestActions.java, and never directly from a test
    public static boolean install() {
        // i would use primitives to do what i need to do
        return kubectl("apply -f src/test/resources/operator.yaml");
    }

}
