// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.joda.time.DateTime;

import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD_NAME;

/**
 * Setup for tests that will involve running the main domain processor functionality. Such tests
 * should run this in their setup, before trying to invoke {@link
 * DomainProcessorImpl#makeRightDomainPresence(DomainPresenceInfo, boolean, boolean, boolean)}
 */
public class DomainProcessorTestSetup {
  public static final String UID = "test-domain";
  public static final String NS = "namespace";
  public static final String SECRET_NAME = "secret-name";

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
          + "@[2018-10-04T21:07:06.864 UTC][introspectDomain.py:105] Printing file "
          + "/u01/introspect/domain1/userKeyNodeManager.secure\n"
          + "\n"
          + ">>>  /u01/introspect/domain1/userKeyNodeManager.secure\n"
          + "BPtNabkCIIc2IJp/TzZ9TzbUHG7O3xboteDytDO3XnwNhumdSpaUGKmcbusdmbOUY+4J2kteu6xJPWTzmNRAtg==\n"
          + "\n"
          + ">>> EOF\n"
          + "\n"
          + "@[2018-10-04T21:07:06.867 UTC][introspectDomain.py:105] Printing file "
          + "/u01/introspect/domain1/topology.yaml\n"
          + "\n"
          + ">>>  /u01/introspect/domain1/topology.yaml\n"
          + "%s\n"
          + ">>> EOF";

  private KubernetesTestSupport testSupport;

  public DomainProcessorTestSetup(KubernetesTestSupport testSupport) {
    this.testSupport = testSupport;
  }

  public static void defineRequiredResources(KubernetesTestSupport testSupport) {
    testSupport.defineResources(createSecret());
  }

  private static V1Secret createSecret() {
    return new V1Secret().metadata(new V1ObjectMeta().name(SECRET_NAME).namespace(NS));
  }

  /**
   * Update the specified object metadata with usable time stamp and resource version data.
   *
   * @param meta a metadata object
   * @return the original metadata object, updated
   */
  private static V1ObjectMeta withTimestamps(V1ObjectMeta meta) {
    return meta.creationTimestamp(DateTime.now()).resourceVersion("1");
  }

  /**
   * Create a basic domain object that meets the needs of the domain processor.
   *
   * @return a domain
   */
  public static Domain createTestDomain() {
    return new Domain()
        .withMetadata(withTimestamps(new V1ObjectMeta().name(UID).namespace(NS)))
        .withSpec(
            new DomainSpec()
                .withWebLogicCredentialsSecret(new V1SecretReference().name(SECRET_NAME).namespace(NS)));
  }

  /**
   * Set up the in-memory Kubernetes environment for the domain processor, so that it can run
   * against what appears to be a domain job that includes the domain topology.
   *
   * @param domainConfig the configuration from which the topology should be computed
   * @throws JsonProcessingException if an error occurs in creating the topology
   */
  public void defineKubernetesResources(WlsDomainConfig domainConfig)
      throws JsonProcessingException {
    defineKubernetesResources(getIntrospectResult(domainConfig));
  }

  /**
   * Set up the in-memory Kubernetes environment for the domain processor, specifying the pod log.
   * This allows testing of log messages in the case of failures.
   *
   * @param introspectResult the log to be returned from the job pod
   */
  public void defineKubernetesResources(String introspectResult) {
    testSupport.addToPacket(JOB_POD_NAME, INTROSPECTION_JOB);
    testSupport.doOnCreate(
        KubernetesTestSupport.JOB,
        job ->
            ((V1Job) job)
                .setStatus(
                    new V1JobStatus()
                        .addConditionsItem(new V1JobCondition().type("Complete").status("True"))));
    testSupport.definePodLog(LegalNames.toJobIntrospectorName(UID), NS, introspectResult);
    testSupport.defineResources(
        new V1Pod()
            .metadata(
                new V1ObjectMeta()
                    .putLabelsItem("job-name", "")
                    .name(LegalNames.toJobIntrospectorName(UID))
                    .namespace(NS)));
  }

  private String getIntrospectResult(WlsDomainConfig domainConfig) throws JsonProcessingException {
    return String.format(INTROSPECT_RESULT, createTopologyYaml(domainConfig));
  }

  private String createTopologyYaml(WlsDomainConfig domainConfig) throws JsonProcessingException {
    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    return yamlMapper
        .writerWithDefaultPrettyPrinter()
        .writeValueAsString(domainConfig.toTopology());
  }
}
