---
title: "Monitor a SOA domain"
date: 2019-12-05T06:46:23-05:00
weight: 5
description: "Use the WebLogic Monitoring Exporter to monitor a SOA instance using Prometheus and Grafana."
---

Monitor a SOA domain using Prometheus and Grafana by exporting the metrics from the domain instance using the
WebLogic Monitoring Exporter. This sample shows you how to set up the WebLogic Monitoring Exporter to push the data
to Prometheus.

#### Prerequisites

This document assumes that the Prometheus Operator is deployed on the Kubernetes cluster. If it is not already deployed, follow the steps below for deploying the Prometheus Operator.

#### Clone the kube-prometheus project

```bash
$ git clone https://github.com/coreos/kube-prometheus.git
```

#### Create the kube-prometheus resources

Change to the `kube-prometheus` directory and execute the following commands to create the namespace and CRDs. Wait for their availability before creating the remaining resources.

```bash
$ cd kube-prometheus

$ kubectl create -f manifests/setup
$ until kubectl get servicemonitors --all-namespaces ; do date; sleep 1; echo ""; done
$ kubectl create -f manifests/
```

#### Label the nodes
Kube-Prometheus requires all the exporter nodes to be labelled with `kubernetes.io/os=linux`. If a node is not labelled, then you must label it using the following command:

```
$ kubectl label nodes --all kubernetes.io/os=linux
```
#### Provide external access
To provide external access for Grafana, Prometheus, and Alertmanager, execute the commands below:

```bash
$ kubectl patch svc grafana -n monitoring --type=json -p '[{"op": "replace", "path": "/spec/type", "value": "NodePort" },{"op": "replace", "path": "/spec/ports/0/nodePort", "value": 32100 }]'
$ kubectl patch svc prometheus-k8s -n monitoring --type=json -p '[{"op": "replace", "path": "/spec/type", "value": "NodePort" },{"op": "replace", "path": "/spec/ports/0/nodePort", "value": 32101 }]'
$ kubectl patch svc alertmanager-main -n monitoring --type=json -p '[{"op": "replace", "path": "/spec/type", "value": "NodePort" },{"op": "replace", "path": "/spec/ports/0/nodePort", "value": 32102 }]'
```

**NOTE**:

* `32100` is the external port for Grafana
* `32101` is the external port for Prometheus
* `32102` is the external port for Alertmanager

--------------

Use the following instructions to set up the WebLogic Monitoring Exporter to collect WebLogic Server metrics and monitor a SOA domain.

#### Download the WebLogic Monitoring Exporter

