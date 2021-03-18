// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.time.OffsetDateTime;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;

import static oracle.kubernetes.operator.helpers.SecretHelper.PASSWORD_KEY;
import static oracle.kubernetes.operator.helpers.SecretHelper.USERNAME_KEY;

/**
 * Setup for tests that will involve running the main domain processor functionality. Such tests
 * should run this in their setup, before trying to create and execute 
 * a {@link DomainProcessorImpl.MakeRightDomainOperationImpl}.
 */
public class DomainProcessorTestSetup {
  public static final String UID = "test-domain";
  public static final String NS = "namespace";
  public static final String SECRET_NAME = "secret-name";
  public static final String KUBERNETES_UID = "12345";
  public static final String NODE_NAME = "Node1";

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
    return meta.creationTimestamp(OffsetDateTime.now()).resourceVersion("1");
  }

  /**
   * Create a basic domain object that meets the needs of the domain processor with the default test domainUID.
   *
   * @return a domain
   */
  public static Domain createTestDomain() {
    return createTestDomain(UID);
  }

  /**
   * Create a basic domain object that meets the needs of the domain processor with a custom domainUID.
   *
   * @param uid domainUid
   * @return a domain

   */
  public static Domain createTestDomain(String uid) {
    DomainSpec ds = new DomainSpec()
        .withDomainUid(uid)
        .withWebLogicCredentialsSecret(new V1SecretReference().name(SECRET_NAME).namespace(NS));
    ds.setNodeName(NODE_NAME);
    return new Domain()
        .withApiVersion(KubernetesConstants.DOMAIN_GROUP + "/" + KubernetesConstants.DOMAIN_VERSION)
        .withKind(KubernetesConstants.DOMAIN)
        .withMetadata(withTimestamps(new V1ObjectMeta().name(uid).namespace(NS).uid(KUBERNETES_UID)))
        .withSpec(ds);
  }

  /**
   * Creates the secret data for any test that must do authentication.
   * @param testSupport a Kubernetes Test Support instance
   */
  public static void defineSecretData(KubernetesTestSupport testSupport) {
    testSupport.defineResources(
                new V1Secret()
                      .metadata(new V1ObjectMeta().namespace(NS).name(SECRET_NAME))
                      .data(Map.of(USERNAME_KEY, "user".getBytes(),
                            PASSWORD_KEY, "password".getBytes())));
  }
}
