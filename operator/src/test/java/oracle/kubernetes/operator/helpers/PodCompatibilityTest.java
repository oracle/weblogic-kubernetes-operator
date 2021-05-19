// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Arrays;
import java.util.Objects;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Probe;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.operator.LabelConstants;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.helpers.CompatibilityCheck.CompatibilityScope.DOMAIN;
import static oracle.kubernetes.operator.helpers.CompatibilityCheck.CompatibilityScope.POD;
import static oracle.kubernetes.operator.helpers.CompatibilityCheck.CompatibilityScope.UNKNOWN;
import static oracle.kubernetes.operator.helpers.PodHelper.AdminPodStepContext.INTERNAL_OPERATOR_CERT_ENV;
import static oracle.kubernetes.weblogic.domain.model.ServerEnvVars.DOMAIN_HOME;
import static oracle.kubernetes.weblogic.domain.model.ServerEnvVars.LOG_HOME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.stringContainsInOrder;

public class PodCompatibilityTest {
  @Test
  public void whenImagesDontMatch_createErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().image("abcde"), new V1Container().image("cdefg"));

    assertThat(
        compatibility.getIncompatibility(),
        both(containsString("abcde")).and(containsString("cdefg")));
  }

  @Test
  public void whenLivenessProbesDontMatch_createErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container()
                .livenessProbe(
                    new V1Probe().initialDelaySeconds(1).timeoutSeconds(5).periodSeconds(3)),
            new V1Container()
                .livenessProbe(
                    new V1Probe().initialDelaySeconds(1).timeoutSeconds(2).periodSeconds(3)));

    assertThat(
        compatibility.getIncompatibility(),
        both(containsString("timeout")).and(containsString("2")));
  }

  @Test
  public void whenResourcesDontMatch_createErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container()
                .resources(new V1ResourceRequirements().putLimitsItem("time", new Quantity("20"))),
            new V1Container());

    assertThat(
        compatibility.getIncompatibility(),
        both(containsString("time")).and(containsString("limits")));
  }

  @Test
  public void whenPortsDontMatch_createErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().addPortsItem(new V1ContainerPort().containerPort(1100)),
            new V1Container().addPortsItem(new V1ContainerPort().containerPort(1234)));

    assertThat(
        compatibility.getIncompatibility(),
        both(containsString("1100")).and(not(containsString("1234"))));
  }

  @Test
  public void whenEnvVarsDontMatch_createErrorMessage() {
    String name = "aa";
    String value = "bb";
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().addEnvItem(envVar(name, value)),
            new V1Container().addEnvItem(envVar(name, "cc")));

    assertThat(
        compatibility.getIncompatibility(), both(containsString("aa")).and(containsString("bb")));
  }

  private V1EnvVar envVar(String name, String value) {
    return new V1EnvVar().name(name).value(value);
  }

  @Test
  public void whenOnlyCertificateForEnvVarsDontMatch_dontCreateErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().name("test").addEnvItem(envVar(INTERNAL_OPERATOR_CERT_ENV, "bb")),
            new V1Container());

    assertThat(compatibility.getIncompatibility(), blankOrNullString());
  }

  @Test
  public void whenExpectedSubsetOfActual_reportCompatible() {
    CompatibilityCheck check =
        new CompatibleSets<>("letters", ImmutableSet.of("a", "b"), ImmutableSet.of("b", "c", "a"));

    assertThat(check.isCompatible(), is(true));
  }

  @Test
  public void whenExpectedNotSubsetOfActual_reportNotCompatible() {
    CompatibilityCheck check =
        new CompatibleSets<>(
            "letters", ImmutableSet.of("a", "b", "d"), ImmutableSet.of("b", "c", "a"));

    assertThat(check.isCompatible(), is(false));
  }

  @Test
  public void whenExpectedNotSubsetOfActual_reportMissingElements() {
    CompatibilityCheck check =
        new CompatibleSets<>(
            "letters",
            ImmutableSet.of("alpha", "beta", "delta"),
            ImmutableSet.of("beta", "gamma", "alpha"));

    assertThat(check.getIncompatibility(), containsString("delta"));
    assertThat(check.getIncompatibility(), not(containsString("alpha")));
    assertThat(check.getIncompatibility(), not(containsString("gamma")));
  }

  @Test
  public void whenCanBeMapsAndExpectedAndActualDifferentValues_reportChangedElements() {
    CompatibilityCheck check =
        CheckFactory.create(
            "letters",
            Arrays.asList(object("alpha", 1), object("beta", 2), object("gamma", 3)),
            Arrays.asList(object("beta", 222), object("gamma", 3), object("alpha", 1)));

    assertThat(check.getIncompatibility(), both(containsString("beta")).and(containsString("222")));
    assertThat(check.getIncompatibility(), not(containsString("alpha")));
  }

  @Test
  public void ignoreCertForComparisons() {
    CompatibilityCheck check =
        CheckFactory.create(
                "envVars",
                Arrays.asList(object("alpha", 1), object(INTERNAL_OPERATOR_CERT_ENV, 3)),
                Arrays.asList(object(INTERNAL_OPERATOR_CERT_ENV, 700), object("alpha", 1)))
            .ignoring(INTERNAL_OPERATOR_CERT_ENV);

    assertThat(check.getIncompatibility(), emptyOrNullString());
  }

  private Object object(String name, int value) {
    return new ObjectWithName(name, value);
  }

  @Test
  public void whenExpectedSubmapOfActual_reportCompatible() {
    CompatibilityCheck check =
        new CompatibleMaps<>(
            "letters", ImmutableMap.of("a", 1, "b", 2), ImmutableMap.of("b", 2, "c", 3, "a", 1));

    assertThat(check.isCompatible(), is(true));
  }

  @Test
  public void whenExpectedNotSubmapOfActual_reportNotCompatible() {
    CompatibilityCheck check =
        new CompatibleMaps<>(
            "letters",
            ImmutableMap.of("a", 1, "b", 2, "d", 4),
            ImmutableMap.of("b", 2, "c", 3, "a", 1));

    assertThat(check.isCompatible(), is(false));
  }

  @Test
  public void whenExpectedNotSubmapOfActual_reportMissingElements() {
    CompatibilityCheck check =
        new CompatibleMaps<>(
            "letters",
            ImmutableMap.of("alpha", 1, "beta", 2, "delta", 4),
            ImmutableMap.of("beta", 2, "gamma", 3, "alpha", 1));

    assertThat(check.getIncompatibility(), containsString("delta"));
    assertThat(check.getIncompatibility(), not(containsString("alpha")));
    assertThat(check.getIncompatibility(), not(containsString("gamma")));
  }

  @Test
  public void whenActualKeysHaveDifferentValues_reportNotCompatible() {
    CompatibilityCheck check =
        new CompatibleMaps<>(
            "letters", ImmutableMap.of("a", 1, "b", 2), ImmutableMap.of("b", 5, "c", 3, "a", 1));

    assertThat(check.isCompatible(), is(false));
  }

  @Test
  public void whenActualKeysHaveDifferentValues_reportMissingElements() {
    CompatibilityCheck check =
        new CompatibleMaps<>(
            "letters",
            ImmutableMap.of("alpha", 1, "beta", 2),
            ImmutableMap.of("beta", 5, "gamma", 3, "alpha", 1));

    assertThat(check.getIncompatibility(), both(containsString("beta")).and(containsString("5")));
    assertThat(check.getIncompatibility(), not(containsString("alpha")));
    assertThat(check.getIncompatibility(), not(containsString("gamma")));
  }

  @Test
  public void whenImagesDontMatch_createDomainAndPodScopeErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().image("abcde"), new V1Container().image("cdefg"));

    assertThat(
        compatibility.getScopedIncompatibility(DOMAIN),
        both(containsString("abcde")).and(containsString("cdefg")));
    assertThat(
        compatibility.getScopedIncompatibility(POD),
        both(containsString("abcde")).and(containsString("cdefg")));
  }

  @Test
  public void whenImagesDontMatch_dontCreateUnknownScopeErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().image("abcde"), new V1Container().image("cdefg"));

    assertThat(
        compatibility.getScopedIncompatibility(UNKNOWN), blankOrNullString());
  }


  @Test
  public void whenImagePullPoliciesDontMatch_createDomainAndPodScopeErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().imagePullPolicy("abcde"), new V1Container().imagePullPolicy("cdefg"));

    assertThat(
        compatibility.getScopedIncompatibility(DOMAIN),
        both(containsString("abcde")).and(containsString("cdefg")));
    assertThat(
        compatibility.getScopedIncompatibility(POD),
        both(containsString("abcde")).and(containsString("cdefg")));
  }

  @Test
  public void whenImagePullPoliciesDontMatch_dontCreateUnknownScopeErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().imagePullPolicy("abcde"), new V1Container().imagePullPolicy("cdefg"));

    assertThat(
        compatibility.getScopedIncompatibility(UNKNOWN), blankOrNullString());
  }


  @Test
  public void whenVolumeMountsDontMatch_createUnknownAndPodScopeErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().name("c1").addVolumeMountsItem(new V1VolumeMount().name("V1").mountPath("abcde")),
            new V1Container().name("c1").addVolumeMountsItem(new V1VolumeMount().name("V1").mountPath("cdefg")));

    assertThat(
        compatibility.getScopedIncompatibility(UNKNOWN),
        stringContainsInOrder("c1", "volumeMount", "changed", "cdefg", "to", "abcde"));
    assertThat(
        compatibility.getScopedIncompatibility(POD),
        stringContainsInOrder("c1", "volumeMount", "changed", "cdefg", "to", "abcde"));
  }

  @Test
  public void whenVolumeMountsDontMatch_dontCreateDomainScopeErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().name("c1").addVolumeMountsItem(new V1VolumeMount().name("V1").mountPath("abcde")),
            new V1Container().name("c1").addVolumeMountsItem(new V1VolumeMount().name("V1").mountPath("cdefg")));

    assertThat(
        compatibility.getScopedIncompatibility(DOMAIN), blankOrNullString());
  }

  @Test
  public void whenRestartVersionsDontMatch_createDomainAndPodScopeErrorMessage() {
    PodCompatibility.PodMetadataCompatibility compatibility =
        new PodCompatibility.PodMetadataCompatibility(
            new V1ObjectMeta().putLabelsItem(LabelConstants.DOMAINRESTARTVERSION_LABEL, "12345"),
            new V1ObjectMeta().putLabelsItem(LabelConstants.DOMAINRESTARTVERSION_LABEL, "abcde"));

    assertThat(
        compatibility.getScopedIncompatibility(DOMAIN),
        stringContainsInOrder("domain restart version changed from", "abcde", "to", "12345"));
    assertThat(
        compatibility.getScopedIncompatibility(POD),
        stringContainsInOrder("domain restart version changed from", "abcde", "to", "12345"));
  }

  @Test
  public void whenRestartVersionsDontMatch_dontCreateUnknownScopeErrorMessage() {
    PodCompatibility.PodMetadataCompatibility compatibility =
        new PodCompatibility.PodMetadataCompatibility(
            new V1ObjectMeta().putLabelsItem(LabelConstants.DOMAINRESTARTVERSION_LABEL, "12345"),
            new V1ObjectMeta().putLabelsItem(LabelConstants.DOMAINRESTARTVERSION_LABEL, "abcde"));

    assertThat(
        compatibility.getScopedIncompatibility(UNKNOWN), blankOrNullString());
  }

  @Test
  public void whenLivenessProbesDontMatch_createDomainAndPodScopeErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container()
                .livenessProbe(
                    new V1Probe().initialDelaySeconds(1).timeoutSeconds(5).periodSeconds(3)),
            new V1Container()
                .livenessProbe(
                    new V1Probe().initialDelaySeconds(1).timeoutSeconds(2).periodSeconds(3)));

    assertThat(
        compatibility.getScopedIncompatibility(DOMAIN),
        both(containsString("timeout")).and(containsString("2")));
    assertThat(
        compatibility.getScopedIncompatibility(POD),
        both(containsString("timeout")).and(containsString("2")));
  }

  @Test
  public void whenLivenessProbesDontMatch_dontCreateUnknownScopeErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container()
                .livenessProbe(
                    new V1Probe().initialDelaySeconds(1).timeoutSeconds(5).periodSeconds(3)),
            new V1Container()
                .livenessProbe(
                    new V1Probe().initialDelaySeconds(1).timeoutSeconds(2).periodSeconds(3)));

    assertThat(
        compatibility.getScopedIncompatibility(UNKNOWN), blankOrNullString());
  }

  @Test
  public void whenResourcesDontMatch_dontCreateDomainScopeErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container()
                .resources(new V1ResourceRequirements().putLimitsItem("time", new Quantity("20"))),
            new V1Container());

    assertThat(
        compatibility.getScopedIncompatibility(DOMAIN), blankOrNullString());
  }

  @Test
  public void whenResourcesDontMatch_createUnknownAndPodScopeScopeErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container()
                .resources(new V1ResourceRequirements().putLimitsItem("time", new Quantity("20"))),
            new V1Container());

    assertThat(
        compatibility.getScopedIncompatibility(UNKNOWN),
        both(containsString("time")).and(containsString("limits")));
    assertThat(
        compatibility.getScopedIncompatibility(POD),
        both(containsString("time")).and(containsString("limits")));
  }

  @Test
  public void whenPortsDontMatch_dontCreateDomainScopeErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().addPortsItem(new V1ContainerPort().containerPort(1100)),
            new V1Container().addPortsItem(new V1ContainerPort().containerPort(1234)));

    assertThat(
        compatibility.getScopedIncompatibility(DOMAIN), blankOrNullString());
  }

  @Test
  public void whenPortsDontMatch_createUnknownAndPodScopeErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().addPortsItem(new V1ContainerPort().containerPort(1100)),
            new V1Container().addPortsItem(new V1ContainerPort().containerPort(1234)));

    assertThat(
        compatibility.getScopedIncompatibility(UNKNOWN),
        both(containsString("1100")).and(not(containsString("1234"))));
    assertThat(
        compatibility.getScopedIncompatibility(POD),
        both(containsString("1100")).and(not(containsString("1234"))));
  }

  @Test
  public void whenEnvVarsDontMatch_dontCreateDomainScopeErrorMessage() {
    String name = "aa";
    String value = "bb";
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().addEnvItem(envVar(name, value)),
            new V1Container().addEnvItem(envVar("aa", "cc")));

    assertThat(
        compatibility.getScopedIncompatibility(DOMAIN), blankOrNullString());
  }

  @Test
  public void whenEnvVarsDontMatch_createUnknownAndPodScopeErrorMessage() {
    String name = "aa";
    String value = "bb";
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().addEnvItem(envVar(name, value)),
            new V1Container().addEnvItem(envVar("aa", "cc")));

    assertThat(
        compatibility.getScopedIncompatibility(UNKNOWN), both(containsString("aa")).and(containsString("bb")));
    assertThat(
        compatibility.getScopedIncompatibility(POD), both(containsString("aa")).and(containsString("bb")));
  }

  @Test
  public void whenDomainHomeEnvVarsDontMatch_createDomainAndPodScopeErrorMessage() {
    String name = DOMAIN_HOME;
    String value = "/myhome";
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().addEnvItem(envVar(name, value)),
            new V1Container().addEnvItem(envVar(name, "cc")));

    assertThat(
        compatibility.getScopedIncompatibility(DOMAIN), both(containsString("domainHome")).and(containsString(value)));
    assertThat(
        compatibility.getScopedIncompatibility(POD), both(containsString(name)).and(containsString(value)));
  }

  @Test
  public void whenDomainHomeEnvVarsDontMatch_dontCreateUnknownScopeErrorMessage() {
    String name = DOMAIN_HOME;
    String value = "/myhome";
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().addEnvItem(envVar(name, value)),
            new V1Container().addEnvItem(envVar(name, "cc")));

    assertThat(
        compatibility.getScopedIncompatibility(UNKNOWN), blankOrNullString());
  }

  @Test
  public void whenLogHomeEnvVarsDontMatch_createDomainAndPodScopeErrorMessage() {
    String name = LOG_HOME;
    String value = "/myhome";
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().addEnvItem(envVar(name, value)),
            new V1Container().addEnvItem(envVar(name, "cc")));

    assertThat(
        compatibility.getScopedIncompatibility(DOMAIN), both(containsString("logHome")).and(containsString(value)));
    assertThat(
        compatibility.getScopedIncompatibility(POD), both(containsString(name)).and(containsString(value)));
  }

  @Test
  public void whenLogHomeEnvVarsDontMatch_dontCreateUnknownScopeErrorMessage() {
    String name = LOG_HOME;
    String value = "/myhome";
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().addEnvItem(envVar(name, value)),
            new V1Container().addEnvItem(envVar(name, "cc")));

    assertThat(
        compatibility.getScopedIncompatibility(UNKNOWN), blankOrNullString());
  }

  @Test
  public void whenOnlyCertificateForEnvVarsDontMatch_dontCreateAnyScopeErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().name("test").addEnvItem(envVar(INTERNAL_OPERATOR_CERT_ENV, "bb")),
            new V1Container());

    assertThat(compatibility.getScopedIncompatibility(DOMAIN), blankOrNullString());
    assertThat(compatibility.getScopedIncompatibility(POD), blankOrNullString());
    assertThat(compatibility.getScopedIncompatibility(UNKNOWN), blankOrNullString());
  }

  @Test
  public void whenExpectedNotSubsetOfActual_dontReportDomainScopeMissingElements() {
    CompatibilityCheck check =
        new CompatibleSets<>(
            "letters",
            ImmutableSet.of("alpha", "beta", "delta"),
            ImmutableSet.of("beta", "gamma", "alpha"));

    assertThat(check.getScopedIncompatibility(DOMAIN), blankOrNullString());
  }

  @Test
  public void whenExpectedNotSubsetOfActual_reportUnknownAndPodScopeMissingElements() {
    CompatibilityCheck check =
        new CompatibleSets<>(
            "letters",
            ImmutableSet.of("alpha", "beta", "delta"),
            ImmutableSet.of("beta", "gamma", "alpha"));

    assertThat(check.getScopedIncompatibility(UNKNOWN), containsString("delta"));
    assertThat(check.getScopedIncompatibility(UNKNOWN), not(containsString("alpha")));
    assertThat(check.getScopedIncompatibility(UNKNOWN), not(containsString("gamma")));
    assertThat(check.getScopedIncompatibility(POD), containsString("delta"));
    assertThat(check.getScopedIncompatibility(POD), not(containsString("alpha")));
    assertThat(check.getScopedIncompatibility(POD), not(containsString("gamma")));
  }

  @Test
  public void whenCanBeMapsAndExpectedAndActualDifferentValues_dontReportDomainScopeChangedElements() {
    CompatibilityCheck check =
        CheckFactory.create(
            "letters",
            Arrays.asList(object("alpha", 1), object("beta", 2), object("gamma", 3)),
            Arrays.asList(object("beta", 222), object("gamma", 3), object("alpha", 1)));

    assertThat(check.getScopedIncompatibility(DOMAIN), blankOrNullString());
  }

  @Test
  public void whenCanBeMapsAndExpectedAndActualDifferentValues_reportUnknownScopeChangedElements() {
    CompatibilityCheck check =
        CheckFactory.create(
            "letters",
            Arrays.asList(object("alpha", 1), object("beta", 2), object("gamma", 3)),
            Arrays.asList(object("beta", 222), object("gamma", 3), object("alpha", 1)));

    assertThat(check.getScopedIncompatibility(UNKNOWN), both(containsString("beta")).and(containsString("222")));
    assertThat(check.getScopedIncompatibility(UNKNOWN), not(containsString("alpha")));
  }

  @Test
  public void ignoreAllScopeCertForComparisons() {
    CompatibilityCheck check =
        CheckFactory.create(
            "envVars",
            Arrays.asList(object("alpha", 1), object(INTERNAL_OPERATOR_CERT_ENV, 3)),
            Arrays.asList(object(INTERNAL_OPERATOR_CERT_ENV, 700), object("alpha", 1)))
            .ignoring(INTERNAL_OPERATOR_CERT_ENV);

    assertThat(check.getScopedIncompatibility(DOMAIN), emptyOrNullString());
    assertThat(check.getScopedIncompatibility(POD), emptyOrNullString());
    assertThat(check.getScopedIncompatibility(UNKNOWN), emptyOrNullString());
  }

  @Test
  public void whenExpectedNotSubmapOfActual_reportDomainScopeMissingElements() {
    CompatibilityCheck check =
        new CompatibleMaps<>(
            "env",
            ImmutableMap.of("alpha", 1, "beta", 2, "DOMAIN_HOME", 4),
            ImmutableMap.of("beta", 2, "gamma", 3, "alpha", 1));

    assertThat(check.getScopedIncompatibility(DOMAIN), containsString("DOMAIN_HOME"));
    assertThat(check.getScopedIncompatibility(DOMAIN), not(containsString("alpha")));
    assertThat(check.getScopedIncompatibility(DOMAIN), not(containsString("gamma")));
  }

  @Test
  public void whenExpectedNonDomainScopedNotSubmapOfActual_dontReportDomainScopeMissingElements() {
    CompatibilityCheck check =
        new CompatibleMaps<>(
            "letters",
            ImmutableMap.of("alpha", 1, "beta", 2, "delta", 4),
            ImmutableMap.of("beta", 2, "gamma", 3, "alpha", 1));

    assertThat(check.getScopedIncompatibility(DOMAIN), not(containsString("delta")));
    assertThat(check.getScopedIncompatibility(DOMAIN), not(containsString("alpha")));
    assertThat(check.getScopedIncompatibility(DOMAIN), not(containsString("gamma")));
  }

  @Test
  public void whenExpectedNonDomainScopedNotSubmapOfActual_dontReportUnknownScopeMissingElements() {
    CompatibilityCheck check =
        new CompatibleMaps<>(
            "letters",
            ImmutableMap.of("alpha", 1, "beta", 2, "delta", 4),
            ImmutableMap.of("beta", 2, "gamma", 3, "alpha", 1));

    assertThat(check.getScopedIncompatibility(UNKNOWN), containsString("delta"));
    assertThat(check.getScopedIncompatibility(UNKNOWN), not(containsString("alpha")));
    assertThat(check.getScopedIncompatibility(UNKNOWN), not(containsString("gamma")));
  }

  @Test
  public void whenExpectedNotSubmapOfActual_reportUnknownAndPodScopeMissingElements() {
    CompatibilityCheck check =
        new CompatibleMaps<>(
            "letters",
            ImmutableMap.of("alpha", 1, "beta", 2, "delta", 4),
            ImmutableMap.of("beta", 2, "gamma", 3, "alpha", 1));

    assertThat(check.getScopedIncompatibility(UNKNOWN), containsString("delta"));
    assertThat(check.getScopedIncompatibility(UNKNOWN), not(containsString("alpha")));
    assertThat(check.getScopedIncompatibility(UNKNOWN), not(containsString("gamma")));
    assertThat(check.getScopedIncompatibility(POD), containsString("delta"));
    assertThat(check.getScopedIncompatibility(POD), not(containsString("alpha")));
    assertThat(check.getScopedIncompatibility(POD), not(containsString("gamma")));
  }

  @Test
  public void whenActualKeysHaveDifferentValues_dontReportDomainScopeAdditionalElements() {
    CompatibilityCheck check =
        new CompatibleMaps<>(
            "letters",
            ImmutableMap.of("alpha", 1, "beta", 2),
            ImmutableMap.of("beta", 5, "gamma", 3, "alpha", 1));

    assertThat(check.getScopedIncompatibility(DOMAIN), emptyOrNullString());
  }

  @Test
  public void whenActualKeysHaveDifferentValues_reportUnknownAndPodScopeMissingElements() {
    CompatibilityCheck check =
        new CompatibleMaps<>(
            "letters",
            ImmutableMap.of("alpha", 1, "beta", 2),
            ImmutableMap.of("beta", 5, "gamma", 3, "alpha", 1));

    assertThat(check.getScopedIncompatibility(UNKNOWN), both(containsString("beta")).and(containsString("5")));
    assertThat(check.getScopedIncompatibility(UNKNOWN), not(containsString("alpha")));
    assertThat(check.getScopedIncompatibility(UNKNOWN), not(containsString("gamma")));
    assertThat(check.getScopedIncompatibility(POD), both(containsString("beta")).and(containsString("5")));
    assertThat(check.getScopedIncompatibility(POD), not(containsString("alpha")));
    assertThat(check.getScopedIncompatibility(POD), not(containsString("gamma")));
  }

  @Test
  public void whenImagesDontMatch_andResourcesDontMatch_createDomainAScopeErrorMessageForImageOnly() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().image("abcde")
                .resources(new V1ResourceRequirements().putLimitsItem("time", new Quantity("20"))),
            new V1Container().image("cdefg"));

    assertThat(
        compatibility.getScopedIncompatibility(DOMAIN),
        both(containsString("abcde")).and(containsString("cdefg")));
    assertThat(
        compatibility.getScopedIncompatibility(POD),
        stringContainsInOrder("resource limits changed from", "{}", "to", "time=Quantity"));
  }

  @Test
  public void whenImagesDontMatch_andResourcesDontMatch_createUnknownScopeErrorMessageForResourcesOnly() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().image("abcde")
                .resources(new V1ResourceRequirements().putLimitsItem("time", new Quantity("20"))),
            new V1Container().image("cdefg"));

    assertThat(
        compatibility.getScopedIncompatibility(UNKNOWN),
        not(both(containsString("abcde")).and(containsString("cdefg"))));
    assertThat(
        compatibility.getScopedIncompatibility(POD),
        both(containsString("time")).and(containsString("limits")));
  }

  @Test
  public void whenImagesDontMatch_andResourcesDontMatch_createPodScopeErrorMessageForBoth() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().image("abcde")
                .resources(new V1ResourceRequirements().putLimitsItem("time", new Quantity("20"))),
            new V1Container().image("cdefg"));

    assertThat(
        compatibility.getScopedIncompatibility(POD),
        both(containsString("abcde")).and(containsString("cdefg")));
    assertThat(
        compatibility.getScopedIncompatibility(POD),
        both(containsString("time")).and(containsString("limits")));
  }


  static class ObjectWithName {
    private final String name;
    private final int value;

    ObjectWithName(String name, int value) {
      this.name = name;
      this.value = value;
    }

    public String getName() {
      return name;
    }

    @Override
    public boolean equals(Object o) {
      return (o instanceof ObjectWithName) && equals((ObjectWithName) o);
    }

    private boolean equals(ObjectWithName that) {
      return value == that.value && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder(17, 37).append(name).append(value).toHashCode();
    }

    @Override
    public String toString() {
      return String.format("<%s = %d>", name, value);
    }
  }
}
