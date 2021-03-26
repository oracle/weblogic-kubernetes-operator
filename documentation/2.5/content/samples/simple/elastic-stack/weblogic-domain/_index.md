---
title: "WebLogic domain"
date: 2019-10-01T14:32:31-05:00
weight: 2
description: "Sample for using Fluentd for WebLogic domain and operator's logs."
---


#### Overview
This document describes to how to configure a WebLogic domain to use Fluentd to send log information to Elasticsearch.

Here's the general mechanism for how this works:

* `fluentd` runs as a separate container in the Administration Server and Managed Server pods
* The log files reside on a volume that is shared between the `weblogic-server` and `fluentd` containers
* `fluentd` tails the domain logs files and exports them to Elasticsearch
* A `ConfigMap` contains the filter and format rules for exporting log records

##### Sample code

The samples in this document assume an existing domain is being edited.  However, all changes to the domain YAML can be performed before the domain is created.

For sample purposes, this document will assume a domain with the following attributes is being configured:

* Domain name is `bobs-bookstore`
* Kubernetes namespace is `bob`
* Kubernetes secret is `bobs-bookstore-weblogic-credentials`

The sample Elasticsearch configuration is:
```text
    elasticsearchhost: elasticsearch.bobs-books.sample.com
    elasticsearchport: 443
    elasticsearchuser: bob
    elasticsearchpassword: changeme
```

#### Configure log files to use a volume
The domain log files must be written to a volume that can be shared between the `weblogic-server` and `fluentd` containers.  The following elements are required to accomplish this:

* `logHome` must be a path that can be shared between containers
* `logHomeEnabled` must be set to `true` so that the logs will be written outside the pod and persist across pod restarts
* A `volume` must be defined that the log files will reside on.  In the example, `emptyDir` is a volume that gets created empty when a Pod is created.  It will persist across pod restarts but deleting the pod would delete the `emptyDir` content.
* The `volumeMounts` mounts the named volume created with `emptyDir` and establishes the base path for accessing the volume.

**NOTE**: For brevity, only the paths to the relevant configuration being added is shown.  A complete example of a domain definition is at the end of this document.

Example: `kubectl edit domain bobs-bookstore -n bob` and make the following edits:

```yaml
spec:
  logHome: /scratch/logs/bobs-bookstore
  logHomeEnabled: true
  serverPod:
    volumes:
    - emptyDir: {}
      name: weblogic-domain-storage-volume
    volumeMounts:
    - mountPath: /scratch
      name: weblogic-domain-storage-volume
```

#### Add Elasticsearch secrets to WebLogic domain credentials
The `fluentd` container will be configured to look for Elasticsearch parameters in the domain credentials.  Edit the domain credentials and add the parameters shown in the example below.

Example: `kubectl edit secret bobs-bookstore-weblogic-credentials -n bob` and add the base64 encoded values of each Elasticsearch parameter:
```text
elasticsearchhost: ZWxhc3RpY3NlYXJjaC5ib2JzLWJvb2tzLnNhbXBsZS5jb20=
elasticsearchport: NDQz
elasticsearchuser: Ym9i
elasticsearchpassword: d2VsY29tZTE=
```

#### Create Fluentd configuration
Create a `ConfigMap` named `fluentd-config` in the namespace of the domain.  The `ConfigMap` contains the parsing rules and Elasticsearch configuration.

Here's an explanation of some elements defined in the `ConfigMap`:

* The `@type tail` indicates that `tail` will be used to obtain updates to the log file
* The `path` of the log file is obtained from the `LOG_PATH` environment variable that is defined in the `fluentd` container
* The `tag` value of log records is obtained from the `DOMAIN_UID` environment variable that is defined in the `fluentd` container
* The `<parse>` section defines how to interpret and tag each element of a log record
* The `<match **>` section contains the configuration information for connecting to Elasticsearch and defines the index name of each record to be the `domainUID`

