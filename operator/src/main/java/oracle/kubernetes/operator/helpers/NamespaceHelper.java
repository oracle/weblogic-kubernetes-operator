// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import com.google.common.base.Strings;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.RequestParams;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import static oracle.kubernetes.operator.helpers.HelmAccess.getHelmVariable;

/**
 * Operations for dealing with namespaces.
 */
public class NamespaceHelper {
  public static final String DEFAULT_NAMESPACE = "default";

  private static final String operatorNamespace = computeOperatorNamespace();

  private static String computeOperatorNamespace() {
    return Optional.ofNullable(getHelmVariable("OPERATOR_NAMESPACE")).orElse(DEFAULT_NAMESPACE);
  }

  public static String getOperatorNamespace() {
    return operatorNamespace;
  }

  /**
   * Parse a string of namespace names and return them as a collection.
   * @param namespaceString a comma-separated list of namespace names
   */
  public static Collection<String> parseNamespaceList(String namespaceString) {
    Collection<String> namespaces
          = Stream.of(namespaceString.split(","))
          .filter(s -> !Strings.isNullOrEmpty(s))
          .map(String::trim)
          .collect(Collectors.toUnmodifiableList());
    
    return namespaces.isEmpty() ? Collections.singletonList(operatorNamespace) : namespaces;
  }

  /**
   * Creates and returns a step to return a list of all namespaces. If there are more namespaces than the
   * request limit, will make multiple requests.
   * @param responseStep the step to receive the final list response.
   * @param labelSelector an optional selector to include only certain namespaces
   */
  public static Step createNamespaceListStep(ResponseStep<V1NamespaceList> responseStep, String labelSelector) {
    return new NamespaceListContext(responseStep, labelSelector).createListStep();
  }

  static class NamespaceListContext {
    private final ResponseStep<V1NamespaceList> responseStep;
    private final String labelSelector;
    private final List<V1Namespace> namespaces = new ArrayList<>();
    private String continueToken = "";
    private String resourceVersion;

    public NamespaceListContext(ResponseStep<V1NamespaceList> responseStep, String labelSelector) {
      this.responseStep = responseStep;
      this.labelSelector = labelSelector;
    }

    Step createListStep() {
      return new NamespaceListStep();
    }

    ResponseStep<V1NamespaceList> createResponseStep() {
      return new NamespaceListChunkedResponseStep();
    }

    boolean restartNeeded(String newResourceVersion) {
      if (resourceVersion == null) {
        resourceVersion = newResourceVersion;
        return false;
      } else if (resourceVersion.equals(newResourceVersion)) {
        return false;
      } else {
        resourceVersion = newResourceVersion;
        return true;
      }
    }

    class SuccessContextUpdate {
      private final CallResponse<V1NamespaceList> callResponse;

      public SuccessContextUpdate(CallResponse<V1NamespaceList> callResponse) {
        this.callResponse = callResponse;
        continueToken = getMetadata().getContinue();

        if (restartNeeded(getMetadata().getResourceVersion())) {
          namespaces.clear();
        }
        namespaces.addAll(callResponse.getResult().getItems());
      }

      private @Nonnull V1ListMeta getMetadata() {
        return Objects.requireNonNull(callResponse.getResult().getMetadata());
      }

      @Nonnull
      public CallResponse<V1NamespaceList> createSuccessResponse() {
        return CallResponse.createSuccess(getRequestParams(), createResult(), getStatusCode());
      }

      private RequestParams getRequestParams() {
        return callResponse.getRequestParams();
      }

      private V1NamespaceList createResult() {
        return new V1NamespaceList().metadata(getMetadata()).items(namespaces);
      }

      private int getStatusCode() {
        return callResponse.getStatusCode();
      }
    }

    class NamespaceListStep extends Step {
      @Override
      public NextAction apply(Packet packet) {
        return doNext(createCallBuilder().listNamespaceAsync(createResponseStep()), packet);
      }
    }

    private CallBuilder createCallBuilder() {
      return new CallBuilder().withLabelSelector(labelSelector).withContinue(continueToken);
    }

    class NamespaceListChunkedResponseStep extends ResponseStep<V1NamespaceList> {
      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1NamespaceList> callResponse) {
        SuccessContextUpdate update = new SuccessContextUpdate(callResponse);
        if (Strings.isNullOrEmpty(continueToken)) {
          return responseStep.onSuccess(packet, update.createSuccessResponse());
        } else {
          return doNext(createListStep(), packet);
        }
      }

      @Override
      public NextAction onFailure(Packet packet, CallResponse<V1NamespaceList> callResponse) {
        return responseStep.onFailure(packet, callResponse);
      }
    }

  }
}
