# Oracle WebLogic Server Kubernetes Operator

Built with [Wercker](http://www.wercker.com)

[![wercker status](https://app.wercker.com/status/68ce42623fce7fb2e52d304de8ea7530/m/develop "wercker status")](https://app.wercker.com/project/byKey/68ce42623fce7fb2e52d304de8ea7530)

Many organizations are exploring, testing, or actively moving application workloads into a cloud environment, either in house or using an external cloud provider.  Kubernetes has emerged as a leading cloud platform and is seeing widespread adoption.  But a new computing model does not necessarily mean new applications or workloads; many of the existing application workloads running in environments designed and built over many years, before the ‘cloud era’, are still mission critical today.  As such, there is a lot of interest in moving such workloads into a cloud environment, like Kubernetes, without forcing application rewrites, retesting, and additional process and cost.  There is also a desire to not just run the application in the new environment, but to run it ‘well’ – to adopt some of the idioms of the new environment and to realize some of the benefits of that new environment.

Oracle has been working with the WebLogic community to find ways to make it as easy as possible for organizations using WebLogic Server to run important workloads, to move those workloads into the cloud.  One aspect of that effort is the creation of the Oracle WebLogic Server Kubernetes Operator.  This release of the Operator provides a number of features to assist with the management of WebLogic domains in a Kubernetes environment, including:

*	A mechanism to create a WebLogic domain on a Kubernetes persistent volume. This persistent volume can reside in NFS.
*	A mechanism to define a WebLogic domain as a Kubernetes resource (using a Kubernetes custom resource definition).
*	The ability to automatically start servers based on declarative startup parameters and desired states.
* The ability to manage a WebLogic configured or dynamic cluster.
*	The ability to automatically expose the WebLogic Server Administration Console outside the Kubernetes cluster (if desired).
*	The ability to automatically expose T3 channels outside the Kubernetes domain (if desired).
*	The ability to automatically expose HTTP paths on a WebLogic domain outside the Kubernetes domain with load balancing, and to update the load balancer when Managed Servers in the WebLogic domain are started or stopped.
*	The ability to scale a WebLogic domain by starting and stopping Managed Servers on demand, or by integrating with a REST API to initiate scaling based on WLDF, Prometheus/Grafana, or other rules.
*	The ability to publish Operator and WebLogic Server logs into Elasticsearch and interact with them in Kibana.

For that reason, Oracle has developed the [WebLogic Server Kubernetes Operator](https://oracle.github.io/weblogic-kubernetes-operator).