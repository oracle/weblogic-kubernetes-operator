// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import com.google.gson.JsonObject;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1IngressRule;
import io.kubernetes.client.openapi.models.V1IngressTLS;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Role;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1StorageClass;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.DomainList;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.AppBuilder;
import oracle.weblogic.kubernetes.actions.impl.AppParams;
import oracle.weblogic.kubernetes.actions.impl.Cluster;
import oracle.weblogic.kubernetes.actions.impl.ClusterRole;
import oracle.weblogic.kubernetes.actions.impl.ClusterRoleBinding;
import oracle.weblogic.kubernetes.actions.impl.ConfigMap;
import oracle.weblogic.kubernetes.actions.impl.Domain;
import oracle.weblogic.kubernetes.actions.impl.Exec;
import oracle.weblogic.kubernetes.actions.impl.Grafana;
import oracle.weblogic.kubernetes.actions.impl.GrafanaParams;
import oracle.weblogic.kubernetes.actions.impl.Ingress;
import oracle.weblogic.kubernetes.actions.impl.Job;
import oracle.weblogic.kubernetes.actions.impl.LoggingExporter;
import oracle.weblogic.kubernetes.actions.impl.LoggingExporterParams;
import oracle.weblogic.kubernetes.actions.impl.Namespace;
import oracle.weblogic.kubernetes.actions.impl.Nginx;
import oracle.weblogic.kubernetes.actions.impl.NginxParams;
import oracle.weblogic.kubernetes.actions.impl.Operator;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.PersistentVolume;
import oracle.weblogic.kubernetes.actions.impl.PersistentVolumeClaim;
import oracle.weblogic.kubernetes.actions.impl.Pod;
import oracle.weblogic.kubernetes.actions.impl.Prometheus;
import oracle.weblogic.kubernetes.actions.impl.PrometheusParams;
import oracle.weblogic.kubernetes.actions.impl.Secret;
import oracle.weblogic.kubernetes.actions.impl.Service;
import oracle.weblogic.kubernetes.actions.impl.ServiceAccount;
import oracle.weblogic.kubernetes.actions.impl.Traefik;
import oracle.weblogic.kubernetes.actions.impl.TraefikParams;
import oracle.weblogic.kubernetes.actions.impl.WebLogicRemoteConsole;
import oracle.weblogic.kubernetes.actions.impl.primitive.Helm;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Image;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.actions.impl.primitive.WebLogicImageTool;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.extensions.InitializationTasks;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ClusterUtils;
import oracle.weblogic.kubernetes.utils.DomainUtils;
import oracle.weblogic.kubernetes.utils.ExecResult;

import static oracle.weblogic.kubernetes.TestConstants.CLUSTER_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.impl.ConfigMap.doesCMExist;
import static oracle.weblogic.kubernetes.actions.impl.Operator.start;
import static oracle.weblogic.kubernetes.actions.impl.Operator.stop;
import static oracle.weblogic.kubernetes.actions.impl.Prometheus.uninstall;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// this class essentially delegates to the impl classes, and "hides" all of the
// detail impl classes - tests would only ever call methods in here, never
// directly call the methods in the impl classes
public class TestActions {
  // ----------------------   operator  ---------------------------------

  /**
   * Install WebLogic Kubernetes Operator.
   *
   * @param params operator parameters for Helm values
   * @return true if the operator is successfully installed, false otherwise.
   */
  public static boolean installOperator(OperatorParams params) {
    return Operator.install(params);
  }

  /**
   * Upgrade existing Operator release.
   *
   * @param params operator parameters for Helm values
   * @return true if the operator is successfully upgraded, false otherwise.
   */
  public static boolean upgradeOperator(OperatorParams params) {
    return Operator.upgrade(params);
  }

  /**
   * Uninstall the Operator release.
   *
   * @param params the parameters to Helm uninstall command, release name and namespace
   * @return true on success, false otherwise
   */

  public static boolean uninstallOperator(HelmParams params) {
    return Operator.uninstall(params);
  }

  /**
   * Image Name for the Operator. Uses branch name for image tag in local runs
   * and branch name, build id for image tag in Jenkins runs.
   *
   * @return image name
   */
  public static String getOperatorImageName() {
    return Operator.getImageName();
  }

  /**
   * Builds an Image for the Oracle WebLogic Kubernetes Operator.
   *
   * @param image image name and tag in 'name:tag' format
   * @return true on success
   */
  public static boolean buildOperatorImage(String image) {
    return Operator.buildImage(image);
  }

  /**
   * Get the image name used in the Operator container.
   * @param namespace namespace of the operator
   * @return image name
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String getOperatorContainerImageName(String namespace) throws ApiException {
    return Operator.getOperatorContainerImage(namespace);
  }

  /**
   * Stop operator by changing the replica in the operator deployment to 0.
   * @param namespace namespace of the operator
   * @return true on success
   */
  public static boolean stopOperator(String namespace) {
    return stop(namespace);
  }

  /**
   * Start operator by changing the replica in the operator deployment to 1.
   * @param namespace namespace of the operator
   * @return true on success
   */
  public static boolean startOperator(String namespace) {
    return start(namespace);
  }

  // ----------------------   domain  -----------------------------------

  /**
   * Create Domain Custom Resource.
   *
   * @param domain Domain custom resource model object
   * @param domainVersion custom resource's version
   * @return true on success, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createDomainCustomResource(DomainResource domain,
                                                   String... domainVersion) throws ApiException {
    return Domain.createDomainCustomResource(domain, domainVersion);
  }

  /**
   * List Domain Custom Resources.
   *
   * @param namespace name of namespace
   * @return List of Domain Custom Resources
   */
  public static DomainList listDomainCustomResources(String namespace) {
    return Domain.listDomainCustomResources(namespace);
  }

  /**
   * Get the Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @return Domain Custom Resource or null if Domain does not exist
   * @throws ApiException if Kubernetes client API call fails
   */
  public static DomainResource getDomainCustomResource(String domainUid,
                                                       String namespace) throws ApiException {
    return Domain.getDomainCustomResource(domainUid, namespace);
  }

  /**
   * Get the Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @param domainVersion version of domain
   * @return Domain Custom Resource or null if Domain does not exist
   * @throws ApiException if Kubernetes client API call fails
   */
  public static DomainResource getDomainCustomResource(String domainUid,
                                                       String namespace,
                                                       String domainVersion) throws ApiException {
    return Domain.getDomainCustomResource(domainUid, namespace, domainVersion);
  }

  /**
   * Shutdown the domain.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @return true on success, false otherwise
   */
  public static boolean shutdownDomain(String domainUid, String namespace) {
    return Domain.shutdown(domainUid, namespace);
  }

  /**
   * Restart the domain.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @return true on success, false otherwise
   */
  public static boolean startDomain(String domainUid, String namespace) {
    return Domain.start(domainUid, namespace);
  }

  /**
   * Delete a Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @return true on success, false otherwise
   */
  public static boolean deleteDomainCustomResource(String domainUid, String namespace) {
    return Domain.deleteDomainCustomResource(domainUid, namespace);
  }

