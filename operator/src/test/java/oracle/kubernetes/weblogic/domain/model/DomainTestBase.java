// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Yaml;
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
  static final String DOMAIN_V2_SAMPLE_YAML_6 = "domain-sample-6.yaml";
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
  protected DomainPresenceInfo readDomainPresence(String resourceName) throws IOException {
    List<KubernetesObject> data = readFromYaml(resourceName);
    DomainPresenceInfo info = new DomainPresenceInfo((DomainResource) data.get(0));
    data.stream().filter(ClusterResource.class::isInstance)
        .forEach(cr -> info.addClusterResource((ClusterResource) cr));
    return info;
  }

  List<KubernetesObject> readFromYaml(String resourceName) throws IOException {
    List<KubernetesObject> results = new ArrayList<>();
    URL resource = DomainTestBase.class.getResource(resourceName);
    Gson gson = new GsonBuilder().create();
    try (Reader reader = new InputStreamReader(resource.openStream())) {
      Iterable<Object> iterable = Yaml.getSnakeYaml(null).loadAll(reader);
      for (Object obj : iterable) {
        ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
        Object json = yamlReader.readValue(Yaml.getSnakeYaml(null).dump(obj), Object.class);

        ObjectMapper jsonWriter = new ObjectMapper();
        String ko = jsonWriter.writeValueAsString(json);
        String kind = (String) ((Map) obj).get("kind");
        switch (kind) {
          case "Domain":
            results.add(gson.fromJson(ko, DomainResource.class));
            break;
          case "Cluster":
            results.add(gson.fromJson(ko, ClusterResource.class));
            break;
          default:
            throw new IllegalStateException();
        }
      }
      return results;
    }
  }
}
