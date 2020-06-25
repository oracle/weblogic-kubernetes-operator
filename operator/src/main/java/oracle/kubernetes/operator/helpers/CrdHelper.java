// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionNames;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionSpec;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionVersion;
import io.kubernetes.client.openapi.models.V1CustomResourceSubresourceScale;
import io.kubernetes.client.openapi.models.V1CustomResourceSubresources;
import io.kubernetes.client.openapi.models.V1CustomResourceValidation;
import io.kubernetes.client.openapi.models.V1JSONSchemaProps;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1beta1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1beta1CustomResourceDefinitionNames;
import io.kubernetes.client.openapi.models.V1beta1CustomResourceDefinitionSpec;
import io.kubernetes.client.openapi.models.V1beta1CustomResourceDefinitionVersion;
import io.kubernetes.client.openapi.models.V1beta1CustomResourceSubresourceScale;
import io.kubernetes.client.openapi.models.V1beta1CustomResourceSubresources;
import io.kubernetes.client.openapi.models.V1beta1CustomResourceValidation;
import io.kubernetes.client.openapi.models.V1beta1JSONSchemaProps;
import io.kubernetes.client.util.Yaml;
import okhttp3.internal.http2.StreamResetException;
import oracle.kubernetes.json.SchemaGenerator;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;

