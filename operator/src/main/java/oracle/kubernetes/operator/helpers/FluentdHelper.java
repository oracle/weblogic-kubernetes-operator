// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
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
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.FluentdSpecification;

import static oracle.kubernetes.operator.helpers.StepContextConstants.FLUENTD_CONFIGMAP_NAME_SUFFIX;
import static oracle.kubernetes.operator.helpers.StepContextConstants.FLUENTD_CONFIGMAP_VOLUME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.FLUENTD_CONFIG_DATA_NAME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.FLUENTD_CONTAINER_NAME;

public class FluentdHelper {

  private FluentdHelper() {
  }

  /**
   * Add sidecar container for fluentd.
   * @param fluentdSpecification  FluentdSpecification.
   * @param containers  List of containers.
   * @param isJobPod  whether it belongs to the introspector job pod.
   * @param domain  Domain.
   */
  public static void addFluentdContainer(FluentdSpecification fluentdSpecification, List<V1Container> containers,
                                         DomainResource domain, boolean isJobPod) {

    V1Container fluentdContainer = new V1Container();

    fluentdContainer
        .name(FLUENTD_CONTAINER_NAME);

    if (fluentdSpecification.getContainerArgs() != null) {
      fluentdContainer.setArgs(fluentdSpecification.getContainerArgs());
    } else {
      fluentdContainer
          .addArgsItem("-c")
          .addArgsItem("/etc/fluent.conf");
    }

    fluentdContainer.setImage(fluentdSpecification.getImage());
    fluentdContainer.setImagePullPolicy(fluentdSpecification.getImagePullPolicy());
    fluentdContainer.setResources(fluentdSpecification.getResources());
    fluentdContainer.setSecurityContext(PodSecurityHelper.getDefaultContainerSecurityContext());

    if (fluentdSpecification.getContainerCommand() != null) {
      fluentdContainer.setCommand(fluentdSpecification.getContainerCommand());
    }

    addFluentdContainerEnvList(fluentdSpecification, fluentdContainer, domain, isJobPod);

    fluentdSpecification.getVolumeMounts()
        .forEach(fluentdContainer::addVolumeMountsItem);

    fluentdContainer.addVolumeMountsItem(createFluentdConfigmapVolumeMount());
    containers.add(fluentdContainer);
  }

