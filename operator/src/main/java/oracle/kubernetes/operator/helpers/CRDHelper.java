// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.VersionConstants.DEFAULT_OPERATOR_VERSION;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.kubernetes.client.models.*;
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

/** Helper class to ensure Domain CRD is created */
public class CRDHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final CRDComparator COMPARATOR = new CRDComparatorImpl();

  private CRDHelper() {}

  /**
   * Factory for {@link Step} that creates Domain CRD
   *
   * @param next Next step
   * @return Step for creating Domain custom resource definition
   */
  public static Step createDomainCRDStep(Step next) {
    return new CRDStep(next);
  }

  static class CRDStep extends Step {
    CRDContext context;

    CRDStep(Step next) {
      super(next);
      context = new CRDContext(this);
    }

    @Override
    public NextAction apply(Packet packet) {
      return doNext(context.verifyCRD(getNext()), packet);
    }
  }

  static class CRDContext {
    private final Step conflictStep;
    private final V1beta1CustomResourceDefinition model;

    CRDContext(Step conflictStep) {
      this.conflictStep = conflictStep;
      this.model = createModel();
    }

    private V1beta1CustomResourceDefinition createModel() {
      return new V1beta1CustomResourceDefinition()
          .apiVersion("apiextensions.k8s.io/v1beta1")
          .kind("CustomResourceDefinition")
          .metadata(createMetadata())
          .spec(createSpec());
    }

    private V1ObjectMeta createMetadata() {
      return new V1ObjectMeta()
          .name(KubernetesConstants.CRD_NAME)
          .putLabelsItem(LabelConstants.RESOURCE_VERSION_LABEL, DEFAULT_OPERATOR_VERSION);
    }

    private V1beta1CustomResourceDefinitionSpec createSpec() {
      return new V1beta1CustomResourceDefinitionSpec()
          .group(KubernetesConstants.DOMAIN_GROUP)
          .version(KubernetesConstants.DOMAIN_VERSION)
          .scope("Namespaced")
          .names(getCRDNames())
          .validation(createSchemaValidation());
    }

    private V1beta1CustomResourceDefinitionNames getCRDNames() {
      return new V1beta1CustomResourceDefinitionNames()
          .plural(KubernetesConstants.DOMAIN_PLURAL)
          .singular(KubernetesConstants.DOMAIN_SINGULAR)
          .kind(KubernetesConstants.DOMAIN)
          .shortNames(Collections.singletonList(KubernetesConstants.DOMAIN_SHORT));
    }

    private V1beta1CustomResourceValidation createSchemaValidation() {
      return new V1beta1CustomResourceValidation().openAPIV3Schema(createOpenAPIV3Schema());
    }

    private V1beta1JSONSchemaProps createOpenAPIV3Schema() {
      Gson gson = new Gson();
      JsonElement jsonElement = gson.toJsonTree(createSchemaGenerator().generate(DomainSpec.class));
      V1beta1JSONSchemaProps spec = gson.fromJson(jsonElement, V1beta1JSONSchemaProps.class);
      return new V1beta1JSONSchemaProps().putPropertiesItem("spec", spec);
    }

    private SchemaGenerator createSchemaGenerator() {
      SchemaGenerator generator = new SchemaGenerator();
      generator.setIncludeAdditionalProperties(false);
      generator.setSupportObjectReferences(false);
      generator.setIncludeDeprecated(true);
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
      return actual.getSpec().getVersion().equals("v1");
      // Similarly, we will later want to check:
      // VersionHelper.matchesResourceVersion(existingCRD.getMetadata(), DEFAULT_OPERATOR_VERSION)
    }
  }
}
