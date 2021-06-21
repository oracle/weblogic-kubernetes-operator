# WebLogic Kubernetes Operator

The WebLogic Kubernetes Operator (the “operator”) supports running your WebLogic Server and Fusion Middleware Infrastructure domains on Kubernetes, an industry standard, cloud neutral deployment platform. It lets you encapsulate your entire WebLogic Server installation and layered applications into a portable set of cloud neutral images and simple resource description files. You can run them on any on-premises or public cloud that supports Kubernetes where you've deployed the operator.

Furthermore, the operator is well suited to CI/CD processes. You can easily inject changes when moving between environments, such as from test to production. For example, you can externally inject database URLs and credentials during deployment or you can inject arbitrary changes to most WebLogic configurations.

The operator takes advantage of the [Kubernetes operator pattern](https://kubernetes.io/docs/concepts/extend-kubernetes/operator/), which means that it uses Kubernetes APIs to provide support for operations, such as: provisioning, lifecycle management, application versioning, product patching, scaling, and security. The operator also enables the use of tooling that is native to this infrastructure for monitoring, logging, tracing, and security.

You can:
* Deploy an operator that manages all WebLogic domains in all namespaces in a Kubernetes cluster, or that only manages domains in a specific subset of the namespaces, or that manages only domains that are located in the same namespace as the operator. At most, a namespace can be managed by one operator.
* Supply WebLogic domain configuration using:
  * _Domain in PV_: Locates WebLogic domain homes in a Kubernetes PersistentVolume (PV). This PV can reside in an NFS file system or other Kubernetes volume types.
  * _Domain in Image_: Includes a WebLogic domain home in a container image.
  * _Model in Image_: Includes [WebLogic Deploy Tooling](https://github.com/oracle/weblogic-deploy-tooling) models and archives in a container image.
* Configure deployment of WebLogic domains as a Kubernetes resource (using a Kubernetes custom resource definition).
* Override certain aspects of the WebLogic domain configuration; for example, use a different database password for different deployments.
* Start and stop servers and clusters in the domain based on declarative startup parameters and desired states.
* Scale WebLogic domains by starting and stopping Managed Servers on demand, or by integrating with a REST API to initiate scaling based on the WebLogic Diagnostics Framework (WLDF), Prometheus, Grafana, or other rules.
* Expose the WebLogic Server Administration Console outside the Kubernetes cluster, if desired.
* Expose T3 channels outside the Kubernetes domain, if desired.
* Expose HTTP paths on a WebLogic domain outside the Kubernetes domain with load balancing, and automatically update the load balancer when Managed Servers in the WebLogic domain are started or stopped.
* Publish operator and WebLogic Server logs into Elasticsearch and interact with them in Kibana.


The fastest way to experience the operator is to follow the [Quick Start guide](https://oracle.github.io/weblogic-kubernetes-operator/quickstart/), or you can peruse our [documentation](https://oracle.github.io/weblogic-kubernetes-operator), read our [blogs](https://blogs.oracle.com/weblogicserver/how-to-weblogic-server-on-kubernetes), or try out the [samples](https://oracle.github.io/weblogic-kubernetes-operator/samples/simple/).

***
The [current release of the operator](https://github.com/oracle/weblogic-kubernetes-operator/releases) is 3.2.4.
This release was published on June 18, 2021.
***

# Documentation

Documentation for the operator is [available here](https://oracle.github.io/weblogic-kubernetes-operator).

This documentation includes information for users and for developers.  It provides samples, reference material, security
information and a [Quick Start](https://oracle.github.io/weblogic-kubernetes-operator/quickstart/) guide if you just want to get up and running quickly.

Documentation for prior releases of the operator: [2.5.0](https://oracle.github.io/weblogic-kubernetes-operator/2.5/), [2.6.0](https://oracle.github.io/weblogic-kubernetes-operator/2.6/), [3.0.x](https://oracle.github.io/weblogic-kubernetes-operator/3.0/), and [3.1.x](https://oracle.github.io/weblogic-kubernetes-operator/3.1/).

# Backward compatibility guidelines

The 2.0 release introduced some breaking changes and did not maintain compatibility with previous releases.

Starting with the 2.0.1 release, operator releases are intended to be backward compatible with respect to the domain
resource schema, operator Helm chart input values, configuration overrides template, Kubernetes resources created
by the operator Helm chart, Kubernetes resources created by the operator, and the operator REST interface. We intend to
maintain compatibility for three releases, except in the case of a clearly communicated deprecated feature, which will be
maintained for one release after a replacement is available.

# Need more help? Have a suggestion? Come and say, "Hello!"

We have a **public Slack channel** where you can get in touch with us to ask questions about using the operator or give us feedback
or suggestions about what features and improvements you would like to see.  We would love to hear from you. To join our channel,
please [visit this site to get an invitation](https://weblogic-slack-inviter.herokuapp.com/).  The invitation email will include
details of how to access our Slack workspace.  After you are logged in, please come to `#operator` and say, "hello!"

# Contributing to the operator

Oracle welcomes contributions to this project from anyone.  Contributions may be reporting an issue with the operator or submitting a pull request.  Before embarking on significant development that may result in a large pull request, it is recommended that you create an issue and discuss the proposed changes with the existing developers first.

If you want to submit a pull request to fix a bug or enhance an existing feature, please first open an issue and link to that issue when you submit your pull request.

If you have any questions about a possible submission, feel free to open an issue too.

## Contributing to the WebLogic Kubernetes Operator repository

Pull requests can be made under The Oracle Contributor Agreement (OCA), which is available at [https://www.oracle.com/technetwork/community/oca-486395.html](https://www.oracle.com/technetwork/community/oca-486395.html).

For pull requests to be accepted, the bottom of the commit message must have the following line, using the contributor’s name and e-mail address as it appears in the OCA Signatories list.

```
Signed-off-by: Your Name <you@example.org>
```

This can be automatically added to pull requests by committing with:

```
git commit --signoff
```

Only pull requests from committers that can be verified as having signed the OCA can be accepted.

## Pull request process

*	Fork the repository.
*	Create a branch in your fork to implement the changes. We recommend using the issue number as part of your branch name, for example, `1234-fixes`.
*	Ensure that any documentation is updated with the changes that are required by your fix.
*	Ensure that any samples are updated if the base image has been changed.
*	Submit the pull request. Do not leave the pull request blank. Explain exactly what your changes are meant to do and provide simple steps on how to validate your changes. Ensure that you reference the issue you created as well. We will assign the pull request to 2-3 people for review before it is merged.

## Introducing a new dependency

Please be aware that pull requests that seek to introduce a new dependency will be subject to additional review.  In general, contributors should avoid dependencies with incompatible licenses, and should try to use recent versions of dependencies.  Standard security vulnerability checklists will be consulted before accepting a new dependency.  Dependencies on closed-source code, including WebLogic Server, will most likely be rejected.
