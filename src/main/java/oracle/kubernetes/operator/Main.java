// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceList;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.models.V1beta1IngressList;
import io.kubernetes.client.util.Watch;

import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.ClusterStartup;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.DomainList;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.ServerStartup;
import oracle.kubernetes.operator.helpers.CRDHelper;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ClientHelper;
import oracle.kubernetes.operator.helpers.ClientHolder;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerStartupInfo;
import oracle.kubernetes.operator.helpers.HealthCheckHelper.KubernetesVersion;
import oracle.kubernetes.operator.helpers.HealthCheckHelper;
import oracle.kubernetes.operator.helpers.IngressHelper;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.helpers.RollingHelper;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.rest.RestConfigImpl;
import oracle.kubernetes.operator.rest.RestServer;
import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsConfigRetriever;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Engine;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import oracle.kubernetes.operator.work.FiberGate;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/**
 * A Kubernetes Operator for WebLogic.
 */
public class Main {

  private static final String RUNNING_STATE = "RUNNING";
  private static final String ADMIN_STATE = "ADMIN";

  private static final String PODWATCHER_COMPONENT_NAME = "podWatcher";
  
  private static final Engine engine = new Engine("operator");

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final FiberGate domainUpdaters = new FiberGate(engine);
  private static final ConcurrentMap<String, DomainPresenceInfo> domains = new ConcurrentHashMap<String, DomainPresenceInfo>();
  
  private static final ConcurrentMap<String, Boolean> initialized = new ConcurrentHashMap<>();
  private static final AtomicBoolean stopping = new AtomicBoolean(false);
  
  private static String principal;
  private static RestServer restServer = null;
  private static Thread livenessThread = null;
  private static Map<String, DomainWatcher> domainWatchers = new HashMap<>();
  private static Map<String, PodWatcher> podWatchers = new HashMap<>();
  private static Map<String, ServiceWatcher> serviceWatchers = new HashMap<>();
  private static Map<String, IngressWatcher> ingressWatchers = new HashMap<>();
  private static KubernetesVersion version = null;
  
  /**
   * Entry point
   *
   * @param args none, ignored
   */
  public static void main(String[] args) {

    // print startup log message
    LOGGER.info(MessageKeys.OPERATOR_STARTED);

    // start liveness thread
    startLivenessThread();

    // read the operator configuration
    String namespace = System.getenv("OPERATOR_NAMESPACE");
    if (namespace == null) {
      namespace = "default";
    }

    Collection<String> targetNamespaces = getTargetNamespaces(namespace);

    ConfigMapHelper cmh = new ConfigMapHelper("/operator/config");

    String serviceAccountName = cmh.get("serviceaccount");
    if (serviceAccountName == null) {
      serviceAccountName = "default";
    }
    principal = "system:serviceaccount:" + namespace + ":" + serviceAccountName;

    LOGGER.info(MessageKeys.OP_CONFIG_NAMESPACE, namespace);
    StringBuilder tns = new StringBuilder();
    Iterator<String> it = targetNamespaces.iterator();
    while (it.hasNext()) {
      tns.append(it.next());
      if (it.hasNext()) {
        tns.append(", ");
      }
    }
    LOGGER.info(MessageKeys.OP_CONFIG_TARGET_NAMESPACES, tns.toString());
    LOGGER.info(MessageKeys.OP_CONFIG_SERVICE_ACCOUNT, serviceAccountName);

    ClientHelper helper = ClientHelper.getInstance();
    try {
      // Client pool
      ClientHolder client = helper.take();
      try {
        // Initialize logging factory with JSON serializer for later logging 
        // that includes k8s objects
        LoggingFactory.setJSON(client.getApiClient().getJSON());

        // start the REST server
        startRestServer(principal, targetNamespaces);
  
        // create the Custom Resource Definitions if they are not already there
        CRDHelper.checkAndCreateCustomResourceDefinition(client);
  
        try {
          HealthCheckHelper healthCheck = new HealthCheckHelper(client, namespace, targetNamespaces);
          version = healthCheck.performK8sVersionCheck();
          healthCheck.performNonSecurityChecks();
          healthCheck.performSecurityChecks(serviceAccountName);
        } catch (ApiException e) {
          LOGGER.warning(MessageKeys.EXCEPTION, e);
        }
      } finally {
        helper.recycle(client);
      }

      // check for any existing resources and add the watches on them
      // this would happen when the Domain was running BEFORE the Operator starts up
      LOGGER.info(MessageKeys.LISTING_DOMAINS);
      for (String ns : targetNamespaces) {
        Step domainList = CallBuilder.create().listDomainAsync(ns, new ResponseStep<DomainList>(null) {
          @Override
          public NextAction onFailure(Packet packet, ApiException e, int statusCode,
              Map<String, List<String>> responseHeaders) {
            if (statusCode == CallBuilder.NOT_FOUND) {
              return onSuccess(packet, null, statusCode, responseHeaders);
            }
            return super.onFailure(packet, e, statusCode, responseHeaders);
          }

          @Override
          public NextAction onSuccess(Packet packet, DomainList result, int statusCode,
              Map<String, List<String>> responseHeaders) {
            initialized.put(ns, Boolean.TRUE);
            if (result != null) {
              for (Domain dom : result.getItems()) {
                doCheckAndCreateDomainPresence(dom);
              }
            }
            
            // delete stranded resources
            for (Map.Entry<String, DomainPresenceInfo> entry : domains.entrySet()) {
              String domainUID = entry.getKey();
              DomainPresenceInfo info = entry.getValue();
              if (info != null) {
                if (info.getDomain() == null) {
                  // no domain resource
                  deleteDomainPresence(info.getNamespace(), domainUID);
                }
              }
            }
            
            // main logic now happens in the watch handlers
            domainWatchers.put(ns, createDomainWatcher(ns, result != null ? result.getMetadata().getResourceVersion() : ""));
            return doNext(packet);
          }
        });
        
        Step initialize = CallBuilder.create().with($ -> {
          $.labelSelector = LabelConstants.DOMAINUID_LABEL; // Any with a domainUID label
        }).listPodAsync(ns, new ResponseStep<V1PodList>(
            CallBuilder.create().with($ -> {
              $.labelSelector = LabelConstants.DOMAINUID_LABEL; // Any with a domainUID label
            }).listServiceAsync(ns, new ResponseStep<V1ServiceList>(
                CallBuilder.create().with($ -> {
                  $.labelSelector = LabelConstants.DOMAINUID_LABEL; // Any with a domainUID label
                }).listIngressAsync(ns, new ResponseStep<V1beta1IngressList>(domainList) {
                  @Override
                  public NextAction onFailure(Packet packet, ApiException e, int statusCode,
                      Map<String, List<String>> responseHeaders) {
                    if (statusCode == CallBuilder.NOT_FOUND) {
                      return onSuccess(packet, null, statusCode, responseHeaders);
                    }
                    return super.onFailure(packet, e, statusCode, responseHeaders);
                  }

                  @Override
                  public NextAction onSuccess(Packet packet, V1beta1IngressList result, int statusCode,
                      Map<String, List<String>> responseHeaders) {
                    if (result != null) {
                      for (V1beta1Ingress ingress : result.getItems()) {
                        String domainUID = IngressWatcher.getIngressDomainUID(ingress);
                        String clusterName = IngressWatcher.getIngressClusterName(ingress);
                        if (domainUID != null && clusterName != null) {
                          DomainPresenceInfo created = new DomainPresenceInfo(ns);
                          DomainPresenceInfo info = domains.putIfAbsent(domainUID, created);
                          if (info == null) {
                            info = created;
                          }
                          info.getIngresses().put(clusterName, ingress);
                        }
                      }
                    }
                    ingressWatchers.put(ns, createIngressWatcher(ns, result != null ? result.getMetadata().getResourceVersion() : ""));
                    return doNext(packet);
                  }
                })) {
              @Override
              public NextAction onFailure(Packet packet, ApiException e, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                if (statusCode == CallBuilder.NOT_FOUND) {
                  return onSuccess(packet, null, statusCode, responseHeaders);
                }
                return super.onFailure(packet, e, statusCode, responseHeaders);
              }

              @Override
              public NextAction onSuccess(Packet packet, V1ServiceList result, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                if (result != null) {
                  for (V1Service service : result.getItems()) {
                    String domainUID = ServiceWatcher.getServiceDomainUID(service);
                    String serverName = ServiceWatcher.getServiceServerName(service);
                    String channelName = ServiceWatcher.getServiceChannelName(service);
                    if (domainUID != null && serverName != null) {
                      DomainPresenceInfo created = new DomainPresenceInfo(ns);
                      DomainPresenceInfo info = domains.putIfAbsent(domainUID, created);
                      if (info == null) {
                        info = created;
                      }
                      ServerKubernetesObjects csko = new ServerKubernetesObjects();
                      ServerKubernetesObjects current = info.getServers().putIfAbsent(serverName, csko);
                      ServerKubernetesObjects sko = current != null ? current : csko;
                      if (channelName != null) {
                        sko.getChannels().put(channelName, service);
                      } else {
                        sko.getService().set(service);
                      }
                    }
                  }
                }
                serviceWatchers.put(ns, createServiceWatcher(ns, result != null ? result.getMetadata().getResourceVersion() : ""));
                return doNext(packet);
              }
            })) {
          @Override
          public NextAction onFailure(Packet packet, ApiException e, int statusCode,
              Map<String, List<String>> responseHeaders) {
            if (statusCode == CallBuilder.NOT_FOUND) {
              return onSuccess(packet, null, statusCode, responseHeaders);
            }
            return super.onFailure(packet, e, statusCode, responseHeaders);
          }

          @Override
          public NextAction onSuccess(Packet packet, V1PodList result, int statusCode,
              Map<String, List<String>> responseHeaders) {
            if (result != null) {
              for (V1Pod pod : result.getItems()) {
                String domainUID = PodWatcher.getPodDomainUID(pod);
                String serverName = PodWatcher.getPodServerName(pod);
                if (domainUID != null && serverName != null) {
                  DomainPresenceInfo created = new DomainPresenceInfo(ns);
                  DomainPresenceInfo info = domains.putIfAbsent(domainUID, created);
                  if (info == null) {
                    info = created;
                  }
                  ServerKubernetesObjects csko = new ServerKubernetesObjects();
                  ServerKubernetesObjects current = info.getServers().putIfAbsent(serverName, csko);
                  ServerKubernetesObjects sko = current != null ? current : csko;
                  sko.getPod().set(pod);
                }
              }
            }
            podWatchers.put(ns, createPodWatcher(ns, result != null ? result.getMetadata().getResourceVersion() : ""));
            return doNext(packet);
          }
        });
        
        engine.createFiber().start(initialize, new Packet(), new CompletionCallback() {
          @Override
          public void onCompletion(Packet packet) {
            // no-op
          }

          @Override
          public void onThrowable(Packet packet, Throwable throwable) {
            LOGGER.severe(MessageKeys.EXCEPTION, throwable);
          }
        }); 
      }

      // now we just wait until the pod is terminated
      waitForDeath();

      // stop the REST server
      stopRestServer();

    } catch (Throwable e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    } finally {
      LOGGER.info(MessageKeys.OPERATOR_SHUTTING_DOWN);
    }
  }

