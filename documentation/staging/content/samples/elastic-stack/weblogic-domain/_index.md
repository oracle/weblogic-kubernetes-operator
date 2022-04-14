---
title: "WebLogic domain"
date: 2019-10-01T14:32:31-05:00
weight: 2
description: "Sample for using Fluentd for WebLogic domain and operator's logs."
---


#### Overview
This document describes to how to configure a WebLogic domain to use Fluentd to send log information to Elasticsearch.

Here's the general mechanism for how this works:

* `fluentd` runs as a separate container in the Administration Server and Managed Server pods.
* The log files reside on a volume that is shared between the `weblogic-server` and `fluentd` containers.
* `fluentd` tails the domain logs files and exports them to Elasticsearch.
* A `ConfigMap` contains the filter and format rules for exporting log records.

##### Sample code

The samples in this document assume an existing domain is being edited.  However, all changes to the domain YAML file can be performed before the domain is created.

For sample purposes, this document will assume a domain with the following attributes is being configured:

* Domain name is `bobs-bookstore`
* Kubernetes Namespace is `bob`
* Kubernetes Secret is `bobs-bookstore-weblogic-credentials`

The sample Elasticsearch configuration is:
```text
    elasticsearchhost: elasticsearch.bobs-books.sample.com
    elasticsearchport: 443
    elasticsearchuser: bob
    elasticsearchpassword: changeme
```

#### Configure log files to use a volume
The domain log files must be written to a volume that can be shared between the `weblogic-server` and `fluentd` containers.  The following elements are required to accomplish this:

* `logHome` must be a path that can be shared between containers.
* `logHomeEnabled` must be set to `true` so that the logs will be written outside the pod and persist across pod restarts.
* A `volume` must be defined on which the log files will reside.  In the example, `emptyDir` is a volume that gets created empty when a pod is created.  It will persist across pod restarts but deleting the pod would delete the `emptyDir` content.
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

#### Create Elasticsearch secrets
The `fluentd` container will be configured to look for Elasticsearch parameters in a Kubernetes secret.  Create a secret with following keys.

Example: 
```text
kubectl -n bob create secret generic fluentd-credential 
  --from-literal elasticsearchhost=quickstart-es-http.default 
  --from-literal elasticsearchport=9200 
  --from-literal elasticsearchuser=elastic 
  --from-literal elasticsearchpassword=xyz
```

#### Specify `fluentdSpecification` in the domain resource

```text
  spec:
    fluentdSpecification:
      elasticSearchCredentials: fluentd-credential
      watchIntrospectorLogs: false
      volumeMounts:
      - mountPath: /shared
        name: weblogic-domain-storage-volume
```

The `Operator` will automatically

1. Creates a config map `fluentd-config` with a default `Fluentd` configuration. See ...
2. Set up the `Fluentd` container in each pod to use the `Elasticsearch` secrets.

You can also customize the `Fluentd` configuration to fit your use case.  

`fluentdSpecification`  options:

|Option|Description|Notes|
|-|-|-|
|elasticSearchCredentials|Kubernetes secret name for the `Fluentd` container to communicate with `Elasticsearch` engine. ||
|watchIntrospectorLogs|If set to true, Operator will also setup a `fluentd` container to watch the introspector job output. Default is false|Operator automatically added a volume mount referencing the Configmap volume containing the `Fluentd` configuration|
|volumeMounts|Additional list of volumeMounts for the `fluentd` container|It should contain at least the logHome shared volume|
|image|`fluentd` container image name. Default: ||
|imagePullPolicy|ImagePull policy for the `fluentd` container||
|env|Additional list of environment variables for `fluentd` container|See below|
|resources|resources for the `fluentd` container||
|fluentdConfiguration|Text for `fluentd` configuration instead of the Operator's defaults |See below|

For example:

```text
 fluentdSpecification:
    elasticSearchCredentials: fluentd-credential
    watchIntrospectorLogs: true
    volumeMounts:
    - mountPath: /shared
      name: weblogic-domain-storage-volume
    fluentdConfiguration: |-
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
          format13 / <(?<message>(.*?))>.*/
          # use the timestamp field in the message as the timestamp
          # instead of the time the message was actually read
          time_key timestamp
          keep_time_key true
        </parse>
      </source>
      <source>
        @type tail
        path "#{ENV['INTROSPECTOR_OUT_PATH']}"
        pos_file /tmp/introspector.log.pos
        read_from_head true
        tag "#{ENV['DOMAIN_UID']}-introspector"
        # multiline_flush_interval 20s

        <parse>
          @type multiline
          format_firstline /@\[/
          format1 /^@\[(?<timestamp>.*)\]\[(?<filesource>.*?)\]\[(?<level>.*?)\](?<message>.*)/
          # use the timestamp field in the message as the timestamp
          # instead of the time the message was actually read
          time_key timestamp
          keep_time_key true
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
        ssl_verify false
        key_name timestamp
        types timestamp:time
        suppress_type_name true
        # inject the @timestamp special field (as type time) into the record
        # so you will be able to do time based queries.
        # not to be confused with timestamp which is of type string!!!
        include_timestamp true
      </match>
```

