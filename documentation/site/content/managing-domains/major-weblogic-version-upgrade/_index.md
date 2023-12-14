+++
title = "Upgrade managed domains"
date = 2023-10-05T16:43:45-05:00
weight = 6
pre = "<b> </b>"
description = "Upgrade managed domains to a higher, major version."
+++


This document provides guidelines for upgrading WLS and FMW/JRF infrastructure domains to a higher, major version.

In general, the process for upgrading WLS and FMW/JRF infrastructure domains in Kubernetes is similar to upgrading domains on premises. For a thorough understanding, we suggest that you read the [Fusion Middleware Upgrade Guide](https://docs.oracle.com/en/middleware/fusion-middleware/12.2.1.4/asmas/planning-upgrade-oracle-fusion-middleware-12c.html#GUID-D9CEE7E2-5062-4086-81C7-79A33A200080).

**Before the upgrade**, you must do the following:

- If your [domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}) is Domain on Persistent Volume (DoPV), then back up the domain home.
- If your domain type is `JRF`:
   - Back up the JRF database.
   - Back up the OPSS wallet file, this allows you to reuse the same JRF database schemas if you need to recreate the domain.

     The operator provides a helper script, the [OPSS wallet utility](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/scripts/domain-lifecycle/opss-wallet.sh), for extracting the wallet file and storing it in a Kubernetes `walletFileSecret`. In addition, you should save the wallet file in a safely backed-up location, outside of Kubernetes. For example, the following command saves the OPSS wallet for the `sample-domain1` domain in the `sample-ns` namespace to a file named `ewallet.p12` in the `/tmp` directory and also stores it in the wallet secret named `sample-domain1-opss-walletfile-secret`.

     ```
     $ opss-wallet.sh -n sample-ns -d sample-domain1 -s -r -wf /tmp/ewallet.p12 -ws sample-domain1-opss-walletfile-secret
     ```

- Make sure nothing else is accessing the database.
- **Do not delete** the domain resource.

{{% notice note %}} According to My Oracle Support [Doc ID 2752458.1](https://support.oracle.com/epmos/faces/DocumentDisplay?id=2752458.1), if you are using an FMW/JRF domain and upgrading from 12.2.1.3 to 12.2.1.4, then before upgrading, you do _not_ need to run the Upgrade Assistant or Reconfiguration Wizard, but we recommend moving the domain to a persistent volume.  See [Move MII/JRF domains to PV]({{< relref "/managing-domains/model-in-image/move-to-pv.md" >}}).
{{% /notice %}}

To upgrade WLS and FMW/JRF infrastructure domains, use the following procedure.

1. Shut down the domain by patching the domain and/or cluster spec `serverStartPolicy` to `Never`. For example:
   ```
   $ kubectl -n sample-domain1-ns patch domain sample-domain1 --type=json -p='[ {"op": "replace", "path": "/spec/serverStartPolicy", "value": "Never"}]'
   ```

2. After the shutdown completes, upgrade the base image in the domain resource YAML file and redeploy the domain.

   You can patch the domain resource YAML file, update the base image and change `serverStartPolicy` to `IfNeeded` again, as follows:

   ```
   $ kubectl -n sample-domain1-ns patch domain sample-domain1 --type=json -p='[ {"op": "replace", "path": "/spec/serverStartPolicy", "value": "IfNeeded"}, {"op": "replace", "path":"/spec/image", "value":"<New WebLogic or Fusion Middleware base image>"]'
   ```
