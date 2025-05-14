// Copyright (c) 2024, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1EnvVarSource;
import io.kubernetes.client.openapi.models.V1ObjectFieldSelector;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1SecretKeySelector;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.LogHomeLayoutType;
import oracle.kubernetes.operator.processing.EffectiveServerSpec;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.FluentbitSpecification;

import static oracle.kubernetes.common.CommonConstants.TMPDIR_MOUNTS_PATH;
import static oracle.kubernetes.common.CommonConstants.TMPDIR_VOLUME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.FLUENTBIT_CONFIGMAP_NAME_SUFFIX;
import static oracle.kubernetes.operator.helpers.StepContextConstants.FLUENTBIT_CONFIGMAP_VOLUME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.FLUENTBIT_CONFIG_DATA_NAME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.FLUENTBIT_CONTAINER_NAME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.FLUENTBIT_PARSER_CONFIG_DATA_NAME;

public class FluentbitHelper {
  private FluentbitHelper() {
  }

  /**
   * Add sidecar container for fluentbit.
   * @param serverSpec Server specification
   * @param fluentbitSpecification  FluentbitSpecification.
   * @param containers  List of containers.
   * @param isJobPod  whether it belongs to the introspector job pod.
   * @param domain  Domain.
   */
  public static void addFluentbitContainer(EffectiveServerSpec serverSpec,
                                           FluentbitSpecification fluentbitSpecification, List<V1Container> containers,
                                           DomainResource domain, boolean isJobPod, boolean isReadOnlyRootFileSystem) {
    V1Container fluentbitContainer = new V1Container();

    fluentbitContainer.name(FLUENTBIT_CONTAINER_NAME);

    if (fluentbitSpecification.getContainerArgs() != null) {
      fluentbitContainer.setArgs(fluentbitSpecification.getContainerArgs());
    } else {
      fluentbitContainer
          .addArgsItem("-c")
          .addArgsItem("/etc/fluent.conf");
    }

    fluentbitContainer.setImage(fluentbitSpecification.getImage());
    fluentbitContainer.setImagePullPolicy(fluentbitSpecification.getImagePullPolicy());
    fluentbitContainer.setResources(fluentbitSpecification.getResources());
    fluentbitContainer.setSecurityContext(serverSpec.getContainerSecurityContext());

    if (fluentbitSpecification.getContainerCommand() != null) {
      fluentbitContainer.setCommand(fluentbitSpecification.getContainerCommand());
    }

    addFluentbitContainerEnvList(fluentbitSpecification, fluentbitContainer, domain, isJobPod);

    fluentbitSpecification.getVolumeMounts().forEach(fluentbitContainer::addVolumeMountsItem);

    fluentbitContainer.addVolumeMountsItem(createFluentbitConfigmapVolumeMount());

    if (isReadOnlyRootFileSystem) {
      fluentbitContainer.addVolumeMountsItem(new V1VolumeMount().name(TMPDIR_VOLUME).mountPath(TMPDIR_MOUNTS_PATH));
    }

    containers.add(fluentbitContainer);
  }

