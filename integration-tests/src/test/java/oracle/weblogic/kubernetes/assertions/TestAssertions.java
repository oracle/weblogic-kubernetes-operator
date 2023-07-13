// Copyright (c) 2020, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.util.Yaml;
import oracle.weblogic.domain.ClusterCondition;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.ClusterStatus;
import oracle.weblogic.domain.DomainCondition;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.ServerStatus;
import oracle.weblogic.kubernetes.actions.impl.LoggingExporter;
import oracle.weblogic.kubernetes.assertions.impl.Application;
import oracle.weblogic.kubernetes.assertions.impl.Cluster;
import oracle.weblogic.kubernetes.assertions.impl.ClusterRole;
import oracle.weblogic.kubernetes.assertions.impl.ClusterRoleBinding;
import oracle.weblogic.kubernetes.assertions.impl.Domain;
import oracle.weblogic.kubernetes.assertions.impl.Grafana;
import oracle.weblogic.kubernetes.assertions.impl.Helm;
import oracle.weblogic.kubernetes.assertions.impl.Image;
import oracle.weblogic.kubernetes.assertions.impl.Job;
import oracle.weblogic.kubernetes.assertions.impl.Kubernetes;
import oracle.weblogic.kubernetes.assertions.impl.Nginx;
import oracle.weblogic.kubernetes.assertions.impl.Operator;
import oracle.weblogic.kubernetes.assertions.impl.PersistentVolume;
import oracle.weblogic.kubernetes.assertions.impl.PersistentVolumeClaim;
import oracle.weblogic.kubernetes.assertions.impl.Pod;
import oracle.weblogic.kubernetes.assertions.impl.Prometheus;
import oracle.weblogic.kubernetes.assertions.impl.Service;
import oracle.weblogic.kubernetes.assertions.impl.Traefik;
import oracle.weblogic.kubernetes.assertions.impl.WitAssertion;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.CLUSTER_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.listSecrets;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.getClusterCustomResource;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * General assertions needed by the tests to validate CRD, Domain, Pods etc.
 */
public class TestAssertions {

  /**
   * Check if Operator is running.
   *
   * @param namespace in which is operator is running
   * @return true if running false otherwise
   */
  public static Callable<Boolean> operatorIsReady(String namespace) {
    return Operator.isReady(namespace);
  }
  
  /**
   * Check if Operator WebHook pod is running.
   *
   * @param namespace in which is operator webhook pod is running
   * @return true if running false otherwise
   */
  public static Callable<Boolean> operatorWebhookIsReady(String namespace) {
    return Operator.isWebhookReady(namespace);
  }  

  /**
   * Check if NGINX is running.
   *
   * @param namespace in which to check if NGINX is running
   * @return true if NGINX is running, false otherwise
   */
  public static Callable<Boolean> isNginxRunning(String namespace) {
    return Nginx.isRunning(namespace);
  }

  /**
   * Check if there are ready NGINX pods in the specified namespace.
   *
   * @param namespace in which to check if NGINX pods are in the ready state
   * @return true if there are ready NGINX pods in the specified namespace , false otherwise
   */
  public static Callable<Boolean> isNginxReady(String namespace) {
    return Nginx.isReady(namespace);
  }

  /**
   * Check traefik controller pod is ready in the specified namespace.
   *
   * @param namespace in which to check for traefik pod readiness
   * @return true if traefik pod is ready, false otherwise
   */
  public static Callable<Boolean> isTraefikReady(String namespace) {
    return Traefik.isReady(namespace);
  }

  /**
   * Check if ELK Stack pod is ready in a given namespace.
   *
   * @param namespace in which to check ELK Stack pod is ready
   * @param podName name of ELK Stack pod
   * @return true if ELK Stack pod is in the ready state, false otherwise
   */
  public static Callable<Boolean> isElkStackPodReady(String namespace, String podName) {
    return LoggingExporter.isReady(namespace, podName);
  }

  /**
   * Check if operator REST service is running.
   *
   * @param namespace in which the operator REST service exists
   * @return true if REST service is running otherwise false
   */
  public static Callable<Boolean> operatorRestServiceRunning(String namespace) {
    return () -> Operator.doesExternalRestServiceExists(namespace);
  }

  /**
   * Check if a WebLogic custom resource domain object exists in specified
   * namespace.
   *
   * @param domainUid ID of the domain
   * @param domainVersion version of the domain resource definition
   * @param namespace in which the domain custom resource object exists
   * @return true if domain object exists
   */
  public static Callable<Boolean> domainExists(String domainUid, String domainVersion, String namespace) {
    return () -> Domain.doesDomainExist(domainUid, domainVersion, namespace);
  }

  /**
   * Check if a WebLogic custom resource domain object does not exist in specified namespace.
   *
   * @param domainUid ID of the domain
   * @param domainVersion version of the domain resource definition
   * @param namespace in which the domain custom resource object exists
   * @return true if domain object exists
   */
  public static Callable<Boolean> domainDoesNotExist(String domainUid, String domainVersion, String namespace) {
    return () -> !Domain.doesDomainExist(domainUid, domainVersion, namespace);
  }

  /**
   * Check if a WebLogic custom resource domain object exists in specified
   * namespace.
   *
   * @param domainUid ID of the domain
   * @param domainVersion version of the domain resource definition
   * @param namespace in which the domain custom resource object exists
   * @return boolean true if domain object exists
   */
  public static boolean doesDomainExist(String domainUid, String domainVersion, String namespace) {
    return Domain.doesDomainExist(domainUid, domainVersion, namespace);
  }

