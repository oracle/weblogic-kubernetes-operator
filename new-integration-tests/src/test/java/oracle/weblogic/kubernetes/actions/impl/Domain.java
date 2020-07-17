// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PolicyRule;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.kubernetes.client.openapi.models.V1RoleRef;
import io.kubernetes.client.openapi.models.V1Subject;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.DomainList;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.PROJECT_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RBAC_API_GROUP;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RBAC_API_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RBAC_CLUSTER_ROLE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RBAC_CLUSTER_ROLE_BINDING;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RBAC_ROLE_BINDING;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLDF_CLUSTER_ROLE_BINDING_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLDF_CLUSTER_ROLE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLDF_ROLE_BINDING_NAME;
import static oracle.weblogic.kubernetes.actions.impl.ClusterRole.createClusterRole;
import static oracle.weblogic.kubernetes.actions.impl.ClusterRoleBinding.createClusterRoleBinding;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.copyFileToPod;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.createNamespacedRoleBinding;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.exec;
import static oracle.weblogic.kubernetes.assertions.impl.ClusterRole.clusterRoleExists;
import static oracle.weblogic.kubernetes.assertions.impl.ClusterRoleBinding.clusterRoleBindingExists;
import static oracle.weblogic.kubernetes.assertions.impl.RoleBinding.roleBindingExists;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;

public class Domain {

  /**
   * Create a domain custom resource.
   *
   * @param domain Domain custom resource model object
   * @return true on success, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createDomainCustomResource(oracle.weblogic.domain.Domain domain)
      throws ApiException {
    return Kubernetes.createDomainCustomResource(domain);
  }

  /**
   * List all Custom Resource Domains in a namespace.
   *
   * @param namespace name of namespace
   * @return list of Custom Resource Domains for a given namespace
   */
  public static DomainList listDomainCustomResources(String namespace) {
    return Kubernetes.listDomains(namespace);
  }

