// Copyright 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions;

import java.util.List;

import oracle.weblogic.kubernetes.actions.impl.Domain;
import oracle.weblogic.kubernetes.actions.impl.Namespace;
import oracle.weblogic.kubernetes.actions.impl.Operator;
import oracle.weblogic.kubernetes.actions.impl.primitive.Installer;
import oracle.weblogic.kubernetes.actions.impl.primitive.InstallParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.WebLogicImageTool;
import oracle.weblogic.kubernetes.actions.impl.primitive.WITParams;

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

    // ------------------------ Docker image  -------------------------

    public static boolean createMIIImage(WITParams params) {
    	return 
            new WebLogicImageTool()
                .with(params)
                .updateImage();
    }
 
 
    // ------------------------ Installer  -------------------------

    public static boolean verifyAndInstallWIT(String version, String location) {
        return new Installer()
            .with(new InstallParams()
                  .type("WIT")
                  .version(version)
                  .location(location))
            .download();
    }
 
    public static boolean verifyAndInstallWDT(String version, String location) {
        return new Installer()
            .with(new InstallParams()
                  .type("WDT")
                  .version(version)
                  .location(location))
            .download();

    }
 
}
