// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.EventHelper;
import oracle.kubernetes.operator.helpers.WebhookHelper;
import oracle.kubernetes.operator.http.rest.BaseRestServer;
import oracle.kubernetes.operator.http.rest.RestConfig;
import oracle.kubernetes.operator.http.rest.RestConfigImpl;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.steps.InitializeWebhookIdentityStep;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.utils.Certificates;
import oracle.kubernetes.operator.webhooks.WebhookRestServer;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.PartialObjectMetadata;

import static oracle.kubernetes.common.CommonConstants.SECRETS_WEBHOOK_CERT;
import static oracle.kubernetes.common.CommonConstants.SECRETS_WEBHOOK_KEY;
import static oracle.kubernetes.operator.EventConstants.OPERATOR_WEBHOOK_COMPONENT;
import static oracle.kubernetes.operator.KubernetesConstants.CLUSTER_CRD_NAME;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_CRD_NAME;
import static oracle.kubernetes.operator.helpers.CrdHelper.createClusterCrdStep;
import static oracle.kubernetes.operator.helpers.CrdHelper.createDomainCrdStep;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.WEBHOOK_STARTUP_FAILED;
import static oracle.kubernetes.operator.helpers.EventHelper.createConversionWebhookEvent;
import static oracle.kubernetes.operator.helpers.EventHelper.createEventStep;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getWebhookNamespace;

/** A Domain Custom Resource Conversion Webhook for WebLogic Kubernetes Operator. */
public class WebhookMain extends BaseMain {

  private final WebhookMainDelegate conversionWebhookMainDelegate;
  private boolean warnedOfCrdAbsence;
  private final AtomicInteger crdPresenceCheckCount = new AtomicInteger(0);
  private final RestConfig restConfig = new RestConfigImpl(new Certificates(delegate));
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static NextStepFactory nextStepFactory = WebhookMain::createInitializeWebhookIdentityStep;

  static class WebhookMainDelegateImpl extends CoreDelegateImpl implements WebhookMainDelegate {
    public WebhookMainDelegateImpl(Properties buildProps, ScheduledExecutorService scheduledExecutorService) {
      super(buildProps, scheduledExecutorService);
    }

    private void logStartup() {
      LOGGER.info(MessageKeys.WEBHOOK_STARTED, buildVersion,
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

      main.stopDeployment(main::completeStop);
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
            createDomainCrdStep(delegate.getProductVersion(), certs),
            createClusterCrdStep(delegate.getProductVersion()),
            new CheckFailureAndCreateEventStep(),
            WebhookHelper.createValidatingWebhookConfigurationStep(certs)));
  }

  @Override
  protected Step createShutdownSteps() {
    return WebhookHelper.deleteValidatingWebhookConfigurationStep();
  }

  private static Step createInitializeWebhookIdentityStep(WebhookMainDelegate delegate, Step next) {
    return new InitializeWebhookIdentityStep(delegate, next);
  }

  void completeBegin() {
    try {
      startMetricsServer(container);
      startRestServer(container);

      // start periodic recheck of CRD
      int recheckInterval = TuningParameters.getInstance().getDomainNamespaceRecheckIntervalSeconds();
      delegate.scheduleWithFixedDelay(recheckCrd(), recheckInterval, recheckInterval, TimeUnit.SECONDS);

      markReadyAndStartLivenessThread();

    } catch (Exception e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
      EventHelper.EventData eventData = new EventHelper.EventData(WEBHOOK_STARTUP_FAILED, e.getMessage())
          .resourceName(OPERATOR_WEBHOOK_COMPONENT);
      createConversionWebhookEvent(eventData);

    }
  }

  void completeStop() {
    stopRestServer();
    stopMetricsServer();
  }

  Runnable recheckCrd() {
    return () -> delegate.runSteps(createCRDRecheckSteps());
  }

