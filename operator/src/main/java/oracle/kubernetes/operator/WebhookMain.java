// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.EventHelper;
import oracle.kubernetes.operator.helpers.WebhookHelper;
import oracle.kubernetes.operator.http.rest.BaseRestServer;
import oracle.kubernetes.operator.http.rest.RestConfig;
import oracle.kubernetes.operator.http.rest.RestConfigImpl;
import oracle.kubernetes.operator.http.rest.WebhookRestServer;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.steps.InitializeWebhookIdentityStep;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.utils.Certificates;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.DomainList;

import static oracle.kubernetes.common.CommonConstants.SECRETS_WEBHOOK_CERT;
import static oracle.kubernetes.common.CommonConstants.SECRETS_WEBHOOK_KEY;
import static oracle.kubernetes.operator.EventConstants.CONVERSION_WEBHOOK_COMPONENT;
import static oracle.kubernetes.operator.helpers.CrdHelper.createDomainCrdStep;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.CONVERSION_WEBHOOK_FAILED;
import static oracle.kubernetes.operator.helpers.EventHelper.createConversionWebhookEvent;
import static oracle.kubernetes.operator.helpers.EventHelper.createEventStep;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getWebhookNamespace;

/** A Domain Custom Resource Conversion Webhook for WebLogic Kubernetes Operator. */
public class WebhookMain extends BaseMain {

  private final WebhookMainDelegate conversionWebhookMainDelegate;
  private boolean warnedOfCrdAbsence;
  private final RestConfig restConfig = new RestConfigImpl(new Certificates(delegate));
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static NextStepFactory nextStepFactory = WebhookMain::createInitializeWebhookIdentityStep;

  static class WebhookMainDelegateImpl extends CoreDelegateImpl implements WebhookMainDelegate {

    public WebhookMainDelegateImpl(Properties buildProps, ScheduledExecutorService scheduledExecutorService) {
      super(buildProps, scheduledExecutorService);
    }

    private void logStartup() {
      LOGGER.info(MessageKeys.CONVERSION_WEBHOOK_STARTED, buildVersion,
              deploymentImpl, deploymentBuildTime);
      LOGGER.info(MessageKeys.WEBHOOK_CONFIG_NAMESPACE, getWebhookNamespace());
    }

    @Override
    public String getWebhookCertUri() {
      return SECRETS_WEBHOOK_CERT;
    }

    @Override
    public String getWebhookKeyUri() {
      return SECRETS_WEBHOOK_KEY;
    }

  }

  /**
   * Entry point.
   *
   * @param args none, ignored
   */
  public static void main(String[] args) {
    WebhookMain main = createMain(getBuildProperties());

    try {
      main.startDeployment(main::completeBegin);

      // now we just wait until the pod is terminated
      main.waitForDeath();

      // stop the webhook REST server
      stopWebhookRestServer();
    } finally {
      LOGGER.info(MessageKeys.WEBHOOK_SHUTTING_DOWN);
    }
  }

  static WebhookMain createMain(Properties buildProps) {
    final WebhookMainDelegateImpl delegate =
            new WebhookMainDelegateImpl(buildProps, wrappedExecutorService);

    delegate.logStartup();
    return new WebhookMain(delegate);
  }

  WebhookMain(WebhookMainDelegate conversionWebhookMainDelegate) {
    super(conversionWebhookMainDelegate);
    this.conversionWebhookMainDelegate = conversionWebhookMainDelegate;
  }

  @Override
  protected Step createStartupSteps() {
    Certificates certs = new Certificates(delegate);
    return nextStepFactory.createInitializationStep(conversionWebhookMainDelegate,
        Step.chain(
            createDomainCrdStep(delegate.getKubernetesVersion(), delegate.getProductVersion(), certs),
            new CheckFailureAndCreateEventStep(),
            WebhookHelper.createValidatingWebhookConfigurationStep(certs)));
  }

  private static Step createInitializeWebhookIdentityStep(WebhookMainDelegate delegate, Step next) {
    return new InitializeWebhookIdentityStep(delegate, next);
  }

  void completeBegin() {
    try {
      // start the conversion webhook REST server
      startRestServer();

      // start periodic recheck of CRD
      int recheckInterval = TuningParameters.getInstance().getDomainNamespaceRecheckIntervalSeconds();
      delegate.scheduleWithFixedDelay(recheckCrd(), recheckInterval, recheckInterval, TimeUnit.SECONDS);

      markReadyAndStartLivenessThread();

    } catch (Exception e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
      EventHelper.EventData eventData = new EventHelper.EventData(CONVERSION_WEBHOOK_FAILED, e.getMessage())
          .resourceName(CONVERSION_WEBHOOK_COMPONENT);
      createConversionWebhookEvent(eventData);

    }
  }

  Runnable recheckCrd() {
    return () -> delegate.runSteps(createCRDRecheckSteps());
  }

  Step createCRDRecheckSteps() {
    return Step.chain(createDomainCrdStep(delegate.getKubernetesVersion(), delegate.getProductVersion(),
            new Certificates(delegate)), createCRDPresenceCheck());
  }

  // Returns a step that verifies the presence of an installed domain CRD. It does this by attempting to list the
  // domains in the operator's namespace. That should succeed (although usually returning an empty list)
  // if the CRD is present.
  private Step createCRDPresenceCheck() {
    return new CallBuilder().listDomainAsync(getWebhookNamespace(), new CrdPresenceResponseStep());
  }

  // on failure, aborts the processing.
  private class CrdPresenceResponseStep extends DefaultResponseStep<DomainList> {

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<DomainList> callResponse) {
      warnedOfCrdAbsence = false;
      return super.onSuccess(packet, callResponse);
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<DomainList> callResponse) {
      if (!warnedOfCrdAbsence) {
        LOGGER.severe(MessageKeys.CRD_NOT_INSTALLED);
        warnedOfCrdAbsence = true;
      }
      return doNext(null, packet);
    }
  }

  @Override
  protected void startRestServer()
          throws Exception {
    WebhookRestServer.create(restConfig);
    BaseRestServer.getInstance().start(container);
  }

  private static void stopWebhookRestServer() {
    BaseRestServer.getInstance().stop();
    BaseRestServer.destroy();
  }

  @Override
  protected void logStartingLivenessMessage() {
    LOGGER.info(MessageKeys.STARTING_WEBHOOK_LIVENESS_THREAD);
  }

  // an interface to provide a hook for unit testing.
  interface NextStepFactory {
    Step createInitializationStep(WebhookMainDelegate delegate, Step next);
  }

  public static class CheckFailureAndCreateEventStep extends Step {
    @Override
    public NextAction apply(Packet packet) {
      Exception failure = packet.getSpi(Exception.class);
      if (failure != null) {
        return doNext(createEventStep(new EventHelper.EventData(CONVERSION_WEBHOOK_FAILED, failure.getMessage())
            .namespace(getWebhookNamespace())), packet);
      }
      return doNext(getNext(), packet);
    }
  }

  public static class DeploymentException extends Exception {
    public DeploymentException(Exception e) {
      super(e);
    }
  }
}
