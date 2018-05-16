// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.KubernetesConstants.*;
import static oracle.kubernetes.operator.helpers.ClusteredServerConfig.*;
import static oracle.kubernetes.operator.helpers.NonClusteredServerConfig.*;
import static oracle.kubernetes.operator.helpers.ServerConfig.*;

import io.kubernetes.client.custom.IntOrString;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.weblogic.domain.v1.Cluster;
import oracle.kubernetes.weblogic.domain.v1.ClusterParams;
import oracle.kubernetes.weblogic.domain.v1.ClusteredServer;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import oracle.kubernetes.weblogic.domain.v1.NonClusteredServer;
import oracle.kubernetes.weblogic.domain.v1.Server;
import org.apache.commons.lang3.ArrayUtils;

/**
 * This helper class uses the domain spec that the customer configured to calculate the effective
 * configuration for the servers and clusters in the domain for a domain-v1.1 DomainSpec.
 */
public class DomainConfigBuilderV1Dot1 extends DomainConfigBuilder {

  private DomainSpec domainSpec;

  /**
   * Construct a DomainConfigBuilderV1Dot1 instance.
   *
   * @param domainSpec the domain spec
   */
  public DomainConfigBuilderV1Dot1(DomainSpec domainSpec) {
    this.domainSpec = domainSpec;
    LOGGER.entering(domainSpec);
    LOGGER.exiting();
  }

  /** {@inheritDoc} */
  @Override
  public void updateDomainSpec(ClusterConfig clusterConfig) {
    LOGGER.entering(clusterConfig);
    if (!updateClusterReplicas(clusterConfig.getClusterName(), clusterConfig.getReplicas())) {
      updateClusterDefaultsReplicas(clusterConfig.getReplicas());
    }
    LOGGER.finer("Updated domainSpec: " + domainSpec);
    LOGGER.exiting();
  }

  protected boolean updateClusterReplicas(String clusterName, int replicas) {
    Map<String, Cluster> clusters = domainSpec.getClusters();
    if (clusters != null) {
      Cluster cluster = clusters.get(clusterName);
      if (cluster != null) {
        if (cluster.getReplicas() != null) {
          // replicas has been customized for this cluster.  Update it.
          cluster.setReplicas(replicas);
          return true;
        }
      }
    }
    return false;
  }

  protected void updateClusterDefaultsReplicas(int replicas) {
    ClusterParams clusterDefaults = domainSpec.getClusterDefaults();
    if (clusterDefaults == null) {
      // we don't have a cluster defaults instance.
      // create one so that we can set its replicas.
      clusterDefaults = new ClusterParams();
      domainSpec.setClusterDefaults(clusterDefaults);
    }
    clusterDefaults.setReplicas(replicas);
  }

