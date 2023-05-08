---
title: "Image creation prerequisites"
date: 2019-02-23T17:32:31-05:00
weight: 1
description: "Prepare for WebLogic image creation with these steps."
---

Complete the following steps before [WDT image creation]({{< relref "/samples/domains/image-creation/_index.md" >}}).

1. Choose the type of domain you're going to use throughout the sample, `WLS` or `JRF`.

1. The `JAVA_HOME` environment variable must be set and must reference a valid JDK 8 or 11 installation.

1. Get the operator source and put it in `/tmp/weblogic-kubernetes-operator`.

   For example:

   ```shell
   $ cd /tmp
   ```
   ```shell
   $ git clone --branch v{{< latestVersion >}} https://github.com/oracle/weblogic-kubernetes-operator.git
   ```

   **Note**: We will refer to the top directory of the operator source tree as `/tmp/weblogic-kubernetes-operator`; however, you can use a different location.

   For additional information about obtaining the operator source, see the [Developer Guide Requirements](https://oracle.github.io/weblogic-kubernetes-operator/developerguide/requirements/).

1. Copy the sample to a new directory; for example, use directory `/tmp/sample`.


   ```shell
   $ mkdir /tmp/sample
   ```
   ```shell
   $ cp -r /tmp/weblogic-kubernetes-operator/kubernetes/samples/scripts/create-weblogic-domain/wdt-artifacts/* /tmp/sample
   ```

   **Note**: We will refer to this working copy of the sample as `/tmp/sample`; however, you can use a different location.
   {{< rawhtml >}}
   <a name="resume"></a>
   {{< /rawhtml >}}
1. Download the latest [WebLogic Deploy Tooling](https://github.com/oracle/weblogic-deploy-tooling/releases) (WDT) and [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool/releases) (WIT) installer ZIP files to your `/tmp/sample/wdt-artifacts` directory. Both WDT and WIT are required to create your Model in Image `auxiliary images` or Domain on PV `domain creation images`.

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
   [WIT Cache documentation](https://oracle.github.io/weblogic-image-tool/userguide/tools/cache/).

   These steps will install WIT to the `/tmp/sample/wdt-artifacts/imagetool` directory,
   plus put a `wdt_latest` entry in the tool's cache which points to the WDT ZIP file installer.
   You will use WIT and its cached reference to the WDT installer later in the sample for creating model images.

1. To set up the WebLogic Deploy Tooling that we will use later for the archive helper, run the following command:

   ```shell
   $ unzip /tmp/sample/wdt-artifacts/weblogic-deploy.zip
   ```
