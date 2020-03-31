// Copyright 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Presents all parameters that the test InstallParams needs.

public class InstallParams {
    public static final String WIT_TYPE = "WIT";
    public static final String WDT_TYPE = "WDT";

    public static final String DEFAULT_WIT_DOWNLOAD_URL = "https://github.com//oracle/weblogic-image-tool";
    public static final String DEFAULT_WIT_VERSION      = "release-1.8.3";
    public static final String WIT_FILE_NAME    = "imagetool.zip";

    public static final String DEFAULT_WDT_DOWNLOAD_URL = "https://github.com//oracle/weblogic-deploy-tooling";
    public static final String DEFAULT_WDT_VERSION      = "weblogic-deploy-tooling-1.7.2";
    public static final String WDT_FILE_NAME    = "weblogic-deploy.zip";

    private String type;
    private String version;
    private String location;
    private boolean verify;
    private boolean unzip;

    public InstallParams type(String type) {
        this.type = type;
        return this;
    }

    public String getType() {
        return type;
    }

    public InstallParams version(String version) {
        this.version = version;
        return this;
    }

    public String getVersion() {
        if (version == null) {
            if (WIT_TYPE.equals(type)) return DEFAULT_WIT_VERSION;
            else return DEFAULT_WDT_VERSION;
        }
        return version;
    }

    public InstallParams location(String location) {
        this.location = location;
        return this;
    }

    public String getLocation() {
        if (location == null) {
            if (WIT_TYPE.equals(type)) return DEFAULT_WIT_DOWNLOAD_URL;
            else return DEFAULT_WDT_DOWNLOAD_URL;
        }
        return location;
    }

    public InstallParams verify(boolean verify) {
        this.verify = verify;
        return this;
    }

    public boolean isVerify() {
        return verify;
    }

    public InstallParams unzip(boolean unzip) {
        this.unzip = unzip;
        return this;
    }

    public boolean isUnzip() {
        return unzip;
    }

    public String getFileName() {
      if (WIT_TYPE.equals(type)) {
        return WIT_FILE_NAME;
      } else {
        return WDT_FILE_NAME;
      }
    }
}
