// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.KubernetesConstants.CLASS_INGRESS;
import static oracle.kubernetes.operator.KubernetesConstants.CLASS_INGRESS_VALUE;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.LabelConstants.RESOURCE_VERSION_LABEL;
import static oracle.kubernetes.operator.Workarounds.INTORSTRING_BAD_EQUALS;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1beta1HTTPIngressPath;
import io.kubernetes.client.models.V1beta1HTTPIngressRuleValue;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.models.V1beta1IngressBackend;
import io.kubernetes.client.models.V1beta1IngressRule;
import io.kubernetes.client.models.V1beta1IngressSpec;
import java.util.Objects;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import org.yaml.snakeyaml.Yaml;

/** Helper class to add/remove server from Ingress. */
public class IngressHelper {
  private IngressHelper() {}

  /**
   * Creates asynchronous step to create or verify Ingress for cluster
   *
   * @param next Next processing step
   * @return Step to create Ingress or verify for cluster
   */
  public static Step createClusterStep(Step next) {
    return new CreateClusterStep(next);
  }

  private static class CreateClusterStep extends Step {

    private DomainPresenceInfo info;
    private String clusterName;
    private Integer port;

    CreateClusterStep(Step next) {
      super(next);
    }

    private void addToDomainInfo(V1beta1Ingress ingress) {
      info.getIngresses().put(clusterName, ingress);
    }

    @Override
    public NextAction apply(Packet packet) {
      info = packet.getSPI(DomainPresenceInfo.class);
      clusterName = (String) packet.get(ProcessingConstants.CLUSTER_NAME);
      port = (Integer) packet.get(ProcessingConstants.PORT);

      if (!hasClusterData()) {
        return doNext(packet);
      } else {
        return doNext(verifyIngressStep(getNext()), packet);
      }
    }

    private boolean hasClusterData() {
      return clusterName != null && port != null;
    }

    private Step verifyIngressStep(Step next) {
      return new CallBuilder()
          .readIngressAsync(getIngressName(), getNamespace(), new ReadIngressResponseStep(next));
    }

    V1beta1Ingress createDesiredResource() {
      return new V1beta1Ingress()
          .apiVersion(KubernetesConstants.EXTENSIONS_API_VERSION)
          .kind(KubernetesConstants.KIND_INGRESS)
          .metadata(
              new V1ObjectMeta()
                  .name(getIngressName())
                  .namespace(getNamespace())
                  .putAnnotationsItem(CLASS_INGRESS, CLASS_INGRESS_VALUE)
                  .putLabelsItem(RESOURCE_VERSION_LABEL, VersionConstants.DOMAIN_V1)
                  .putLabelsItem(DOMAINUID_LABEL, getUid())
                  .putLabelsItem(DOMAINNAME_LABEL, getDomainName())
                  .putLabelsItem(CLUSTERNAME_LABEL, clusterName)
                  .putLabelsItem(CREATEDBYOPERATOR_LABEL, "true"))
          .spec(createIngressSpec());
    }

    private String getNamespace() {
      return info.getDomain().getMetadata().getNamespace();
    }

    private String getClusterServiceName() {
      return LegalNames.toClusterServiceName(getUid(), clusterName);
    }

    private String getDomainName() {
      return info.getDomain().getSpec().getDomainName();
    }

    private String getUid() {
      return info.getDomain().getSpec().getDomainUID();
    }

    String getIngressName() {
      return LegalNames.toIngressName(getUid(), clusterName);
    }

    V1beta1IngressSpec createIngressSpec() {
      String serviceName = getClusterServiceName();
      return new V1beta1IngressSpec()
          .addRulesItem(
              new V1beta1IngressRule()
                  .http(
                      new V1beta1HTTPIngressRuleValue()
                          .addPathsItem(
                              new V1beta1HTTPIngressPath()
                                  .path("/")
                                  .backend(
                                      new V1beta1IngressBackend()
                                          .serviceName(serviceName)
                                          .servicePort(new IntOrString(port))))));
    }

    private boolean isCompatible(V1beta1Ingress result) {
      return VersionHelper.matchesResourceVersion(result.getMetadata(), VersionConstants.DOMAIN_V1)
          && equalObjects(result.getSpec(), createIngressSpec());
    }

    private class ReadIngressResponseStep extends DefaultResponseStep<V1beta1Ingress> {
      private final Step next;

      ReadIngressResponseStep(Step next) {
        super(next);
        this.next = next;
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1beta1Ingress> callResponse) {
        V1beta1Ingress result = callResponse.getResult();
        if (result == null) {
          return doNext(createIngressStep(next), packet);
        } else if (!isCompatible(result)) {
          return doNext(replaceIngressStep(next), packet);
        } else {
          return doNext(packet);
        }
      }

      private Step createIngressStep(Step next) {
        return new CallBuilder()
            .createIngressAsync(
                getNamespace(), createDesiredResource(), new CreateIngressResponseStep(next));
      }

      Step replaceIngressStep(Step next) {
        return new CallBuilder()
            .replaceIngressAsync(
                getIngressName(),
                getNamespace(),
                createDesiredResource(),
                new ReplaceIngressResponseStep(next));
      }
    }

    private class CreateIngressResponseStep extends ResponseStep<V1beta1Ingress> {
      CreateIngressResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onFailure(Packet packet, CallResponse<V1beta1Ingress> callResponse) {
        return super.onFailure(CreateClusterStep.this, packet, callResponse);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1beta1Ingress> callResponse) {
        V1beta1Ingress result = callResponse.getResult();
        if (result != null) {
          addToDomainInfo(result);
        }
        return doNext(packet);
      }
    }

    private class ReplaceIngressResponseStep extends ResponseStep<V1beta1Ingress> {

      ReplaceIngressResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onFailure(Packet packet, CallResponse<V1beta1Ingress> callResponse) {
        return super.onFailure(CreateClusterStep.this, packet, callResponse);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1beta1Ingress> callResponse) {
        V1beta1Ingress result = callResponse.getResult();
        if (result != null) {
          addToDomainInfo(result);
        }
        return doNext(packet);
      }
    }
  }

  private static boolean equalObjects(V1beta1IngressSpec object1, V1beta1IngressSpec object2) {
    return INTORSTRING_BAD_EQUALS ? yamlEquals(object1, object2) : Objects.equals(object1, object2);
  }

  private static boolean yamlEquals(Object actual, Object expected) {
    return Objects.equals(objectToYaml(actual), objectToYaml(expected));
  }

  private static String objectToYaml(Object object) {
    return new Yaml().dump(object);
  }
}
