> **WARNING** This documentation is for version 1.0 of the operator.  To view documenation for the current release, [please click here](/site).

# Installation

Note that there is a short video demonstration of the installation process available [here](https://youtu.be/B5UmY2xAJnk).

[comment]: # ( Register for access to the Oracle Container Registry )
[comment]: # ( The operator Docker images are hosted in the Oracle Container Registry.  Before downloading the images, users must register for access to the registry by visiting [https://container-registry.oracle.com] https://container-registry.oracle.com  and clicking on the Register link. )
[comment]: # ( Setting up secrets to access the Oracle Container Registry )
[comment]: # (In order to obtain the operator Docker image from the Oracle Container Registry, which requires authentication, a Kubernetes secret containing the registry credentials must be created. To create a secret with Oracle Container Registry credentials, issue the following command: )
[comment]: # ( kubectl create namespace weblogic-operator )
[comment]: # ( kubectl create secret docker-registry SECRET_NAME -n weblogic-operator --docker-server=container-registry.oracle.com --docker-username=YOUR_USERNAME --docker-password=YOUR_PASSWORD --docker-email=YOUR_EMAIL )
[comment]: # ( Note that you *must* create the `docker-registry` secret in the `weblogic-operator` namespace, so you will need to create the namespace first. )
[comment]: # ( In this command, replace the uppercase items with the appropriate values. The `SECRET_NAME` will be needed in later parameter files.  The `NAMESPACE` must match the namespace where the operator will be deployed. )

## Build the Docker image for the operator using Wercker

You can build, test, and publish the Docker image for the operator directly from Wercker using the ```wercker.yml``` from this repository.

If you haven't done so already, navigate to [wercker.com](https://www.wercker.com) and create an account.  After you are logged in,
on the [app.wercker.com](https://app.wercker.com) page, click "Create your first application."

Select GitHub (the default, if you are new to Wercker).  If you haven't done so already, click the "Connect" button within the
larger GitHub button, and follow the prompts to provide a login for GitHub.  This connects your Wercker and GitHub accounts so
that Wercker pipelines will later be able to clone this repository.  Click "Next."

Select the repository from GitHub.  This will be `oracle / weblogic-kubernetes-operator` or a different value if you
forked this repository.  Click "Next."

Configure Wercker's access to the GitHub repository.  The default choice, "wercker will check out the code without using an SSH key",
is typically sufficient.  Click "Next."

Verify the settings so far on the review page and click "Create."

Because this GitHub repository already has a ```wercker.yml``` file, you can skip directly to the "Environment" tab.

Please provide the following key/value pairs on the Environment page.  Remember that these values will be
visible to anyone to whom you give access to the Wercker application, therefore, select "Protected" for any
values that should remain hidden, including all passwords.

| Key | Value | OCIR Sample |
| --- | --- | --- |
| `DOCKER_USERNAME` | Username for the Docker store for pulling server JRE image | |
| `DOCKER_PASSWORD` | Password for the Docker store | |
| `REPO_REGISTRY`| Registry address | `https://phx.ocir.io/v2`  |
| `REPO_REPOSITORY` | Repository value | `phx.ocir.io/<your tenancy>/weblogic-kubernetes-operator` |
| `REPO_USERNAME` | Username for registry | `<your tenancy>/<your username>` |
| `REPO_PASSWORD` | Password for registry | `Use generated SWIFT password` |
| `IMAGE_TAG_OPERATOR` | Image tag, such as 0.2 or latest |  |

Select the "Runs" tab.  Scroll to the bottom and click "Trigger your first build now."

When the run completes successfully, the Docker image for the operator will be built and published to your repository.

## Build the Docker image for the operator locally

The following software is required to obtain and build the operator:

*    Git (1.8 or later recommended)
*    Apache Maven (3.3 or later recommended)
*    Java Developer Kit (1.8u131 or later recommended, not 1.9)
*    Docker 17.03.1.ce

To run the operator in a Kubernetes cluster, you need to build the Docker image and then deploy it to your cluster.  The operator is built using Apache Maven, which must be installed first.  Maven may be downloaded from the [Apache Maven site](http://maven.apache.org).  The GitHub repository should be cloned locally:

```
git clone https://github.com/oracle/weblogic-kubernetes-operator.git
```

Then run the build using this command:

```
mvn clean install
```

Log in to the Docker Store so that you will be able to pull the base image and create the Docker image as follows.  These commands should be executed in the project root directory:

```
docker login
docker build -t weblogic-kubernetes-operator:developer --no-cache=true .
```

**Note**: If you have not used the base image (`store/oracle/serverjre:8`) before, you will need to visit the [Docker Store web interface](https://store.docker.com/images/oracle-serverjre-8) and accept the license agreement before the Docker Store will give you permission to pull that image.

We recommend that you use a tag other than `latest`, to make it easy to distinguish your image.  In the example above, the tag could be the GitHub ID of the developer.

Next, upload your image to your Kubernetes server as follows:

```
# on your build machine:
docker save weblogic-kubernetes-operator:developer > operator.tar
scp operator.tar YOUR_USER@YOUR_SERVER:/some/path/operator.tar
# on each Kubernetes worker:
docker load < /some/path/operator.tar
```

Verify that you have the right image by running `docker images | grep webloogic-kubernetes-operator` on both machines and comparing the image IDs.

[comment]: # ( Pull the operator image )
[comment]: # ( You can let Kubernetes pull the Docker image for you the first time you try to create a pod that uses the image, but we have found that you can generally avoid various common issues like putting the secret in the wrong namespace or getting the credentials wrong by just manually pulling the image by running these commands *on the Kubernetes master*: )
[comment]: # ( docker login container-registry.oracle.com )
[comment]: # ( docker pull container-registry.oracle.com/middleware/weblogic-kubernetes-operator:latest )

## Customizing the operator parameters file

The operator is deployed with the provided installation script (`create-weblogic-operator.sh`).  The default input to this script is the file `create-weblogic-operator-inputs.yaml`.  It contains the following parameters:

### Configuration parameters for the operator

| Parameter | Definition | Default |
| --- | --- | --- |
| `elkIntegrationEnabled` | Determines whether the Elastic Stack integration will be enabled.  If set to `true`, then Elasticsearch, Logstash, and Kibana will be installed, and Logstash will be configured to export the operator’s logs to Elasticsearch. | `false` |
| `externalDebugHttpPort` | The port number of the operator's debugging port outside of the Kubernetes cluster. | `30999` |
| `externalOperatorCert` | A base64 encoded string containing the X.509 certificate that the operator will present to clients accessing its REST endpoints. This value is only used when `externalRestOption` is set to `CUSTOM_CERT`. | |
| `externalOperatorKey` | A base64 encoded string containing the private key of the operator's X.509 certificate.  This value is used only when `externalRestOption` is set to `CUSTOM_CERT`. | |
| `externalRestHttpsPort`| The `NodePort` number that should be allocated on which the operator REST server should listen for HTTPS requests. | `31001` |
| `externalRestOption` | The available REST options.  Allowed values are: <br/>- `NONE` Disable the REST interface.  <br/>- `SELF_SIGNED_CERT` The operator will use a self-signed certificate for its REST server.  If this value is specified, then the `externalSans` parameter must also be set. <br/>- `CUSTOM_CERT` Provide custom certificates, for example, from an external certification authority. If this value is specified, then `externalOperatorCert` and `externalOperatorKey` must also be provided.| `NONE` |
| `externalSans`| A comma-separated list of Subject Alternative Names that should be included in the X.509 Certificate.  This list should include ... <br/>Example:  `DNS:myhost,DNS:localhost,IP:127.0.0.1` | |
| `internalDebugHttpPort` | The port number of the operator's debugging port inside the Kubernetes cluster. | `30999` |
| `javaLoggingLevel` | The level of Java logging that should be enabled in the operator.  Allowed values are: `SEVERE`, `WARNING`, `INFO`, `CONFIG`, `FINE`, `FINER`, and `FINEST` | `INFO` |
| `namespace` | The Kubernetes namespace that the operator will be deployed in.  It is recommended that a namespace be created for the operator rather than using the `default` namespace.| `weblogic-operator` |
| `remoteDebugNodePortEnabled` | Controls whether or not the operator will start a Java remote debug server on the provided port and suspend execution until a remote debugger has attached. | `false` |
| `serviceAccount`| The name of the service account that the operator will use to make requests to the Kubernetes API server. | `weblogic-operator` |
| `targetNamespaces` | A list of the Kubernetes namespaces that may contain WebLogic domains that the operator will manage.  The operator will not take any action against a domain that is in a namespace not listed here. | `default` |
| `weblogicOperatorImage` | The Docker image containing the operator code. | `container-registry.oracle.com/middleware/weblogic-kubernetes-operator:latest` |
| `weblogicOperatorImagePullPolicy` | The image pull policy for the operator Docker image.  Allowed values are: `Always`, `Never` and `IfNotPresent` | `IfNotPresent` |
| `weblogicOperatorImagePullSecretName` | Name of the Kubernetes secret to access the Docker Store to pull the WebLogic Server Docker image.  The presence of the secret will be validated when this parameter is enabled. | |

Review the default values to see if any need to be updated to reflect the target environment.  If so, then make a copy of the input file and modify it.  Otherwise, you can use the default input file.

## Decide which REST configuration to use

The operator provides three REST certificate options:

*	`NONE` Disables the REST server.
*	`SELF_SIGNED_CERT` Generates self-signed certificates.
*	`CUSTOM_CERT` Provides a mechanism to provide certificates that were created and signed by some other means.

## Decide which options to enable

The operator provides some optional features that can be enabled in the configuration file.

### Load balancing with an Ingress controller or a web server

You can choose a load balancer provider for your WebLogic domains running in a Kubernetes cluster. Please refer to [Load balancing with Voyager/HAProxy](voyager.md), [Load balancing with Traefik](traefik.md), and [Load balancing with the Apache HTTP Server](apache.md) for information about the current capabilities and setup instructions for each of the supported load balancers.

Note these limitations:

*	Only HTTP(S) is supported. Other protocols are not supported.
*	A root path rule is created for each cluster.  Rules based on the DNS name, or on URL paths other than ‘/’, are not supported.
*	No non-default configuration of the load balancer is performed in this release.  The default configuration gives round robin routing and WebLogic Server will provide cookie-based session affinity.

Note that Ingresses are not created for servers that are not part of a WebLogic cluster, including the Administration Server.  Such servers are exposed externally using NodePort services.

### Log integration with Elastic Stack

The operator can install the Elastic Stack and publish its logs into it.  If enabled, Elasticsearch and Kibana will be installed in the `default` namespace, and a Logstash container will be created in the operator pod.  Logstash will be configured to publish the operator’s logs into Elasticsearch, and the log data will be available for visualization and analysis in Kibana.

To enable the Elastic Stack integration, set the `elkIntegrationEnabled` option to `true`.

## Deploying the operator to a Kubernetes cluster

At this point, you've created a custom inputs file, or you've decided to use the default one.

Next, choose and create a directory that generated operator-related files will be stored in, for example, `/path/to/weblogic-operator-output-directory`.

Finally, run the operator installation script to deploy the operator, pointing it at your inputs file and your output directory:

```
./create-weblogic-operator.sh \
  -i /path/to/create-weblogic-operator-inputs.yaml \
  -o /path/to/weblogic-operator-output-directory
```

## What the script does

The script will carry out the following actions:

*	Create a directory for the generated Kubernetes YAML files for this operator.  The pathname is `/path/to/weblogic-operator-output-directory/weblogic-operators/<namespace parameter from create-weblogic-operator-inputs.yaml`.
*	A set of Kubernetes YAML files will be created from the inputs provided in this directory.
*	A namespace will be created for the operator.
*	A service account will be created in that namespace.
*	A set of RBAC roles and bindings will be created.
*	The operator will be deployed.
*	If requested, the load balancer will be deployed.
*	If requested, Elastic Stack will be deployed and Logstash will be configured for the operator’s logs.

The script will validate each action before it proceeds.

## YAML files created during the deployment of the operator

The script will create a YAML file called `weblogic-operator.yaml`.  An example of this file is shown below.  This file can be kept for later use.  Developers or advanced users may wish to hand edit this file.

```
# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
#  This is a template YAML file to deploy the Kubernetes Operator for WebLogic.
#

apiVersion: apps/v1beta1 # for versions before 1.6.0 use extensions/v1beta1
kind: Deployment
metadata:
  name: weblogic-operator
  # set the namespace that you want the operator deployed in here
  namespace: weblogic-operator
spec:
  replicas: 1
  template:
    metadata:
      labels:
        app: weblogic-operator
```

The spec section provides details for the container that the operator will execute in.

```
    spec:
      serviceAccountName: weblogic-operator
      containers:
      - name: weblogic-operator
        image: container-registry.oracle.com/middleware/weblogic-operator:latest
        imagePullPolicy: IfNotPresent
        command: ["bash"]
        args: ["/operator/operator.sh"]
        env:
        - name: OPERATOR_NAMESPACE
          valueFrom:
            fieldRef:
              fieldPath: metadata.namespace
        - name: OPERATOR_VERBOSE
          value: "false"
        # If you wish to enable remote debugging, uncomment the following lines and set
        # the value to the port number you want to use - note that you will also need to
        # update the Service definition to expose this port
        # - name: REMOTE_DEBUG_PORT
        #   value: "30999"
        - name: JAVA_LOGGING_LEVEL
          value: "INFO"
        volumeMounts:
        - name: weblogic-operator-cm-volume
          mountPath: /operator/config
        - name: weblogic-operator-secrets-volume
          mountPath: /operator/secrets
          readOnly: true
        # uncomment this mount if using the ELK integration:
        # - mountPath: /logs
        #   name: log-dir
        livenessProbe:
          exec:
            command:
              - bash
              - '/operator/livenessProbe.sh'
          initialDelaySeconds: 120
          periodSeconds: 5
      volumes:
      - name: weblogic-operator-cm-volume
        configMap:
          name: weblogic-operator-cm
      - name: weblogic-operator-secrets-volume
        secret:
          weblogicCredentialsSecretName: weblogic-operator-secrets
      # Uncomment this volume if using ELK integration:
      # - name: log-dir
      #   emptyDir:
      #    medium: Memory
      # Update the imagePullSecrets with the name of your Kubernetes secret containing
      # your credentials for the Docker Store/Oracle Container Registry.
      imagePullSecrets:
      - name: ocr-secret
```

This section defines the external service that provides access to the operator to clients outside the Kubernetes cluster.  The service exposes one port for HTTPS access to the operator’s REST server.  It also includes a commented-out definition for the debug port.

[comment]: # (Debugging the operator is described in the [developer guide] developer.md )

```
---
apiVersion: v1
kind: Service
metadata:
  name: external-weblogic-operator-svc
  namespace: weblogic-operator
spec:
  type: NodePort
  selector:
    app: weblogic-operator
  ports:
    - port: 8081
      nodePort: 31001
      name: rest
    # - port: 30999
    #   nodePort: 30999
    #   name: debug
```

This section defines a service that provides access to the operator’s REST server inside the Kubernetes cluster.  This is used when something inside the cluster wants to initiate a scaling request, a WLDF action, or a Prometheus alert, for example.

```
---
apiVersion: v1
kind: Service
metadata:
  name: internal-weblogic-operator-svc
  namespace: weblogic-operator
spec:
  type: ClusterIP
  selector:
    app: weblogic-operator
  ports:
    - port: 8082
      name: rest
```

This section creates a ConfigMap that passes configuration information to the operator including the list of namespaces in which the operator will manage domains, the service account that the operator will use to authenticate to the Kubernetes API server, and the certificates that the operator’s REST server will use.

```
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: weblogic-operator-cm
  namespace: weblogic-operator
data:
  serviceaccount: weblogic-operator
  targetNamespaces: "domain1,domain2"
  externalOperatorCert: LS0tL (truncated) 0tLQo=
  internalOperatorCert: LS0tL (truncated) 0tLS0K
```

This section defines a secret that contains the keys needed by the operator for its REST server.

```
---
apiVersion: v1
kind: Secret
metadata:
  name: weblogic-operator-secrets
  namespace: weblogic-operator
type: Opaque
data:
  externalOperatorKey: QmFnI (truncated) 0tCg==
  internalOperatorKey: QmFnI (truncated) 0tCg==
```


## Verifying the operator deployment

The script validates that each step is successful before continuing. However, it may still be desirable to perform manual validation, particularly the first time the operator is deployed, in order to become more familiar with the various artifacts that are created.  This section provides details on how to verify the operator deployment.

Issue the following command to check that the operator is deployed:

```
kubectl -n NAMESPACE get all
```

Replace `NAMESPACE` with the namespace the operator is deployed in.

Copy the pod name from the output of the previous command, and use the following command to obtain the logs of the operator:

```
kubectl -n NAMESPACE logs POD_NAME
```

To tail the logs continuously, add a `-f` flag after logs.

The logs should show the operator startup messages, including messages about starting the REST server and checking for WebLogic domains.

Check that the custom resource definition was created by entering this command:

```
kubectl get crd
```

The output should include `domains.weblogic.oracle`.
