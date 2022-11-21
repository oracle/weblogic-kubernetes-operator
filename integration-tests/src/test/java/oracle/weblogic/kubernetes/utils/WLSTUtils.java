// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.kubernetes.actions.impl.Namespace;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.actions.TestActions.createNamespacedJob;
import static oracle.weblogic.kubernetes.actions.TestActions.getJob;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.listPods;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.jobCompleted;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapFromFiles;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A utility class for WLST.
 */
public class WLSTUtils {

  private static final String MOUNT_POINT = "/scripts";

  /**
   * Execute WLST script.
   *
   * @param wlstScript WLST script file path
   * @param domainProperties domain property file path
   * @param namespace namespace in which to run the job
   */
  public static void executeWLSTScript(Path wlstScript, Path domainProperties, String namespace) {
    final LoggingFacade logger = getLogger();

    // this secret is used only for non-kind cluster
    createBaseRepoSecret(namespace);


    String wlstScriptFileName = wlstScript.getFileName().toString();
    String wlstPropertiesFile = domainProperties.getFileName().toString();

    logger.info("Creating a config map to hold WLST script files");
    String uniqueName = Namespace.uniqueName();
    String wlstScriptConfigMapName = "wlst-scripts-cm-" + uniqueName;
    String wlstJobName = "wlst-job-" + uniqueName;

    createConfigMapFromFiles(wlstScriptConfigMapName,
        Arrays.asList(wlstScript, domainProperties), namespace);

    logger.info("Preparing to run WLST job");
    // create a V1Container with specific scripts and properties for running WLST script
    V1Container jobCreationContainer = new V1Container()
        .addCommandItem("/bin/sh")
        .addArgsItem("/u01/oracle/oracle_common/common/bin/wlst.sh")
        .addArgsItem(MOUNT_POINT + "/" + wlstScriptFileName) //WLST py script
        .addArgsItem("-skipWLSModuleScanning")
        .addArgsItem("-loadProperties")
        .addArgsItem(MOUNT_POINT + "/" + wlstPropertiesFile); //WLST property file

    assertDoesNotThrow(()
        -> createWLSTJob(wlstJobName, wlstScriptConfigMapName, namespace, jobCreationContainer),
        "Online WLST execution failed");
  }

  /**
   * Create a job to execute WLST script.
   *
   * @param wlstJobName a unique job name
   * @param wlstScriptConfigMapName configmap holding WLST script file
   * @param namespace name of the namespace in which the job is created
   * @param jobContainer V1Container with job commands to execute WLST script
   * @throws ApiException when Kubernetes cluster query fails
   */
  public static void createWLSTJob(String wlstJobName, String wlstScriptConfigMapName, String namespace,
                                   V1Container jobContainer) throws ApiException {
    LoggingFacade logger = getLogger();
    logger.info("Running Kubernetes job to execute WLST script");

    V1Job jobBody = new V1Job()
        .metadata(
            new V1ObjectMeta()
                .name(wlstJobName)
                .namespace(namespace))
        .spec(new V1JobSpec()
            .backoffLimit(0) // try only once
            .template(new V1PodTemplateSpec()
                .spec(new V1PodSpec()
                    .restartPolicy("Never")
                    .containers(Arrays.asList(jobContainer
                        .name("execute-wlst-container")
                        .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
                        .imagePullPolicy(IMAGE_PULL_POLICY)
                        .volumeMounts(Arrays.asList(
                            new V1VolumeMount()
                                .name("wlst-job-cm-volume") // WLST script volume
                                .mountPath(MOUNT_POINT))))) // mounted under /sctipts inside pod
                    .volumes(Arrays.asList(new V1Volume()
                        .name("wlst-job-cm-volume") // WLST scripts volume
                        .configMap(new V1ConfigMapVolumeSource()
                            .name(wlstScriptConfigMapName)))) //config map containing WLST script
                    .imagePullSecrets(Arrays.asList(
                        new V1LocalObjectReference()
                            .name(BASE_IMAGES_REPO_SECRET_NAME))))));  // this secret is used only for non-kind cluster
    String jobName = assertDoesNotThrow(()
        -> createNamespacedJob(jobBody), "Failed to create WLST execution Job");

    logger.info("Checking if the WLST job {0} completed in namespace {1}",
        jobName, namespace);
    testUntil(
        jobCompleted(jobName, null, namespace),
        logger,
        "job {0} to be completed in namespace {1}",
        jobName,
        namespace);

    // check job status and fail test if the job failed to execute WLST
    V1Job job = getJob(jobName, namespace);
    if (job != null) {
      V1JobCondition jobCondition = job.getStatus().getConditions().stream().filter(
          v1JobCondition -> "Failed".equals(v1JobCondition.getType()))
          .findAny()
          .orElse(null);
      if (jobCondition != null) {
        logger.severe("Job {0} failed to execute WLST script", jobName);
        List<V1Pod> pods = listPods(namespace, "job-name=" + jobName).getItems();
        if (!pods.isEmpty()) {
          logger.severe(getPodLog(pods.get(0).getMetadata().getName(), namespace));
          fail("WLST execute job failed");
        }
      }
      List<V1Pod> pods = listPods(namespace, "job-name=" + jobName).getItems();
      if (!pods.isEmpty()) {
        logger.info(getPodLog(pods.get(0).getMetadata().getName(), namespace));
      }
    }
  }

