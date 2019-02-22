// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.VersionConstants.DEFAULT_OPERATOR_VERSION;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1beta1CustomResourceDefinition;
import io.kubernetes.client.models.V1beta1CustomResourceDefinitionNames;
import io.kubernetes.client.models.V1beta1CustomResourceDefinitionSpec;
import io.kubernetes.client.models.V1beta1CustomResourceSubresourceScale;
import io.kubernetes.client.models.V1beta1CustomResourceSubresources;
import io.kubernetes.client.models.V1beta1CustomResourceValidation;
import io.kubernetes.client.models.V1beta1JSONSchemaProps;
import java.util.Collections;
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
import oracle.kubernetes.weblogic.domain.v2.DomainSpec;
import oracle.kubernetes.weblogic.domain.v2.DomainStatus;

/** Helper class to ensure Domain CRD is created. */
public class CRDHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final CRDComparator COMPARATOR = new CRDComparatorImpl();

  private CRDHelper() {}

  /**
   * Factory for {@link Step} that creates Domain CRD.
   *
   * @param version Version of the Kubernetes API Server
   * @param next Next step
   * @return Step for creating Domain custom resource definition
   */
  public static Step createDomainCRDStep(KubernetesVersion version, Step next) {
    return new CRDStep(version, next);
  }

  static class CRDStep extends Step {
    CRDContext context;

    CRDStep(KubernetesVersion version, Step next) {
      super(next);
      context = new CRDContext(version, this);
    }

    @Override
    public NextAction apply(Packet packet) {
      return doNext(context.verifyCRD(getNext()), packet);
    }
  }

  static class CRDContext {
    private final Step conflictStep;
    private final V1beta1CustomResourceDefinition model;
    private final KubernetesVersion version;

    CRDContext(KubernetesVersion version, Step conflictStep) {
      this.version = version;
      this.conflictStep = conflictStep;
      this.model = createModel(version);
    }

    static V1beta1CustomResourceDefinition createModel(KubernetesVersion version) {
      return new V1beta1CustomResourceDefinition()
          .apiVersion("apiextensions.k8s.io/v1beta1")
          .kind("CustomResourceDefinition")
          .metadata(createMetadata())
          .spec(createSpec(version));
    }

    static V1ObjectMeta createMetadata() {
      return new V1ObjectMeta()
          .name(KubernetesConstants.CRD_NAME)
          .putLabelsItem(LabelConstants.RESOURCE_VERSION_LABEL, DEFAULT_OPERATOR_VERSION);
    }

    static V1beta1CustomResourceDefinitionSpec createSpec(KubernetesVersion version) {
      V1beta1CustomResourceDefinitionSpec spec =
          new V1beta1CustomResourceDefinitionSpec()
              .group(KubernetesConstants.DOMAIN_GROUP)
              .version(KubernetesConstants.DOMAIN_VERSION)
              .scope("Namespaced")
              .names(getCRDNames())
              .validation(createSchemaValidation());
      if (version.isCRDSubresourcesSupported()) {
        spec.setSubresources(
            new V1beta1CustomResourceSubresources()
                .scale(
                    new V1beta1CustomResourceSubresourceScale()
                        .specReplicasPath(".spec.replicas")
                        .statusReplicasPath(".status.replicas")));
        // Remove status for now because seeing status not updated on some k8s environments
        // Consider adding this only for K8s version 1.13+
        // See the note in KubernetesVersion
        // .status(new HashMap<String, Object>()));
      }
      return spec;
    }

    static V1beta1CustomResourceDefinitionNames getCRDNames() {
      return new V1beta1CustomResourceDefinitionNames()
          .plural(KubernetesConstants.DOMAIN_PLURAL)
          .singular(KubernetesConstants.DOMAIN_SINGULAR)
          .kind(KubernetesConstants.DOMAIN)
          .shortNames(Collections.singletonList(KubernetesConstants.DOMAIN_SHORT));
    }

    static V1beta1CustomResourceValidation createSchemaValidation() {
      return new V1beta1CustomResourceValidation().openAPIV3Schema(createOpenAPIV3Schema());
    }

    static V1beta1JSONSchemaProps createOpenAPIV3Schema() {
      Gson gson = new Gson();
      JsonElement jsonElementSpec =
          gson.toJsonTree(createSchemaGenerator().generate(DomainSpec.class));
      V1beta1JSONSchemaProps spec = gson.fromJson(jsonElementSpec, V1beta1JSONSchemaProps.class);
      JsonElement jsonElementStatus =
          gson.toJsonTree(createSchemaGenerator().generate(DomainStatus.class));
      V1beta1JSONSchemaProps status =
          gson.fromJson(jsonElementStatus, V1beta1JSONSchemaProps.class);
      return new V1beta1JSONSchemaProps()
          .putPropertiesItem("spec", spec)
          .putPropertiesItem("status", status);
    }

    static SchemaGenerator createSchemaGenerator() {
      SchemaGenerator generator = new SchemaGenerator();
      generator.setIncludeAdditionalProperties(false);
      generator.setSupportObjectReferences(false);
      generator.setIncludeDeprecated(true);
      generator.setIncludeSchemaReference(false);
      return generator;
    }

    Step verifyCRD(Step next) {
      return new CallBuilder()
          .readCustomResourceDefinitionAsync(
              model.getMetadata().getName(), createReadResponseStep(next));
    }

    ResponseStep<V1beta1CustomResourceDefinition> createReadResponseStep(Step next) {
      return new ReadResponseStep(next);
    }

    class ReadResponseStep extends DefaultResponseStep<V1beta1CustomResourceDefinition> {
      ReadResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onSuccess(
          Packet packet, CallResponse<V1beta1CustomResourceDefinition> callResponse) {
        V1beta1CustomResourceDefinition existingCRD = callResponse.getResult();
        if (existingCRD == null) {
          return doNext(createCRD(getNext()), packet);
        } else if (isOutdatedCRD(existingCRD)) {
          return doNext(updateCRD(getNext(), existingCRD), packet);
        } else {
          return doNext(packet);
        }
      }
    }

    Step createCRD(Step next) {
      return new CallBuilder()
          .createCustomResourceDefinitionAsync(model, createCreateResponseStep(next));
    }

    ResponseStep<V1beta1CustomResourceDefinition> createCreateResponseStep(Step next) {
      return new CreateResponseStep(next);
    }

    private class CreateResponseStep extends ResponseStep<V1beta1CustomResourceDefinition> {
      CreateResponseStep(Step next) {
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
    }

    private boolean isOutdatedCRD(V1beta1CustomResourceDefinition existingCRD) {
      return COMPARATOR.isOutdatedCRD(existingCRD, this.model);
    }

    Step updateCRD(Step next, V1beta1CustomResourceDefinition existingCRD) {
      model.getMetadata().setResourceVersion(existingCRD.getMetadata().getResourceVersion());
      return new CallBuilder()
          .replaceCustomResourceDefinitionAsync(
              model.getMetadata().getName(), model, createReplaceResponseStep(next));
    }

    ResponseStep<V1beta1CustomResourceDefinition> createReplaceResponseStep(Step next) {
      return new ReplaceResponseStep(next);
    }

    private class ReplaceResponseStep extends ResponseStep<V1beta1CustomResourceDefinition> {
      ReplaceResponseStep(Step next) {
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
    }
  }

  interface CRDComparator {
    boolean isOutdatedCRD(
        V1beta1CustomResourceDefinition actual, V1beta1CustomResourceDefinition expected);
  }

  static class CRDComparatorImpl implements CRDComparator {
    @Override
    public boolean isOutdatedCRD(
        V1beta1CustomResourceDefinition actual, V1beta1CustomResourceDefinition expected) {
      // For later versions of the product, we will want to do a complete comparison
      // of the version, supporting alpha and beta variants, e.g. v3alpha1 format, but
      // for now we just need to replace v1.
      return actual.getSpec().getVersion().equals("v1")
          || (actual.getSpec().getVersion().equals("v2")
              && (getSchemaValidation(actual) == null
                  || !getSchemaValidation(expected).equals(getSchemaValidation(actual))
                  || !getSchemaSubresources(expected).equals(getSchemaSubresources(actual))));
      // Similarly, we will later want to check:
      // VersionHelper.matchesResourceVersion(existingCRD.getMetadata(), DEFAULT_OPERATOR_VERSION)
    }

    private V1beta1JSONSchemaProps getSchemaValidation(V1beta1CustomResourceDefinition crd) {
      if (crd != null && crd.getSpec() != null && crd.getSpec().getValidation() != null) {
        return crd.getSpec().getValidation().getOpenAPIV3Schema();
      }
      return null;
    }

    private V1beta1CustomResourceSubresources getSchemaSubresources(
        V1beta1CustomResourceDefinition crd) {
      if (crd != null && crd.getSpec() != null) {
        return crd.getSpec().getSubresources();
      }
      return null;
    }
  }
}
