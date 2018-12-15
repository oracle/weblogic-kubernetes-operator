# Create and manage the operator

An operator is an application-specific controller that extends Kubernetes to create, configure, and manage instances 
of complex applications. The Oracle WebLogic Server Kubernetes Operator follows the standard Kubernetes operator pattern, and 
simplifies the management and operation of WebLogic domains and deployments. 


You can have one or more operators in your Kubernetes cluster that manage one or more WebLogic domains each. 
We provide a Helm chart to manage the installation and configuration of the operator and that is the 
*preferred method* of managing operators.  Detailed instructions are available [here](helm-charts.md).

If, for some reason, you do not want to use Helm, it is still possible to perform a manual 
installation of the operator.  This is *not the recommended approach*, but is fully [documented here](manual-installation.md)
if you wish to use this approach.

## Operator Docker image

You can find the operator image in 
[Docker Hub](https://hub.docker.com/r/oracle/weblogic-kubernetes-operator/).

