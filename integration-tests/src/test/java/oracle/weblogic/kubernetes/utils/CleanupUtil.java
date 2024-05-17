// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1IngressList;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1ReplicaSetList;
import io.kubernetes.client.openapi.models.V1Role;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.kubernetes.client.openapi.models.V1RoleBindingList;
import io.kubernetes.client.openapi.models.V1RoleList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import io.kubernetes.client.openapi.models.V1ServiceAccountList;
import io.kubernetes.client.openapi.models.V1ServiceList;
import oracle.weblogic.domain.DomainList;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.TestActions;
import oracle.weblogic.kubernetes.actions.impl.Cluster;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deleteConfigMap;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deleteDeployment;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deleteJob;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deleteNamespacedRole;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deleteNamespacedRoleBinding;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deletePv;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deletePvc;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deleteReplicaSet;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deleteSecret;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deleteService;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deleteServiceAccount;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listConfigMaps;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listDeployments;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listDomains;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listJobs;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listNamespacedIngresses;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listNamespacedRoleBinding;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listNamespacedRoles;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listNamespaces;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listPersistentVolumeClaims;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listPersistentVolumes;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listReplicaSets;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listSecrets;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listServiceAccounts;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listServices;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;

/**
 * CleanupUtil is used for cleaning up all the Kubernetes artifacts left behind by the integration tests.
 *
 */
public class CleanupUtil {

