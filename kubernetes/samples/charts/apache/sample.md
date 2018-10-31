# Install and Configure Apache Webtier
## Configure Apache Webtier as Load Balancer for WLS Domains
In this section we will demonstrate how to use Apache webtier to handle traffic to backend WLS domains.

### 1. Install WLS Domains
Now we need to prepare some backends for Apache to do load balancing.

Create two WLS domains: 
- One domain with name 'domain1' under namespace 'default'.
- One domain with name 'domain2' under namespace 'default'.
- Each domain has a webapp installed with url context 'testwebapp'.

### 2. Provide custom Apache Plugin Configuration
In this sample we will provide custom Apache plugin configuration to fine tune the behavior of Apache.
- Create a custom Apache plugin configuration file named `custom_mod_wl_apache.conf`. The file content is similar as below.
```
<IfModule mod_weblogic.c>
WebLogicHost ${WEBLOGIC_HOST}
WebLogicPort ${WEBLOGIC_PORT}
</IfModule>

# Directive for weblogic admin Console deployed on Weblogic Admin Server
<Location /console>
SetHandler weblogic-handler
WebLogicHost domain1-admin-server
WebLogicPort ${WEBLOGIC_PORT}
</Location>

# Directive for all application deployed on weblogic cluster with a prepath defined by LOCATION variable
# For example, if the LOCAITON is set to '/weblogic', all applications deployed on the cluster can be accessed via 
# http://myhost:myport/weblogic/application_end_url
# where 'myhost' is the IP of the machine that runs the Apache web tier, and 
#       'myport' is the port that the Apache web tier is publicly exposed to.
# Note that LOCATION cannot be set to '/' unless this is the only Location module configured.
<Location /weblogic1>
WLSRequest On
WebLogicCluster domain1-cluster-cluster-1:8001
PathTrim /weblogic1
</Location>

# Directive for all application deployed on weblogic cluster with a prepath defined by LOCATION2 variable
# For example, if the LOCAITON2 is set to '/weblogic2', all applications deployed on the cluster can be accessed via
# http://myhost:myport/weblogic2/application_end_url
# where 'myhost' is the IP of the machine that runs the Apache web tier, and
#       'myport' is the port that the Apache webt ier is publicly exposed to.
<Location /weblogic2>
WLSRequest On
WebLogicCluster domain2-cluster-cluster-1:8021
PathTrim /weblogic2
</Location>
```
- Place the `custom_mod_wl_apache.conf` file in a local directory `<host-config-dir>` on the host machine.

### 3. Pull Apache Webtier Docker Image
Run the following commands to pull Apache webtier docker image from repositry manually.
```
$ docker pull wlsldi-v2.docker.oraclecorp.com/weblogic-webtier-apache-12.2.1.3.0:latest
$ docker tag wlsldi-v2.docker.oraclecorp.com/weblogic-webtier-apache-12.2.1.3.0:latest store/oracle/apache:12.2.1.3
```

### 4. Install Apache Webtier with Helm Chart
Apache webtier helm chart is located at https://github.com/oracle/weblogic-kubernetes-operator/blob/develop/kubernetes/samples/charts/apache-webtier.
Install Apache webtier helm chart to default namespace with specified docker volume path:
```
$ cd kubernetes/samples/charts
$ helm install --name my-release --set volumePath=<host-config-dir> apache-webtier
```
Now you can send requests to different WLS domains with the unique entry point of Apache with different path. Alternatively, you can access the URLs in a web browser.
```
$ curl --silent http://${HOSTNAME}:30305/weblogic1/testwebapp/
$ curl --silent http://${HOSTNAME}:30305/weblogic2/testwebapp/
```
You can also access SSL URL `https://${HOSTNAME}:30443/weblogic1/testwebapp/` and `https://${HOSTNAME}:30443/weblogic2/testwebapp/` in your web browser.

### 5. Update Apache Plugin Configuration
Users can update or add the new Apache plugin configuration first, then run the following commands to restart the Apache HTTP Server gracefully:
```
$ kubectl exec -it <apache-http-server-pod-name> bash
$ httpd -k graceful
```
A graceful restart will take effect on the updated configuration without interrupting the current requests.

## Uninstall Apache Webtier
```
$ helm delete --purge my-release
```