  // -----------------------------------------------------------------------------
  //
  //  Below this point are methods that are called primarily from watch handlers,
  //  after watch events are received.
  //
  // -----------------------------------------------------------------------------


  private static void normalizeDomainSpec(DomainSpec spec) {
    // Normalize DomainSpec so that equals() will work correctly
    String imageName = spec.getImage();
    if (imageName == null || imageName.length() == 0) {
      spec.setImage(imageName = KubernetesConstants.DEFAULT_IMAGE);
    }
    String imagePullPolicy = spec.getImagePullPolicy();
    if (imagePullPolicy == null || imagePullPolicy.length() == 0) {
      spec.setImagePullPolicy(imagePullPolicy = (imageName.endsWith(KubernetesConstants.LATEST_IMAGE_SUFFIX)) ? KubernetesConstants.ALWAYS_IMAGEPULLPOLICY : KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY);
    }
    if (spec.getExportT3Channels() == null) {
      spec.setExportT3Channels(new ArrayList<String>());
    }
    String startupControl = spec.getStartupControl();
    if (startupControl == null || startupControl.length() == 0) {
      spec.setStartupControl(startupControl = StartupControlConstants.AUTO_STARTUPCONTROL);
    }
    if (spec.getServerStartup() == null) {
      spec.setServerStartup(new ArrayList<ServerStartup>());
    } else {
      for (ServerStartup ss : spec.getServerStartup()) {
        if (ss.getDesiredState() == null) {
          ss.setDesiredState(RUNNING_STATE);
        }
        if (ss.getEnv() == null) {
          ss.setEnv(new ArrayList<V1EnvVar>());
        }
      }
    }
    if (spec.getClusterStartup() == null) {
      spec.setClusterStartup(new ArrayList<ClusterStartup>());
    } else {
      for (ClusterStartup cs : spec.getClusterStartup()) {
        if (cs.getDesiredState() == null) {
          cs.setDesiredState(RUNNING_STATE);
        }
        if (cs.getEnv() == null) {
          cs.setEnv(new ArrayList<V1EnvVar>());
        }
        if (cs.getReplicas() == null) {
          cs.setReplicas(1);
        }
      }
    }
    if (spec.getReplicas() == null) {
      spec.setReplicas(1);
    }
  }

  /**
   * Restarts the admin server, if already running
   * @param principal Service principal
   * @param domainUID Domain UID
   */
  public static void doRestartAdmin(String principal, String domainUID) {
    DomainPresenceInfo info = domains.get(domainUID);
    if (info != null) {
      Domain dom = info.getDomain();
      if (dom != null) {
        doCheckAndCreateDomainPresence(dom, false, true, null, null);
      }
    }
  }

  /**
   * Restarts the listed servers, if already running.  Singleton servers will be immediately restarted.
   * Clustered servers will be rolled so that the cluster maintains minimal availability, if possible.
   * @param principal Service principal
   * @param domainUID Domain UID
   * @param servers Servers to roll
   */
  public static void doRollingRestartServers(String principal, String domainUID, List<String> servers) {
    DomainPresenceInfo info = domains.get(domainUID);
    if (info != null) {
      Domain dom = info.getDomain();
      if (dom != null) {
        doCheckAndCreateDomainPresence(dom, false, false, servers, null);
      }
    }
  }

  /**
   * Restarts the listed clusters, if member servers are running.  Member servers will be restarted
   * in a rolling fashion in order to maintain minimal availability, if possible.
   * @param principal Service principal
   * @param domainUID Domain UID
   * @param clusters Clusters to roll
   */
  public static void doRollingRestartClusters(String principal, String domainUID, List<String> clusters) {
    DomainPresenceInfo info = domains.get(domainUID);
    if (info != null) {
      Domain dom = info.getDomain();
      if (dom != null) {
        doCheckAndCreateDomainPresence(dom, false, false, null, clusters);
      }
    }
  }

  private static void doCheckAndCreateDomainPresence(Domain dom) {
    doCheckAndCreateDomainPresence(dom, false, false, null, null);
  }
  
  private static void doCheckAndCreateDomainPresence(Domain dom, boolean explicitRecheck) {
    doCheckAndCreateDomainPresence(dom, explicitRecheck, false, null, null);
  }
  