The following is an example of how to create the `ConfigMap`:
```bash
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  labels:
    weblogic.domainUID: bobs-bookstore
    weblogic.resourceVersion: domain-v2
  name: fluentd-config
  namespace: bob
data:
  fluentd.conf: |
    <match fluent.**>
      @type null
    </match>
    <source>
      @type tail
      path "#{ENV['LOG_PATH']}"
      pos_file /tmp/server.log.pos
      read_from_head true
      tag "#{ENV['DOMAIN_UID']}"
      # multiline_flush_interval 20s
      <parse>
        @type multiline
        format_firstline /^####/
        format1 /^####<(?<timestamp>(.*?))>/
        format2 / <(?<level>(.*?))>/
        format3 / <(?<subSystem>(.*?))>/
        format4 / <(?<serverName>(.*?))>/
        format5 / <(?<serverName2>(.*?))>/
        format6 / <(?<threadName>(.*?))>/
        format7 / <(?<info1>(.*?))>/
        format8 / <(?<info2>(.*?))>/
        format9 / <(?<info3>(.*?))>/
        format10 / <(?<sequenceNumber>(.*?))>/
        format11 / <(?<severity>(.*?))>/
        format12 / <(?<messageID>(.*?))>/
        format13 / <(?<message>(.*?))>/
      </parse>
    </source>
    <match **>
      @type elasticsearch
      host "#{ENV['ELASTICSEARCH_HOST']}"
      port "#{ENV['ELASTICSEARCH_PORT']}"
      user "#{ENV['ELASTICSEARCH_USER']}"
      password "#{ENV['ELASTICSEARCH_PASSWORD']}"
      index_name "#{ENV['DOMAIN_UID']}"
      scheme https
      ssl_version TLSv1_2
    </match>
EOF
```

#### Mount the ConfigMap as a volume in the `weblogic-server` container
Edit the domain definition and configure a volume for the `ConfigMap` containing the `fluentd` configuration.

**NOTE**: For brevity, only the paths to the relevant configuration being added is shown.  A complete example of a domain definition is at the end of this document.

Example: `kubectl edit domain bobs-bookstore -n bob` and add the following portions to the domain definition.  
```yaml
spec:
  serverPod:
    volumes:
    - configMap:
        defaultMode: 420
        name: fluentd-config
      name: fluentd-config-volume
```

#### Add `fluentd` container
Add a container to the domain that will run `fluentd` in the Administration Server and Managed Server pods.

Notice the container definition:

* Defines a `LOG_PATH` environment variable that points to the log location of `bobbys-front-end`
* Defines `ELASTICSEARCH_HOST`, `ELASTICSEARCH_PORT`, `ELASTICSEARCH_USER`, and `ELASTICSEARCH_PASSWORD` environment variables that are all retrieving their values from the secret `bobs-bookstore-weblogic-credentials`
* Has volume mounts for the `fluentd-config` `ConfigMap` and the volume containing the domain logs

**NOTE**: For brevity, only the paths to the relevant configuration being added is shown.  A complete example of a domain definition is at the end of this document.

Example: `kubectl edit domain bobs-bookstore -n bob` and add the following container definition.
```yaml
spec:
  serverPod:
    containers:
    - args:
      - -c
      - /etc/fluent.conf
      env:
      - name: DOMAIN_UID
        valueFrom:
          fieldRef:
            fieldPath: metadata.labels['weblogic.domainUID']
      - name: SERVER_NAME
        valueFrom:
          fieldRef:
            fieldPath: metadata.labels['weblogic.serverName']
      - name: LOG_PATH
        value: /scratch/logs/bobs-bookstore/$(SERVER_NAME).log
      - name: FLUENTD_CONF
        value: fluentd.conf
      - name: FLUENT_ELASTICSEARCH_SED_DISABLE
        value: "true"
      - name: ELASTICSEARCH_HOST
        valueFrom:
          secretKeyRef:
            key: elasticsearchhost
            name: bobs-bookstore-weblogic-credentials
      - name: ELASTICSEARCH_PORT
        valueFrom:
          secretKeyRef:
            key: elasticsearchport
            name: bobs-bookstore-weblogic-credentials
      - name: ELASTICSEARCH_USER
        valueFrom:
          secretKeyRef:
            key: elasticsearchuser
            name: bobs-bookstore-weblogic-credentials
            optional: true
      - name: ELASTICSEARCH_PASSWORD
        valueFrom:
          secretKeyRef:
            key: elasticsearchpassword
            name: bobs-bookstore-weblogic-credentials
            optional: true
      image: fluent/fluentd-kubernetes-daemonset:v1.3.3-debian-elasticsearch-1.3
      imagePullPolicy: IfNotPresent
      name: fluentd
      resources: {}
      volumeMounts:
      - mountPath: /fluentd/etc/fluentd.conf
        name: fluentd-config-volume
        subPath: fluentd.conf
      - mountPath: /scratch
        name: weblogic-domain-storage-volume
```

#### Verify logs are exported to Elasticsearch

After the Administration Server and Managed Server pods have started with all the changes described above, the logs should now be sent to Elasticsearch.

You can check if the `fluentd` container is successfully tailing the log by executing a command like `kubectl logs -f bobs-bookstore-admin-server -n bob fluentd`.  The log output should look similar to this:
```text
2019-10-01 16:23:44 +0000 [info]: #0 starting fluentd worker pid=13 ppid=9 worker=0
2019-10-01 16:23:44 +0000 [warn]: #0 /scratch/logs/bobs-bookstore/managed-server1.log not found. Continuing without tailing it.
2019-10-01 16:23:44 +0000 [info]: #0 fluentd worker is now running worker=0
2019-10-01 16:24:01 +0000 [info]: #0 following tail of /scratch/logs/bobs-bookstore/managed-server1.log
```

