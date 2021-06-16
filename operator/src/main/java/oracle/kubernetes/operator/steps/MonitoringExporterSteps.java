// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.SecretHelper;
import oracle.kubernetes.operator.http.HttpResponseStep;
import oracle.kubernetes.operator.logging.LoggingContext;
import oracle.kubernetes.operator.wlsconfig.PortDetails;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.MonitoringExporterConfiguration;

import static oracle.kubernetes.operator.ProcessingConstants.SERVER_NAME;
import static oracle.kubernetes.operator.steps.HttpRequestProcessing.createRequestStep;

public class MonitoringExporterSteps {

  /** Time in seconds to wait to recheck for ready state. **/
  private static final int READY_RECHECK_INTERVAL = 2;

  /**
   * Creates a step to initiate processing for all servers in the domain for which a configuration is defined,
   * checking the configuration of each exporter sidecar and updating it if necessary.
   *
   * Expects packet to contain DomainPresenceInfo and:
   *     DOMAIN_TOPOLOGY              the domain configuration
   *     AUTHORIZATION_HEADER_FACTORY a factory to create an http authentication header
   */
  public static Step updateExporterSidecars() {
    return new Step() {
      @Override
      public NextAction apply(Packet packet) {
        return doNext(updateExportersWithConfiguration(packet), packet);
      }

      private Step updateExportersWithConfiguration(Packet packet) {
        final Collection<String> serverNames = getExporterEnabledServerNames(packet);
        if (serverNames.isEmpty()) {
          return getNext();
        } else {
          return Step.chain(
                SecretHelper.createAuthorizationSourceStep(),
                RunInParallel.perServer(serverNames, ConfigurationVerificationStartStep::new));
        }
      }

      private Collection<String> getExporterEnabledServerNames(Packet packet) {
        return getAllServerNames(packet).stream()
              .filter(name -> hasExporterConfiguration(packet, name))
              .filter(name -> hasExporterSidecar(packet, name))
              .collect(Collectors.toList());
      }

      private Collection<String> getAllServerNames(Packet packet) {
        return DomainPresenceInfo.fromPacket(packet)
              .map(DomainPresenceInfo::getServerNames)
              .orElse(Collections.emptyList());
      }

      @SuppressWarnings("unused") // Change this to handle per-cluster configuration
      private boolean hasExporterConfiguration(Packet packet, String serverName) {
        return DomainPresenceInfo.fromPacket(packet)
              .map(DomainPresenceInfo::getDomain)
              .map(Domain::getMonitoringExporterConfiguration)
              .isPresent();
      }

      private boolean hasExporterSidecar(Packet packet, String serverName) {
        return DomainPresenceInfo.fromPacket(packet)
              .map(dpi -> dpi.getServerPod(serverName))
              .map(this::hasExporterSidecar)
              .orElse(false);
      }

      private boolean hasExporterSidecar(V1Pod pod) {
        return Optional.ofNullable(pod)
              .map(V1Pod::getSpec)
              .map(V1PodSpec::getContainers)
              .orElse(Collections.emptyList())
              .stream()
              .anyMatch(c -> c.getName().equals(KubernetesConstants.EXPORTER_CONTAINER_NAME));
      }
    };
  }

  /**
   * Creates a step to check the configuration for a single server pod sidecar and update it if necessary.
   *
   * Expects packet to contain DomainPresenceInfo and:
   *     SERVER_NAME                  the name of the server for which processing is to be done
   *     AUTHORIZATION_HEADER_FACTORY a factory to create an http authentication header
   */
  static Step createConfigurationTestAndUpdateSteps() {
    return new ConfigurationQueryStep();
  }

  /**
   * Creates a step to send a PUT request to the exporter sidecar with the new configuration.
   *
   * Expects packet to contain DomainPresenceInfo and:
   *     SERVER_NAME                  the name of the server for which processing is to be done
   *     AUTHORIZATION_HEADER_FACTORY a factory to create an http authentication header
   */
  static Step createConfigurationUpdateStep() {
    return new ConfigurationUpdateStep();
  }

  //------------ Step to start processing for a server

  private static class ConfigurationVerificationStartStep extends Step {

    private final String serverName;

    ConfigurationVerificationStartStep(String serverName) {
      this.serverName = serverName;
    }

    @Override
    public NextAction apply(Packet packet) {
      if (PodHelper.isDeleting(getServerPod(packet))) {
        return doNext(packet);
      } else if (PodHelper.isReady(getServerPod(packet))) {
        packet.put(SERVER_NAME, serverName);
        return doNext(new ConfigurationQueryStep(), packet);
      } else {
        return doDelay(this, packet, READY_RECHECK_INTERVAL, TimeUnit.SECONDS);
      }
    }

    private V1Pod getServerPod(Packet packet) {
      return DomainPresenceInfo.fromPacket(packet).map(dpi -> dpi.getServerPod(serverName)).orElseThrow();
    }
  }

  //------------ Step to query existing configuration

  private static class ConfigurationQueryStep extends Step {

    // use server name to locate pod and service
    // send get request
    // compare to desired configuration
    // if not match, response should run update step

    @Override
    public NextAction apply(Packet packet) {
      ExporterRequestProcessing processing = new ExporterRequestProcessing(packet);

      return doNext(createRequestStep(processing.createConfigurationQueryRequest(),
            new ConfigurationQueryResponseStep(getNext())), packet);
    }
  }

  private static class ConfigurationQueryResponseStep extends HttpResponseStep {

    public ConfigurationQueryResponseStep(Step next) {
      super(next);
    }

    @Override
    public NextAction onSuccess(Packet packet, HttpResponse<String> response) {
      if (hasUpToDateConfiguration(packet, response)) {
        return doNext(packet);
      } else {
        return doNext(new ConfigurationUpdateStep(), packet);
      }
    }

