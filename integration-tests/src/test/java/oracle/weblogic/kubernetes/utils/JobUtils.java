// Copyright (c) 2021, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.actions.TestActions.createNamespacedJob;
import static oracle.weblogic.kubernetes.actions.TestActions.getJob;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.listPods;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.jobCompleted;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createfixPVCOwnerContainer;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

public class JobUtils {

  public static String getIntrospectJobName(String domainUid) {
    return domainUid + TestConstants.DEFAULT_INTROSPECTOR_JOB_NAME_SUFFIX;
  }

  /**
   * Create a job in the specified namespace and wait until it completes.
   *
   * @param jobBody V1Job object to create in the specified namespace
   * @param namespace the namespace in which the job will be created
   */
  public static String createJobAndWaitUntilComplete(V1Job jobBody, String namespace) {
    LoggingFacade logger = getLogger();
    String jobName = assertDoesNotThrow(() -> createNamespacedJob(jobBody), "createNamespacedJob failed");

    logger.info("Checking if the job {0} completed in namespace {1}", jobName, namespace);
    testUntil(
        jobCompleted(jobName, null, namespace),
        logger,
        "job {0} to be completed in namespace {1}",
        jobName,
        namespace);

    return jobName;
  }

  /**
   * Create a job to create a domain in persistent volume.
   *
   * @param image image name used to create the domain
   * @param pvName name of the persistent volume to create domain in
   * @param pvcName name of the persistent volume claim
   * @param domainScriptCM configmap holding domain creation script files
   * @param namespace name of the domain namespace in which the job is created
   * @param jobContainer V1Container with job commands to create domain
   */
  public static void createDomainJob(String image, String pvName,
                                     String pvcName, String domainScriptCM, String namespace,
                                     V1Container jobContainer) {
    createDomainJob(image, pvName, pvcName, domainScriptCM,
        namespace, jobContainer, null);
  }

  /**
   * Create a job to create a domain in persistent volume.
   *
   * @param image             image name used to create the domain
   * @param pvName            name of the persistent volume to create domain in
   * @param pvcName           name of the persistent volume claim
   * @param domainScriptCM    configmap holding domain creation script files
   * @param namespace         name of the domain namespace in which the job is created
   * @param jobContainer      V1Container with job commands to create domain
   * @param podAnnotationsMap annotations for the job pod
   */
  public static void createDomainJob(String image, String pvName, String pvcName, String domainScriptCM,
                                     String namespace, V1Container jobContainer,
                                     Map<String, String> podAnnotationsMap) {

    LoggingFacade logger = getLogger();
    logger.info("Running Kubernetes job to create domain for image: {0}"
            + " pvName: {1}, pvcName: {2}, domainScriptCM: {3}, namespace: {4}", image,
        pvName, pvcName, domainScriptCM, namespace);

    V1PodSpec podSpec = new V1PodSpec()
        .restartPolicy("Never")
        .containers(Arrays.asList(jobContainer  // container containing WLST or WDT details
            .name("create-weblogic-domain-onpv-container")
            .image(image)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .ports(Arrays.asList(new V1ContainerPort()
                .containerPort(7001)))
            .volumeMounts(Arrays.asList(
                new V1VolumeMount()
                    .name("create-weblogic-domain-job-cm-volume") // domain creation scripts volume
                    .mountPath("/u01/weblogic"), // available under /u01/weblogic inside pod
                new V1VolumeMount()
                    .name(pvName) // location to write domain
                    .mountPath("/shared"))))) // mounted under /shared inside pod
        .volumes(Arrays.asList(
            new V1Volume()
                .name(pvName)
                .persistentVolumeClaim(
                    new V1PersistentVolumeClaimVolumeSource()
                        .claimName(pvcName)),
            new V1Volume()
                .name("create-weblogic-domain-job-cm-volume")
                .configMap(
                    new V1ConfigMapVolumeSource()
                        .name(domainScriptCM)))) //config map containing domain scripts
        .imagePullSecrets(Arrays.asList(
            new V1LocalObjectReference()
                .name(BASE_IMAGES_REPO_SECRET_NAME)));
    if (!OKD) {
      podSpec.initContainers(Arrays.asList(createfixPVCOwnerContainer(pvName, "/shared")));
    }

    V1PodTemplateSpec podTemplateSpec = new V1PodTemplateSpec();
    if (podAnnotationsMap != null) {
      podTemplateSpec.metadata(new V1ObjectMeta()
          .annotations(podAnnotationsMap));
    }
    podTemplateSpec.spec(podSpec);

    V1Job jobBody = new V1Job()
        .metadata(
            new V1ObjectMeta()
                .name("create-domain-onpv-job-" + pvName) // name of the create domain job
                .namespace(namespace))
        .spec(new V1JobSpec()
            .backoffLimit(0) // try only once
            .template(podTemplateSpec));
    String jobName = assertDoesNotThrow(()
        -> createNamespacedJob(jobBody), "Failed to create Job");

    logger.info("Checking if the domain creation job {0} completed in namespace {1}",
        jobName, namespace);
    testUntil(
        withLongRetryPolicy,
        jobCompleted(jobName, null, namespace),
        logger,
        "job {0} to be completed in namespace {1}",
        jobName,
        namespace);

    // check job status and fail test if the job failed to create domain
    V1Job job = assertDoesNotThrow(() -> getJob(jobName, namespace),
        "Getting the job failed");
    if (job != null && job.getStatus() != null && job.getStatus().getConditions() != null) {
      V1JobCondition jobCondition = job.getStatus().getConditions().stream().filter(
          v1JobCondition -> "Failed".equals(v1JobCondition.getType()))
          .findAny()
          .orElse(null);
      if (jobCondition != null) {
        logger.severe("Job {0} failed to create domain", jobName);
        List<V1Pod> pods = assertDoesNotThrow(() -> listPods(
            namespace, "job-name=" + jobName).getItems(),
            "Listing pods failed");
        if (!pods.isEmpty() && pods.get(0).getMetadata() != null) {
          String podLog = assertDoesNotThrow(() -> getPodLog(pods.get(0).getMetadata().getName(), namespace),
              "Failed to get pod log");
          logger.severe(podLog);
          fail("Domain create job failed");
        }
      }
    }
  }

}