  /**
   * Cleanup Kubernetes artifacts in the namespaces used by the test class.
   *
   * <p>Tries to gracefully delete any existing WebLogic domains and WebLogic Operator in the namespaces.
   * Then deletes everything in the namespaces.
   *
   * <p>Waits for the deletion task to be completed, for up to 10 minutes.
   *
   * @param namespaces list of namespaces
   */
  public static void cleanup(List<String> namespaces) {
    LoggingFacade logger = getLogger();
    try {
      // If namespace list is empty or null return
      if (namespaces == null || namespaces.isEmpty()) {
        logger.info("Nothing to cleanup");
        return;
      }      
      // delete clusters if any exists
      for (var namespace : namespaces) {
        deleteClusters(namespace);
      }
      // delete domains if any exists
      for (var namespace : namespaces) {
        deleteDomains(namespace);
      }
      // delete operators if any exists
      for (var namespace : namespaces) {
        uninstallWebLogicOperator(namespace);
      }

      // Delete artifacts in namespace used by the test class and the namespace itself
      for (var namespace : namespaces) {
        deleteNamespacedArtifacts(namespace);
        deleteNamespace(namespace);
      }

      // Using Thread.sleep for a one time 30 sec sleep.
      // If pollDelay is set to 30 seconds below, its going to sleep 30 seconds for every namespace.
      try {
        Thread.sleep(30 * 1000);
      } catch (InterruptedException e) {
        //ignore
      }

      for (var namespace : namespaces) {
        logger.info("Check for artifacts in namespace {0}", namespace);
        testUntil(
            nothingFoundInNamespace(namespace),
            logger,
            "artifacts to be deleted in namespace {0}",
            namespace);

        logger.info("Check for namespace {0} existence", namespace);
        testUntil(
            namespaceNotFound(namespace),
            logger,
            "namespace to be deleted {0}",
            namespace);
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Cleanup failed");
    }
  }

  /**
   * Delete all clusters in the given namespace, if any exists.
   *
   * @param namespace namespace
   */
  private static void deleteClusters(String namespace) {
    LoggingFacade logger = getLogger();
    try {
      for (var item : Cluster.listClusterCustomResources(namespace).getItems()) {
        String cluster = item.getMetadata().getName();
        logger.info("Deleting cluster {0} in namespace {1}", cluster, namespace);
        Kubernetes.deleteClusterCustomResource(cluster, namespace);
      }
    } catch (Exception ex) {
      logger.severe(ex.getMessage());
      logger.severe("Failed to delete cluster in namespace {0}", namespace);
    }
  }
  
  /**
   * Delete all domains in the given namespace, if any exists.
   *
   * @param namespace name of the namespace
   */
  private static void deleteDomains(String namespace) {
    LoggingFacade logger = getLogger();
    DomainList domainList = listDomains(namespace);
    if (domainList != null) {
      List<DomainResource> items = domainList.getItems();
      try {
        for (var item : items) {
          if (item.getMetadata() != null) {
            String domainUid = item.getMetadata().getName();
            deleteDomainCustomResource(domainUid, namespace);
          }
        }
      } catch (Exception ex) {
        logger.severe(ex.getMessage());
        logger.severe("Failed to delete domain or the {0} is not a domain namespace", namespace);
      }
    }
  }

  /**
   * Uninstall the WebLogic operator.
   *
   * @param namespace name of the namespace
   */
  private static void uninstallWebLogicOperator(String namespace) {
    HelmParams opHelmParams = new HelmParams()
        .releaseName(TestConstants.OPERATOR_RELEASE_NAME)
        .namespace(namespace);
    TestActions.uninstallOperator(opHelmParams);
  }

  /**
   * Returns true if no artifacts exists in the given namespace.
   *
   * @param namespace name of the namespace
   * @return true if no artifacts exists, otherwise false
   */
  public static Callable<Boolean> nothingFoundInNamespace(String namespace) {
    LoggingFacade logger = getLogger();
    return () -> {
      boolean nothingFound = true;
      logger.info("Checking for "
          + "domains, "
          + "replica sets, "
          + "jobs, "
          + "config maps, "
          + "secrets, "
          + "persistent volume claims, "
          + "persistent volumes, "
          + "deployments, "
          + "services, "
          + "service accounts, "
          + "ingresses "
          + "namespaced roles"
          + "namespaced rolebindings in namespace {0}\n", namespace);

      // Check if any domains exist
      try {
        DomainList domainList = listDomains(namespace);
        if (domainList != null) {
          List<DomainResource> items = domainList.getItems();
          if (!items.isEmpty()) {
            logger.info("Domain still exists !!!");
            for (var item : items) {
              if (item.getMetadata() != null) {
                logger.info(item.getMetadata().getName());
              }
            }
            nothingFound = false;
          }
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list domains");
      }

      // Check if any replica sets exist
      try {
        V1ReplicaSetList replicaSetList = listReplicaSets(namespace);
        if (replicaSetList != null) {
          List<V1ReplicaSet> items = replicaSetList.getItems();
          if (!items.isEmpty()) {
            logger.info("ReplicaSets still exists!!!");
            for (var item : items) {
              if (item.getMetadata() != null) {
                logger.info(item.getMetadata().getName());
              }
            }
            nothingFound = false;
          }
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list replica sets");
      }

      // check if any jobs exist
      try {
        V1JobList jobList = listJobs(namespace);
        if (jobList != null) {
          List<V1Job> items = jobList.getItems();
          if (items != null && !items.isEmpty()) {
            logger.info("Jobs still exists!!!");
            for (var item : items) {
              if (item.getMetadata() != null) {
                logger.info(item.getMetadata().getName());
              }
            }
            nothingFound = false;
          }
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list jobs");
      }

      // check if any configmaps exist
      try {
        V1ConfigMapList configMapList = listConfigMaps(namespace);
        if (configMapList != null) {
          List<V1ConfigMap> items = configMapList.getItems();
          if (!items.isEmpty()) {
            logger.info("Config Maps still exists!!!");
            for (var item : items) {
              if (item.getMetadata() != null) {
                logger.info(item.getMetadata().getName());
              }
            }
            nothingFound = false;
          }
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list config maps");
      }

      // check if any secrets exist
      try {
        V1SecretList secretList = listSecrets(namespace);
        if (secretList != null) {
          List<V1Secret> items = secretList.getItems();
          if (!items.isEmpty()) {
            for (var item : items) {
              if (item.getMetadata() != null) {
                logger.info(item.getMetadata().getName());
              }
            }
            nothingFound = false;
          }
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list secrets");
      }

      // check if any persistent volume claims exist
      try {
        V1PersistentVolumeClaimList pvcList = listPersistentVolumeClaims(namespace);
        if (pvcList != null) {
          List<V1PersistentVolumeClaim> items = pvcList.getItems();
          if (!items.isEmpty()) {
            logger.info("Persistent Volumes Claims still exists!!!");
            for (var item : items) {
              if (item.getMetadata() != null) {
                logger.info(item.getMetadata().getName());
              }
            }
            nothingFound = false;
          }
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list persistent volume claims");
      }

      // check if any persistent volumes exist
      try {
        V1PersistentVolumeClaimList persistentVolumeClaimList = listPersistentVolumeClaims(namespace);
        if (persistentVolumeClaimList != null) {
          List<V1PersistentVolumeClaim> items = persistentVolumeClaimList.getItems();
          for (var item : items) {
            String label = Optional.ofNullable(item)
                .map(pvc -> pvc.getMetadata())
                .map(metadata -> metadata.getLabels())
                .map(labels -> labels.get("weblogic.domainUid")).get();

            if (!listPersistentVolumes(
                String.format("weblogic.domainUid = %s", label))
                .getItems().isEmpty()) {
              logger.info("Persistent Volumes still exists!!!");
              List<V1PersistentVolume> pvs = listPersistentVolumes(
                  String.format("weblogic.domainUid = %s", label))
                  .getItems();
              for (var pv : pvs) {
                if (pv.getMetadata() != null) {
                  logger.info(pv.getMetadata().getName());
                }
              }
              nothingFound = false;
            }
          }
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list persistent volumes");
      }

      // check if any deployments exist
      try {
        V1DeploymentList deploymentList = listDeployments(namespace);
        if (deploymentList != null) {
          List<V1Deployment> items = deploymentList.getItems();
          if (!items.isEmpty()) {
            logger.info("Deployments still exists!!!");
            for (var item : items) {
              if (item.getMetadata() != null) {
                logger.info(item.getMetadata().getName());
              }
            }
            nothingFound = false;
          }
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list deployments");
      }

      // check if any services exist
      try {
        V1ServiceList serviceList = listServices(namespace);
        if (serviceList != null) {
          List<V1Service> items = serviceList.getItems();
          if (!items.isEmpty()) {
            logger.info("Services still exists!!!");
            for (var item : items) {
              if (item.getMetadata() != null) {
                logger.info(item.getMetadata().getName());
              }
            }
            nothingFound = false;
          }
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list services");
      }

      // check if any service accounts exist
      try {
        V1ServiceAccountList serviceAccountList = listServiceAccounts(namespace);
        if (serviceAccountList != null) {
          List<V1ServiceAccount> items = serviceAccountList.getItems();
          if (!items.isEmpty()) {
            logger.info("Service Accounts still exists!!!");
            for (var item : items) {
              if (item.getMetadata() != null) {
                logger.info(item.getMetadata().getName());
              }
            }
            nothingFound = false;
          }
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list service accounts");
      }

      // check if any ingress exist
      try {
        V1IngressList ingressList = listNamespacedIngresses(namespace);
        if (ingressList != null) {
          List<V1Ingress> items = listNamespacedIngresses(namespace).getItems();
          if (!items.isEmpty()) {
            for (var item : items) {
              if (item.getMetadata() != null) {
                logger.info(item.getMetadata().getName());
              }
            }
            nothingFound = false;
          }
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list Ingresses");
      }

      // check if any namespaced roles exist
      try {
        V1RoleList roleList = listNamespacedRoles(namespace);
        if (roleList != null) {
          List<V1Role> items = roleList.getItems();
          if (!items.isEmpty()) {
            logger.info("Namespaced roles still exists!!!");
            for (var item : items) {
              if (item.getMetadata() != null) {
                logger.info(item.getMetadata().getName());
              }
            }
            nothingFound = false;
          }
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list namespaced roles");
      }

      // check if any namespaced role bindings exist
      try {
        V1RoleBindingList roleBindingList = listNamespacedRoleBinding(namespace);
        if (roleBindingList != null) {
          List<V1RoleBinding> items = roleBindingList.getItems();
          if (!items.isEmpty()) {
            logger.info("Namespaced role bindings still exists!!!");
            for (var item : items) {
              if (item.getMetadata() != null) {
                logger.info(item.getMetadata().getName());
              }
            }
            nothingFound = false;
          }
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list namespaced role bindings");
      }

      return nothingFound;
    };

  }

  /**
   * Return true if the namespace was not found.
   *
   * @param namespace name of the namespace
   * @return true if namespace was not found, otherwise false
   */
  public static Callable<Boolean> namespaceNotFound(String namespace) {
    LoggingFacade logger = getLogger();
    return () -> {
      boolean notFound = true;
      // get namespaces
      try {
        List<String> namespaceList = listNamespaces();
        if (namespaceList.contains(namespace)) {
          logger.info("Namespace still exists!!!");
          logger.info(namespace);
          notFound = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list namespaces");
      }
      return notFound;
    };
  }

  /**
   * Deletes artifacts in the Kubernetes cluster in the given namespace.
   *
   * @param namespace name of the namespace
   */
  public static void deleteNamespacedArtifacts(String namespace) {
    LoggingFacade logger = getLogger();
    logger.info("Deleting artifacts in namespace {0}", namespace);

    // Delete all Domain objects in the given namespace
    try {
      DomainList domainList = listDomains(namespace);
      if (domainList != null) {
        List<DomainResource> items = domainList.getItems();
        for (var item : items) {
          if (item.getMetadata() != null) {
            deleteDomainCustomResource(item.getMetadata().getName(), namespace);
          }
        }
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete domains");
    }

    // Delete deployments
    try {
      V1DeploymentList deploymentList = listDeployments(namespace);
      if (deploymentList != null) {
        List<V1Deployment> items = deploymentList.getItems();
        for (var item : items) {
          if (item.getMetadata() != null) {
            deleteDeployment(namespace, item.getMetadata().getName());
          }
        }
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete deployments");
    }

    // Delete replicasets
    try {
      V1ReplicaSetList replicaSetList = listReplicaSets(namespace);
      if (replicaSetList != null) {
        List<V1ReplicaSet> items = replicaSetList.getItems();
        for (var item : items) {
          if (item.getMetadata() != null) {
            deleteReplicaSet(namespace, item.getMetadata().getName());
          }
        }
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete replica sets");
    }

    // Delete jobs
    try {
      V1JobList jobList = listJobs(namespace);
      if (jobList != null) {
        for (var item : jobList.getItems()) {
          if (item.getMetadata() != null) {
            deleteJob(namespace, item.getMetadata().getName());
          }
        }
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete jobs");
    }

    // Delete configmaps
    try {
      V1ConfigMapList configMapList = listConfigMaps(namespace);
      if (configMapList != null) {
        List<V1ConfigMap> items = configMapList.getItems();
        for (var item : items) {
          if (item.getMetadata() != null) {
            deleteConfigMap(item.getMetadata().getName(), namespace);
          }
        }

      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete config maps");
    }

    // Delete secrets
    try {
      V1SecretList secretList = listSecrets(namespace);
      if (secretList != null) {
        List<V1Secret> items = secretList.getItems();
        for (var item : items) {
          if (item.getMetadata() != null) {
            deleteSecret(item.getMetadata().getName(), namespace);
          }
        }
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete secrets");
    }

    // Delete pvc
    List<V1PersistentVolume> pvs = new ArrayList<>();

    V1PersistentVolumeClaimList persistentVolumeClaimList = listPersistentVolumeClaims(namespace);
    if (persistentVolumeClaimList != null) {
      List<V1PersistentVolumeClaim> pvcItems = persistentVolumeClaimList.getItems();
      for (var pvc : pvcItems) {
        String label = null;
        if (pvc.getMetadata() != null && pvc.getMetadata().getLabels() != null) {
          label = pvc.getMetadata().getLabels().get("weblogic.domainUid");
        }
        // get a list of pvs used by the pvcs in this namespace
        try {
          if (null != label) {
            List<V1PersistentVolume> items = listPersistentVolumes(
                String.format("weblogic.domainUid = %s", label)).getItems();
            pvs.addAll(items);
          }
          // delete the pvc
          if (pvc.getMetadata() != null) {
            deletePvc(pvc.getMetadata().getName(), namespace);
          }
        } catch (ApiException ex) {
          logger.warning(ex.getResponseBody());
        }
      }
    }

    // Delete pv
    if (!pvs.isEmpty()) {
      for (var item : pvs) {
        if (item.getMetadata() != null) {
          try {
            logger.info("Deleting persistent volume {0}", item.getMetadata().getName());
            deletePv(item.getMetadata().getName());
          } catch (Exception ex) {
            logger.warning("Failed to delete persistent volumes");
          }
        }
      }
    }

    // Delete services
    try {
      V1ServiceList serviceList = listServices(namespace);
      if (serviceList != null) {
        List<V1Service> items = serviceList.getItems();
        for (var item : items) {
          if (item.getMetadata() != null) {
            deleteService(item.getMetadata().getName(), namespace);
          }
        }
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete services");
    }

    // Delete namespaced roles
    try {
      V1RoleList roleList = listNamespacedRoles(namespace);
      if (roleList != null) {
        for (var item : roleList.getItems()) {
          if (item.getMetadata() != null) {
            deleteNamespacedRole(namespace, item.getMetadata().getName());
          }
        }
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete namespaced roles");
    }

    // Delete namespaced role bindings
    try {
      V1RoleBindingList roleBindingList = listNamespacedRoleBinding(namespace);
      if (roleBindingList != null) {
        for (var item : roleBindingList.getItems()) {
          if (item.getMetadata() != null) {
            deleteNamespacedRoleBinding(namespace, item.getMetadata().getName());
          }
        }
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete namespaced rolebindings");
    }

    // Delete service accounts
    try {
      V1ServiceAccountList serviceAccountList = listServiceAccounts(namespace);
      if (serviceAccountList != null) {
        List<V1ServiceAccount> items = serviceAccountList.getItems();
        for (var item : items) {
          if (item.getMetadata() != null) {
            deleteServiceAccount(item.getMetadata().getName(), namespace);
          }
        }
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete service accounts");
    }

  }

  /**
   * Delete a namespace.
   *
   * @param namespace name of the namespace
   */
  public static void deleteNamespace(String namespace) {
    LoggingFacade logger = getLogger();
    try {
      Kubernetes.deleteNamespace(namespace);
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete namespace {0}", namespace);
    }
  }

}
