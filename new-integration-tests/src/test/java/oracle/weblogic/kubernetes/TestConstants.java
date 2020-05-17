// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.net.InetAddress;
import java.util.Optional;

import oracle.weblogic.kubernetes.utils.TestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public interface TestConstants {

  // domain constants
  public static final String DOMAIN_VERSION = "v7";
  public static final String DOMAIN_API_VERSION = "weblogic.oracle/" + DOMAIN_VERSION;

  // operator constants
  public static final String OPERATOR_RELEASE_NAME = "weblogic-operator";
  public static final String OPERATOR_CHART_DIR =
      "../kubernetes/charts/weblogic-operator";
  public static final String IMAGE_NAME_OPERATOR =
      "oracle/weblogic-kubernetes-operator";
  public static final String OPERATOR_DOCKER_BUILD_SCRIPT =
      "../buildDockerImage.sh";
  public static final String REPO_DUMMY_VALUE = "dummy";
  public static final String REPO_SECRET_NAME = "ocir-secret";
  public static final String REPO_REGISTRY = Optional.ofNullable(System.getenv("REPO_REGISTRY"))
      .orElse(REPO_DUMMY_VALUE);
  public static final String REPO_DEFAULT = "phx.ocir.io/weblogick8s/";
  public static final String REPO_NAME = Optional.ofNullable(System.getenv("KIND_REPO"))
      .orElse(!REPO_REGISTRY.equals(REPO_DUMMY_VALUE) ? REPO_DEFAULT : "");
  public static final String REPO_USERNAME = Optional.ofNullable(System.getenv("REPO_USERNAME"))
      .orElse(REPO_DUMMY_VALUE);
  public static final String REPO_PASSWORD = Optional.ofNullable(System.getenv("REPO_PASSWORD"))
      .orElse(REPO_DUMMY_VALUE);
  public static final String REPO_EMAIL = Optional.ofNullable(System.getenv("REPO_EMAIL"))
      .orElse(REPO_DUMMY_VALUE);

  // jenkins constants
  public static final String BUILD_ID = Optional.ofNullable(System.getenv("BUILD_ID"))
      .orElse("");
  public static final String BRANCH_NAME_FROM_JENKINS = Optional.ofNullable(System.getenv("BRANCH"))
      .orElse("");

  public static final String K8S_NODEPORT_HOST = Optional.ofNullable(System.getenv("K8S_NODEPORT_HOST"))
        .orElse(assertDoesNotThrow(() -> InetAddress.getLocalHost().getHostName()));
  public static final String GOOGLE_REPO_URL = "https://kubernetes-charts.storage.googleapis.com/";
  public static final String LOGS_DIR = System.getenv().getOrDefault("RESULT_ROOT",
      System.getProperty("java.io.tmpdir")) + "/diagnosticlogs";

  public static final String PV_ROOT = System.getenv().getOrDefault("PV_ROOT",
      System.getProperty("java.io.tmpdir") + "/ittestspvroot");

  // NGINX constants
  public static final String NGINX_RELEASE_NAME = "nginx-release" + BUILD_ID;
  public static final String STABLE_REPO_NAME = "stable";
  public static final String NGINX_CHART_NAME = "nginx-ingress";

  // MII image constants
  public static final String MII_BASIC_WDT_MODEL_FILE = "model-singleclusterdomain-sampleapp-wls.yaml";
  public static final String MII_BASIC_IMAGE_NAME = REPO_NAME + "mii-basic-image";
  public static final String MII_BASIC_IMAGE_TAG = TestUtils.getDateAndTimeStamp();
  public static final String MII_BASIC_APP_NAME = "sample-app";
  public static final String MII_TWO_APP_WDT_MODEL_FILE = "model-singlecluster-two-sampleapp-wls.yaml";

  // application constants
  public static final String MII_APP_RESPONSE_V1 = "Hello World, you have reached server managed-server";
  public static final String MII_APP_RESPONSE_V2 = "Hello World AGAIN, you have reached server managed-server";
  public static final String MII_APP_RESPONSE_V3 = "How are you doing! You have reached server managed-server";
  public static final String READ_STATE_COMMAND = "/weblogic-operator/scripts/readState.sh";

}
