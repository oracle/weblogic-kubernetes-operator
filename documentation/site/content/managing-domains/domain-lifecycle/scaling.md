---
title: "Scaling"
date: 2019-02-23T17:04:45-05:00
draft: false
weight: 3
description: "The operator provides several ways to initiate scaling of WebLogic clusters."
---

This document describes approaches for scaling WebLogic clusters in a Kubernetes environment.

{{< table_of_contents >}}

### Overview

WebLogic Server supports two types of clustering configurations, configured and dynamic. Configured clusters are created by defining each individual Managed Server instance. In dynamic clusters, the Managed Server configurations are generated from a single, shared template. With dynamic clusters, when additional server capacity is needed, new server instances can be added to the cluster without having to configure them individually. Also, unlike configured clusters, scaling up of dynamic clusters is not restricted to the set of servers defined in the cluster but can be increased based on runtime demands. For more information on how to create, configure, and use dynamic clusters in WebLogic Server, see [Dynamic Clusters](https://docs.oracle.com/en/middleware/standalone/weblogic-server/14.1.1.0/clust/dynamic_clusters.html#GUID-DA7F7FAD-49AA-4F3D-8A05-0D9921B96971).

WebLogic Kubernetes Operator 4.0 introduces a new custom resource, `Cluster`.  A [Cluster resource]({{< relref "/managing-domains/domain-resource.md" >}}) references an individual WebLogic cluster and its configuration.  It also controls how many member servers are running, along with potentially any additional Kubernetes resources that are specific to that WebLogic cluster.  Because the Cluster resource enables the Kubernetes [Scale subresource](https://kubernetes.io/docs/tasks/extend-kubernetes/custom-resources/custom-resource-definitions/#scale-subresource), the `kubectl scale` command and the [Kubernetes Horizontal Pod Autoscaler (HPA)](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/) are fully supported for scaling individual WebLogic clusters.

The operator provides several ways to initiate scaling of WebLogic clusters, including:

* [`kubectl` CLI commands](#kubectl-cli-commands)
* [On-demand, updating the Cluster or Domain directly](#on-demand-updating-the-cluster-or-domain-directly) (using `kubectl`)
* [Using Domain lifecycle sample scripts](#using-domain-lifecycle-sample-scripts)
* [Kubernetes Horizontal Pod Autoscaler (HPA)](#kubernetes-horizontal-pod-autoscaler-hpa)
* [Using a WLDF policy rule and script action to call the scaling script](#using-a-wldf-policy-rule-and-script-action-to-call-the-scaling-script)
* [Using a Prometheus alert action to call the scaling script](#using-a-prometheus-alert-action-to-call-the-scaling-script)

### `kubectl` CLI commands
Use the Kubernetes command-line tool, `kubectl`, to manually scale WebLogic clusters with the following commands:

* `scale` - Set a new size for a resource.
* `patch` - Update a field or fields of a resource using a strategic merge patch.

#### `kubectl scale` command
You can scale an individual WebLogic cluster directly using the `kubectl scale` command. For example, the following command sets `.spec.replicas` of the Cluster resource `cluster-1` to `5`:
```shell
$ kubectl scale --replicas=5 clusters/cluster-1
  clusters "cluster-1" scaled

$ kubectl get clusters cluster-1 -o jsonpath='{.spec.replicas}'
  5
```

#### `kubectl patch` command
Also, you can scale an individual WebLogic cluster directly using the `kubectl patch` command with your patch object inline. For example, the following command sets `.spec.replicas` of the Cluster resource `cluster-1` to `3`:
```shell
$ kubectl patch cluster cluster-1 --type=merge -p '{"spec":{"replicas":3}}'
  cluster.weblogic.oracle/cluster-1 patched

$ kubectl get clusters cluster-1 -o jsonpath='{.spec.replicas}'
  3
```

### On-demand, updating the Cluster or Domain directly
You can use on-demand scaling (scaling a cluster manually) by directly updating the `.spec.replicas` field of the Cluster or Domain resources.
#### Updating the Cluster directly
To scale an individual WebLogic cluster directly, simply edit the `replicas` field of its associated Cluster resource; you can do this using `kubectl`. More specifically, you can modify the Cluster directly by using the `kubectl edit` command.  For example:
```shell
$ kubectl edit cluster cluster-1 -n [namespace]
```
In this command, you are editing a Cluster named `cluster-1`. The `kubectl edit` command opens the Cluster definition in an editor and lets you modify the `replicas` value directly. Once committed, the operator will be notified of the change and will immediately attempt to scale the corresponding cluster by reconciling the number of running pods/Managed Server instances with the `replicas` value specification.
```yaml
spec:
  ...
  clusterName: cluster-1
  replicas: 1
  ...
```
#### Updating the Domain directly
To scale every WebLogic cluster in a domain that does not have a corresponding Cluster resource, or that has a Cluster resource which does not specify a `cluster.spec.replicas` field, modify the `domain.spec.replicas` field using the `kubectl edit` command:

```shell
$ kubectl edit domain domain1 -n [namespace]
```
In this command, you are editing a Domain named `domain1`. The `kubectl edit` command opens the Domain definition in an editor and lets you modify the `replicas` value directly. Once committed, the operator will be notified of the change and will immediately attempt to scale the corresponding cluster or clusters by reconciling the number of running pods/Managed Server instances with the `replicas` value specification.
```yaml
spec:
  ...
  replicas: 1
  ...
```

### Using Domain lifecycle sample scripts
Beginning in version 3.1.0, the operator provides sample lifecycle scripts. See the helper scripts in the [Domain lifecycle sample scripts]({{< relref "/managing-domains/domain-lifecycle/scripts.md" >}}) section, which you can use for scaling WebLogic clusters.

### Supported autoscaling controllers
While continuing to support automatic scaling of WebLogic clusters with the WebLogic Diagnostic Framework (WLDF) and Prometheus, Operator 4.0 now supports the [Kubernetes Horizontal Pod Autoscaler (HPA)](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/).
#### Kubernetes Horizontal Pod Autoscaler (HPA)
Automatic scaling of an individual WebLogic cluster, by the Kubernetes Horizontal Pod Autoscaler, is now supported since the Cluster custom resource has enabled the Kubernetes [/scale subresource](https://kubernetes.io/docs/tasks/extend-kubernetes/custom-resources/custom-resource-definitions/#scale-subresource/). Autoscaling based on resource metrics requires the installation of the [Kubernetes Metrics Server](https://kubernetes-sigs.github.io/metrics-server/) or another implementation of the resource metrics API. If using Prometheus for monitoring WebLogic Server metrics, then you can use the [Prometheus Adapter](https://github.com/kubernetes-sigs/prometheus-adapter) in place of the [Kubernetes Metrics Server](https://kubernetes-sigs.github.io/metrics-server/).

The following step-by-step example illustrates how to configure and run an HPA to scale a WebLogic cluster, `cluster-1`, based on the `cpu utilization` resource metric.

1. To illustrate scaling of a WebLogic cluster based on CPU utilization, deploy the Kubernetes Metrics Server to the Kubernetes cluster.
```shell
$ kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
```
2. Confirm that the Kubernetes Metric Server is running by listing the pods in the `kube-system` namespace.
```shell
$ kubectl get po -n kube-system
```
{{% notice note %}}
If the Kubernetes Metric Server does not reach the READY state (for example, `READY 0/1` ) due to `Readiness probe failed: HTTP probe failed with statuscode: 500`, then you may need to install a valid cluster certificate.  For testing purposes, you can resolve this issue by downloading the `components.yaml` file and adding the argument `--kubelet-insecure-tls` to the Metrics Server container.
{{% /notice %}}

3. Assuming a WebLogic domain running in the default namespace, use the following command to create an HPA resource targeted at the Cluster resource (`sample-domain1-cluster-1`) that will autoscale WebLogic Server instances from a minimum of `2` cluster members up to `5` cluster members, and the scale up or down will occur when the average CPU is consistently over 50%.
```shell
$ kubectl autoscale cluster sample-domain1-cluster-1 --cpu-percent=50 --min=2 --max=5
  horizontalpodautoscaler.autoscaling/sample-domain1-cluster-1 autoscaled
```
{{% notice note %}}
Beginning with Operator 4.0, the `allowReplicasBelowMinDynClusterSize` field has been removed from the Domain resource schema. When scaling down, the minimum allowed replica count must now be configured on the selected autoscaling controller.
{{% /notice %}}

4. Verify the status of the autoscaler and its behavior by inspecting the HPA resource.
```shell
$ kubectl get hpa
  NAME                       REFERENCE                          TARGETS   MINPODS   MAXPODS   REPLICAS   AGE
  sample-domain1-cluster-1   Cluster/sample-domain1-cluster-1   8%/50%    2         5         2          3m27s
```

5. To see the HPA scale up the WebLogic cluster `sample-domain1-cluster-1`, generate a loaded CPU by getting a shell to a running container in one of the cluster member pods and run the following command.
```shell
$ kubectl exec --stdin --tty sample-domain1-managed-server1 -- /bin/bash
  [oracle@sample-domain1-managed-server1 oracle]$ dd if=/dev/zero of=/dev/null
```
6. By listing the Managed Server pods, you will see the autoscaler increase the replicas on the Cluster resource and the operator respond by starting additional cluster member servers.  Conversely, after stopping the load and when the CPU utilization average is consistently under 50%, the autoscaler will scale down the WebLogic cluster by decreasing the replicas value on the Cluster resource.

For more in-depth information on the Kubernetes Horizontal Pod Autoscaler, see [Horizontal Pod Autoscaling](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/).

#### Using a WLDF policy rule and script action to call the scaling script
The WebLogic Diagnostics Framework (WLDF) is a suite of services and APIs that collect and surface metrics that provide visibility into server and application performance.
To support automatic scaling of WebLogic clusters in Kubernetes, WLDF provides the Policies and Actions component, which lets you write policy expressions for automatically executing scaling
operations on a cluster. These policies monitor one or more types of WebLogic Server metrics, such as memory, idle threads, and CPU load.  When the configured threshold
in a policy is met, the policy is triggered, and the corresponding scaling action is executed.  The WebLogic Kubernetes Operator project provides a shell script, [`scalingAction.sh`](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/kubernetes/samples/scripts/scaling/scalingAction.sh),
for use as a Script Action, which initiates a scaling of the selected cluster.

##### Configure automatic scaling of WebLogic clusters in Kubernetes with WLDF
The following steps are provided as a guideline on how to configure a WLDF Policy and Script Action component for issuing scaling requests:

1. Copy the [`scalingAction.sh`](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/kubernetes/samples/scripts/scaling/scalingAction.sh) script to `$DOMAIN_HOME/bin/scripts` so that it's accessible within the Administration Server pod. For more information, see [Configuring Script Actions](https://docs.oracle.com/en/middleware/standalone/weblogic-server/14.1.1.0/wldfc/config_notifications.html#GUID-5CC52534-13CD-40D9-915D-3380C86580F1) in _Configuring and Using the Diagnostics Framework for Oracle WebLogic Server_.

1. Configure a WLDF policy and action as part of a diagnostic module targeted to the Administration Server. For information about configuring the WLDF Policies and Actions component,
see [Configuring Policies and Actions](https://docs.oracle.com/en/middleware/standalone/weblogic-server/14.1.1.0/wldfc/config_watch_notif.html#GUID-D3FA8301-AAF2-41CE-A6A5-AB4005849913) in _Configuring and Using the Diagnostics Framework for Oracle WebLogic Server_.

     a. Configure a WLDF policy with a rule expression for monitoring WebLogic Server metrics, such as memory, idle threads, and CPU load for example.

     b. Configure a WLDF script action and associate the [`scalingAction.sh`](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/kubernetes/samples/scripts/scaling/scalingAction.sh) script.

The scalingAction.sh script accepts a number of customizable parameters:

* `action` - scaleUp or scaleDown (Required)

* `domain_uid` - WebLogic domain unique identifier (Required)

* `cluster_name` - WebLogic cluster name (Required)

* `kubernetes_master` - Kubernetes master URL, default=https://kubernetes  

{{% notice note %}}
Set this to `https://${KUBERNETES_SERVICE_HOST}:${KUBERNETES_SERVICE_PORT}` when invoking `scalingAction.sh` from the Administration Server pod.
{{% /notice %}}

* `access_token` - Service Account Bearer token for authentication and authorization for access to REST Resources

* `wls_domain_namespace` - Kubernetes Namespace in which the WebLogic domain is defined, default=`default`

* `scaling_size` – Incremental number of Managed Server instances by which to scale up or down, default=`1`

You can use any of the following tools to configure policies for diagnostic system modules:

* WebLogic Server Administration Console
* WLST
* REST
* JMX application    

A more in-depth description and example on using WLDF's Policies and Actions component for initiating scaling requests can be found in the blogs:

* [Automatic Scaling of WebLogic Clusters on Kubernetes](https://blogs.oracle.com/weblogicserver/automatic-scaling-of-weblogic-clusters-on-kubernetes-v2)
* [WebLogic Dynamic Clusters on Kubernetes](https://blogs.oracle.com/weblogicserver/weblogic-dynamic-clusters-on-kubernetes)

##### Create ClusterRoleBindings to allow a namespace user to query WLS Kubernetes cluster information
The script `scalingAction.sh`, specified in the WLDF script action, needs the appropriate RBAC permissions granted for the service account user (in the namespace in which the WebLogic domain is deployed) to query the Kubernetes API server for both configuration and runtime information of the Domain.
The following is an example YAML file for creating the appropriate Kubernetes ClusterRole bindings:

{{% notice note %}}
In the following example ClusterRoleBinding definition, the WebLogic domain is deployed to a namespace `weblogic-domain`.  Replace the namespace value with the name of the namespace in which the WebLogic domain is deployed in your Kubernetes environment.
{{% /notice %}}

```yaml
kind: ClusterRole
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: weblogic-domain-cluster-role
rules:
- apiGroups: ["weblogic.oracle"]
  resources: ["domains", "clusters", "clusters/scale"]
  verbs: ["get", "list", "patch", "update"]
---
#
# creating role-bindings for cluster role
#
kind: ClusterRoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: domain-cluster-rolebinding
subjects:
- kind: ServiceAccount
  name: default
  namespace: weblogic-domain
  apiGroup: ""
roleRef:
  kind: ClusterRole
  name: weblogic-domain-cluster-role
  apiGroup: "rbac.authorization.k8s.io"
---
#
# creating role-bindings
#
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: weblogic-domain-operator-rolebinding
  namespace: weblogic-operator
subjects:
- kind: ServiceAccount
  name: default
  namespace: weblogic-domain
  apiGroup: ""
roleRef:
  kind: ClusterRole
  name: cluster-admin
  apiGroup: "rbac.authorization.k8s.io"
---
```

#### Horizontal Pod Autoscaler (HPA) using WebLogic Exporter Metrics
Please read this blog post to learn how to scale a WebLogic cluster, based on WebLogic metrics provided by the Monitoring Exporter, using the Kubernetes Horizontal Pod Autoscaler (HPA). We will use the Prometheus Adapter to gather the names of the available metrics from Prometheus at regular intervals. A custom configuration of the adapter will expose only metrics that follow specific formats. [Horizontal Pod Autoscaler (HPA) using WebLogic Exporter Metrics](https://blogs.oracle.com/weblogicserver/post/horizontal-pod-autoscaler-hpausing-weblogic-exporter-metrics). See this corresponding video for a demonstration of the blog post in action. [WebLogic Kubernetes Operator support for Kubernetes Horizontal Pod Autoscaling](https://www.youtube.com/watch?v=aKBG6yJ3sMg).

#### Using a Prometheus alert action to call the scaling script
In addition to using the WebLogic Diagnostic Framework for automatic scaling of a dynamic cluster,
you can use a third-party monitoring application like Prometheus.  Please read the following blog for
details about [Using Prometheus to Automatically Scale WebLogic Clusters on Kubernetes](https://blogs.oracle.com/weblogicserver/using-prometheus-to-automatically-scale-weblogic-clusters-on-kubernetes-v5).



### Helpful tips
#### Debugging scalingAction.sh
The [`scalingAction.sh`](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/kubernetes/samples/scripts/scaling/scalingAction.sh) script was designed to be executed within a container of the
Administration Server Pod because the associated diagnostic module is targeted to the Administration Server.

The easiest way to verify and debug the `scalingAction.sh` script is to open a shell on the running Administration Server pod and execute the script on the command line.

The following example illustrates how to open a bash shell on a running Administration Server pod named `domain1-admin-server` and execute the `scriptAction.sh` script.  It assumes that:

* The domain home is in `/u01/oracle/user-projects/domains/domain1` (that is, the domain home is inside an image).
* The Dockerfile copied [`scalingAction.sh`](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/kubernetes/samples/scripts/scaling/scalingAction.sh) to `/u01/oracle/user-projects/domains/domain1/bin/scripts/scalingAction.sh`.

```shell
$ kubectl exec -it domain1-admin-server /bin/bash
```
```shell
$ cd /u01/oracle/user-projects/domains/domain1/bin/scripts && \
  ./scalingAction.sh
```

A log, `scalingAction.log`, will be generated in the same directory in which the script was executed and can be examined for errors.
