---
title: "Channels"
date: 2019-03-08T19:07:36-05:00
weight: 2
description: "WebLogic channels"
---

#### WebLogic T3 channels

{{% notice warning %}}
Oracle recommends not to expose any administrative, RMI, or T3 channels outside the Kubernetes cluster
unless absolutely necessary. If exposing any of these channels, limit access using
controls like security lists or set up a Bastion to provide access.
{{% /notice %}}

When accessing T3/RMI based channels, the preferred approach is to `kubectl exec` into
the Kubernetes pod and then run `wlst` or set up Bastion access and then run
`wlst` from the Bastion host to connect to the Kubernetes cluster.

Also, consider a private VPN if you need use cross-domain T3 access
between clouds, data centers, and such.