  /**
   * Patch the Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @param patch patch data in format matching the specified media type
   * @param patchFormat one of the following types used to identify patch document:
   *                    "application/json-patch+json", "application/merge-patch+json",
   * @return true if successful, false otherwise
   */
  public static boolean patchDomainCustomResource(String domainUid, String namespace, V1Patch patch,
                                                  String patchFormat) {
    return Domain.patchDomainCustomResource(domainUid, namespace, patch, patchFormat);
  }

  /**
   * Patch the Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @param patch patch data in format matching the specified media type
   * @param patchFormat one of the following types used to identify patch document:
   *                    "application/json-patch+json", "application/merge-patch+json",
   * @return response msg of patching domain
   */
  public static String patchDomainCustomResourceReturnResponse(String domainUid, String namespace, V1Patch patch,
                                                  String patchFormat) {
    return Domain.patchDomainCustomResourceReturnResponse(domainUid, namespace, patch, patchFormat);
  }

  /**
   * Patch a running domain with introspectVersion.
   * If the introspectVersion doesn't exist it will add the value as 2,
   * otherwise the value is updated by 1.
   *
   * @param domainUid UID of the domain to patch with introspectVersion
   * @param namespace namespace in which the domain resource exists
   * @return introspectVersion new introspectVersion of the domain resource
   */
  public static String patchDomainResourceWithNewIntrospectVersion(
      String domainUid, String namespace) {
    return DomainUtils.patchDomainResourceWithNewIntrospectVersion(domainUid, namespace);
  }

  /**
   * Get current introspectVersion for a given domain.
   *
   * @param domainUid domain id
   * @param namespace namespace in which the domain resource exists
   * @return String containing current introspectVersion
   * @throws ApiException when getting domain resource fails
   */
  public static String getCurrentIntrospectVersion(String domainUid, String namespace) throws ApiException {
    return Domain.getCurrentIntrospectVersion(domainUid, namespace);
  }

  /**
   * Get next introspectVersion for a given domain.
   *
   * @param domainUid domain id
   * @param namespace namespace in which the domain resource exists
   * @return String containing next introspectVersion
   * @throws ApiException when getting domain resource fails
   */
  public static String getNextIntrospectVersion(String domainUid, String namespace) throws ApiException {
    LoggingFacade logger = getLogger();
    DomainResource domain = Domain.getDomainCustomResource(domainUid, namespace);
    String introspectVersion = domain.getSpec().getIntrospectVersion();
    if (null != introspectVersion) {
      logger.info("current introspectVersion: {0}", introspectVersion);
      introspectVersion = Integer.toString(Integer.valueOf(introspectVersion) + 1);
      logger.info("modified introspectVersion: {0}", introspectVersion);
    } else {
      introspectVersion = Integer.toString(1);
    }
    return introspectVersion;
  }

  /**
   * Scale all clusters in a domain by patching domain resource.
   *
   * @param domainUid domainUid of the domain to be scaled
   * @param namespace namespace in which the domain exists
   * @param replicaCount number of servers to be scaled to
   * @return true on success, false otherwise
   */
  public static boolean scaleAllClustersInDomain(String domainUid, String namespace, int replicaCount) {
    return Domain.scaleAllClustersInDomain(domainUid, namespace, replicaCount);
  }

  // ----------------------   cluster  -----------------------------------

  /**
   * Create Cluster Custom Resource.
   *
   * @param cluster Cluster custom resource model object
   * @return true on success, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createClusterCustomResource(ClusterResource cluster) throws ApiException {
    return Cluster.createClusterCustomResource(cluster, CLUSTER_VERSION);
  }

  /**
   * Delete Cluster Custom Resource.
   *
   * @param clusterResName Cluster custom resource name
   * @param namespace namespace in which cluster custom resource exists
   */
  public static void deleteClusterCustomResource(String clusterResName, String namespace) {
    ClusterUtils.deleteClusterCustomResourceAndVerify(clusterResName, namespace);
  }

  /**
   * Patch the Cluster Custom Resource.
   *
   * @param clusterResName unique cluster resource identifier
   * @param namespace name of namespace
   * @param patch patch data in format matching the specified media type
   * @param patchFormat one of the following types used to identify patch document: "application/json-patch+json",
  "application/merge-patch+json",
   * @return true if successful, false otherwise
   */
  public static boolean patchClusterCustomResource(String clusterResName, String namespace,
                                                   V1Patch patch, String patchFormat) {
    return Cluster.patchClusterCustomResource(clusterResName, namespace, patch, patchFormat);
  }

  /**
   * Patch the Cluster Custom Resource.
   *
   * @param clusterName unique cluster identifier
   * @param namespace name of namespace
   * @param patch patch data in format matching the specified media type
   * @param patchFormat one of the following types used to identify patch document: "application/json-patch+json",
  "application/merge-patch+json",
   * @return response msg of patching cluster resource
   */
  public static String patchClusterCustomResourceReturnResponse(String clusterName, String namespace,
                                                                V1Patch patch, String patchFormat) {
    return Cluster.patchClusterCustomResourceReturnResponse(clusterName, namespace, patch, patchFormat);
  }

  /**
   * Scale a cluster in a specified namespace by patching cluster resource.
   *
   * @param clusterResName cluster resource name
   * @param namespace name of Kubernetes namespace that the domain belongs to
   * @param numOfServers number of servers to be scaled to.
   * @return true on success, false otherwise
   */
  public static boolean scaleCluster(String clusterResName, String namespace, int numOfServers) {
    return Cluster.scaleCluster(clusterResName, namespace, numOfServers);
  }

