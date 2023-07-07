# WebLogic Kubernetes Operator

The WebLogic Kubernetes Operator (the “operator”) supports running your WebLogic Server and Fusion Middleware Infrastructure domains on Kubernetes, an industry standard, cloud neutral deployment platform. It lets you encapsulate your entire WebLogic Server installation and layered applications into a portable set of cloud neutral images and simple resource description files. You can run them on any on-premises or public cloud that supports Kubernetes where you've deployed the operator.

Furthermore, the operator is well suited to CI/CD processes. You can easily inject changes when moving between environments, such as from test to production. For example, you can externally inject database URLs and credentials during deployment or you can inject arbitrary changes to most WebLogic configurations.

The operator takes advantage of the [Kubernetes operator pattern](https://kubernetes.io/docs/concepts/extend-kubernetes/operator/), which means that it uses Kubernetes APIs to provide support for operations, such as: provisioning, lifecycle management, application versioning, product patching, scaling, and security. The operator also enables the use of tooling that is native to this infrastructure for monitoring, logging, tracing, and security.

You can:
* Deploy an operator that manages all WebLogic domains in all namespaces in a Kubernetes cluster, or that only manages domains in a specific subset of the namespaces, or that manages only domains that are located in the same namespace as the operator. At most, a namespace can be managed by one operator.
* Supply WebLogic domain configuration using:
  * _Model in Image_: Includes [WebLogic Deploy Tooling](https://github.com/oracle/weblogic-deploy-tooling) models and archives in a container image.
  * _Domain in Image_: Includes a WebLogic domain home in a container image.
  * _Domain on PV_: Locates WebLogic domain homes in a Kubernetes PersistentVolume (PV). This PV can reside in an NFS file system or other Kubernetes volume types.
* Configure deployment of WebLogic domains as Kubernetes resources (using Kubernetes custom resource definitions).
* Override certain aspects of the WebLogic domain configuration; for example, use a different database password for different deployments.
* Start and stop servers and clusters in the domain based on declarative startup parameters and desired states.
* Scale WebLogic domains by starting and stopping Managed Servers on demand, Kubernetes scale commands, setting up a Kubernetes Horizontal Pod Autoscaler, or by integrating with a REST API to initiate scaling based on the WebLogic Diagnostics Framework (WLDF), Prometheus, Grafana, or other rules.
* Expose HTTP paths on a WebLogic domain outside the Kubernetes domain with load balancing, and automatically update the load balancer when Managed Servers in the WebLogic domain are started or stopped.
* Expose the WebLogic Server Administration Console outside the Kubernetes cluster, if desired.
* Expose T3 channels outside the Kubernetes domain, if desired.
* Publish operator and WebLogic Server logs into Elasticsearch and interact with them in Kibana.


The fastest way to experience the operator is to follow the [Quick Start guide](https://oracle.github.io/weblogic-kubernetes-operator/quickstart/), or you can peruse our [documentation](https://oracle.github.io/weblogic-kubernetes-operator), read our [blogs](https://blogs.oracle.com/weblogicserver/how-to-weblogic-server-on-kubernetes), or try out the [samples](https://oracle.github.io/weblogic-kubernetes-operator/samples/).

## Documentation

Documentation for the operator is [available here](https://oracle.github.io/weblogic-kubernetes-operator).

This documentation includes information for users and for developers.  It provides samples, reference material, security
information and a [Quick Start](https://oracle.github.io/weblogic-kubernetes-operator/quickstart/) guide if you just want to get up and running quickly.

Documentation for prior releases of the operator: [3.4](https://oracle.github.io/weblogic-kubernetes-operator/3.4/) and [4.0](https://oracle.github.io/weblogic-kubernetes-operator/4.0/).

## Backward compatibility guidelines

The 2.0 release introduced some breaking changes and did not maintain compatibility with previous releases.

Starting with the 2.0.1 release, operator releases are intended to be backward compatible with respect to the domain
resource schema, operator Helm chart input values, configuration overrides template, Kubernetes resources created
by the operator Helm chart, Kubernetes resources created by the operator, and the operator REST interface. We intend to
maintain compatibility for three releases, except in the case of a clearly communicated deprecated feature, which will be
maintained for one release after a replacement is available.

## Need more help? Have a suggestion? Come and say, "Hello!"

We have a **public Slack channel** where you can get in touch with us to ask questions about using the operator or give us feedback
or suggestions about what features and improvements you would like to see.  We would love to hear from you. To join our channel,
please [visit this site to get an invitation](https://join.slack.com/t/oracle-weblogic/shared_invite/zt-1lnz4kpci-WdY2gWfeJc5jS_a_1Z06MA).  The invitation email will include
details of how to access our Slack workspace.  After you are logged in, please come to `#operator` and say, "hello!"

## Contributing

This project welcomes contributions from the community. Before submitting a pull request, please [review our contribution guide](./CONTRIBUTING.md)

## Security

Please consult the [security guide](./SECURITY.md) for our responsible security vulnerability disclosure process.

## License

Copyright (c) 2017, 2022 Oracle and/or its affiliates.

Released under the Universal Permissive License v1.0 as shown at
<https://oss.oracle.com/licenses/upl/>.
