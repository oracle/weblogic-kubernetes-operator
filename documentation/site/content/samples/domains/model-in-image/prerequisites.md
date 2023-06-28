---
title: "Prerequisites"
date: 2019-02-23T17:32:31-05:00
weight: 1
description: "Follow these prerequisite steps for WLS domain type."
---

### Prerequisites

1. The `JAVA_HOME` environment variable must be set and must reference a valid JDK 8 or 11 installation.

1. Get the operator source and put it in `/tmp/weblogic-kubernetes-operator`.

   For example:

   ```shell
   $ cd /tmp
   ```
   ```shell
   $ git clone --branch v{{< latestVersion >}} https://github.com/oracle/weblogic-kubernetes-operator.git
   ```

   **NOTE**: We will refer to the top directory of the operator source tree as `/tmp/weblogic-kubernetes-operator`; however, you can use a different location.

   For additional information about obtaining the operator source, see the [Developer Guide Requirements](https://oracle.github.io/weblogic-kubernetes-operator/developerguide/requirements/).

1. Copy the Model in Image sample to a new directory; for example, use directory `/tmp/sample`.
   ```
   $ mkdir -p /tmp/sample
   ```

   ```
   $ cp -r /tmp/weblogic-kubernetes-operator/kubernetes/samples/scripts/create-weblogic-domain/model-in-image/* /tmp/sample
   ```
   **NOTE**: We will refer to this working copy of the sample as `/tmp/sample`; however, you can use a different location.

1. Copy the `wdt-artifacts` directory of the sample to a new directory; for example, use directory `/tmp/sample/wdt-artifacts`.


   ```shell
   $ mkdir -p /tmp/sample/wdt-artifacts
   ```
   ```shell
   $ cp -r /tmp/weblogic-kubernetes-operator/kubernetes/samples/scripts/create-weblogic-domain/wdt-artifacts/* /tmp/sample/wdt-artifacts
   ```

1. Download the latest [WebLogic Deploy Tooling](https://github.com/oracle/weblogic-deploy-tooling/releases) (WDT) and [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool/releases) (WIT) installer ZIP files to your `/tmp/sample/wdt-artifacts` directory. Both WDT and WIT are required to create the images.

   ```shell
   $ cd /tmp/sample/wdt-artifacts
   ```
   ```shell
   $ curl -m 120 -fL https://github.com/oracle/weblogic-deploy-tooling/releases/latest/download/weblogic-deploy.zip \
     -o /tmp/sample/wdt-artifacts/weblogic-deploy.zip
   ```
   ```shell
   $ curl -m 120 -fL https://github.com/oracle/weblogic-image-tool/releases/latest/download/imagetool.zip \
     -o /tmp/sample/wdt-artifacts/imagetool.zip
   ```

1. To set up the WebLogic Image Tool, run the following commands:

   ```shell
   $ cd /tmp/sample/wdt-artifacts
   ```
   ```shell
   $ unzip imagetool.zip
   ```
   ```shell
   $ ./imagetool/bin/imagetool.sh cache deleteEntry --key wdt_latest
   ```
   ```shell
   $ ./imagetool/bin/imagetool.sh cache addInstaller \
     --type wdt \
     --version latest \
     --path /tmp/sample/wdt-artifacts/weblogic-deploy.zip
   ```

   Note that the WebLogic Image Tool `cache deleteEntry` command does nothing
   if the `wdt_latest` key doesn't have a corresponding cache entry. It is included
   because the WIT cache lookup information is stored in the `$HOME/cache/.metadata`
   file by default, and if the cache already
   has a version of WDT in its `--type wdt --version latest` location, then the
   `cache addInstaller` command would fail.
   For more information about the WIT cache, see the
   [WIT Cache](https://oracle.github.io/weblogic-image-tool/userguide/tools/cache/) documentation.

   These steps will install WIT to the `/tmp/sample/wdt-artifacts/imagetool` directory,
   plus put a `wdt_latest` entry in the tool's cache which points to the WDT ZIP file installer.
   You will use WIT and its cached reference to the WDT installer later in the sample for creating model images.

1. To set up the WebLogic Deploy Tooling that we will use later for the archive helper, run the following command:

   ```shell
   $ unzip /tmp/sample/wdt-artifacts/weblogic-deploy.zip
   ```
   {{< rawhtml >}}
   <a name="resume"></a>
   {{< /rawhtml >}}

1. Make sure an operator is set up to manage the namespace, `sample-domain1-ns`. Also, make sure a Traefik ingress controller is managing the same namespace and listening on port `30305`.
To do this, follow the same steps as the [Quick Start](https://oracle.github.io/weblogic-kubernetes-operator/quickstart/) guide up through the [Prepare for a domain]({{< relref "/quickstart/prepare.md" >}}) step.


   {{% notice note %}}
   Make sure you **stop** when you complete the "Prepare for a domain" step and then resume following these instructions.
   {{% /notice %}}

1. Set up ingresses that will redirect HTTP from the Traefik port `30305` to the clusters in this sample's WebLogic domains.
   - Run `kubectl apply -f` on each of the ingress YAML files that are already included in the sample source directory:

       ```
       $ kubectl apply -f https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/scripts/create-weblogic-domain/ingresses/traefik-ingress-sample-domain1-admin-server.yaml
       $ kubectl apply -f https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/scripts/create-weblogic-domain/ingresses/traefik-ingress-sample-domain1-cluster-cluster-1.yaml
       $ kubectl apply -f https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/scripts/create-weblogic-domain/ingresses/traefik-ingress-sample-domain2-cluster-cluster-1.yaml
       ```

   **NOTE**: We give each cluster ingress a different host name that is decorated using both its operator domain UID and its cluster name. This makes each cluster uniquely addressable even when cluster names are the same across different clusters.  When using `curl` to access the WebLogic domain through the ingress, you will need to supply a host name header that matches the host names in the ingress.

   For more information on ingresses and load balancers, see [Ingress]({{< relref "/managing-domains/accessing-the-domain/ingress/_index.md" >}}).


1. Obtain the WebLogic 12.2.1.4 image that is referenced by the sample's Domain resource YAML.

   a. Use a browser to access the [Oracle Container Registry](http://container-registry.oracle.com).

   c. Select Sign In and accept the license agreement.

   d. Use your terminal to log in to the container registry: `docker login container-registry.oracle.com`.

   e. Later in this sample, when you run WebLogic Image Tool commands, the tool will use the image as a base image for creating model images. Specifically, the tool will implicitly call `docker pull` for one of the previous licensed images as specified in the tool's command line using the `--fromImage` parameter.

   {{% notice warning %}}
   The example base images are General Availability (GA) images that are suitable for demonstration and development purposes _only_ where the environments are not available from the public Internet; they are **not acceptable for production use**. In production, you should always use CPU (patched) images from [OCR]({{< relref "/base-images/ocr-images.md" >}}) or create your images using the [WebLogic Image Tool]({{< relref "/base-images/custom-images#create-a-custom-base-image" >}}) (WIT) with the `--recommendedPatches` option. For more guidance, see [Apply the Latest Patches and Updates](https://www.oracle.com/pls/topic/lookup?ctx=en/middleware/standalone/weblogic-server/14.1.1.0&id=LOCKD-GUID-2DA84185-46BA-4D7A-80D2-9D577A4E8DE2) in _Securing a Production Environment for Oracle WebLogic Server_.

     {{% /notice %}}
