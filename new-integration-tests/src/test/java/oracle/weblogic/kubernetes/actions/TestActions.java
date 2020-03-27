// Copyright 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions;

import java.util.HashMap;

import oracle.weblogic.kubernetes.actions.impl.ConfigMap;
import oracle.weblogic.kubernetes.actions.impl.Domain;
import oracle.weblogic.kubernetes.actions.impl.Namespace;
import oracle.weblogic.kubernetes.actions.impl.Operator;
import oracle.weblogic.kubernetes.actions.impl.PersistentVolume;
import oracle.weblogic.kubernetes.actions.impl.PersistentVolumeClaim;
import oracle.weblogic.kubernetes.actions.impl.Secret;
import oracle.weblogic.kubernetes.actions.impl.Traefik;

// this class essentially delegates to the impl classes, and "hides" all of the
// detail impl classes - tests would only ever call methods in here, never
// directly call the methods in the impl classes
public class TestActions {

    // ----------------------   operator  ---------------------------------

    public static boolean installOperator() {
        return Operator.install();
    }

    public static boolean upgradeOperator(HashMap<String, String> values) {
        return Operator.upgrade(values);
    }

    public static boolean scaleDomain(String domainUID, String clusterName, int numOfServers) {
        return Operator.scaleDomain(domainUID, clusterName, numOfServers);
    }

    public static boolean deleteOperator() { return Operator.delete(); }

    // ----------------------   domain  -----------------------------------

    public static boolean createDomainCustomResource(String domainUID, String namespace, String domainYAML) {
        return Domain.createDomainCustomResource(domainUID, namespace, domainYAML);
    }

    public static boolean shutdown(String domainUID, String namespace) {
        return Domain.shutdown(domainUID, namespace);
    }

    public static boolean restart(String domainUID, String namespace) {
        return Domain.restart(domainUID, namespace);
    }

    public static boolean deleteDomainCustomResource(String domainUID, String namespace) {
        return Domain.deleteDomainCustomResource(domainUID, namespace);
    }

    // ------------------------   ingress controller ----------------------

    public static boolean install(String valuesYaml) {
        return Traefik.install(valuesYaml);
    }

    public static boolean createIngress(String valuesYaml) {
        return Traefik.createIngress(valuesYaml);
    }

    // -------------------------  namespaces -------------------------------

    public static boolean createNamespace(String name) {
        return Namespace.createNamespace(name);
    }

    public static String createUniqueNamespace() {
        return Namespace.createUniqueNamespace();
    }

    // -------------------------   pv/pvc  ---------------------------------

    public static boolean createPersistentVolume(String pvYaml) {
        return PersistentVolume.create(pvYaml);
    }

    public static boolean createPersistentVolumeClaim(String pvcYaml) {
        return PersistentVolumeClaim.create(pvcYaml);
    }

    // --------------------------  secret  ----------------------------------

    public static boolean createSecret(String secretYaml) {
        return Secret.create(secretYaml);
    }

    // -------------------------- config map ---------------------------------

    public static boolean createConfigMap(String cmName, String namespace, String fromFile) {
        return ConfigMap.create(cmName, namespace, fromFile);
    }

    // ------------------------ where does this go  -------------------------

    public static boolean createImage(String imageName, String imageTag) {
        return true;
    }
    public static boolean deployApplication(String appName, String appLocation, String t3Url,
                                         String username, String password, String target) {
        return true;
    }

    // etc...

}