  /**
   * Build application.
   * @param appSrcPath path of the application source folder
   * @param antParams ant parameters
   * @param antTargets ant targets to call
   * @param archiveDistDir location of the archive built inside source directory
   * @param namespace name of the namespace to create the pod in
   * @param targetPath the target path where the application will be archived
   */
  public static void buildApplication(Path appSrcPath, Map<String, String> antParams,
                                      String antTargets, String archiveDistDir,
                                      String namespace, Path targetPath) {

    final LoggingFacade logger = getLogger();

    // this secret is used only for non-kind cluster
    createBaseRepoSecret(namespace);

    // add ant properties as env variable in pod
    V1Container buildContainer = new V1Container();
  }

  /**
   * Execute WLST script in local.
   *
   * @param wlstScriptFile WLST script file path
   * @param t3Url t3 URL
   * @return ExecResult output of executing WLST script
   */
  public static ExecResult executeWLSTScriptInLocal(String wlstScriptFile,
                                                    String t3Url) {
    final LoggingFacade logger = getLogger();
    ExecResult result = null;

    // create a V1Container with specific scripts and properties for running WLST script
    StringBuffer cmdRunWlstScript = new StringBuffer("java weblogic.WLST ")
        .append(wlstScriptFile)
        .append(" -username ")
        .append(ADMIN_USERNAME_DEFAULT)
        .append(" -password ")
        .append(ADMIN_PASSWORD_DEFAULT)
        .append(" -url ")
        .append(t3Url);

    logger.info("execute WLST script in local: command {0}", cmdRunWlstScript.toString());
    try {
      result = exec(cmdRunWlstScript.toString(), true);
    } catch (Exception ex) {
      logger.info("cmdRunWlstScript: caught unexpected exception {0}", ex);
      return null;
    }

    return result;
  }

  /**
   * Start a port-forward process and tests using forwarded port.
   * @param containerName container name
   * @param wlstScriptFile WLST script file path
   * @param wlstPropertiesFile WLST property file path
   * @return ExecResult output of executing WLST script
   */
  public static ExecResult executeWLSTScriptInImageContainer(String containerName,
                                                              String wlstScriptFile,
                                                              String wlstPropertiesFile) {
    final LoggingFacade logger = getLogger();
    ExecResult result = null;

    String checkImageBuilderVersion = WLSIMG_BUILDER + " version";
    try {
      result = exec(checkImageBuilderVersion, true);
      logger.info(WLSIMG_BUILDER + " version: {0}", result.stdout());
    } catch (Exception ex) {
      logger.info(WLSIMG_BUILDER + " version failed error {0} and {1}", result.stderr(), ex.getMessage());
      ex.printStackTrace();
    }

    // execute WLST script in a container
    logger.info("Preparing to run WLST script");
    //StringBuffer cmdRunWlstScript = new StringBuffer(" " + WLSIMG_BUILDER + " exec -it ")
    StringBuffer cmdRunWlstScript = new StringBuffer(WLSIMG_BUILDER + " exec ")
        .append(containerName)
        .append(" sh /u01/oracle/oracle_common/common/bin/wlst.sh ")
        .append(wlstScriptFile)
        .append(" -skipWLSModuleScanning ")
        .append(" -loadProperties ")
        .append(wlstPropertiesFile);

    logger.info("execute WLST script in a container: command {0}", cmdRunWlstScript.toString());
    try {
      result = exec(cmdRunWlstScript.toString(), true);
    } catch (Exception ex) {
      logger.info("cmdRunWlstScript: caught unexpected exception {0}", ex);
      return null;
    }

    return result;
  }
}
