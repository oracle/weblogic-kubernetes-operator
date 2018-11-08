# Apache Load Balancer default sample
## Configure Apache Webtier as Load Balancer for WLS Domains
In this section we will demonstrate how to use Apache webtier to handle traffic to backend WLS domains.

### 1. Install WebLogic Server Domain
Now we need to prepare some backends for Apache to do load balancing.

Create a WebLogic Server domain:
- Under default namespace.
- Domain name is 'domain1'.
- Deploy a webapp  with url context 'testwebapp'.

### 2. Pull Apache Webtier Docker Image
Run the following commands to pull Apache webtier docker image from repositry manually.
```
$ docker pull wlsldi-v2.docker.oraclecorp.com/weblogic-webtier-apache-12.2.1.3.0:latest
$ docker tag wlsldi-v2.docker.oraclecorp.com/weblogic-webtier-apache-12.2.1.3.0:latest store/oracle/apache:12.2.1.3
```

### 3. Install Apache Webtier with Helm Chart
Apache webtier helm chart is located at https://github.com/oracle/weblogic-kubernetes-operator/blob/develop/kubernetes/samples/charts/apache-webtier.
Install Apache webtier helm chart to default namespace with default settings:
```
$ cd kubernetes/samples/charts
$ helm install --name my-release apache-webtier
```

### 4. Run the sample application
Now you can send requests to different WLS domains with the unique entry point of Apache with different path. Alternatively, you can access the URLs in a web browser.
```
$ curl --silent http://${HOSTNAME}:30305/weblogic/testwebapp/
```
You can also access SSL URL `https://${HOSTNAME}:30443/weblogic/testwebapp/` in your web browser.

## Uninstall Apache Webtier
```
$ helm delete --purge my-release
```
