// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.NetworkingV1beta1Ingress;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1Role;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.TestActions;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;

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

      // wait for the artifacts to be deleted, waiting for a maximum of 10 minutes
      ConditionFactory withStandardRetryPolicy = with().pollDelay(0, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(10, MINUTES).await();

      for (var namespace : namespaces) {
        logger.info("Check for artifacts in namespace {0}", namespace);
        withStandardRetryPolicy
            .conditionEvaluationListener(
                condition -> logger.info("Waiting for artifacts to be deleted in namespace {0}, "
                        + "(elapsed time {1} , remaining time {2}",
                    namespace,
                    condition.getElapsedTimeInMS(),
                    condition.getRemainingTimeInMS()))
            .until(nothingFoundInNamespace(namespace));

        logger.info("Check for namespace {0} existence", namespace);
        withStandardRetryPolicy
            .conditionEvaluationListener(
                condition -> logger.info("Waiting for namespace to be deleted {0}, "
                        + "(elapsed time {1} , remaining time {2}",
                    namespace,
                    condition.getElapsedTimeInMS(),
                    condition.getRemainingTimeInMS()))
            .until(namespaceNotFound(namespace));
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Cleanup failed");
    }
  }

  /**
   * Delete all domains in the given namespace, if any exists.
   *
   * @param namespace name of the namespace
   */
  private static void deleteDomains(String namespace) {
    LoggingFacade logger = getLogger();
    try {
      for (var item : Kubernetes.listDomains(namespace).getItems()) {
        String domainUid = item.getMetadata().getName();
        Kubernetes.deleteDomainCustomResource(domainUid, namespace);
      }
    } catch (Exception ex) {
      logger.severe(ex.getMessage());
      logger.severe("Failed to delete domain or the {0} is not a domain namespace", namespace);
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
        if (!Kubernetes.listDomains(namespace).getItems().isEmpty()) {
          logger.info("Domain still exists !!!");
          List<Domain> items = Kubernetes.listDomains(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          nothingFound = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list domains");
      }

      // Check if any replica sets exist
      try {
        if (!Kubernetes.listReplicaSets(namespace).getItems().isEmpty()) {
          logger.info("ReplicaSets still exists!!!");
          List<V1ReplicaSet> items = Kubernetes.listReplicaSets(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          nothingFound = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list replica sets");
      }

      // check if any jobs exist
      try {
        if (!Kubernetes.listJobs(namespace).getItems().isEmpty()) {
          logger.info("Jobs still exists!!!");
          List<V1Job> items = Kubernetes.listJobs(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          nothingFound = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list jobs");
      }

      // check if any configmaps exist
      try {
        if (!Kubernetes.listConfigMaps(namespace).getItems().isEmpty()) {
          logger.info("Config Maps still exists!!!");
          List<V1ConfigMap> items = Kubernetes.listConfigMaps(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          nothingFound = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list config maps");
      }

      // check if any secrets exist
      try {
        if (!Kubernetes.listSecrets(namespace).getItems().isEmpty()) {
          logger.info("Secrets still exists!!!");
          List<V1Secret> items = Kubernetes.listSecrets(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          nothingFound = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list secrets");
      }

      // check if any persistent volume claims exist
      try {
        if (!Kubernetes.listPersistentVolumeClaims(namespace).getItems().isEmpty()) {
          logger.info("Persistent Volumes Claims still exists!!!");
          List<V1PersistentVolumeClaim> items = Kubernetes.listPersistentVolumeClaims(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          nothingFound = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list persistent volume claims");
      }

      // check if any persistent volumes exist
      try {
        for (var item : Kubernetes.listPersistentVolumeClaims(namespace).getItems()) {
          String label = Optional.ofNullable(item)
              .map(pvc -> pvc.getMetadata())
              .map(metadata -> metadata.getLabels())
              .map(labels -> labels.get("weblogic.domainUid")).get();

          if (!Kubernetes.listPersistentVolumes(
              String.format("weblogic.domainUid = %s", label))
              .getItems().isEmpty()) {
            logger.info("Persistent Volumes still exists!!!");
            List<V1PersistentVolume> pvs = Kubernetes.listPersistentVolumes(
                String.format("weblogic.domainUid = %s", label))
                .getItems();
            for (var pv : pvs) {
              logger.info(pv.getMetadata().getName());
            }
            nothingFound = false;
          }
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list persistent volumes");
      }

      // check if any deployments exist
      try {
        if (!Kubernetes.listDeployments(namespace).getItems().isEmpty()) {
          logger.info("Deployments still exists!!!");
          List<V1Deployment> items = Kubernetes.listDeployments(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          nothingFound = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list deployments");
      }

      // check if any services exist
      try {
        if (!Kubernetes.listServices(namespace).getItems().isEmpty()) {
          logger.info("Services still exists!!!");
          List<V1Service> items = Kubernetes.listServices(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          nothingFound = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list services");
      }

      // check if any service accounts exist
      try {
        if (!Kubernetes.listServiceAccounts(namespace).getItems().isEmpty()) {
          logger.info("Service Accounts still exists!!!");
          List<V1ServiceAccount> items = Kubernetes.listServiceAccounts(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          nothingFound = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list service accounts");
      }

      // check if any ingress exist
      try {
        if (!Kubernetes.listNamespacedIngresses(namespace).getItems().isEmpty()) {
          logger.info("Ingresses still exists!!!");
          List<NetworkingV1beta1Ingress> items = Kubernetes.listNamespacedIngresses(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          nothingFound = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list Ingresses");
      }

      // check if any namespaced roles exist
      try {
        if (!Kubernetes.listNamespacedRoles(namespace).getItems().isEmpty()) {
          logger.info("Namespaced roles still exists!!!");
          List<V1Role> items = Kubernetes.listNamespacedRoles(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          nothingFound = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list namespaced roles");
      }

      // check if any namespaced role bindings exist
      try {
        if (!Kubernetes.listNamespacedRoleBinding(namespace).getItems().isEmpty()) {
          logger.info("Namespaced role bindings still exists!!!");
          List<V1RoleBinding> items = Kubernetes.listNamespacedRoleBinding(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          nothingFound = false;
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
        List<String> namespaceList = Kubernetes.listNamespaces();
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
      for (var item : Kubernetes.listDomains(namespace).getItems()) {
        Kubernetes.deleteDomainCustomResource(item.getMetadata().getName(), namespace);
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete domains");
    }

    // Delete deployments
    try {
      for (var item : Kubernetes.listDeployments(namespace).getItems()) {
        Kubernetes.deleteDeployment(namespace, item.getMetadata().getName());
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete deployments");
    }

    // Delete replicasets
    try {
      for (var item : Kubernetes.listReplicaSets(namespace).getItems()) {
        Kubernetes.deleteReplicaSet(namespace, item.getMetadata().getName());
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete replica sets");
    }

    // Delete jobs
    try {
      for (var item : Kubernetes.listJobs(namespace).getItems()) {
        Kubernetes.deleteJob(namespace, item.getMetadata().getName());
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete jobs");
    }

    // Delete configmaps
    try {
      for (var item : Kubernetes.listConfigMaps(namespace).getItems()) {
        Kubernetes.deleteConfigMap(item.getMetadata().getName(), namespace);
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete config maps");
    }

    // Delete secrets
    try {
      for (var item : Kubernetes.listSecrets(namespace).getItems()) {
        Kubernetes.deleteSecret(item.getMetadata().getName(), namespace);
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete secrets");
    }

    // Delete pvc
    List<V1PersistentVolume> pvs = new ArrayList<V1PersistentVolume>();

    for (var pvc : Kubernetes.listPersistentVolumeClaims(namespace).getItems()) {
      String label = null;
      if (pvc.getMetadata().getLabels() != null) {
        label = pvc.getMetadata().getLabels().get("weblogic.domainUid");
      }
      // get a list of pvs used by the pvcs in this namespace
      try {
        if (null != label) {
          List<V1PersistentVolume> items = Kubernetes.listPersistentVolumes(
              String.format("weblogic.domainUid = %s", label)).getItems();
          pvs.addAll(items);
        }
        // delete the pvc
        Kubernetes.deletePvc(pvc.getMetadata().getName(), namespace);
      } catch (ApiException ex) {
        logger.warning(ex.getResponseBody());
      }
    }

    // Delete pv
    if (!pvs.isEmpty()) {
      for (var item : pvs) {
        try {
          logger.info("Deleting persistent volume {0}", item.getMetadata().getName());
          Kubernetes.deletePv(item.getMetadata().getName());
        } catch (Exception ex) {
          logger.warning("Failed to delete persistent volumes");
        }
      }
    }

    // Delete services
    try {
      for (var item : Kubernetes.listServices(namespace).getItems()) {
        Kubernetes.deleteService(item.getMetadata().getName(), namespace);
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete services");
    }

    // Delete namespaced roles
    try {
      for (var item : Kubernetes.listNamespacedRoles(namespace).getItems()) {
        Kubernetes.deleteNamespacedRole(namespace, item.getMetadata().getName());
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete namespaced roles");
    }

    // Delete namespaced role bindings
    try {
      for (var item : Kubernetes.listNamespacedRoleBinding(namespace).getItems()) {
        Kubernetes.deleteNamespacedRoleBinding(namespace, item.getMetadata().getName());
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete namespaced rolebindings");
    }

    // Delete service accounts
    try {
      for (var item : Kubernetes.listServiceAccounts(namespace).getItems()) {
        Kubernetes.deleteServiceAccount(item.getMetadata().getName(), namespace);
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
      logger.warning("Failed to delete namespace");
    }
  }

}
