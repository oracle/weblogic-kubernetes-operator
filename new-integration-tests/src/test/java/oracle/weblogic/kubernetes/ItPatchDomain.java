// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1LocalObjectReferenceBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import oracle.weblogic.domain.DomainList;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.junit.jupiter.api.DisplayName;

import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.listDomainCustomResources;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;


@DisplayName("Patch Domain CRD")
@IntegrationTest
class ItPatchDomain implements LoggedTest {

  //@Test
  @DisplayName("Patch a Domain")
  public void testPatchDomain() {
    String jsonPatch = "[{\"op\": \"replace\", \"path\": \"/spec/restartVersion\", \"value\": \"1\" }]";
    V1Patch patch = new V1Patch(jsonPatch);
    String jsonMergePatch = "{\"spec\":{\"restartVersion\":\"1\"}}";

    Kubernetes.patchCustomResourceDomainJsonMergePatch("sample-domain1",
        "sample-domain1-ns",
        jsonMergePatch);

    patchDomainCustomResource("sample-domain1", "sample-domain1-ns", patch,
        V1Patch.PATCH_FORMAT_JSON_PATCH);
  }

  //@Test
  @DisplayName("Create Docker Registry Secret")
  public void testCreateSecretDockerRegistry() {
    // Docker parameters
    String registry = "container-registry.oracle.com";
    String dockerUsername = "lenny.phan@oracle.com";
    String dockerPassword = "weblogic1";
    String dockerEmail = "lenny.phan@oracle.com";

    JsonObject dockerConfigJsonObject = getDockerConfigJson(dockerUsername, dockerPassword, dockerEmail, registry);
    String dockerConfigJson = dockerConfigJsonObject.toString();

    // Create the V1Secret configuration
    V1ObjectMeta meta = new V1ObjectMetaBuilder()
        .withName("regsecret")
        .withNamespace("default")
        .build();
    V1Secret secret = new V1SecretBuilder()
        .withMetadata(meta)
        .withType("kubernetes.io/dockerconfigjson")
        .addToData(".dockerconfigjson", dockerConfigJson.getBytes())
        .build();

    try {
      createSecret(secret);
    } catch (ApiException e) {
      e.printStackTrace();
    }

    V1LocalObjectReference localObjectReference = new V1LocalObjectReferenceBuilder()
        .withName("regsecret")
        .build();
    DomainSpec spec = new DomainSpec()
        .addImagePullSecretsItem(localObjectReference);
  }

  private static JsonObject getDockerConfigJson(String username, String password, String email, String registry) {

    JsonObject authObject = new JsonObject();
    authObject.addProperty("username", username);
    authObject.addProperty("password", password);
    authObject.addProperty("email", email);
    String auth = username + ":" + password;
    String authEncoded = Base64.getEncoder().encodeToString(auth.getBytes());
    System.out.println("auth encoded: " + authEncoded);
    authObject.addProperty("auth", authEncoded);
    JsonObject registryObject = new JsonObject();
    registryObject.add(registry, authObject);
    JsonObject configJsonObject = new JsonObject();
    configJsonObject.add("auths", registryObject);
    return configJsonObject;
  }

  //@Test
  @DisplayName("Create a Config Map")
  public void testCreateConfigMap() {
    String [] modelAndPropFiles = {"tmp.model.jms.yml","tmp.model.props"};
    addConfigMap(modelAndPropFiles,"myconfigmap");
  }

  private void addConfigMap(String[] modelAndPropFiles, String cmName) {
    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.domainUID", "sample-domain1");
    Map<String, String> data = new HashMap<>();
    for (int i = 0; i < modelAndPropFiles.length; i++) {
      data.put(modelAndPropFiles[i], "");
    }
    V1ObjectMeta meta = new V1ObjectMetaBuilder()
        .withLabels(labels)
        .withName(cmName)
        .withNamespace("sample-domain1-ns")
        .build();
    V1ConfigMap configMap = new V1ConfigMapBuilder()
        .withData(data)
        .withMetadata(meta)
        .build();
    try {
      createConfigMap(configMap);
    } catch (ApiException e) {
      e.printStackTrace();
    }
  }

  //@Test
  @DisplayName("list Domains")
  public void testListDomains() {
    DomainList domains = listDomainCustomResources("sample-domain1-ns");
    System.out.println("domains: " + domains);
  }
}
