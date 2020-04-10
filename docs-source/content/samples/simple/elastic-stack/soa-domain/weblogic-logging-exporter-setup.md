---
title: "Publish logs to Elasticsearch"
date: 2019-12-05T06:46:23-05:00
weight: 4
description: "Use the WebLogic Logging Exporter to publish the WebLogic Server logs to Elasticsearch."
---

The WebLogic Logging Exporter adds a log event handler to WebLogic Server. WebLogic Server logs can be pushed to Elasticsearch in Kubernetes directly
by using the Elasticsearch REST API. For more details, refer to the [WebLogic Logging Exporter](https://github.com/oracle/weblogic-logging-exporter) project.  

This sample shows you how to publish WebLogic Server logs to Elasticsearch and view them in Kibana. For publishing operator logs, see this [sample]({{< relref "/samples/simple/elastic-stack/operator/_index.md" >}}).

#### Prerequisites

This document assumes that you have already set up Elasticsearch and Kibana for logs collection. If you have not, please refer this [document](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/scripts/elasticsearch-and-kibana/README.md).

---  

#### Download the WebLogic Logging Exporter binaries

The pre-built binaries are available on the WebLogic Logging Exporter [Releases](https://github.com/oracle/weblogic-logging-exporter/releases) page.  

Download:

* [`weblogic-logging-exporter-0.1.1.jar`](https://github.com/oracle/weblogic-logging-exporter/releases) from the Releases page.
* [`snakeyaml-1.23.jar`](https://search.maven.org/artifact/org.yaml/snakeyaml/1.23/bundle) from Maven Central.

{{% notice note %}} These identifiers are used in the sample commands in this document.

* `soans`: SOA domain namespace
* `soainfra`: `domainUID`
* `soainfra-adminserver`: Administration Server pod name
{{% /notice %}}

#### Copy the JAR files to the WebLogic domain home

Copy the `weblogic-logging-exporter-0.1.1.jar` and `snakeyaml-1.23.jar` files to the domain home directory in the Administration Server pod.

```
$ kubectl cp <file-to-copy> <namespace>/<Administration-Server-pod>:<domainhome>

```

```
$ kubectl cp snakeyaml-1.23.jar soans/soainfra-adminserver:/u01/oracle/user_projects/domains/soainfra/

$ kubectl cp weblogic-logging-exporter-0.1.1.jar soans/soainfra-adminserver:/u01/oracle/user_projects/domains/soainfra/
```

#### Add a startup class to the domain configuration

1. Using the WebLogic Server Administration Console, in the left-side navigation pane, expand **Environment**, and then select **Startup and Shutdown Classes**.

1. Add a new startup class. You may choose any descriptive name, however, the class name must be `weblogic.logging.exporter.Startup`.   

1. Target the startup class to each server from which you want to export logs.  

You can verify this step by looking for the update in your `config.xml` file, which should look similar to the following example:
```
<startup-class>
  <name>weblogic-logging-exporter</name>
  <target>AdminServer,soa_cluster</target>
  <class-name>weblogic.logging.exporter.Startup</class-name>
</startup-class>
```  

#### Update the WebLogic Server `CLASSPATH`

Copy the `setDomainEnv.sh` file from the pod to a local folder.  
```
$ kubectl cp soans/soainfra-adminserver:/u01/oracle/user_projects/domains/soainfra/bin/setDomainEnv.sh .
```  

Modify `setDomainEnv.sh` to update the server class path.  
```
CLASSPATH=/u01/oracle/user_projects/domains/soainfra/weblogic-logging-exporter-0.1.1.jar:/u01/oracle/user_projects/domains/soainfra/snakeyaml-1.23.jar:${CLASSPATH}
export CLASSPATH
```  

Copy back the modified `setDomainEnv.sh` file to the pod.  
```
$ kubectl cp setDomainEnv.sh soans/soainfra-adminserver:/u01/oracle/user_projects/domains/soainfra/bin/setDomainEnv.sh
```

#### Create a configuration file for the WebLogic Logging Exporter  

Create a file named `WebLogicLoggingExporter.yaml`. Specify the Elasticsearch server host and port number.
```
weblogicLoggingIndexName: wls
publishHost: elasticsearch.default.svc.cluster.local
publishPort: 9200
domainUID: soainfra
weblogicLoggingExporterEnabled: true
weblogicLoggingExporterSeverity: TRACE
weblogicLoggingExporterBulkSize: 1
```  

Copy the `WebLogicLoggingExporter.yaml` file to the domain home directory in the WebLogic Server Administration Server pod.  
```
$ kubectl cp WebLogicLoggingExporter.yaml soans/soainfra-adminserver:/u01/oracle/user_projects/domains/soainfra/config/
```  

#### Restart all the servers in the domain

To restart the servers, stop and then start them using the following commands.

To stop the servers:
```
$ kubectl patch domain soainfra -n soans --type='json' -p='[{"op": "replace", "path": "/spec/serverStartPolicy", "value": "NEVER" }]'

```

To start the servers:
```
$ kubectl patch domain soainfra -n soans --type='json' -p='[{"op": "replace", "path": "/spec/serverStartPolicy", "value": "IF_NEEDED" }]'
```

After all the servers are restarted, in all the server logs, you will see the `weblogic-logging-exporter` class being called, as shown below.  
```
======================= WebLogic Logging Exporter Startup class called                                                 
Reading configuration from file name: /u01/oracle/user_projects/domains/soainfra/config/WebLogicLoggingExporter.yaml   
Config{weblogicLoggingIndexName='wls', publishHost='domain.host.com', publishPort=9200, weblogicLoggingExporterSeverity='Notice', weblogicLoggingExporterBulkSize='2', enabled=true, weblogicLoggingExporterFilters=FilterConfig{expression='NOT(MSGID = 'BEA-000449')', servers=[]}], domainUID='soainfra'}
```  

#### Create an index pattern in Kibana  
Create an index pattern `wls*` in **Kibana > Management**. After the servers are started, you will see the log data in the Kibana dashboard.  