  /**
   * Check if a pod's restartVersion has been updated.
   *
   * @param podName   name of the pod to check
   * @param domainUid WebLogic domain uid in which the pod belongs
   * @param namespace in which the pod is running
   * @param expectedRestartVersion restartVersion that is expected
   * @return true if the pod's restartVersion has been updated
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean podRestartVersionUpdated(
      String podName,
      String domainUid,
      String namespace,
      String expectedRestartVersion
  ) throws ApiException {
    return Kubernetes.podRestartVersionUpdated(namespace, domainUid, podName, expectedRestartVersion);
  }

  /**
   * Check if a pod's introspectVersion has been updated.
   *
   * @param podName   name of the pod to check
   * @param namespace in which the pod is running
   * @param expectedIntrospectVersion introspectVersion that is expected
   * @return true if the pod's introspectVersion has been updated
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean podIntrospectVersionUpdated(
      String podName,
      String namespace,
      String expectedIntrospectVersion
  ) throws ApiException {
    return Kubernetes.podIntrospectVersionUpdated(namespace, podName, expectedIntrospectVersion);
  }

  /**
   * Check if a WebLogic domain custom resource has been patched with a new WebLogic credentials secret.
   *
   * @param domainUid ID of the domain resource
   * @param namespace Kubernetes namespace in which the domain custom resource object exists
   * @param secretName name of the secret that was used to patch the domain resource
   * @return true if the domain is patched correctly
   */
  public static Callable<Boolean> domainResourceCredentialsSecretPatched(
      String domainUid,
      String namespace,
      String secretName
  ) {
    return () -> Domain.domainResourceCredentialsSecretPatched(domainUid, namespace, secretName);
  }

  /**
   * Check if a WebLogic domain custom resource has been patched with a new image.
   *
   * @param domainUid ID of the domain resource
   * @param namespace Kubernetes namespace in which the domain custom resource object exists
   * @param image name of the image that was used to patch the domain resource
   * @return true if the domain is patched correctly
   */
  public static Callable<Boolean> domainResourceImagePatched(
      String domainUid,
      String namespace,
      String image
  ) {
    return () -> Domain.domainResourceImagePatched(domainUid, namespace, image);
  }

  /**
   * Check if a WebLogic server pod has been patched with a new image.
   *
   * @param domainUid ID of the domain resource
   * @param namespace Kubernetes namespace in which the domain custom resource object exists
   * @param podName name of the WebLogic server pod
   * @param containerName name of the container inside the pod where the image is used
   * @param image name of the image that was used to patch the domain resource
   * @return true if the pod is patched correctly
   */
  public static Callable<Boolean> podImagePatched(
      String domainUid,
      String namespace,
      String podName,
      String containerName,
      String image
  ) {
    return () -> {
      return Kubernetes.podImagePatched(namespace, domainUid, podName, containerName, image);
    };
  }

  /**
   * Check if a Kubernetes pod exists in any state in the given namespace.
   *
   * @param podName   name of the pod to check for
   * @param domainUid UID of WebLogic domain in which the pod exists
   * @param namespace in which the pod exists
   * @return true if the pod exists in the namespace otherwise false
   */
  public static Callable<Boolean> podExists(String podName, String domainUid, String namespace) {
    return Pod.podExists(podName, domainUid, namespace);
  }

  /**
   * Check a named pod does not exist in the given namespace.
   *
   * @param podName name of the pod to check for
   * @param domainUid Uid of WebLogic domain
   * @param namespace namespace in which to check for the pod
   * @return true if the pod does not exist in the namespace otherwise false
   */
  public static Callable<Boolean> podDoesNotExist(String podName, String domainUid, String namespace) {
    return Pod.podDoesNotExist(podName, domainUid, namespace);
  }

  /**
   * Check if a Kubernetes pod is in running/ready state.
   *
   * @param podName   name of the pod to check for
   * @param domainUid WebLogic domain uid in which the pod belongs
   * @param namespace in which the pod is running
   * @return true if the pod is running otherwise false
   */
  public static Callable<Boolean> podReady(String podName, String domainUid, String namespace) {
    return Pod.podReady(namespace, domainUid, podName);
  }

  /**
   * Checks if the pod is running in a given namespace.
   * The method assumes the pod name to starts with provided value for podName
   * and decorated with provided label selector
   * @param podName name of pod
   * @param labels label for pod
   * @param namespace in which to check for the pod existence
   * @return true if pods are exist and running otherwise false
   * @throws ApiException when there is error in querying the cluster
   */
  public static Callable<Boolean> isPodReady(String namespace,
                                             Map<String, String> labels,
                                             String podName) throws ApiException {
    return Pod.podReady(namespace, podName,labels);
  }

  /**
   * Check if a Kubernetes pod is in initializing state.
   *
   * @param podName   name of the pod to check for
   * @param domainUid WebLogic domain uid in which the pod belongs
   * @param namespace in which the pod is initializing
   * @return true if the pod is initializing otherwise false
   */
  public static Callable<Boolean> podInitialized(String podName, String domainUid, String namespace) {
    return Pod.podInitialized(namespace, domainUid, podName);
  }

  /**
   * Check if a pod given by the podName is in Terminating state.
   *
   * @param podName   name of the pod to check for Terminating status
   * @param domainUid WebLogic domain uid in which the pod belongs
   * @param namespace in which the pod is running
   * @return true if the pod is terminating otherwise false
   */
  public static Callable<Boolean> podTerminating(String podName, String domainUid, String namespace) {
    return Pod.podTerminating(namespace, domainUid, podName);
  }

  /**
   * Verify pods are restarted in a rolling fashion with not more than maxUnavailable pods are restarted concurrently.
   * @param pods map of pod names with its creation time stamps
   * @param maxUnavailable number of pods can concurrently restart at the same time
   * @param namespace name of the namespace in which the pod restart status to be checked
   * @return true if pods are restarted in a rolling fashion
   */
  public static boolean verifyRollingRestartOccurred(Map<String, OffsetDateTime> pods,
                                                     int maxUnavailable, String namespace) {
    return Pod.verifyRollingRestartOccurred(pods, maxUnavailable, namespace);
  }


  /**
   * Check is a service exists in given namespace.
   *
   * @param serviceName the name of the service to check for
   * @param label       a Map of key value pairs the service is decorated with
   * @param namespace   in which the service is running
   * @return true if the service exists otherwise false
   */
  public static Callable<Boolean> serviceExists(
      String serviceName,
      Map<String, String> label,
      String namespace) {
    return Service.serviceExists(serviceName, label, namespace);
  }


