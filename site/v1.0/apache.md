> **WARNING** This documentation is for version 1.0 of the operator.  To view documenation for the current release, [please click here](/site).


# Load balancing with the Apache HTTP Server

This document describes how to set up and start an Apache HTTP Server for load balancing inside a Kubernetes cluster. The configuration and startup can be either automatic, when you create a domain using the WebLogic Operator's `create-weblogic-domain.sh` script, or manual, if you have an existing WebLogic domain configuration.

## Build the Docker image for the Apache HTTP Server

You need to build the Docker image for the Apache HTTP Server that embeds the Oracle WebLogic Server Proxy Plugin.

  1. Download and build the Docker image for the Apache HTTP Server with the 12.2.1.3.0 Oracle WebLogic Server Proxy Plugin.  See the instructions in [Apache HTTP Server with Oracle WebLogic Server Proxy Plugin on Docker](https://github.com/oracle/docker-images/tree/master/OracleWebLogic/samples/12213-webtier-apache).

  2. Tag your Docker image, `store/oracle/apache:12.2.1.3`, using the `docker tag` command.

```

     $ docker tag 12213-apache:latest store/oracle/apache:12.2.1.3

```

For more information about the Apache plugin, see [Apache HTTP Server with Oracle WebLogic Server Proxy Plugin on Docker](https://docs.oracle.com/middleware/1213/webtier/develop-plugin/apache.htm#PLGWL395).

After you have access to the Docker image of the Apache HTTP Server, you can follow the instructions below to set up and start the Kubernetes resources for the Apache HTTP Server.


## Use the Apache load balancer with a WebLogic domain created with the WebLogic Operator

For how to create a domain with the WebLogic Operator, please refer to [Creating a domain using the WebLogic Operator](creating-domain.md).

You need to configure the Apache HTTP Server as your load balancer for a WebLogic domain by setting the `loadBalancer` option to `APACHE` in the `create-weblogic-domain-inputs.yaml` (as shown below) when running the `create-weblogic-domain.sh` script to create a domain.

```

# Load balancer to deploy.  Supported values are: VOYAGER, TRAEFIK, APACHE, NONE

loadBalancer: APACHE

```

The `create-weblogic-domain.sh` script installs the Apache HTTP Server with the Oracle WebLogic Server Proxy Plugin into the Kubernetes *cluster*  in the same namespace as the *domain*.

The Apache HTTP Server will expose a `NodePort` that allows access to the load balancer from outside of the Kubernetes cluster.  The port is configured by setting `loadBalancerWebPort` in the `create-weblogic-domain-inputs.yaml` file.

```

# Load balancer web port

loadBalancerWebPort: 30305

```

Users can access an application from outside of the Kubernetes cluster by using `http://<host>:30305/<application-url>`.

### Use the default plugin WL module configuration

By default, the Apache Docker image supports a simple WebLogic Server proxy plugin configuration for a single WebLogic domain with an Administration Server and a cluster. The `create-weblogic-domain.sh` script automatically customizes the default behavior based on your domain configuration by generating a customized Kubernetes resources YAML file for Apache named `weblogic-domain-apache.yaml`. The default setting supports only the type of load balancing that uses the root path ("/"). You can further customize the root path of the load balancer with the `loadBalancerAppPrepath` property in the `create-weblogic-domain-inputs.yaml` file.

```

# Load balancer app prepath

loadBalancerAppPrepath: /weblogic

```

It is sometimes, but rarely, desirable to expose a WebLogic Administration Server host and port through a load balancer to a public network.  If this is needed, then, after the `weblogic-domain-apache.yaml` file is generated, you can customize exposure of the WebLogic Administration Server host and port by uncommenting the `WEBLOGIC_HOST` and `WEBLOGIC_PORT` environment variables in the file.   If this file's resources have already been deployed (as happens automatically when running `create-weblogic-domain.sh`), one way to make the change is to delete the file's running Kubernetes resources using `kubectl delete -f weblogic-domain-apache.yaml`, and then deploy them again via `kubectl create -f weblogic-domain-apache.yaml`.

Users can then access an application from outside of the Kubernetes cluster by using `http://<host>:30305/weblogic/<application-url>,` and, if the WebLogic Administration Server host and port environment variables are uncommented below, an adminstrator can access the Administration Console using `http://<host>:30305/console`.

The generated Kubernetes YAML files look like the following, given the `domainUID`, "`domain1`".

Sample `weblogic-domain-apache.yaml` file for Apache HTTP Server deployment.

```
--
apiVersion: v1
kind: ServiceAccount
metadata:
  name: domain1-apache-webtier
  namespace: default
  labels:
    weblogic.domainUID: domain1
    weblogic.domainName: base_domain
    app: apache-webtier
---
kind: Deployment
apiVersion: extensions/v1beta1
metadata:
  name: domain1-apache-webtier
  namespace: default
  labels:
    weblogic.domainUID: domain1
    weblogic.domainName: base_domain
    app: apache-webtier
spec:
  replicas: 1
  selector:
    matchLabels:
      weblogic.domainUID: domain1
      weblogic.domainName: base_domain
      app: apache-webtier
  template:
    metadata:
      labels:
        weblogic.domainUID: domain1
        weblogic.domainName: base_domain
        app: apache-webtier
    spec:
      serviceAccountName: domain1-apache-webtier
      terminationGracePeriodSeconds: 60
      # volumes:
      # - name: "domain1-apache-webtier"
      #   hostPath:
      #     path: %LOAD_BALANCER_VOLUME_PATH%
      containers:
      - name: domain1-apache-webtier
        image: store/oracle/apache:12.2.1.3
        imagePullPolicy: Never
        # volumeMounts:
        # - name: "domain1-apache-webtier"
        #   mountPath: "/config"
        env:
          - name: WEBLOGIC_CLUSTER
            value: 'domain1-cluster-cluster-1:8001'
          - name: LOCATION
            value: '/weblogic'
          #- name: WEBLOGIC_HOST
          #  value: 'domain1-admin-server'
          #- name: WEBLOGIC_PORT
          #  value: '7001'
        readinessProbe:
          tcpSocket:
            port: 80
          failureThreshold: 1
          initialDelaySeconds: 10
          periodSeconds: 10
          successThreshold: 1
          timeoutSeconds: 2
        livenessProbe:
          tcpSocket:
            port: 80
          failureThreshold: 3
          initialDelaySeconds: 10
          periodSeconds: 10
          successThreshold: 1
          timeoutSeconds: 2
---
apiVersion: v1
kind: Service
metadata:
  name: domain1-apache-webtier
  namespace: default
  labels:
    weblogic.domainUID: domain1
    weblogic.domainName: base_domain
spec:
  type: NodePort
  selector:
    weblogic.domainUID: domain1
    weblogic.domainName: base_domain
    app: apache-webtier
  ports:
    - port: 80
      nodePort: 30305
      name: rest-https
```

Sample `weblogic-domain-apache-security.yaml` file for associated RBAC roles and role bindings.

```
---
kind: ClusterRole
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  name: domain1-apache-webtier
  labels:
    weblogic.domainUID: domain1
    weblogic.domainName: base_domain
rules:
  - apiGroups:
      - ""
    resources:
      - pods
      - services
      - endpoints
      - secrets
    verbs:
      - get
      - list
      - watch
  - apiGroups:
      - extensions
    resources:
      - ingresses
    verbs:
      - get
      - list
      - watch
---
kind: ClusterRoleBinding
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  name: domain1-apache-webtier
  labels:
    weblogic.domainUID: domain1
    weblogic.domainName: base_domain
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: domain1-apache-webtier
subjects:
- kind: ServiceAccount
  name: domain1-apache-webtier
  namespace: default
```

Here are examples of the Kubernetes resources created by the WebLogic Operator:

```
NAME                            DESIRED   CURRENT   UP-TO-DATE   AVAILABLE   AGE
bash-4.2$ kubectl get all |grep apache
deploy/domain1-apache-webtier   1         1         1            1           2h
rs/domain1-apache-webtier-7b8b789797   1         1         1         2h
deploy/domain1-apache-webtier   1         1         1            1           2h
rs/domain1-apache-webtier-7b8b789797   1         1         1         2h
po/domain1-apache-webtier-7b8b789797-lm45z   1/1       Running   0          2h
svc/domain1-apache-webtier                      NodePort    10.111.114.67    <none>        80:30305/TCP      2h

bash-4.2$ kubectl get clusterroles |grep apache
domain1-apache-webtier                                                 2h

bash-4.2$ kubectl get clusterrolebindings |grep apache
domain1-apache-webtier                                    2h
```

### Use your own plugin WL module configuration

You can fine tune the behavior of the Apache plugin by providing your own Apache plugin configuration. You put your `custom_mod_wl_apache.conf` file in a local directory, for example, `<host-config-dir>` , and specify this location in the `create-weblogic-domain-inputs.yaml` file as follows:

```
# Docker volume path for APACHE
# By default, the VolumePath is empty, which will cause the volume mount be disabled
loadBalancerVolumePath: <host-config-dir>
```

After the `loadBalancerVolumePath` property is specified, the `create-weblogic-domain.sh` script will use the `custom_mod_wl_apache.conf` file in `<host-config-dir>` directory to replace what is in the Docker image.

The generated YAML files will look similar except with un-commented entries like below:

```
      volumes:
      - name: "domain1-apache-webtier"
        hostPath:
          path: <host-config-dir>
      containers:
      - name: domain1-apache-webtier
        image: store/oracle/apache:12.2.1.3
        imagePullPolicy: Never
        volumeMounts:
        - name: "domain1-apache-webtier"
          mountPath: "/config"
```

## Use the Apache load balancer with a manually created WebLogic Domain

If your WebLogic domain is not created by the WebLogic Operator, you need to manually create and start all Kubernetes' resources for the Apache HTTP Server.


  1. Create your own `custom_mod_wl_apache.conf` file, and put it in a local directory, for example, `<host-conf-dir>`. See the instructions in [Apache Web Server with Oracle WebLogic Server Proxy Plugin on Docker](https://docs.oracle.com/middleware/1213/webtier/develop-plugin/apache.htm#PLGWL395).

  2. Create the Apache deployment YAML file. See the example above. Note that you need to use the **volumes** and **volumeMounts** to mount `<host-config-dir>` into the `/config` directory inside the pod that runs the Apache web tier. Note that the Apache HTTP Server needs to be in the same Kubernetes namespace as the WebLogic domain that it needs to access.

  3. Create a RBAC YAML file. See the example above.

Note that you can choose to run one Apache HTTP Server to balance the loads from multiple domains/clusters inside the same Kubernetes cluster, as long as the Apache HTTP Server and the domains are all in the same namespace.
