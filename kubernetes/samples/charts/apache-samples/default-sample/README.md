# Apache load balancer default sample
In this sample, we will configure the Apache webtier as a load balancer for a WebLogic domain using the default configuration. We will demonstrate how to use the Apache webtier to handle traffic to a backend WebLogic domain.

## 1. Create a WebLogic domain
We need to prepare a backend domain for load balancing by the Apache webtier. Refer to the [sample](/kubernetes/samples/scripts/create-weblogic-domain/domain-home-on-pv/README.md), to create a WebLogic domain. Keep the default values for the following configuration parameters:
- namespace: `default`
- domainUID: `domain1`
- clusterName: `cluster-1`
- adminServerName: `admin-server`
- adminPort: `7001`
- managedServerPort: `8001`

After the domain is successfully created, deploy the sample web application, `testwebapp.war`, on the domain cluster using the WLS Administration Console. The sample web application is located in the `kubernetes/samples/charts/application` directory.

## 2. Build the Apache webtier Docker image
Refer to the [sample](https://github.com/oracle/docker-images/tree/master/OracleWebLogic/samples/12213-webtier-apache), to build the Apache webtier Docker image.

## 3. Install the Apache webtier with a Helm chart
The Apache webtier Helm chart [is located here](../../apache-webtier/README.md).
Install the Apache webtier Helm chart into the default namespace with the default settings:
```shell
$ cd kubernetes/samples/charts
$ helm install my-release apache-webtier
```

## 4. Run the sample application
Now you can send request to the WebLogic domain with the unique entry point of Apache. Alternatively, you can access the URL in a web browser.
```shell
$ curl --silent http://${HOSTNAME}:30305/weblogic/testwebapp/
```
You can also use an SSL URL to send requests to the WebLogic domain. Access the SSL URL via the `curl` command or a web browser.
```shell
$ curl -k --silent https://${HOSTNAME}:30443/weblogic/testwebapp/
```

## 5. Uninstall the Apache webtier
```shell
$ helm uninstall my-release
```
