### Oracle WebLogic Server Kubernetes Operator

The WebLogic Server Kubernetes Operator supports running any of your WebLogic Server and Fusion Middleware Infrastructure domains on Kubernetes, an industry standard cloud neutral deployment platform. It enables you to encapsulate your entire WebLogic installation and layered applications into a portable set of cloud neutral images and simple resource description files. You can run them on any on-premises or public cloud that supports Kubernetes where you've deployed the operator.

Furthermore, the operator is well suited to CI/CD processes. You can easily inject changes when moving between environments such as from test to production. For example, you can externally inject database URLs and credentials during deployment, or you can inject arbitrary changes to most WebLogic configuration.

The operator uses a common set of Kubernetes APIs to provide support for operations such as: provisioning, life cycle management, application versioning, product patching, scaling, and security. The operator also enables the use of tooling that is native to this infrastructure for monitoring, logging, tracing, and security.

In detail, you can:

* Supply your WebLogic domain configuration using one of three options:
  * _Domain in Image_: Include a WebLogic domain home in a container image.
  * _Model in Image_: Include WebLogic Deploy Tooling models and archives in a container image.
  * _Domain in PV_: Locate WebLogic domain homes in a Kubernetes PersistentVolume. This PersistentVolume can reside in an NFS file system or other Kubernetes volume types.
* Configure deployment of WebLogic domains as a Kubernetes resource (using a Kubernetes custom resource definition).
* Override certain aspects of the WebLogic domain configuration; for example, use a different database password for different deployments.
* Start and stop  particular servers and clusters in the domain based on declarative startup parameters and desired states.
* Scale WebLogic domains by starting and stopping Managed Servers on demand, or by integrating with a REST API to initiate scaling based on WLDF, Prometheus, Grafana, or other rules.
* Expose the WebLogic Server Administration Console outside the Kubernetes cluster, if desired.
* Expose T3 channels outside the Kubernetes domain, if desired.
* Expose HTTP paths on a WebLogic domain outside the Kubernetes domain with load balancing and automatically update the load balancer when Managed Servers in the WebLogic domain are started or stopped.
* Publish operator and WebLogic Server logs into Elasticsearch and interact with them in Kibana.

