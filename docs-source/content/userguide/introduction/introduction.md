---
title: "Get started"
date: 2019-02-23T16:40:54-05:00
weight: 1
description: "Learn about the Oracle WebLogic Server Kubernetes Operator, how it works and how to use it to manage WebLogic domains."
---

An operator is an application-specific controller that extends Kubernetes to create, configure, and manage instances
of complex applications. The Oracle WebLogic Server Kubernetes Operator follows the standard Kubernetes operator pattern, and
simplifies the management and operation of WebLogic domains and deployments.

You can have one or more operators in your Kubernetes cluster that manage one or more WebLogic domains each.
We provide a Helm chart to manage the installation and configuration of the operator.
Detailed instructions are available [here]({{< relref "/userguide/managing-operators/installation/_index.md" >}}).


### Prerequisites

* Kubernetes 1.10.11+, 1.11.5+, 1.12.3+, and 1.13.0+  (check with `kubectl version`).
* Flannel networking v0.9.1-amd64 (check with `docker images | grep flannel`).
* Docker 18.03.1.ce (check with `docker version`).
* Helm 2.8.2+ (check with `helm version`).
* Oracle WebLogic Server 12.2.1.3.0 with patch 29135930.
   * The existing WebLogic Docker image, `store/oracle/weblogic:12.2.1.3`, was updated on January 17, 2019, and has all the necessary patches applied.
   * A `docker pull` is required if you pulled the image prior to that date.
   * Check the WLS version with `docker run store/oracle/weblogic:12.2.1.3 sh -c` `'source $ORACLE_HOME/wlserver/server/bin/setWLSEnv.sh > /dev/null 2>&1 && java weblogic.version'`.
   * Check the WLS patches with `docker run store/oracle/weblogic:12.2.1.3 sh -c` `'$ORACLE_HOME/OPatch/opatch lspatches'`.
* You must have the `cluster-admin` role to install the operator.

### OpenShift

Operator 2.0.1+ is certified for use on OpenShift 3.11.43+, with Kubernetes 1.11.5+

When using the operator in OpenShift, the anyuid security context constraint is required to ensure that WebLogic containers run with a UNIX UID that has the correct permissions on the domain filesystem.

### Operator Docker image

You can find the operator image in
[Docker Hub](https://hub.docker.com/r/oracle/weblogic-kubernetes-operator/).
