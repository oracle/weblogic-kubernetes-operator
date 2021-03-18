// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.io.IOException;
import java.time.OffsetDateTime;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.ApiextensionsV1beta1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1beta1CustomResourceDefinition;
import io.kubernetes.client.util.ClientBuilder;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;

import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DEFAULT_CHANNEL_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.getDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.impl.Kubernetes.doesPodNotExist;
import static oracle.weblogic.kubernetes.assertions.impl.Kubernetes.isPodReady;
import static oracle.weblogic.kubernetes.assertions.impl.Kubernetes.isPodRestarted;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Domain {

  static {
    try {
      Configuration.setDefaultApiClient(ClientBuilder.defaultClient());
    } catch (IOException ioex) {
      throw new ExceptionInInitializerError(ioex);
    }
  }

  private static ConditionFactory withQuickRetryPolicy = with().pollDelay(0, SECONDS)
      .and().with().pollInterval(3, SECONDS)
      .atMost(12, SECONDS).await();

  private static final CustomObjectsApi customObjectsApi = new CustomObjectsApi();
  private static final ApiextensionsV1beta1Api apiextensionsV1beta1Api = new ApiextensionsV1beta1Api();

  /**
   * Check if the Domain CRD exists.
   *
   * @return true if domains.weblogic.oracle CRD exists otherwise false
   * @throws ApiException when Domain CRD doesn't exist
   */
  public static boolean doesCrdExist() throws ApiException {
    try {
      V1beta1CustomResourceDefinition domainBetaCrd
          = apiextensionsV1beta1Api.readCustomResourceDefinition(
          "domains.weblogic.oracle", null, null, null);
      assertNotNull(domainBetaCrd, "Domain CRD is null");
      return true;
    } catch (ApiException aex) {
      if (aex.getCode() == 404) {
        assertTrue(false, "CRD domains.weblogic.oracle not found");
      } else {
        throw aex;
      }
    }
    return false;
  }

  /**
   * Checks if weblogic.oracle CRD domain object exists.
   *
   * @param domainUid domain UID of the domain object
   * @param domainVersion version value for Kind Domain
   * @param namespace in which the domain object exists
   * @return true if domain object exists otherwise false
   */
  public static boolean doesDomainExist(String domainUid, String domainVersion, String namespace) {

    Object domainObject = null;
    try {
      domainObject
          = customObjectsApi.getNamespacedCustomObject(
          "weblogic.oracle", domainVersion, namespace, "domains", domainUid);
    } catch (ApiException apex) {
      getLogger().info(apex.getMessage());
    }
    boolean domainExist = (domainObject != null);
    getLogger().info("Domain Object exists : " + domainExist);
    return domainExist;
  }

  /**
   * Check if the domain resource has been patched with a new image.
   *
   * @param domainUID identifier of the domain resource
   * @param namespace Kubernetes namespace in which the domain exists
   * @param image name of the image that the pod is expected to be using
   * @return true if domain resource's image matches the expected value
   */
  public static boolean domainResourceImagePatched(
      String domainUID,
      String namespace,
      String image
  ) {
    oracle.weblogic.domain.Domain domain = null;
    try {
      domain = getDomainCustomResource(domainUID, namespace);
    } catch (ApiException apex) {
      getLogger().severe("Failed to obtain the domain resource object from the API server", apex);
      return false;
    }

    boolean domainPatched = (domain.spec().image().equals(image));
    getLogger().info("Domain Object patched : " + domainPatched + " domain image = " + domain.spec().image());
    return domainPatched;
  }

  /**
   * Check if the domain resource has been patched with a new WebLogic domain credentials secret.
   *
   * @param domainUID identifier of the domain resource
   * @param namespace Kubernetes namespace in which the domain exists
   * @param secretName name of the secret that the domain resource is expected to be using
   * @return true if domain resource's webLogicCredentialsSecret matches the expected value
   */
  public static boolean domainResourceCredentialsSecretPatched(
      String domainUID,
      String namespace,
      String secretName
  ) {
    oracle.weblogic.domain.Domain domain = null;
    try {
      domain = getDomainCustomResource(domainUID, namespace);
    } catch (ApiException apex) {
      getLogger().severe(String.format("Failed to obtain domain resource %s in namespace %s",
          domainUID, namespace), apex);
      return false;
    }

    boolean domainPatched = domain.spec().webLogicCredentialsSecret().getName().equals(secretName);
    getLogger().info("Domain {0} is patched with webLogicCredentialsSecret: {1}",
        domainUID, domain.getSpec().webLogicCredentialsSecret().getName());
    return domainPatched;
  }

  public static boolean adminT3ChannelAccessible(String domainUid, String namespace) {
    return true;
  }

  /**
   * Verify admin node port(default/t3channel) is accessible by login to WebLogic console
   * using the node port and validate its the Home page.
   *
   * @param nodePort the node port that needs to be tested for access
   * @param userName WebLogic administration server user name
   * @param password WebLogic administration server password
   * @return true if login to WebLogic administration console is successful
   * @throws IOException when connection to console fails
   */
  public static boolean adminNodePortAccessible(int nodePort, String userName, String password)
      throws IOException {

    LoggingFacade logger = getLogger();

    String consoleUrl = new StringBuffer()
        .append("http://")
        .append(K8S_NODEPORT_HOST)
        .append(":")
        .append(nodePort)
        .append("/console/login/LoginForm.jsp").toString();

    getLogger().info("Accessing WebLogic console with url {0}", consoleUrl);
    final WebClient webClient = new WebClient();
    final HtmlPage loginPage = assertDoesNotThrow(() -> webClient.getPage(consoleUrl),
        "connection to the WebLogic admin console failed");
    HtmlForm form = loginPage.getFormByName("loginData");
    form.getInputByName("j_username").type(userName);
    form.getInputByName("j_password").type(password);
    HtmlElement submit = form.getOneHtmlElementByAttribute("input", "type", "submit");
    getLogger().info("Clicking login button");
    HtmlPage home = submit.click();
    assertTrue(home.asText().contains("Persistent Stores"), "Home does not contain Persistent Stores text");
    getLogger().info("Console login passed");
    return true;
  }

  /**
   * Verify the pod state is not changed.
   * @param podName the name of the pod to check
   * @param domainUid the label the pod is decorated with
   * @param domainNamespace the domain namespace in which the pod exists
   * @param podOriginalCreationTimestamp the pod original creation timestamp
   * @return true if the pod state is not changed, false otherwise
   */
  public static boolean podStateNotChanged(String podName,
                                           String domainUid,
                                           String domainNamespace,
                                           OffsetDateTime podOriginalCreationTimestamp) {

    // if pod does not exist, return false
    if (assertDoesNotThrow(() -> doesPodNotExist(domainNamespace, domainUid, podName),
        String.format("podExists failed with ApiException for pod %s in namespace %s",
            podName, domainNamespace))) {
      getLogger().info("pod {0} does not exist in namespace {1}", podName, domainNamespace);
      return false;
    }

    // if the pod is not in ready state, return false
    getLogger().info("Checking that pod {0} is ready in namespace {1}", podName, domainNamespace);
    if (!assertDoesNotThrow(() -> isPodReady(domainNamespace, domainUid, podName),
        String.format("isPodReady failed with ApiException for pod %s in namespace %s", podName, domainNamespace))) {
      getLogger().info("pod {0} is not ready in namespace {1}", podName, domainNamespace);
      return false;
    }

    // if the pod was restarted, return false
    getLogger().info("Checking that pod {0} is not restarted in namespace {1}", podName, domainNamespace);
    if (assertDoesNotThrow(() ->
        isPodRestarted(podName, domainNamespace, podOriginalCreationTimestamp),
        String.format("isPodRestarted failed with ApiException for pod %s in namespace %s",
            podName, domainNamespace))) {
      getLogger().info("pod {0} is restarted in namespace {1}", podName, domainNamespace);
      return false;
    }

    return true;
  }

  /**
   * Check if the given WebLogic credentials are valid by using the credentials to
   * invoke a RESTful Management Services command.
   *
   * @param host hostname of the admin server pod
   * @param podName name of the admin server pod
   * @param namespace name of the namespace that the pod is running in
   * @param username WebLogic admin username
   * @param password WebLogic admin password
   * @return true if the RESTful Management Services command succeeded
   **/
  public static boolean credentialsValid(
      String host,
      String podName,
      String namespace,
      String username,
      String password) {
    CommandParams params = createCommandParams(host, podName, namespace, username, password);
    return Command.withParams(params).executeAndVerify("200");
  }

  /**
   * Check if the given WebLogic credentials are not valid by using the credentials to
   * invoke a RESTful Management Services command.
   *
   * @param host hostname of the admin server pod
   * @param podName name of the admin server pod
   * @param namespace name of the namespace that the pod is running in
   * @param username WebLogic admin username
   * @param password WebLogic admin password
   * @return true if the RESTful Management Services command failed with exitCode 401
   **/
  public static boolean credentialsNotValid(
      String host,
      String podName,
      String namespace,
      String username,
      String password) {
    CommandParams params = createCommandParams(host, podName, namespace, username, password);
    return Command.withParams(params).executeAndVerify("401");
  }

  private static CommandParams createCommandParams(
      String host,
      String podName,
      String namespace,
      String username,
      String password) {
    int adminServiceNodePort
        = getServiceNodePort(namespace, getExternalServicePodName(podName), WLS_DEFAULT_CHANNEL_NAME);

    if (username == null) {
      username = ADMIN_USERNAME_DEFAULT;
    }
    if (password == null) {
      password = ADMIN_PASSWORD_DEFAULT;
    }

    // create a RESTful management services command that connects to admin server using given credentials to get
    // information about a managed server
    StringBuffer cmdString = new StringBuffer()
        .append("status=$(curl --user " + username + ":" + password)
        .append(" http://" + host + ":" + adminServiceNodePort)
        .append("/management/tenant-monitoring/servers/managed-server1")
        .append(" --silent --show-error")
        .append(" --noproxy '*'")
        .append(" -o /dev/null")
        .append(" -w %{http_code});")
        .append(" echo ${status}");

    return Command
            .defaultCommandParams()
            .command(cmdString.toString())
            .saveResults(true)
            .redirect(true)
            .verbose(true);
  }

}
