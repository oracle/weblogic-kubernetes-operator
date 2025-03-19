// Copyright (c) 2018, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.IOException;
import java.io.Serial;
import java.io.StringReader;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.ToNumberPolicy;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.kubernetes.client.openapi.models.ApiextensionsV1ServiceReference;
import io.kubernetes.client.openapi.models.ApiextensionsV1WebhookClientConfig;
import io.kubernetes.client.openapi.models.V1CustomResourceConversion;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionNames;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionSpec;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionVersion;
import io.kubernetes.client.openapi.models.V1CustomResourceSubresourceScale;
import io.kubernetes.client.openapi.models.V1CustomResourceSubresources;
import io.kubernetes.client.openapi.models.V1CustomResourceValidation;
import io.kubernetes.client.openapi.models.V1JSONSchemaProps;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1WebhookConversion;
import io.kubernetes.client.util.Yaml;
import okhttp3.internal.http2.StreamResetException;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.utils.Certificates;
import oracle.kubernetes.operator.utils.PathSupport;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import org.apache.commons.codec.binary.Base64;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import static oracle.kubernetes.operator.KubernetesConstants.CLUSTER_CRD_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.WEBHOOK;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getWebhookNamespace;
import static oracle.kubernetes.operator.http.rest.RestConfigImpl.CONVERSION_WEBHOOK_HTTPS_PORT;
import static oracle.kubernetes.operator.utils.SelfSignedCertUtils.WEBLOGIC_OPERATOR_WEBHOOK_SVC;
import static oracle.kubernetes.weblogic.domain.model.CrdSchemaGenerator.createCrdSchemaGenerator;