  /**
   *  Return the default or user supplied fluentd configuration.
   * @param info DomainPresenceInfo.
   * @return fluentd configuration configmap.
   */
  public static V1ConfigMap getFluentdConfigMap(DomainPresenceInfo info) {
    StringBuilder fluentdConfBuilder = new StringBuilder();
    String domainUid = info.getDomainUid();
    String namespace = info.getNamespace();
    FluentdSpecification fluentdSpecification = info.getDomain().getFluentdSpecification();

    // Make sure every line has a next line character, otherwise fluentd will fail.
    if (fluentdSpecification.getFluentdConfiguration() != null) {
      fluentdConfBuilder.append(fluentdSpecification.getFluentdConfiguration());
    } else {
      fluentdConfBuilder.append("   <match fluent.**>\n");
      fluentdConfBuilder.append("      @type null\n");
      fluentdConfBuilder.append("    </match>\n");
      fluentdConfBuilder.append("    <source>\n");
      fluentdConfBuilder.append("      @type tail\n");
      fluentdConfBuilder.append("      path \"#{ENV['LOG_PATH']}\"\n");
      fluentdConfBuilder.append("      pos_file /tmp/server.log.pos\n");
      fluentdConfBuilder.append("      read_from_head true\n");
      fluentdConfBuilder.append("      tag \"#{ENV['DOMAIN_UID']}\"\n");
      fluentdConfBuilder.append("      # multiline_flush_interval 20s\n");
      fluentdConfBuilder.append("      <parse>\n");
      fluentdConfBuilder.append("        @type multiline\n");
      fluentdConfBuilder.append("        format_firstline /^####/\n");
      fluentdConfBuilder.append("        format1 /^####<(?<timestamp>(.*?))>/\n");
      fluentdConfBuilder.append("        format2 / <(?<level>(.*?))>/\n");
      fluentdConfBuilder.append("        format3 / <(?<subSystem>(.*?))>/\n");
      fluentdConfBuilder.append("        format4 / <(?<serverName>(.*?))>/\n");
      fluentdConfBuilder.append("        format5 / <(?<serverName2>(.*?))>/\n");
      fluentdConfBuilder.append("        format6 / <(?<threadName>(.*?))>/\n");
      fluentdConfBuilder.append("        format7 / <(?<info1>(.*?))>/\n");
      fluentdConfBuilder.append("        format8 / <(?<info2>(.*?))>/\n");
      fluentdConfBuilder.append("        format9 / <(?<info3>(.*?))>/\n");
      fluentdConfBuilder.append("        format10 / <(?<sequenceNumber>(.*?))>/\n");
      fluentdConfBuilder.append("        format11 / <(?<severity>(.*?))>/\n");
      fluentdConfBuilder.append("        format12 / <(?<messageID>(.*?))>/\n");
      fluentdConfBuilder.append("        format13 / <(?<message>(.*?))>/\n");
      fluentdConfBuilder.append("        # use the timestamp field in the message as the timestamp\n");
      fluentdConfBuilder.append("        # instead of the time the message was actually read\n");
      fluentdConfBuilder.append("        time_key timestamp\n");
      fluentdConfBuilder.append("        keep_time_key true\n");
      fluentdConfBuilder.append("      </parse>\n");
      fluentdConfBuilder.append("    </source>\n");

      if (Boolean.TRUE.equals(fluentdSpecification.getWatchIntrospectorLogs())) {

        fluentdConfBuilder.append("    <source>\n");
        fluentdConfBuilder.append("      @type tail\n");
        fluentdConfBuilder.append("      path \"#{ENV['INTROSPECTOR_OUT_PATH']}\"\n");
        fluentdConfBuilder.append("      pos_file /tmp/introspector.log.pos\n");
        fluentdConfBuilder.append("      read_from_head true\n");
        fluentdConfBuilder.append("      tag \"#{ENV['DOMAIN_UID']}-introspector\"\n");
        fluentdConfBuilder.append("      # multiline_flush_interval 20s\n");
        fluentdConfBuilder.append("      <parse>\n");
        fluentdConfBuilder.append("        @type multiline\n");
        fluentdConfBuilder.append("        format_firstline /@\\[/\n");
        fluentdConfBuilder.append("        format1 /^@\\[(?<timestamp>.*)\\]\\["
                + "(?<filesource>.*?)\\]\\[(?<level>.*?)\\](?<message>.*)/\n");
        fluentdConfBuilder.append("        # use the timestamp field in the message as the timestamp\n");
        fluentdConfBuilder.append("        # instead of the time the message was actually read\n");
        fluentdConfBuilder.append("        time_key timestamp\n");
        fluentdConfBuilder.append("        keep_time_key true\n");
        fluentdConfBuilder.append("      </parse>\n");
        fluentdConfBuilder.append("     </source>\n");
        fluentdConfBuilder.append("    <match \"#{ENV['DOMAIN_UID']}-introspector\">\n");
        fluentdConfBuilder.append("      @type elasticsearch\n");
        fluentdConfBuilder.append("      host \"#{ENV['ELASTICSEARCH_HOST']}\"\n");
        fluentdConfBuilder.append("      port \"#{ENV['ELASTICSEARCH_PORT']}\"\n");
        fluentdConfBuilder.append("      user \"#{ENV['ELASTICSEARCH_USER']}\"\n");
        fluentdConfBuilder.append("      password \"#{ENV['ELASTICSEARCH_PASSWORD']}\"\n");
        fluentdConfBuilder.append("      index_name \"#{ENV['DOMAIN_UID']}\"\n");
        fluentdConfBuilder.append("      suppress_type_name true\n");
        fluentdConfBuilder.append("      type_name introspectord\n");
        fluentdConfBuilder.append("      logstash_format true\n");
        fluentdConfBuilder.append("      logstash_prefix introspectord\n");
        fluentdConfBuilder.append("      # inject the @timestamp special field (as type time) into the record\n");
        fluentdConfBuilder.append("      # so you will be able to do time based queries.\n");
        fluentdConfBuilder.append("      # not to be confused with timestamp which is of type string!!!\n");
        fluentdConfBuilder.append("      include_timestamp true\n");
        fluentdConfBuilder.append("    </match>\n");


      }

      fluentdConfBuilder.append("    <match \"#{ENV['DOMAIN_UID']}\">\n");
      fluentdConfBuilder.append("      @type elasticsearch\n");
      fluentdConfBuilder.append("      host \"#{ENV['ELASTICSEARCH_HOST']}\"\n");
      fluentdConfBuilder.append("      port \"#{ENV['ELASTICSEARCH_PORT']}\"\n");
      fluentdConfBuilder.append("      user \"#{ENV['ELASTICSEARCH_USER']}\"\n");
      fluentdConfBuilder.append("      password \"#{ENV['ELASTICSEARCH_PASSWORD']}\"\n");
      fluentdConfBuilder.append("      index_name \"#{ENV['DOMAIN_UID']}\"\n");
      fluentdConfBuilder.append("      suppress_type_name true\n");
      fluentdConfBuilder.append("      type_name fluentd\n");
      fluentdConfBuilder.append("      logstash_format true\n");
      fluentdConfBuilder.append("      logstash_prefix fluentd\n");
      fluentdConfBuilder.append("      # inject the @timestamp special field (as type time) into the record\n");
      fluentdConfBuilder.append("      # so you will be able to do time based queries.\n");
      fluentdConfBuilder.append("      # not to be confused with timestamp which is of type string!!!\n");
      fluentdConfBuilder.append("      include_timestamp true\n");
      fluentdConfBuilder.append("    </match>");
    }

    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.domainUID", domainUid);
    labels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");

    Map<String, String> data = new HashMap<>();
    data.put(FLUENTD_CONFIG_DATA_NAME, fluentdConfBuilder.toString());

    V1ObjectMeta meta = new V1ObjectMeta()
        .name(domainUid + FLUENTD_CONFIGMAP_NAME_SUFFIX)
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

  private static void addFluentdContainerEnvList(
      FluentdSpecification fluentdSpecification, V1Container fluentdContainer,
      DomainResource domain, boolean isJobPod) {

    if (fluentdSpecification.getElasticSearchCredentials() != null) {
      addFluentdContainerELSCredEnv(fluentdSpecification, fluentdContainer, "ELASTICSEARCH_HOST",
          "elasticsearchhost");
      addFluentdContainerELSCredEnv(fluentdSpecification, fluentdContainer, "ELASTICSEARCH_PORT",
          "elasticsearchport");
      addFluentdContainerELSCredEnv(fluentdSpecification, fluentdContainer, "ELASTICSEARCH_USER",
          "elasticsearchuser");
      addFluentdContainerELSCredEnv(fluentdSpecification, fluentdContainer, "ELASTICSEARCH_PASSWORD",
          "elasticsearchpassword");
    }

    addFluentdContainerEnvItem(fluentdSpecification, fluentdContainer, "FLUENT_ELASTICSEARCH_SED_DISABLE",
        "true",
        false);
    addFluentdContainerEnvItem(fluentdSpecification, fluentdContainer, "FLUENTD_CONF", FLUENTD_CONFIG_DATA_NAME,
        false);
    addFluentdContainerEnvItem(fluentdSpecification, fluentdContainer, "DOMAIN_UID",
        "metadata.labels['weblogic.domainUID']",
        true);
    addFluentdContainerEnvItem(fluentdSpecification, fluentdContainer, "SERVER_NAME",
        "metadata.labels['weblogic.serverName']",
        true);

    if (LogHomeLayoutType.FLAT.equals(domain.getLogHomeLayout())) {
      addFluentdContainerEnvItem(fluentdSpecification, fluentdContainer, "LOG_PATH",
          domain.getEffectiveLogHome() + "/$(SERVER_NAME).log",
          false);
    } else {
      addFluentdContainerEnvItem(fluentdSpecification, fluentdContainer, "LOG_PATH",
          domain.getEffectiveLogHome() + "/servers/$(SERVER_NAME)/logs/$(SERVER_NAME).log",
          false);
    }

    // Always add this because we only have one fluentd configmap, and it may contain the
    // introspector log parser config. If this environment variable is not set then the managed server
    // fluentd will not run. If the file is not there, then there won't be any problems.  Set it to
    //  a dummy name for non job pod fluentd container
    String introspectorJobScript = "/introspector_script.out";
    if (!isJobPod) {
      introspectorJobScript = "not_introspector_script.outx";
    }
    addFluentdContainerEnvItem(fluentdSpecification, fluentdContainer, "INTROSPECTOR_OUT_PATH",
        domain.getEffectiveLogHome() + introspectorJobScript,
        false);


    fluentdSpecification.getEnv()
        .forEach(fluentdContainer::addEnvItem);

  }

  private static void addFluentdContainerEnvItem(FluentdSpecification fluentdSpecification,
                                                 V1Container fluentdContainer,  String name, String value,
                                                 boolean useValueFromFieldRef) {
    if (!hasFluentdContainerEnv(fluentdSpecification, name)) {
      V1EnvVar item;
      if (!useValueFromFieldRef) {
        item = new V1EnvVar().name(name).value(value);
      } else {
        item = new V1EnvVar().name(name)
            .valueFrom(new V1EnvVarSource().fieldRef(new V1ObjectFieldSelector().fieldPath(value)));
      }
      fluentdContainer.addEnvItem(item);
    }

  }

  private static void addFluentdContainerELSCredEnv(FluentdSpecification fluentdSpecification,
                                                    V1Container fluentdContainer, String envName, String keyName) {
    if (!hasFluentdContainerEnv(fluentdSpecification, envName)) {
      boolean isOptional = envName.equals("ELASTICSEARCH_USER") || envName.equals("ELASTICSEARCH_PASSWORD");
      V1SecretKeySelector keySelector = new V1SecretKeySelector()
          .key(keyName)
          .optional(isOptional)
          .name(fluentdSpecification.getElasticSearchCredentials());
      V1EnvVarSource source = new V1EnvVarSource()
          .secretKeyRef(keySelector);
      V1EnvVar envItem = new V1EnvVar()
          .name(envName)
          .valueFrom(source);
      fluentdContainer.addEnvItem(envItem);
    }
  }

  private static boolean hasFluentdContainerEnv(FluentdSpecification fluentdSpecification, String name) {
    V1EnvVar containeerEnv = fluentdSpecification.getEnv().stream()
        .filter(c -> c.getName().equals(name))
        .findFirst()
        .orElse(null);
    return containeerEnv != null;
  }

  private static V1VolumeMount createFluentdConfigmapVolumeMount() {
    return new V1VolumeMount()
        .name(FLUENTD_CONFIGMAP_VOLUME)
        .mountPath("/fluentd/etc/fluentd.conf")
        .subPath(FLUENTD_CONFIG_DATA_NAME);
  }


}