/** Helper class to ensure Domain CRD is created. */
public class CrdHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final String SCHEMA_LOCATION = "/schema";
  private static final String NO_ERROR = "NO_ERROR";
  private static final CrdComparator COMPARATOR = new CrdComparatorImpl();

  private static final FileGroupReader schemaReader = new FileGroupReader(SCHEMA_LOCATION);

  private CrdHelper() {
  }

  /**
   * Used by build to generate crd-validation.yaml
   * @param args Arguments that must be one value giving file name to create
   */
  public static void main(String[] args) {
    if (args == null || args.length != 2) {
      throw new IllegalArgumentException();
    }

    CrdContext context = new CrdContext(null, null, null);

    String outputFileName = args[0];
    Path outputFilePath = Paths.get(outputFileName);

    try (Writer writer = Files.newBufferedWriter(outputFilePath)) {
      writer.write(
          "# Copyright (c) 2020, Oracle Corporation and/or its affiliates.\n"
              + "# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.\n");
      writer.write("\n");
      Yaml.dump(context.model, writer);
    } catch (IOException io) {
      throw new RuntimeException(io);
    }

    String betaOutputFileName = args[1];
    Path betaOutputFilePath = Paths.get(betaOutputFileName);

    try (Writer writer = Files.newBufferedWriter(betaOutputFilePath)) {
      writer.write(
          "# Copyright (c) 2020, Oracle Corporation and/or its affiliates.\n"
              + "# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.\n");
      writer.write("\n");
      Yaml.dump(context.betaModel, writer);
    } catch (IOException io) {
      throw new RuntimeException(io);
    }
  }

  /**
   * Factory for {@link Step} that creates Domain CRD.
   *
   * @param version Version of the Kubernetes API Server
   * @param productVersion Version of the operator
   * @param next Next step
   * @return Step for creating Domain custom resource definition
   */
  public static Step createDomainCrdStep(KubernetesVersion version, SemanticVersion productVersion, Step next) {
    return new CrdStep(version, productVersion, next);
  }

  private static List<ResourceVersion> getVersions(V1CustomResourceDefinition crd) {
    List<ResourceVersion> versions = new ArrayList<>();
    List<V1CustomResourceDefinitionVersion> vs = crd.getSpec().getVersions();
    if (vs != null) {
      for (V1CustomResourceDefinitionVersion vi : vs) {
        versions.add(new ResourceVersion(vi.getName()));
      }
    }

    return versions;
  }

  private static List<ResourceVersion> getBetaVersions(V1beta1CustomResourceDefinition crd) {
    List<ResourceVersion> versions = new ArrayList<>();
    List<V1beta1CustomResourceDefinitionVersion> vs = crd.getSpec().getVersions();
    if (vs != null) {
      for (V1beta1CustomResourceDefinitionVersion vi : vs) {
        versions.add(new ResourceVersion(vi.getName()));
      }
    }

    return versions;
  }

  interface CrdComparator {
    boolean isOutdatedCrd(
        SemanticVersion productVersion, V1CustomResourceDefinition actual, V1CustomResourceDefinition expected);

    boolean isOutdatedBetaCrd(
        SemanticVersion productVersion,
        V1beta1CustomResourceDefinition actual, V1beta1CustomResourceDefinition expected);
  }

  static class CrdStep extends Step {
    final CrdContext context;

    CrdStep(KubernetesVersion version, SemanticVersion productVersion, Step next) {
      super(next);
      context = new CrdContext(version, productVersion, this);
    }

    @Override
    public NextAction apply(Packet packet) {
      if (context.version.isCrdV1Supported()) {
        return doNext(context.verifyCrd(getNext()), packet);
      } else {
        return doNext(context.verifyBetaCrd(getNext()), packet);
      }
    }
  }

  static class CrdContext {
    private final Step conflictStep;
    private final V1CustomResourceDefinition model;
    private final V1beta1CustomResourceDefinition betaModel;
    private final KubernetesVersion version;
    private final SemanticVersion productVersion;

    CrdContext(KubernetesVersion version, SemanticVersion productVersion, Step conflictStep) {
      this.version = version;
      this.productVersion = productVersion;
      this.conflictStep = conflictStep;
      this.model = createModel(version, productVersion);
      this.betaModel = createBetaModel(version, productVersion);
    }

    static V1CustomResourceDefinition createModel(KubernetesVersion version, SemanticVersion productVersion) {
      return new V1CustomResourceDefinition()
          .apiVersion("apiextensions.k8s.io/v1")
          .kind("CustomResourceDefinition")
          .metadata(createMetadata(productVersion))
          .spec(createSpec(version));
    }

    static V1beta1CustomResourceDefinition createBetaModel(KubernetesVersion version, SemanticVersion productVersion) {
      return new V1beta1CustomResourceDefinition()
          .apiVersion("apiextensions.k8s.io/v1beta1")
          .kind("CustomResourceDefinition")
          .metadata(createMetadata(productVersion))
          .spec(createBetaSpec(version));
    }

    static V1ObjectMeta createMetadata(SemanticVersion productVersion) {
      V1ObjectMeta metadata = new V1ObjectMeta()
          .name(KubernetesConstants.CRD_NAME);

      if (productVersion != null) {
        metadata.putLabelsItem(LabelConstants.OPERATOR_VERISON, productVersion.toString());
      }
      return metadata;
    }

    static V1CustomResourceDefinitionSpec createSpec(KubernetesVersion version) {
      V1CustomResourceDefinitionSpec spec =
          new V1CustomResourceDefinitionSpec()
              .group(KubernetesConstants.DOMAIN_GROUP)
              .preserveUnknownFields(false)
              .versions(getCrdVersions())
              .scope("Namespaced")
              .names(getCrdNames());
      return spec;
    }

    static V1beta1CustomResourceDefinitionSpec createBetaSpec(KubernetesVersion version) {
      V1beta1CustomResourceDefinitionSpec spec =
          new V1beta1CustomResourceDefinitionSpec()
              .group(KubernetesConstants.DOMAIN_GROUP)
              .preserveUnknownFields(false)
              .versions(getBetaCrdVersions())
              .validation(createBetaSchemaValidation())
              .subresources(createBetaSubresources())
              .scope("Namespaced")
              .names(getBetaCrdNames());
      return spec;
    }

    static String getVersionFromCrdSchemaFileName(String name) {
      // names will be like "domain-crd-schemav2-201.yaml"
      // want "v2"
      String end = name.substring(17);
      return end.substring(0, end.indexOf('-'));
    }

    static V1CustomResourceValidation getValidationFromCrdSchemaFile(String fileContents) {
      return Yaml.getSnakeYaml().loadAs(new StringReader(fileContents), V1CustomResourceValidation.class);
    }

    static V1CustomResourceSubresources createSubresources() {
      return new V1CustomResourceSubresources()
          .status(new HashMap<String, String>()) // this just needs an empty object to enable status subresource
          .scale(
              new V1CustomResourceSubresourceScale()
                  .specReplicasPath(".spec.replicas")
                  .statusReplicasPath(".status.replicas"));
    }

    static V1beta1CustomResourceSubresources createBetaSubresources() {
      return new V1beta1CustomResourceSubresources()
          .status(new HashMap<String, String>()) // this just needs an empty object to enable status subresource
          .scale(
              new V1beta1CustomResourceSubresourceScale()
                  .specReplicasPath(".spec.replicas")
                  .statusReplicasPath(".status.replicas"));
    }

    static List<V1CustomResourceDefinitionVersion> getCrdVersions() {
      Map<String, String> schemas = schemaReader.loadFilesFromClasspath();
      List<V1CustomResourceDefinitionVersion> versions = schemas.entrySet().stream()
          .sorted(Comparator.comparing(Map.Entry::getKey))
          .map(entry -> new V1CustomResourceDefinitionVersion()
              .name(getVersionFromCrdSchemaFileName(entry.getKey()))
              .schema(getValidationFromCrdSchemaFile(entry.getValue()))
              .subresources(createSubresources())
              .served(true)
              .storage(false))
          .collect(Collectors.toList());

      versions.add(
          0, // must be first
          new V1CustomResourceDefinitionVersion()
              .name(KubernetesConstants.DOMAIN_VERSION)
              .schema(createSchemaValidation())
              .subresources(createSubresources())
              .served(true)
              .storage(true));
      return versions;
    }

    static List<V1beta1CustomResourceDefinitionVersion> getBetaCrdVersions() {
      Map<String, String> schemas = schemaReader.loadFilesFromClasspath();
      List<V1beta1CustomResourceDefinitionVersion> versions = schemas.entrySet().stream()
          .sorted(Comparator.comparing(Map.Entry::getKey))
          .map(entry -> new V1beta1CustomResourceDefinitionVersion()
              .name(getVersionFromCrdSchemaFileName(entry.getKey()))
              .served(true)
              .storage(false))
          .collect(Collectors.toList());

      versions.add(
          0, // must be first
          new V1beta1CustomResourceDefinitionVersion()
              .name(KubernetesConstants.DOMAIN_VERSION)
              .served(true)
              .storage(true));
      return versions;
    }

    static V1CustomResourceDefinitionNames getCrdNames() {
      return new V1CustomResourceDefinitionNames()
          .plural(KubernetesConstants.DOMAIN_PLURAL)
          .singular(KubernetesConstants.DOMAIN_SINGULAR)
          .kind(KubernetesConstants.DOMAIN)
          .shortNames(Collections.singletonList(KubernetesConstants.DOMAIN_SHORT));
    }

    static V1beta1CustomResourceDefinitionNames getBetaCrdNames() {
      return new V1beta1CustomResourceDefinitionNames()
          .plural(KubernetesConstants.DOMAIN_PLURAL)
          .singular(KubernetesConstants.DOMAIN_SINGULAR)
          .kind(KubernetesConstants.DOMAIN)
          .shortNames(Collections.singletonList(KubernetesConstants.DOMAIN_SHORT));
    }

    static V1CustomResourceValidation createSchemaValidation() {
      return new V1CustomResourceValidation().openAPIV3Schema(createOpenApiV3Schema());
    }

    static V1beta1CustomResourceValidation createBetaSchemaValidation() {
      return new V1beta1CustomResourceValidation().openAPIV3Schema(createBetaOpenApiV3Schema());
    }

    static V1JSONSchemaProps createOpenApiV3Schema() {
      Gson gson = new Gson();
      JsonElement jsonElementSpec =
          gson.toJsonTree(createSchemaGenerator().generate(DomainSpec.class));
      V1JSONSchemaProps spec = gson.fromJson(jsonElementSpec, V1JSONSchemaProps.class);
      JsonElement jsonElementStatus =
          gson.toJsonTree(createSchemaGenerator().generate(DomainStatus.class));
      V1JSONSchemaProps status =
          gson.fromJson(jsonElementStatus, V1JSONSchemaProps.class);
      return new V1JSONSchemaProps()
          .type("object")
          .putPropertiesItem("spec", spec)
          .putPropertiesItem("status", status);
    }

    static V1beta1JSONSchemaProps createBetaOpenApiV3Schema() {
      Gson gson = new Gson();
      JsonElement jsonElementSpec =
          gson.toJsonTree(createSchemaGenerator().generate(DomainSpec.class));
      V1beta1JSONSchemaProps spec = gson.fromJson(jsonElementSpec, V1beta1JSONSchemaProps.class);
      JsonElement jsonElementStatus =
          gson.toJsonTree(createSchemaGenerator().generate(DomainStatus.class));
      V1beta1JSONSchemaProps status =
          gson.fromJson(jsonElementStatus, V1beta1JSONSchemaProps.class);
      return new V1beta1JSONSchemaProps()
          .type("object")
          .putPropertiesItem("spec", spec)
          .putPropertiesItem("status", status);
    }

    static SchemaGenerator createSchemaGenerator() {
      SchemaGenerator generator = new SchemaGenerator();
      generator.setIncludeAdditionalProperties(false);
      generator.setSupportObjectReferences(false);
      generator.setIncludeSchemaReference(false);
      return generator;
    }

    Step verifyCrd(Step next) {
      return new CallBuilder().readCustomResourceDefinitionAsync(
              model.getMetadata().getName(), createReadResponseStep(next));
    }

    ResponseStep<V1CustomResourceDefinition> createReadResponseStep(Step next) {
      return new ReadResponseStep(next);
    }

    Step verifyBetaCrd(Step next) {
      return new CallBuilder().readBetaCustomResourceDefinitionAsync(
          betaModel.getMetadata().getName(), createBetaReadResponseStep(next));
    }

    ResponseStep<V1beta1CustomResourceDefinition> createBetaReadResponseStep(Step next) {
      return new ReadBetaResponseStep(next);
    }

    Step createCrd(Step next) {
      return new CallBuilder().createCustomResourceDefinitionAsync(
              model, createCreateResponseStep(next));
    }

    ResponseStep<V1CustomResourceDefinition> createCreateResponseStep(Step next) {
      return new CreateResponseStep(next);
    }

    Step createBetaCrd(Step next) {
      return new CallBuilder().createBetaCustomResourceDefinitionAsync(
          betaModel, createBetaCreateResponseStep(next));
    }

    ResponseStep<V1beta1CustomResourceDefinition> createBetaCreateResponseStep(Step next) {
      return new CreateBetaResponseStep(next);
    }

    private boolean isOutdatedCrd(V1CustomResourceDefinition existingCrd) {
      return COMPARATOR.isOutdatedCrd(productVersion, existingCrd, this.model);
    }

    private boolean existingCrdContainsVersion(V1CustomResourceDefinition existingCrd) {
      List<V1CustomResourceDefinitionVersion> versions = existingCrd.getSpec().getVersions();
      boolean found = false;
      if (versions != null) {
        for (V1CustomResourceDefinitionVersion v : versions) {
          if (KubernetesConstants.DOMAIN_VERSION.equals(v.getName())) {
            found = true;
            break;
          }
        }
      }

      return found;
    }

    private boolean isOutdatedBetaCrd(V1beta1CustomResourceDefinition existingCrd) {
      return COMPARATOR.isOutdatedBetaCrd(productVersion, existingCrd, this.betaModel);
    }

    private boolean existingBetaCrdContainsVersion(V1beta1CustomResourceDefinition existingCrd) {
      List<V1beta1CustomResourceDefinitionVersion> versions = existingCrd.getSpec().getVersions();
      boolean found = false;
      if (versions != null) {
        for (V1beta1CustomResourceDefinitionVersion v : versions) {
          if (KubernetesConstants.DOMAIN_VERSION.equals(v.getName())) {
            found = true;
            break;
          }
        }
      }

      return found;
    }

    Step updateExistingCrd(Step next, V1CustomResourceDefinition existingCrd) {
      existingCrd
          .getSpec()
          .addVersionsItem(
              new V1CustomResourceDefinitionVersion()
                  .name(KubernetesConstants.DOMAIN_VERSION)
                  .schema(createSchemaValidation())
                  .subresources(createSubresources())
                  .served(true));

      return new CallBuilder().replaceCustomResourceDefinitionAsync(
              existingCrd.getMetadata().getName(), existingCrd, createReplaceResponseStep(next));
    }

    Step updateExistingBetaCrd(Step next, V1beta1CustomResourceDefinition existingCrd) {
      existingCrd
          .getSpec()
          .addVersionsItem(
              new V1beta1CustomResourceDefinitionVersion()
                  .name(KubernetesConstants.DOMAIN_VERSION)
                  .served(true));

      return new CallBuilder().replaceBetaCustomResourceDefinitionAsync(
          existingCrd.getMetadata().getName(), existingCrd, createBetaReplaceResponseStep(next));
    }

    Step updateCrd(Step next, V1CustomResourceDefinition existingCrd) {
      model.getMetadata().setResourceVersion(existingCrd.getMetadata().getResourceVersion());

      return new CallBuilder().replaceCustomResourceDefinitionAsync(
          model.getMetadata().getName(), model, createReplaceResponseStep(next));
    }

    Step updateCrd(Step next, V1beta1CustomResourceDefinition existingCrd) {
      model.getMetadata().setResourceVersion(existingCrd.getMetadata().getResourceVersion());

      return new CallBuilder().replaceCustomResourceDefinitionAsync(
          model.getMetadata().getName(), model, createReplaceResponseStep(next));
    }

    Step updateBetaCrd(Step next, V1beta1CustomResourceDefinition existingCrd) {
      betaModel.getMetadata().setResourceVersion(existingCrd.getMetadata().getResourceVersion());

      return new CallBuilder().replaceBetaCustomResourceDefinitionAsync(
          betaModel.getMetadata().getName(), betaModel, createBetaReplaceResponseStep(next));
    }

    ResponseStep<V1CustomResourceDefinition> createReplaceResponseStep(Step next) {
      return new ReplaceResponseStep(next);
    }

    class ReadResponseStep extends DefaultResponseStep<V1CustomResourceDefinition> {
      ReadResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onSuccess(
          Packet packet, CallResponse<V1CustomResourceDefinition> callResponse) {
        V1CustomResourceDefinition existingCrd = callResponse.getResult();
        if (existingCrd == null) {
          // Check to see if there is a v1beta1 CRD
          return doNext(verifyBetaCrd(getNext()), packet);
        } else if (isOutdatedCrd(existingCrd)) {
          return doNext(updateCrd(getNext(), existingCrd), packet);
        } else if (!existingCrdContainsVersion(existingCrd)) {
          return doNext(updateExistingCrd(getNext(), existingCrd), packet);
        } else {
          return doNext(packet);
        }
      }

      @Override
      protected NextAction onFailureNoRetry(Packet packet, CallResponse<V1CustomResourceDefinition> callResponse) {
        return isNotAuthorizedOrForbidden(callResponse)
            ? doNext(packet) : super.onFailureNoRetry(packet, callResponse);
      }
    }

    ResponseStep<V1beta1CustomResourceDefinition> createBetaReplaceResponseStep(Step next) {
      return new ReplaceBetaResponseStep(next);
    }

    class ReadBetaResponseStep extends DefaultResponseStep<V1beta1CustomResourceDefinition> {
      ReadBetaResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onSuccess(
          Packet packet, CallResponse<V1beta1CustomResourceDefinition> callResponse) {
        V1beta1CustomResourceDefinition existingCrd = callResponse.getResult();
        if (version.isCrdV1Supported()) {
          if (existingCrd == null) {
            return doNext(createCrd(getNext()), packet);
          } else {
            return doNext(updateCrd(getNext(), existingCrd), packet);
          }
        } else {
          if (existingCrd == null) {
            return doNext(createBetaCrd(getNext()), packet);
          } else if (isOutdatedBetaCrd(existingCrd)) {
            return doNext(updateBetaCrd(getNext(), existingCrd), packet);
          } else if (!existingBetaCrdContainsVersion(existingCrd)) {
            return doNext(updateExistingBetaCrd(getNext(), existingCrd), packet);
          }
        }
        return doNext(packet);
      }

      @Override
      protected NextAction onFailureNoRetry(Packet packet, CallResponse<V1beta1CustomResourceDefinition> callResponse) {
        return isNotAuthorizedOrForbidden(callResponse)
            ? doNext(packet) : super.onFailureNoRetry(packet, callResponse);
      }
    }

    private class CreateResponseStep extends ResponseStep<V1CustomResourceDefinition> {
      CreateResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onFailure(
          Packet packet, CallResponse<V1CustomResourceDefinition> callResponse) {
        return super.onFailure(conflictStep, packet, callResponse);
      }

      @Override
      public NextAction onSuccess(
          Packet packet, CallResponse<V1CustomResourceDefinition> callResponse) {
        LOGGER.info(MessageKeys.CREATING_CRD, callResponse);
        return doNext(packet);
      }

      @Override
      protected NextAction onFailureNoRetry(Packet packet, CallResponse<V1CustomResourceDefinition> callResponse) {
        LOGGER.info(MessageKeys.CREATE_CRD_FAILED, callResponse);
        return isNotAuthorizedOrForbidden(callResponse)
            ? doNext(packet) : super.onFailureNoRetry(packet, callResponse);
      }
    }

    private class CreateBetaResponseStep extends ResponseStep<V1beta1CustomResourceDefinition> {
      CreateBetaResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onFailure(
          Packet packet, CallResponse<V1beta1CustomResourceDefinition> callResponse) {
        return super.onFailure(conflictStep, packet, callResponse);
      }

      @Override
      public NextAction onSuccess(
          Packet packet, CallResponse<V1beta1CustomResourceDefinition> callResponse) {
        LOGGER.info(MessageKeys.CREATING_CRD, callResponse);
        return doNext(packet);
      }

      @Override
      protected NextAction onFailureNoRetry(Packet packet, CallResponse<V1beta1CustomResourceDefinition> callResponse) {
        LOGGER.info(MessageKeys.CREATE_CRD_FAILED, callResponse);
        return isNotAuthorizedOrForbidden(callResponse)
            ? doNext(packet) : super.onFailureNoRetry(packet, callResponse);
      }
    }

    private class ReplaceResponseStep extends ResponseStep<V1CustomResourceDefinition> {
      ReplaceResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onFailure(
          Packet packet, CallResponse<V1CustomResourceDefinition> callResponse) {
        return super.onFailure(conflictStep, packet, callResponse);
      }

      @Override
      public NextAction onSuccess(
          Packet packet, CallResponse<V1CustomResourceDefinition> callResponse) {
        LOGGER.info(MessageKeys.CREATING_CRD, callResponse);
        return doNext(packet);
      }

      @Override
      protected NextAction onFailureNoRetry(Packet packet, CallResponse<V1CustomResourceDefinition> callResponse) {
        LOGGER.info(MessageKeys.REPLACE_CRD_FAILED, callResponse);
        return isNotAuthorizedOrForbidden(callResponse)
           || ((callResponse.getE().getCause() instanceof StreamResetException) 
           && (callResponse.getExceptionString().contains(NO_ERROR)))
           ? doNext(packet) : super.onFailureNoRetry(packet, callResponse);
      }      
    }

    private class ReplaceBetaResponseStep extends ResponseStep<V1beta1CustomResourceDefinition> {
      ReplaceBetaResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onFailure(
          Packet packet, CallResponse<V1beta1CustomResourceDefinition> callResponse) {
        return super.onFailure(conflictStep, packet, callResponse);
      }

      @Override
      public NextAction onSuccess(
          Packet packet, CallResponse<V1beta1CustomResourceDefinition> callResponse) {
        LOGGER.info(MessageKeys.CREATING_CRD, callResponse);
        return doNext(packet);
      }

      @Override
      protected NextAction onFailureNoRetry(Packet packet, CallResponse<V1beta1CustomResourceDefinition> callResponse) {
        LOGGER.info(MessageKeys.REPLACE_CRD_FAILED, callResponse);
        return isNotAuthorizedOrForbidden(callResponse)
           || ((callResponse.getE().getCause() instanceof StreamResetException) 
           && (callResponse.getExceptionString().contains(NO_ERROR)))
           ? doNext(packet) : super.onFailureNoRetry(packet, callResponse);
      }
    }
  }

  static class CrdComparatorImpl implements CrdComparator {
    @Override
    public boolean isOutdatedCrd(SemanticVersion productVersion,
        V1CustomResourceDefinition actual, V1CustomResourceDefinition expected) {
      ResourceVersion current = new ResourceVersion(KubernetesConstants.DOMAIN_VERSION);
      List<ResourceVersion> actualVersions = getVersions(actual);

      for (ResourceVersion v : actualVersions) {
        if (!isLaterOrEqual(v, current)) {
          return false;
        }
      }

      // Check product version label
      if (productVersion != null) {
        SemanticVersion currentCrdVersion = getProductVersionFromMetadata(actual.getMetadata());
        if (currentCrdVersion == null || productVersion.compareTo(currentCrdVersion) < 0) {
          return false;
        }
      }

      return getSchemaValidation(actual) == null
          || !getSchemaValidation(expected).equals(getSchemaValidation(actual));
    }

    @Override
    public boolean isOutdatedBetaCrd(SemanticVersion productVersion,
        V1beta1CustomResourceDefinition actual, V1beta1CustomResourceDefinition expected) {
      ResourceVersion current = new ResourceVersion(KubernetesConstants.DOMAIN_VERSION);
      List<ResourceVersion> actualVersions = getBetaVersions(actual);

      for (ResourceVersion v : actualVersions) {
        if (!isLaterOrEqual(v, current)) {
          return false;
        }
      }

      // Check product version label
      if (productVersion != null) {
        SemanticVersion currentCrdVersion = getProductVersionFromMetadata(actual.getMetadata());
        if (currentCrdVersion == null || productVersion.compareTo(currentCrdVersion) < 0) {
          return false;
        }
      }

      return getBetaSchemaValidation(actual) == null
          || !getBetaSchemaValidation(expected).equals(getBetaSchemaValidation(actual));
    }

    private SemanticVersion getProductVersionFromMetadata(V1ObjectMeta metadata) {
      return Optional.ofNullable(metadata)
              .map(V1ObjectMeta::getLabels)
              .map(labels -> labels.get(LabelConstants.OPERATOR_VERISON))
              .map(SemanticVersion::new)
              .orElse(null);
    }

    // true, if version is later than base
    private boolean isLaterOrEqual(ResourceVersion base, ResourceVersion version) {
      if (!version.getVersion().equals(base.getVersion())) {
        return version.getVersion().compareTo(base.getVersion()) >= 0;
      }

      if (version.getPrerelease() == null) {
        if (base.getPrerelease() != null) {
          return true;
        }
      } else if (!version.getPrerelease().equals(base.getPrerelease())) {
        if (base.getPrerelease() == null) {
          return false;
        }
        return "alpha".equals(base.getPrerelease());
      }

      if (version.getPrereleaseVersion() == null) {
        return base.getPrereleaseVersion() == null;
      } else if (base.getPrereleaseVersion() == null) {
        return true;
      }
      return version.getPrereleaseVersion() >= base.getPrereleaseVersion();
    }

    private V1JSONSchemaProps getSchemaValidation(V1CustomResourceDefinition crd) {
      if (crd != null && crd.getSpec() != null && crd.getSpec().getVersions() != null) {
        for (V1CustomResourceDefinitionVersion version : crd.getSpec().getVersions()) {
          if (KubernetesConstants.DOMAIN_VERSION.equals(version.getName())) {
            V1CustomResourceValidation schema = version.getSchema();
            if (schema != null) {
              return schema.getOpenAPIV3Schema();
            }
          }
        }
      }
      return null;
    }

    private V1beta1JSONSchemaProps getBetaSchemaValidation(V1beta1CustomResourceDefinition crd) {
      if (crd != null && crd.getSpec() != null) {
        V1beta1CustomResourceValidation validation = crd.getSpec().getValidation();
        if (validation != null) {
          return validation.getOpenAPIV3Schema();
        }
      }
      return null;
    }
  }
}
