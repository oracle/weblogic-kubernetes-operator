// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
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
import io.kubernetes.client.util.Yaml;
import okhttp3.internal.http2.StreamResetException;
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
import org.yaml.snakeyaml.constructor.SafeConstructor;

import static oracle.kubernetes.weblogic.domain.model.CrdSchemaGenerator.createCrdSchemaGenerator;

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
  public static void main(String[] args) throws URISyntaxException {
    if (args == null || args.length != 1) {
      throw new IllegalArgumentException();
    }

    writeCrdFiles(args[0]);
  }

  static void writeCrdFiles(String crdFileName) throws URISyntaxException {
    CrdContext context = new CrdContext(null, null, null);

    final URI outputFile = asFileURI(crdFileName);

    writeAsYaml(outputFile, context.model);
  }

  private static URI asFileURI(String fileName) throws URISyntaxException {
    if (fileName.startsWith("file:/")) {
      return new URI(fileName);
    } else if (fileName.startsWith("/")) {
      return asFileURI("file:" + fileName);
    } else {
      return asFileURI("file:/" + fileName);
    }
  }

  @SuppressWarnings("FieldMayBeFinal") // allow unit tests to set this
  private static Function<URI, Path> uriToPath = Paths::get;

  static void writeAsYaml(URI outputFileName, Object model) {
    try (Writer writer = Files.newBufferedWriter(uriToPath.apply(outputFileName))) {
      writer.write(
            "# Copyright (c) 2020, 2021, Oracle and/or its affiliates.\n"
                  + "# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.\n");
      writer.write("\n");
      dumpYaml(writer, model);
    } catch (IOException io) {
      throw new RuntimeException(io);
    } catch (IllegalArgumentException e) {
      throw new RuntimeException("Bad argument: " + outputFileName, e);
    }
  }

  // Writes a YAML representation of the specified model to the writer. First converts to JSON in order
  // to respect the @SerializedName annotation.
  @SuppressWarnings("unchecked")
  private static void dumpYaml(Writer writer, Object model) {
    final Gson gson = new Gson();
    Map<String,Object> map = gson.fromJson(gson.toJson(model), Map.class);
    Yaml.dump(map, writer);
  }
  // a = gson.toJson(model)
  // Map = gson.fromJson(Map.class)
  // yaml dump ?  // ordering and format likely to change massively

  public static Step createDomainCrdStep(KubernetesVersion version, SemanticVersion productVersion) {
    return new CrdStep(version, productVersion);
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

  interface CrdComparator {
    boolean isOutdatedCrd(
        SemanticVersion productVersion, V1CustomResourceDefinition actual, V1CustomResourceDefinition expected);
  }

  static class CrdStep extends Step {
    final CrdContext context;

    CrdStep(KubernetesVersion version, SemanticVersion productVersion) {
      context = new CrdContext(version, productVersion, this);
    }

    @Override
    public NextAction apply(Packet packet) {
      return doNext(context.verifyCrd(getNext()), packet);
    }
  }

  @SuppressWarnings("ConstantConditions")
  static class CrdContext {
    private final Step conflictStep;
    private final V1CustomResourceDefinition model;
    private final KubernetesVersion version;
    private final SemanticVersion productVersion;

    CrdContext(KubernetesVersion version, SemanticVersion productVersion, Step conflictStep) {
      this.version = version;
      this.productVersion = productVersion;
      this.conflictStep = conflictStep;
      this.model = createModel(productVersion);
    }

    static V1CustomResourceDefinition createModel(SemanticVersion productVersion) {
      V1CustomResourceDefinition model = new V1CustomResourceDefinition()
          .apiVersion("apiextensions.k8s.io/v1")
          .kind("CustomResourceDefinition")
          .metadata(createMetadata(productVersion))
          .spec(createSpec());
      return AnnotationHelper.withSha256Hash(model,
          Objects.requireNonNull(
              model.getSpec().getVersions().stream().findFirst().orElseThrow().getSchema()).getOpenAPIV3Schema());
    }

    static V1ObjectMeta createMetadata(SemanticVersion productVersion) {
      V1ObjectMeta metadata = new V1ObjectMeta()
          .name(KubernetesConstants.CRD_NAME);

      if (productVersion != null) {
        metadata.putLabelsItem(LabelConstants.OPERATOR_VERSION, productVersion.toString());
      }
      return metadata;
    }

    static V1CustomResourceDefinitionSpec createSpec() {
      return new V1CustomResourceDefinitionSpec()
          .group(KubernetesConstants.DOMAIN_GROUP)
          .preserveUnknownFields(false)
          .versions(getCrdVersions())
          .scope("Namespaced")
          .names(getCrdNames());
    }

    static String getVersionFromCrdSchemaFileName(String name) {
      // names will be like "domain-crd-schemav2-201.yaml"
      // want "v2"
      String end = name.substring(17);
      return end.substring(0, end.indexOf('-'));
    }

    static V1CustomResourceValidation getValidationFromCrdSchemaFile(String fileContents) {
      Map<String, Object> data = getSnakeYaml(null).load(new StringReader(fileContents));
      final Gson gson = new Gson();
      return gson.fromJson(gson.toJsonTree(data), V1CustomResourceValidation.class);
    }

    private static org.yaml.snakeyaml.Yaml getSnakeYaml(Class<?> type) {
      return type != null ? new org.yaml.snakeyaml.Yaml(new Yaml.CustomConstructor(type),
          new Yaml.CustomRepresenter()) :
          new org.yaml.snakeyaml.Yaml(new SafeConstructor(), new Yaml.CustomRepresenter());
    }

    static V1CustomResourceSubresources createSubresources() {
      return new V1CustomResourceSubresources()
          .status(new HashMap<String, String>()) // this just needs an empty object to enable status subresource
          .scale(
              new V1CustomResourceSubresourceScale()
                  .specReplicasPath(".spec.replicas")
                  .statusReplicasPath(".status.replicas"));
    }

    static List<V1CustomResourceDefinitionVersion> getCrdVersions() {
      Map<String, String> schemas = schemaReader.loadFilesFromClasspath();
      List<V1CustomResourceDefinitionVersion> versions = schemas.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
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

    static V1CustomResourceDefinitionNames getCrdNames() {
      return new V1CustomResourceDefinitionNames()
          .plural(KubernetesConstants.DOMAIN_PLURAL)
          .singular(KubernetesConstants.DOMAIN_SINGULAR)
          .kind(KubernetesConstants.DOMAIN)
          .shortNames(Collections.singletonList(KubernetesConstants.DOMAIN_SHORT));
    }

    static V1CustomResourceValidation createSchemaValidation() {
      return new V1CustomResourceValidation().openAPIV3Schema(createOpenApiV3Schema());
    }

    static V1JSONSchemaProps createOpenApiV3Schema() {
      Gson gson = new Gson();
      JsonElement jsonElementSpec =
          gson.toJsonTree(createCrdSchemaGenerator().generate(DomainSpec.class));
      V1JSONSchemaProps spec = gson.fromJson(jsonElementSpec, V1JSONSchemaProps.class);
      JsonElement jsonElementStatus =
          gson.toJsonTree(createCrdSchemaGenerator().generate(DomainStatus.class));
      V1JSONSchemaProps status =
          gson.fromJson(jsonElementStatus, V1JSONSchemaProps.class);
      return new V1JSONSchemaProps()
          .type("object")
          .putPropertiesItem("spec", spec)
          .putPropertiesItem("status", status);
    }

    Step verifyCrd(Step next) {
      return new CallBuilder().readCustomResourceDefinitionAsync(
          model.getMetadata().getName(), createReadResponseStep(next));
    }

    ResponseStep<V1CustomResourceDefinition> createReadResponseStep(Step next) {
      return new ReadResponseStep(next);
    }

    Step createCrd(Step next) {
      return new CallBuilder().createCustomResourceDefinitionAsync(
          model, createCreateResponseStep(next));
    }

    ResponseStep<V1CustomResourceDefinition> createCreateResponseStep(Step next) {
      return new CreateResponseStep(next);
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

    Step updateExistingCrd(Step next, V1CustomResourceDefinition existingCrd) {
      List<V1CustomResourceDefinitionVersion> versions = existingCrd.getSpec().getVersions();
      for (V1CustomResourceDefinitionVersion version : versions) {
        version.setStorage(false);
      }
      versions.add(0,
          new V1CustomResourceDefinitionVersion()
              .name(KubernetesConstants.DOMAIN_VERSION)
              .schema(createSchemaValidation())
              .subresources(createSubresources())
              .served(true)
              .storage(true));

      return new CallBuilder().replaceCustomResourceDefinitionAsync(
          existingCrd.getMetadata().getName(), existingCrd, createReplaceResponseStep(next));
    }

    Step updateCrd(Step next, V1CustomResourceDefinition existingCrd) {
      model.getMetadata().setResourceVersion(existingCrd.getMetadata().getResourceVersion());

      return new CallBuilder().replaceCustomResourceDefinitionAsync(
          model.getMetadata().getName(), model, createReplaceResponseStep(next));
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
          return doNext(createCrd(getNext()), packet);
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
        LOGGER.info(MessageKeys.CREATING_CRD, callResponse.getResult().getMetadata().getName());
        return doNext(packet);
      }

      @Override
      protected NextAction onFailureNoRetry(Packet packet, CallResponse<V1CustomResourceDefinition> callResponse) {
        LOGGER.info(MessageKeys.CREATE_CRD_FAILED, callResponse.getE().getResponseBody());
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
        LOGGER.info(MessageKeys.CREATING_CRD, callResponse.getResult().getMetadata().getName());
        return doNext(packet);
      }

      @Override
      protected NextAction onFailureNoRetry(Packet packet, CallResponse<V1CustomResourceDefinition> callResponse) {
        LOGGER.info(MessageKeys.REPLACE_CRD_FAILED, callResponse.getE().getResponseBody());
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
        SemanticVersion currentCrdVersion = KubernetesUtils.getProductVersionFromMetadata(actual.getMetadata());
        if (currentCrdVersion == null || productVersion.compareTo(currentCrdVersion) < 0) {
          return false;
        }
      }

      return !AnnotationHelper.getHash(expected).equals(AnnotationHelper.getHash(actual));
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
  }
}
