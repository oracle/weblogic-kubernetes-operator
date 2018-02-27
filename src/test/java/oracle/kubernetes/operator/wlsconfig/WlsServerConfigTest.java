// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.

package oracle.kubernetes.operator.wlsconfig;

import oracle.kubernetes.operator.rest.model.UpgradeApplicationModel;
import oracle.kubernetes.operator.rest.model.UpgradeApplicationsModel;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 */
public class WlsServerConfigTest {

  @Test
  public void verifyOneAppLoadedFromJsonString() throws Exception {

    UpgradeApplicationsModel appsToUpgrade = new UpgradeApplicationsModel();

    List<UpgradeApplicationModel> appsParamsModelList = new ArrayList<UpgradeApplicationModel>();

    UpgradeApplicationModel app1ParamsModel = new UpgradeApplicationModel();
    app1ParamsModel.setApplicationName("testApp");
    app1ParamsModel.setPatchedLocation("/tmp/test1v2.war");
    app1ParamsModel.setBackupLocation("/tmp/test1v1.war");

    appsParamsModelList.add(app1ParamsModel);

    appsToUpgrade.setApplications(appsParamsModelList);

    Map<String, WlsAppConfig> wlsAppConfigMap =
      WlsServerConfig.loadAppsFromJsonResult(JSON_STRING_1_APP, appsToUpgrade);

    assertNotNull(wlsAppConfigMap);
    assertEquals(1, wlsAppConfigMap.size());

    for (String key : wlsAppConfigMap.keySet()) {
      assertEquals("testApp", key);
      assertEquals("testApp", wlsAppConfigMap.get(key).getAppName());
      assertEquals("/tmp/test1v2.war", wlsAppConfigMap.get(key).getPatchedLocation());
      assertEquals("/tmp/test1v1.war", wlsAppConfigMap.get(key).getBackupLocation());

      assertEquals("/shared/applications/simpleApp.war", wlsAppConfigMap.get(key).getSourcePath());

      Map<String, List<String>> targetsMap = wlsAppConfigMap.get(key).getTargets();
      assertEquals(0,targetsMap.get("servers").size());
      assertEquals(1, targetsMap.get("clusters").size());
      assertEquals("cluster-1", targetsMap.get("clusters").get(0));
    }
  }

  @Test
  public void verifyTwoAppLoadedFromJsonString() throws Exception {

    UpgradeApplicationsModel appsToUpgrade = new UpgradeApplicationsModel();

    List<UpgradeApplicationModel> appsParamsModelList = new ArrayList<UpgradeApplicationModel>();

    UpgradeApplicationModel app1ParamsModel = new UpgradeApplicationModel();
    app1ParamsModel.setApplicationName("testApp1");
    app1ParamsModel.setPatchedLocation("/tmp/test1v2.war");
    app1ParamsModel.setBackupLocation("/tmp/test1v1.war");

    appsParamsModelList.add(app1ParamsModel);

    UpgradeApplicationModel app2ParamsModel = new UpgradeApplicationModel();
    app2ParamsModel.setApplicationName("testApp2");
    app2ParamsModel.setPatchedLocation("/tmp/test2v2.war");
    app2ParamsModel.setBackupLocation("/tmp/test2v1.war");

    appsParamsModelList.add(app2ParamsModel);
    appsToUpgrade.setApplications(appsParamsModelList);

    Map<String, WlsAppConfig> wlsAppConfigMap =
      WlsServerConfig.loadAppsFromJsonResult(JSON_STRING_2_APPS, appsToUpgrade);

    assertNotNull(wlsAppConfigMap);
    assertEquals(2, wlsAppConfigMap.size());

    // for testApp1
    assertEquals("testApp1", wlsAppConfigMap.get("testApp1").getAppName());
    assertEquals("/tmp/test1v2.war", wlsAppConfigMap.get("testApp1").getPatchedLocation());
    assertEquals("/tmp/test1v1.war", wlsAppConfigMap.get("testApp1").getBackupLocation());

    assertEquals("/shared/applications/app1.war", wlsAppConfigMap.get("testApp1").getSourcePath());

    Map<String, List<String>> targetsMap = wlsAppConfigMap.get("testApp1").getTargets();

    // should be {servers=[admin-server,ms1],clusters=[]}
    assertEquals(2,targetsMap.get("servers").size());
    assertEquals("admin-server", targetsMap.get("servers").get(0));
    assertEquals("ms1", targetsMap.get("servers").get(1));
    assertEquals(0, targetsMap.get("clusters").size());

    // for testApp2
    assertEquals("testApp2", wlsAppConfigMap.get("testApp2").getAppName());
    assertEquals("/tmp/test2v2.war", wlsAppConfigMap.get("testApp2").getPatchedLocation());
    assertEquals("/tmp/test2v1.war", wlsAppConfigMap.get("testApp2").getBackupLocation());

    assertEquals("/shared/applications/app2.war", wlsAppConfigMap.get("testApp2").getSourcePath());

    targetsMap = wlsAppConfigMap.get("testApp2").getTargets();

    // should be {servers=[], clusters=[cluster-1]}
    assertEquals(0,targetsMap.get("servers").size());
    assertEquals(1,targetsMap.get("clusters").size());
    assertEquals("cluster-1", targetsMap.get("clusters").get(0));
  }


