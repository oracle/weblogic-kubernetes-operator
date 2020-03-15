package oracle.weblogic.kubernetes.actions;

import oracle.weblogic.kubernetes.actions.impl.Domain;
import oracle.weblogic.kubernetes.actions.impl.Namespace;
import oracle.weblogic.kubernetes.actions.impl.Operator;

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

    // ------------------------   ingress controller ----------------------



    // -------------------------  namespaces -------------------------------

    public static boolean createNamespace(String name) {
        return Namespace.createNamespace(name);
    }

    public static String createUniqueNamespace() {
        return Namespace.createUniqueNamespace();
    }

    // etc...

}