#### Environment variables in the `fluentd` container

Operator automatically set up the `fluentd` container with the following:

```text
    env:
    - name: ELASTICSEARCH_HOST
      valueFrom:
        secretKeyRef:
          key: elasticsearchhost
          name: fluentd-credential
          optional: false
    - name: ELASTICSEARCH_PORT
      valueFrom:
        secretKeyRef:
          key: elasticsearchport
          name: fluentd-credential
          optional: false
    - name: ELASTICSEARCH_USER
      valueFrom:
        secretKeyRef:
          key: elasticsearchuser
          name: fluentd-credential
          optional: true
    - name: ELASTICSEARCH_PASSWORD
      valueFrom:
        secretKeyRef:
          key: elasticsearchpassword
          name: fluentd-credential
          optional: true
    - name: FLUENT_ELASTICSEARCH_SED_DISABLE
      value: "true"
    - name: FLUENTD_CONF
      value: fluentd.conf
    - name: DOMAIN_UID
      valueFrom:
        fieldRef:
          apiVersion: v1
          fieldPath: metadata.labels['weblogic.domainUID']
    - name: SERVER_NAME
      valueFrom:
        fieldRef:
          apiVersion: v1
          fieldPath: metadata.labels['weblogic.serverName']
    - name: LOG_PATH
      value: /shared/logs/sample-domain1/admin-server.log
    - name: INTROSPECTOR_OUT_PATH
      value: /shared/logs/sample-domain1/introspector_script.out

```

#### Fluentd configuration
Operator automatically creates a `ConfigMap` named `webogic-fluentd-configmap` in the namespace of the domain.  The `ConfigMap` contains the parsing rules and Elasticsearch configuration.

Here's an explanation of some elements defined in the `ConfigMap`:

* The `@type tail` indicates that `tail` will be used to obtain updates to the log file.
* The `path` of the log file is obtained from the `LOG_PATH` environment variable that is defined in the `fluentd` container.
* The `tag` value of log records is obtained from the `DOMAIN_UID` environment variable that is defined in the `fluentd` container.
* The `<parse>` section defines how to interpret and tag each element of a log record.
* The `<match **>` section contains the configuration information for connecting to Elasticsearch and defines the index name of each record to be the `domainUID`.

The following is the default `Fluentd` configuration if `fluentdConfiguration` is not specified:
```text
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
            # use the timestamp field in the message as the timestamp
            # instead of the time the message was actually read
            time_key timestamp
            keep_time_key true
          </parse>
        </source>
        <source>
          @type tail
          path "#{ENV['INTROSPECTOR_OUT_PATH']}"
          pos_file /tmp/introspector.log.pos
          read_from_head true
          tag "#{ENV['DOMAIN_UID']}-introspector"
          # multiline_flush_interval 20s
          <parse>
            @type multiline
            format_firstline /@\[/
            format1 /^@\[(?<timestamp>.*)\]\[(?<filesource>.*?)\]\[(?<level>.*?)\](?<message>.*)/
            # use the timestamp field in the message as the timestamp
            # instead of the time the message was actually read
            time_key timestamp
            keep_time_key true
          </parse>
         </source>
        <match **>
          @type elasticsearch
          host "#{ENV['ELASTICSEARCH_HOST']}"
          port "#{ENV['ELASTICSEARCH_PORT']}"
          user "#{ENV['ELASTICSEARCH_USER']}"
          password "#{ENV['ELASTICSEARCH_PASSWORD']}"
          index_name "#{ENV['DOMAIN_UID']}"
          suppress_type_name true
          type_name fluentd
          logstash_format true
          logstash_prefix fluentd
          # inject the @timestamp special field (as type time) into the record
          # so you will be able to do time based queries.
          # not to be confused with timestamp which is of type string!!!
          include_timestamp true
        </match>

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

