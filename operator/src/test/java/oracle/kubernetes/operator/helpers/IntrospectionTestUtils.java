// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD_NAME;

public class IntrospectionTestUtils {


  private static final String INTROSPECTION_JOB = LegalNames.toJobIntrospectorName(UID);
  private static final String INTROSPECT_RESULT =
      ">>>  /u01/introspect/domain1/userConfigNodeManager.secure\n"
          + "#WebLogic User Configuration File; 2\n"
          + "#Thu Oct 04 21:07:06 GMT 2018\n"
          + "weblogic.management.username={AES}fq11xKVoE927O07IUKhQ00d4A8QY598Dvd+KSnHNTEA\\=\n"
          + "weblogic.management.password={AES}LIxVY+aqI8KBkmlBTwkvAnQYQs4PS0FX3Ili4uLBggo\\=\n"
          + "\n"
          + ">>> EOF\n"
          + "\n"
          + "@[2018-10-04T21:07:06.864000Z][introspectDomain.py:105] Printing file "
          + "/u01/introspect/domain1/userKeyNodeManager.secure\n"
          + "\n"
          + ">>>  /u01/introspect/domain1/userKeyNodeManager.secure\n"
          + "BPtNabkCIIc2IJp/TzZ9TzbUHG7O3xboteDytDO3XnwNhumdSpaUGKmcbusdmbOUY+4J2kteu6xJPWTzmNRAtg==\n"
          + "\n"
          + ">>> EOF\n"
          + "\n"
          + "@[2018-10-04T21:07:06.867000Z][introspectDomain.py:105] Printing file "
          + "/u01/introspect/domain1/topology.yaml\n"
          + "\n"
          + ">>>  /u01/introspect/domain1/topology.yaml\n"
          + "%s\n"
          + ">>> EOF";

  /**
   * Define in-memory kubernetes resources to represent an introspector job.
   *
   * @param testSupport a kubernetes test support instance
   * @param domainConfig the configuration from which the topology should be computed
   * @throws JsonProcessingException if an error occurs in creating the topology
   */
  static void defineResources(KubernetesTestSupport testSupport, WlsDomainConfig domainConfig)
      throws JsonProcessingException {
    defineResources(testSupport, domainConfig, IntrospectionTestUtils::createCompletedStatus);
  }

  /**
   * Define in-memory kubernetes resources to represent an introspector job.
   *
   * @param testSupport a kubernetes test support instance
   * @param domainConfig the configuration from which the topology should be computed
   * @param jobStatus a supplier for a job status to be applied to the introspector job
   * @throws JsonProcessingException if an error occurs in creating the topology
   */
  public static void defineResources(KubernetesTestSupport testSupport,
                                     WlsDomainConfig domainConfig,
                                     Supplier<V1JobStatus> jobStatus)
        throws JsonProcessingException {
    defineResources(testSupport, getIntrospectResult(domainConfig), jobStatus);
  }

  /**
   * Set up the in-memory Kubernetes environment for the domain processor, specifying the pod log.
   * This allows testing of log messages in the case of failures.
   *
   * @param testSupport a kubernetes test support instance
   * @param introspectResult the log to be returned from the job pod
   */
  static void defineResources(KubernetesTestSupport testSupport, String introspectResult) {
    defineResources(testSupport, introspectResult, IntrospectionTestUtils::createCompletedStatus);
  }

  private static void defineResources(KubernetesTestSupport testSupport,
                              String introspectResult,
                              Supplier<V1JobStatus> jobStatus) {
    testSupport.addToPacket(JOB_POD_NAME, INTROSPECTION_JOB);
    testSupport.doOnCreate(KubernetesTestSupport.JOB, job -> ((V1Job) job).setStatus(jobStatus.get()));
    testSupport.definePodLog(LegalNames.toJobIntrospectorName(UID), NS, introspectResult);
    testSupport.defineResources(
        new V1Pod()
            .metadata(
                new V1ObjectMeta()
                    .putLabelsItem("job-name", "")
                    .name(LegalNames.toJobIntrospectorName(UID))
                    .namespace(NS)));
  }

  private static V1JobStatus createCompletedStatus() {
    return new V1JobStatus()
          .addConditionsItem(new V1JobCondition().type("Complete").status("True"));
  }

  private static String getIntrospectResult(WlsDomainConfig domainConfig) throws JsonProcessingException {
    return String.format(INTROSPECT_RESULT, createTopologyYaml(domainConfig));
  }

  /**
   * Create a topologyYaml similar to that produced by the introspector.
   * @param domainConfig the domain configuration used as a basis for the produced YAML..
   * @throws JsonProcessingException if unable to convert the configuration to YAML.
   */
  public static String createTopologyYaml(WlsDomainConfig domainConfig) throws JsonProcessingException {
    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    return yamlMapper
        .writerWithDefaultPrettyPrinter()
        .writeValueAsString(domainConfig.toTopology());
  }
}
