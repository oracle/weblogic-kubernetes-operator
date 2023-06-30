---
title: "Liveness and readiness probes customization"
date: 2019-02-23T20:58:51-05:00
draft: false
weight: 6
description: "This document describes how to customize the liveness and readiness probes for WebLogic Server instance Pods."
---

This document describes how to customize the liveness and readiness probes for WebLogic Server instance Pods.

{{< table_of_contents >}}

### Liveness probe customization

The liveness probe is configured to check that a server is alive by querying the Node Manager process.  By default, the liveness probe is configured to check liveness every 45 seconds, to timeout after 5 seconds, and to perform the first check after 30 seconds.  The default success and failure threshold values are 1.  If a pod fails the liveness probe, Kubernetes will restart that container.

You can customize the liveness probe initial delay, interval, timeout, and failure threshold using the `livenessProbe` attribute under the `serverPod` element of the domain or cluster resource.

Following is an example configuration to change the liveness probe interval, timeout, and failure threshold value.
```yaml
  serverPod:
    livenessProbe:
      periodSeconds: 30
      timeoutSeconds: 10
      failureThreshold: 3
```

**NOTE**: The liveness probe success threshold value must always be 1. See [Configure Probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/#configure-probes) in the Kubernetes documentation for more details.

After the liveness probe script (livenessProbe.sh) performs its normal checks, you can customize the liveness probe by specifying a custom script, which will be invoked by livenessProbe.sh. You can specify the custom script either by using the `livenessProbeCustomScript` attribute in the domain resource, or by setting the `LIVENESS_PROBE_CUSTOM_SCRIPT` environment variable using the `env` attribute under the `serverPod` element (see the following configuration examples). If the custom script fails with a non-zero exit status, the liveness probe will fail and Kubernetes will restart the container.


* The `spec.livenessProbeCustomScript` domain resource attribute affects all WebLogic Server instance Pods in the domain.
* The `LIVENESS_PROBE_CUSTOM_SCRIPT` environment variable takes precedence over the `spec.livenessProbeCustomScript` domain resource attribute when both are configured, and, like all domain resource environment variables, can be customized on a per domain, per cluster, or even a per server basis.
* Changes to either the domain resource attribute or the environment variable on a running domain take effect on running WebLogic Server instance Pods when such Pods are restarted (rolled).

**NOTE**: The liveness probe custom script option is for advanced usage only and its value is not set by default. If the specified script is not found, then the custom script is ignored and the existing liveness script will perform its normal checks.

**NOTE**: Oracle recommends against having any long running calls (for example, any network calls or executing wlst.sh) in the liveness probe custom script.

Use the following configuration to specify a liveness probe custom script using the `livenessProbeCustomScript` domain resource field.
```yaml
spec:
  livenessProbeCustomScript: /u01/customLivenessProbe.sh
```

Use the following configuration to specify the liveness probe custom script using the `LIVENESS_PROBE_CUSTOM_SCRIPT` environment variable.
```yaml
    serverPod:
      env:
      - name: LIVENESS_PROBE_CUSTOM_SCRIPT
        value: /u01/customLivenessProbe.sh
```

The following operator-populated environment variables are available for use in the liveness probe custom script, which will be invoked by `livenessProbe.sh`.

`ORACLE_HOME` or `MW_HOME`: The Oracle Fusion Middleware software location as a file system path within the container.

`WL_HOME`: The Weblogic Server installation location as a file system path within the container.

`DOMAIN_HOME`: The domain home location as a file system path within the container.

`JAVA_HOME`: The Java software installation location as a file system path within the container.

`DOMAIN_NAME`: The WebLogic Server domain name.

`DOMAIN_UID`: The domain unique identifier.

`SERVER_NAME`: The WebLogic Server instance name.

`LOG_HOME`: The WebLogic log location as a file system path within the container. This variable is available only if its value is set in the configuration.

**NOTES**:

* Additional operator-populated environment variables that are not listed, are not supported for use in the liveness probe custom script.

* The custom liveness probe script can call `source $DOMAIN_HOME/bin/setDomainEnv.sh` if it needs to set up its PATH or CLASSPATH to access WebLogic utilities in its domain.

* A custom liveness probe must not fail (exit non-zero) when the WebLogic Server instance itself is unavailable. This could be the case when the WebLogic Server instance is booting or about to boot.

### Automatic restart of failed server instances by Node Manager

WebLogic Server provides a self-health monitoring feature to improve the reliability and availability of server instances in a domain. If an individual subsystem determines that it can no longer operate consistently and reliably, it registers its health state as `FAILED` with the host server.  Each WebLogic Server instance, in turn, checks the health state of its registered subsystems to determine its overall viability. If one or more of its critical subsystems have reached the `FAILED` state, the server instance marks its health state as `FAILED` to indicate that it cannot reliably host an application.  

Using Node Manager, server self-health monitoring enables the automatic restart of the failed server instances. The operator configures the Node Manager to restart the failed server a maximum of two times within a one-hour interval. It does this by setting the value of the `RestartMax` property (in the [server startup properties](https://docs.oracle.com/en/middleware/fusion-middleware/weblogic-server/12.2.1.4/nodem/java_nodemgr.html#GUID-26475256-2830-434B-B31F-A2D06F48B244) file) to `2` and the value of the `RestartInterval` property to `3600`. You can change the number of times the Node Manager will attempt to restart the server in a given interval by setting the `RESTART_MAX` and `RESTART_INTERVAL` environment variables in the domain resource using the `env` attribute under the `serverPod` element.

Use the following configuration to specify the number of times the Node Manager can attempt to restart the server within a given interval using the `RESTART_MAX` and `RESTART_INTERVAL` environment variables.
```yaml
    serverPod:
      env:
      - name: RESTART_MAX
        value: "4"
      - name: RESTART_INTERVAL
        value: "3600"
```

If the Node Manager can't restart the failed server and marks the server state as `FAILED_NOT_RESTARTABLE`, then the liveness probe will fail and the WebLogic Server container will be restarted. You can set the `RESTART_MAX` environment variable value to `0` to prevent the Node Manager from restarting the failed server and allow the liveness probe to fail immediately.

See [Server Startup Properties](https://docs.oracle.com/en/middleware/fusion-middleware/weblogic-server/12.2.1.4/nodem/java_nodemgr.html#GUID-26475256-2830-434B-B31F-A2D06F48B244) for more details.

### Readiness probe customization

Here are the options for customizing the readiness probe and its tuning:

- By default, the readiness probe is configured to use the WebLogic Server ReadyApp framework. The ReadyApp framework allows fine customization of the readiness probe by the application's participation in the framework. For more details, see [Using the ReadyApp Framework](https://docs.oracle.com/en/middleware/fusion-middleware/weblogic-server/12.2.1.4/depgd/managing.html#GUID-C98443B1-D368-4CA4-A7A4-97B86FFD3C28). The readiness probe is used to determine if the server is ready to accept user requests. The readiness is used to determine when a server should be included in a load balancer's endpoints, in the case of a rolling restart, when a restarted server is fully started, and for various other purposes.

- By default, the readiness probe is configured to check readiness every 5 seconds, to timeout after 5 seconds, and to perform the first check after 30 seconds. The default success and failure thresholds values are 1. You can customize the readiness probe initial delay, interval, timeout, success and failure thresholds using the `readinessProbe` attribute under the `serverPod` element of the domain resource.

  Following is an example configuration to change readiness probe interval, timeout and failure threshold value.
  ```yaml
    serverPod:
      readinessProbe:
        periodSeconds: 10
        timeoutSeconds: 10
        failureThreshold: 3
  ```

- You can use domain resource configuration to customize
  the amount of time the operator will wait for a WebLogic Server pod to become ready
  before it forces the pod to restart. The default is 30 minutes.
  See the `maxReadyWaitTimeSeconds` attribute on `domain.spec.serverPod`
  (which applies to all pods in the domain),
  `domain.spec.adminServer.serverPod`,
  `domain.spec.managedServers[*].serverPod`,
  or `cluster.spec.serverPod`.
