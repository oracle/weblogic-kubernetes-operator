# Oracle WebLogic Operator Tutorial #

### Deploy Oracle WebLogic Domain (*Domain-home-in-image*) on Kubernetes using Oracle WebLogic Operator  ###

This lab demonstrates how to deploy and run Weblogic Domain container packaged Web Application on Kubernetes Cluster using [Oracle WebLogic Server Kubernetes Operator 2.5](https://github.com/oracle/weblogic-kubernetes-operator).

The demo Web Application is a simple JSP page which shows WebLogic Domain's MBean attributes to demonstrate WebLogic Operator features.

**Architecture**

WebLogic domain can be located either in a persistent volume (PV) or in a Docker image. [There are advantages to both approaches, and there are sometimes technical limitations](https://github.com/oracle/weblogic-kubernetes-operator/blob/2.0/site/domains.md#create-and-manage-weblogic-domains) of various cloud providers that may make one approach better suited to your needs.

This tutorial implements the Docker image with the WebLogic domain inside the image deployment. This means all the artefacts, domain related files are stored within the image. There is no central, shared domain folder from the pods. This is similar to the standard installation topology where you distribute your domain to different host to scale out Managed servers. The main difference is that using container packaged WebLogic Domain you don't need to use pack/unpack mechanism to distribute domain binaries and configuration files between multiple host.

![](images/wlsonk8s.domain-home-in-image.png)

In Kubernetes environment WebLogic Operator ensures that only one Admin and multiple Managed servers will run in the domain. An operator is an application-specific controller that extends Kubernetes to create, configure, and manage instances of complex applications. The Oracle WebLogic Server Kubernetes Operator (the "operator") simplifies the management and operation of WebLogic domains and deployments.

Helm is a framework that helps you manage Kubernetes applications, and helm charts help you define and install Helm applications into a Kubernetes cluster.

This tutorial has been tested on Oracle Cloud Infrastructure Container Engine for Kubernetes (OKE).

### The topics to be covered in this hands-on session are: ###

1. [Setup Oracle Kubernetes Engine instance on Oracle Cloud Infrastructure.](tutorials/setup.oke.ocishell.md)
2. [Install WebLogic Operator](tutorials/install.operator.ocishell.md)
3. [Install Traefik Software Loadbalancer](tutorials/install.traefik.ocishell.md)
4. [Deploy WebLogic Domain](tutorials/deploy.weblogic_short.ocishell.md)
5. [Scaling WebLogic Cluster](tutorials/scale.weblogic.ocishell.md)
6. [Override JDBC Datasource parameters](tutorials/override.jdbc.ocishell.md)
7. [Updating deployed application by rolling restart to the new image](tutorials/update.application_short.ocishell.md)
7. [Assigning WebLogic Pods to Nodes (scenario simulating cluster spanning 2 data center)](tutorials/node.selector.ocishell.md)
8. [Assigning WebLogic Pods to Nodes (scenario simulating licensing only the subset of the cluster)](tutorials/node.selector.license.ocishell.md)