__TBD RM: The tutorial following may be out of date. Doc and runtime (someone should verify that the image references are still good etc.). We should also make clear that the difference is between it and 'quick start'.__
{{% notice tip %}} 
The fastest way to experience the operator is to follow the [Quick Start guide]({{< relref "/quickstart/_index.md" >}}), or you can peruse our [documentation]({{< relref "/userguide/_index.md" >}}), read our [blogs](https://blogs.oracle.com/weblogicserver/how-to-weblogic-server-on-kubernetes), or try out the [samples]({{< relref "/samples/_index.md" >}}).
You can also step through the [Tutorial](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/hands-on-lab/README.md)
using the operator to deploy and run a WebLogic domain container-packaged web application on an Oracle Cloud Infrastructure Container Engine for Kubernetes (OKE) cluster.
{{% /notice %}}

***
### Current production release

The [current release of the operator](https://github.com/oracle/weblogic-kubernetes-operator/releases) is 3.2.1.
This release was published on April 5, 2021. See the operator prerequisites and supported environments [here]({{< relref "/userguide/introduction/introduction#operator-prerequisites" >}}).

***

### Recent changes and known issues

See the [Release Notes]({{< relref "release-notes.md" >}})  for recent changes to the operator and known issues.

### Operator earlier versions

Documentation for prior releases of the operator: [2.5.0](https://oracle.github.io/weblogic-kubernetes-operator/2.5/), [2.6.0](https://oracle.github.io/weblogic-kubernetes-operator/2.6/), [3.0.x](https://oracle.github.io/weblogic-kubernetes-operator/3.0/), and [3.1.x](https://oracle.github.io/weblogic-kubernetes-operator/3.1/).

### Backward compatibility guidelines

Starting from the 2.0.1 release, operator releases are backward compatible with respect to the domain
resource schema, operator Helm chart input values, configuration overrides template, Kubernetes resources created
by the operator Helm chart, Kubernetes resources created by the operator, and the operator REST interface. We intend to
maintain compatibility for three releases, except in the case of a clearly communicated deprecated feature, which will be
maintained for one release after a replacement is available.

### About this documentation

This documentation includes sections targeted to different audiences.  To help you find what you are looking for more easily,
please consult this table of contents:

* The [Quick Start guide]({{< relref "/quickstart/_index.md" >}}) explains how to quickly get the operator running, using the defaults, nothing special.
* The [User guide]({{< relref "/userguide/_index.md" >}}) contains detailed usage information, including how to install and configure the operator,
  and how to use it to create and manage WebLogic domains.  
* The [Samples]({{< relref "/samples/_index.md" >}}) provide detailed example code and instructions that show you how to perform
  various tasks related to the operator.
* The [Developer guide]({{< relref "/developerguide/_index.md" >}}) provides details for people who want to understand how the operator is built, tested, and so on. Those who wish to contribute to the operator code will find useful information here. 
* [Reference]({{< relref "/reference/_index.md" >}}) describes domain resource attributes and the operator REST API.
* [Security]({{< relref "/security/_index.md" >}}) describes Kubernetes, WebLogic, and OpenShift security requirements.
* [Frequently asked questions]({{< relref "/faq/_index.md" >}}) provides answers to common questions.

### Oracle support

To access Oracle support for running WebLogic Server domains on Kubernetes platforms, see [WebLogic Server Certifications on Kubernetes in My Oracle Support Doc ID 2349228.1](https://support.oracle.com/epmos/faces/DocumentDisplay?_afrLoop=208317433106215&id=2349228.1&_afrWindowMode=0&_adf.ctrl-state=c2nhai8p3_4).

### Need more help? Have a suggestion? Come and say, "Hello!"

We have a **public Slack channel** where you can get in touch with us to ask questions about using the operator or give us feedback
or suggestions about what features and improvements you would like to see.  We would love to hear from you. To join our channel,
please [visit this site to get an invitation](https://weblogic-slack-inviter.herokuapp.com/).  The invitation email will include
details of how to access our Slack workspace.  After you are logged in, please come to `#operator` and say, "hello!"

__TBD RM: Move this to the first section of the developer guide. But maybe cross-reference to the new location from here.__

### Contributing to the operator

Oracle welcomes contributions to this project from anyone.  Contributions may be reporting an issue with the operator or submitting a pull request.  Before embarking on significant development that may result in a large pull request, it is recommended that you create an issue and discuss the proposed changes with the existing developers first.

If you want to submit a pull request to fix a bug or enhance an existing feature, please first open an issue and link to that issue when you submit your pull request.

If you have any questions about a possible submission, feel free to open an issue too.

#### Contributing to the Oracle WebLogic Server Kubernetes Operator repository

Pull requests can be made under The Oracle Contributor Agreement (OCA), which is available at [https://www.oracle.com/technetwork/community/oca-486395.html](https://www.oracle.com/technetwork/community/oca-486395.html).

For pull requests to be accepted, the bottom of the commit message must have the following line, using the contributor’s name and e-mail address as it appears in the OCA Signatories list.

```
Signed-off-by: Your Name <you@example.org>
```

This can be automatically added to pull requests by committing with:

```shell
$ git commit --signoff
```

Only pull requests from committers that can be verified as having signed the OCA can be accepted.

#### Pull request process

*	Fork the repository.
*	Create a branch in your fork to implement the changes. We recommend using the issue number as part of your branch name, for example, `1234-fixes`.
*	Ensure that any documentation is updated with the changes that are required by your fix.
*	Ensure that any samples are updated if the base image has been changed.
*	Submit the pull request. Do not leave the pull request blank. Explain exactly what your changes are meant to do and provide simple steps on how to validate your changes. Ensure that you reference the issue you created as well. We will assign the pull request to 2-3 people for review before it is merged.

#### Introducing a new dependency

Please be aware that pull requests that seek to introduce a new dependency will be subject to additional review.  In general, contributors should avoid dependencies with incompatible licenses, and should try to use recent versions of dependencies.  Standard security vulnerability checklists will be consulted before accepting a new dependency.  Dependencies on closed-source code, including WebLogic Server, will most likely be rejected.
