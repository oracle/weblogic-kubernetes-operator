---
title: "Liveness and readiness probes customization"
date: 2019-02-23T20:58:51-05:00
draft: false
weight: 6
description: "This document describes how to customize the liveness and readiness probes for WebLogic Server instance Pods."
---

This document describes how to customize the liveness and readiness probes for WebLogic Server instance Pods.

#### Contents

* [Liveness probe customization](#liveness-probe-customization)
* [Readiness probe customization](#readiness-probe-customization)

#### Liveness probe customization

The liveness probe is configured to check that the server is alive by querying the Node Manager process.  The liveness probe is by default configured to check liveness every 15 seconds, and to timeout after 5 seconds.  If a pod fails the liveness probe, Kubernetes will restart that container. 

The liveness probe interval and timeout can be customized using the `livenessProbe` attribute under the `serverPod` element of the domain resource. 

Following is an example configuration to change liveness probe interval and timeout value.
```
  serverPod:
    livenessProbe:
      periodSeconds: 30
      timeoutSeconds: 10
```

It is also possible to customize the liveness probe by specifying a custom script which will be invoked by the existing liveness probe script (`livenessProbe.sh`) after the existing script performs its own checks. This custom script can be specified either by using the `livenessProbeCustomScript` attribute in the domain resource or by setting `LIVENESS_PROBE_CUSTOM_SCRIPT` environment variable using `env` attribute under the `serverPod` element (see configuration examples below). If the custom script fails with non-zero exit status, the liveness probe will fail and Kubernetes will restart the container.


* The `spec.livenessProbeCustomScript` domain resource attribute affects all WebLogic Server instance Pods in the domain.
* The `LIVENESS_PROBE_CUSTOM_SCRIPT` environment variable takes precedence over the `spec.livenessProbeCustomScript` domain resource attribute when both are configured, and, like all domain resource environment variables, can be customized on a per domain, per cluster, or even a per server basis.
* Changes to either the domain resource attribute or the environment variable on a running domain take effect on already running WebLogic Server instance Pods when such Pods are restarted (rolled).

**Note**: The liveness probe custom script option is for advanced usage only and its value is not set by default. If the specified script is not found, then custom script is ignored and existing liveness script will perform its normal checks.

**Note**: Oracle recommends against having any long running calls (e.g. any network calls or executing wlst.sh) in the liveness probe custom script.

Use the following configuration to specify liveness probe custom script using `livenessProbeCustomScript` domain resource field.
```
spec:
  livenessProbeCustomScript: /u01/customLivenessProbe.sh
```

Use the following configuration to specify liveness probe custom script using `LIVENESS_PROBE_CUSTOM_SCRIPT` environment variable.
```
    serverPod:
      env:
      - name: LIVENESS_PROBE_CUSTOM_SCRIPT
        value: /u01/customLivenessProbe.sh
```

The following environment variables that are populated by the Operator are available for use in the liveness probe custom script which will be invoked by `livenessProbe.sh`.

`ORACLE_HOME` or `MW_HOME`: The Oracle Fusion Middleware software location as a file system path within the container.

`WL_HOME`: The Weblogic Server installation location as a file system path within the container. 

`DOMAIN_HOME`: The domain home location as a file system path within the container.

`JAVA_HOME`: The Java software installation location as a file system path within the container.

`DOMAIN_NAME`: The WebLogic Server domain name.

`DOMAIN_UID`: The domain unique identifier.

`SERVER_NAME`: The WebLogic Server instance name.

`LOG_HOME`: The WebLogic log location as a file system path within the container. This variable is available only if its value is set in the configuration.

**Note**: Additional environment variables that are populated by the Operator and that are not listed above are not supported for use in the liveness probe custom script; their existence or meaning may change in the future.

**Note**: The custom liveness probe script can call `source $DOMAIN_HOME/bin/setDomainEnv.sh` if it needs to setup its PATH or CLASSPATH to access WebLogic utilities in its domain.

**Note**: A custom liveness probe must not fail (exit non-zero) when the WebLogic Server instance itself is unavailable. This could be the case when the WebLogic Server instance is booting or about to boot.

#### Readiness probe customization

The readiness probe is by default configured to use the WebLogic Server ReadyApp framework. The ReadyApp framework allows fine customization of the readiness probe by application's participation in the framework. See [Using the ReadyApp Framework](https://docs.oracle.com/en/middleware/fusion-middleware/weblogic-server/12.2.1.4/depgd/managing.html#GUID-C98443B1-D368-4CA4-A7A4-97B86FFD3C28) for more details. The readiness probe is used to determine if the server is ready to accept user requests. The readiness is used to determine when a server should be included in a load balancer's endpoints, when a restarted server is fully started in the case of a rolling restart, and for various other purposes.

The readiness probe is by default configured to check readiness every 5 seconds, and to timeout after 5 seconds.  The readiness probe interval and timeout can be customized using the `readinessProbe` attribute under the `serverPod` element of the domain resource.

Following is an example configuration to change readiness probe interval and timeout value.
```
  serverPod:
    readinessProbe:
      periodSeconds: 10
      timeoutSeconds: 10
```