  /** {@inheritDoc} */
  @Override
  public NonClusteredServerConfig getEffectiveNonClusteredServerConfig(String serverName) {
    LOGGER.entering(serverName);
    NonClusteredServerConfig result =
        toNonClusteredServerConfig(serverName, getEffectiveNonClusteredServer(serverName));
    LOGGER.exiting(result);
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public ClusteredServerConfig getEffectiveClusteredServerConfig(
      String clusterName, String serverName) {
    LOGGER.entering(clusterName, serverName);
    ClusteredServerConfig result =
        toClusteredServerConfig(
            clusterName, serverName, getEffectiveClusteredServer(clusterName, serverName));
    LOGGER.exiting(result);
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public ClusterConfig getEffectiveClusterConfig(String clusterName) {
    LOGGER.entering(clusterName);
    ClusterConfig result = toClusterConfig(clusterName, getEffectiveCluster(clusterName));
    LOGGER.exiting(result);
    return result;
  }

  protected static Server SERVER_DEFAULTS =
      (new Server())
          .withStartedServerState(STARTED_SERVER_STATE_RUNNING)
          .withRestartedLabel(null)
          .withNodePort(null)
          .withEnv(null)
          .withImage(DEFAULT_IMAGE)
          .withImagePullPolicy(
              null) // since the default value depends on whether image ends with ":latest"
          .withImagePullSecrets(null) // TBD - should it be an empty list?
          .withShutdownPolicy(SHUTDOWN_POLICY_FORCED_SHUTDOWN) // TBD - is this correct?
          .withGracefulShutdownTimeout(new Integer(0))
          .withGracefulShutdownIgnoreSessions(Boolean.FALSE)
          .withGracefulShutdownWaitForSessions(Boolean.FALSE);

  protected static ClusteredServer CLUSTERED_SERVER_DEFAULTS =
      (new ClusteredServer())
          .withClusteredServerStartPolicy(CLUSTERED_SERVER_START_POLICY_IF_NEEDED);

  protected static NonClusteredServer NON_CLUSTERED_SERVER_DEFAULTS =
      (new NonClusteredServer())
          .withNonClusteredServerStartPolicy(NON_CLUSTERED_SERVER_START_POLICY_ALWAYS);

  protected static ClusterParams CLUSTER_DEFAULTS =
      (new ClusterParams())
          .withReplicas(null) // TBD - is this correct?
          .withMaxSurge(new IntOrString("20%")) // TBD - is this correct?
          .withMaxUnavailable(new IntOrString("20%")); // TBD - is this correct?

  private static final String[] SERVER_PROPERTY_NAMES = {
    "startedServerState",
    "restartedLabel",
    "nodePort",
    "env",
    "image",
    "imagePullPolicy",
    "imagePullSecrets",
    "shutdownPolicy",
    "gracefulShutdownTimeout",
    "gracefulShutdownIgnoreSessions",
    "gracefulShutdownWaitForSessions",
  };

  private static final String[] CLUSTERED_SERVER_ONLY_PROPERTY_NAMES = {
    "clusteredServerStartPolicy"
  };

  private static final String[] NON_CLUSTERED_SERVER_ONLY_PROPERTY_NAMES = {
    "nonClusteredServerStartPolicy"
  };

  private static final String[] CLUSTERED_SERVER_PROPERTY_NAMES =
      ArrayUtils.addAll(SERVER_PROPERTY_NAMES, CLUSTERED_SERVER_ONLY_PROPERTY_NAMES);

  private static final String[] NON_CLUSTERED_SERVER_PROPERTY_NAMES =
      ArrayUtils.addAll(SERVER_PROPERTY_NAMES, NON_CLUSTERED_SERVER_ONLY_PROPERTY_NAMES);

  private static final String[] CLUSTER_PROPERTY_NAMES = {"replicas", "maxSurge", "maxUnavailable"};

  protected NonClusteredServerConfig toNonClusteredServerConfig(
      String serverName, NonClusteredServer ncs) {
    NonClusteredServerConfig rtn =
        (new NonClusteredServerConfig())
            .withNonClusteredServerStartPolicy(ncs.getNonClusteredServerStartPolicy());
    copyServerPropertiesToServerConfig(serverName, ncs, rtn);
    return rtn;
  }

  protected ClusteredServerConfig toClusteredServerConfig(
      String clusterName, String serverName, ClusteredServer cs) {
    ClusteredServerConfig rtn =
        (new ClusteredServerConfig())
            .withClusterName(clusterName)
            .withClusteredServerStartPolicy(cs.getClusteredServerStartPolicy());
    copyServerPropertiesToServerConfig(serverName, cs, rtn);
    return rtn;
  }

  protected ClusterConfig toClusterConfig(String clusterName, Cluster c) {
    int replicas = toInt(c.getReplicas());
    ClusterConfig rtn =
        (new ClusterConfig())
            .withClusterName(clusterName)
            .withReplicas(replicas) // TBD - not negative
            .withMaxReplicas(getMaxReplicas(replicas, c.getMaxSurge()))
            .withMinReplicas(getMinReplicas(replicas, c.getMaxUnavailable()));
    // TBD - do we need to check that either min or max replicas doesn't equal replicas
    // so that we have some wiggle room to automatically restart the cluster?
    return rtn;
  }

  protected void copyServerPropertiesToServerConfig(String serverName, Server s, ServerConfig sc) {
    sc.withServerName(serverName)
        .withStartedServerState(s.getStartedServerState())
        .withRestartedLabel(s.getRestartedLabel())
        .withNodePort(getNonNegativeInt("nodePort", toInt(s.getNodePort())))
        .withEnv(s.getEnv())
        .withImage(s.getImage())
        .withImagePullPolicy(s.getImagePullPolicy())
        .withImagePullSecrets(s.getImagePullSecrets())
        .withShutdownPolicy(s.getShutdownPolicy())
        .withGracefulShutdownTimeout(
            getNonNegativeInt("gracefulShutdownTimeout", toInt(s.getGracefulShutdownTimeout())))
        .withGracefulShutdownIgnoreSessions(toBool(s.getGracefulShutdownIgnoreSessions()))
        .withGracefulShutdownWaitForSessions(toBool(s.getGracefulShutdownWaitForSessions()));
    if (sc.getStartedServerState() == null) {
      sc.setStartedServerState(SERVER_DEFAULTS.getStartedServerState());
    }
    if (sc.getImage() == null) {
      sc.setImage(DEFAULT_IMAGE);
    }
    if (sc.getImagePullPolicy() == null) {
      sc.setImagePullPolicy(getDefaultImagePullPolicy(sc.getImage()));
    }
    if (sc.getShutdownPolicy() == null) {
      sc.setShutdownPolicy(SERVER_DEFAULTS.getShutdownPolicy());
    }
  }

  protected int getMaxReplicas(int replicas, IntOrString maxSurge) {
    String context = "maxSurge " + maxSurge;
    int delta = 0;
    if (maxSurge.isInteger()) {
      delta = getNonNegativeInt("maxSurge", toInt(maxSurge.getIntValue()));
    } else {
      int percent = getPercent(context, maxSurge.getStrValue());
      delta = getPercentage(replicas, percent);
    }
    return replicas + delta;
  }

  protected int getMinReplicas(int replicas, IntOrString maxUnavailable) {
    String context = "maxUnavailable " + maxUnavailable;
    int delta = 0;
    if (maxUnavailable.isInteger()) {
      delta = getNonNegativeInt("maxUnavailable", toInt(maxUnavailable.getIntValue()));
    } else {
      int percent = getPercent(context, maxUnavailable.getStrValue());
      delta = getPercentage(replicas, percent);
    }
    if (delta > replicas) {
      logWarning(
          context,
          " maxUnavailable must be less or equal to replicas ("
              + replicas
              + "), using "
              + replicas);
      delta = replicas;
    }
    return replicas - delta;
  }

  protected NonClusteredServer getEffectiveNonClusteredServer(String serverName) {
    NonClusteredServer rtn = new NonClusteredServer();
    getEffectiveProperties(
        rtn, getNonClusteredServerParents(serverName), NON_CLUSTERED_SERVER_PROPERTY_NAMES);
    return rtn;
  }

  protected ClusteredServer getEffectiveClusteredServer(String clusterName, String serverName) {
    ClusteredServer rtn = new ClusteredServer();
    getEffectiveProperties(
        rtn, getClusteredServerParents(clusterName, serverName), CLUSTERED_SERVER_PROPERTY_NAMES);
    return rtn;
  }

  protected Cluster getEffectiveCluster(String clusterName) {
    Cluster rtn = new Cluster();
    getEffectiveProperties(rtn, getClusterParents(clusterName), CLUSTER_PROPERTY_NAMES);
    return rtn;
  }

  protected List<Object> getNonClusteredServerParents(String serverName) {
    List<Object> parents = new ArrayList();
    parents.add(domainSpec.getServers().get(serverName));
    parents.add(domainSpec.getNonClusteredServerDefaults());
    parents.add(domainSpec.getServerDefaults());
    parents.add(NON_CLUSTERED_SERVER_DEFAULTS);
    parents.add(SERVER_DEFAULTS);
    return parents;
  }

  protected List<Object> getClusteredServerParents(String clusterName, String serverName) {
    List<Object> parents = new ArrayList();
    Cluster cluster = domainSpec.getClusters().get(clusterName);
    if (cluster != null) {
      parents.add(cluster.getServers().get(serverName));
      parents.add(cluster.getServerDefaults());
    }
    ClusterParams clusterParams = domainSpec.getClusterDefaults();
    if (clusterParams != null) {
      parents.add(clusterParams.getServerDefaults());
    }
    parents.add(domainSpec.getServerDefaults());
    parents.add(CLUSTERED_SERVER_DEFAULTS);
    parents.add(SERVER_DEFAULTS);
    return parents;
  }

  protected List<Object> getClusterParents(String clusterName) {
    List<Object> parents = new ArrayList();
    parents.add(domainSpec.getClusters().get(clusterName));
    parents.add(domainSpec.getClusterDefaults());
    parents.add(CLUSTER_DEFAULTS);
    return parents;
  }

  protected void getEffectiveProperties(Object to, List<Object> parents, String... propertyNames) {
    BeanInfo toBI = getBeanInfo(to);
    for (Object parent : parents) {
      if (parent != null) {
        BeanInfo parentBI = getBeanInfo(parent);
        copyParentProperties(to, toBI, parent, parentBI, propertyNames);
      }
    }
  }

  protected void copyParentProperties(
      Object to, BeanInfo toBI, Object parent, BeanInfo parentBI, String... propertyNames) {
    for (String propertyName : propertyNames) {
      setPropertyIfUnsetAndHaveValue(
          to,
          toBI,
          propertyName,
          getProperty(parent, getPropertyDescriptor(parentBI, propertyName)));
    }
  }

  protected void setPropertyIfUnsetAndHaveValue(
      Object to, BeanInfo toBI, String propertyName, Object propertyValue) {
    if (propertyValue == null) {
      return; // we don't have a value to set
    }
    PropertyDescriptor pd = getPropertyDescriptor(toBI, propertyName);
    if (pd == null) {
      throw new AssertionError("Property does not exist: " + propertyName + ", " + toBI);
    }
    if (getProperty(to, pd) != null) {
      return; // the property has already been set
    }
    invoke(pd.getWriteMethod(), to, propertyValue);
  }

  protected Object getProperty(Object o, PropertyDescriptor pd) {
    if (pd == null) {
      return null; // the object does not have this property
    }
    return invoke(pd.getReadMethod(), o);
  }

  protected PropertyDescriptor getPropertyDescriptor(BeanInfo bi, String propertyName) {
    for (PropertyDescriptor pd : bi.getPropertyDescriptors()) {
      if (propertyName.equals(pd.getName())) {
        return pd;
      }
    }
    return null;
  }

  protected BeanInfo getBeanInfo(Object o) {
    try {
      return Introspector.getBeanInfo(o.getClass());
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  protected Object invoke(Method m, Object o, Object... args) {
    try {
      return m.invoke(o, args);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  protected int getPercentage(double value, double percent) {
    double percentage = value * (percent / 100.0);
    return (int) (Math.ceil(percentage)); // round up to the next integer
  }

  protected int getPercent(String context, String val) {
    if (val == null) {
      logWarning(context, "value must not be null, using 0");
      return 0;
    }
    String percentSuffix = "%";
    if (!val.endsWith(percentSuffix)) {
      logWarning(context, "value must end with '%', using 0");
      return 0;
    }
    String percentString = val.substring(0, val.length() - percentSuffix.length());
    int percent = 0;
    try {
      percent = Integer.parseInt(percentString);
    } catch (NumberFormatException e) {
      logWarning(context, "percent must be an integer, using 0");
      return 0;
    }
    if (percent < 0 || 100 < percent) {
      logWarning(context, "percent must >= 0 and <= 100, using 0");
      return 0;
    }
    return percent;
  }

  protected int getNonNegativeInt(String context, int val) {
    if (val < 0) {
      logWarning(context, ": " + val + ", must be >= 0, using 0");
      return 0;
    }
    return val;
  }

  protected void logWarning(String context, String message) {
    LOGGER.warning(context + ": " + message); // TBD - do we need to i18n this?
  }

  protected int toInt(Integer val) {
    return (val != null) ? val.intValue() : 0;
  }

  protected boolean toBool(Boolean val) {
    return (val != null) ? val.booleanValue() : false;
  }
}