  /**
   * Check if a service Load Balancer External IP is generated.
   *
   * @param serviceName the name of the service to check for
   * @param label       a Map of key value pairs the service is decorated with
   * @param namespace   in which the service is running
   * @return true if the service Load Balancer External IP is generated otherwise false
   */
  public static Callable<Boolean> isOCILoadBalancerReady(
      String serviceName,
      Map<String, String> label,
      String namespace) {
    return () -> {
      return Kubernetes.isOCILoadBalancerReady(serviceName, label, namespace);
    };
  }

  /**
   * Check a service does not exist in the specified namespace.
   *
   * @param serviceName the name of the service to check for
   * @param label       a Map of key value pairs the service is decorated with
   * @param namespace   in which to check whether the service exists
   * @return true if the service does not exist, false otherwise
   */
  public static Callable<Boolean> serviceDoesNotExist(String serviceName,
                                                      Map<String, String> label,
                                                      String namespace) {
    return () -> !Kubernetes.doesServiceExist(serviceName, label, namespace);
  }

  /**
   * Check the status cluster in the domain matches status in the cluster resource.
   * @param domainUid  domain uid
   * @param namespace namespace in which the domain resource exists
   * @param clusterName  name of cluster
   * @return true if the status matches, false otherwise
   */
  public static Callable<Boolean> clusterStatusMatchesDomain(String domainUid, String namespace,
                                                   String clusterName) throws ApiException {
    LoggingFacade logger = getLogger();
    return () -> {
      DomainResource domain =
          assertDoesNotThrow(() -> getDomainCustomResource(domainUid, namespace));
      logger.info("Domain resource : " + Yaml.dump(domain));
      if (domain.getSpec().getClusters().stream().anyMatch(cluster -> cluster.getName().contains(clusterName))) {

        if (domain != null && domain.getStatus() != null) {
          List<ClusterStatus> clusterStatusList = domain.getStatus().getClusters();
          logger.info(Yaml.dump(clusterStatusList));

          for (ClusterStatus domainClusterStatus : clusterStatusList) {
            boolean match = true;
            if (domainClusterStatus.getClusterName().equalsIgnoreCase(clusterName)) {
              logger.info("Checking cluster resource status for cluster {0} status {1}",
                  domainClusterStatus.getClusterName(), domainClusterStatus.toString());
              ClusterResource clusterRes = getClusterCustomResource(domainUid + "-" + clusterName,
                  namespace, CLUSTER_VERSION);
              assertNotNull(clusterRes, "ClusterResource does not exists");
              assertNotNull(clusterRes.getStatus(), "ClusterResource status is null");
              logger.info("Cluster Resource status : " + Yaml.dump(clusterRes.getStatus()));
              ClusterStatus clusterResourceStatus = clusterRes.getStatus();
              if (domainClusterStatus.getMaximumReplicas() != clusterResourceStatus.getMaximumReplicas()) {
                logger.info("MaximumReplicas {0} number in domain.status does not match  ClusterResource status {1}",
                    domainClusterStatus.getMaximumReplicas(), clusterResourceStatus.getMaximumReplicas());
                match = false;
              }
              if (domainClusterStatus.getMinimumReplicas() != clusterResourceStatus.getMinimumReplicas()) {
                logger.info("MinimumReplicas {0} number in domain.status does not match  ClusterResource status {1}",
                    domainClusterStatus.getMinimumReplicas(), clusterResourceStatus.getMinimumReplicas());
                match = false;
              }
              if (domainClusterStatus.getObservedGeneration() != clusterResourceStatus.getObservedGeneration()) {
                logger.info("ObservedGeneration {0} number in domain.status does not match  "
                        + "clusterResource ObservedGeneration status {1}",
                    domainClusterStatus.getObservedGeneration(), clusterResourceStatus.getObservedGeneration());
                match = false;
              }
              if (domainClusterStatus.getReplicasGoal() != clusterResourceStatus.getReplicasGoal()) {
                logger.info("ReplicasGoal {0} number in domain.status does not match  ClusterResource status {1}",
                    domainClusterStatus.getReplicasGoal(), clusterResourceStatus.getReplicasGoal());
                match = false;
              }
              if (domainClusterStatus.getReplicasGoal() > 0) {
                if (domainClusterStatus.getReadyReplicas() != clusterResourceStatus.getReadyReplicas()) {
                  logger.info("ReadyReplicas {0} number in domain.status does not match  ClusterResource status {1}",
                      domainClusterStatus.getReadyReplicas(), clusterResourceStatus.getReadyReplicas());
                  match = false;
                }
                if (domainClusterStatus.getReplicas() != clusterResourceStatus.getReplicas()) {
                  logger.info("Replicas {0} number in domain.status does not match  ClusterResource status {1}",
                      domainClusterStatus.getReplicas(), clusterResourceStatus.getReplicas());
                  match = false;
                }
              }
              return match;
            }
          }
        } else {
          if (domain == null) {
            logger.info("domain is null");
          } else {
            logger.info("domain status is null");
          }
        }
      } else {
        logger.info("Cluster with name {0} is not in the domain spec", clusterName);
      }
      return false;
    };
  }

