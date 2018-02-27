// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contains configuration of a WLS server
 * <p>
 * Copyright (c) 2017-2018, Oracle and/or its affiliates. All rights reserved.
 */
public class WlsServerConfig {
  final String name;
  final Integer listenPort;
  final String listenAddress;
  final Map<String, NetworkAccessPoint> networkAccessPoints = new HashMap<>();

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  public String getName() {
    return name;
  }

  public Integer getListenPort() {
    return listenPort;
  }

  public String getListenAddress() {
    return listenAddress;
  }

  /**
   * Returns an array containing all network access points configured in this WLS server
   * @return An array of NetworkAccessPoint containing configured network access points in this WLS server. If there
   * are no network access points configured in this server, an empty array is returned.
   */
  public List<NetworkAccessPoint> getNetworkAccessPoints() {
    return new ArrayList<>(networkAccessPoints.values());
  }

  WlsServerConfig(Map<String, Object> serverConfigMap) {
    this((String) serverConfigMap.get("name"),
            (Integer) serverConfigMap.get("listenPort"),
            (String) serverConfigMap.get("listenAddress"),
            (Map) serverConfigMap.get("networkAccessPoints"));
  }

  public WlsServerConfig(String name, Integer listenPort, String listenAddress, Map networkAccessPointsMap) {
    this.name = name;
    this.listenPort = listenPort;
    this.listenAddress = listenAddress;
    if (networkAccessPointsMap != null) {
      List<Map<String, Object>> networkAccessPointItems =  (List<Map<String, Object>>) networkAccessPointsMap.get("items");
      if (networkAccessPointItems != null && networkAccessPointItems.size() > 0) {
        for (Map<String, Object> networkAccessPointConfigMap:  networkAccessPointItems) {
          NetworkAccessPoint networkAccessPoint = new NetworkAccessPoint(networkAccessPointConfigMap);
          this.networkAccessPoints.put(networkAccessPoint.getName(), networkAccessPoint);
        }
      }
    }
  }

  /**
   * Return the list of configuration attributes to be retrieved from the REST search request to the WLS admin server.
   * The value would be used for constructing the REST POST request.
   */
  static String getSearchFields() {
    return "'name', 'cluster', 'listenPort', 'listenAddress', 'publicPort'";
  }

  @Override
  public String toString() {
    return "WlsServerConfig{" +
            "name='" + name + '\'' +
            ", listenPort=" + listenPort +
            ", listenAddress='" + listenAddress + '\'' +
            ", networkAccessPoints=" + networkAccessPoints +
            '}';
  }


  /** Load the applications to be upgraded from the json result
   *
   * @param appJsonResult A JSON string result from getting all applications deployed to WebLogic domain using REST API
   * @param patchAppMap A map containing a list of application info provided as REST client input data
   * @return A map containing a list of applications to be upgraded.
   */
  public static synchronized Map<String, WlsAppConfig> loadAppsFromJsonResult(String appJsonResult,
                                                                       Map<String, List<String>> patchAppMap) {

     // The jsonResult looks like this:
     // {"items": [{
     //    "sourcePath": "\/shared\/applications\/simpleApp.war",
     //    "name": "simpleApp",
     //    "targets": [{"identity": [
     //        "clusters",
     //        "cluster-1"
     //    ]}]
     //}]}

    LOGGER.fine(appJsonResult);

    ObjectMapper mapper = new ObjectMapper();
    try {
      Map result = mapper.readValue(appJsonResult, Map.class);

      // items=[{sourcePath=/shared/applications/simpleApp.war, name=simpleApp, targets=[{identity=[clusters, cluster-1]}]}]
      List<Map<String,Object>> items = (List<Map<String,Object>>) result.get("items");

      Map<String, WlsAppConfig> wlsAppConfigMap = new HashMap<String, WlsAppConfig>();

      if (items != null) {
        for (Map<String, Object> thisApp : items) {

          // Each item is related to an app info.
          WlsAppConfig wlsAppConfig = new WlsAppConfig();

          String appNameFromJson = (String)thisApp.get("name");

          if (!patchAppMap.containsKey(appNameFromJson)) {
            continue;
          }

          wlsAppConfig.setAppName(appNameFromJson);

          wlsAppConfig.setSourcePath((String)thisApp.get("sourcePath"));

          //update patchedLocation, backupLocation in wlsAppConfig.
          // The first element for the value of the MapEntry of patchAppMap is the patchedLocation,
          // the second element is the backupLocation. The patchAppMap is built at UpgradeApplicationsResource.
          wlsAppConfig.setPatchedLocation(patchAppMap.get(appNameFromJson).get(0));
          wlsAppConfig.setBackupLocation(patchAppMap.get(appNameFromJson).get(1));

          // The targets looks like: [{identity=[servers, admin-server]}, {identity=[clusters, cluster-1]},
          //                          {identity=[servers, ms3]}, {identity=[clusters,cluster-2]}]
          // Change this targets Map to Map<String, List<String>> type for easy handling later:
          // e.g., [servers={admin-server,ms3}, clusters={cluster-1,cluster-2}]

          // targets looks like this: [clusters={cluster-1, cluster-2},servers={ms3,ms4}]
          Map<String, List<String>> targets = new HashMap<String, List<String>>();

          List<String> serversList = new ArrayList<String>();
          List<String> clustersList = new ArrayList<String>();

          List<Map<String, Object>> targetsList = (List<Map<String, Object>>)thisApp.get("targets");

          if (targetsList != null) {
            for (Map<String, Object> thisTargets : targetsList) {
              List<String> targetIdentity = (List<String>) thisTargets.get("identity");


              // targetIdentity = [servers, admin-server]
              // convert each targetIdentity list to a MapEntry: servers=admin-server
              if (targetIdentity != null) {

                String firstElem = targetIdentity.get(0);

                if (firstElem.equals("servers")) {
                  serversList.add(targetIdentity.get(1));
                } else if (firstElem.equals("clusters")) {
                  clustersList.add(targetIdentity.get(1));
                }
              }
            }
          }
          targets.put("servers", serversList);
          targets.put("clusters", clustersList);

          wlsAppConfig.setTargets(targets);

          LOGGER.fine(wlsAppConfig.toString());

          // add to the map
          wlsAppConfigMap.put(appNameFromJson, wlsAppConfig);

        }

        return wlsAppConfigMap;
      }

    } catch (Exception e) {
      LOGGER.warning(MessageKeys.JSON_PARSING_FAILED, appJsonResult, e.getMessage());
    }
    return null;

  }

}
