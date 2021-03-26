### Oracle WebLogic Server Kubernetes Operator

Oracle is finding ways for organizations using WebLogic Server to run important workloads, to move those workloads into the cloud. By certifying on industry standards, such as Docker and Kubernetes, WebLogic now runs in a cloud neutral infrastructure. In addition, we've provided an open source Oracle WebLogic Server Kubernetes Operator (the “operator”) which has several key features to assist you with deploying and managing WebLogic domains in a Kubernetes environment. You can:

* Create WebLogic domains in a Kubernetes PersistentVolume. This PersistentVolume can reside in an NFS file system or other Kubernetes volume types.
* Create a WebLogic domain in a Docker image.
* Override certain aspects of the WebLogic domain configuration.
* Define WebLogic domains as a Kubernetes resource (using a Kubernetes custom resource definition).
* Start servers based on declarative startup parameters and desired states.
* Manage WebLogic configured or dynamic clusters.
* Expose the WebLogic Server Administration Console outside the Kubernetes cluster, if desired.
* Expose T3 channels outside the Kubernetes domain, if desired.
* Expose HTTP paths on a WebLogic domain outside the Kubernetes domain with load balancing and update the load balancer when Managed Servers in the WebLogic domain are started or stopped.
* Scale WebLogic domains by starting and stopping Managed Servers on demand, or by integrating with a REST API to initiate scaling based on WLDF, Prometheus, Grafana, or other rules.
* Publish operator and WebLogic Server logs into Elasticsearch and interact with them in Kibana.

The fastest way to experience the operator is to follow the [Quick Start guide]({{< relref "/quickstart/_index.md" >}}), or you can peruse our [documentation]({{< relref "/userguide/_index.md" >}}), read our [blogs](https://blogs.oracle.com/weblogicserver/how-to-weblogic-server-on-kubernetes), or try out the [samples]({{< relref "/samples/_index.md" >}}).

{{% notice tip %}} Step through the [Tutorial](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/hands-on-lab/README.md)
using the operator to deploy and run a WebLogic domain container-packaged web application on an Oracle Cloud Infrastructure Container Engine for Kubernetes (OKE) cluster.
{{% /notice %}}

***
#### Current production release

The [current production release of the operator](https://github.com/oracle/weblogic-kubernetes-operator/releases) is 2.6.0.
This release was published on June 22, 2020. See the operator prerequisites and supported environments [here]({{< relref "/userguide/introduction/introduction#operator-prerequisites" >}}).

#### Preview of next major release

The [current preview release of the operator](https://github.com/oracle/weblogic-kubernetes-operator/releases) is 3.0.0-rc1 (release candidate).
This release candidate is suitable for use by early adopters who wish to test 3.0.0 features and provide feedback.
This release candidate was published on May 8, 2020.  There may be additional release candidates before the final 3.0.0 release.

This release candidate introduces _non-backward compatible_ changes.  This release candidate cannot be run in the same
cluster as another release of the operator.  You can upgrade from 2.6.0 to 3.0.0-rc1 without needing to restart or recreate
any existing domains. However, please note that we _do_ plan to support running the final 3.0.0
release in the same cluster with at least one 2.x release of the operator to allow for staged migration.

The feature changes in 3.0.0-rc1 are:

* Introduction of a new ["Model In Image"]({{% relref "/userguide/managing-domains/model-in-image" %}}) feature which allows you to have a domain
  created at pod startup time from a WebLogic Deploy Tool model and archive.
  This supports user-requested use cases like creating multiple domains from
  the same model and automated updating of the domain based on model changes.
  The operator automates management of the domain encryption keys to ensure
  that they are not changed during domain updates.
  We provide a [sample]({{% relref "/samples/simple/domains/model-in-image" %}}) that
  demonstrates the key use cases for this feature.
* Support for running the operator on Kubernetes 1.16.
* Deprecation and removal of support for running the operator on Kubernetes 1.13
  and earlier versions.
* Deprecation and removal of support for Helm 2.x.  Helm 2.x uses the "tiller" pod
  which needs to run with elevated privileges (`cluster-admin` or very close to that)
  and which could be a vector for a privilege escalation attack.  Helm 3.x removes
  Tiller and does not create the same exposure.

***

#### Recent changes and known issues

See the [Release Notes]({{< relref "release-notes.md" >}})  for recent changes to the operator and known issues.

#### Operator earlier versions

Documentation for prior releases of the operator: [2.5.0](https://oracle.github.io/weblogic-kubernetes-operator/2.5/).

#### Backward compatibility guidelines

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
* The [Developer guide]({{< relref "/developerguide/_index.md" >}}) provides details for people who want to understand how the operator is built, tested, and so on. Those who wish to contribute to the operator code will find useful information here.  This section also includes
  the Swagger/OpenAPI documentation for the REST APIs.
* The [Contributing](#contributing-to-the-operator) section provides information about contribution requirements.


### User guide

The [User guide]({{< relref "/userguide/_index.md" >}}) provides detailed information about all aspects of using the operator including:

* Installing and configuring the operator.
* Using the operator to create and manage WebLogic domains.
* Manually creating WebLogic domains to be managed by the operator.
* Scaling WebLogic clusters.
* Configuring Kubernetes load balancers.
* Configuring Elasticsearch and Kibana to access the operator's log files.
* Shutting down domains.
* Removing/deleting domains.
* And much more!

### Samples

Please refer to our [samples]({{< relref "/samples/_index.md" >}}) for information about the available sample code.

### Developer guide

Developers interested in this project are encouraged to read the [Developer guide]({{< relref "/developerguide/_index.md" >}}) to learn how to build the project, run tests, and so on.  The Developer guide also provides details about the structure of the code, coding standards, and the Asynchronous Call facility used in the code to manage calls to the Kubernetes API.

### API documentation

Documentation for APIs:

* The operator provides a REST API that you can use to obtain configuration information and to initiate scaling actions. For details about how to use the REST APIs, see [Use the operator's REST services]({{< relref "/userguide/managing-operators/using-the-operator/the-rest-api#use-the-operators-rest-services" >}}).

* See the [Swagger](https://oracle.github.io/weblogic-kubernetes-operator/swagger/index.html) documentation for the operator's REST interface.

### Need more help? Have a suggestion? Come and say, "Hello!"

We have a **public Slack channel** where you can get in touch with us to ask questions about using the operator or give us feedback
or suggestions about what features and improvements you would like to see.  We would love to hear from you. To join our channel,
please [visit this site to get an invitation](https://weblogic-slack-inviter.herokuapp.com/).  The invitation email will include
details of how to access our Slack workspace.  After you are logged in, please come to `#operator` and say, "hello!"

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

```
git commit --signoff
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