  /**
   * Shut down a domain in the specified namespace.
   * @param domainUid the domain to shut down
   * @param namespace the namespace in which the domain exists
   * @return true if patching domain custom resource succeeded, false otherwise
   */
  public static boolean shutdown(String domainUid, String namespace) {
    LoggingFacade logger = getLogger();
    // change the /spec/serverStartPolicy to NEVER to shut down all servers in the domain
    // create patch string to shut down the domain
    StringBuffer patchStr = new StringBuffer("[{")
        .append("\"op\": \"replace\", ")
        .append("\"path\": \"/spec/serverStartPolicy\", ")
        .append("\"value\": \"NEVER\"")
        .append("}]");

    logger.info("Shutting down domain {0} in namespace {1} using patch string: {2}",
        domainUid, namespace, patchStr.toString());

    V1Patch patch = new V1Patch(new String(patchStr));

    return patchDomainCustomResource(domainUid, namespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
  }

  /**
   * Start domain in the specified namespace.
   *
   * @param domainUid the domain to restart
   * @param namespace the namespace in which the domain exists
   * @return true if patching domain resource succeeded, false otherwise
   */
  public static boolean start(String domainUid, String namespace) {
    LoggingFacade logger = getLogger();
    // change the /spec/serverStartPolicy to IF_NEEDED to start all servers in the domain
    // create patch string to start the domain
    StringBuffer patchStr = new StringBuffer("[{")
        .append("\"op\": \"replace\", ")
        .append("\"path\": \"/spec/serverStartPolicy\", ")
        .append("\"value\": \"IF_NEEDED\"")
        .append("}]");

    logger.info("Restarting domain {0} in namespace {1} using patch string: {2}",
        domainUid, namespace, patchStr.toString());

    V1Patch patch = new V1Patch(new String(patchStr));

    return patchDomainCustomResource(domainUid, namespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
  }

  /**
   * Delete a Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean deleteDomainCustomResource(String domainUid, String namespace) {
    return Kubernetes.deleteDomainCustomResource(domainUid, namespace);
  }

  /**
   * Get a Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @return domain custom resource or null if Domain does not exist
   * @throws ApiException if Kubernetes request fails
   */
  public static oracle.weblogic.domain.Domain getDomainCustomResource(String domainUid,
                                                                      String namespace) throws ApiException {
    return Kubernetes.getDomainCustomResource(domainUid, namespace);
  }

  /**
   * Patch the Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @param patch patch data in format matching the specified media type
   * @param patchFormat one of the following types used to identify patch document:
   *     "application/json-patch+json", "application/merge-patch+json",
   * @return true if successful, false otherwise
   */
  public static boolean patchDomainCustomResource(String domainUid, String namespace, V1Patch patch,
                                                  String patchFormat) {
    return Kubernetes.patchDomainCustomResource(domainUid, namespace, patch, patchFormat);
  }

  /**
   * Patch a running domain with introspectVersion.
   * If the introspectVersion doesn't exist it will add the value as 2,
   * otherwise the value is updated by 1.
   *
   * @param domainUid UID of the domain to patch with introspectVersion
   * @param namespace namespace in which the domain resource exists
   * @return true if patching is successful, otherwise false
   * @throws ApiException when patching fails
   */
  public static boolean patchDomainResourceWithNewIntrospectVersion(
      String domainUid, String namespace) throws ApiException {
    LoggingFacade logger = getLogger();
    StringBuffer patchStr;
    oracle.weblogic.domain.Domain res = getDomainCustomResource(domainUid, namespace);
    // construct the patch string
    if (res.getSpec().getIntrospectVersion() == null) {
      patchStr = new StringBuffer("[{")
          .append("\"op\": \"add\", ")
          .append("\"path\": \"/spec/introspectVersion\", ")
          .append("\"value\": \"2")
          .append("\"}]");
    } else {
      int introspectVersion = Integer.parseInt(res.getSpec().getIntrospectVersion()) + 1;
      patchStr = new StringBuffer("[{")
          .append("\"op\": \"replace\", ")
          .append("\"path\": \"/spec/introspectVersion\", ")
          .append("\"value\": \"")
          .append(introspectVersion)
          .append("\"}]");
    }

    logger.info("Patch String \n{0}", patchStr);
    logger.info("Adding/updating introspectVersion in domain {0} in namespace {1} using patch string: {2}",
        domainUid, namespace, patchStr.toString());

    // patch the domain
    V1Patch patch = new V1Patch(new String(patchStr));
    return patchDomainCustomResource(domainUid, namespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);

  }

  /**
   * Scale the cluster of the domain in the specified namespace.
   *
   * @param domainUid domainUid of the domain to be scaled
   * @param namespace namespace in which the domain exists
   * @param clusterName name of the WebLogic cluster to be scaled in the domain
   * @param numOfServers number of servers to be scaled to
   * @return true if patch domain custom resource succeeds, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean scaleCluster(String domainUid, String namespace, String clusterName, int numOfServers)
      throws ApiException {
    LoggingFacade logger = getLogger();
    // get the domain cluster list
    oracle.weblogic.domain.Domain domain = getDomainCustomResource(domainUid, namespace);

    List<Cluster> clusters = new ArrayList<>();
    if (domain.getSpec() != null) {
      clusters = domain.getSpec().getClusters();
    }

    // get the index of the cluster with clusterName in the cluster list
    int index = 0;
    for (int i = 0; i < clusters.size(); i++) {
      if (clusters.get(i).getClusterName().equals(clusterName)) {
        index = i;
        break;
      }
    }

    // construct the patch string for scaling the cluster in the domain
    StringBuffer patchStr = new StringBuffer("[{")
        .append("\"op\": \"replace\", ")
        .append("\"path\": \"/spec/clusters/")
        .append(index)
        .append("/replicas\", ")
        .append("\"value\": ")
        .append(numOfServers)
        .append("}]");

    logger.info("Scaling cluster {0} in domain {1} using patch string: {2}",
        clusterName, domainUid, patchStr.toString());

    V1Patch patch = new V1Patch(new String(patchStr));

    return Kubernetes.patchDomainCustomResource(domainUid, namespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
  }

  /**
   * Scale the cluster of the domain in the specified namespace with REST API.
   *
   * @param domainUid domainUid of the domain to be scaled
   * @param clusterName name of the WebLogic cluster to be scaled in the domain
   * @param numOfServers number of servers to be scaled to
   * @param externalRestHttpsPort node port allocated for the external operator REST HTTPS interface
   * @param opNamespace namespace of WebLogic operator
   * @param opServiceAccount the service account for operator
   * @return true if REST call succeeds, false otherwise
   */
  public static boolean scaleClusterWithRestApi(String domainUid,
                                                String clusterName,
                                                int numOfServers,
                                                int externalRestHttpsPort,
                                                String opNamespace,
                                                String opServiceAccount) {
    LoggingFacade logger = getLogger();
    logger.info("Getting the secret of service account {0} in namespace {1}", opServiceAccount, opNamespace);
    String secretName = Secret.getSecretOfServiceAccount(opNamespace, opServiceAccount);
    if (secretName.isEmpty()) {
      logger.info("Did not find secret of service account {0} in namespace {1}", opServiceAccount, opNamespace);
      return false;
    }
    logger.info("Got secret {0} of service account {1} in namespace {2}",
        secretName, opServiceAccount, opNamespace);

    logger.info("Getting service account token stored in secret {0} to authenticate as service account {1}"
        + " in namespace {2}", secretName, opServiceAccount, opNamespace);
    String secretToken = Secret.getSecretEncodedToken(opNamespace, secretName);
    if (secretToken.isEmpty()) {
      logger.info("Did not get encoded token for secret {0} associated with service account {1} in namespace {2}",
          secretName, opServiceAccount, opNamespace);
      return false;
    }
    logger.info("Got encoded token for secret {0} associated with service account {1} in namespace {2}: {3}",
        secretName, opServiceAccount, opNamespace, secretToken);

    // decode the secret encoded token
    String decodedToken = new String(Base64.getDecoder().decode(secretToken));
    logger.info("Got decoded token for secret {0} associated with service account {1} in namespace {2}: {3}",
        secretName, opServiceAccount, opNamespace, decodedToken);

    // build the curl command to scale the cluster
    String command = new StringBuffer()
        .append("curl --noproxy '*' -v -k ")
        .append("-H \"Authorization:Bearer ")
        .append(decodedToken)
        .append("\" ")
        .append("-H Accept:application/json ")
        .append("-H Content-Type:application/json ")
        .append("-H X-Requested-By:MyClient ")
        .append("-d '{\"managedServerCount\": ")
        .append(numOfServers)
        .append("}' ")
        .append("-X POST https://")
        .append(K8S_NODEPORT_HOST)
        .append(":")
        .append(externalRestHttpsPort)
        .append("/operator/latest/domains/")
        .append(domainUid)
        .append("/clusters/")
        .append(clusterName)
        .append("/scale").toString();

    CommandParams params = Command
        .defaultCommandParams()
        .command(command)
        .saveResults(true)
        .redirect(true);

    return Command.withParams(params).execute();
  }

  /**
   * Scale the cluster of the domain in the specified namespace with WLDF.
   *
   * @param clusterName name of the WebLogic cluster to be scaled in the domain
   * @param domainUid domainUid of the domain to be scaled
   * @param domainNamespace domain namespace in which the domain exists
   * @param domainHomeLocation domain home location of the domain
   * @param scalingAction scaling action, accepted value: scaleUp or scaleDown
   * @param scalingSize number of servers to be scaled up or down
   * @param opNamespace namespace of WebLogic operator
   * @param opServiceAccount service account of operator
   * @param myWebAppName web app name deployed to the domain used in the WLDF policy expression
   * @param curlCommand curl command to call the web app used in the WLDF policy expression
   * @return true if scaling the cluster succeeds, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   * @throws IOException if an I/O error occurs
   * @throws InterruptedException if any thread has interrupted the current thread
   */
  public static boolean scaleClusterWithWLDF(String clusterName,
                                             String domainUid,
                                             String domainNamespace,
                                             String domainHomeLocation,
                                             String scalingAction,
                                             int scalingSize,
                                             String opNamespace,
                                             String opServiceAccount,
                                             String myWebAppName,
                                             String curlCommand)
      throws ApiException, IOException, InterruptedException {
    LoggingFacade logger = getLogger();
    // create RBAC API objects for WLDF script
    logger.info("Creating RBAC API objects for WLDF script");
    if (!createRbacApiObjectsForWLDFScript(domainNamespace, opNamespace)) {
      return false;
    }

    // copy scalingAction.sh to Admin Server pod
    // NOTE: you must copy scalingAction.sh to $DOMAIN_HOME/bin/scripts on admin server pod
    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    V1Pod adminPod = Kubernetes.getPod(domainNamespace, null, adminServerPodName);
    if (adminPod == null) {
      logger.info("The admin pod {0} does not exist in namespace {1}!", adminServerPodName, domainNamespace);
      return false;
    }

    // create $DOMAIN_HOME/bin/scripts directory on admin server pod
    logger.info("Creating directory {0}/bin/scripts on admin server pod", domainHomeLocation);
    ExecResult result = exec(adminPod, null, true,
        "/bin/sh", "-c", "mkdir -p " + domainHomeLocation + "/bin/scripts");
    if (result.exitValue() != 0) {
      return false;
    }

    logger.info("Copying scalingAction.sh to admin server pod");
    try {
      copyFileToPod(domainNamespace, adminServerPodName, null,
          Paths.get(PROJECT_ROOT + "/../src/scripts/scaling/scalingAction.sh"),
          Paths.get(domainHomeLocation + "/bin/scripts/scalingAction.sh"));
    } catch (ApiException apex) {
      logger.severe("Got ApiException while copying file to admin pod {0}", apex.getResponseBody());
      return false;
    } catch (IOException ioex) {
      logger.severe("Got IOException while copying file to admin pod {0}", ioex.getStackTrace());
      return false;
    }

    logger.info("Adding execute mode for scalingAction.sh");
    result = exec(adminPod, null, true,
        "/bin/sh", "-c", "chmod +x " + domainHomeLocation + "/bin/scripts/scalingAction.sh");
    if (result.exitValue() != 0) {
      return false;
    }

    // copy wldf.py and callpyscript.sh to Admin Server pod
    logger.info("Copying wldf.py and callpyscript.sh to admin server pod");
    try {
      copyFileToPod(domainNamespace, adminServerPodName, null,
          Paths.get(RESOURCE_DIR, "python-scripts", "wldf.py"),
          Paths.get("/u01/oracle/wldf.py"));

      copyFileToPod(domainNamespace, adminServerPodName, null,
          Paths.get(RESOURCE_DIR, "bash-scripts", "callpyscript.sh"),
          Paths.get("/u01/oracle/callpyscript.sh"));
    } catch (ApiException apex) {
      logger.severe("Got ApiException while copying file to admin pod {0}", apex.getResponseBody());
      return false;
    } catch (IOException ioex) {
      logger.severe("Got IOException while copying file to admin pod {0}", ioex.getStackTrace());
      return false;
    }

    logger.info("Adding execute mode for callpyscript.sh");
    result = exec(adminPod, null, true,
        "/bin/sh", "-c", "chmod +x /u01/oracle/callpyscript.sh");
    if (result.exitValue() != 0) {
      return false;
    }

    if (!scalingAction.equals("scaleUp") && !scalingAction.equals("scaleDown")) {
      logger.info("Set scaleAction to either scaleUp or scaleDown");
      return false;
    }

    logger.info("Creating WLDF policy rule and action");
    String command = new StringBuffer("/u01/oracle/callpyscript.sh /u01/oracle/wldf.py ")
        .append(ADMIN_USERNAME_DEFAULT)
        .append(" ")
        .append(ADMIN_PASSWORD_DEFAULT)
        .append(" t3://")
        .append(adminServerPodName)
        .append(":7001 ")
        .append(scalingAction)
        .append(" ")
        .append(domainUid)
        .append(" ")
        .append(clusterName)
        .append(" ")
        .append(domainNamespace)
        .append(" ")
        .append(opNamespace)
        .append(" ")
        .append(opServiceAccount)
        .append(" ")
        .append(scalingSize)
        .append(" ")
        .append(myWebAppName).toString();

    result = exec(adminPod, null, true, "/bin/sh", "-c", command);
    if (result.exitValue() != 0) {
      return false;
    }

    // sleep for a while to make sure the diagnostic modules are created
    try {
      Thread.sleep(5000);
    } catch (InterruptedException ex) {
      // ignore
    }

    // call the web app to trigger the WLDF policy which will call the script action to scale the cluster
    CommandParams params = Command
        .defaultCommandParams()
        .command(curlCommand)
        .saveResults(true)
        .redirect(true);

    return Command.withParams(params).execute();
  }

  /**
   * Create cluster role, cluster role binding and role binding used by WLDF script action.
   *
   * @param domainNamespace WebLogic domain namespace
   * @param opNamespace WebLogic operator namespace
   */
  private static boolean createRbacApiObjectsForWLDFScript(String domainNamespace, String opNamespace)
      throws ApiException {
    LoggingFacade logger = getLogger();
    // create cluster role
    if (!clusterRoleExists(WLDF_CLUSTER_ROLE_NAME)) {
      logger.info("Creating cluster role {0}", WLDF_CLUSTER_ROLE_NAME);

      V1ClusterRole v1ClusterRole = new V1ClusterRole()
          .kind(RBAC_CLUSTER_ROLE)
          .apiVersion(RBAC_API_VERSION)
          .metadata(new V1ObjectMeta()
              .name(WLDF_CLUSTER_ROLE_NAME))
          .addRulesItem(new V1PolicyRule()
              .addApiGroupsItem("weblogic.oracle")
              .addResourcesItem("domains")
              .addVerbsItem("get")
              .addVerbsItem("list")
              .addVerbsItem("update"))
          .addRulesItem(new V1PolicyRule()
              .addApiGroupsItem("apiextensions.k8s.io")
              .addResourcesItem("customresourcedefinitions")
              .addVerbsItem("get")
              .addVerbsItem("list"));

      if (!createClusterRole(v1ClusterRole)) {
        return false;
      }
    }

    // create cluster role binding
    if (!clusterRoleBindingExists(WLDF_CLUSTER_ROLE_BINDING_NAME)) {
      logger.info("Creating cluster role binding {0}", WLDF_CLUSTER_ROLE_BINDING_NAME);

      V1ClusterRoleBinding v1ClusterRoleBinding = new V1ClusterRoleBinding()
          .kind(RBAC_CLUSTER_ROLE_BINDING)
          .apiVersion(RBAC_API_VERSION)
          .metadata(new V1ObjectMeta()
              .name(WLDF_CLUSTER_ROLE_BINDING_NAME))
          .addSubjectsItem(new V1Subject()
              .kind("ServiceAccount")
              .name("default")
              .namespace(domainNamespace)
              .apiGroup(""))
          .roleRef(new V1RoleRef()
              .kind(RBAC_CLUSTER_ROLE)
              .name(WLDF_CLUSTER_ROLE_NAME)
              .apiGroup(RBAC_API_GROUP));

      if (!createClusterRoleBinding(v1ClusterRoleBinding)) {
        return false;
      }
    }

    // create domain operator role binding
    if (!roleBindingExists(WLDF_ROLE_BINDING_NAME, opNamespace)) {
      logger.info("Creating role binding {0} in namespace {1}", WLDF_ROLE_BINDING_NAME, opNamespace);

      V1RoleBinding v1RoleBinding = new V1RoleBinding()
          .kind(RBAC_ROLE_BINDING)
          .apiVersion(RBAC_API_VERSION)
          .metadata(new V1ObjectMeta()
              .name(WLDF_ROLE_BINDING_NAME)
              .namespace(opNamespace))
          .addSubjectsItem(new V1Subject()
              .kind("ServiceAccount")
              .name("default")
              .namespace(domainNamespace)
              .apiGroup(""))
          .roleRef(new V1RoleRef()
              .kind(RBAC_CLUSTER_ROLE)
              .name("cluster-admin")
              .apiGroup(RBAC_API_GROUP));

      return createNamespacedRoleBinding(opNamespace, v1RoleBinding);
    }

    return true;
  }
}