    private boolean hasUpToDateConfiguration(Packet packet, HttpResponse<String> response) {
      return getExporterConfiguration(packet).equals(getReportedConfiguration(response));
    }

    // todo REG-> refactor for clarity
    private String getReportedConfiguration(HttpResponse<String> response) {
      return MonitoringExporterConfiguration.createFromYaml(withoutHtml(response.body())).asJsonString();
    }

    private String withoutHtml(String body) {
      return getStringBetween(body, "<pre>", "</pre>");
    }

    @SuppressWarnings("SameParameterValue")
    private String getStringBetween(String string, String prefix, String suffix) {
      return string.substring(string.indexOf(prefix) + prefix.length(), string.indexOf(suffix));
    }

    @Override
    public NextAction onFailure(Packet packet, HttpResponse<String> response) {
      return doNext(packet);
    }
  }

  private static String getExporterConfiguration(Packet packet) {
    return DomainPresenceInfo.fromPacket(packet)
          .map(DomainPresenceInfo::getDomain)
          .map(Domain::getMonitoringExporterConfiguration)
          .map(MonitoringExporterConfiguration::asJsonString)
          .orElse("");
  }

  //------------ Step to send a configuration update

  private static class ConfigurationUpdateStep extends Step {

    @Override
    public NextAction apply(Packet packet) {
      ExporterRequestProcessing processing = new ExporterRequestProcessing(packet);

      return doNext(createRequestStep(processing.createConfigurationUpdateRequest(packet),
            new ConfigurationUpdateResponseStep(getNext())), packet);
    }
  }

  private static class ExporterRequestProcessing extends HttpRequestProcessing {
    private final DomainPresenceInfo info;

    ExporterRequestProcessing(Packet packet) {
      super(packet, getServerService(packet), getServerPod(packet));
      info = packet.getSpi(DomainPresenceInfo.class);
    }

    private static V1Service getServerService(Packet packet) {
      final String serverName = packet.getValue(SERVER_NAME);
      return DomainPresenceInfo.fromPacket(packet).map(dpi -> dpi.getServerService(serverName)).orElseThrow();
    }

    private static V1Pod getServerPod(Packet packet) {
      final String serverName = packet.getValue(SERVER_NAME);
      return DomainPresenceInfo.fromPacket(packet).map(dpi -> dpi.getServerPod(serverName)).orElseThrow();
    }

    @Override
    protected PortDetails getPortDetails() {
      return new PortDetails(getExporterPort(), false);
    }

    private Integer getExporterPort() {
      return getServicePorts().stream()
            .filter(this::isExporterPort)
            .findFirst()
            .map(V1ServicePort::getPort)
            .orElseThrow();
    }

    @Nonnull
    private List<V1ServicePort> getServicePorts() {
      return Optional.ofNullable(getService().getSpec()).map(V1ServiceSpec::getPorts).orElse(Collections.emptyList());
    }

    private boolean isExporterPort(V1ServicePort servicePort) {
      return getMetricsPortName().equals(servicePort.getName());
    }

    Domain getDomain() {
      return info.getDomain();
    }

    private String getMetricsPortName() {
      return getDomain().isIstioEnabled() ? "tcp-metrics" : "metrics";
    }

    private HttpRequest createConfigurationQueryRequest() {
      return createRequestBuilder(getConfigurationQueryUrl()).GET().build();
    }

    private String getConfigurationQueryUrl() {
      return getServiceUrl() + "/";
    }

    private HttpRequest createConfigurationUpdateRequest(Packet packet) {
      return createRequestBuilder(getConfigurationUpdateUrl())
            .PUT(HttpRequest.BodyPublishers.ofString(getExporterConfiguration(packet)))
            .build();
    }

    private String getConfigurationUpdateUrl() {
      return getServiceUrl() + "/configuration";
    }
  }

  private static class ConfigurationUpdateResponseStep extends HttpResponseStep {

    public ConfigurationUpdateResponseStep(Step next) {
      super(next);
    }

    @Override
    public NextAction onSuccess(Packet packet, HttpResponse<String> response) {
      return doNext(packet);
    }

    @Override
    public NextAction onFailure(Packet packet, HttpResponse<String> response) {
      return doNext(packet);
    }
  }

  /**
   * Given a list of server names and a method that creates steps for the server,
   * will create the appropriate steps and run them in parallel, waiting for all to complete
   * before proceeding.
   * 
   * The packet is expected to contain a DomainPresenceInfo.
   */
  static class RunInParallel extends Step {

    final Function<String, Step> stepFactory;
    private final Collection<String> serverNames;

    RunInParallel(Collection<String> serverNames, Function<String, Step> stepFactory) {
      this.serverNames = serverNames;
      this.stepFactory = stepFactory;
    }

    static Step perServer(Collection<String> serverNames, Function<String, Step> stepFactory) {
      return new RunInParallel(serverNames, stepFactory);
    }

    @Override
    protected String getDetail() {
      return Optional.ofNullable(serverNames).map(d -> String.join(",", d)).orElse(null);
    }

    @Override
    public NextAction apply(Packet packet) {
      if (serverNames == null) {
        return doNext(packet);
      } else {
        Collection<StepAndPacket> startDetails = new ArrayList<>();

        try (LoggingContext ignored = LoggingContext.setThreadContext().namespace(getNamespace(packet))) {
          for (String serverName : serverNames) {
            startDetails.add(new StepAndPacket(stepFactory.apply(serverName), packet.copy()));
          }
        }
        return doForkJoin(getNext(), packet, startDetails);
      }
    }

    private String getNamespace(Packet packet) {
      return DomainPresenceInfo.fromPacket(packet).map(DomainPresenceInfo::getNamespace).orElseThrow();
    }
  }
}