Download these WebLogic Monitoring Exporter files from the [Releases](https://github.com/oracle/weblogic-monitoring-exporter/releases) page:

* `wls-exporter.war`
* `getX.X.X.sh`

#### Create a configuration file for the WebLogic Monitoring Exporter

The configuration file will have the server port of the WebLogic Server instance
where the monitoring exporter application will be deployed.

See the following sample snippet of the configuration:

```
metricsNameSnakeCase: true
restPort: 7001
queries:
- key: name
  keyName: location
  prefix: wls_server_
  applicationRuntimes:
    key: name
    keyName: app
    componentRuntimes:
      prefix: wls_webapp_config_
      type: WebAppComponentRuntime
      key: name
      values: [deploymentState, contextRoot, sourceInfo, openSessionsHighCount, openSessionsCurrentCount, sessionsOpenedTotalCount, sessionCookieMaxAgeSecs, sessionInvalidationIntervalSecs, sessionTimeoutSecs, singleThreadedServletPoolSize, sessionIDLength, servletReloadCheckSecs, jSPPageCheckSecs]
      servlets:
        prefix: wls_servlet_
        key: servletName

- JVMRuntime:
    prefix: wls_jvm_
    key: name

- executeQueueRuntimes:
    prefix: wls_socketmuxer_
    key: name
    values: [pendingRequestCurrentCount]

- workManagerRuntimes:
    prefix: wls_workmanager_
    key: name
    values: [stuckThreadCount, pendingRequests, completedRequests]

- threadPoolRuntime:
    prefix: wls_threadpool_
    key: name
    values: [executeThreadTotalCount, queueLength, stuckThreadCount, hoggingThreadCount]

- JMSRuntime:
    key: name
    keyName: jmsruntime
    prefix: wls_jmsruntime_
    JMSServers:
      prefix: wls_jms_
      key: name
      keyName: jmsserver
      destinations:
        prefix: wls_jms_dest_
        key: name
        keyName: destination

- persistentStoreRuntimes:
    prefix: wls_persistentstore_
    key: name
- JDBCServiceRuntime:
    JDBCDataSourceRuntimeMBeans:
      prefix: wls_datasource_
      key: name
- JTARuntime:
    prefix: wls_jta_
    key: name
```

#### Generate the deployment package

You must generate two separate deployment packages with the `restPort` as `7001` or `8001` in the `config.yaml` file.
Two packages are required because the listening ports are different for the Administration Server and Managed Servers.

Use the `getX.X.X.sh` script to update the configuration file into the `wls-exporter` package.  
See the following sample usage:

```
$ ./get1.1.0.sh config-admin.yaml
 % Total % Received % Xferd Average Speed Time Time Time Current
 Dload Upload Total Spent Left Speed
100 607 0 607 0 0 915 0 --:--:-- --:--:-- --:--:-- 915
100 2016k 100 2016k 0 0 839k 0 0:00:02 0:00:02 --:--:-- 1696k
created /tmp/ci-H1SNbxKo1b
/tmp/ci-H1SNbxKo1b ~/weblogic-monitor/tmp
in temp dir
 adding: config.yml (deflated 63%)
~/weblogic-monitor/tmp
$ ls
config-admin.yaml get1.1.0.sh wls-exporter.war
```

Similarly, you must generate the deployment package for the Managed Servers with a different configuration file.

#### Deploy the WebLogic Monitoring Exporter

Follow these steps to deploy the package in the WebLogic Server instances.

1. Deploy the WebLogic Monitoring Exporter (`wls-exporter.war`) in the Administration Server and Managed Servers separately using the Oracle Enterprise Manager.

1. Select the servers to deploy.

1. Set the application name. The application name has to be different if it is deployed separately in the Administration Server and Managed Servers.

1. Set the context-root to `wls-exporter` for both the deployments.

1. Select **Install and start application**.

1. Then deploy the WebLogic Monitoring Exporter application (`wls-exporter.war`).

1. Activate the changes to start the application. If the application is started and the port is exposed,
then you can access the WebLogic Monitoring Exporter console using this URL, `http://<server:port>/wls-exporter`.

#### Prometheus Operator configuration

You must configure Prometheus to collect the metrics from the WebLogic Monitoring Exporter. The Prometheus Operator identifies the targets using service discovery.
To get the WebLogic Monitoring Exporter end point discovered as a target, you must create a service monitor pointing to the service.

See the following sample service monitor deployment YAML configuration file.  

`ServiceMonitor` for wls-exporter:
```
apiVersion: v1
kind: Secret
metadata:
  name: basic-auth
  namespace: monitoring
data:
  password: V2VsY29tZTE= # Welcome1 i.e.'WebLogic password'
  user: d2VibG9naWM= # weblogic  i.e. 'WebLogic username'
type: Opaque
---
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: wls-exporter-soainfra
  namespace: monitoring
  labels:
    k8s-app: wls-exporter
spec:
  namespaceSelector:
    matchNames:
    - soans
  selector:
    matchLabels:
      weblogic.domainName: soainfra
  endpoints:
  - basicAuth:
      password:
        name: basic-auth
        key: password
      username:
        name: basic-auth
        key: user
    port: default
    relabelings:
      - action: labelmap
        regex: __meta_kubernetes_service_label_(.+)
    interval: 10s
    honorLabels: true
    path: /wls-exporter/metrics
```

The exporting of metrics from `wls-exporter` requires `basicAuth` so a Kubernetes `Secret` is created with the user name and password that are base64 encoded. This `Secret` will be used in the `ServiceMonitor` deployment.

{{% notice note %}} Be careful in the generation of the base64 encoded strings for the user name and password. A new line character might get appended in the encoded string and cause an authentication failure. To avoid a new line string, use the following example:

```
$ echo -n "Welcome1" | base64
V2VsY29tZTE=
```
{{% /notice %}}

In the deployment YAML configuration for `wls-exporter` shown above, `weblogic.domainName: soainfra` is used as a label under `spec.selector.matchLabels`, so all the server services will be selected for the service monitor. Otherwise, you may have to create separate service monitors for each server if the server name is used as matching labels in `spec.selector.matchLabels`. The relabeling of the configuration is required, because Prometheus, by default, ignores the labels provided in the `wls-exporter`.  

By default, Prometheus will not store all the labels provided by the target. In the service monitor deployment YAML configuration, it is required to mention the relabeling configuration (`spec.endpoints.relabelings`), so that certain labels provided by `weblogic-monitoring-exporter` (required for the Grafana dashboard) are stored in Prometheus. Do not delete the following section from the configuration YAML file.
```
relabelings:
  - action: labelmap
    regex: __meta_kubernetes_service_label_(.+)
```

#### Add `RoleBinding` and `Role` for the WebLogic domain namespace

You need to add `RoleBinding` for the namespace under which the WebLogic Servers pods are running in the Kubernetes cluster. This `RoleBinding` is required for Prometheus to access the endpoints provided by the WebLogic Monitoring Exporter. Edit the `prometheus-roleBindingSpecificNamespaces.yaml` file in the Prometheus Operator deployment manifests and add the `RoleBinding` for the namespace (`soans`) similar to the following example:

```
- apiVersion: rbac.authorization.k8s.io/v1
 kind: RoleBinding
 metadata:
 name: prometheus-k8s
 namespace: soans
 roleRef:
 apiGroup: rbac.authorization.k8s.io
 kind: Role
 name: prometheus-k8s
 subjects:
 - kind: ServiceAccount
 name: prometheus-k8s
 namespace: monitoring
```
Similarly, you need to add the `Role` for the namespace under which the WebLogic Servers pods are running in the Kubernetes cluster. Edit `prometheus-roleSpecificNamespaces.yaml` in the Prometheus Operator deployment manifests and add the `Role` for the namespace (`soans`) similar to the following example:
```
- apiVersion: rbac.authorization.k8s.io/v1
 kind: Role
 metadata:
 name: prometheus-k8s
 namespace: soans
 rules:
 - apiGroups:
 - ""
 resources:
 - services
 - endpoints
 - pods
 verbs:
 - get
 - list
 - watch
```
Then apply `prometheus-roleBindingSpecificNamespaces.yaml` and `prometheus-roleSpecificNamespaces.yaml` for the `RoleBinding` and `Role` to take effect in the cluster.
```
$ kubectl apply -f prometheus-roleBindingSpecificNamespaces.yaml

$ kubectl apply -f prometheus-roleSpecificNamespaces.yaml
```
#### Deploy the service monitor

After creating the deployment YAML for the service monitor, deploy the service monitor using the following command:
```
$ kubectl create -f wls-exporter.yaml
```
#### Service discovery

After the deployment of the service monitor, `wls-exporter` should be discovered by Prometheus and able to export metrics.


#### Grafana dashboard

Deploy the Grafana dashboard provided in the WebLogic Monitoring Exporter to view the domain metrics.