When you connect to Kibana, you will see an index created for the `domainUID`.

Example Kibana log output:
```text
timestamp:Oct 1, 2019 4:18:07,111 PM GMT level:Info subSystem:Management serverName:bobs-bookstore-admin-server serverName2:
threadName:Thread-8 info1: info2: info3: sequenceNumber:1569946687111 severity:[severity-value: 64] [partition-id: 0] [partition-name: DOMAIN]
messageID:BEA-141107 message:Version: WebLogic Server 12.2.1.3.0 Thu Aug 17 13:39:49 PDT 2017 1882952
_id:OQIeiG0BGd1zHsxmUrEJ _type:fluentd _index:bobs-bookstore _score:1
```

#### Domain example

The following is a complete example of a domain custom resource with a `fluentd` container configured.

```yaml
apiVersion: weblogic.oracle/v6
kind: Domain
metadata:
  labels:
    weblogic.domainUID: bobs-bookstore
    weblogic.resourceVersion: domain-v2
  name: bobs-bookstore
  namespace: bob
spec:
  adminServer:
    adminService:
      channels:
      - channelName: default
        nodePort: 32401
      - channelName: T3Channel
        nodePort: 32402
  clusters:
  - clusterName: cluster-1
    serverPod:
  domainHome: /u01/oracle/user_projects/domains/bobs-bookstore
  domainHomeInImage: true
  domainUID: bobs-bookstore
  experimental:
    istio:
      enabled: true
      readinessPort: 8888
  image: phx.ocir.io/bobs-bookstore
  imagePullPolicy: IfNotPresent
  imagePullSecrets:
  - name: ocir
  includeServerOutInPodLog: true
  logHome: /scratch/logs/bobs-bookstore
  logHomeEnabled: true
  replicas: 2
  serverPod:
    containers:
    - args:
      - -c
      - /etc/fluent.conf
      env:
      - name: DOMAIN_UID
        valueFrom:
          fieldRef:
            fieldPath: metadata.labels['weblogic.domainUID']
      - name: SERVER_NAME
        valueFrom:
          fieldRef:
            fieldPath: metadata.labels['weblogic.serverName']
      - name: LOG_PATH
        value: /scratch/logs/bobs-bookstore/$(SERVER_NAME).log
      - name: FLUENTD_CONF
        value: fluentd.conf
      - name: FLUENT_ELASTICSEARCH_SED_DISABLE
        value: "true"
      - name: ELASTICSEARCH_HOST
        valueFrom:
          secretKeyRef:
            key: elasticsearchhost
            name: bobs-bookstore-weblogic-credentials
      - name: ELASTICSEARCH_PORT
        valueFrom:
          secretKeyRef:
            key: elasticsearchport
            name: bobs-bookstore-weblogic-credentials
      - name: ELASTICSEARCH_USER
        valueFrom:
          secretKeyRef:
            key: elasticsearchuser
            name: bobs-bookstore-weblogic-credentials
            optional: true
      - name: ELASTICSEARCH_PASSWORD
        valueFrom:
          secretKeyRef:
            key: elasticsearchpassword
            name: bobs-bookstore-weblogic-credentials
            optional: true
      image: fluent/fluentd-kubernetes-daemonset:v1.3.3-debian-elasticsearch-1.3
      imagePullPolicy: IfNotPresent
      name: fluentd
      resources: {}
      volumeMounts:
      - mountPath: /fluentd/etc/fluentd.conf
        name: fluentd-config-volume
        subPath: fluentd.conf
      - mountPath: /scratch
        name: weblogic-domain-storage-volume
    env:
    - name: JAVA_OPTIONS
      value: -Dweblogic.StdoutDebugEnabled=false
    - name: USER_MEM_ARGS
      value: '-Djava.security.egd=file:/dev/./urandom -Xms64m -Xmx256m '
    - name: WL_HOME
      value: /u01/oracle/wlserver
    - name: MW_HOME
      value: /u01/oracle
    volumeMounts:
    - mountPath: /scratch
      name: weblogic-domain-storage-volume
    volumes:
    - emptyDir: {}
      name: weblogic-domain-storage-volume
    - configMap:
        defaultMode: 420
        name: fluentd-config
      name: fluentd-config-volume
  serverStartPolicy: IF_NEEDED
  webLogicCredentialsSecret:
    name: bobs-bookstore-weblogic-credentials
```
