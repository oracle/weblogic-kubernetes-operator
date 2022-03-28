// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.CrdHelper;
import oracle.kubernetes.operator.rest.RestConfigImpl;
import oracle.kubernetes.operator.rest.WebhookRestServer;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.steps.InitializeWebhookIdentityStep;
import oracle.kubernetes.operator.utils.Certificates;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.DomainList;

import static oracle.kubernetes.operator.helpers.NamespaceHelper.getWebhookNamespace;

/** A Domain Custom Resource Conversion Webhook for WebLogic Kubernetes Operator. */
public class ConversionWebhookMain extends BaseMain {
  private boolean warnedOfCrdAbsence;
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static NextStepFactory nextStepFactory = ConversionWebhookMain::createInitializeWebhookIdentityStep;

  static class ConversionWebhookMainDelegateImpl extends CoreDelegateImpl {
    public ConversionWebhookMainDelegateImpl(Properties buildProps, ScheduledExecutorService scheduledExecutorService) {
      super(buildProps, scheduledExecutorService);
    }

    private void logStartup() {
      ConversionWebhookMain.LOGGER.info(MessageKeys.CONVERSION_WEBHOOK_STARTED, buildVersion,
              deploymentImpl, deploymentBuildTime);
      ConversionWebhookMain.LOGGER.info(MessageKeys.WEBHOOK_CONFIG_NAMESPACE, getWebhookNamespace());
    }
  }

  /**
   * Entry point.
   *
   * @param args none, ignored
   */
  public static void main(String[] args) {
    ConversionWebhookMain main = createMain(getBuildProperties());

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

  static ConversionWebhookMain createMain(Properties buildProps) {
    final ConversionWebhookMainDelegateImpl delegate =
            new ConversionWebhookMainDelegateImpl(buildProps, wrappedExecutorService);

    delegate.logStartup();
    return new ConversionWebhookMain(delegate);
  }

  ConversionWebhookMain(CoreDelegate delegate) {
    super(delegate);
  }

  @Override
  protected Step createStartupSteps() {
    return nextStepFactory.createInitializationStep(delegate, CrdHelper.createDomainCrdStep(
            delegate.getKubernetesVersion(), delegate.getProductVersion(), new Certificates(delegate)));
  }

  private static Step createInitializeWebhookIdentityStep(CoreDelegate delegate, Step next) {
    return new InitializeWebhookIdentityStep(delegate, next);
  }

  private void completeBegin() {
    try {
      // start the conversion webhook REST server
      startRestServer();

      // start periodic recheck of CRD
      int recheckInterval = TuningParameters.getInstance().getMainTuning().domainNamespaceRecheckIntervalSeconds;
      delegate.scheduleWithFixedDelay(recheckCrd(), recheckInterval, recheckInterval, TimeUnit.SECONDS);

      markReadyAndStartLivenessThread();

    } catch (Throwable e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }
  }

  Runnable recheckCrd() {
    return () -> delegate.runSteps(createCRDRecheckSteps());
  }

  Step createCRDRecheckSteps() {
    return Step.chain(CrdHelper.createDomainCrdStep(delegate.getKubernetesVersion(), delegate.getProductVersion(),
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
    WebhookRestServer.create(new RestConfigImpl(new Certificates(delegate)));
    WebhookRestServer.getInstance().start(container);
  }

  private static void stopWebhookRestServer() {
    WebhookRestServer.getInstance().stop();
    WebhookRestServer.destroy();
  }

  @Override
  protected void logStartingLivenessMessage() {
    LOGGER.info(MessageKeys.STARTING_WEBHOOK_LIVENESS_THREAD);
  }

  // an interface to provide a hook for unit testing.
  interface NextStepFactory {
    Step createInitializationStep(CoreDelegate delegate, Step next);
  }
}