/** Helper class to ensure Domain CRD is created. */
public class CrdHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final String SCHEMA_LOCATION = "/schema";
  private static final String NO_ERROR = "NO_ERROR";
  private static final CrdComparator COMPARATOR = new CrdComparatorImpl();

  private static final FileGroupReader schemaReader = new FileGroupReader(SCHEMA_LOCATION);
  public static final String VERSION_V1 = "v1";
  public static final String WEBHOOK_PATH = "/webhook";

  private enum CrdType {
    DOMAIN {
      @Override
      CrdContext createContext() {
        return new DomainCrdContext();
      }
    }, CLUSTER {
      @Override
      CrdContext createContext() {
        return new ClusterCrdContext();
      }
    };

    abstract CrdContext createContext();
  }

  private CrdHelper() {
  }

  /**
   * Used by build to generate crd-validation.yaml.
   * @param args Arguments that must be one value giving file name to create
   */
  public static void main(String... args) throws URISyntaxException {
    if (args == null || args.length != CrdType.values().length) {
      throw new IllegalArgumentException();
    }

    writeCrdFiles(args);
  }

  static void writeCrdFiles(String... filenames) throws URISyntaxException {
    for (CrdType type : CrdType.values()) {
      final URI outputFile = asFileURI(filenames[type.ordinal()]);
      writeAsYaml(outputFile, type.createContext().model);
    }
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

  static void writeAsYaml(URI outputFileName, Object model) {
    try (Writer writer = Files.newBufferedWriter(PathSupport.getPath(outputFileName))) {
      writer.write(
              """
              # Copyright (c) 2020, 2025, Oracle and/or its affiliates.
              # Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
              """);
      writer.write("\n");
      dumpYaml(writer, model);
    } catch (IOException io) {
      throw new CrdCreationException(io);
    } catch (IllegalArgumentException e) {
      throw new CrdCreationException("Bad argument: " + outputFileName, e);
    }
  }

  private static class SimpleNumberTypeAdapter extends TypeAdapter<Double> {
    @Override
    public void write(JsonWriter out, Double value) throws IOException {
      if (value != null && value.equals(Math.rint(value))) {
        out.value(value.longValue());
      } else {
        out.value(value);
      }
    }

    @Override
    public Double read(JsonReader in) throws IOException {
      throw new IllegalStateException();
    }
  }

  // Writes a YAML representation of the specified model to the writer. First converts to JSON in order
  // to respect the @SerializedName annotation.
  @SuppressWarnings("unchecked")
  private static void dumpYaml(Writer writer, Object model) {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE);
    gsonBuilder.registerTypeAdapter(Double.class, new SimpleNumberTypeAdapter());
    final Gson gson = gsonBuilder.create();
    Map<String, Object> map = gson.fromJson(gson.toJson(model), Map.class);
    Yaml.dump(map, writer);
  }

  public static Step createDomainCrdStep(SemanticVersion productVersion) {
    return new DomainCrdStep(productVersion, null);
  }

  public static Step createDomainCrdStep(SemanticVersion productVersion,
                                         Certificates certificates) {
    return new DomainCrdStep(productVersion, certificates);
  }

  interface CrdComparator {
    boolean isOutdatedCrd(
        SemanticVersion productVersion, String resourceVersion,
        V1CustomResourceDefinition actual, V1CustomResourceDefinition expected);
  }

  static class DomainCrdStep extends Step {
    final CrdContext context;

    DomainCrdStep(SemanticVersion productVersion, Certificates certificates) {
      context = new DomainCrdContext(productVersion, this, certificates);
    }

    @Override
    public NextAction apply(Packet packet) {
      return doNext(context.verifyCrd(getNext()), packet);
    }
  }

  public static Step createClusterCrdStep(SemanticVersion productVersion) {
    return new ClusterCrdStep(productVersion);
  }

  static class ClusterCrdStep extends Step {
    final CrdContext context;

    ClusterCrdStep(SemanticVersion productVersion) {
      context = new ClusterCrdContext(productVersion, this);
    }

    @Override
    public NextAction apply(Packet packet) {
      return doNext(context.verifyCrd(getNext()), packet);
    }
  }

  @SuppressWarnings("ConstantConditions")
  abstract static class CrdContext {
    private final Step conflictStep;
    private final V1CustomResourceDefinition model;

    private final SemanticVersion productVersion;
    private final Certificates certificates;

    CrdContext() {
      this(null, null, null);
    }

    CrdContext(SemanticVersion productVersion, Step conflictStep, Certificates certificates) {
      this.productVersion = productVersion;
      this.conflictStep = conflictStep;
      this.certificates = certificates;
      this.model = createModel(productVersion, certificates);
    }

    /**
     * Returns the string used by Kubernetes to distinguish the kind of resource.
     */
    abstract String getKind();

    /**
     * Returns the name of the generated CRD.
     */
    abstract String getCrdName();

    /**
     * Returns the version of the CRD.
     */
    abstract String getVersionString();

    /**
     * Returns the name that Kubernetes will use for kubectl commands dealing with single resources.
     */
    abstract String getSingularName();

    /**
     * Returns the name that Kubernetes will use for kubectl commands dealing with multiple resources.
     */
    abstract String getPluralName();

    /**
     * Returns a list of name abbreviations that Kubernetes will recognize.
     */
    abstract List<String> getShortNames();

    /**
     * Returns the class that represents the spec portion of the resource.
     */
    abstract Class<?> getSpecClass();

    /**
     * Returns the class that represents the status portion of the resource.
     */
    abstract Class<?> getStatusClass();

    /**
     * Returns the class that represents the resource.
     */
    abstract Class<?> getResourceClass();

    /**
     * Returns a prefix which identifies the CRD schema files for this type of resource.
     */
    abstract String getPrefix();

    V1CustomResourceDefinition createModel(SemanticVersion productVersion, Certificates certificates) {
      V1CustomResourceDefinition result = new V1CustomResourceDefinition()
          .apiVersion("apiextensions.k8s.io/v1")
          .kind("CustomResourceDefinition")
          .metadata(createMetadata(productVersion))
          .spec(createSpec(certificates));
      return AnnotationHelper.withSha256Hash(result,
          Objects.requireNonNull(
              result.getSpec().getVersions().stream().findFirst().orElseThrow().getSchema()).getOpenAPIV3Schema());
    }

    V1ObjectMeta createMetadata(SemanticVersion productVersion) {
      V1ObjectMeta metadata = new V1ObjectMeta()
          .name(getCrdName());

      if (productVersion != null) {
        metadata.putLabelsItem(LabelConstants.OPERATOR_VERSION, productVersion.toString());
      }
      return metadata;
    }

    V1CustomResourceDefinitionSpec createSpec(Certificates certificates) {
      return new V1CustomResourceDefinitionSpec()
          .group(KubernetesConstants.DOMAIN_GROUP)
          .preserveUnknownFields(false)
          .versions(getCrdVersions())
          .scope("Namespaced")
          .names(getCrdNames())
          .conversion(createConversionWebhook(certificates));
    }

    private static V1CustomResourceConversion createConversionWebhook(Certificates certificates) {
      return createConversionWebhook(getCertificateData(certificates));
    }

    public static V1CustomResourceConversion createConversionWebhook(String certificateData) {
      return Optional.ofNullable(getCABundle(certificateData)).map(CrdContext::createConversionWebhook).orElse(null);
    }

    private static V1CustomResourceConversion createConversionWebhook(byte[] caBundle) {
      return new V1CustomResourceConversion().strategy(WEBHOOK)
              .webhook(new V1WebhookConversion().conversionReviewVersions(
                      List.of(VERSION_V1)).clientConfig(new ApiextensionsV1WebhookClientConfig()
                      .service(new ApiextensionsV1ServiceReference().name(WEBLOGIC_OPERATOR_WEBHOOK_SVC)
                              .namespace(getWebhookNamespace()).port(CONVERSION_WEBHOOK_HTTPS_PORT)
                              .path(WEBHOOK_PATH))
                      .caBundle(caBundle)));
    }

    private static String getCertificateData(Certificates certificates) {
      return Optional.ofNullable(certificates).map(Certificates::getWebhookCertificateData).orElse(null);
    }

    private static byte[] getCABundle(String certificateData) {
      return Optional.ofNullable(certificateData).map(Base64::decodeBase64).orElse(null);
    }

    static V1CustomResourceValidation getValidationFromCrdSchemaFile(String fileContents) {
      Map<String, Object> data = getSnakeYaml().load(new StringReader(fileContents));
      final Gson gson = new Gson();
      return gson.fromJson(gson.toJsonTree(data), V1CustomResourceValidation.class);
    }

    private static org.yaml.snakeyaml.Yaml getSnakeYaml() {
      LoaderOptions loaderOptions = new LoaderOptions();
      loaderOptions.setEnumCaseSensitive(false);
      return new org.yaml.snakeyaml.Yaml(new SafeConstructor(new LoaderOptions()), new Yaml.CustomRepresenter());
    }

    static V1CustomResourceSubresources createSubresources() {
      return new V1CustomResourceSubresources()
          .status(new HashMap<String, String>()) // this just needs an empty object to enable status subresource
          .scale(
              new V1CustomResourceSubresourceScale()
                  .specReplicasPath(".spec.replicas")
                  .statusReplicasPath(".status.replicas")
                  .labelSelectorPath(".status.labelSelector"));
    }

    List<V1CustomResourceDefinitionVersion> getCrdVersions() {
      List<V1CustomResourceDefinitionVersion> list = new ArrayList<>();
      list.add(createNewVersion());
      getExistingVersions().forEach(list::add);
      return list;
    }

    @Nonnull
    private Stream<V1CustomResourceDefinitionVersion> getExistingVersions() {
      final Map<String, String> schemas = schemaReader.loadFilesFromClasspath();
      return schemas.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .filter(entry -> getVersionFromCrdSchemaFileName(entry.getKey()) != null)
          .map(entry -> new V1CustomResourceDefinitionVersion()
              .name(getVersionFromCrdSchemaFileName(entry.getKey()))
              .schema(getValidationFromCrdSchemaFile(entry.getValue()))
              .subresources(createSubresources())
              .served(true)
              .storage(false));
    }

    private String getVersionFromCrdSchemaFileName(String name) {
      final Pattern versionPattern = Pattern.compile(getPrefix() + "-schema(\\w*)-");
      final Matcher matcher = versionPattern.matcher(name);
      return matcher.find() ? matcher.group(1) : null;
    }

    private V1CustomResourceDefinitionVersion createNewVersion() {
      return new V1CustomResourceDefinitionVersion()
          .name(getVersionString())
          .schema(createSchemaValidation())
          .subresources(createSubresources())
          .served(true)
          .storage(true);
    }

    V1CustomResourceDefinitionNames getCrdNames() {
      return new V1CustomResourceDefinitionNames()
          .plural(getPluralName())
          .singular(getSingularName())
          .kind(getKind())
          .shortNames(getShortNames())
          .categories(getCategories());
    }

    private List<String> getCategories() {
      return List.of("all", "oracle", "weblogic");
    }

    V1CustomResourceValidation createSchemaValidation() {
      return new V1CustomResourceValidation().openAPIV3Schema(createOpenApiV3Schema());
    }

    private V1JSONSchemaProps createOpenApiV3Schema() {
      GsonBuilder gsonBuilder = new GsonBuilder();
      gsonBuilder.setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE);
      Gson gson = gsonBuilder.create();

      JsonElement jsonElementSpec = gson.toJsonTree(createCrdSchemaGenerator().generate(getSpecClass()));
      V1JSONSchemaProps spec = gson.fromJson(jsonElementSpec, V1JSONSchemaProps.class);

      JsonElement jsonElementStatus = gson.toJsonTree(createCrdSchemaGenerator().generate(getStatusClass()));
      V1JSONSchemaProps status = gson.fromJson(jsonElementStatus, V1JSONSchemaProps.class);

      String description = getResourceClass().getAnnotation(Description.class).value();

      return new V1JSONSchemaProps()
          .type("object")
          .description(description)
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

    Step updateExistingCrd(Step next, V1CustomResourceDefinition existingCrd) {
      List<V1CustomResourceDefinitionVersion> versions = existingCrd.getSpec().getVersions();
      for (V1CustomResourceDefinitionVersion crdVersion : versions) {
        crdVersion.setStorage(false);
      }
      versions.add(0,
          new V1CustomResourceDefinitionVersion()
              .name(getVersionString())
              .schema(createSchemaValidation())
              .subresources(createSubresources())
              .served(true)
              .storage(true));

      return new CallBuilder().replaceCustomResourceDefinitionAsync(
          existingCrd.getMetadata().getName(), existingCrd, createReplaceResponseStep(next));
    }

    Step updateExistingCrdWithConversion(Step next, V1CustomResourceDefinition existingCrd) {
      existingCrd.getSpec().conversion(createConversionWebhook(certificates));
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

      private boolean existingCrdContainsCompatibleConversionWebhook(V1CustomResourceDefinition existingCrd) {
        return isClusterCrd(existingCrd)
            || (hasConversionWebhookStrategy(existingCrd) && webhookIsCompatible(existingCrd));
      }

      private boolean isClusterCrd(V1CustomResourceDefinition existingCrd) {
        return existingCrd.getMetadata().getName().equals(CLUSTER_CRD_NAME);
      }

      private Boolean hasConversionWebhookStrategy(V1CustomResourceDefinition existingCrd) {
        return Optional.ofNullable(existingCrd.getSpec().getConversion())
            .map(c -> c.getStrategy().equalsIgnoreCase(WEBHOOK)).orElse(false);
      }

      private boolean webhookIsCompatible(V1CustomResourceDefinition existingCrd) {
        return crdVersionHigherThanProductVersion(existingCrd)
            || createConversionWebhook(getCaBundle()).equals(getConversionWebhook(existingCrd));
      }

      private byte[] getCaBundle() {
        return Optional.ofNullable(getCertificateData(certificates)).map(Base64::decodeBase64).orElse(null);
      }

      private V1CustomResourceConversion getConversionWebhook(V1CustomResourceDefinition existingCrd) {
        return Optional.ofNullable(existingCrd.getSpec())
            .map(V1CustomResourceDefinitionSpec::getConversion).orElse(null);
      }

      private boolean crdVersionHigherThanProductVersion(V1CustomResourceDefinition existingCrd) {
        if (productVersion == null) {
          return false;
        }
        
        SemanticVersion existingCrdVersion = KubernetesUtils.getProductVersionFromMetadata(existingCrd.getMetadata());
        return existingCrdVersion != null && existingCrdVersion.compareTo(productVersion) > 0;
      }

      private boolean isOutdatedCrd(V1CustomResourceDefinition existingCrd) {
        return COMPARATOR.isOutdatedCrd(productVersion, getVersionString(), existingCrd, CrdContext.this.model);
      }

      private boolean existingCrdContainsVersion(V1CustomResourceDefinition existingCrd) {
        List<V1CustomResourceDefinitionVersion> versions = existingCrd.getSpec().getVersions();
        boolean found = false;
        if (versions != null) {
          for (V1CustomResourceDefinitionVersion v : versions) {
            if (getVersionString().equals(v.getName())) {
              found = true;
              break;
            }
          }
        }

        return found;
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
        } else if (!existingCrdContainsCompatibleConversionWebhook(existingCrd)) {
          return doNext(updateExistingCrdWithConversion(getNext(), existingCrd), packet);
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

  static class DomainCrdContext extends CrdContext {

    DomainCrdContext() {
    }

    DomainCrdContext(SemanticVersion productVersion, Step conflictStep, Certificates certificates) {
      super(productVersion, conflictStep, certificates);
    }

    @Override
    Class<?> getSpecClass() {
      return DomainSpec.class;
    }

    @Override
    Class<?> getStatusClass() {
      return DomainStatus.class;
    }

    @Override
    Class<?> getResourceClass() {
      return DomainResource.class;
    }

    @Override
    protected String getSingularName() {
      return KubernetesConstants.DOMAIN_SINGULAR;
    }

    @Override
    protected String getKind() {
      return KubernetesConstants.DOMAIN;
    }

    @Override
    protected List<String> getShortNames() {
      return Collections.singletonList(KubernetesConstants.DOMAIN_SHORT);
    }

    @Override
    protected String getPluralName() {
      return KubernetesConstants.DOMAIN_PLURAL;
    }

    @Override
    protected String getVersionString() {
      return KubernetesConstants.DOMAIN_VERSION;
    }

    @Override
    protected String getCrdName() {
      return KubernetesConstants.DOMAIN_CRD_NAME;
    }

    @Override
    protected String getPrefix() {
      return "domain-crd";
    }
  }

  static class ClusterCrdContext extends CrdContext {

    ClusterCrdContext() {
    }

    ClusterCrdContext(SemanticVersion productVersion, Step conflictStep) {
      super(productVersion, conflictStep, null);
    }

    @Override
    Class<?> getSpecClass() {
      return ClusterSpec.class;
    }

    @Override
    Class<?> getStatusClass() {
      return ClusterStatus.class;
    }

    @Override
    Class<?> getResourceClass() {
      return ClusterResource.class;
    }

    @Override
    protected String getSingularName() {
      return KubernetesConstants.CLUSTER_SINGULAR;
    }

    @Override
    protected String getKind() {
      return KubernetesConstants.CLUSTER;
    }

    @Override
    protected List<String> getShortNames() {
      return List.of(KubernetesConstants.CLUSTER_SHORT);
    }

    @Override
    protected String getPluralName() {
      return KubernetesConstants.CLUSTER_PLURAL;
    }

    @Override
    protected String getVersionString() {
      return KubernetesConstants.CLUSTER_VERSION;
    }

    @Override
    protected String getCrdName() {
      return CLUSTER_CRD_NAME;
    }

    @Override
    protected String getPrefix() {
      return "cluster-crd";
    }
  }

  static class CrdComparatorImpl implements CrdComparator {
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

    @Override
    public boolean isOutdatedCrd(SemanticVersion productVersion, String resourceVersionString,
                                 V1CustomResourceDefinition actual, V1CustomResourceDefinition expected) {
      // Check product version label
      if (productVersion != null) {
        SemanticVersion currentCrdVersion = KubernetesUtils.getProductVersionFromMetadata(actual.getMetadata());
        if (currentCrdVersion == null) {
          return false;
        }
        int compareToResult = productVersion.compareTo(currentCrdVersion);
        if (compareToResult < 0) {
          return false;
        } else if (compareToResult > 0) {
          return true;
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

  static class CrdCreationException extends RuntimeException {
    @Serial
    private static final long serialVersionUID  = 1L;

    public CrdCreationException(String message, Exception e) {
      super(message, e);
    }

    public CrdCreationException(Exception e) {
      super(e);
    }
  }
}
