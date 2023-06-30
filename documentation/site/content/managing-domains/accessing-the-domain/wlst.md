---
title: "Use WLST"
date: 2019-02-23T17:39:19-05:00
draft: false
weight: 2
description: "Use the WebLogic Scripting Tool (WLST) with domains running in Kubernetes."
---

{{< table_of_contents >}}

### Introduction
You can use the WebLogic Scripting Tool (WLST) to manage a domain running in Kubernetes.

To give WLST access to a domain running in Kubernetes, you can:

- [Use kubectl exec](#use-kubectl-exec)
- [Use a `NodePort`](#use-a-nodeport)
- [Use port forwarding](#use-port-forwarding)

**NOTE**:  If your domain home type is either [Domain in Image]({{< relref "/samples/domains/domain-home-in-image/_index.md" >}}) or [Model in Image]({{< relref "/samples/domains/model-in-image/_index.md" >}}), then do not use the WLST to make changes to the WebLogic domain configuration because these changes are ephemeral and will be lost when servers restart. See [Choose a domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}).

### Use `kubectl exec`

You can use the `kubectl exec` command to start an interactive WLST session
within a pod or to remotely run a WLST script on a pod.
Typically, this is the preferred method.

For example, if a `domainUID` is `sample-domain1`,
its Administration Server is named `admin-server` and is configured with default port `7001`,
and its pods are running in namespace `sample-domain1-ns`,
then you can start an interactive WLST session this way:

```text

$ kubectl -n sample-domain1-ns exec -it sample-domain1-admin-server /bin/bash

[oracle@sample-domain1-admin-server oracle]$ wlst.sh

Initializing WebLogic Scripting Tool (WLST) ...

Welcome to WebLogic Server Administration Scripting Shell

Type help() for help on available commands

wls:/offline> connect('myusername','mypassword','t3://sample-domain1-admin-server:7001')
Connecting to t3://sample-domain1-admin-server:7001 with userid myusername ...
Successfully connected to Admin Server "admin-server" that belongs to domain "base_domain".

Warning: An insecure protocol was used to connect to the server.
To ensure on-the-wire security, the SSL port or Admin port should be used instead.

wls:/base_domain/serverConfig/> exit()


Exiting WebLogic Scripting Tool.
[oracle@sample-domain1-admin-server oracle]$ exit

$
```

### Use a `NodePort`

{{% notice tip %}}
If you are setting up WLST access through a `NodePort` and your external port
is not going to be the same as the port number on the WebLogic Administration Server Pod, then see
[Enabling WLST access when local and remote ports do not match]({{< relref "/managing-domains/accessing-the-domain/port-forward#enabling-wlst-access-when-local-and-remote-ports-do-not-match" >}})
for an additional required setup step.
{{% /notice %}}

{{% notice warning %}}
A `NodePort` can expose a WebLogic T3 or administrative channel
outside the Kubernetes cluster.
For domain security considerations, see [External network access security]({{< relref "/security/domain-security/weblogic-channels.md" >}}).
{{% /notice %}}

You can configure an Administration Server to expose an
externally accessible `NodePort` using these two steps:

1. Configure a Network Access Point (custom channel) with
  the T3 protocol on the Administration Server.
1. Expose this channel on a NodePort service using
  the `domain.spec.adminServer.adminService.channels` attribute.  

Here is an example snippet of a WebLogic domain `config.xml` file
for T3 channel `T3Channel` defined for an Administration Server named `admin-server`:

```xml
<server>
  <name>admin-server</name>
  <listen-port>7001</listen-port>
  <listen-address/>
  <network-access-point>
    <name>T3Channel</name>
    <protocol>t3</protocol>
    <public-address>kubernetes001</public-address>
    <listen-port>30012</listen-port>
    <public-port>30012</public-port>
  </network-access-point>
</server>
```

Here is an example snippet of a domain resource that
sets up a NodePort for the channel:

```yaml
spec:
  adminServer:
    adminService:
      channels:
       - channelName: T3Channel
         nodePort: 30012
```

If you set the `nodePort:` value to `0`, then Kubernetes will choose
an open port for you.

For more details on exposing the T3 channel using a NodePort service,
run the `kubectl explain domain.spec.adminServer.adminService.channels` command
or see the domain resource [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md) and [documentation]({{< relref "/managing-domains/domain-resource.md" >}}).

For example, if a `domainUID` is `domain1`,
the Administration Server name is `admin-server`,
and you have set up a NodePort service
on external port `30012` using
the `domain.spec.adminServer.adminService.channels` attribute,
then the service would be called:

```
domain1-admin-server-ext
```

This service will be in the same namespace as the domain, and its external port number can be obtained by checking its `nodePort` field:

```shell
$ kubectl get service domain1-admin-server-ext -n mynamespace -o jsonpath='{.spec.ports[0].nodePort}'
```
```
30012
```

If the Kubernetes node machine address is `kubernetes001`, then WLST can connect to
the WebLogic Server Administration Server pod through the `NodePort` as follows:

```shell
$ $ORACLE_HOME/oracle_common/common/bin/wlst.sh
```
```text
Initializing WebLogic Scripting Tool (WLST) ...

Welcome to WebLogic Server Administration Scripting Shell

Type help() for help on available commands

wls:/offline> connect('myusername','mypassword','t3://kubernetes001:30012')
Connecting to t3://kubernetes001:30012 with userid myusername ...
Successfully connected to Admin Server "admin-server" that belongs to domain "base_domain".

Warning: An insecure protocol was used to connect to the server.
To ensure on-the-wire security, the SSL port or Admin port should be used instead.

wls:/base_domain/serverConfig/> exit()


Exiting WebLogic Scripting Tool.
```

### Use port forwarding

One way to provide external access to WLST
is to forward network traffic from a local port on your local machine
to the administration port of an Administration Server Pod.
See these [instructions]({{< relref "/managing-domains/accessing-the-domain/port-forward.md#forward-a-local-port-to-an-administration-port-on-the-administration-server-pod" >}}).

{{% notice warning %}}
Port forwarding can expose a WebLogic T3 or administrative channel
outside the Kubernetes cluster.
For domain security considerations, see [External network access security]({{< relref "/security/domain-security/weblogic-channels.md" >}}).
{{% /notice %}}
