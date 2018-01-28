# Prometheus integration

**ATTENTION EARLY ACCESS USERS** This page is not ready for general consumption yet, we have some rough notes in here, we are working on writing better doc for how to set up this integration.

Note that there is a video demonstration of the Prometheus integration available [here](https://youtu.be/D7KWVXzzqx8).

The [WebLogic Monitoring Exporter](https://github.com/oracle/weblogic-monitoring-exporter) provides the ability to export WebLogic and JVM metrics to Prometheus, from where they can be visualized in Grafana dashboards, and alerts can be created to initation scaling of WebLogic clusters.

To enable this integration, users must complete the following steps:

* Deploy the WebLogic Monitoring Exporter to the WebLogic domains.
* x.
* y.


## Deploy WebLogic Monitoring Exporter on WebLogic Servers

There are more detailed instructions in the [WebLogic Monitoring Exporter repository](https://github.com/oracle/wls-monitoring-exporter) about how deploy the WebLogic Monitoring Exporter, and you are encouraged to follow those steps.  A quick overview is presented here which will be satisfactory for most demo/trial scenarios:

1. Obtain a clone of the WebLogic Monitoring Exporter project:

```
git clone https://github.com/oracle/wls-monitoring-exporter
```

2. Build the Servlets:

```
mvn clean install
```

3. Create a configuration file.  Here is a simple example, call this `config.yaml` for example.

```
query_sync:
  url: http://coordinator:8999/
  interval: 5
metricsNameSnakeCase: true
queries:
- applicationRuntimes:
    key: name
    keyName: app
    componentRuntimes:
      type: WebAppComponentRuntime
      prefix: webapp_config_
      key: name
      values: [deploymentState, contextRoot, sourceInfo, openSessionsHighCount]
      servlets:
        prefix: weblogic_servlet_
        key: servletName
        values: invocationTotalCount
```

4. Create the WLS Exporter WAR file:

```
cd webapp
mvn package -DconfigurationFile=/path/to/config.yaml
```

5. If you did not create a `NodePort` service so the domain1 Administration Console can be accessed from a browser, do that now.  This example assumes the `domainUID` is `domain1`, the Administration Server is `admin-server` and the namespace is `domain1`.  The second command is used to obtain the `NodePort` that was allocated.

```
kubectl expose pod domain1-admin-server --type=NodePort --name=domain1-7001 -n domain1
kubectl -n domain1 get service domain1-7001
```

6. Copy the WLS Exporter WAR file `webapp/target/wls-exporter.war` to the target domain's application directory, that is `<persistent-volume>/applications`.

7. Using the external service port, use your browser to go to the WebLogic Administration Console `http://<hostname>:<nodeport>/console`:

8. Deploy and start the web application:

   - Deploy `wls-exporter.war` to all managed servers.
   - Start each deployed application.

9. For each started Managed Server, it is necessary to set the Prometheus annotations
   so that the servers are recognized by Prometheus and it will fetch the metrics.

For each Managed Server pod, set the following Prometheus annotations.  Care should be taked to ensure these are added as `annotations` and not `labels`:

```
prometheus.io/port: "8001"
prometheus.io/path: /wls-exporter/metrics
prometheus.io/scrape: "true"
```

This can be done by editing the pods:

```
kubectl -n domain1 edit pod domain1.managed-serverN
```

Note:  Because the operator does not yet support copying these manually added annotations to a new pod, this procedure is applicable only to a running pod.  If a new pod is started, for example during a scale operation, the annotations would need to be added to that pod manually. Oracle plans to automate the creation of these annotations, so this step will not be necessary.

## Setting up the webhook for AlertManager

**TODO** check with Bill where this script comes from.

1. Create the webhook image - `./createWebhook.sh`

## Deploying Prometheus, Grafana, AlertManager, and Webhook

1. Startup the Prometheus and Grafana monitoring pods. These are started in the
   monitoring namespace. These applications can be stopped and removed by deleting
   the namespace.

   ./deploy.sh

   For each, a NodePort service is started so the web GUI can be accessed from a
   browser.

   kubectl -n monitoring get services

2. Grafana is optional - Navigate the browser to the Grafana http:<host><nodePort>  When the Grafana login screen appears
   login with username=admin and password=admin

   - Expand menu from icon in upper left-hand corner of screen.
     1. Goto Data Sources and click on Add
     2. Enter Name - Prometheus
     3. Enter Type - select prometheus from pull down menu
     4. Enter URL  - http://prometheus:9090
     5. Click Add button at bottom of panel mc

   - Expand menu from icon in upper left-hand corner of screen.
     1. Goto Dashboards -> Import
     2. Click Upload .json File from Import Dashboasrd panel
     3. From file selection screen, go to folder containing WLS-dashboard.json
     4. Double Click WLS-dashboard from file selection panel
     5. When Import Dashboard panel appears, Select a Prometheus data source, then click Import button
     6. The Prometheus dashboard will appear with three graphs.

3. Access the Prometheus dashboard at http://<host>:30000
   You can access the metrics and Alert configuration from this page.

4. Access the AlertManager dashboard at http://<host>:30001
   You can access the AlertManager active alerts from this page.

5. Run curly <host> to generate a bunch of sessions with curl.    
