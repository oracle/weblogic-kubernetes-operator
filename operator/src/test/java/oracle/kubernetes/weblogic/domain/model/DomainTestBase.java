// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.io.IOException;
import java.net.URL;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.weblogic.domain.AdminServerConfigurator;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;

public abstract class DomainTestBase {
  static final String CLUSTER_NAME = "cluster1";
  static final String SERVER1 = "ms1";
  static final String DOMAIN_V2_SAMPLE_YAML = "domain-sample.yaml";
  static final String DOMAIN_V2_SAMPLE_YAML_2 = "domain-sample-2.yaml";
  static final String DOMAIN_V2_SAMPLE_YAML_3 = "domain-sample-3.yaml";
  static final String DOMAIN_V2_SAMPLE_YAML_4 = "domain-sample-4.yaml";
  static final String DOMAIN_V2_SAMPLE_YAML_5 = "domain-sample-5.yaml";
  static final String SECRET_NAME = "secret";
  static final String NS = "test-namespace";
  static final String DOMAIN_UID = "uid1";

  final DomainResource domain = createDomain();
  final DomainPresenceInfo info = new DomainPresenceInfo(domain);

  static DomainResource createDomain() {
    return new DomainResource()
        .withMetadata(new V1ObjectMeta().namespace(NS))
        .withSpec(
              new DomainSpec()
                    .withWebLogicCredentialsSecret(new V1LocalObjectReference().name(SECRET_NAME))
                    .withDomainUid(DOMAIN_UID));
  }

  static DomainConfigurator configureDomain(DomainResource domain) {
    DomainCommonConfigurator commonConfigurator = new DomainCommonConfigurator(domain);
    commonConfigurator.configureAdminServer();
    return commonConfigurator;
  }

  final ClusterConfigurator configureCluster(String clusterName) {
    return configureDomain(domain).configureCluster(info, clusterName);
  }

  final ServerConfigurator configureServer(String serverName) {
    return configureDomain(domain).configureServer(serverName);
  }

  final AdminServerConfigurator configureAdminServer() {
    return configureDomain(domain).configureAdminServer();
  }

  @SuppressWarnings("SameParameterValue")
  protected DomainResource readDomain(String resourceName) throws IOException {
    String json = jsonFromYaml(resourceName);
    Gson gson = new GsonBuilder().create();
    return gson.fromJson(json, DomainResource.class);
  }

  @SuppressWarnings("SameParameterValue")
  protected DomainPresenceInfo readDomainPresence(String resourceName) throws IOException {
    return new DomainPresenceInfo(readDomain(resourceName));
  }

  private String jsonFromYaml(String resourceName) throws IOException {
    URL resource = DomainTestBase.class.getResource(resourceName);
    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    Object obj = yamlReader.readValue(resource, Object.class);

    ObjectMapper jsonWriter = new ObjectMapper();
    return jsonWriter.writeValueAsString(obj);
  }
}
