# Load balancing with the Apache HTTP Server

This document describes how to set up and start an Apache HTTP Server for load balancing inside a Kubernetes cluster. 

## Build the Docker image for the Apache HTTP Server

You need to build the Docker image for the Apache HTTP Server that embeds the Oracle WebLogic Server Proxy Plugin.

* Download and build the Docker image for the Apache HTTP Server with the 12.2.1.3.0 Oracle WebLogic Server Proxy Plugin.  See the instructions in [Apache HTTP Server with Oracle WebLogic Server Proxy Plugin on Docker](https://github.com/oracle/docker-images/tree/master/OracleWebLogic/samples/12213-webtier-apache).

* Tag your Docker image, `store/oracle/apache:12.2.1.3`, using the `docker tag` command.

```
     $ docker tag 12213-apache:latest store/oracle/apache:12.2.1.3
```

For more information about the Apache plugin, see [Apache HTTP Server with Oracle WebLogic Server Proxy Plugin on Docker](https://docs.oracle.com/middleware/12213/webtier/develop-plugin/apache.htm#PLGWL395).

After you have access to the Docker image of the Apache HTTP Server, you can follow the instructions below to set up and start the Kubernetes resources for the Apache HTTP Server.
A graceful restart will take effect on the updated configuration without interrupting the current requests.

## Use the Apache load balancer with a WebLogic domain
In order to start the Apache HTTP Server for your WebLogic domain, you need to create and start all Kubernetes' resources for the Apache HTTP Server.

* Create your own `custom_mod_wl_apache.conf` file, and put it in a local directory, for example, `<host-conf-dir>`. See the instructions in [Apache Web Server with Oracle WebLogic Server Proxy Plugin on Docker](https://docs.oracle.com/middleware/1213/webtier/develop-plugin/apache.htm#PLGWL395).

* Create a Kubernetes deployment to start an Apache load balancer instance. Note that you need to use the **volumes** and **volumeMounts** to mount `<host-config-dir>` into the `/config` directory inside the pod that runs the Apache web tier. Note that the Apache HTTP Server needs to be in the same Kubernetes namespace as the WebLogic domain that it needs to access.

* Create a Kubernetes NodePort service to expose the internal ports to external access.

* Optionally create a RBAC YAML file to setup security for your load balancer. 

See the samples for creating Apache HTTP Server as a load balancer at [Apache load balancer helm chart](../kubernetes/samples/charts/apache-webtier/README.md) and [Apache load balance samples](../kubernetes/samples/charts/apache-samples/README.md).

Note that you can choose to run one Apache HTTP Server to balance the loads from multiple domains/clusters inside the same Kubernetes cluster, as long as the Apache HTTP Server and the domains are all in the same namespace.

## Update the plugin WL module configuration

Users can update or add the new Apache plugin configuration first, then run the following commands to restart the Apache HTTP Server gracefully:

```
     $ kubectl exec -it <apache-http-server-pod-name> bash
     $ httpd -k graceful
```

A graceful restart will take effect on the updated configuration without interrupting the current requests.