  @Test
  public void verifyEmptyPatchedLocation() throws Exception {

    UpgradeApplicationsModel appsToUpgrade = new UpgradeApplicationsModel();

    List<UpgradeApplicationModel> appsParamsModelList = new ArrayList<UpgradeApplicationModel>();

    UpgradeApplicationModel app1ParamsModel = new UpgradeApplicationModel();
    app1ParamsModel.setApplicationName("testApp");
    app1ParamsModel.setPatchedLocation("");
    app1ParamsModel.setBackupLocation("/tmp/test1v1.war");

    appsParamsModelList.add(app1ParamsModel);

    appsToUpgrade.setApplications(appsParamsModelList);


    try {
      Map<String, WlsAppConfig> wlsAppConfigMap =
        WlsServerConfig.loadAppsFromJsonResult(JSON_STRING_1_APP, appsToUpgrade);
      assertFalse("Should throw exception because of the empty patchedLocation", true);
    } catch (Exception e) {
      assertTrue("Catch the exception as expected because of the empty patchedLocation", true);
    }

  }

  @Test
  public void verifyNotSetPatchedLocation() throws Exception {

    UpgradeApplicationsModel appsToUpgrade = new UpgradeApplicationsModel();

    List<UpgradeApplicationModel> appsParamsModelList = new ArrayList<UpgradeApplicationModel>();

    UpgradeApplicationModel app1ParamsModel = new UpgradeApplicationModel();
    app1ParamsModel.setApplicationName("testApp");
    app1ParamsModel.setBackupLocation("/tmp/test1v1.war");

    appsParamsModelList.add(app1ParamsModel);

    appsToUpgrade.setApplications(appsParamsModelList);

    try {
      Map<String, WlsAppConfig> wlsAppConfigMap =
        WlsServerConfig.loadAppsFromJsonResult(JSON_STRING_1_APP, appsToUpgrade);
      assertFalse("Should throw exception because of the missing patchedLocation", true);
    } catch (Exception e) {
      assertTrue("Catch the exception as expected because of the missing patchedLocation", true);
    }
  }


  @Test
  public void verifyEmptyBackupLocation() throws Exception {

    UpgradeApplicationsModel appsToUpgrade = new UpgradeApplicationsModel();

    List<UpgradeApplicationModel> appsParamsModelList = new ArrayList<UpgradeApplicationModel>();

    UpgradeApplicationModel app1ParamsModel = new UpgradeApplicationModel();
    app1ParamsModel.setApplicationName("testApp");
    app1ParamsModel.setPatchedLocation("/tmp/test1v1.war");
    app1ParamsModel.setBackupLocation("");

    appsParamsModelList.add(app1ParamsModel);

    appsToUpgrade.setApplications(appsParamsModelList);

    System.out.println("JSON_STRING_1_APP = " + JSON_STRING_1_APP);

    try {
      Map<String, WlsAppConfig> wlsAppConfigMap =
        WlsServerConfig.loadAppsFromJsonResult(JSON_STRING_1_APP, appsToUpgrade);
      assertFalse("Should throw exception because of the empty backupLocation", true);
    } catch (Exception e) {
      assertTrue("Catch the exception as expected because of the empty backupLocation", true);
    }

  }

  @Test
  public void verifyNotSetBackupLocation() throws Exception {

    UpgradeApplicationsModel appsToUpgrade = new UpgradeApplicationsModel();

    List<UpgradeApplicationModel> appsParamsModelList = new ArrayList<UpgradeApplicationModel>();

    UpgradeApplicationModel app1ParamsModel = new UpgradeApplicationModel();
    app1ParamsModel.setApplicationName("testApp");
    app1ParamsModel.setPatchedLocation("/tmp/test1v1.war");

    appsParamsModelList.add(app1ParamsModel);

    appsToUpgrade.setApplications(appsParamsModelList);

    try {
      Map<String, WlsAppConfig> wlsAppConfigMap =
        WlsServerConfig.loadAppsFromJsonResult(JSON_STRING_1_APP, appsToUpgrade);
      assertFalse("Should throw exception because of the missing backupLocation", true);
    } catch (Exception e) {
      assertTrue("Catch the exception as expected because of the missing backupLocation", true);
    }
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
          "        \"name\": \"testApp\",\n" +
          "        \"targets\": [\n" +
          "            {\"identity\":[\"clusters\", \"cluster-1\"]\n" +
          "            }\n" +
          "          ]\n" +
          "    }]}\n";

  final String JSON_STRING_2_APPS = "{\"items\": [\n" +
                                    "    {\n" +
                                    "        \"sourcePath\": \"\\/shared\\/applications\\/app1.war\",\n" +
                                    "        \"name\": \"testApp1\",\n" +
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
                                    "        \"name\": \"testApp2\",\n" +
                                    "        \"targets\": [{\"identity\": [\n" +
                                    "            \"clusters\",\n" +
                                    "            \"cluster-1\"\n" +
                                    "        ]}]\n" +
                                    "    }\n" +
                                    "]}";


}
