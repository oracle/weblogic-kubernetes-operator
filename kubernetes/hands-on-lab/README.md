# Oracle WebLogic Server Kubernetes Operator Tutorial #

### Deploy a WebLogic domain (*Domain-home-in-image*) on Kubernetes using the Oracle WebLogic Server Kubernetes Operator  ###

This lab shows you how to deploy and run a WebLogic domain container-packaged web application on a Kubernetes cluster using [Oracle WebLogic Server Kubernetes Operator 2.5](https://github.com/oracle/weblogic-kubernetes-operator) (the "operator").

The sample web application is a simple JSP page which shows WebLogic Server domain MBean attributes to demonstrate operator features.

**Architecture**

A WebLogic domain can be located either in a persistent volume (PV) or in a Docker image. There are advantages to both approaches, and sometimes there are technical limitations of various cloud providers that may make one approach better suited to your needs. See
[Choose a model](https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/choosing-a-model/).

This tutorial uses the Docker image with the WebLogic domain inside the image deployment. This means that all the artifacts and domain-related files are stored within the image. There is no central, shared domain folder from the pods. This is similar to the standard installation topology where you distribute your domain to different hosts to scale out Managed Servers. The main difference is that by using a container-packaged WebLogic domain, you don't need to use the pack/unpack mechanism to distribute domain binaries and configuration files between multiple hosts.

![](images/wlsonk8s.domain-home-in-image.png)

In a Kubernetes environment, the operator ensures that only one Administration Server and multiple Managed Servers will run in the domain. An operator is an application-specific controller that extends Kubernetes to create, configure, and manage instances of complex applications. The Oracle WebLogic Server Kubernetes Operator simplifies the management and operation of WebLogic domains and deployments.

Helm is a framework that helps you manage Kubernetes applications, and helm charts help you define and install Helm applications in a Kubernetes cluster.

This tutorial has been tested on the Oracle Cloud Infrastructure Container Engine for Kubernetes (OKE).

### The topics to be covered in this hands-on session are: ###

1. [Set up an Oracle Kubernetes Engine instance on the Oracle Cloud Infrastructure](tutorials/setup.oke.ocishell.md)
2. [Install the WebLogic Server Kubernetes Operator](tutorials/install.operator.ocishell.md)
3. [Install a Traefik software load balancer](tutorials/install.traefik.ocishell.md)
4. [Deploy a WebLogic domain](tutorials/deploy.weblogic_short.ocishell.md)
5. [Scale a WebLogic cluster](tutorials/scale.weblogic.ocishell.md)
6. [Override JDBC Data source parameters](tutorials/override.jdbc.ocishell.md)
7. [Update a deployed application by a rolling restart of the new image](tutorials/update.application_short.ocishell.md)
7. [Assign WebLogic pods to nodes (a scenario simulating a cluster spanning 2 data centers)](tutorials/node.selector.ocishell.md)
8. [Assign WebLogic domain to nodes (a scenario simulating deploying WebLogic domain to only a subset of the cluster)](tutorials/node.selector.license.ocishell.md)
