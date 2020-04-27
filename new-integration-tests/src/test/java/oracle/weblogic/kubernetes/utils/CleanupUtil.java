// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.ExtensionsV1beta1Ingress;
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
import org.awaitility.core.ConditionFactory;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static org.awaitility.Awaitility.with;

/**
 * CleanupUtil is used for cleaning up all the Kubernetes artifacts left behind by the integration tests.
 *
 */
public class CleanupUtil {

  /**
   * Cleanup all artifacts in the Kubernetes cluster.
   *
   * <p>Tries to gracefully delete the WebLogic domains and WebLogic Operator in the namespaces, if found.
   * Then deletes everything in the namespaces.
   *
   * <p>Waits for the deletion task to be completed until up to 3 minutes.
   *
   * @param namespaces list of namespaces
   */
  public static void cleanup(List<String> namespaces) {
    try {
      // If namespace list is empty or null return
      if (namespaces == null || namespaces.isEmpty()) {
        return;
      }
      // iterate through the namespaces and delete domain as a
      // first entity if its not operator namespace
      for (var namespace : namespaces) {
        if (!isOperatorNamespace(namespace)) {
          deleteDomains(namespace);
        }
      }
      // iterate through the namespaces and delete operator if
      // its operator namespace.
      for (var namespace : namespaces) {
        if (isOperatorNamespace(namespace)) {
          uninstallOperator(namespace);
        }
      }

      // Delete artifacts in namespace used by the test class
      for (var namespace : namespaces) {
        deleteNamespacedArtifacts(namespace);
      }

      Thread.sleep(30 * 1000); // I am using Thread.sleep for a one time 30 sec sleep.
      // If put 30 seconds in pollDelay in below, its going to sleep 30 seconds for every namespace.

      // wait for the artifacts to be deleted, waiting for a maximum of 3 minutes
      // with pollDelay 0, since already waited for 30 seconds
      ConditionFactory withStandardRetryPolicy = with().pollDelay(0, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(3, MINUTES).await();

      for (var namespace : namespaces) {
        logger.info("Check for artifacts in namespace {0}", namespace);
        withStandardRetryPolicy
            .conditionEvaluationListener(
                condition -> logger.info("Waiting for artifacts to be deleted in namespace {0}, "
                    + "(elapsed time {1} , remaining time {2}",
                    namespace,
                    condition.getElapsedTimeInMS(),
                    condition.getRemainingTimeInMS()))
            .until(artifactsDoesntExist(namespace));
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Cleanup failed");
    }
  }

  /**
   * Delete domains in the given namespace, if exists.
   *
   * @param namespace name of the namespace
   */
  private static void deleteDomains(String namespace) {
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
   * Uninstalls the operator.
   *
   * @param namespace name of the namespace
   */
  private static void uninstallOperator(String namespace) {
    HelmParams opHelmParams = new HelmParams()
        .releaseName(TestConstants.OPERATOR_RELEASE_NAME)
        .namespace(namespace);
    TestActions.uninstallOperator(opHelmParams);
  }

  /**
   * Returns true if the namespace is operator namespace, otherwise false.
   *
   * @param namespace name of the namespace
   * @return true if the namespace is operator namespace, otherwise false
   * @throws ApiException when Kubernetes cluster query fails
   */
  public static boolean isOperatorNamespace(String namespace) throws ApiException {
    return !Kubernetes.listPods(namespace, "weblogic.operatorName").getItems().isEmpty();
  }

  /**
   * Returns true if artifacts doesn't exists in the Kubernetes cluster.
   *
   * @param namespace name of the namespace
   * @return true if no artifacts exists otherwise false
   */
  public static Callable<Boolean> artifactsDoesntExist(String namespace) {
    return () -> {
      boolean doesnotExist = true;
      logger.info("Checking for artifacts in namespace {0}\n", namespace);

      // Check if domain exists
      try {
        if (!Kubernetes.listDomains(namespace).getItems().isEmpty()) {
          logger.info("Domain still exists !!!");
          List<Domain> items = Kubernetes.listDomains(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          doesnotExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list domains");
      }

      // Check if the replica sets exist
      try {
        if (!Kubernetes.listReplicaSets(namespace).getItems().isEmpty()) {
          logger.info("ReplicaSets still exists!!!");
          List<V1ReplicaSet> items = Kubernetes.listReplicaSets(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          doesnotExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list replica sets");
      }

      // check if the jobs exist
      try {
        if (!Kubernetes.listJobs(namespace).getItems().isEmpty()) {
          logger.info("Jobs still exists!!!");
          List<V1Job> items = Kubernetes.listJobs(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          doesnotExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list jobs");
      }

      // check if the configmaps exist
      try {
        if (!Kubernetes.listConfigMaps(namespace).getItems().isEmpty()) {
          logger.info("Config Maps still exists!!!");
          List<V1ConfigMap> items = Kubernetes.listConfigMaps(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          doesnotExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list config maps");
      }

      // check if the secrets exist
      try {
        if (!Kubernetes.listSecrets(namespace).getItems().isEmpty()) {
          logger.info("Secrets still exists!!!");
          List<V1Secret> items = Kubernetes.listSecrets(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          doesnotExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list secrets");
      }

      // get pvc
      try {
        if (!Kubernetes.listPersistentVolumeClaims(namespace).getItems().isEmpty()) {
          logger.info("Persistent Volumes Claims still exists!!!");
          List<V1PersistentVolumeClaim> items = Kubernetes.listPersistentVolumeClaims(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          doesnotExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list persistent volume claims");
      }

      // check if persistent volume exist
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
            doesnotExist = false;
          }
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list persistent volumes");
      }

      // check if deployments exist
      try {
        if (!Kubernetes.listDeployments(namespace).getItems().isEmpty()) {
          logger.info("Deployments still exists!!!");
          List<V1Deployment> items = Kubernetes.listDeployments(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          doesnotExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list deployments");
      }

      // check if services exist
      try {
        if (!Kubernetes.listServices(namespace).getItems().isEmpty()) {
          logger.info("Services still exists!!!");
          List<V1Service> items = Kubernetes.listServices(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          doesnotExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list services");
      }

      // check if service accounts exist
      try {
        if (!Kubernetes.listServiceAccounts(namespace).getItems().isEmpty()) {
          logger.info("Service Accounts still exists!!!");
          List<V1ServiceAccount> items = Kubernetes.listServiceAccounts(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          doesnotExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list service accounts");
      }

      // check if ingress exist
      try {
        if (!Kubernetes.listIngressExtensions(namespace).getItems().isEmpty()) {
          logger.info("Ingress Extensions still exists!!!");
          List<ExtensionsV1beta1Ingress> items = Kubernetes.listIngressExtensions(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          doesnotExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list Ingress Extensions");
      }

      // check if namespaced roles exist
      try {
        if (!Kubernetes.listNamespacedRole(namespace).getItems().isEmpty()) {
          logger.info("Namespaced roles still exists!!!");
          List<V1Role> items = Kubernetes.listNamespacedRole(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          doesnotExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list namespaced roles");
      }

      // check if namespaced role bindings exist
      try {
        if (!Kubernetes.listNamespacedRoleBinding(namespace).getItems().isEmpty()) {
          logger.info("Namespaced role bindings still exists!!!");
          List<V1RoleBinding> items = Kubernetes.listNamespacedRoleBinding(namespace).getItems();
          for (var item : items) {
            logger.info(item.getMetadata().getName());
          }
          doesnotExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list namespaced role bindings");
      }

      // get namespaces
      try {
        if (Kubernetes.listNamespaces().contains(namespace)) {
          logger.info("Namespace still exists!!!");
          List<String> items = Kubernetes.listNamespaces();
          for (var item : items) {
            logger.info(item);
          }
          doesnotExist = false;
        }
      } catch (Exception ex) {
        logger.warning(ex.getMessage());
        logger.warning("Failed to list namespaces");
      }

      return doesnotExist;
    };

  }

  /**
   * Deletes artifacts in the Kubernetes cluster in the given namespace.
   *
   * @param namespace name of the namespace
   */
  public static void deleteNamespacedArtifacts(String namespace) {
    logger.info("Deleting artifacts in namespace {0}", namespace);

    // Delete all Domain objects in given namespace
    try {
      for (var item : Kubernetes.listDomains(namespace).getItems()) {
        Kubernetes.deleteDomainCustomResource(item.getMetadata().getName(), namespace);
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete domains");
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

    // Delete pv
    try {
      for (var item : Kubernetes.listPersistentVolumeClaims(namespace).getItems()) {
        String label = Optional.ofNullable(item)
            .map(pvc -> pvc.getMetadata())
            .map(metadata -> metadata.getLabels())
            .map(labels -> labels.get("weblogic.domainUid")).get();
        for (var pv : Kubernetes.listPersistentVolumes(
            String.format("weblogic.domainUid = %s", label)).getItems()) {
          Kubernetes.deletePv(pv.getMetadata().getName());
        }
      }
    } catch (ApiException ex) {
      logger.warning(ex.getResponseBody());
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete persistent volumes");
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

    // Delete pvc
    try {
      for (var item : Kubernetes.listPersistentVolumeClaims(namespace).getItems()) {
        Kubernetes.deletePvc(item.getMetadata().getName(), namespace);
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete persistent volume claims");
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
      for (var item : Kubernetes.listNamespacedRole(namespace).getItems()) {
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
    // Delete namespace
    try {
      Kubernetes.deleteNamespace(namespace);
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete namespace");
    }
  }

}
