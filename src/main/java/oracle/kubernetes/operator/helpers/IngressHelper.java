// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.V1beta1HTTPIngressPath;
import io.kubernetes.client.models.V1beta1HTTPIngressRuleValue;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.models.V1beta1IngressBackend;
import io.kubernetes.client.models.V1beta1IngressRule;
import io.kubernetes.client.models.V1beta1IngressSpec;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/**
 * Helper class to add/remove server from Ingress.
 */
public class IngressHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  
  private IngressHelper() {}

  /**
   * Creates asynchronous step to create or update ingress registration for a server in a cluster
   * @param next Next processing step
   * @return Step to create ingress or update cluster with an entry for this server
   */
  public static Step createAddServerStep(Step next) {
    return new AddServerStep(next);
  }
  
  private static class AddServerStep extends Step {

    private AddServerStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      WlsClusterConfig clusterConfig = (WlsClusterConfig) packet.get(ProcessingConstants.CLUSTER_SCAN);
      String serverName = (String) packet.get(ProcessingConstants.SERVER_NAME);

      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      ServerKubernetesObjects sko = info.getServers().get(serverName);
      if (sko != null) {
        V1Service service = sko.getService();
        if (service != null) {
          // If we have a cluster, create a cluster level ingress
          if (clusterConfig != null) {
            String clusterName = clusterConfig.getClusterName();
            String ingressName = CallBuilder.toDNS1123LegalName(
                info.getDomain().getSpec().getDomainUID() + "-" + clusterName);
            V1ObjectMeta meta = service.getMetadata();
            return doNext(CallBuilder.create().readIngressAsync(
              ingressName, meta.getNamespace(), new ResponseStep<V1beta1Ingress>(next) {
                @Override
                public NextAction onFailure(Packet packet, ApiException e, int statusCode,
                                            Map<String, List<String>> responseHeaders) {
                  if (statusCode == CallBuilder.NOT_FOUND) {
                    return onSuccess(packet, null, statusCode, responseHeaders);
                  }
                  return super.onFailure(AddServerStep.this, packet, e, statusCode, responseHeaders);
                }

                @Override
                public NextAction onSuccess(Packet packet, V1beta1Ingress result, int statusCode,
                                            Map<String, List<String>> responseHeaders) {
                  if (result == null) {
                    V1beta1Ingress v1beta1Ingress = prepareV1beta1Ingress(ingressName, clusterName, service, info);
                    return doNext(CallBuilder.create().createIngressAsync(meta.getNamespace(), v1beta1Ingress, new ResponseStep<V1beta1Ingress>(next) {
                      @Override
                      public NextAction onFailure(Packet packet, ApiException e, int statusCode,
                                                  Map<String, List<String>> responseHeaders) {
                        return super.onFailure(AddServerStep.this, packet, e, statusCode, responseHeaders);
                      }
                      
                      @Override
                      public NextAction onSuccess(Packet packet, V1beta1Ingress result, int statusCode,
                                                  Map<String, List<String>> responseHeaders) {
                        return doNext(packet);
                      }
                    }), packet);
                  } else {
                    if (addV1beta1HTTPIngressPath(result, service)) {
                      return doNext(packet);
                    }
                    return doNext(CallBuilder.create().replaceIngressAsync(ingressName, meta.getNamespace(), result, new ResponseStep<V1beta1Ingress>(next) {
                      @Override
                      public NextAction onFailure(Packet packet, ApiException e, int statusCode,
                                                  Map<String, List<String>> responseHeaders) {
                        return super.onFailure(AddServerStep.this, packet, e, statusCode, responseHeaders);
                      }
                      
                      @Override
                      public NextAction onSuccess(Packet packet, V1beta1Ingress result, int statusCode,
                                                  Map<String, List<String>> responseHeaders) {
                        return doNext(packet);
                      }
                    }), packet);
                  }
                }
              }), packet);
          }
        }

      }

      return doNext(packet);
    }
  }

  /**
   * Creates asynchronous step to update an ingress registration to remove a server or delete the ingress
   * entirely if this was the last server in the cluster
   * @param serverName Server name
   * @param service Service
   * @param next Next processing step
   * @return Step to update or delete the ingress 
   */
  public static Step createRemoveServerStep(String serverName, V1Service service, Step next) {
    return new RemoveServerStep(serverName, service, next);
  }
  
  private static class RemoveServerStep extends Step {
    private final String serverName;
    private final V1Service service;

    private RemoveServerStep(String serverName, V1Service service, Step next) {
      super(next);
      this.serverName = serverName;
      this.service = service;
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      V1ObjectMeta meta = service.getMetadata();

      String ingressName;
      ingressName = getIngressName(info, serverName);
      if (ingressName != null) {
        return doNext(CallBuilder.create().readIngressAsync(
          ingressName, meta.getNamespace(), new ResponseStep<V1beta1Ingress>(next) {
            @Override
            public NextAction onFailure(Packet packet, ApiException e, int statusCode,
                                        Map<String, List<String>> responseHeaders) {
              if (statusCode == CallBuilder.NOT_FOUND) {
                return doNext(packet);
              }
              return super.onFailure(RemoveServerStep.this, packet, e, statusCode, responseHeaders);
            }

            @Override
            public NextAction onSuccess(Packet packet, V1beta1Ingress result, int statusCode,
                                        Map<String, List<String>> responseHeaders) {
              V1beta1IngressSpec v1beta1IngressSpec = result.getSpec();
              List<V1beta1IngressRule> v1beta1IngressRules = v1beta1IngressSpec.getRules();
              V1beta1IngressRule v1beta1IngressRule = v1beta1IngressRules.get(0);
              V1beta1HTTPIngressRuleValue v1beta1HTTPIngressRuleValue = v1beta1IngressRule.getHttp();
              List<V1beta1HTTPIngressPath> v1beta1HTTPIngressPaths = v1beta1HTTPIngressRuleValue.getPaths();
              Iterator<V1beta1HTTPIngressPath> itr = v1beta1HTTPIngressPaths.iterator();
              while (itr.hasNext()) {
                V1beta1HTTPIngressPath v1beta1HTTPIngressPath = itr.next();
                V1beta1IngressBackend v1beta1IngressBackend = v1beta1HTTPIngressPath.getBackend();
                if (meta.getName().equals(v1beta1IngressBackend.getServiceName())) {
                  itr.remove();
                }
              }
              v1beta1HTTPIngressPaths = v1beta1HTTPIngressRuleValue.getPaths();
              if (v1beta1HTTPIngressPaths.isEmpty()) {
                return doNext(CallBuilder.create().deleteIngressAsync(result.getMetadata().getName(), meta.getNamespace(), new V1DeleteOptions(), new ResponseStep<V1Status>(next) {
                  @Override
                  public NextAction onFailure(Packet packet, ApiException e, int statusCode,
                                              Map<String, List<String>> responseHeaders) {
                    return super.onFailure(RemoveServerStep.this, packet, e, statusCode, responseHeaders);
                  }
                  
                  @Override
                  public NextAction onSuccess(Packet packet, V1Status result, int statusCode,
                                              Map<String, List<String>> responseHeaders) {
                    return doNext(packet);
                  }
                }), packet);
              } else {
                return doNext(CallBuilder.create().replaceIngressAsync(ingressName, meta.getNamespace(), result, new ResponseStep<V1beta1Ingress>(next) {
                  @Override
                  public NextAction onFailure(Packet packet, ApiException e, int statusCode,
                                              Map<String, List<String>> responseHeaders) {
                    return super.onFailure(RemoveServerStep.this, packet, e, statusCode, responseHeaders);
                  }
                  
                  @Override
                  public NextAction onSuccess(Packet packet, V1beta1Ingress result, int statusCode,
                                              Map<String, List<String>> responseHeaders) {
                    return doNext(packet);
                  }
                }), packet);
              }
            }
          }), packet);
      }
      return doNext(packet);
    }
  }

  /**
   * Setup an ingress resource to be created.
   * @param ingressName name of ingress to create
   * @param clusterName name of cluster
   * @param service service object
   * @param info domain info
   * @return ingress resource object
   */
  private static V1beta1Ingress prepareV1beta1Ingress(String ingressName, String clusterName, V1Service service, DomainPresenceInfo info) {
    LOGGER.entering();
    V1beta1Ingress v1beta1Ingress = new V1beta1Ingress();
    v1beta1Ingress.setApiVersion(KubernetesConstants.EXTENSIONS_API_VERSION);
    v1beta1Ingress.setKind(KubernetesConstants.KIND_INGRESS);
    V1ObjectMeta v1ObjectMeta = new V1ObjectMeta();
    v1ObjectMeta.setName(ingressName);
    v1ObjectMeta.setNamespace(service.getMetadata().getNamespace());
    Map<String, String> annotations = new HashMap<>();
    annotations.put(KubernetesConstants.CLASS_INGRESS, KubernetesConstants.CLASS_INGRESS_VALUE);
    v1ObjectMeta.setAnnotations(annotations);
    Map<String, String> labels = new HashMap<>();
    labels.put(LabelConstants.DOMAINUID_LABEL, info.getDomain().getSpec().getDomainUID());
    labels.put(LabelConstants.DOMAINNAME_LABEL, info.getDomain().getSpec().getDomainName());
    labels.put(LabelConstants.CLUSTERNAME_LABEL, clusterName);
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
    v1beta1IngressBackend.setServiceName(service.getMetadata().getName());
    v1beta1IngressBackend.setServicePort(new IntOrString(service.getSpec().getPorts().get(0).getPort()));
    v1beta1HTTPIngressPath.setBackend(v1beta1IngressBackend);
    paths.add(v1beta1HTTPIngressPath);
    v1beta1HTTPIngressRuleValue.setPaths(paths);
    v1beta1IngressRule.setHttp(v1beta1HTTPIngressRuleValue);
    rules.add(v1beta1IngressRule);
    v1beta1IngressSpec.setRules(rules);
    v1beta1Ingress.setSpec(v1beta1IngressSpec);
    LOGGER.exiting(v1beta1Ingress);
    return v1beta1Ingress;
  }

  private static boolean addV1beta1HTTPIngressPath(V1beta1Ingress v1beta1Ingress, V1Service service) {
    LOGGER.entering();
    
    V1beta1IngressSpec v1beta1IngressSpec = v1beta1Ingress.getSpec();
    List<V1beta1IngressRule> v1beta1IngressRules = v1beta1IngressSpec.getRules();
    V1beta1IngressRule v1beta1IngressRule = v1beta1IngressRules.get(0);
    V1beta1HTTPIngressRuleValue v1beta1HTTPIngressRuleValue = v1beta1IngressRule.getHttp();
    List<V1beta1HTTPIngressPath> v1beta1HTTPIngressPaths = v1beta1HTTPIngressRuleValue.getPaths();

    for (V1beta1HTTPIngressPath v1beta1HTTPIngressPath : v1beta1HTTPIngressPaths) {
      V1beta1IngressBackend v1beta1IngressBackend = v1beta1HTTPIngressPath.getBackend();
      if (service.getMetadata().getName().equals(v1beta1IngressBackend.getServiceName())) {
        LOGGER.exiting();
        return true;
      }
    }

    V1beta1HTTPIngressPath v1beta1HTTPIngressPath = new V1beta1HTTPIngressPath();
    v1beta1HTTPIngressPath.setPath("/");
    V1beta1IngressBackend v1beta1IngressBackend = new V1beta1IngressBackend();
    v1beta1IngressBackend.setServiceName(service.getMetadata().getName());
    v1beta1IngressBackend.setServicePort(new IntOrString(service.getSpec().getPorts().get(0).getPort()));
    v1beta1HTTPIngressPath.setBackend(v1beta1IngressBackend);

    
    v1beta1HTTPIngressPaths.add(v1beta1HTTPIngressPath);
    LOGGER.exiting();
    return false;
  }

  /**
   * Get the ingress name to remove.
   * @param info DomainPresenceInfo object
   * @param serverName server name to remove
   * @return name of ingress
   */
  private static String getIngressName(DomainPresenceInfo info, String serverName) {
    Map<String, WlsClusterConfig> clusters = info.getScan().getClusterConfigs();
    String ingressName = null;

    // Get the cluster ingress if we have one
    if (clusters != null) {
      for (Map.Entry<String, WlsClusterConfig> clusterConfig : clusters.entrySet()) {
        List<WlsServerConfig> servers = clusterConfig.getValue().getServerConfigs();
        if (servers != null) {
          for (WlsServerConfig server : servers) {
            if (serverName.equals(server.getName())) {
              ingressName = CallBuilder.toDNS1123LegalName(
                  info.getDomain().getSpec().getDomainUID() + "-" + clusterConfig.getKey());
              break;
            }
          }
        }
        if (ingressName != null) {
          break;
        }
      }
    }

    return ingressName;
  }
}
