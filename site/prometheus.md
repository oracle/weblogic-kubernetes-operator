# Prometheus integration

**ATTENTION EARLY ACCESS USERS** This page is not ready for general consumption yet, we have some rough notes in here, we are working on writing better doc for how to set up this integration.

Note that there is a video demonstration of the Prometheus integration available [here](https://youtu.be/D7KWVXzzqx8).

### Prometheus/Grafana Weblogic Cluster setup ###

Configuration settings described here will yield a working configuration. Changing the
settings must be done carefully to provide an alternative setup.

### Prequisites ###

Running on a Kubernetes Cluster --

1. Deploy the Weblogic Kubernetes Operator (create-weblogic-operator.sh)

   - In create-operator-inputs.yaml
        targetNamespaces: domain1

2. Create and start a domain (create-domain-job.sh)

   - In create-domain-job-inputs.yaml
        domainUid: domain1
        managedServerCount: 4
        managedServerStartCount: 2
        namespace: domain1

### WebLogic Server Application setup ###  

1. Create a NodePort service so the domain1 admin console can be access from a browser

   kubectl expose pod domain1-admin-server --type=NodePort --name=domain1-7001 -n domain1
   kubectl -n domain1 get service domain1-7001

2. Copy apps/testwebapp.war and apps/wls-exporter.war to <persistent-volume>/applications

3. Using the external service port, use your browser to go to http://<hostname>:<nodeport>/console

4. Login to the admin server using weblogic/welcome1 as the login credentials.

   - Deploy testwebapp.war and wls-exporter.war to all managed servers.
   - Start each deployed application

5. For each started managed server it is necessary to set the Prometheus annotations
   so that the servers are recognized by Prometheus and it will fetch the metrics.

   - For each managed server pod set the following Prometheus annotations:

	prometheus.io/port: "8001"
	prometheus.io/path: /wls-exporter/metrics
	prometheus.io/scrape: "true"

    kubectl -n domain1 edit pod domain1.managed-serverN

    Note:
    Since the operator does not support changed annotations this procedure is only
    applicable to a running pod.

### Setting up the webhook for AlertManager ###

1. Create the webhook image - ./createWebhook.sh

### Deploying Prometheus, Grafana, AlertManager, and Webhook ###

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
