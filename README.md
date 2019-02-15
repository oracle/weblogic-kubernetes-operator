# Oracle WebLogic Server Kubernetes Operator

Built with [Jenkins](http://build.weblogick8s.org:8080/job/weblogic-kubernetes-operator/)

[![Build Status](http://build.weblogick8s.org:8080/buildStatus/icon?job=weblogic-kubernetes-operator)](http://build.weblogick8s.org:8080/job/weblogic-kubernetes-operator/)

Oracle is finding ways for organizations using WebLogic Server to run important workloads, to move those workloads into the cloud. By certifying on industry standards, such as Docker and Kubernetes, WebLogic now runs in a cloud neutral infrastructure. In addition, we've provided an open-source Oracle WebLogic Server Kubernetes Operator (the “operator”) which has several key features to assist you with deploying and managing WebLogic domains in a Kubernetes environment. You can:

* Create WebLogic domains in a Kubernetes persistent volume. This persistent volume can reside in an NFS file system or other Kubernetes volume types.
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

The fastest way to experience the operator is to follow the [Quick Start guide](site/quickstart.md), or you can peruse our [documentation](site), read our [blogs](https://blogs.oracle.com/fusionmiddlewaresupport/updated-weblogic-kubernetes-support-with-operator-20-v2), or try out the [samples](kubernetes/samples/README.md).

```diff
+ The current release of the operator is 2.0-rc2, a release candidate for our 2.0 release.
+ This release candidate was published on Jan. 16, 2019.
+ We expect to publish the final 2.0 release later in January, 2019.
+ We expect that there will be some minor changes to documentation and samples in the final 2.0 release.
+ However, this release candidate is suitable for testing and early adopters.
```

## Known issues

| Issue | Description |
| --- | --- |
| [#726](https://github.com/oracle/weblogic-kubernetes-operator/issues/726) | Clusters only support default channel. |

## Operator version 1.1

Documentation for the 1.1 release of the operator is available [here](site/v1.1/README.md).

# Backward compatibility guidelines

The 2.0 release introduces some breaking changes and does not maintain compatibility with previous releases.

Starting with the 2.0 release, future operator releases are intended to be backward compatible with respect to the domain
resource schema, operator Helm chart input values, configuration overrides template, Kubernetes resources created
by the operator Helm chart, Kubernetes resources created by the operator, and the operator REST interface. We intend to
maintain compatibility for three releases, except in the case of a clearly communicated deprecated feature, which will be
maintained for one release after a replacement is available.

# About this documentation

This documentation includes sections targeted to different audiences.  To help you find what you are looking for more easily,
please consult this table of contents:

* The [Quick Start guide](site/quickstart.md) explains how to quickly get the operator running, using the defaults, nothing special.
* The [User guide](site/user-guide.md) contains detailed usage information, including how to install and configure the operator,
  and how to use it to create and manage WebLogic domains.  
* The [Samples](kubernetes/samples/README.md) provide detailed example code and instructions that show you how to perform
  various tasks related to the operator.
* The [Developer guide](site/developer.md) provides details for people who want to understand how the operator is built, tested, and so on. Those who wish to contribute to the operator code will find useful information here.  This section also includes
  API documentation (Javadoc) and Swagger/OpenAPI documentation for the REST APIs.
* The [Contributing](#contributing-to-the-operator) section provides information about contribution requirements.


# User guide

The [User guide](site/user-guide.md) provides detailed information about all aspects of using the operator including:

* Installing and configuring the operator.
* Using the operator to create and manage WebLogic domains.
* Manually creating WebLogic domains to be managed by the operator.
* Scaling WebLogic clusters.
* Configuring Kubernetes load balancers.
* Configuring Elasticsearch and Kibana to access the operator's log files.
* Shutting down domains.
* Removing/deleting domains.
* And much more!

# Samples

Please refer to our [samples](kubernetes/samples/README.md) for information about the available sample code.

# Need more help? Have a suggestion? Come and say, "Hello!"

We have a **public Slack channel** where you can get in touch with us to ask questions about using the operator or give us feedback
or suggestions about what features and improvements you would like to see.  We would love to hear from you. To join our channel,
please [visit this site to get an invitation](https://weblogic-slack-inviter.herokuapp.com/).  The invitation email will include
details of how to access our Slack workspace.  After you are logged in, please come to `#operator` and say, "hello!"

# Recent changes

See [Recent changes](site/recent-changes.md) for changes to the operator, including any backward incompatible changes.

# Developer guide

Developers interested in this project are encouraged to read the [Developer guide](site/developer.md) to learn how to build the project, run tests, and so on.  The Developer guide also provides details about the structure of the code, coding standards, and the Asynchronous Call facility used in the code to manage calls to the Kubernetes API.

Please take a look at our [wish list](https://github.com/oracle/weblogic-kubernetes-operator/wiki/Wish-list) to get an idea of the kind of features we would like to add to the operator.  Maybe you will see something to which you would like to contribute!

## API documentation

Documentation for APIs:

* The operator provides a REST API that you can use to obtain configuration information and to initiate scaling actions. For details about how to use the REST APIs, see [Using the operator's REST services](site/rest.md).

* See the [Swagger](https://oracle.github.io/weblogic-kubernetes-operator/swagger/index.html) documentation for the operator's REST interface.

* See the [Javadoc](https://oracle.github.io/weblogic-kubernetes-operator/apidocs/index.html) for the operator.

# Contributing to the operator

Oracle welcomes contributions to this project from anyone.  Contributions may be reporting an issue with the operator or submitting a pull request.  Before embarking on significant development that may result in a large pull request, it is recommended that you create an issue and discuss the proposed changes with the existing developers first.

If you want to submit a pull request to fix a bug or enhance an existing feature, please first open an issue and link to that issue when you submit your pull request.

If you have any questions about a possible submission, feel free to open an issue too.

## Contributing to the Oracle WebLogic Server Kubernetes Operator repository

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

## Use Helm Chart from Github chart repository

Add this repo to Helm installation:

```
$ helm repo add weblogic-operator https://oracle.github.io/weblogic-kubernetes-operator/charts
```

Verify repository was added correctly:

````
$ helm repo list
NAME           URL
weblogic-operator    https://oracle.github.io/weblogic-kubernetes-operator/charts
```

Update with latest information about charts from chart repositories:

```
$ helm repo update
```

Install Operator from the repo:

```
$ helm install helm install weblogic-operator/weblogic-operator --name weblogic-operator
```