  private static void doCheckAndCreateDomainPresence(
      Domain dom,
      boolean explicitRecheck,
      boolean explicitRestartAdmin, 
      List<String> explicitRestartServers, 
      List<String> explicitRestartClusters) {
    LOGGER.entering();
    
    boolean hasExplicitRestarts = explicitRestartAdmin || explicitRestartServers != null || explicitRestartClusters != null;

    DomainSpec spec = dom.getSpec();
    normalizeDomainSpec(spec);
    String domainUID = spec.getDomainUID();

    DomainPresenceInfo created = new DomainPresenceInfo(dom);
    DomainPresenceInfo info = domains.putIfAbsent(domainUID, created);
    if (info == null) {
      info = created;
    } else {
      // Has the spec actually changed?  We will get watch events for status updates
      Domain current = info.getDomain();
      if (current != null) {
        if (!explicitRecheck && !hasExplicitRestarts && spec.equals(current.getSpec())) {
          // nothing in the spec has changed
          LOGGER.fine(MessageKeys.NOT_STARTING_DOMAINUID_THREAD, domainUID);
          return;
        }
      }
      info.setDomain(dom);
    }
    
    LOGGER.info(MessageKeys.PROCESSING_DOMAIN, domainUID);

    Step managedServerStrategy = bringManagedServersUp(
        DomainStatusUpdater.createEndProgressingStep(null));
    Step adminServerStrategy = bringAdminServerUp(
        connectToAdminAndInspectDomain(
            managedServerStrategy));
    
    Step strategy = DomainStatusUpdater.createProgressingStep(
        DomainStatusUpdater.INSPECTING_DOMAIN_PROGRESS_REASON, 
        true, 
        new DomainPrescenceStep(adminServerStrategy, managedServerStrategy));
    
    Packet p = new Packet();
    
    String ns = dom.getMetadata().getNamespace();
    if (initialized.getOrDefault(ns, Boolean.FALSE)) {
      PodWatcher pw = podWatchers.get(ns);
      p.getComponents().put(ProcessingConstants.DOMAIN_COMPONENT_NAME, Component.createFor(info, version, pw));
      p.put(ProcessingConstants.PRINCIPAL, principal);
      
      if (explicitRestartAdmin) {
        p.put(ProcessingConstants.EXPLICIT_RESTART_ADMIN, Boolean.TRUE);
      }
      p.put(ProcessingConstants.EXPLICIT_RESTART_SERVERS, explicitRestartServers);
      p.put(ProcessingConstants.EXPLICIT_RESTART_CLUSTERS, explicitRestartClusters);
      
      if (explicitRestartAdmin) {
        LOGGER.info(MessageKeys.RESTART_ADMIN_STARTING, domainUID);
      }
      if (explicitRestartServers != null) {
        LOGGER.info(MessageKeys.RESTART_SERVERS_STARTING, domainUID, explicitRestartServers);
      }
      if (explicitRestartClusters != null) {
        LOGGER.info(MessageKeys.ROLLING_CLUSTERS_STARTING, domainUID, explicitRestartClusters);
      }
  
      if (!stopping.get()) {
        domainUpdaters.startFiber(domainUID, strategy, p, new CompletionCallback() {
          @Override
          public void onCompletion(Packet packet) {
            if (explicitRestartAdmin) {
              LOGGER.info(MessageKeys.RESTART_ADMIN_COMPLETE, domainUID);
            }
            if (explicitRestartServers != null) {
              LOGGER.info(MessageKeys.RESTART_SERVERS_COMPLETE, domainUID, explicitRestartServers);
            }
            if (explicitRestartClusters != null) {
              LOGGER.info(MessageKeys.ROLLING_CLUSTERS_COMPLETE, domainUID, explicitRestartClusters);
            }
          }
  
          @Override
          public void onThrowable(Packet packet, Throwable throwable) {
            LOGGER.severe(MessageKeys.EXCEPTION, throwable);
            
            domainUpdaters.replaceAndStartFiber(domainUID, Fiber.getCurrentIfSet(), DomainStatusUpdater.createFailedStep(throwable, null), p, new CompletionCallback() {
              @Override
              public void onCompletion(Packet packet) {
                // no-op
              }
  
              @Override
              public void onThrowable(Packet packet, Throwable throwable) {
                LOGGER.severe(MessageKeys.EXCEPTION, throwable);
              }
            });
            
            // TODO: consider retrying domain update after a delay
          }
        });
      }
    }
    LOGGER.exiting();
  }
  
  private static class DomainPrescenceStep extends Step {
    private final Step managedServerStep;

    public DomainPrescenceStep(Step adminStep, Step managedServerStep) {
      super(adminStep);
      this.managedServerStep = managedServerStep;
    }

    @Override
    public NextAction apply(Packet packet) {
      LOGGER.entering();
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

      Domain dom = info.getDomain();
      DomainSpec spec = dom.getSpec();

      String sc = spec.getStartupControl();
      if (sc == null || !StartupControlConstants.NONE_STARTUPCONTROL.equals(sc.toUpperCase())) {
        LOGGER.exiting();
        return doNext(packet);
      }
      
      LOGGER.exiting();
      // admin server will be stopped as part of scale down flow
      return doNext(managedServerStep, packet);
    }
  }

  // pre-conditions: DomainPresenceInfo SPI
  //                 "principal"
  private static Step bringAdminServerUp(Step next) {
    return new ListPersistentVolumeClaimStep(
            PodHelper.createAdminPodStep(
                new BeforeAdminServiceStep(
                    ServiceHelper.createForServerStep(next))));
  }
  
  private static class ListPersistentVolumeClaimStep extends Step {
    public ListPersistentVolumeClaimStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
       
      Domain dom = info.getDomain();
      V1ObjectMeta meta = dom.getMetadata();
      DomainSpec spec = dom.getSpec();
      String namespace = meta.getNamespace();

      String domainUID = spec.getDomainUID();

      Step list = CallBuilder.create().with($ -> {
        $.labelSelector = LabelConstants.DOMAINUID_LABEL + "=" + domainUID;
      }).listPersistentVolumeClaimAsync(namespace, new ResponseStep<V1PersistentVolumeClaimList>(next) {
        @Override
        public NextAction onFailure(Packet packet, ApiException e, int statusCode,
            Map<String, List<String>> responseHeaders) {
          if (statusCode == CallBuilder.NOT_FOUND) {
            return onSuccess(packet, null, statusCode, responseHeaders);
          }
          return super.onFailure(packet, e, statusCode, responseHeaders);
        }

        @Override
        public NextAction onSuccess(Packet packet, V1PersistentVolumeClaimList result, int statusCode,
            Map<String, List<String>> responseHeaders) {
          info.setClaims(result);
          return doNext(packet);
        }
      });
      