  /**
   *  Return the default or user supplied fluentbit configuration.
   * @param info DomainPresenceInfo.
   * @return fluentbit configuration configmap.
   */
  public static V1ConfigMap getFluentbitConfigMap(DomainPresenceInfo info) {
    StringBuilder fluentbitConfBuilder = new StringBuilder();
    StringBuilder parserConfBuilder = new StringBuilder();
    String domainUid = info.getDomainUid();
    String namespace = info.getNamespace();
    FluentbitSpecification fluentbitSpecification = info.getDomain().getFluentbitSpecification();

    // Make sure every line has a next line character, otherwise fluentbit will fail.
    if (fluentbitSpecification.getFluentbitConfiguration() != null
        && fluentbitSpecification.getParserConfiguration() != null) {
      fluentbitConfBuilder.append(fluentbitSpecification.getFluentbitConfiguration());
      parserConfBuilder.append(fluentbitSpecification.getParserConfiguration());
    } else {
      fluentbitConfBuilder.append("[SERVICE]\n");
      fluentbitConfBuilder.append("    parsers_file parsers.conf\n");
      fluentbitConfBuilder.append("[INPUT]\n");
      fluentbitConfBuilder.append("    Name tail\n");
      fluentbitConfBuilder.append("    Path \"${LOG_PATH}\"\n");
      fluentbitConfBuilder.append("    Read_From_Head true\n");
      fluentbitConfBuilder.append("    multiline_parser \n");
      fluentbitConfBuilder.append("    Tag \"${DOMAIN_UID}\"\n");
      fluentbitConfBuilder.append("[FILTER]\n");
      fluentbitConfBuilder.append("    Match \"${DOMAIN_UID}\"\n");
      fluentbitConfBuilder.append("    Name parser\n");
      fluentbitConfBuilder.append("    preserve_key true\n");
      fluentbitConfBuilder.append("    parser \"${DOMAIN_UID}\"\n");
      fluentbitConfBuilder.append("    key_name log\n");
      fluentbitConfBuilder.append("[OUTPUT]");
      fluentbitConfBuilder.append("    name stdout\n");
      fluentbitConfBuilder.append("    Match *\n");

      parserConfBuilder.append("[MULTILINE_PARSER]\n");
      parserConfBuilder.append("    Name multiline-" + "\"${DOMAIN_UID}\"\n");
      parserConfBuilder.append("    type regex\n");
      parserConfBuilder.append("    rule      \"start_state\"   \"/^####(.*)/\"       \"cont\"\n");
      parserConfBuilder.append("    rule      \"cont\"          \"/^(?!####)(.*)/\"    \"cont\"\n");
      parserConfBuilder.append("[PARSER]");
      parserConfBuilder.append("    Name \"${DOMAIN_UID}\"\n");
      parserConfBuilder.append("    Format regex\n");
      parserConfBuilder.append("    Time_Key timestamp\n");
      parserConfBuilder.append("    regex /^####<(?<timestamp>(.*?))> <(?<level>(.*?))> <(?<subSystem>(.*?))>"
          + " <(?<serverName>(.*?))> <(?<serverName2>(.*?))>"
          + " <(?<threadName>(.*?))> <(?<info1>(.*?))> <(?<info2>(.*?))> <(?<info3>(.*?))>"
          + " <(?<sequenceNumber>(.*?))> <(?<severity>(.*?))> <(?<messageID>(.*?))> <(?<message>((?m).*?))>/");
    }

    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.domainUID", domainUid);
    labels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");

    Map<String, String> data = new HashMap<>();
    data.put(FLUENTBIT_CONFIG_DATA_NAME, fluentbitConfBuilder.toString());
    data.put(FLUENTBIT_PARSER_CONFIG_DATA_NAME, parserConfBuilder.toString());

    V1ObjectMeta meta = new V1ObjectMeta()
        .name(domainUid + FLUENTBIT_CONFIGMAP_NAME_SUFFIX)
        .labels(labels)
        .namespace(namespace);

    DomainResource domain = info.getDomain();
    if (domain != null) {
      V1ObjectMeta domainMetadata = domain.getMetadata();
      meta.addOwnerReferencesItem(
        new V1OwnerReference()
            .apiVersion(domain.getApiVersion())
            .kind(domain.getKind())
            .name(domainMetadata.getName())
            .uid(domainMetadata.getUid())
            .controller(true));
    }

    return new V1ConfigMap()
      .kind("ConfigMap")
      .apiVersion("v1")
      .metadata(meta).data(data);
  }