  Step createCRDRecheckSteps() {
    Step optimizedRecheckSteps = Step.chain(createDomainCRDPresenceCheck(), createClusterCRDPresenceCheck());
    try {
      String clusterCrdResourceVersion = getCrdResourceVersion(CLUSTER_CRD_NAME);
      String domainCrdResourceVersion = getCrdResourceVersion(DOMAIN_CRD_NAME);
      if (crdMissingOrChanged(clusterCrdResourceVersion, delegate.getClusterCrdResourceVersion())) {
        optimizedRecheckSteps = Step.chain(createClusterCrdStep(delegate.getProductVersion()), optimizedRecheckSteps);
      }
      if (crdMissingOrChanged(domainCrdResourceVersion, delegate.getDomainCrdResourceVersion())) {
        optimizedRecheckSteps = Step.chain(createDomainCrdStep(delegate.getProductVersion(),
            new Certificates(delegate)), optimizedRecheckSteps);
      }
      delegate.setDomainCrdResourceVersion(domainCrdResourceVersion);
      delegate.setClusterCrdResourceVersion(clusterCrdResourceVersion);
      return optimizedRecheckSteps;
    } catch (Exception e) {
      return createFullCRDRecheckSteps();
    }
  }

  private String getCrdResourceVersion(String crdName) throws ApiException {
    return Optional.of(new CallBuilder().readCRDMetadata(crdName))
        .map(PartialObjectMetadata::getMetadata).map(V1ObjectMeta::getResourceVersion).orElse(null);
  }

  private Step createFullCRDRecheckSteps() {
    return Step.chain(
        createDomainCrdStep(delegate.getProductVersion(), new Certificates(delegate)),
        createClusterCrdStep(delegate.getProductVersion()),
        createDomainCRDPresenceCheck(),
        createClusterCRDPresenceCheck());
  }

  private boolean crdMissingOrChanged(String crdResourceVersion, String cachedCrdResourceVersion) {
    return crdResourceVersion == null || !crdResourceVersion.equals(cachedCrdResourceVersion);
  }

  // Returns a step that verifies the presence of an installed domain CRD. It does this by attempting to list the
  // domains in the operator's namespace. That should succeed (although usually returning an empty list)
  // if the CRD is present.
  private Step createDomainCRDPresenceCheck() {
    return new CallBuilder().listDomainAsync(getWebhookNamespace(), new CrdPresenceResponseStep<>());
  }

  // Returns a step that verifies the presence of an installed cluster CRD. It does this by attempting to list the
  // domains in the operator's namespace. That should succeed (although usually returning an empty list)
  // if the CRD is present.
  private Step createClusterCRDPresenceCheck() {
    return new CallBuilder().listClusterAsync(getWebhookNamespace(), new CrdPresenceResponseStep<>());
  }

  // on failure, aborts the processing.
  private class  CrdPresenceResponseStep<L extends KubernetesListObject> extends DefaultResponseStep<L> {

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<L> callResponse) {
      clearExistingKubernetesNetworkException(packet);
      warnedOfCrdAbsence = false;
      crdPresenceCheckCount.set(0);
      return super.onSuccess(packet, callResponse);
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<L> callResponse) {
      if (crdPresenceCheckCount.getAndIncrement() < getCrdPresenceFailureRetryMaxCount()) {
        return doNext(this, packet);
      }
      if (!warnedOfCrdAbsence) {
        LOGGER.severe(MessageKeys.CRD_NOT_INSTALLED);
        warnedOfCrdAbsence = true;
      }
      return doNext(null, packet);
    }

    private int getCrdPresenceFailureRetryMaxCount() {
      return TuningParameters.getInstance().getCrdPresenceFailureRetryMaxCount();
    }
  }

  @Override
  protected BaseRestServer createRestServer() {
    return WebhookRestServer.create(restConfig);
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
        return doNext(createEventStep(new EventHelper.EventData(WEBHOOK_STARTUP_FAILED, failure.getMessage())
            .resourceName(OPERATOR_WEBHOOK_COMPONENT)
            .namespace(getWebhookNamespace())), packet);
      }
      return doNext(getNext(), packet);
    }
  }
}
