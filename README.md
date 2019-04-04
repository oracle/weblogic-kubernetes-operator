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

The fastest way to experience the operator is to follow the [Quick Start guide](https://oracle.github.io/weblogic-kubernetes-operator/quickstart/), or you can peruse our [documentation](https://oracle.github.io/weblogic-kubernetes-operator), read our [blogs](https://blogs.oracle.com/weblogicserver/updated-weblogic-kubernetes-support-with-operator-20), or try out the [samples](https://oracle.github.io/weblogic-kubernetes-operator/samples/).

***
The [current release of the operator](https://github.com/oracle/weblogic-kubernetes-operator/releases) is 2.1.
This release was published on April 4, 2019.
***

# Documentation

Documentation for the operator is available [here](https://oracle.github.io/weblogic-kubernetes-operator) and includes
information for users and for developers.  It provides samples, reference material like API documentation, security
information and a *Quick Start* guide if you just want to get up and running quickly.

Documentation for old releases of the operator is available [here](site/README.md).

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

