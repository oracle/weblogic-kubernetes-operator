// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.

package oracle.kubernetes.operator.wlsconfig;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 */
public class WlsServerConfigTest {

  @Test
  public void verifyOneAppLoadedFromJsonString() throws Exception {

    Map<String, List<String>> patchedAppInfoMap = new HashMap<>();
    List<String> locationList = new ArrayList<>();
    locationList.add("/path/to/patchedLocation1v1");
    locationList.add("/patch/to/backupLocation1v1");
    patchedAppInfoMap.put("simpleApp", locationList);

    System.out.println("JSON_STRING_1_APP = " + JSON_STRING_1_APP);

    Map<String, WlsAppConfig> wlsAppConfigMap =
      WlsServerConfig.loadAppsFromJsonResult(JSON_STRING_1_APP, patchedAppInfoMap);

    assertNotNull(wlsAppConfigMap);
    assertEquals(1, wlsAppConfigMap.size());

    for (String key : wlsAppConfigMap.keySet()) {
      assertEquals("simpleApp", key);
      assertEquals("simpleApp", wlsAppConfigMap.get(key).getAppName());
      assertEquals("/path/to/patchedLocation1v1", wlsAppConfigMap.get(key).getPatchedLocation());
      assertEquals("/patch/to/backupLocation1v1", wlsAppConfigMap.get(key).getBackupLocation());
      assertEquals("/shared/applications/simpleApp.war", wlsAppConfigMap.get(key).getSourcePath());

      Map<String, List<String>> targetsMap = wlsAppConfigMap.get(key).getTargets();
      assertEquals(0,targetsMap.get("servers").size());
      assertEquals(1, targetsMap.get("clusters").size());
      assertEquals("cluster-1", targetsMap.get("clusters").get(0));
    }
  }

  @Test
  public void verifyTwoAppLoadedFromJsonString() throws Exception {

    Map<String, List<String>> patchedAppInfoMap = new HashMap<>();
    List<String> locationList = new ArrayList<>();
    locationList.add("/path/to/patchedLocation1v1");
    locationList.add("/patch/to/backupLocation1v1");
    patchedAppInfoMap.put("app1", locationList);

    locationList = new ArrayList<>();
    locationList.add("/path/to/patchedLocation2v1");
    locationList.add("/patch/to/backupLocation2v1");
    patchedAppInfoMap.put("app2", locationList);

    Map<String, WlsAppConfig> wlsAppConfigMap =
      WlsServerConfig.loadAppsFromJsonResult(JSON_STRING_2_APPS, patchedAppInfoMap);

    assertNotNull(wlsAppConfigMap);
    assertEquals(2, wlsAppConfigMap.size());

    // for app1
    assertEquals("app1", wlsAppConfigMap.get("app1").getAppName());
    assertEquals("/path/to/patchedLocation1v1", wlsAppConfigMap.get("app1").getPatchedLocation());
    assertEquals("/patch/to/backupLocation1v1", wlsAppConfigMap.get("app1").getBackupLocation());
    assertEquals("/shared/applications/app1.war", wlsAppConfigMap.get("app1").getSourcePath());

    Map<String, List<String>> targetsMap = wlsAppConfigMap.get("app1").getTargets();

    // should be {servers=[admin-server,ms1],clusters=[]}
    assertEquals(2,targetsMap.get("servers").size());
    assertEquals("admin-server", targetsMap.get("servers").get(0));
    assertEquals("ms1", targetsMap.get("servers").get(1));
    assertEquals(0, targetsMap.get("clusters").size());

    // for app2
    assertEquals("app2", wlsAppConfigMap.get("app2").getAppName());
    assertEquals("/path/to/patchedLocation2v1", wlsAppConfigMap.get("app2").getPatchedLocation());
    assertEquals("/patch/to/backupLocation2v1", wlsAppConfigMap.get("app2").getBackupLocation());
    assertEquals("/shared/applications/app2.war", wlsAppConfigMap.get("app2").getSourcePath());

    targetsMap = wlsAppConfigMap.get("app2").getTargets();

    // should be {servers=[], clusters=[cluster-1]}
    assertEquals(0,targetsMap.get("servers").size());
    assertEquals(1,targetsMap.get("clusters").size());
    assertEquals("cluster-1", targetsMap.get("clusters").get(0));
  }


     // {"items": [{
     //    "sourcePath": "\/shared\/applications\/simpleApp.war",
     //    "name": "simpleApp",
     //    "targets": [{"identity": [
     //        "clusters",
     //        "cluster-1"
     //    ]}]
     //}]}

  final String JSON_STRING_1_APP = "{\"items\": [\n" +
          "    {\n" +
          "        \"sourcePath\": \"/shared/applications/simpleApp.war\",\n" +
          "        \"name\": \"simpleApp\",\n" +
          "        \"targets\": [\n" +
          "            {\"identity\":[\"clusters\", \"cluster-1\"]\n" +
          "            }\n" +
          "          ]\n" +
          "    }]}\n";

  final String JSON_STRING_2_APPS = "{\"items\": [\n" +
                                    "    {\n" +
                                    "        \"sourcePath\": \"\\/shared\\/applications\\/app1.war\",\n" +
                                    "        \"name\": \"app1\",\n" +
                                    "        \"targets\": [\n" +
                                    "            {\"identity\": [\n" +
                                    "                \"servers\",\n" +
                                    "                \"admin-server\"\n" +
                                    "            ]},\n" +
                                    "            {\"identity\": [\n" +
                                    "                \"servers\",\n" +
                                    "                \"ms1\"\n" +
                                    "            ]}\n" +
                                    "        ]\n" +
                                    "    },\n" +
                                    "    {\n" +
                                    "        \"sourcePath\": \"\\/shared\\/applications\\/app2.war\",\n" +
                                    "        \"name\": \"app2\",\n" +
                                    "        \"targets\": [{\"identity\": [\n" +
                                    "            \"clusters\",\n" +
                                    "            \"cluster-1\"\n" +
                                    "        ]}]\n" +
                                    "    }\n" +
                                    "]}";


}
