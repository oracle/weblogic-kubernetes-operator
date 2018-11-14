// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json.mojo;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

class ExternalSchema {

  private String url;
  private String cache;

  ExternalSchema(String url, String cache) {
    this.url = url;
    this.cache = cache;
  }

  URL getUrl() throws MalformedURLException {
    return new URL(url);
  }

  URL getCacheURL(String baseDir) throws MalformedURLException {
    return new File(new File(baseDir), cache).toURI().toURL();
  }
}
