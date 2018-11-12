# Apache Load Balancer custom sample
In this sample, we will configure Apache webtier as a load balancer for multiple WebLogic domains using custom configuration. We will demonstrate how to use Apache webtier to handle traffic to the multiple backend WebLogic domains.

## 1. Create namespace
In this sample, both Apache webtier and WebLogic domain instances are located in the namespace `apache-sample`.
```
$ kubectl create namespace apache-sample
```

## 2. Create WebLogic Domains
Now we need to prepare some backends for Apache webtier to do load balancing. Please refer the sample https://github.com/oracle/weblogic-kubernetes-operator/tree/develop/kubernetes/samples/scripts/create-weblogic-domain/domain-home-on-pv to create two WebLogic domains under the namespace `apache-sample`.

The first domain uses the following custom configuration parameters:
- namespace: apache-sample
- domainUID: domain1
- clusterName: cluster-1
- adminServerName: admin-server
- adminPort: 7001
- adminNodePort: 30701
- managedServerPort: 8001

The second domain uses the following custom configuration parameters:
- namespace: apache-sample
- domainUID: domain2
- clusterName: cluster-1
- adminServerName: admin-server
- adminPort: 7011
- adminNodePort: 30702
- managedServerPort: 8021

After the domains are successfully created, deploy the sample web application testwebapp.war on each domain cluster through the admin console.

## 3. Provide custom Apache Plugin Configuration
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

## 4. Build Apache Webtier Docker Image
Please refer the sample https://github.com/oracle/docker-images/tree/master/OracleWebLogic/samples/12213-webtier-apache to build Apache webtier docker image.

## 5. Install Apache Webtier with Helm Chart
Apache webtier helm chart is located at https://github.com/oracle/weblogic-kubernetes-operator/blob/develop/kubernetes/samples/charts/apache-webtier.
Install Apache webtier helm chart to apache-sample namespace with specified docker volume path:
```
$ cd kubernetes/samples/charts
$ helm install --name my-release --set volumePath=<host-config-dir> --namespace apache-sample apache-webtier
```

## 6. Run the sample application
Now you can send requests to different WLS domains with the unique entry point of Apache with different path. Alternatively, you can access the URLs in a web browser.
```
$ curl --silent http://${HOSTNAME}:30305/weblogic1/testwebapp/
$ curl --silent http://${HOSTNAME}:30305/weblogic2/testwebapp/
```
You can also access SSL URL `https://${HOSTNAME}:30443/weblogic1/testwebapp/` and `https://${HOSTNAME}:30443/weblogic2/testwebapp/` in your web browser.

## 7. Uninstall Apache Webtier
```
$ helm delete --purge my-release
```