  /**
   * Scale the cluster of the domain and change introspect version in the specified namespace by
   * patching the domain resource.
   *
   * @param domainUid domainUid of the domain to be scaled
   * @param namespace name of Kubernetes namespace that the domain belongs to
   * @param clusterName cluster in the domain to be scaled
   * @param numOfServers number of servers to be scaled to
   * @param introspectVersion new introspectVersion value
   * @return true on success, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean scaleClusterAndChangeIntrospectVersion(String domainUid, String namespace, String clusterName,
                                                               int numOfServers, int introspectVersion)
      throws ApiException {
    return Domain.scaleClusterAndChangeIntrospectVersion(domainUid, namespace, clusterName, numOfServers,
        introspectVersion);
  }

  /**
   * Scale the cluster of the domain in the specified namespace using REST API.
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
    return Domain.scaleClusterWithRestApi(domainUid, clusterName, numOfServers,
        externalRestHttpsPort, opNamespace, opServiceAccount);
  }

  /**
   * Scale the cluster of the domain in the specified namespace using REST API.
   *
   * @param domainUid domainUid of the domain to be scaled
   * @param clusterName name of the WebLogic cluster to be scaled in the domain
   * @param numOfServers number of servers to be scaled to
   * @param opPodName operator pod name
   * @param opPort operator port
   * @param opNamespace namespace of WebLogic operator
   * @param opServiceAccount the service account for operator
   * @return true if REST call succeeds, false otherwise
   */
  public static boolean scaleClusterWithRestApiInOpPod(String domainUid,
                                                       String clusterName,
                                                       int numOfServers,
                                                       String opPodName,
                                                       int opPort,
                                                       String opNamespace,
                                                       String opServiceAccount) {
    return Domain.scaleClusterWithRestApiInOpPod(domainUid, clusterName, numOfServers,
        opPodName, opPort, opNamespace, opServiceAccount);
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
   * @return ExecResult object
   */
  public static ExecResult scaleClusterWithRestApiAndReturnResult(String domainUid,
                                                                  String clusterName,
                                                                  int numOfServers,
                                                                  int externalRestHttpsPort,
                                                                  String opNamespace,
                                                                  String opServiceAccount) {
    return Domain.scaleClusterWithRestApiAndReturnResult(domainUid, clusterName, numOfServers, externalRestHttpsPort,
        opNamespace, opServiceAccount);
  }

  /**
   * Scale the cluster of the domain in the specified namespace with WLDF policy.
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
      throws ApiException {
    return Domain.scaleClusterWithWLDF(clusterName, domainUid, domainNamespace, domainHomeLocation, scalingAction,
        scalingSize, opNamespace, opServiceAccount, myWebAppName, curlCommand);
  }

  /**
   * Scale the cluster of the domain in the specified namespace with WLDF policy.
   *
   * @param clusterName name of the WebLogic cluster to be scaled in the domain
   * @param domainUid domainUid of the domain to be scaled
   * @param domainNamespace domain namespace in which the domain exists
   * @param domainHomeLocation domain home location of the domain
   * @param scalingAction scaling action, accepted value: scaleUp or scaleDown
   * @param scalingSize number of servers to be scaled up or down
   * @param opNamespace namespace of WebLogic operator
   * @param opServiceAccount service account of operator
   * @return true if scaling the cluster succeeds, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   * @throws InterruptedException if any thread has interrupted the current thread
   */
  public static boolean scaleClusterWithScalingActionScript(String clusterName,
                                             String domainUid,
                                             String domainNamespace,
                                             String domainHomeLocation,
                                             String scalingAction,
                                             int scalingSize,
                                             String opNamespace,
                                             String opServiceAccount)
      throws ApiException, InterruptedException {
    return Domain.scaleClusterWithScalingActionScript(clusterName,
        domainUid, domainNamespace, domainHomeLocation, scalingAction,
        scalingSize, opNamespace, opServiceAccount);
  }

  // ------------------------   Ingress Controller ----------------------

  /**
   * Install NGINX ingress controller.
   *
   * @param params the parameters to Helm install command, such as release name, namespace, repo url,
   *               repo name and chart name
   * @return true on success, false otherwise
   */
  public static boolean installNginx(NginxParams params) {
    return Nginx.install(params);
  }

  /**
   * Install Traefik ingress controller.
   *
   * @param params the parameters to Helm install command, such as release name, namespace, repo url,
   *               repo name and chart name
   * @return true on success, false otherwise
   */
  public static boolean installTraefik(TraefikParams params) {
    return Traefik.install(params);
  }

  /**
   * Upgrade Traefik ingress controller.
   *
   * @param params the parameters to upgrade command, such as image.repository, image.registry,
   *               and image.tag
   * @return true on success, false otherwise
   */
  public static boolean upgradeTraefikImage(TraefikParams params) {
    return Traefik.upgradeTraefikImage(params);
  }

  /**
   * Upgrade NGINX release.
   *
   * @param params the parameters to Helm upgrade command, such as release name and http/https nodeport
   * @return true on success, false otherwise
   */
  public static boolean upgradeNginx(NginxParams params) {
    return Nginx.upgrade(params);
  }

  /**
   * Uninstall the NGINX release.
   *
   * @param params the parameters to Helm uninstall command, such as release name and namespace
   * @return true on success, false otherwise
   */
  public static boolean uninstallNginx(HelmParams params) {
    return Nginx.uninstall(params);
  }

  /**
   * Uninstall the Traefik release.
   *
   * @param params the parameters to Helm uninstall command, such as release name and namespace
   * @return true on success, false otherwise
   */
  public static boolean uninstallTraefik(HelmParams params) {
    return Traefik.uninstall(params);
  }

  /**
   * Create an ingress for the WebLogic domain with domainUid in the specified domain namespace.
   * The ingress host is set to 'domainUid.clusterName.test'.
   *
   * @param ingressName the name of the ingress to be created
   * @param domainNamespace the WebLogic domain namespace in which to create the ingress
   * @param domainUid WebLogic domainUid which is backend to the ingress
   * @param clusterNameMsPortMap the map with key as cluster name and value as managed server port of the cluster
   * @param annotations annotations to create ingress resource
   * @param ingressClassName Ingress class name
   * @param setIngressHost if true set to specific host or all
   * @param tlsSecret TLS secret name if any
   * @return list of ingress hosts or null if got ApiException when calling Kubernetes client API to create ingress
   */
  public static List<String> createIngress(
      String ingressName,
      String domainNamespace,
      String domainUid,
      Map<String, Integer> clusterNameMsPortMap,
      Map<String, String> annotations,
      String ingressClassName,
      boolean setIngressHost,
      String tlsSecret) {
    return Ingress.createIngress(ingressName,
            domainNamespace,
            domainUid,
            clusterNameMsPortMap,
            annotations, ingressClassName, setIngressHost, tlsSecret, false, 0);
  }

  /**
   * Create an ingress for the WebLogic domain with domainUid in the specified domain namespace.
   * The ingress host is set to 'domainUid.clusterName.test'.
   *
   * @param ingressName the name of the ingress to be created
   * @param domainNamespace the WebLogic domain namespace in which to create the ingress
   * @param domainUid WebLogic domainUid which is backend to the ingress
   * @param clusterNameMsPortMap the map with key as cluster name and value as managed server port of the cluster
   * @param annotations annotations to create ingress resource
   * @param ingressClassName Ingress class name
   * @param setIngressHost if true set to specific host or all
   * @param tlsSecret TLS secret name if any
   * @param enableAdminServerRouting enable the ingress rule to admin server
   * @param adminServerPort the port number of admin server pod of the domain
   * @return list of ingress hosts or null if got ApiException when calling Kubernetes client API to create ingress
   */
  public static List<String> createIngress(String ingressName,
                                           String domainNamespace,
                                           String domainUid,
                                           Map<String, Integer> clusterNameMsPortMap,
                                           Map<String, String> annotations,
                                           String ingressClassName,
                                           boolean setIngressHost,
                                           String tlsSecret,
                                           boolean enableAdminServerRouting,
                                           int adminServerPort) {
    return Ingress.createIngress(ingressName, domainNamespace, domainUid, clusterNameMsPortMap,
        annotations, ingressClassName, setIngressHost, tlsSecret, enableAdminServerRouting, adminServerPort);
  }

  /**
   * Create an ingress in specified namespace.
   * @param ingressName ingress name
   * @param namespace namespace in which the ingress will be created
   * @param annotations annotations of the ingress
   * @param ingressClassName Ingress class name
   * @param ingressRules a list of ingress rules
   * @param tlsList list of ingress tls
   * @throws ApiException if Kubernetes API call fails
   */
  public static void createIngress(String ingressName,
                                   String namespace,
                                   Map<String, String> annotations,
                                   String ingressClassName,
                                   List<V1IngressRule> ingressRules,
                                   List<V1IngressTLS> tlsList) throws ApiException {
    Ingress.createIngress(ingressName, namespace, annotations, ingressClassName, ingressRules, tlsList);
  }

  /**
   * Get a list of ingresses in the specified namespace.
   *
   * @param namespace in which to list all the ingresses
   * @return list of ingress names in the specified namespace
   * @throws ApiException if Kubernetes client API call fails
   */
  public static List<String> listIngresses(String namespace) throws ApiException {
    return Ingress.listIngresses(namespace);
  }

  /**
   * Delete an ingress in the specified namespace.
   *
   * @param name  ingress name to be deleted
   * @param namespace namespace in which the specified ingress exists
   * @return true if deleting ingress succeed, false otherwise
   * @throws ApiException if Kubernetes API client call fails
   */
  public static boolean deleteIngress(String name, String namespace) throws ApiException {
    return Ingress.deleteIngress(name, namespace);
  }

  // -------------------------  namespaces -------------------------------

  /**
   * Create Kubernetes namespace.
   *
   * @param name the name of the namespace
   * @return true on success, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createNamespace(String name) throws ApiException {
    return new Namespace().name(name).create();
  }

  /**
   * Add labels to a namespace.
   *
   * @param name name of the namespace
   * @param labels map of labels to add to the namespace
   * @throws ApiException when adding labels to namespace fails
   */
  public static void addLabelsToNamespace(String name, Map<String, String> labels)
      throws ApiException {
    Namespace.addLabelsToNamespace(name, labels);
  }

  /**
   * Create a namespace with unique name.
   *
   * @return true on success, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String createUniqueNamespace() throws ApiException {
    String name = Namespace.uniqueName();
    try {
      new Namespace().name(name).create();
      return name;
    } catch (ApiException ex) {
      //retry in case of failure
      if (listNamespaces().contains(name)) {
        return createUniqueNamespace();
      } else {
        getLogger().severe(ex.getResponseBody());
        throw ex;
      }
    }
  }

  /**
   * List of namespaces in Kubernetes cluster.
   *
   * @return List of names of all namespaces in Kubernetes cluster
   * @throws ApiException if Kubernetes client API call fails
   */
  public static List<String> listNamespaces() throws ApiException {
    return Namespace.listNamespaces();
  }

  /**
   * Delete a Kubernetes namespace.
   *
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean deleteNamespace(String namespace) {
    return Namespace.delete(namespace);
  }

  // ------------------------ image  -------------------------

  /**
   * Create a WITParams that contains the parameters for executing a WIT command.
   *
   * @return an instance of WITParams that contains the default values
   */
  public static WitParams defaultWitParams() {
    return
        WebLogicImageTool.defaultWitParams();
  }

  /**
   * Create an image using WDT models using WebLogic Image Tool.
   *
   * @param params - the parameters for creating a model-in-image image
   * @return true if the operation succeeds
   */
  public static boolean createImage(WitParams params) {
    return
        WebLogicImageTool
            .withParams(params)
            .updateImage();
  }

  /**
   * Inspect base image using WDT models using WebLogic Image Tool.
   *
   * @param imageName - image name
   * @param imageTag - image tag
   * @return output if the operation succeeds
   */
  public static String inspectImage(String imageName, String imageTag) {
    WitParams params = defaultWitParams()
            .wdtVersion(WDT_VERSION)
            .redirect(true);
    return WebLogicImageTool
                    .withParams(params)
                    .inspectImage(imageName, imageTag);
  }

  /**
   * Create an auxiliary image using WebLogic Image Tool.
   *
   * @param params - the parameters for creating a model-in-image image
   * @return true if the operation succeeds
   */
  public static boolean createAuxImage(WitParams params) {
    return WebLogicImageTool
            .withParams(params)
            .createAuxImage();
  }

  /**
   * Create an auxiliary image using WebLogic Image Tool and return result output.
   *
   * @param params - the parameters for creating a model-in-image image
   * @return true if the operation succeeds
   */
  public static ExecResult createAuxImageAndReturnResult(WitParams params) {
    return WebLogicImageTool
            .withParams(params)
            .createAuxImageAndReturnResult();
  }

  // -------------------------   pv/pvc  ---------------------------------
  /**
   * Get the V1PersistentVolume object in the Kubernetes cluster with specified Persistent Volume name.
   * @param pvname the name of the Persistent Volume
   * @return V1PersistentVolume the Persistent Volume object with specified name in Kubernetes cluster
   */
  public static V1PersistentVolume getPersistentVolume(String pvname) {
    return Kubernetes.getPersistentVolume(pvname);
  }

  /**
   * Create a Kubernetes Persistent Volume.
   *
   * @param persistentVolume V1PersistentVolume object containing persistent volume
   *     configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createPersistentVolume(V1PersistentVolume persistentVolume) throws ApiException {
    return PersistentVolume.create(persistentVolume);
  }

  /**
   * Delete the Kubernetes Persistent Volume.
   *
   * @param name name of the Persistent Volume
   * @return true if successful, false otherwise
   */
  public static boolean deletePersistentVolume(String name) {
    return PersistentVolume.delete(name);
  }

  /**
   * Get V1PersistentVolumeClaim object in the namespace with the specified Persistent Volume Claim name .
   * @param namespace namespace in which to get the Persistent Volume Claim
   * @param pvcname the name of Persistent Volume Claim
   * @return V1PersistentVolumeClaim the Persistent Volume Claims Object in specified namespace
   */
  public static V1PersistentVolumeClaim getPersistentVolumeClaim(String namespace, String pvcname) {
    return Kubernetes.getPersistentVolumeClaim(namespace, pvcname);
  }

  /**
   * Create a Kubernetes Persistent Volume Claim.
   *
   * @param persistentVolumeClaim V1PersistentVolumeClaim object containing Kubernetes
   *     persistent volume claim configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createPersistentVolumeClaim(V1PersistentVolumeClaim persistentVolumeClaim)
      throws ApiException {
    return PersistentVolumeClaim.create(persistentVolumeClaim);
  }

  /**
   * Delete the Kubernetes Persistent Volume Claim.
   *
   * @param name name of the Persistent Volume Claim
   * @param namespace name of the namespace
   * @return true if successful, false otherwise
   */
  public static boolean deletePersistentVolumeClaim(String name, String namespace) {
    return PersistentVolumeClaim.delete(name, namespace);
  }

  // --------------------------  secret  ----------------------------------

  /**
   * Create a Kubernetes Secret.
   *
   * @param secret V1Secret object containing Kubernetes secret configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createSecret(V1Secret secret) throws ApiException {
    return Secret.create(secret);
  }

  /**
   * Delete Kubernetes Secret.
   *
   * @param name name of the Secret
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean deleteSecret(String name, String namespace) {
    return Secret.delete(name, namespace);
  }

  /**
   * List secrets in the Kubernetes cluster.
   *
   * @param namespace Namespace in which to query
   * @return V1SecretList of secrets in the Kubernetes cluster
   */
  public static V1SecretList listSecrets(String namespace) {
    return Secret.listSecrets(namespace);
  }

  // -------------------------- config map ---------------------------------

  /**
   * Create Kubernetes Config Map.
   *
   * @param configMap V1ConfigMap object containing config map configuration data
   * @return true on success
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createConfigMap(V1ConfigMap configMap) throws ApiException {
    // delete the config map if exists
    assertNotNull(configMap);
    assertNotNull(configMap.getMetadata());
    String cmName = configMap.getMetadata().getName();
    String cmNamespace = configMap.getMetadata().getNamespace();
    if (doesCMExist(cmName, cmNamespace)) {
      deleteConfigMap(cmName, cmNamespace);
      // wait until the cm is deleted
      testUntil(withLongRetryPolicy,
          () -> !doesCMExist(cmName, cmNamespace),
          getLogger(),
          "configmap {0} in namespace {1} is deleted",
          cmName,
          cmNamespace);
    }

    return ConfigMap.create(configMap);
  }

  /**
   * Delete Kubernetes Config Map.
   *
   * @param name name of the Config Map
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean deleteConfigMap(String name, String namespace) {
    return ConfigMap.delete(name, namespace);
  }

  // -------------------------- Service ---------------------------------

  /**
   * Create Kubernetes Service.
   *
   * @param service V1Service object containing service configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createService(V1Service service) throws ApiException {
    return Service.create(service);
  }

  /**
   * List services in a namespace.
   *
   * @param namespace namespace in which to list services
   * @return V1ServiceList
   */
  public static V1ServiceList listServices(String namespace) {
    return Service.listServices(namespace);
  }

  /**
   * Delete Kubernetes Service.
   *
   * @param name name of the Service
   * @param namespace name of namespace
   * @return true if successful
   */
  public static boolean deleteService(String name, String namespace) {
    return Service.delete(name, namespace);
  }

  /**
   * Get node port of a namespaced service given the channel name.
   *
   * @param namespace name of the namespace in which to get the service
   * @param serviceName name of the service
   * @param channelName name of the channel for which to get the nodeport
   * @return node port if service and channel is found, otherwise -1
   */
  public static int getServiceNodePort(String namespace, String serviceName, String channelName) {
    return Service.getServiceNodePort(namespace, serviceName, channelName);
  }

  /**
   * Get node port of a namespaced service.
   *
   * @param namespace name of the namespace in which to get the service
   * @param serviceName name of the service
   * @return node port if service is found, otherwise -1
   */
  public static Integer getServiceNodePort(String namespace, String serviceName) {
    return Service.getServiceNodePort(namespace, serviceName);
  }

  /**
   * Get port of a namespaced service given the channel name.
   *
   * @param namespace name of the namespace in which to get the service
   * @param serviceName name of the service
   * @param channelName name of the channel for which to get the port
   * @return node port if service and channel is found, otherwise -1
   */
  public static int getServicePort(String namespace, String serviceName, String channelName) {
    return Service.getServicePort(namespace, serviceName, channelName);
  }

  /**
   * Get namespaced service object.
   *
   * @param namespace name of the namespace in which to get the service
   * @param serviceName name of the service object to get
   * @return V1Service object if found, otherwise null
   */
  public static V1Service getNamespacedService(String namespace, String serviceName) {
    return Service.getNamespacedService(namespace, serviceName);
  }


  // ------------------------ service account  --------------------------

  /**
   * Create a Kubernetes Service Account.
   *
   * @param serviceAccount V1ServiceAccount object containing service account configuration data
   * @return true on success, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createServiceAccount(V1ServiceAccount serviceAccount) throws ApiException {
    return ServiceAccount.create(serviceAccount);
  }

  /**
   * Delete a Kubernetes Service Account.
   *
   * @param name name of the Service Account
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean deleteServiceAccount(String name, String namespace) {
    return ServiceAccount.delete(name, namespace);
  }

  // ----------------------- Role-based access control (RBAC)   ---------------------------

  /**
   * Create a cluster role.
   *
   * @param clusterRole V1ClusterRole object containing cluster role configuration data
   * @return true if creation is successful, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createClusterRole(V1ClusterRole clusterRole) throws ApiException {
    return ClusterRole.createClusterRole(clusterRole);
  }

  /**
   * Create a cluster role binding.
   *
   * @param clusterRoleBinding V1ClusterRoleBinding object containing cluster role binding configuration data
   * @return true if creation is successful, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createClusterRoleBinding(V1ClusterRoleBinding clusterRoleBinding)
      throws ApiException {
    return ClusterRoleBinding.createClusterRoleBinding(clusterRoleBinding);
  }

  /**
   * Create a role binding in the specified namespace.
   *
   * @param namespace the namespace in which the role binding to be created
   * @param roleBinding V1RoleBinding object containing role binding configuration data
   * @return true if the creation succeeds, false otherwise
   * @throws ApiException if Kubernetes client call fails
   */
  public static boolean createRoleBinding(String namespace, V1RoleBinding roleBinding) throws ApiException {
    return Kubernetes.createNamespacedRoleBinding(namespace, roleBinding);
  }

  /**
   * Delete cluster role binding.
   *
   * @param name name of cluster role binding
   * @return true if deletion is successful, false otherwise
   */
  public static boolean deleteClusterRoleBinding(String name) {
    return ClusterRoleBinding.deleteClusterRoleBinding(name);
  }

  /**
   * Delete a cluster role.
   *
   * @param name the name of cluster role to delete
   * @return true if deletion succeeds, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean deleteClusterRole(String name) throws ApiException {
    return ClusterRole.deleteClusterRole(name);
  }

  /**
   * Create a role in the specified namespace.
   *
   * @param namespace the namespace in which the role to be created
   * @param role V1Role object containing role configuration data
   * @return true if the creation succeeds, false otherwise
   * @throws ApiException if Kubernetes client call fails
   */
  public static boolean createRole(String namespace, V1Role role) throws ApiException {
    return Kubernetes.createNamespacedRole(namespace, role);
  }


  // ----------------------- Storage class---------------------------

  /**
   * Create StorageClass.
   *
   * @param sco V1StorageClass object
   * @return true if success otherwise false
   */
  public static boolean createStorageClass(V1StorageClass sco) {
    return Kubernetes.createStorageClass(sco);
  }

  /**
   * Delete StorageClass.
   *
   * @param name V1StorageClass object name
   * @return true if success otherwise false
   */
  public static boolean deleteStorageClass(String name) {
    return Kubernetes.deleteStorageClass(name);
  }

  // ----------------------- Helm -----------------------------------

  /**
   * List releases.
   *
   * @param params namespace
   * @return true on success
   */
  public static boolean helmList(HelmParams params) {
    return Helm.list(params);
  }

  // ------------------------ Application Builder  -------------------------

  /**
   * Create an AppParams instance that contains the default values.
   *
   * @return an AppParams instance that contains the default values
   */
  public static AppParams defaultAppParams() {
    return
        AppBuilder.defaultAppParams();
  }

  /**
   * Create an application archive that can be used by WebLogic Image Tool
   * to create an image with the application for a model-in-image use case.
   *
   * @param params the parameters for creating a model-in-image image
   * @return true if the operation succeeds
   */
  public static boolean buildAppArchive(AppParams params) {
    return
        AppBuilder
            .withParams(params)
            .build();
  }

  /**
   * Create an application archive that can be used by WebLogic Image Tool
   * to create an image with coh-proxy-server.gar for testing Coherence use case.
   *
   * @param params the parameters for creating a model-in-image image
   * @return true if the operation succeeds
   */
  public static boolean buildCoherenceArchive(AppParams params) {
    return
      AppBuilder
        .withParams(params)
        .buildCoherence();
  }

  /**
   * Archive an application from provided ear or war file that can be used by WebLogic Image Tool
   * to create an image with the application for a model-in-image use case.
   *
   * @param params the parameters for creating a model-in-image image
   * @return true if the operation succeeds
   */
  public static boolean archiveApp(AppParams params) {
    return AppBuilder
        .withParams(params)
        .archiveApp();
  }

  // ------------------------ Image Handling --------------------------------------

  /**
   * Log in to an image registry.
   *
   * @param registryName name of registry
   * @param username username for the registry
   * @param password password for the registry
   * @return true if successful, false otherwise
   */
  public static boolean imageRepoLogin(String registryName, String username, String password) {
    return Image.login(registryName, username, password);
  }

  /**
   * Push an image to a registry.
   *
   * @param image fully qualified image, image name:image tag
   * @return true if successful
   */
  public static boolean imagePush(String image) {
    boolean result = Image.push(image);
    if (result) {
      InitializationTasks.registerPushedImage(image);
    }
    return result;
  }

  /**
   * Tag an image.
   *
   * @param originalImage fully qualified original image, image name:image tag
   * @param taggedImage fully qualified tagged image, image name:image tag
   * @return true if successful
   */
  public static boolean imageTag(String originalImage, String taggedImage) {
    return Image.tag(originalImage, taggedImage);
  }

  /**
   * Pull an image from a registry.
   *
   * @param image fully qualified image, image name:image tag
   * @return true if successful
   */
  public static boolean imagePull(String image) {
    return Image.pull(image);
  }

  /**
   * Tag a originalImage to taggedImage and push it to repo.
   * @param originalImage original image
   * @param taggedImage tagged image
   * @return true if tag and push succeeds, false otherwise
   */
  public static Callable<Boolean> tagAndPushToKind(String originalImage, String taggedImage) {
    return (() -> {
      return imageTag(originalImage, taggedImage) && imagePush(taggedImage);
    });
  }

  /**
   * Delete image.
   *
   * @param image image name:image tag
   * @return true if delete image is successful
   */
  public static boolean deleteImage(String image) {
    return Image.deleteImage(image);
  }

  /**
   * Create registry configuration in json object.
   *
   * @param username username for the image registry
   * @param password password for the image registry
   * @param email email for the image registry
   * @param registry registry name
   * @return json object for the registry configuration
   */
  public static JsonObject createImageBuilderConfigJson(
      String username,
      String password,
      String email,
      String registry
  ) {
    return Image.createImageBuilderConfigJson(username, password, email, registry);
  }

  // ----------------------- Execute a Command   ---------------------------

  /**
   * Execute a command in a container of a Kubernetes pod.
   *
   * @param namespace The Kubernetes namespace that the pod is in
   * @param podName The name of the Kubernetes pod where the command is expected to run
   * @param containerName The container in the Pod where the command is to be run. If no
   *                         container name is provided than the first container in the Pod is used.
   * @param redirectToStdout copy process output to stdout
   * @param command The command to run
   * @return result of command execution
   * @throws IOException if an I/O error occurs.
   * @throws ApiException if Kubernetes client API call fails
   * @throws InterruptedException if any thread has interrupted the current thread
   */
  public static synchronized ExecResult execCommand(
      String namespace,
      String podName,
      String containerName,
      boolean redirectToStdout,
      String... command
  ) throws IOException, ApiException, InterruptedException {
    // get the pod given the namespace and name of the pod
    // no label selector is needed (thus null below)
    final V1Pod pod = Kubernetes.getPod(namespace, null, podName);
    if (pod == null) {
      throw new IllegalArgumentException(
          String.format("The pod %s does not exist in namespace %s!", podName, namespace));
    }
    return Exec.exec(pod, containerName, redirectToStdout, command);
  }

  // ------------------------ Jobs ----------------------------------

  /**
   * Create a job.
   *
   * @param jobBody V1Job object containing job configuration data
   * @return String job name if job creation is successful
   * @throws ApiException when create job fails
   */
  public static String createNamespacedJob(V1Job jobBody) throws ApiException {
    return Job.createNamespacedJob(jobBody);
  }

  /**
   * Get V1Job object if any exists in the namespace with given job name.
   *
   * @param jobName name of the job
   * @param namespace name of the namespace in which to get the job object
   * @return V1Job object if any exists otherwise null
   * @throws ApiException when Kubernetes cluster query fails
   */
  public static V1Job getJob(String jobName, String namespace) throws ApiException {
    return Job.getJob(jobName, namespace);
  }

  /**
   * Delete a job.
   *
   * @param jobName name of the job
   * @param namespace name of the namespace
   * @return true if delete was successful
   * @throws ApiException when deletion of job fails
   */
  public static boolean deleteJob(String jobName, String namespace) throws ApiException {
    return Job.deleteJob(jobName, namespace);
  }

  /**
   * List jobs in the given namespace.
   *
   * @param namespace in which to list the jobs
   * @return V1JobList list of {@link V1Job} from Kubernetes cluster
   * @throws ApiException when list fails
   */
  public static V1JobList listJobs(String namespace) throws ApiException {
    return Job.listJobs(namespace);
  }

  // ----------------------   pod  ---------------------------------

  /**
   * Get the creationTimestamp for a given pod with following parameters.
   *
   * @param namespace namespace in which to check for the pod existence
   * @param labelSelector in the format "weblogic.domainUID in (%s)"
   * @param podName name of the pod
   * @return creationTimestamp from metadata section of the Pod
   * @throws ApiException if Kubernetes client API call fails
   **/
  public static OffsetDateTime getPodCreationTimestamp(String namespace, String labelSelector, String podName)
      throws ApiException {
    return Pod.getPodCreationTimestamp(namespace, labelSelector, podName);
  }

  /**
   * Get the Pod object with following parameters.
   *
   * @param namespace namespace in which to check for the pod existence
   * @param labelSelector in the format "weblogic.domainUID in (%s)"
   * @param podName name of the pod
   * @return V1Pod pod object
   * @throws ApiException if Kubernetes client API call fails
   **/
  public static V1Pod getPod(String namespace, String labelSelector, String podName) throws ApiException {
    return Pod.getPod(namespace, labelSelector, podName);
  }

  /**
   * Get the IP address allocated to the pod with following parameters.
   *
   * @param namespace namespace in which to check for the pod existence
   * @param labelSelector in the format "weblogic.domainUID in (%s)"
   * @param podName name of the pod to return
   * @return IP address allocated to the pod
   * @throws ApiException if Kubernetes client API call fails
   **/
  public static String getPodIP(String namespace, String labelSelector, String podName) throws ApiException {
    return Pod.getPodIP(namespace, labelSelector, podName);
  }

  /**
   * Returns the status phase of the pod.
   *
   * @param namespace in which to check for the pod status
   * @param labelSelectors in the format "weblogic.domainUID in (%s)"
   * @param podName name of the pod to check
   * @return the status phase of the pod
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String getPodStatusPhase(String namespace, String labelSelectors, String podName)
      throws ApiException {
    return Pod.getPodStatusPhase(namespace, labelSelectors, podName);
  }

  /**
   * Get a pod's log.
   *
   * @param podName name of the pod
   * @param namespace name of the namespace
   * @return log as a String
   * @throws ApiException if Kubernetes client API call fails
   **/
  public static String getPodLog(String podName, String namespace) throws ApiException {
    return Pod.getPodLog(podName, namespace);
  }

  /**
   * Get a pod's log.
   *
   * @param podName name of the pod
   * @param namespace name of the namespace
   * @param container name of the container
   * @return log as a String
   * @throws ApiException if Kubernetes client API call fails
   **/
  public static String getPodLog(String podName, String namespace, String container) throws ApiException {
    return Pod.getPodLog(podName, namespace, container);
  }

  /**
   * Get a pod's log.
   *
   * @param podName name of the pod
   * @param namespace name of the namespace
   * @param container name of the container
   * @param previous whether return previous terminated container logs
   * @param sinceSeconds relative time in seconds before the current time from which to show logs
   * @param follow whether to follow the log stream of the pod
   * @return log as a String
   * @throws ApiException if Kubernetes client API call fails
   **/
  public static String getPodLog(String podName, String namespace, String container, Boolean previous,
                                 Integer sinceSeconds,Boolean follow) throws ApiException {
    return Pod.getPodLog(podName, namespace, container, previous, sinceSeconds, follow);
  }

  /**
   * Get a pod's log.
   *
   * @param podName name of the pod
   * @param namespace name of the namespace
   * @param container name of the container
   * @param sinceSeconds a relative time in seconds before the current time from which to show logs.
   * @param previous whether return previous terminated container logs
   * @return log as a String
   * @throws ApiException if Kubernetes client API call fails
   **/
  public static String getPodLog(String podName, String namespace, String container, Boolean previous,
                                 Integer sinceSeconds) throws ApiException {
    return Pod.getPodLog(podName, namespace, container, previous, sinceSeconds);
  }

  /**
   * List Kubernetes pods in a namespace.
   *
   * @param namespace name of namespace
   * @param labelSelectors with which pods are decorated
   * @return V1PodList list of pods
   * @throws ApiException if Kubernetes client API call fails
   */
  public static V1PodList listPods(String namespace, String labelSelectors) throws ApiException {
    return Pod.listPods(namespace, labelSelectors);
  }

  /**
   * Delete a pod in a given namespace.
   *
   * @param podName name of the pod to be deleted
   * @param namespace Kubernetes namespace that the pod is running in
   * @throws ApiException if Kubernetes client API call fails
   */
  public static void deletePod(String podName, String namespace) throws ApiException {
    Kubernetes.deletePod(podName, namespace);
  }

  /**
   * Get the weblogic.domainRestartVersion label from a given pod.
   *
   * @param namespace in which to check for the pod existence
   * @param labelSelector in the format "weblogic.domainUID in (%s)"
   * @param podName  name of the pod
   * @return value of weblogic.domainRestartVersion label, null if unset or the pod is not available
   * @throws ApiException when there is error in querying the cluster
   */
  public static String getPodRestartVersion(String namespace, String labelSelector, String podName)
      throws ApiException {
    return Kubernetes.getPodRestartVersion(namespace, labelSelector, podName);
  }

  /**
   * Patch domain to shutdown a WebLogic server by changing the value of
   * server's serverStartPolicy property to Never.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of the namespace
   * @param serverName name of the WebLogic server to shutdown
   * @return true if patching domain operation succeeds or false if the operation fails
   * @throws ApiException if Kubernetes client API call fails
   **/
  public static boolean shutdownManagedServerUsingServerStartPolicy(String domainUid,
                                                                    String namespace,
                                                                    String serverName) throws ApiException {
    return Pod.shutdownManagedServerUsingServerStartPolicy(domainUid,namespace, serverName);
  }

  /**
   * Patch domain to start a WebLogic server by changing the value of
   * server's serverStartPolicy property to IfNeeded.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of the namespace
   * @param serverName name of the WebLogic server to start
   * @return true if patching domain operation succeeds or false if the operation fails
   * @throws ApiException if Kubernetes client API call fails
   **/
  public static boolean startManagedServerUsingServerStartPolicy(String domainUid,
                                                                 String namespace,
                                                                 String serverName) throws ApiException {
    return Pod.startManagedServerUsingServerStartPolicy(domainUid,namespace, serverName);
  }

  /**
   * Patch domain to restart a WebLogic server by changing its serverStartPolicy properties.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of the namespace
   * @param serverName name of the WebLogic server to start
   * @return true if patching domain operation succeeds or false if the operation fails
   * @throws ApiException if Kubernetes client API call fails
   **/
  public static boolean restartManagedServerUsingServerStartPolicy(String domainUid,
                                                                   String namespace,
                                                                   String serverName) throws ApiException {
    boolean serverStopped = shutdownManagedServerUsingServerStartPolicy(domainUid, namespace, serverName);
    boolean serverStarted = startManagedServerUsingServerStartPolicy(domainUid, namespace, serverName);

    return serverStopped && serverStarted;
  }

  /**
   * Get the container's restart count in the pod.
   * @param namespace name of the pod's namespace
   * @param labelSelector in the format "weblogic.domainUID in (%s)"
   * @param podName name of the pod
   * @param containerName name of the container, if null looks for first container
   * @return restart count of the container
   * @throws ApiException if Kubernetes client API call fails
   */
  public static int getContainerRestartCount(
      String namespace, String labelSelector, String podName, String containerName)
      throws ApiException {
    return Kubernetes.getContainerRestartCount(namespace, labelSelector, podName, containerName);
  }

  // ------------------------ where does this go  -------------------------

  /**
   * Deploy the application to the given target.
   *
   * @param appName     the name of the application
   * @param appLocation location of the war/ear file
   * @param t3Url       the t3 url to connect
   * @param username    username
   * @param password    password
   * @param target      the name of the target
   * @return true on success, false otherwise
   */
  public static boolean deployApplication(String appName, String appLocation, String t3Url,
                                          String username, String password, String target) {
    return true;
  }

  // --------------------------- Prometheus ---------------------------------

  /**
   * Install Prometheus.
   *
   * @param params prometheus parameters for Helm values
   * @return true if the prometheus is successfully installed, false otherwise.
   */
  public static boolean installPrometheus(PrometheusParams params) {
    return Prometheus.install(params);
  }

  /**
   * Uninstall the Prometheus release.
   *
   * @param params the parameters to Helm uninstall command, release name and namespace
   * @return true on success, false otherwise
   */

  public static boolean uninstallPrometheus(HelmParams params) {
    return uninstall(params);
  }

  // --------------------------- Grafana ---------------------------------
  /**
   * Install Grafana.
   *
   * @param params grafana parameters for Helm values
   * @return true if the prometheus is successfully installed, false otherwise.
   */
  public static boolean installGrafana(GrafanaParams params) {
    return Grafana.install(params);
  }

  /**
   * Uninstall the Grafana release.
   *
   * @param params the parameters to Helm uninstall command, release name and namespace
   * @return true on success, false otherwise
   */

  public static boolean uninstallGrafana(HelmParams params) {
    return Grafana.uninstall(params);
  }

  // --------------------------- ELK Stack ---------------------------------
  /**
   * Install Elasticsearch.
   *
   * @param params the parameters to install Elasticsearch
   * @return true if Elasticsearch is successfully installed, false otherwise.
   */
  public static boolean installElasticsearch(LoggingExporterParams params) {
    return LoggingExporter.installElasticsearch(params);
  }

  /**
   * Install WebLogic Remote Console.
   * @param domainNamespace namespace in which the domain will be created
   * @param adminServerPodName the name of the admin server pod
   *
   * @return true if WebLogic Remote Console is successfully installed, false otherwise.
   */
  public static boolean installWlsRemoteConsole(String domainNamespace, String adminServerPodName) {
    return WebLogicRemoteConsole.installWlsRemoteConsole(domainNamespace, adminServerPodName);
  }

  /**
   * Shutdown WebLogic Remote Console.
   *
   * @return true if WebLogic Remote Console is successfully shutdown, false otherwise.
   */
  public static boolean shutdownWlsRemoteConsole() {
    return WebLogicRemoteConsole.shutdownWlsRemoteConsole();
  }

  /**
   * Install Kibana.
   *
   * @param params the parameters to install Kibana
   * @return true if Kibana is successfully installed, false otherwise.
   */
  public static boolean installKibana(LoggingExporterParams params) {
    return LoggingExporter.installKibana(params);
  }

  /**
   * Uninstall Elasticsearch.
   *
   * @param params the parameters to uninstall Elasticsearch
   * @return true if Elasticsearch is successfully uninstalled, false otherwise.
   */

  public static boolean uninstallElasticsearch(LoggingExporterParams params) {
    return LoggingExporter.uninstallElasticsearch(params);
  }

  /**
   * Uninstall Kibana.
   *
   * @param params the parameters to uninstall Kibana
   * @return true if Kibana is successfully uninstalled, false otherwise.
   */

  public static boolean uninstallKibana(LoggingExporterParams params) {
    return LoggingExporter.uninstallKibana(params);
  }

  /**
   * Verify that the logging exporter is ready to use in Operator pod or WebLogic server pod.
   *
   * @param opNamespace namespace of Operator pod (for ELK Stack) or
   *                  WebLogic server pod (for WebLogic logging exporter)
   * @param esNamespace namespace of Elastic search component
   * @param labelSelector string containing the labels the Operator or WebLogic server is decorated with
   * @param index index key word used to search the index status of the logging exporter
   * @return a map containing key and value pair of logging exporter index
   */
  public static Map<String, String> verifyLoggingExporterReady(String opNamespace,
      String esNamespace,
      String labelSelector,
      String index) {
    return LoggingExporter.verifyLoggingExporterReady(opNamespace, esNamespace, labelSelector, index);
  }

  /**
   * Patch the domain resource with a new restartVersion.
   *
   * @param domainResourceName name of the domain resource
   * @param namespace Kubernetes namespace that the domain is hosted
   * @return restartVersion new restartVersion of the domain resource
   */
  public static String patchDomainResourceWithNewRestartVersion(
      String domainResourceName, String namespace) {
    return Domain.patchDomainResourceWithNewRestartVersion(domainResourceName, namespace);
  }

  /**
   * Patch the domain resource with a new model configMap.
   *
   * @param domainResourceName name of the domain resource
   * @param namespace Kubernetes namespace that the domain is hosted
   * @param configMapName name of the configMap to be set in spec.configuration.model.configMap
   */
  public static void patchDomainResourceWithModelConfigMap(
      String domainResourceName, String namespace, String configMapName) {
    Domain.patchDomainResourceWithModelConfigMap(domainResourceName,
        namespace, configMapName);
  }

  /**
   * Patch a running domain with spec.configuration.model.onlineUpdate.onNonDynamicChanges.
   * spec.configuration.model.onlineUpdate.onNonDynamicChanges accepts three values:
   *   CommitUpdateOnly    - Default value or if not set. All changes are committed, but if there are non-dynamic mbean
   *                         changes. The domain needs to be restart manually.
   *   CommitUpdateAndRoll - All changes are committed, but if there are non-dynamic mbean changes,
   *                         the domain will rolling restart automatically; if not, no restart is necessary
   *   CancelUpdate        - If there are non-dynamic mbean changes, all changes are canceled before
   *                         they are committed. The domain will continue to run, but changes to the configmap
   *                         and resources in the domain resource YAML should be reverted manually,
   *                         otherwise in the next introspection will still use the same content
   *                         in the changed configmap
   *
   * @param domainUid UID of the domain to patch with spec.configuration.model.onlineUpdate.onNonDynamicChanges
   * @param namespace namespace in which the domain resource exists
   * @param onNonDynamicChanges accepted values: CommitUpdateOnly|CommitUpdateAndRoll|CancelUpdate
   * @return introspectVersion new introspectVersion of the domain resource
   */
  public static String patchDomainResourceWithOnNonDynamicChanges(
      String domainUid, String namespace, String onNonDynamicChanges) {
    return Domain.patchDomainResourceWithOnNonDynamicChanges(domainUid, namespace, onNonDynamicChanges);
  }

  /**
   * Patch the cluster resource with a new restartVersion.
   *
   * @param clusterResourceName name of the cluster resource
   * @param namespace Kubernetes namespace that the cluster is hosted
   * @return restartVersion new restartVersion of the cluster resource
   */
  public static String patchClusterResourceWithNewRestartVersion(
      String clusterResourceName, String namespace) {
    return Cluster.patchClusterResourceWithNewRestartVersion(clusterResourceName, namespace);
  }

  /**
   * Get the name of the operator pod.
   *
   * @param release release name of the operator
   * @param namespace Kubernetes namespace that the operator belongs to
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String getOperatorPodName(String release, String namespace) throws ApiException {
    return Kubernetes.getOperatorPodName(release, namespace);
  }

  /**
   * Append the helmValues to the given string buffer.
   * @param helmValues hash map with key, value pairs
   * @return string with chart helmValues
   */
  public static String helmValuesToString(Map<String, Object> helmValues) {
    return Helm.valuesToString(helmValues);
  }

  /**
   * Return the current time, but truncated to the second so that comparisons with Kubernetes timestamps,
   * which are often to the nearest second, work as expected.
   * @return Current time.
   */
  public static OffsetDateTime now() {
    return OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
  }
}
