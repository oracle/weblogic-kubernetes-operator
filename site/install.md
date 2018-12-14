**TODO** write me -- richard is working on helm install docs too


## Create and manage the operator

An operator is an application-specific controller that extends Kubernetes to create, configure, and manage instances of complex applications. The WebLogic Kubernetes Operator follows the standard Kubernetes Operator pattern, and simplifies the management and operation of WebLogic domains and deployments. You can find the operator image in [Docker Hub](https://hub.docker.com/r/oracle/weblogic-kubernetes-operator/).

In your Kubernetes cluster you can have one or more operators that manage one or more WebLogic domains (running in a cluster). We provide a Helm chart to create the operator. (Point to the documentation and sample that describes how to do the steps below.)


### Starting the operator

* Create the namespace and service account for the operator (See the script and doc that describe how to do these.)
* Edit the operator input YAML
* Start the operator with a Helm chart

### Modifying the operator

(images, RBAC roles, ...)

### Shutting down the operator
(See the operator sample README section that describes how to shutdown and delete all resources for the operator.)

