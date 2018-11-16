# Apache Load Balancer default sample
In this sample, we will configure Apache webtier as a load balancer for WebLogic domain using the default configuration. We will demonstrate how to use Apache webtier to handle traffic to the backend WebLogic domain.

## 1. Create WebLogic Domain
Now we need to prepare backend for Apache webtier to do load balancing. Please refer the sample https://github.com/oracle/weblogic-kubernetes-operator/tree/develop/kubernetes/samples/scripts/create-weblogic-domain/domain-home-on-pv to create a WebLogic domain. Keep the default values for the following configuration parameters:
- namespace: default
- domainUID: domain1
- clusterName: cluster-1
- adminServerName: admin-server
- adminPort: 7001
- managedServerPort: 8001

After the domain is successfully created, deploy the sample web application testwebapp.war on the domain cluster through the admin console. The sample web application is located in the kubernetes/samples/charts/application directory.

## 2. Build Apache Webtier Docker Image
Please refer the sample https://github.com/oracle/docker-images/tree/master/OracleWebLogic/samples/12213-webtier-apache to build Apache webtier docker image.

## 3. Install Apache Webtier with Helm Chart
Apache webtier helm chart is located at https://github.com/oracle/weblogic-kubernetes-operator/blob/develop/kubernetes/samples/charts/apache-webtier.
Install Apache webtier helm chart to default namespace with default settings:
```
$ cd kubernetes/samples/charts
$ helm install --name my-release apache-webtier
```

## 4. Run the sample application
Now you can send request to WebLogic domain with the unique entry point of Apache. Alternatively, you can access the URL in a web browser.
```
$ curl --silent http://${HOSTNAME}:30305/weblogic/testwebapp/
```
You can also use SSL URL to send request to WebLogic domain. Access the SSL URL via the curl command or a web browser.
```
$ curl -k --silent https://${HOSTNAME}:30443/weblogic/testwebapp/
```

## 5. Uninstall Apache Webtier
```
$ helm delete --purge my-release
```