      return doNext(list, packet);
    }
  }
  
  private static class BeforeAdminServiceStep extends Step {
    public BeforeAdminServiceStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      
      Domain dom = info.getDomain();
      DomainSpec spec = dom.getSpec();

      packet.put(ProcessingConstants.SERVER_NAME, spec.getAsName());
      packet.put(ProcessingConstants.PORT, spec.getAsPort());
      packet.put(ProcessingConstants.NODE_PORT, spec.getAsNodePort());
      return doNext(packet);
    }
  }
  
  private static Step connectToAdminAndInspectDomain(Step next) {
    return new WatchPodReadyAdminStep(WlsConfigRetriever.readConfigStep(
        new ExternalAdminChannelsStep(next)));
  }
  
  private static class WatchPodReadyAdminStep extends Step {
    public WatchPodReadyAdminStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      V1Pod adminPod = info.getAdmin().getPod().get();
      
      PodWatcher pw = podWatchers.get(adminPod.getMetadata().getNamespace());
      packet.getComponents().put(PODWATCHER_COMPONENT_NAME, Component.createFor(pw));
      
      return doNext(pw.waitForReady(adminPod, next), packet);
    }
  }

  private static class ExternalAdminChannelsStep extends Step {
    public ExternalAdminChannelsStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

      Collection<NetworkAccessPoint> validChannels = adminChannelsToCreate(info.getScan(), info.getDomain());
      if (validChannels != null && !validChannels.isEmpty()) {
        return doNext(new ExternalAdminChannelIteratorStep(info, validChannels, next), packet);
      }
      
      return doNext(packet);
    }
  }
  
  private static class ExternalAdminChannelIteratorStep extends Step {
    private final DomainPresenceInfo info;
    private final Iterator<NetworkAccessPoint> it;
 
    public ExternalAdminChannelIteratorStep(DomainPresenceInfo info, Collection<NetworkAccessPoint> naps, Step next) {
      super(next);
      this.info = info;
      this.it = naps.iterator();
    }

    @Override
    public NextAction apply(Packet packet) {
      if (it.hasNext()) {
        packet.put(ProcessingConstants.SERVER_NAME, info.getDomain().getSpec().getAsName());
        packet.put(ProcessingConstants.NETWORK_ACCESS_POINT, it.next());
        Step step = ServiceHelper.createForExternalChannelStep(this);
        return doNext(step, packet);
      }
      return doNext(packet);
    }
  }
  
  private static Step bringManagedServersUp(Step next) {
    return new ManagedServersUpStep(next);
  }
  
  private static class ManagedServersUpStep extends Step {

    public ManagedServersUpStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      LOGGER.entering();
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

      Domain dom = info.getDomain();
      DomainSpec spec = dom.getSpec();
      
      if (LOGGER.isFineEnabled()) {
        Collection<String> runningList = new ArrayList<>();
        for (Map.Entry<String, ServerKubernetesObjects> entry : info.getServers().entrySet()) {
          ServerKubernetesObjects sko = entry.getValue();
          if (sko != null && sko.getPod() != null) {
            runningList.add(entry.getKey());
          }
         }
        LOGGER.fine("Running servers for domain with UID: " + spec.getDomainUID() + ", running list: " + runningList);
      }
      
      String sc = spec.getStartupControl();
      if (sc == null) {
        sc = StartupControlConstants.AUTO_STARTUPCONTROL;
      } else {
        sc = sc.toUpperCase();
      }

      WlsDomainConfig scan = info.getScan();
      Collection<ServerStartupInfo> ssic = new ArrayList<ServerStartupInfo>();

      String asName = spec.getAsName();

      boolean startAll = false;
      Collection<String> servers = new ArrayList<String>();
      switch (sc) {
        case StartupControlConstants.ALL_STARTUPCONTROL:
          startAll = true;
        case StartupControlConstants.AUTO_STARTUPCONTROL:
        case StartupControlConstants.SPECIFIED_STARTUPCONTROL:
          Collection<String> clusters = new ArrayList<String>();

          // start specified servers with their custom options
          List<ServerStartup> ssl = spec.getServerStartup();
          if (ssl != null) {
            for (ServerStartup ss : ssl) {
              String serverName = ss.getServerName();
              WlsServerConfig wlsServerConfig = scan.getServerConfig(serverName);
              if (!serverName.equals(asName) && wlsServerConfig != null && !servers.contains(serverName)) {
                // start server
                servers.add(serverName);
                // find cluster if this server is part of one
                WlsClusterConfig cc = null;
                find:
                for (WlsClusterConfig wlsClusterConfig : scan.getClusterConfigs().values()) {
                  for (WlsServerConfig clusterMemberServerConfig : wlsClusterConfig.getServerConfigs()) {
                    if (serverName.equals(clusterMemberServerConfig.getName())) {
                      cc = wlsClusterConfig;
                      break find;
                    }
                  }
                }
                List<V1EnvVar> env = ss.getEnv();
                if (ADMIN_STATE.equals(ss.getDesiredState())) {
                  env = startInAdminMode(env);
                }
                ssic.add(new ServerStartupInfo(wlsServerConfig, cc, env, ss));
              }
            }
          }
          List<ClusterStartup> lcs = spec.getClusterStartup();
          if (lcs != null) {
            cluster:
            for (ClusterStartup cs : lcs) {
              String clusterName = cs.getClusterName();
              clusters.add(clusterName);
              int startedCount = 0;
              // find cluster
              WlsClusterConfig wlsClusterConfig = scan.getClusterConfig(clusterName);
              if (wlsClusterConfig != null) {
                for (WlsServerConfig wlsServerConfig : wlsClusterConfig.getServerConfigs()) {
                  // done with the current cluster
                  if (startedCount >= cs.getReplicas() && !startAll) 
                    continue cluster;

                  String serverName = wlsServerConfig.getName();
                  if (!serverName.equals(asName) && !servers.contains(serverName)) {
                    List<V1EnvVar> env = cs.getEnv();
                    ServerStartup ssi = null;
                    ssl = spec.getServerStartup();
                    if (ssl != null) {
                      for (ServerStartup ss : ssl) {
                        String s = ss.getServerName();
                        if (serverName.equals(s)) {
                          env = ss.getEnv();
                          ssi = ss;
                          break;
                        }
                      }
                    }
                    // start server
                    servers.add(serverName);
                    if (ADMIN_STATE.equals(cs.getDesiredState())) {
                      env = startInAdminMode(env);
                    }
                    ssic.add(new ServerStartupInfo(wlsServerConfig, wlsClusterConfig, env, ssi));
                    startedCount++;
                  }
                }
              }
            }
          }
          if (startAll) {
            // Look for any other servers
            for (WlsClusterConfig wlsClusterConfig : scan.getClusterConfigs().values()) {
              for (WlsServerConfig wlsServerConfig : wlsClusterConfig.getServerConfigs()) {
                String serverName = wlsServerConfig.getListenAddress();
                // do not start admin server
                if (!serverName.equals(asName) && !servers.contains(serverName)) {
                  // start server
                  servers.add(serverName);
                  ssic.add(new ServerStartupInfo(wlsServerConfig, wlsClusterConfig, null, null));
                }
              }
            }
            for (Map.Entry<String, WlsServerConfig> wlsServerConfig : scan.getServerConfigs().entrySet()) {
              String serverName = wlsServerConfig.getKey();
              // do not start admin server
              if (!serverName.equals(asName) && !servers.contains(serverName)) {
                // start server
                servers.add(serverName);
                ssic.add(new ServerStartupInfo(wlsServerConfig.getValue(), null, null, null));
              }
            }
          }
          else if (StartupControlConstants.AUTO_STARTUPCONTROL.equals(sc)) {
            for (Map.Entry<String, WlsClusterConfig> wlsClusterConfig : scan.getClusterConfigs().entrySet()) {
              if (!clusters.contains(wlsClusterConfig.getKey())) {
                int startedCount = 0;
                WlsClusterConfig config = wlsClusterConfig.getValue();
                for (WlsServerConfig wlsServerConfig : config.getServerConfigs()) {
                  if (startedCount >= spec.getReplicas())
                    break;
                  String serverName = wlsServerConfig.getName();
                  if (!serverName.equals(asName) && !servers.contains(serverName)) {
                    // start server
                    servers.add(serverName);
                    ssic.add(new ServerStartupInfo(wlsServerConfig, config, null, null));
                    startedCount++;
                  }
                }
              }
            }
          }
          
          info.setServerStartupInfo(ssic);
          LOGGER.exiting();
          return doNext(scaleDownIfNecessary(info, servers, 
              checkServerServices(info, new ManagedServerUpIteratorStep(ssic, next))), 
              packet);
        case StartupControlConstants.ADMIN_STARTUPCONTROL:
        case StartupControlConstants.NONE_STARTUPCONTROL:
        default:
          
          info.setServerStartupInfo(null);
          LOGGER.exiting();
          return doNext(scaleDownIfNecessary(info, servers, 
              checkServerServices(info, next)), 
              packet);
      }
    }
  }
  
  private static List<V1EnvVar> startInAdminMode(List<V1EnvVar> env) {
    if (env == null) {
      env = new ArrayList<>();
    }
    
    // look for JAVA_OPTIONS
    V1EnvVar jo = null;
    for (V1EnvVar e : env) {
      if ("JAVA_OPTIONS".equals(e.getName())) {
        jo = e;
        if (jo.getValueFrom() != null) {
          throw new IllegalStateException();
        }
        break;
      }
    }
    if (jo == null) {
      jo = new V1EnvVar();
      jo.setName("JAVA_OPTIONS");
      env.add(jo);
    }
    
    // create or update value
    String startInAdmin = "-Dweblogic.management.startupMode=ADMIN";
    String value = jo.getValue();
    value = (value != null) ? (startInAdmin + " " + value) : startInAdmin;
    jo.setValue(value);
    
    return env;
  }
  
  private static Step checkServerServices(DomainPresenceInfo info, Step next) {
    Collection<String> allServers = new ArrayList<>();
    Collection<ServerStartupInfo> ssic = new ArrayList<>();
    
    WlsDomainConfig scan = info.getScan();

    // Iterate all servers
    for (WlsClusterConfig wlsClusterConfig : scan.getClusterConfigs().values()) {
      for (WlsServerConfig wlsServerConfig : wlsClusterConfig.getServerConfigs()) {
        String serverName = wlsServerConfig.getListenAddress();
        if (!allServers.contains(serverName)) {
          allServers.add(serverName);
          ssic.add(new ServerStartupInfo(wlsServerConfig, wlsClusterConfig, null, null));
        }
      }
    }
    for (Map.Entry<String, WlsServerConfig> wlsServerConfig : scan.getServerConfigs().entrySet()) {
      String serverName = wlsServerConfig.getKey();
      if (!allServers.contains(serverName)) {
        allServers.add(serverName);
        ssic.add(new ServerStartupInfo(wlsServerConfig.getValue(), null, null, null));
      }
    }

    return new ManagedServerServicesStep(info, ssic, next);
  }
  
  private static class ManagedServerServicesStep extends Step {
    private final DomainPresenceInfo info;
    private final Collection<ServerStartupInfo> ssic;

    public ManagedServerServicesStep(DomainPresenceInfo info, Collection<ServerStartupInfo> ssic, Step next) {
      super(next);
      this.info = info;
      this.ssic = ssic;
    }

    @Override
    public NextAction apply(Packet packet) {
      Collection<StepAndPacket> startDetails = new ArrayList<>();
      
      for (ServerStartupInfo ssi : ssic) {
        Packet p = packet.clone();
        WlsServerConfig serverConfig = ssi.serverConfig;
        ServerStartup serverStartup = ssi.serverStartup;
        String serverName = serverConfig.getName();
        p.put(ProcessingConstants.SERVER_SCAN, serverConfig);
        p.put(ProcessingConstants.CLUSTER_SCAN, ssi.clusterConfig);
        p.put(ProcessingConstants.ENVVARS, ssi.envVars);
        
        DomainSpec spec = info.getDomain().getSpec();
        Integer nodePort = null;
        if (serverStartup == null) {
          List<ServerStartup> ssl = spec.getServerStartup();
          if (ssl != null) {
            for (ServerStartup ss : ssl) {
              if (serverName.equals(ss.getServerName())) {
                serverStartup = ss;
                break;
              }
            }
          }
        }

        if (serverStartup != null) {
          nodePort = serverStartup.getNodePort();
        }
        if (nodePort == null && serverName.equals(spec.getAsName())) {
          nodePort = spec.getAsNodePort();
        }
        
        p.put(ProcessingConstants.SERVER_NAME, serverName);
        if (ssi.clusterConfig != null) {
          p.put(ProcessingConstants.CLUSTER_NAME, ssi.clusterConfig.getClusterName());
        }
        p.put(ProcessingConstants.PORT, serverConfig.getListenPort());
        p.put(ProcessingConstants.NODE_PORT, nodePort);

        startDetails.add(new StepAndPacket(ServiceHelper.createForServerStep(null), p));
      }
      
      // Add cluster services
      WlsDomainConfig scan = info.getScan();
      if (scan != null) {
        for (Map.Entry<String, WlsClusterConfig> entry : scan.getClusterConfigs().entrySet()) {
          Packet p = packet.clone();
          WlsClusterConfig clusterConfig = entry.getValue();
          p.put(ProcessingConstants.CLUSTER_SCAN, clusterConfig);
          p.put(ProcessingConstants.CLUSTER_NAME, clusterConfig.getClusterName());
          for (WlsServerConfig serverConfig : clusterConfig.getServerConfigs()) {
            p.put(ProcessingConstants.PORT, serverConfig.getListenPort());
            break;
          }

          startDetails.add(new StepAndPacket(
              ServiceHelper.createForClusterStep(
                  IngressHelper.createClusterStep(null)), p));
        }
      }
      
      if (startDetails.isEmpty()) {
        return doNext(packet);
      }
      return doForkJoin(next, packet, startDetails);
    }
  }
  
  private static Step scaleDownIfNecessary(DomainPresenceInfo info, Collection<String> servers, Step next) {
    Domain dom = info.getDomain();
    DomainSpec spec = dom.getSpec();

    boolean shouldStopAdmin = false;
    String sc = spec.getStartupControl();
    if (sc != null && StartupControlConstants.NONE_STARTUPCONTROL.equals(sc.toUpperCase())) {
      shouldStopAdmin = true;
      next = DomainStatusUpdater.createAvailableStep(DomainStatusUpdater.ALL_STOPPED_AVAILABLE_REASON, next);
    }

    String adminName = spec.getAsName();
    Map<String, ServerKubernetesObjects> currentServers = info.getServers();
    Collection<Map.Entry<String, ServerKubernetesObjects>> serversToStop = new ArrayList<>();
    for(Map.Entry<String, ServerKubernetesObjects> entry : currentServers.entrySet()) {
      if ((shouldStopAdmin || !entry.getKey().equals(adminName)) && !servers.contains(entry.getKey())) {
        serversToStop.add(entry);
      }
    }
    
    if (!serversToStop.isEmpty()) {
      return new ServerDownIteratorStep(serversToStop, next);
    }
    
    return next;
  }
  
  private static class ManagedServerUpIteratorStep extends Step {
    private final Collection<ServerStartupInfo> c;

    public ManagedServerUpIteratorStep(Collection<ServerStartupInfo> c, Step next) {
      super(next);
      this.c = c;
    }

    @Override
    public NextAction apply(Packet packet) {
      Collection<StepAndPacket> startDetails = new ArrayList<>();
      Map<String, StepAndPacket> rolling = new ConcurrentHashMap<>();
      packet.put(ProcessingConstants.SERVERS_TO_ROLL, rolling);
      
      for (ServerStartupInfo ssi : c) {
        Packet p = packet.clone();
        p.put(ProcessingConstants.SERVER_SCAN, ssi.serverConfig);
        p.put(ProcessingConstants.CLUSTER_SCAN, ssi.clusterConfig);
        p.put(ProcessingConstants.ENVVARS, ssi.envVars);
        
        startDetails.add(new StepAndPacket(bringManagedServerUp(ssi, null), p));
      }
      
      if (LOGGER.isFineEnabled()) {
        DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

        Domain dom = info.getDomain();
        DomainSpec spec = dom.getSpec();
        
        Collection<String> serverList = new ArrayList<>();
        for (ServerStartupInfo ssi : c) {
          serverList.add(ssi.serverConfig.getName());
        }        
        LOGGER.fine("Starting or validating servers for domain with UID: " + spec.getDomainUID() + ", server list: " + serverList);
      }
      
      if (startDetails.isEmpty()) {
        return doNext(packet);
      }
      return doForkJoin(new ManagedServerUpAfterStep(next), packet, startDetails);
    }
  }
  
  private static class ManagedServerUpAfterStep extends Step {
    public ManagedServerUpAfterStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      @SuppressWarnings("unchecked")
      Map<String, StepAndPacket> rolling = (Map<String, StepAndPacket>) packet.get(ProcessingConstants.SERVERS_TO_ROLL);

      if (LOGGER.isFineEnabled()) {
        DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

        Domain dom = info.getDomain();
        DomainSpec spec = dom.getSpec();
        
        Collection<String> rollingList = Collections.emptyList();
        if (rolling != null) {
          rollingList = rolling.keySet();
        }
        LOGGER.fine("Rolling servers for domain with UID: " + spec.getDomainUID() + ", rolling list: " + rollingList);
      }

      if (rolling == null || rolling.isEmpty()) {
        return doNext(packet);
      }
      
      return doNext(RollingHelper.rollServers(rolling, next), packet);
    }
  }
  
  private static class ServerDownIteratorStep extends Step {
    private final Collection<Map.Entry<String, ServerKubernetesObjects>> c;

    public ServerDownIteratorStep(Collection<Map.Entry<String, ServerKubernetesObjects>> serversToStop, Step next) {
      super(next);
      this.c = serversToStop;
    }

    @Override
    public NextAction apply(Packet packet) {
      Collection<StepAndPacket> startDetails = new ArrayList<>();

      for (Map.Entry<String, ServerKubernetesObjects> entry : c) {
        startDetails.add(new StepAndPacket(new ServerDownStep(entry.getKey(), entry.getValue(), null), packet.clone()));
      }
      
      if (LOGGER.isFineEnabled()) {
        DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

        Domain dom = info.getDomain();
        DomainSpec spec = dom.getSpec();
        
        Collection<String> stopList = new ArrayList<>();
        for (Map.Entry<String, ServerKubernetesObjects> entry : c) {
          stopList.add(entry.getKey());
        }
        LOGGER.fine("Stopping servers for domain with UID: " + spec.getDomainUID() + ", stop list: " + stopList);
      }
      
      if (startDetails.isEmpty()) {
        return doNext(packet);
      }
      return doForkJoin(next, packet, startDetails);
    }
  }
  
  private static class ServerDownStep extends Step {
    private final String serverName;
    private final ServerKubernetesObjects sko;

    public ServerDownStep(String serverName, ServerKubernetesObjects sko, Step next) {
      super(next);
      this.serverName = serverName;
      this.sko = sko;
    }

    @Override
    public NextAction apply(Packet packet) {
      return doNext(PodHelper.deletePodStep(sko, new ServerDownFinalizeStep(serverName, next)), packet);
    }
  }
  
  private static class ServerDownFinalizeStep extends Step {
    private final String serverName;

    public ServerDownFinalizeStep(String serverName, Step next) {
      super(next);
      this.serverName = serverName;
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      info.getServers().remove(serverName);
      return doNext(next, packet);
    }
  }
  
  // pre-conditions: DomainPresenceInfo SPI
  //                 "principal"
  //                 "serverScan"
  //                 "clusterScan"
  //                 "envVars"
  private static Step bringManagedServerUp(ServerStartupInfo ssi, Step next) {
    return PodHelper.createManagedPodStep(next);
  }

  private static void deleteDomainPresence(Domain dom) {
    V1ObjectMeta meta = dom.getMetadata();
    DomainSpec spec = dom.getSpec();
    String namespace = meta.getNamespace();

    String domainUID = spec.getDomainUID();
    
    deleteDomainPresence(namespace, domainUID);
  }
  
  private static void deleteDomainPresence(String namespace, String domainUID) {
    LOGGER.entering();

    domains.remove(domainUID);

    domainUpdaters.startFiber(domainUID, new DeleteDomainStep(namespace, domainUID), new Packet(), new CompletionCallback() {
      @Override
      public void onCompletion(Packet packet) {
        // no-op
      }

      @Override
      public void onThrowable(Packet packet, Throwable throwable) {
        LOGGER.severe(MessageKeys.EXCEPTION, throwable);
      }
    });

    LOGGER.exiting();
  }
  
  private static class DeleteDomainStep extends Step {
    private final String namespace;
    private final String domainUID;

    public DeleteDomainStep(String namespace, String domainUID) {
      super(null);
      this.namespace = namespace;
      this.domainUID = domainUID;
    }

    @Override
    public NextAction apply(Packet packet) {
      Step deletePods = CallBuilder.create().with($ -> {
        $.labelSelector = LabelConstants.DOMAINUID_LABEL + "=" + domainUID;
      }).deleteCollectionPodAsync(namespace, new ResponseStep<V1Status>(next) {
        @Override
        public NextAction onFailure(Packet packet, ApiException e, int statusCode,
            Map<String, List<String>> responseHeaders) {
          if (statusCode == CallBuilder.NOT_FOUND) {
            return onSuccess(packet, null, statusCode, responseHeaders);
          }
          return super.onFailure(packet, e, statusCode, responseHeaders);
        }
        
        @Override
        public NextAction onSuccess(Packet packet, V1Status result, int statusCode,
            Map<String, List<String>> responseHeaders) {
          return doNext(packet);
        }
      });
      
      Step serviceList = CallBuilder.create().with($ -> {
        $.labelSelector = LabelConstants.DOMAINUID_LABEL + "=" + domainUID;
      }).listServiceAsync(namespace, new ResponseStep<V1ServiceList>(deletePods) {
        @Override
        public NextAction onFailure(Packet packet, ApiException e, int statusCode,
            Map<String, List<String>> responseHeaders) {
          if (statusCode == CallBuilder.NOT_FOUND) {
            return onSuccess(packet, null, statusCode, responseHeaders);
          }
          return super.onFailure(packet, e, statusCode, responseHeaders);
        }

        @Override
        public NextAction onSuccess(Packet packet, V1ServiceList result, int statusCode,
            Map<String, List<String>> responseHeaders) {
          if (result != null) {
            return doNext(new DeleteServiceListStep(result.getItems(), deletePods), packet);
          }
          return doNext(packet);
        }
      });

      LOGGER.finer(MessageKeys.LIST_INGRESS_FOR_DOMAIN, domainUID, namespace);
      Step deleteIngress = CallBuilder.create().with($ -> {
        $.labelSelector = LabelConstants.DOMAINUID_LABEL + "=" + domainUID;
      }).listIngressAsync(namespace, new ResponseStep<V1beta1IngressList>(serviceList) {
        @Override
        public NextAction onFailure(Packet packet, ApiException e, int statusCode,
                                    Map<String, List<String>> responseHeaders) {
          if (statusCode == CallBuilder.NOT_FOUND) {
            return onSuccess(packet, null, statusCode, responseHeaders);
          }
          return super.onFailure(packet, e, statusCode, responseHeaders);
        }

        @Override
        public NextAction onSuccess(Packet packet, V1beta1IngressList result, int statusCode,
                                    Map<String, List<String>> responseHeaders) {
          if (result != null) {

            return doNext(new DeleteIngressListStep(result.getItems(), serviceList), packet);
          }
          return doNext(packet);
        }
      });

      return doNext(deleteIngress, packet);
    }
  }
  
  private static class DeleteServiceListStep extends Step {
    private final Iterator<V1Service> it;

    public DeleteServiceListStep(Collection<V1Service> c, Step next) {
      super(next);
      this.it = c.iterator();
    }

    @Override
    public NextAction apply(Packet packet) {
      if (it.hasNext()) {
        V1Service service = it.next();
        V1ObjectMeta meta = service.getMetadata();
        Step delete = CallBuilder.create().deleteServiceAsync(meta.getName(), meta.getNamespace(), new ResponseStep<V1Status>(this) {
          @Override
          public NextAction onFailure(Packet packet, ApiException e, int statusCode,
              Map<String, List<String>> responseHeaders) {
            if (statusCode == CallBuilder.NOT_FOUND) {
              return onSuccess(packet, null, statusCode, responseHeaders);
            }
            return super.onFailure(packet, e, statusCode, responseHeaders);
          }
          
          @Override
          public NextAction onSuccess(Packet packet, V1Status result, int statusCode,
              Map<String, List<String>> responseHeaders) {
            return doNext(packet);
          }
        });
        return doNext(delete, packet);
      }
      
      return doNext(packet);
    }
  }

  private static class DeleteIngressListStep extends Step {
    private final Iterator<V1beta1Ingress> it;

    public DeleteIngressListStep(Collection<V1beta1Ingress> c, Step next) {
      super(next);
      this.it = c.iterator();
    }

    @Override
    public NextAction apply(Packet packet) {
      if (it.hasNext()) {
        V1beta1Ingress v1beta1Ingress = it.next();
        V1ObjectMeta meta = v1beta1Ingress.getMetadata();
        String ingressName = meta.getName();
        String namespace = meta.getNamespace();
        LOGGER.finer(MessageKeys.REMOVING_INGRESS, ingressName, namespace);
        Step delete = CallBuilder.create().deleteIngressAsync(ingressName, meta.getNamespace(), new V1DeleteOptions(), new ResponseStep<V1Status>(this) {
          @Override
          public NextAction onFailure(Packet packet, ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
            if (statusCode == CallBuilder.NOT_FOUND) {
              return onSuccess(packet, null, statusCode, responseHeaders);
            }
            return super.onFailure(packet, e, statusCode, responseHeaders);
          }

          @Override
          public NextAction onSuccess(Packet packet, V1Status result, int statusCode, Map<String, List<String>> responseHeaders) {
            return doNext(packet);
          }
        });
        return doNext(delete, packet);
      }
      return doNext(packet);
    }
  }

  /**
   * Obtain the list of target namespaces
   * @return the collection of target namespace names
   */
  private static Collection<String> getTargetNamespaces(String namespace) {
    Collection<String> targetNamespaces = new ArrayList<String>();

    ConfigMapHelper cmh = new ConfigMapHelper("/operator/config");
    String tnValue = cmh.get("targetNamespaces");
    if (tnValue != null) {
      StringTokenizer st = new StringTokenizer(tnValue, ",");
      while (st.hasMoreTokens()) {
        targetNamespaces.add(st.nextToken());
      }
    }

    // If no namespaces were found, default to the namespace of the operator
    if (targetNamespaces.isEmpty()) {
      targetNamespaces.add(namespace);
    }

    return targetNamespaces;
  }

  private static void startRestServer(String principal, Collection<String> targetNamespaces) throws Exception {
    restServer = new RestServer(new RestConfigImpl(ClientHelper.getInstance(), principal, targetNamespaces));
    restServer.start();
  }

  private static void stopRestServer() {
    restServer.stop();
    restServer = null;
  }

  private static void startLivenessThread() {
    LOGGER.info(MessageKeys.STARTING_LIVENESS_THREAD);
    livenessThread = new OperatorLiveness();
    livenessThread.setDaemon(true);
    livenessThread.start();
  }

  private static void waitForDeath() {

    try {
      livenessThread.join();
    } catch (InterruptedException ignore) {
      // ignoring
    }

    stopping.set(true);
  }

  /**
   * True, if the operator is stopping
   * @return Is operator stopping
   */
  public static boolean getStopping() {
    return stopping.get();
  }
  
  private static DomainWatcher createDomainWatcher(String namespace, String initialResourceVersion) {
    return DomainWatcher.create(namespace, initialResourceVersion, Main::dispatchDomainWatch, stopping);
  }
  
  private static PodWatcher createPodWatcher(String namespace, String initialResourceVersion)  {
    return PodWatcher.create(namespace, initialResourceVersion, Main::dispatchPodWatch, stopping);
  }

  private static void dispatchPodWatch(Watch.Response<V1Pod> item) {
    V1Pod p = item.object;
    if (p != null) {
      V1ObjectMeta metadata = p.getMetadata();
      String domainUID = metadata.getLabels().get(LabelConstants.DOMAINUID_LABEL);
      String serverName = metadata.getLabels().get(LabelConstants.SERVERNAME_LABEL);
      if (domainUID != null) {
        DomainPresenceInfo info = domains.get(domainUID);
        if (info != null && serverName != null) {
          ServerKubernetesObjects created = new ServerKubernetesObjects();
          ServerKubernetesObjects current = info.getServers().putIfAbsent(serverName, created);
          ServerKubernetesObjects sko = current != null ? current : created;
          if (sko != null) {
            Fiber f;
            Packet packet;
            switch (item.type) {
              case "ADDED":
                sko.getPod().set(p);
                f = engine.createFiber();
                packet = new Packet();
                packet.getComponents().put(ProcessingConstants.DOMAIN_COMPONENT_NAME, Component.createFor(info, version));
                f.start(DomainStatusUpdater.createStatusStep(p, false, null), packet, null);
                break;
              case "MODIFIED":
                V1Pod skoPod = sko.getPod().get();
                if (skoPod != null) {
                  // If the skoPod is null then the operator deleted this pod
                  // and modifications are to the terminating pod
                  sko.getPod().compareAndSet(skoPod, p);
                }
                f = engine.createFiber();
                packet = new Packet();
                packet.getComponents().put(ProcessingConstants.DOMAIN_COMPONENT_NAME, Component.createFor(info, version));
                f.start(DomainStatusUpdater.createStatusStep(p, false, null), packet, null);
                break;
              case "DELETED":
                V1Pod oldPod = sko.getPod().getAndSet(null);
                f = engine.createFiber();
                packet = new Packet();
                packet.getComponents().put(ProcessingConstants.DOMAIN_COMPONENT_NAME, Component.createFor(info, version));
                f.start(DomainStatusUpdater.createStatusStep(p, true, null), packet, null);
                if (oldPod != null) {
                  // Pod was deleted, but sko still contained a non-null entry
                  LOGGER.info(MessageKeys.POD_DELETED, domainUID, metadata.getNamespace(), serverName);
                  doCheckAndCreateDomainPresence(info.getDomain(), true);
                }
                break;

              case "ERROR":
              default:
            }
          }
        }
      }
    }
  }
  

  private static ServiceWatcher createServiceWatcher(String namespace, String initialResourceVersion)  {
    return ServiceWatcher.create(namespace, initialResourceVersion, Main::dispatchServiceWatch, stopping);
  }

  private static void dispatchServiceWatch(Watch.Response<V1Service> item) {
    V1Service s = item.object;
    if (s != null) {
      V1ObjectMeta metadata = s.getMetadata();
      String domainUID = metadata.getLabels().get(LabelConstants.DOMAINUID_LABEL);
      String serverName = metadata.getLabels().get(LabelConstants.SERVERNAME_LABEL);
      String channelName = metadata.getLabels().get(LabelConstants.CHANNELNAME_LABEL);
      String clusterName = metadata.getLabels().get(LabelConstants.CLUSTERNAME_LABEL);
      if (domainUID != null) {
        DomainPresenceInfo info = domains.get(domainUID);
        ServerKubernetesObjects sko = null;
        if (info != null) {
          if (serverName != null) {
            ServerKubernetesObjects created = new ServerKubernetesObjects();
            ServerKubernetesObjects current = info.getServers().putIfAbsent(serverName, created);
            sko = current != null ? current : created;
          }
          switch (item.type) {
            case "ADDED":
              if (sko != null) {
                if (channelName != null) {
                  sko.getChannels().put(channelName, s);
                } else {
                  sko.getService().set(s);
                }
              } else if (clusterName != null) {
                info.getClusters().put(clusterName, s);
              }
              break;
            case "MODIFIED":
              if (sko != null) {
                if (channelName != null) {
                  V1Service skoService = sko.getChannels().get(channelName);
                  if (skoService != null) {
                    sko.getChannels().replace(channelName, skoService, s);
                  }
                } else {
                  V1Service skoService = sko.getService().get();
                  if (skoService != null) {
                    sko.getService().compareAndSet(skoService, s);
                  }
                }
              } else if (clusterName != null) {
                V1Service clusterService = info.getClusters().get(clusterName);
                if (clusterService != null) {
                  info.getClusters().replace(clusterName, clusterService, s);
                }
              }
              break;
            case "DELETED":
              if (sko != null) {
                if (channelName != null) {
                  V1Service oldService = sko.getChannels().put(channelName, null);
                  if (oldService != null) {
                    // Service was deleted, but sko still contained a non-null entry
                    LOGGER.info(MessageKeys.SERVER_SERVICE_DELETED, domainUID, metadata.getNamespace(), serverName);
                    doCheckAndCreateDomainPresence(info.getDomain(), true);
                  }
                } else {
                  V1Service oldService = sko.getService().getAndSet(null);
                  if (oldService != null) {
                    // Service was deleted, but sko still contained a non-null entry
                    LOGGER.info(MessageKeys.SERVER_SERVICE_DELETED, domainUID, metadata.getNamespace(), serverName);
                    doCheckAndCreateDomainPresence(info.getDomain(), true);
                  }
                }
              } else if (clusterName != null) {
                V1Service oldService = info.getClusters().put(clusterName, null);
                if (oldService != null) {
                  // Service was deleted, but clusters still contained a non-null entry
                  LOGGER.info(MessageKeys.CLUSTER_SERVICE_DELETED, domainUID, metadata.getNamespace(), clusterName);
                  doCheckAndCreateDomainPresence(info.getDomain(), true);
                }
              }
              break;

            case "ERROR":
            default:
          }
        }
      }
    }
  }
  
  private static IngressWatcher createIngressWatcher(String namespace, String initialResourceVersion)  {
    return IngressWatcher.create(namespace, initialResourceVersion, Main::dispatchIngressWatch, stopping);
  }

  private static void dispatchIngressWatch(Watch.Response<V1beta1Ingress> item) {
    V1beta1Ingress i = item.object;
    if (i != null) {
      V1ObjectMeta metadata = i.getMetadata();
      String domainUID = metadata.getLabels().get(LabelConstants.DOMAINUID_LABEL);
      String clusterName = metadata.getLabels().get(LabelConstants.CLUSTERNAME_LABEL);
      if (domainUID != null) {
        DomainPresenceInfo info = domains.get(domainUID);
        if (info != null && clusterName != null) {
          switch (item.type) {
            case "ADDED":
              info.getIngresses().put(clusterName, i);
              break;
            case "MODIFIED":
              V1beta1Ingress skoIngress = info.getIngresses().get(clusterName);
              if (skoIngress != null) {
                info.getIngresses().replace(clusterName, skoIngress, i);
              }
              break;
            case "DELETED":
              V1beta1Ingress oldIngress = info.getIngresses().remove(clusterName);
              if (oldIngress != null) {
                // Ingress was deleted, but sko still contained a non-null entry
                LOGGER.info(MessageKeys.INGRESS_DELETED, domainUID, metadata.getNamespace(), clusterName);
                doCheckAndCreateDomainPresence(info.getDomain(), true);
              }
              break;

            case "ERROR":
            default:
          }
        }
      }
    }
  }
  
  /**
   * Dispatch the Domain event to the appropriate handler.
   *
   * @param item  An item received from a Watch response.
   * @param principal The name of the principal that will be used in this watch.
   */
  private static void dispatchDomainWatch(Watch.Response<Domain> item) {
    Domain d;
    String domainUID;
    switch (item.type) {
      case "ADDED":
      case "MODIFIED":
        d = item.object;
        domainUID = d.getSpec().getDomainUID();
        LOGGER.info(MessageKeys.WATCH_DOMAIN, domainUID);
        doCheckAndCreateDomainPresence(d);
        break;

      case "DELETED":
        d = item.object;
        domainUID = d.getSpec().getDomainUID();
        LOGGER.info(MessageKeys.WATCH_DOMAIN_DELETED, domainUID);
        deleteDomainPresence(d);
        break;

      case "ERROR":
      default:
    }
  }

  /**
   * This method checks the domain spec against any configured network
   * access points defined for the domain. This implementation only handles T3 
   * protocol for externalization.
   *
   * @param scan      WlsDomainConfig from discovery containing configuration
   * @param dom       Domain containing Domain resource information
   * @return Validated collection of network access points
   */
  public static Collection<NetworkAccessPoint> adminChannelsToCreate(WlsDomainConfig scan, Domain dom) {
    LOGGER.entering();
    
    // The following hard-coded values for the nodePort min/max ranges are
    // provided here until the appropriate API is discovered to obtain
    // this information from Kubernetes.
    Integer nodePortMin = 30000;
    Integer nodePortMax = 32767;

    DomainSpec spec = dom.getSpec();
    if (spec.getExportT3Channels() == null) {
      return null;
    }
    
    WlsServerConfig adminServerConfig = scan.getServerConfig(spec.getAsName());

    List<NetworkAccessPoint> naps = adminServerConfig.getNetworkAccessPoints();
    // This will become a list of valid channels to create services for.
    Collection<NetworkAccessPoint> channels = new ArrayList<>();

    // Pick out externalized channels from the server channels list
    for ( String incomingChannel : spec.getExportT3Channels() ) {
      boolean missingChannel = true;
      for ( NetworkAccessPoint nap : naps ) {
        if (nap.getName().equalsIgnoreCase(incomingChannel)) {
          missingChannel = false;
          channels.add(nap);
          break;
        }
      }
      if ( missingChannel ) {
        LOGGER.warning(MessageKeys.EXCH_CHANNEL_NOT_DEFINED, incomingChannel, spec.getAsName());
      }
    }
        
    // Iterate through the selected channels and validate
    Collection<NetworkAccessPoint> validatedChannels = new ArrayList<>();
    for ( NetworkAccessPoint nap : channels ) {
        
      // Only supporting T3 for now.
      if ( !nap.getProtocol().equalsIgnoreCase("t3")) {
        LOGGER.severe(MessageKeys.EXCH_WRONG_PROTOCOL, nap.getName(), nap.getProtocol());
        continue;
      }
  
      // Until otherwise determined, ports must be the same.
      if ( !nap.getListenPort().equals(nap.getPublicPort()) ) {
        // log a warning and ignore this item.
        LOGGER.warning(MessageKeys.EXCH_UNEQUAL_LISTEN_PORTS, nap.getName());
        continue;
      }

      // Make sure configured port is within NodePort range.
      if ( nap.getListenPort().compareTo(nodePortMin) < 0 || nap.getListenPort().compareTo(nodePortMax) > 0 ) {
        // port setting is outside the NodePort range limits
        LOGGER.warning(MessageKeys.EXCH_OUTSIDE_RANGE, nap.getName(), nap.getPublicPort()
          , nodePortMin, nodePortMax);
        continue;
      }
      
      validatedChannels.add(nap);
    }
    
    LOGGER.exiting();
    return validatedChannels;
  }
}
