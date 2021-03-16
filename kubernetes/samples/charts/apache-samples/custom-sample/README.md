# Apache load balancer custom sample
In this sample, we will configure the Apache webtier as a load balancer for multiple WebLogic domains using a custom configuration. We will demonstrate how to use the Apache webtier to handle traffic to multiple backend WebLogic domains.

## 1. Create a namespace
In this sample, both the Apache webtier and WebLogic domain instances are located in the namespace `apache-sample`.
```shell
$ kubectl create namespace apache-sample
```

## 2. Create WebLogic domains
We need to prepare some backend domains for load balancing by the Apache webtier. Refer to the [sample](/kubernetes/samples/scripts/create-weblogic-domain/domain-home-on-pv/README.md), to create two WebLogic domains under the namespace `apache-sample`.

The first domain uses the following custom configuration parameters:
- namespace: `apache-sample`
- domainUID: `domain1`
- clusterName: `cluster-1`
- adminServerName: `admin-server`
- adminPort: `7001`
- adminNodePort: `30701`
- managedServerPort: `8001`

The second domain uses the following custom configuration parameters:
- namespace: `apache-sample`
- domainUID: `domain2`
- clusterName: `cluster-1`
- adminServerName: `admin-server`
- adminPort: `7011`
- adminNodePort: `30702`
- managedServerPort: `8021`

After the domains are successfully created, deploy the sample web application, `testwebapp.war`, on each domain cluster using the WLS Administration Console. The sample web application is located in the `kubernetes/samples/charts/application` directory.

## 3. Build the Apache webtier Docker image
Refer to the [sample](https://github.com/oracle/docker-images/tree/master/OracleWebLogic/samples/12213-webtier-apache), to build the Apache webtier Docker image.

## 4. Provide the custom Apache plugin configuration
In this sample, we will provide a custom Apache plugin configuration to fine tune the behavior of Apache.

* Create a custom Apache plugin configuration file named `custom_mod_wl_apache.conf`. The file content is similar to below.

```
# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

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

* Create a PV / PVC (pv-claim-name) that can be used to store the `custom_mod_wl_apache.conf`. Refer to the [Sample for creating a PV or PVC](/kubernetes/samples/scripts/create-weblogic-domain-pv-pvc/README.md).

## 5. Prepare your own certificate and private key
In production, Oracle strongly recommends that you provide your own certificates. Run the following commands to generate your own certificate and private key using `openssl`.

```shell
$ cd kubernetes/samples/charts/apache-samples/custom-sample
$ export VIRTUAL_HOST_NAME=apache-sample-host
$ export SSL_CERT_FILE=apache-sample.crt
$ export SSL_CERT_KEY_FILE=apache-sample.key
$ sh certgen.sh
```

## 6. Prepare the input values for the Apache webtier Helm chart
Run the following commands to prepare the input value file for the Apache webtier Helm chart.

```shell
$ base64 -i ${SSL_CERT_FILE} | tr -d '\n'
$ base64 -i ${SSL_CERT_KEY_FILE} | tr -d '\n'
$ touch input.yaml
```
Edit the input parameters file, `input.yaml`. The file content is similar to below.

```yaml
# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Use this to provide your own Apache webtier configuration as needed; simply define this
# Persistence Volume which contains your own custom_mod_wl_apache.conf file.
persistentVolumeClaimName: <pv-claim-name>

# The VirtualHostName of the Apache HTTP server. It is used to enable custom SSL configuration.
virtualHostName: apache-sample-host

# The customer supplied certificate to use for Apache webtier SSL configuration.
# The value must be a string containing a base64 encoded certificate. Run following command to get it.
# base64 -i ${SSL_CERT_FILE} | tr -d '\n'
customCert: <cert_data>

# The customer supplied private key to use for Apache webtier SSL configuration.
# The value must be a string containing a base64 encoded key. Run following command to get it.
# base64 -i ${SSL_KEY_FILE} | tr -d '\n'
customKey: <key_data>
```

## 7. Install the Apache webtier Helm chart
The Apache webtier Helm chart is located in the `kubernetes/samples/charts/apache-webtier` directory. Install the Apache webtier Helm chart to the `apache-sample` namespace with the specified input parameters:

```shell
$ cd kubernetes/samples/charts
$ helm install my-release --values apache-samples/custom-sample/input.yaml --namespace apache-sample apache-webtier
```

## 8. Run the sample application
Now you can send requests to different WebLogic domains with the unique entry point of Apache with different paths. Alternatively, you can access the URLs in a web browser.
```shell
$ curl --silent http://${HOSTNAME}:30305/weblogic1/testwebapp/
$ curl --silent http://${HOSTNAME}:30305/weblogic2/testwebapp/
```
Also, you can use SSL URLs to send requests to different WebLogic domains. Access the SSL URL via the `curl` command or a web browser.
```shell
$ curl -k --silent https://${HOSTNAME}:30443/weblogic1/testwebapp/
$ curl -k --silent https://${HOSTNAME}:30443/weblogic2/testwebapp/
```

## 9. Uninstall the Apache webtier
```shell
$ helm uninstall my-release --namespace apache-sample
```
