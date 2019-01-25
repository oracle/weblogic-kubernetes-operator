// Copyright 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import java.net.MalformedURLException;
import java.net.URL;

public class KubernetesSchemaReference {
  private static final String K8S_SCHEMA_URL =
      "https://github.com/garethr/kubernetes-json-schema/blob/master/v%s/_definitions.json";
  private static final String K8S_SCHEMA_CACHE = "caches/kubernetes-%s.json";
  private static final String K8S_MARKDOWN_LINK = "k8s%s.md";

  private String version;

  private KubernetesSchemaReference(String version) {
    this.version = version;
  }

  public static KubernetesSchemaReference create(String version) {
    return new KubernetesSchemaReference(version);
  }

  URL getKubernetesSchemaUrl() throws MalformedURLException {
    return new URL(getUrlString());
  }

  private String getUrlString() {
    return String.format(K8S_SCHEMA_URL, version);
  }

  boolean matchesUrl(String url) {
    return url.equals(getUrlString());
  }

  public URL getKubernetesSchemaCacheUrl() {
    return KubernetesSchemaReference.class.getResource(String.format(K8S_SCHEMA_CACHE, version));
  }

  String getK8sMarkdownLink() {
    return String.format(K8S_MARKDOWN_LINK, version);
  }
}
