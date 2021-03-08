// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.SECRET_NAME;
import static oracle.kubernetes.operator.helpers.SecretHelper.PASSWORD_KEY;
import static oracle.kubernetes.operator.helpers.SecretHelper.USERNAME_KEY;
import static oracle.kubernetes.operator.helpers.SecretHelper.getAuthorizationHeaderFactory;
import static oracle.kubernetes.operator.logging.MessageKeys.SECRET_DATA_NOT_FOUND;
import static oracle.kubernetes.operator.logging.MessageKeys.SECRET_NOT_FOUND;
import static oracle.kubernetes.utils.LogMatcher.containsWarning;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class SecretHelperTest {

  private static final String USERNAME = "itsMe";
  private static final String PASSWORD = "shhh";
  private static final byte[] USERNAME_BYTES = USERNAME.getBytes();
  private static final byte[] PASSWORD_BYTES = PASSWORD.getBytes();

  private final Domain domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);

  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();

  @BeforeEach
  public void setUp() {
    mementos.add(TestUtils.silenceOperatorLogger()
          .collectLogMessages(logRecords, SECRET_NOT_FOUND, SECRET_DATA_NOT_FOUND));
    mementos.add(testSupport.install());

    testSupport.addDomainPresenceInfo(info);
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void whenSecretNotDefined_logWarning() {
    runSteps();

    assertThat(logRecords, containsWarning(SECRET_NOT_FOUND));
  }

  private Packet runSteps() {
    return testSupport.runSteps(SecretHelper.createAuthorizationHeaderFactoryStep());
  }

  @Test
  void whenSecretUserNotDefined_logWarning() {
    testSupport.defineResources(new V1Secret()
                      .metadata(new V1ObjectMeta().namespace(NS).name(SECRET_NAME))
                      .data(Map.of(PASSWORD_KEY, PASSWORD_BYTES)));
    
    runSteps();

    assertThat(logRecords, containsWarning(SECRET_DATA_NOT_FOUND));
  }

  @Test
  void whenSecretPasswordNotDefined_logWarning() {
    testSupport.defineResources(new V1Secret()
                      .metadata(new V1ObjectMeta().namespace(NS).name(SECRET_NAME))
                      .data(Map.of(USERNAME_KEY, USERNAME_BYTES)));

    runSteps();

    assertThat(logRecords, containsWarning(SECRET_DATA_NOT_FOUND));
  }

  @Test
  void afterStepsRun_packetContainsAuthorizationHeaderFactoryWithCredentials() {
    testSupport.defineResources(new V1Secret()
                      .metadata(new V1ObjectMeta().namespace(NS).name(SECRET_NAME))
                      .data(Map.of(
                            USERNAME_KEY, USERNAME_BYTES,
                            PASSWORD_KEY, PASSWORD_BYTES)));

    Packet packet = runSteps();

    assertThat(getAuthorizationHeaderFactory(packet).createBasicAuthorizationString(),
          equalTo(createExpectedBasicAuthorizationString()));
  }

  private String createExpectedBasicAuthorizationString() {
    return "Basic " + Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes());
  }
}