  /**
   * Check the status conditions for cluster in the domain matches status conditions in the cluster resource.
   * @param domainUid  domain uid
   * @param namespace namespace in which the domain resource exists
   * @param clusterName name of cluster
   * @param conditionType conditions type
   * @param expectedStatus expected status
   * @return true if the status matches, false otherwise
  */
  public static Callable<Boolean> clusterStatusConditionsMatchesDomain(String domainUid, String namespace,
                                                                       String clusterName,
                                                                       String conditionType, String expectedStatus)
      throws ApiException {
    LoggingFacade logger = getLogger();
    return () -> {
      DomainResource domain =
          assertDoesNotThrow(() -> getDomainCustomResource(domainUid, namespace));

      if (domain != null && domain.getStatus() != null) {
        List<ClusterStatus> clusterStatusList = domain.getStatus().getClusters();
        logger.info(Yaml.dump(clusterStatusList));
        for (ClusterStatus domainClusterStatus : clusterStatusList) {
          if (domainClusterStatus.getClusterName().equalsIgnoreCase(clusterName)) {
            logger.info("Checking cluster resource status for cluster {0} status {1}",
                domainClusterStatus.getClusterName(), domainClusterStatus.toString());
            ClusterResource clusterRes = getClusterCustomResource(domainUid + "-"
                + clusterName, namespace, CLUSTER_VERSION);
            assertNotNull(clusterRes, "ClusterResource does not exists");
            assertNotNull(clusterRes.getStatus(), "ClusterResource status is null");
            logger.info("Cluster Resource status : " + Yaml.dump(clusterRes.getStatus()));
            ClusterStatus clusterResourceStatus = clusterRes.getStatus();
            List<ClusterCondition> clusterResConditionList = clusterResourceStatus.getConditions();
            List<ClusterCondition> domainClusterConditionList = domainClusterStatus.getConditions();
            logger.info("Domain Cluster conditions : " + Yaml.dump(domainClusterConditionList));
            logger.info("Cluster Resource conditions : " + Yaml.dump(clusterResConditionList));
            boolean foundConditionTypeInClusterResource = false;
            boolean foundConditionTypeInDomainStatusCluster = false;
            OffsetDateTime lastTransitionTimeClusterRes = null;
            OffsetDateTime lastTransitionTimeDomainCluster = null;
            for (ClusterCondition clusterResCondition : clusterResConditionList) {
              if (clusterResCondition.getType().equalsIgnoreCase(conditionType)) {
                if (expectedStatus.equals(clusterResCondition.getStatus())) {
                  lastTransitionTimeClusterRes = clusterResCondition.getLastTransitionTime();
                  foundConditionTypeInClusterResource = true;
                  logger.info("Found matching condition type {0} and status {1} for cluster resource {2}",
                      clusterResCondition.getType(), clusterResCondition.getStatus(),
                      clusterResourceStatus.getClusterName());
                }
              }
            }
            for (ClusterCondition domainClusterCondition : domainClusterConditionList) {
              if (domainClusterCondition.getType().equalsIgnoreCase(conditionType)) {
                if (expectedStatus.equals(domainClusterCondition.getStatus())) {
                  foundConditionTypeInDomainStatusCluster = true;
                  lastTransitionTimeDomainCluster = domainClusterCondition.getLastTransitionTime();
                  logger.info("Found matching condition type {0} and status {1} for domain status cluster {2} ",
                      domainClusterCondition.getType(), domainClusterCondition.getStatus(),
                      domainClusterStatus.getClusterName());
                }
              }
            }
            boolean isTransitionTimeSame = (lastTransitionTimeDomainCluster != null
                && lastTransitionTimeDomainCluster.isEqual(lastTransitionTimeClusterRes));
            if (!isTransitionTimeSame) {
              logger.info("Found not matching condition lastTransitionTime {0} "
                      + "for domain cluster status and cluster resource status {1} ",
                  lastTransitionTimeDomainCluster, lastTransitionTimeClusterRes);
            }
            //commented out check for lastTransition time until OWLS-102910 will be fixed
            //return (foundConditionTypeInClusterResource &&
            // foundConditionTypeInDomainStatusCluster && isTransitionTimeSame);
            return (foundConditionTypeInClusterResource && foundConditionTypeInDomainStatusCluster);
          }
        }
      } else {
        if (domain == null) {
          logger.info("domain is null");
        } else {
          logger.info("domain status is null");
        }
      }
      return false;
    };
  }

  /**
   * Check the status reason of the domain matches the given reason.
   * @param domainUid  domain uid
   * @param namespace namespace in which the domain resource exists
   * @param statusReason the expected status reason of the domain
   * @return true if the status reason matches, false otherwise
   */
  public static Callable<Boolean> domainStatusReasonMatches(String domainUid, String namespace,
      String statusReason) {
    LoggingFacade logger = getLogger();
    return () -> {
      DomainResource domain = getDomainCustomResource(domainUid, namespace);
      if (domain != null && domain.getStatus() != null && domain.getStatus().getConditions() != null
          && !domain.getStatus().getConditions().isEmpty()) {
        boolean match = domain.getStatus().getConditions().stream()
            .anyMatch(condition -> condition.getReason() != null && condition.getReason().contains(statusReason));
        return match;
      } else {
        if (domain == null) {
          logger.info("domain is null");
        } else if (domain.getStatus() == null) {
          logger.info("domain status is null");
        } else {
          logger.info("domain status conditions is empty");
        }
        return false;
      }
    };
  }

  /**
   * Check the status message of the domain contains the expected message.
   * @param domainUid  domain uid
   * @param namespace namespace in which the domain resource exists
   * @param statusMsg the expected status message of the domain
   * @return true if the status message contains the expected message, false otherwise
   */
  public static Callable<Boolean> domainStatusMessageContainsExpectedMsg(String domainUid, String namespace,
                                                                         String statusMsg) {
    LoggingFacade logger = getLogger();
    return () -> {
      DomainResource domain = getDomainCustomResource(domainUid, namespace);
      if (domain != null && domain.getStatus() != null && domain.getStatus().getMessage() != null) {
        return domain.getStatus().getMessage().equalsIgnoreCase(statusMsg);
      } else {
        if (domain == null) {
          logger.info("domain is null");
        } else if (domain.getStatus() == null) {
          logger.info("domain status is null");
        } else if (domain.getStatus().getMessage() == null) {
          logger.info("domain status message is null");
        }
        return false;
      }
    };
  }