  private static void addFluentbitContainerEnvList(
      FluentbitSpecification fluentbitSpecification, V1Container fluentbitContainer,
      DomainResource domain, boolean isJobPod) {
    if (fluentbitSpecification.getElasticSearchCredentials() != null) {
      addFluentbitContainerELSCredEnv(fluentbitSpecification, fluentbitContainer,
          "ELASTICSEARCH_HOST", "elasticsearchhost");
      addFluentbitContainerELSCredEnv(fluentbitSpecification, fluentbitContainer, "ELASTICSEARCH_PORT",
          "elasticsearchport");
      addFluentbitContainerELSCredEnv(fluentbitSpecification, fluentbitContainer, "ELASTICSEARCH_USER",
          "elasticsearchuser");
      addFluentbitContainerELSCredEnv(fluentbitSpecification, fluentbitContainer, "ELASTICSEARCH_PASSWORD",
          "elasticsearchpassword");
    }

    addFluentbitContainerEnvItem(fluentbitSpecification, fluentbitContainer, "FLUENT_ELASTICSEARCH_SED_DISABLE",
        "true",
        false);
    addFluentbitContainerEnvItem(fluentbitSpecification, fluentbitContainer, "FLUENT_CONF",
        FLUENTBIT_CONFIG_DATA_NAME, false);
    addFluentbitContainerEnvItem(fluentbitSpecification, fluentbitContainer, "DOMAIN_UID",
        "metadata.labels['weblogic.domainUID']",
        true);
    addFluentbitContainerEnvItem(fluentbitSpecification, fluentbitContainer, "SERVER_NAME",
        "metadata.labels['weblogic.serverName']",
        true);

    if (LogHomeLayoutType.FLAT.equals(domain.getLogHomeLayout())) {
      addFluentbitContainerEnvItem(fluentbitSpecification, fluentbitContainer, "LOG_PATH",
          domain.getEffectiveLogHome() + "/$(SERVER_NAME).log",
          false);
    } else {
      addFluentbitContainerEnvItem(fluentbitSpecification, fluentbitContainer, "LOG_PATH",
          domain.getEffectiveLogHome() + "/servers/$(SERVER_NAME)/logs/$(SERVER_NAME).log",
          false);
    }

    // Always add this because we only have one fluentbit configmap, and it may contain the
    // introspector log parser config. If this environment variable is not set then the managed server
    // fluentbit will not run. If the file is not there, then there won't be any problems.  Set it to
    //  a dummy name for non job pod fluentbit container
    String introspectorJobScript = "/introspector_script.out";
    if (!isJobPod) {
      introspectorJobScript = "not_introspector_script.outx";
    }
    addFluentbitContainerEnvItem(fluentbitSpecification, fluentbitContainer, "INTROSPECTOR_OUT_PATH",
        domain.getEffectiveLogHome() + introspectorJobScript,
        false);

    fluentbitSpecification.getEnv().forEach(fluentbitContainer::addEnvItem);
  }

  private static void addFluentbitContainerEnvItem(FluentbitSpecification fluentbitSpecification,
                                                   V1Container fluentbitContainer,  String name, String value,
                                                   boolean useValueFromFieldRef) {
    if (!hasFluentbitContainerEnv(fluentbitSpecification, name)) {
      V1EnvVar item;
      if (!useValueFromFieldRef) {
        item = new V1EnvVar().name(name).value(value);
      } else {
        item = new V1EnvVar().name(name)
            .valueFrom(new V1EnvVarSource().fieldRef(new V1ObjectFieldSelector().fieldPath(value)));
      }
      fluentbitContainer.addEnvItem(item);
    }
  }

  private static void addFluentbitContainerELSCredEnv(FluentbitSpecification fluentbitSpecification,
                                                      V1Container fluentbitContainer, String envName, String keyName) {
    if (!hasFluentbitContainerEnv(fluentbitSpecification, envName)) {
      boolean isOptional = envName.equals("ELASTICSEARCH_USER") || envName.equals("ELASTICSEARCH_PASSWORD");
      V1SecretKeySelector keySelector = new V1SecretKeySelector()
          .key(keyName)
          .optional(isOptional)
          .name(fluentbitSpecification.getElasticSearchCredentials());
      V1EnvVarSource source = new V1EnvVarSource()
          .secretKeyRef(keySelector);
      V1EnvVar envItem = new V1EnvVar()
          .name(envName)
          .valueFrom(source);
      fluentbitContainer.addEnvItem(envItem);
    }
  }

  private static boolean hasFluentbitContainerEnv(FluentbitSpecification fluentbitSpecification, String name) {
    V1EnvVar containeerEnv = fluentbitSpecification.getEnv().stream()
        .filter(c -> c.getName().equals(name))
        .findFirst()
        .orElse(null);
    return containeerEnv != null;
  }

  private static V1VolumeMount createFluentbitConfigmapVolumeMount() {
    return new V1VolumeMount()
        .name(FLUENTBIT_CONFIGMAP_VOLUME)
        .mountPath("/fluent-bit/etc/");
  }
}

