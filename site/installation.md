# Installation

## Register for access to the Oracle Container Registry

The *operator* Docker images are hosted in the Oracle Container Registry.  Before downloading the images, users must register for access to the registry by visiting [https://container-registry.oracle.com](https://container-registry.oracle.com) and clicking on the Register link.

**ATTENTION EARLY ACCESS USERS** You will not need to register for Oracle Container Registry for early access.

## Setting up secrets to access the Oracle Container Registry

In order to obtain the *operator* Docker image from the Oracle Container Registry, which requires authentication, a Kubernetes secret containing the registry credentials must be created. To create a secret with Oracle Container Registry credentials, issue the following command:

```
kubectl create secret docker-registry SECRET_NAME
  -n NAMESPACE  
  --docker-server=container-registry.oracle.com
  --docker-username=YOUR_USERNAME
  --docker-password=YOUR_PASSWORD
  --docker-email=YOUR_EMAIL
```
**ATTENTION EARLY ACCESS USERS** You will need to use the early access image in quay.io.  
Please create your secret as shown below:

```
kubectl create secret docker-registry earlybird-secret
  -n NAMESPACE
  --docker-server=quay.io
  --docker-username=earlybird
  --docker-password=welcome1
  --docker-email=mark.x.nelson@gmail.com
```


In this command, replace the uppercase items with the appropriate values. The `SECRET_NAME` will be needed in later parameter files.  The `NAMESPACE` must match the namespace where the operator will be deployed.

## Customizing the operator parameters file

The *operator* is deployed with the provided installation script (`create-weblogic-operator.sh`).  The input to this script is the file `create-operator-inputs.yaml`, which needs to updated to reflect the target environment.  

The following parameters must be provided in the input file:

### CONFIGURATION PARAMETERS FOR THE OPERATOR

| Parameter	| Definition	| Default |
| --- | --- | --- |
| externalOperatorCert	| A base64 encoded string containing the X.509 certificate that the *operator* will present to clients accessing its REST endpoints. This value is only used when `externalRestOption` is set to custom-cert. | |
| externalOperatorKey	| A base64 encoded string containing the private key **ask tom** This value is only used when externalRestOption is set to custom-cert. | |
| externalRestOption	| Write me.  Allowed values: <br/>- `none` Write me <br/>- `self-signed-cert` The *operator* will use a self-signed certificate for its REST server.  If this value is specified, then the `externalSans` parameter must also be set. <br/>- `custom-cert` Write me. If this value is specified, then the `externalOperatorCert` and `externalOperatorKey` must also be provided.	| none |
| externalSans	| A comma-separated list of Subject Alternative Names that should be included in the X.509 Certificate.  This list should include ... <br/>Example:  `DNS:myhost,DNS:localhost,IP:127.0.0.1` | |
| namespace	| The Kubernetes *namespace* that the *operator* will be deployed in.  It is recommended that a *namespace* be created for the *operator* rather than using the `default` namespace.	| weblogic-operator |
| targetNamespaces	| A list of the Kubernetes *namespaces* that may contain WebLogic *domains* that the *operator* will manage.  The *operator* will not take any action against a *domain* that is in a *namespace* not listed here.	| default |
| remoteDebugNodePort	| Tom is adding a debug on/off parameter <br/>If the debug parameter if set to on, then the *operator* will start a Java remote debug server on the provided port and will suspend execution until a remote debugger has attached.	| 30999 |
| restHttpsNodePort	| The NodePort number that should be allocated for the *operator* REST server should listen for HTTPS requests on. 	| 31001 |
| serviceAccount	| The name of the *service account* that the *operator* will use to make requests to the Kubernetes API server. |	weblogic-operator |
| loadBalancer	| Determines which load balancer should be installed to provide load balancing for WebLogic *clusters*.  Allowed values are:<br/>-	`none` – do not configure a load balancer<br/>- `traefik` – configure the Traefik *ingress* provider<br/>- `nginx` – reserved for future use<br/>- `ohs` – reserved for future use |	traefik |
| loadBalancerWebPort	| The NodePort for the load balancer to accept user traffic. |	30305 |
| enableELKintegration	| Determines whether the ELK integration will be enabled.  If set to `true`, then ElasticSearch, Logstash and Kibana will be installed, and Logstash will be configured to export the *operator’s* logs to ElasticSearch. |	false |

## Decide which REST configuration to use

The *operator* provides three REST certificate options:

*	`none` will disable the REST server.
*	`self-signed-cert` will generate self-signed certificates.
*	`custom-cert` provides a mechanism to provide certificates that were created and signed by some other means.

## Decide which options to enable

The *operator* provides some optional features that can be enabled in the configuration file.

### Load Balancing

The *operator* can install the Traefik *ingress* provider to provide load balancing for web applications running in WebLogic *clusters*.  If enabled, an instance of Traefik and an *ingress* will be created for each WebLogic *cluster*.  Additional configuration is performed when creating the *domain*.

Note that the Technology Preview release provides only basic load balancing:

*	Only HTTP(S) is supported, other protocols are not supported.
*	A root path rule is created for each cluster.  Rules based on the DNS name, or on URL paths other than ‘/’ are not supported.
*	No non-default configuration of the load balancer is performed in this release.  The default configuration gives round robin routing and WebLogic will provide cookie-based session affinity.

Note that ingresses are not created for servers that are not part of a WebLogic cluster, including the administration server.  Such servers are exposed externally using NodePort services.

### Log integration with ELK

The *operator* can install the ELK stack and publish its logs into ELK.  If enabled, ElasticSearch and Kibana will be installed in the `default` *namespace*, and a Logstash pod will be created in the *operator’s namespace*.  Logstash will be configured to publish the *operator’s* logs into Elasticsearch, and the log data will be available for visualization and analysis in Kibana.

To enable the ELK integration, set the `enableELKintegration` option to `true`.

### Metrics integration with Prometheus

Write me

## Deploying the operator to a Kubernetes cluster

To deploy the *operator*, run the deployment script and give it the location of your inputs file:

```
./create-weblogic-operator.sh –i /path/to/create-operator-inputs.yaml
```

## What the script does

The script will carry out the following actions:

*	A set of Kubernetes YAML files will be created from the inputs provided.
*	A *namespace* will be created for the *operator*.
*	A *service account* will be created in that *namespace*.
*	If ELK integration was enabled, a *persistent volume* for ELK will be created.
*	A set of RBAC *roles* and *bindings* will be created.
*	The *operator* will be deployed.
*	If requested, the load balancer will be deployed.
*	If requested, ELK will be deployed and logstash will be configured for the *operator’s* logs.

The script will validate each action before it proceeds.

## Common problems

Write me

### Could not pull Docker image

If the *operator* has not started, Kubernetes may not have been able to pull the Docker image from the Docker registry.  Check the Docker registry *secret* is correct, or log on to the Kubernetes master and manually pull the image using these commands:

```
docker login container-registry.oracle.com
docker pull container-registry.oracle.com/middleware/weblogic-operator:latest
```

### Secret not created or in the wrong namespace

Write me

### Persistent Volume not created or has wrong permissions

Write me

### Failed to mount shared volumes

X elk..

## YAML files created during the deployment of the operator

The script will create a YAML file called `weblogic-operator.yaml`.  An example of this file is shown below.  This file can be kept for later use.  Developers or advanced users may wish to hand-edit this file.

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
The spec section provides details for the container that the operator will execute in.
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
        - name: operator-config-volume
          mountPath: /operator/config
        - name: operator-secrets-volume
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
      - name: operator-config-volume
        configMap:
          name: operator-config-map
      - name: operator-secrets-volume
        secret:
          secretName: operator-secrets
      # Uncomment this volume if using ELK integration:
      # - name: log-dir
      #   persistentVolumeClaim:
      #     claimName: elk-pvc
      # Update the imagePullSecrets with the name of your Kubernetes secret containing
      # your credentials for the Docker Store/Oracle Container Registry.
      imagePullSecrets:
      - name: ocr-secret
```

This section defines the external service that provides access to the operator to clients outside the Kubernetes cluster.  The service exposes one port for HTTPS access to the operator’s REST server.  It also includes a commented-out definition for the debug port.  Debugging the operator is described in the “Developer Guide” section of this document.

```
---
apiVersion: v1
kind: Service
metadata:
  name: external-weblogic-operator-service
  namespace: weblogic-operator
spec:
  type: NodePort
  selector:
    app: weblogic-operator
  ports:
    - port: 8081
      nodePort: 31001
      name: rest-https
    # - port: 30999
    #   nodePort: 30999
    #   name: debug
```

This section defines a service that provides access to the operator’s REST server inside the Kubernetes cluster.  This is used when something inside the cluster wants to initiate a scaling request, a WLDF action or Prometheus alert for example.

```
---
apiVersion: v1
kind: Service
metadata:
  name: internal-weblogic-operator-service
  namespace: weblogic-operator
spec:
  type: ClusterIP
  selector:
    app: weblogic-operator
  ports:
    - port: 8082
      name: rest-https
```

This section creates a ConfigMap that passes configuration information to the operator including the list of namespaces in which the operator will manage domains, the service account that the operator will use to authenticate to the Kubernetes API server, and the certificates that the operator’s REST server will use.

```
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: operator-config-map
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
  name: operator-secrets
  namespace: weblogic-operator
type: Opaque
data:
  externalOperatorKey: QmFnI (truncated) 0tCg==
  internalOperatorKey: QmFnI (truncated) 0tCg==
```

write something

## Verifying the operator deployment

The script does validate each step was successful before continuing, however it may still be desirable to perform manual validation, particularly the first time the *operator* is deployed, in order to become more familiar with the various artifacts that are created.  This section provides details on how to verify the operator deployment.

Issue the following command to check the operator is deployed:

```
kubectl -n NAMESPACE get all
```

Replace `NAMESPACE` with the *namespace* the *operator* is deployed in.

Copy the *pod* name from the output of the previous command, and use the following command to obtain the logs of the *operator*:

```
kubectl -n NAMESPACE logs POD_NAME
```

To tail the logs continuously, add a `-f` flag after logs.

The logs should show the operator startup messages, including messages about starting the REST server and checking for WebLogic domains.

Check that the custom resource definition was created with this command:

```
kubectl get crd
```

The output should include `domains.weblogic.oracle`.