  /**
   * Check the domain status condition type exists.
   * @param domainUid uid of the domain
   * @param domainNamespace namespace of the domain
   * @param conditionType the type name of condition, accepted value: Completed, Available, Failed and
   *                      ConfigChangesPendingRestart
   * @return true if the condition type exists, false otherwise
   */
  public static Callable<Boolean> domainStatusConditionTypeExists(String domainUid,
                                                                  String domainNamespace,
                                                                  String conditionType) {
    return domainStatusConditionTypeExists(domainUid, domainNamespace, conditionType, DOMAIN_VERSION);
  }

  /**
   * Check the domain status condition type exists.
   * @param domainUid uid of the domain
   * @param domainNamespace namespace of the domain
   * @param conditionType the type name of condition, accepted value: Completed, Available, Failed and
   *                      ConfigChangesPendingRestart
   * @param domainVersion version of domain
   * @return true if the condition type exists, false otherwise
   */
  public static Callable<Boolean> domainStatusConditionTypeExists(String domainUid,
                                                                  String domainNamespace,
                                                                  String conditionType,
                                                                  String domainVersion) {
    LoggingFacade logger = getLogger();
    return () -> {
      DomainResource domain =
          assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace, domainVersion));

      if (domain != null && domain.getStatus() != null) {
        List<DomainCondition> domainConditionList = domain.getStatus().getConditions();
        logger.info(Yaml.dump(domainConditionList));
        for (DomainCondition domainCondition : domainConditionList) {
          if (domainCondition.getType().equalsIgnoreCase(conditionType)) {
            return true;
          }
        }
      } else {
        if (domain == null) {
          logger.info("domain is null");
        } else {
          logger.info("domain status is null");
        }
      }
      return false;
    };
  }

  /**
   * Check the domain status condition type has expected status.
   * @param domainUid uid of the domain
   * @param domainNamespace namespace of the domain
   * @param conditionType the type name of condition, accepted value: Completed, Available, Failed and
   *                      ConfigChangesPendingRestart
   * @param expectedStatus expected status value, either True or False
   * @return true if the condition type has the expected status, false otherwise
   */
  public static Callable<Boolean> domainStatusConditionTypeHasExpectedStatus(String domainUid,
                                                                             String domainNamespace,
                                                                             String conditionType,
                                                                             String expectedStatus) {
    return domainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        conditionType, expectedStatus, DOMAIN_VERSION);
  }

  /**
   * Check the domain status condition type has expected status.
   * @param domainUid uid of the domain
   * @param domainNamespace namespace of the domain
   * @param conditionType the type name of condition, accepted value: Completed, Available, Failed and
   *                      ConfigChangesPendingRestart
   * @param expectedStatus expected status value, either True or False
   * @param domainVersion version of domain
   * @return true if the condition type has the expected status, false otherwise
   */
  public static Callable<Boolean> domainStatusConditionTypeHasExpectedStatus(String domainUid,
                                                                             String domainNamespace,
                                                                             String conditionType,
                                                                             String expectedStatus,
                                                                             String domainVersion) {
    LoggingFacade logger = getLogger();

    return () -> {
      DomainResource domain =
          assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace, domainVersion));

      if (domain != null && domain.getStatus() != null) {
        List<DomainCondition> domainConditionList = domain.getStatus().getConditions();
        logger.info(Yaml.dump(domainConditionList));
        for (DomainCondition domainCondition : domainConditionList) {
          if (domainCondition.getType().equalsIgnoreCase(conditionType)
              && domainCondition.getStatus().equalsIgnoreCase(expectedStatus)) {
            return true;
          } else {
            logger.info("domainCondition={0}", domainCondition.toString());
          }
        }
      } else {
        if (domain == null) {
          logger.info("domain is null");
        } else {
          logger.info("domain status is null");
        }
      }
      return false;
    };
  }

  /**
   * Check the domain status condition type has expected status.
   * @param domainUid uid of the domain
   * @param domainNamespace namespace of the domain
   * @param clusterName cluster name of the domain
   * @param conditionType the type name of condition, accepted value: Completed, Available, Failed and
   *                      ConfigChangesPendingRestart
   * @param expectedStatus expected status value, either True or False
   * @param domainVersion version of domain
   * @return true if the condition type has the expected status, false otherwise
   */
  public static Callable<Boolean> domainStatusClustersConditionTypeHasExpectedStatus(String domainUid,
                                                                                     String domainNamespace,
                                                                                     String clusterName,
                                                                                     String conditionType,
                                                                                     String expectedStatus,
                                                                                     String domainVersion) {
    LoggingFacade logger = getLogger();

    return () -> {
      DomainResource domain =
          assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace, domainVersion));

      if (domain != null && domain.getStatus() != null) {
        List<ClusterStatus> clusterStatusList = domain.getStatus().getClusters();
        logger.info(Yaml.dump(clusterStatusList));
        for (ClusterStatus clusterStatus : clusterStatusList) {
          if (clusterStatus.getClusterName() != null && clusterStatus.getClusterName().equals(clusterName)) {
            List<ClusterCondition> clusterConditions = clusterStatus.getConditions();
            for (ClusterCondition clusterCondition : clusterConditions) {
              if (clusterCondition.getType() != null && clusterCondition.getType().equalsIgnoreCase(conditionType)
                  && clusterCondition.getStatus() != null
                  && clusterCondition.getStatus().equalsIgnoreCase(expectedStatus)) {
                return true;
              }
            }
          }
        }
      } else {
        if (domain == null) {
          logger.info("domain is null");
        } else {
          logger.info("domain status is null");
        }
      }
      return false;
    };
  }

  /**
   * Check the staus of the given server in domain status.
   *
   * @param domainUid uid of the domain
   * @param domainNamespace namespace of the domain
   * @param serverName name of the server
   * @param podPhase phase of the server pod
   * @param podReadyStatus status of the pod Ready condition
   * @return true if the condition type has the expected status, false otherwise
   */
  public static Callable<Boolean> domainStatusServerStatusHasExpectedPodStatus(String domainUid,
                                                                             String domainNamespace,
                                                                             String serverName,
                                                                             String podPhase,
                                                                             String podReadyStatus) {
    LoggingFacade logger = getLogger();

    return () -> {
      DomainResource domain =
          assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace));

      if (domain != null && domain.getStatus() != null) {
        List<ServerStatus> serverStatusList = domain.getStatus().getServers();
        logger.info(Yaml.dump(serverStatusList));
        for (ServerStatus server : serverStatusList) {
          if (server.getPodPhase().equalsIgnoreCase(podPhase)
              && server.getPodReady().equalsIgnoreCase(podReadyStatus)) {
            return true;
          } else {
            logger.info("serverStatus={0}", server);
          }
        }
      } else {
        if (domain == null) {
          logger.info("domain is null");
        } else {
          logger.info("domain status is null");
        }
      }
      return false;
    };
  }


  /**
   * Check if a loadbalancer pod is ready.
   *
   * @param domainUid id of the WebLogic domain custom resource domain
   * @return true, if the load balancer is ready
   */
  public static boolean loadbalancerReady(String domainUid) {
    return Kubernetes.loadBalancerReady(domainUid);
  }

  /**
   * Check if the admin server pod is ready.
   *
   * @param domainUid id of the domain in which admin server pod is running
   * @param namespace in which the pod exists
   * @return true if the admin server is ready otherwise false
   */
  public static boolean adminServerReady(String domainUid, String namespace) {
    return Kubernetes.adminServerReady(domainUid, namespace);
  }

  /**
   * Check if a adminserver T3 channel is accessible.
   *
   * @param domainUid id of the domain in which admin server pod is running
   * @param namespace in which the WebLogic server pod exists
   * @return true if the admin T3 channel is accessible otherwise false
   */
  public static boolean adminT3ChannelAccessible(String domainUid, String namespace) {
    return Domain.adminT3ChannelAccessible(domainUid, namespace);
  }

  /**
   * Check if a admin server pod admin node port is accessible.
   *
   * @param nodePort the node port of the WebLogic administration server service
   * @param userName user name to access WebLogic administration server
   * @param password password to access WebLogic administration server
   * @return true if the WebLogic administration service node port is accessible otherwise false
   * @throws java.io.IOException when connection to WebLogic administration server fails
   */
  public static Callable<Boolean> adminNodePortAccessible(int nodePort, String userName,
                                                     String password, String... routeHost)
      throws IOException {
    if (routeHost.length == 0) {
      return () -> Domain.adminNodePortAccessible(nodePort, userName, password, null);
    } else {
      return () -> Domain.adminNodePortAccessible(nodePort, userName, password, routeHost[0]);
    }
  }

  /**
   * Check if a admin server pod admin node port is accessible.
   *
   * @param nodePort the node port of the WebLogic administration server service
   * @param userName user name to access WebLogic administration server
   * @param password password to access WebLogic administration server
   * @return true if the WebLogic administration service node port is accessible otherwise false
   * @throws java.io.IOException when connection to WebLogic administration server fails
   */
  public static Callable<Boolean> adminLoginPageAccessible(int nodePort, String userName,
                                                          String password, String... routeHost)
      throws IOException {
    if (routeHost.length == 0) {
      return () -> Domain.adminNodePortAccessible(nodePort, userName, password, null);
    } else {
      return () -> Domain.adminNodePortAccessible(nodePort, userName, password, routeHost[0]);
    }
  }

  /**
   * Check if an image exists.
   *
   * @param imageName the name of the image to be checked
   * @param imageTag  the tag of the image to be checked
   * @return true if the image does exist, false otherwise
   */
  public static boolean imageExists(String imageName, String imageTag) {
    return WitAssertion.doesImageExist(imageName, imageTag);
  }

  /**
   * Check if the given WebLogic credentials are valid by using the credentials to
   * invoke a RESTful Management Services command.
   *
   * @param port listen port of the admin server pod
   * @param podName name of the admin server pod
   * @param namespace name of the namespace that the pod is running in
   * @param username WebLogic admin username
   * @param password WebLogic admin password
   * @return true if the RESTful Management Services command succeeded
   */
  public static Callable<Boolean> credentialsValid(
      int port,
      String podName,
      String namespace,
      String username,
      String password,
      String... args) {
    return () -> Domain.credentialsValid(port, podName, namespace, username, password, args);
  }

  /**
   * Check if the given WebLogic credentials are NOT valid by using the credentials to
   * invoke a RESTful Management Services command.
   *
   * @param port listen port of the admin server pod
   * @param podName name of the admin server pod
   * @param namespace name of the namespace that the pod is running in
   * @param username WebLogic admin username
   * @param password WebLogic admin password
   * @return true if the RESTful Management Services command failed with exitCode 401
   */
  public static Callable<Boolean> credentialsNotValid(
      int port,
      String podName,
      String namespace,
      String username,
      String password,
      String... args) {
    return () -> Domain.credentialsNotValid(port, podName, namespace, username, password, args);
  }

  /**
   * Check if an application is accessible inside a WebLogic server pod.
   *
   * @param namespace Kubernetes namespace where the WebLogic server pod is running
   * @param podName name of the WebLogic server pod
   * @param port internal port of the managed server running in the pod
   * @param appPath path to access the application
   * @param expectedResponse the expected response from the application
   * @return true if the command succeeds
   */
  public static boolean appAccessibleInPod(
      String namespace,
      String podName,
      String port,
      String appPath,
      String expectedResponse
  ) {
    return Application.appAccessibleInPodKubernetesCLI(namespace, podName, port, appPath, expectedResponse);
  }

  /**
   * Check if an application is Not running inside a WebLogic server pod.
   * .
   * @param namespace Kubernetes namespace where the WebLogic server pod is running
   * @param podName name of the WebLogic server pod
   * @param port internal port of the managed server running in the pod
   * @param appPath path to access the application
   * @param expectedResponse the expected response from the application
   * @return true if the command succeeds
   */
  public static boolean appNotAccessibleInPod(
      String namespace,
      String podName,
      String port,
      String appPath,
      String expectedResponse
  ) {
    return !Application.appAccessibleInPodKubernetesCLI(namespace, podName, port, appPath, expectedResponse);
  }

  /**
   * Check if the image containing the search string exists.
   * @param searchString search string
   * @return true on success
   */
  public static boolean doesImageExist(String searchString) {
    return Image.doesImageExist(searchString);
  }

  /**
   * Check Helm release status is deployed.
   * @param releaseName release name which unique in a namespace
   * @param namespace namespace name
   * @return true on success
   */
  public static boolean isHelmReleaseDeployed(String releaseName, String namespace) {
    return Helm.isReleaseDeployed(releaseName, namespace);
  }

  /**
   * Check Helm release status is deployed.
   * @param releaseName release name which unique in a namespace
   * @param namespace namespace name
   * @param status expected helm release status
   * @return true on success
   */
  public static boolean checkHelmReleaseStatus(String releaseName, String namespace, String status) {
    return Helm.checkHelmReleaseStatus(releaseName, namespace, status);
  }

  /**
   * Check Helm release status is deployed.
   * @param releaseName release name which unique in a namespace
   * @param namespace namespace name
   * @return true on success
   */
  public static boolean isHelmReleaseFailed(String releaseName, String namespace) {
    return Helm.isReleaseFailed(releaseName, namespace);
  }

  /**
   * Check Helm release revision against expected.
   * @param releaseName release name which is unique in a namespace
   * @param namespace namespace name
   * @param revision expected revision for the helm release
   * @return true on success
   */
  public static boolean checkHelmReleaseRevision(String releaseName, String namespace, String revision) {
    return Helm.checkHelmReleaseRevision(releaseName, namespace, revision);
  }

  /**
   * Check if a pod is restarted based on podCreationTimestamp.
   *
   * @param podName the name of the pod to check for
   * @param namespace in which the pod is running
   * @param timestamp the initial podCreationTimestamp
   * @return true if the pod new timestamp is not equal to initial PodCreationTimestamp otherwise false
   */
  public static Callable<Boolean> isPodRestarted(
      String podName,
      String namespace,
      OffsetDateTime timestamp
  ) {
    return () -> {
      return Kubernetes.isPodRestarted(podName, namespace,timestamp);
    };
  }

  /**
   * Check if the oeprator pod in a given namespace is restarted based on podCreationTimestamp.
   *
   * @param namespace in which the pod is running
   * @param timestamp the initial podCreationTimestamp
   * @return true if the pod new timestamp is not equal to initial PodCreationTimestamp otherwise false
   */
  public static Callable<Boolean> isOperatorPodRestarted(
      String namespace,
      OffsetDateTime timestamp
  ) {
    return () -> {
      return Kubernetes.isOperatorPodRestarted(namespace, timestamp);
    };
  }

  /**
   * Verify the pod state is not changed.
   *
   * @param podName the name of the pod to check
   * @param domainUid the label the pod is decorated with
   * @param namespace the namespace in which the pod exists
   * @param podOriginalCreationTimestamp the pod original creation timestamp
   * @return true if the pod state is not changed, false otherwise
   */
  public static boolean podStateNotChanged(String podName,
                                           String domainUid,
                                           String namespace,
                                           OffsetDateTime podOriginalCreationTimestamp) {
    return Domain.podStateNotChanged(podName, domainUid, namespace, podOriginalCreationTimestamp);
  }

  /**
   * Check if a job completed running.
   *
   * @param jobName name of the job to check for its completion status
   * @param labelSelectors label selectors used to get the right pod object
   * @param namespace name of the namespace in which the job running
   * @return true if completed false otherwise
   */
  public static Callable<Boolean> jobCompleted(String jobName, String labelSelectors, String namespace) {
    return Job.jobCompleted(namespace, labelSelectors, jobName);
  }

  /**
   * Check if Prometheus is running.
   *
   * @param namespace in which is prometheus is running
   * @param releaseName name of prometheus helm chart release
   * @return true if running false otherwise
   */
  public static Callable<Boolean> isPrometheusReady(String namespace, String releaseName) {
    return Prometheus.isReady(namespace, releaseName);
  }

  /**
   * Check if the prometheus pods are running in a given namespace.
   * @param namespace in which to check for the prometheus pods
   * @param podName prometheus adapter pod name
   * @return true if found and running otherwise false
   */
  public static Callable<Boolean> isPrometheusAdapterReady(String namespace, String podName) {
    Map<String,String> labelMapPromSvc = new HashMap<>();
    labelMapPromSvc.put("name", "prometheus-adapter");

    return () -> {
      return (Kubernetes.isPodReady(namespace, (Map<String, String>) null, podName));
    };
  }

  /**
   * Check if Grafana is running.
   *
   * @param namespace in which is grafana is running
   * @return true if running false otherwise
   */
  public static Callable<Boolean> isGrafanaReady(String namespace) {
    return Grafana.isReady(namespace);
  }

  /*
   * Check whether persistent volume with pvName exists.
   *
   * @param pvName persistent volume to check
   * @param labelSelector String containing the labels the PV is decorated with
   * @return true if the persistent volume exists, false otherwise
   */
  public static Callable<Boolean> pvExists(String pvName, String labelSelector) {
    return PersistentVolume.pvExists(pvName, labelSelector);
  }


  /**
   * Check whether persistent volume with pvName not exists.
   *
   * @param pvName name of the persistent volume
   * @param labelSelector labelselector for pv
   * @return true if pv not exists otherwise false
   */
  public static Callable<Boolean> pvNotExists(String pvName, String labelSelector) {
    return () -> {
      return !PersistentVolume.pvExists(pvName, labelSelector).call();
    };
  }

  /**
   * Check whether persistent volume claims with pvcName exists in the specified namespace.
   *
   * @param pvcName persistent volume claim to check
   * @param namespace the namespace in which the persistent volume claim to be checked
   * @return true if the persistent volume claim exists in the namespace, false otherwise
   */
  public static Callable<Boolean> pvcExists(String pvcName, String namespace) {
    return PersistentVolumeClaim.pvcExists(pvcName, namespace);
  }

  /**
   * Check whether the cluster role exists.
   *
   * @param clusterRoleName name of the cluster role
   * @return true if cluster role exists, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean clusterRoleExists(String clusterRoleName) throws ApiException {
    return ClusterRole.clusterRoleExists(clusterRoleName);
  }

  /**
   * Check whether the cluster role binding exists.
   *
   * @param clusterRoleBindingName name of the cluster role binding
   * @return true if cluster role binding exists, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean clusterRoleBindingExists(String clusterRoleBindingName) throws ApiException {
    return ClusterRoleBinding.clusterRoleBindingExists(clusterRoleBindingName);
  }

  /**
   * Check whether the secret exists in the specified namespace.
   *
   * @param secretName name of the secret
   * @param namespace namespace in which the secret exists
   * @return true if secret exists, false otherwise
   */
  public static boolean secretExists(String secretName, String namespace) {
    for (V1Secret secret : listSecrets(namespace).getItems()) {
      if (secret.getMetadata() != null) {
        String name = secret.getMetadata().getName();
        if (name != null && name.equals(secretName)) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Verify the web app can be accessed from all managed servers in the domain through Load Balancer.
   * @param curlCmd curl command to call the sample app
   * @param managedServerNames managed server names that the sample app response should return
   * @param maxIterations max iterations to call the curl command
   * @return true if the web app can hit all managed servers, false otherwise
   */
  public static Callable<Boolean> callTestWebAppAndCheckForServerNameInResponse(String curlCmd,
                                                                            List<String> managedServerNames,
                                                                            int maxIterations) {
    return () ->
      callWebAppAndCheckForServerNameInResponse(curlCmd, managedServerNames, maxIterations);
  }

  /**
   * Check if executed command contains expected output.
   *
   * @param pod   V1Pod object
   * @param searchKey expected string in the log
   * @return true if the output matches searchKey otherwise false
   */
  public static Callable<Boolean> searchPodLogForKey(V1Pod pod, String searchKey) {
    return () -> oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.getPodLog(pod.getMetadata().getName(),
        pod.getMetadata().getNamespace()).contains(searchKey);
  }

  /**
   * Check if a WebLogic custom resource cluster object exists in specified
   * namespace.
   *
   * @param clusterResName cluster resource name
   * @param clusterVersion version value for Kind Cluster
   * @param namespace in which the cluster custom resource object exists
   * @return true if cluster object exists
   */
  public static Callable<Boolean> clusterExists(String clusterResName, String clusterVersion, String namespace) {
    return () -> Cluster.doesClusterExist(clusterResName, clusterVersion, namespace);
  }
  
  /**
   * Check if a WebLogic custom resource cluster object does not exist in specified namespace.
   *
   * @param clusterResName cluster resource name
   * @param clusterVersion version value for Kind Cluster
   * @param namespace in which the cluster custom resource object exists
   * @return true if cluster object exists
   */
  public static Callable<Boolean> clusterDoesNotExist(String clusterResName, String clusterVersion, String namespace) {
    return () -> !Cluster.doesClusterExist(clusterResName, clusterVersion, namespace);
  }

  /**
   * Get the value of the domain status condition type.
   * @param domainUid uid of the domain
   * @param domainNamespace namespace of the domain
   * @param conditionType the type name of condition, accepted value: Completed, Available, Failed and
   *                      ConfigChangesPendingRestart
   * @param domainVersion version of domain
   * @return value of the status condition type, True or False
   */
  public static String getDomainStatusConditionTypeValue(String domainUid,
                                                         String domainNamespace,
                                                         String conditionType,
                                                         String domainVersion) {
    LoggingFacade logger = getLogger();

    DomainResource domain =
        assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace, domainVersion));

    if (domain != null && domain.getStatus() != null) {
      List<DomainCondition> domainConditionList = domain.getStatus().getConditions();
      logger.info(Yaml.dump(domainConditionList));
      for (DomainCondition domainCondition : domainConditionList) {
        if (domainCondition.getType().equalsIgnoreCase(conditionType)) {
          return domainCondition.getStatus();
        }
      }
    } else {
      if (domain == null) {
        logger.info("domain is null");
      } else {
        logger.info("domain status is null");
      }
    }
    return "";
  }

  /**
   * Get the value of the domain status cluster condition type.
   * @param domainUid uid of the domain
   * @param domainNamespace namespace of the domain
   * @param clusterName cluster name of the domain
   * @param conditionType the type name of condition, accepted value: Completed, Available, Failed and
   *                      ConfigChangesPendingRestart
   * @param domainVersion version of domain
   * @return true if the condition type has the expected status, false otherwise
   */
  public static String getDomainStatusClustersConditionTypeValue(String domainUid,
                                                                 String domainNamespace,
                                                                 String clusterName,
                                                                 String conditionType,
                                                                 String domainVersion) {
    LoggingFacade logger = getLogger();


    DomainResource domain =
        assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace, domainVersion));

    if (domain != null && domain.getStatus() != null) {
      List<ClusterStatus> clusterStatusList = domain.getStatus().getClusters();
      logger.info(Yaml.dump(clusterStatusList));
      for (ClusterStatus clusterStatus : clusterStatusList) {
        if (clusterStatus.getClusterName() != null && clusterStatus.getClusterName().equals(clusterName)) {
          List<ClusterCondition> clusterConditions = clusterStatus.getConditions();
          for (ClusterCondition clusterCondition : clusterConditions) {
            if (clusterCondition.getType() != null && clusterCondition.getType().equalsIgnoreCase(conditionType)
                && clusterCondition.getStatus() != null) {
              return clusterCondition.getStatus();
            }
          }
        }
      }
    } else {
      if (domain == null) {
        logger.info("domain is null");
      } else {
        logger.info("domain status is null");
      }
    }
    return "";
  }
}
