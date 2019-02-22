// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.http.HttpClient;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/** Step for updating the cluster size of a WebLogic dynamic cluster. */
public class UpdateDynamicClusterStep extends Step {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  final WlsClusterConfig wlsClusterConfig;
  final int targetClusterSize;

  /**
   * Constructor.
   *
   * @param wlsClusterConfig The WlsClusterConfig object for the WebLogic dynamic cluster to be
   *     updated
   * @param targetClusterSize The target dynamic cluster size
   * @param next The next Step to be performed
   */
  public UpdateDynamicClusterStep(
      WlsClusterConfig wlsClusterConfig, int targetClusterSize, Step next) {
    super(next);
    this.wlsClusterConfig = wlsClusterConfig;
    this.targetClusterSize = targetClusterSize;
  }

  @Override
  public NextAction apply(Packet packet) {

    String clusterName = wlsClusterConfig == null ? "null" : wlsClusterConfig.getClusterName();

    if (wlsClusterConfig == null || !wlsClusterConfig.hasDynamicServers()) {
      LOGGER.warning(MessageKeys.WLS_UPDATE_CLUSTER_SIZE_INVALID_CLUSTER, clusterName);
    } else {
      try {
        LOGGER.info(MessageKeys.WLS_UPDATE_CLUSTER_SIZE_STARTING, clusterName, targetClusterSize);
        HttpClient httpClient = (HttpClient) packet.get(HttpClient.KEY);
        DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
        WlsDomainConfig domainTopology =
            (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);

        long startTime = System.currentTimeMillis();

        String serviceURL =
            HttpClient.getServiceURL(
                info.getServers().get(domainTopology.getAdminServerName()).getService().get());

        boolean successful =
            updateDynamicClusterSizeWithServiceURL(
                wlsClusterConfig, targetClusterSize, httpClient, serviceURL);

        if (successful) {
          LOGGER.info(
              MessageKeys.WLS_CLUSTER_SIZE_UPDATED,
              clusterName,
              targetClusterSize,
              (System.currentTimeMillis() - startTime));
        } else {
          LOGGER.warning(MessageKeys.WLS_UPDATE_CLUSTER_SIZE_FAILED, clusterName, null);
        }
      } catch (Throwable t) {
        LOGGER.warning(MessageKeys.WLS_UPDATE_CLUSTER_SIZE_FAILED, clusterName, t);
      }
    }
    return doNext(packet);
  }

  /**
   * Static method to update the WebLogic dynamic cluster size configuration.
   *
   * @param wlsClusterConfig The WlsClusterConfig object of the WLS cluster whose cluster size needs
   *     to be updated. The caller should make sure that the cluster is a dynamic cluster.
   * @param targetClusterSize The target dynamic cluster size
   * @param httpClient HttpClient object for issuing the REST request
   * @param serviceURL service URL of the WebLogic admin server
   * @return true if the request to update the cluster size is successful, false if it was not
   *     successful
   */
  private static boolean updateDynamicClusterSizeWithServiceURL(
      final WlsClusterConfig wlsClusterConfig,
      final int targetClusterSize,
      final HttpClient httpClient,
      final String serviceURL) {
    LOGGER.entering();

    boolean result = false;
    // Update the dynamic cluster size of the WebLogic cluster
    String jsonResult =
        httpClient
            .executePostUrlOnServiceClusterIP(
                wlsClusterConfig.getUpdateDynamicClusterSizeUrl(),
                serviceURL,
                wlsClusterConfig.getUpdateDynamicClusterSizePayload(targetClusterSize))
            .getResponse();

    result = wlsClusterConfig.checkUpdateDynamicClusterSizeJsonResult(jsonResult);
    LOGGER.exiting(result);
    return result;
  }
}
