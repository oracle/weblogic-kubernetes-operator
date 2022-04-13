---
title: "Operator prerequisites"
date: 2019-02-23T16:40:54-05:00
description: "Review the prerequisites for the current release of the operator."
weight: 2
---

For the current production release {{< latestVersion >}}:

* Kubernetes 1.19.15+, 1.20.11+, and 1.21.5+ (check with `kubectl version`).
* Flannel networking v0.13.0-amd64 or later (check with `docker images | grep flannel`), Calico networking v3.16.1 or later,
 *or* OpenShift SDN on OpenShift 4.3 systems.
* Docker 18.9.1 or 19.03.1+ (check with `docker version`) *or* CRI-O 1.20.2+ (check with `crictl version | grep RuntimeVersion`).
* Helm 3.3.4+ (check with `helm version --client --short`).
* For domain home source type `Model in Image`, WebLogic Deploy Tooling 1.9.11.
* Either Oracle WebLogic Server 12.2.1.3.0 with patch 29135930, Oracle WebLogic Server 12.2.1.4.0, or Oracle WebLogic Server 14.1.1.0.0.
   * The existing WebLogic Server image, `container-registry.oracle.com/middleware/weblogic:12.2.1.3`,
   has all the necessary patches applied.
   * Check the WLS version with `docker run container-registry.oracle.com/middleware/weblogic:12.2.1.3 sh -c` `'source $ORACLE_HOME/wlserver/server/bin/setWLSEnv.sh > /dev/null 2>&1 && java weblogic.version'`.
   * Check the WLS patches with `docker run container-registry.oracle.com/middleware/weblogic:12.2.1.3 sh -c` `'$ORACLE_HOME/OPatch/opatch lspatches'`.
* Container images based on Oracle Linux 8 are now supported. The Oracle Container Registry hosts container images
  based on both Oracle Linux 7 and 8, including Oracle WebLogic Server 14.1.1.0.0 images based on Java 8 and 11.
* You must have the `cluster-admin` role to install the operator.  The operator does
  not need the `cluster-admin` role at runtime. For more information, see the role-based access control, [RBAC]({{< relref "/security/rbac.md" >}}), documentation.
* We do not currently support running WebLogic in non-Linux containers.

See also [Supported platforms]({{< relref "userguide/platforms/environments.md" >}}) for environment and licensing requirements.
