---
title: "Introduction"
date: 2019-02-22T15:44:42-05:00
draft: false
weight: 1
---

Use this Quick Start guide to create a WebLogic deployment in a Kubernetes cluster with the Oracle WebLogic Kubernetes Operator. Please note that this walk-through is for demonstration purposes only, not for use in production.
These instructions assume that you are already familiar with Kubernetes.  If you need more detailed instructions, please
refer to the [User guide]({{< relref "/userguide/_index.md" >}}).

**Important note for users of operator releases before 2.0**
{{% expand "Click here to expand" %}}


{{% notice warning %}}
If you have an older version of the operator installed on your cluster, for example, a 1.x version or one of the 2.0 release
candidates, then you must remove it before installing this version.  This includes the 2.0-rc1 version; it must be completely removed.
You should remove the deployment (for example, `kubectl delete deploy weblogic-operator -n your-namespace`) and the custom
resource definition (for example, `kubectl delete crd domain`).  If you do not remove
the custom resource definition you may see errors like this:
```
Error from server (BadRequest): error when creating "/scratch/output/uidomain/weblogic-domains/uidomain/domain.yaml":
the API version in the data (weblogic.oracle/v2) does not match the expected API version (weblogic.oracle/v1
```
{{% /notice %}}

{{% /expand %}}
