# Create and manage the operator

An operator is an application-specific controller that extends Kubernetes to create, configure, and manage instances 
of complex applications. The Oracle WebLogic Server Kubernetes Operator follows the standard Kubernetes operator pattern, and 
simplifies the management and operation of WebLogic domains and deployments. 

You can have one or more operators in your Kubernetes cluster that manage one or more WebLogic domains each. 
We provide a Helm chart to manage the installation and configuration of the operator.
Detailed instructions are available [here](helm-charts.md).

## Operator Docker image

You can find the operator image in 
[Docker Hub](https://hub.docker.com/r/oracle/weblogic-kubernetes-operator/).

