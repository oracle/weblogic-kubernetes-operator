// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1beta1HTTPIngressPath;
import io.kubernetes.client.models.V1beta1HTTPIngressRuleValue;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.models.V1beta1IngressBackend;
import io.kubernetes.client.models.V1beta1IngressRule;
import io.kubernetes.client.models.V1beta1IngressSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.operator.work.ContainerResolver;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;

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
    public CreateClusterStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      String clusterName = (String) packet.get(ProcessingConstants.CLUSTER_NAME);
      Integer port = (Integer) packet.get(ProcessingConstants.PORT);

      if (clusterName != null && port != null) {
        Domain dom = info.getDomain();
        V1ObjectMeta meta = dom.getMetadata();
        DomainSpec spec = dom.getSpec();
        String namespace = meta.getNamespace();

        String weblogicDomainUID = spec.getDomainUID();
        String weblogicDomainName = spec.getDomainName();

        String serviceName =
            CallBuilder.toDNS1123LegalName(weblogicDomainUID + "-cluster-" + clusterName);

        String ingressName = CallBuilder.toDNS1123LegalName(weblogicDomainUID + "-" + clusterName);

        V1beta1Ingress v1beta1Ingress = new V1beta1Ingress();
        v1beta1Ingress.setApiVersion(KubernetesConstants.EXTENSIONS_API_VERSION);
        v1beta1Ingress.setKind(KubernetesConstants.KIND_INGRESS);

        V1ObjectMeta v1ObjectMeta = new V1ObjectMeta();
        v1ObjectMeta.setName(ingressName);
        v1ObjectMeta.setNamespace(namespace);

        v1ObjectMeta.putAnnotationsItem(
            KubernetesConstants.CLASS_INGRESS, KubernetesConstants.CLASS_INGRESS_VALUE);

        Map<String, String> labels = new HashMap<>();
        labels.put(LabelConstants.RESOURCE_VERSION_LABEL, VersionConstants.DOMAIN_V1);
        labels.put(LabelConstants.DOMAINUID_LABEL, weblogicDomainUID);
        labels.put(LabelConstants.DOMAINNAME_LABEL, weblogicDomainName);
        labels.put(LabelConstants.CLUSTERNAME_LABEL, clusterName);
        labels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
        v1ObjectMeta.setLabels(labels);
        v1beta1Ingress.setMetadata(v1ObjectMeta);

        V1beta1IngressSpec v1beta1IngressSpec = new V1beta1IngressSpec();
        List<V1beta1IngressRule> rules = new ArrayList<>();
        V1beta1IngressRule v1beta1IngressRule = new V1beta1IngressRule();

        V1beta1HTTPIngressRuleValue v1beta1HTTPIngressRuleValue = new V1beta1HTTPIngressRuleValue();
        List<V1beta1HTTPIngressPath> paths = new ArrayList<>();
        V1beta1HTTPIngressPath v1beta1HTTPIngressPath = new V1beta1HTTPIngressPath();
        v1beta1HTTPIngressPath.setPath("/");
        V1beta1IngressBackend v1beta1IngressBackend = new V1beta1IngressBackend();
        v1beta1IngressBackend.setServiceName(serviceName);
        v1beta1IngressBackend.setServicePort(new IntOrString(port));
        v1beta1HTTPIngressPath.setBackend(v1beta1IngressBackend);
        paths.add(v1beta1HTTPIngressPath);
        v1beta1HTTPIngressRuleValue.setPaths(paths);
        v1beta1IngressRule.setHttp(v1beta1HTTPIngressRuleValue);
        rules.add(v1beta1IngressRule);
        v1beta1IngressSpec.setRules(rules);
        v1beta1Ingress.setSpec(v1beta1IngressSpec);

        CallBuilderFactory factory =
            ContainerResolver.getInstance().getContainer().getSPI(CallBuilderFactory.class);
        return doNext(
            factory
                .create()
                .readIngressAsync(
                    ingressName,
                    meta.getNamespace(),
                    new ResponseStep<V1beta1Ingress>(next) {
                      @Override
                      public NextAction onFailure(
                          Packet packet,
                          ApiException e,
                          int statusCode,
                          Map<String, List<String>> responseHeaders) {
                        if (statusCode == CallBuilder.NOT_FOUND) {
                          return onSuccess(packet, null, statusCode, responseHeaders);
                        }
                        return super.onFailure(
                            CreateClusterStep.this, packet, e, statusCode, responseHeaders);
                      }

                      @Override
                      public NextAction onSuccess(
                          Packet packet,
                          V1beta1Ingress result,
                          int statusCode,
                          Map<String, List<String>> responseHeaders) {
                        if (result == null) {
                          return doNext(
                              factory
                                  .create()
                                  .createIngressAsync(
                                      meta.getNamespace(),
                                      v1beta1Ingress,
                                      new ResponseStep<V1beta1Ingress>(next) {
                                        @Override
                                        public NextAction onFailure(
                                            Packet packet,
                                            ApiException e,
                                            int statusCode,
                                            Map<String, List<String>> responseHeaders) {
                                          return super.onFailure(
                                              CreateClusterStep.this,
                                              packet,
                                              e,
                                              statusCode,
                                              responseHeaders);
                                        }

                                        @Override
                                        public NextAction onSuccess(
                                            Packet packet,
                                            V1beta1Ingress result,
                                            int statusCode,
                                            Map<String, List<String>> responseHeaders) {
                                          if (result != null) {
                                            info.getIngresses().put(clusterName, result);
                                          }
                                          return doNext(packet);
                                        }
                                      }),
                              packet);
                        } else {
                          if (VersionHelper.matchesResourceVersion(
                                  result.getMetadata(), VersionConstants.DOMAIN_V1)
                              && v1beta1Ingress.getSpec().equals(result.getSpec())) {
                            return doNext(packet);
                          }
                          return doNext(
                              factory
                                  .create()
                                  .replaceIngressAsync(
                                      ingressName,
                                      meta.getNamespace(),
                                      v1beta1Ingress,
                                      new ResponseStep<V1beta1Ingress>(next) {
                                        @Override
                                        public NextAction onFailure(
                                            Packet packet,
                                            ApiException e,
                                            int statusCode,
                                            Map<String, List<String>> responseHeaders) {
                                          return super.onFailure(
                                              CreateClusterStep.this,
                                              packet,
                                              e,
                                              statusCode,
                                              responseHeaders);
                                        }

                                        @Override
                                        public NextAction onSuccess(
                                            Packet packet,
                                            V1beta1Ingress result,
                                            int statusCode,
                                            Map<String, List<String>> responseHeaders) {
                                          if (result != null) {
                                            info.getIngresses().put(clusterName, result);
                                          }
                                          return doNext(packet);
                                        }
                                      }),
                              packet);
                        }
                      }
                    }),
            packet);
      }

      return doNext(packet);
    }
  }
}
