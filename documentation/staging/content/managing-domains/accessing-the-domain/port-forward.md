---
title: "Use port forwarding"
date: 2019-02-23T17:39:15-05:00
draft: false
weight: 3
description: "Use port forwarding to access WebLogic Server administration consoles and WLST."
---

{{< table_of_contents >}}

### Overview

Beginning with WebLogic Kubernetes Operator version 3.3.2,
or earlier if you are using an
[Istio-enabled]({{< relref "/managing-domains/accessing-the-domain/istio/istio.md" >}}) domain,
you can use the `kubectl port-forward` command to set up external access for
the WebLogic Server Administration Console, the Remote Console, and WLST.
This approach is particularly useful for managing WebLogic
from a private local network without exposing WebLogic's ports to a public network.

Here are the steps:

1. Set up [Administration Server network channels for port forward access](#set-up-administration-server-network-channels-for-port-forward-access).
1. If you are setting up WLST access
and your port forwarding local port is not going to be the same as the
port number on the WebLogic Administration Server Pod,
then see [enabling WLST access when local and remote ports do not match](#enabling-wlst-access-when-local-and-remote-ports-do-not-match) for an additional required setup step.
1. Be sure to review the [port forward notes and warnings](#port-forward-notes-and-warnings) first, and then run a [port forwarding command](#port-forward-to-an-administration-server-pod).
1. Use the WebLogic Server Administration Console, the Remote Console, or WLST with the port forwarding command's local address.
1. Finally, [terminate port forwarding](#terminating-port-forwarding).

{{% notice warning %}}
Externally exposing administrative, RMI, or T3 capable WebLogic channels
using a Kubernetes `NodePort`, load balancer,
port forwarding, or a similar method can create an insecure configuration.
For more information, see [External network access security]({{<relref "/security/domain-security/weblogic-channels.md">}}).
{{% /notice %}}

### Set up Administration Server network channels for port forward access

To enable a `kubectl port-forward` command to communicate
with a WebLogic Administration Server Pod, the operator
must modify the Administration Server configuration
to add network channels (Network Access Points)
with a `localhost` address for each existing  administration protocol capable port.
This behavior depends on your version and domain resource configuration:

* If Istio is _not_ enabled on the domain, then, for
  operator versions 3.3.2 and later,
  or for Istio enabled domains running Istio 1.10 and later,
  this behavior
  is configurable on the domain resource using the
  `domain.spec.adminServer.adminChannelPortForwardingEnabled`
  domain resource attribute.

  This attribute is enabled by default in operator versions 4.0
  and later, and is disabled by default in versions prior to 4.0.

  For details about this attribute, run the
  `kubectl explain domain.spec.adminServer.adminChannelPortForwardingEnabled`
  command or see the domain resource
  [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md).

* If WLST access is required for Istio-enabled domains running Istio versions prior to 1.10,
  you must add an additional network channel to the WebLogic Administration Server
  configured with the following attributes:
  * Protocol defined as `t3`.
  * Listen address defined with `localhost`. (Note: Setting the address to localhost is solely
  for self-documenting purposes. The address can be set to any value, and the operator will override
  it to the required value.)
  * Listen port. Note: Choose a port value that does not conflict with any ports defined
  in any of the additional network channels created for use with Istio versions prior to v1.10.
  For more details, see [Added network channels for Istio versions prior to v1.10]({{< relref "/managing-domains/accessing-the-domain/istio/istio#added-network-channels-for-istio-versions-prior-to-v110" >}}).
  * Enable `HTTP` protocol for this network channel.
  * Do _NOT_ set an `external listen address` or `external listen port`.

{{% notice note %}}
For Istio-enabled domains running Istio versions prior to 1.10, if console only access is required,
then it is not necessary to add an additional network channel to the WebLogic Administration Server.
{{% /notice %}}

For example, here is a snippet of a WebLogic domain `config.xml` file for channel `PortForward` for the Administration Server.
```xml
<server>
  <name>admin-server</name>
  <network-access-point>
    <name>PortForward</name>
    <protocol>t3</protocol>
    <listen-address>localhost</listen-address>
    <listen-port>7890</listen-port>
    <http-enabled-for-this-protocol>true</http-enabled-for-this-protocol>
  </network-access-point>
</server>
```
For Model in Image (MII) and Domain in Image (DII), here is a snippet model configuration for channel `PortForward` for the Administration Server.
```yaml
topology:
    ...
    Server:
        'admin-server':
            ListenPort: 7001
            NetworkAccessPoint:
                PortForward:
                    Protocol: 't3'
                    ListenAddress: 'localhost'
                    ListenPort: '7890'
                    HttpEnabledForThisProtocol: true
```

{{% notice note %}}
If your domain is already running, and you have made configuration changes,
then you will need to rerun its introspector job and ensure that the admin pod
restarts for the configuration changes to take effect.
{{% /notice %}}

If Istio is _not_ enabled on the domain or for Istio enabled domains running
Istio 1.10 and later, when administration channel port forwarding is enabled,
the operator automatically adds the following network channels
(also known as Network Access Points) to the WebLogic Administration Server Pod:

Description | Channel Name | Listen Address | Port | Protocol
--- |  --- | --- | --- | ---
When there's no administration port or administration channels configured, and a non-SSL default channel exists | internal-t3 | localhost | Server listening port | t3
When there's no administration port or administration channels configured, and an SSL default channel exists | internal-t3s | localhost | Server SSL listening port | t3s
When an administration port is enabled | internal-admin | localhost | WebLogic administration port | admin
When one or more custom administration channels are configured | internal-admin${index} (where ${index} is a number that starts with 1 and increases by 1 for each custom administration channel) | localhost | Custom administration port | admin

### Port forward to an Administration Server Pod    

If you have
[set up Administration Server network channels for port forward access](#set-up-administration-server-network-channels-for-port-forward-access),
then you can run a `kubectl port-forward` command to access such a channel. The command:
* By default, opens a port of your choosing on `localhost`
  on the machine where you run the command.
* Forwards traffic from this location to an
  address and port on the pod.
For administrative access, you need to forward to a port on the Administration Server Pod    
that accepts administration traffic.

Port forwarding occurs as long as the remote pod
and the command are running. If you exit the command,
then the forwarding stops and the local port and address is freed.
If the pod cycles or shuts down, then the forwarding
also stops and the command must be rerun after the
pod recovers in order for the forwarding to restart.

The `kubectl port-forward` command has the following syntax:

```shell
kubectl port-forward K8S_RESOURCE_TYPE/K8S_RESOURCE_NAME [options] LOCAL_PORT:REMOTE_PORT_ON_RESOURCE
```

For detailed usage, see [port-forward](https://kubernetes.io/docs/reference/generated/kubectl/kubectl-commands#port-forward) in the Kubernetes reference documentation and run `kubectl port-forward -h`.

For examples, notes, and warnings, see the [Port forward example](#port-forward-example) and [Port forward notes and warnings](#port-forward-notes-and-warnings).

#### Port forward example

For example, if you have a WebLogic domain with:

* Domain UID `domain1`
* Namespace `mynamespace`
* An Administration Server named `admin-server` listening on non-SSL listen port `7001`
* No administration port or administration channels configured on the Administration Server

And, you have [set up Administration Server network channels for port forward access](#set-up-administration-server-network-channels-for-port-forward-access) for this domain,
then you can run either of the following commands to forward local port `32015` to
the Administration Server Pod:

```shell
kubectl port-forward pods/domain1-admin-server -n mynamespace 32015:7001
```

or

```shell
kubectl port-forward service/domain1-admin-server -n mynamespace 32015:7001
```

The command output will be similar to the following:


```
Forwarding from 127.0.0.1:32015 -> 7001
```

In this example:

* You can access the WebLogic Server Administration Console
  at the `http://localhost:32015/console` URL by using a browser
  on the machine where you run the `kubectl port-forward` command.

* You can use WLST
  on the machine where you run the `kubectl port-forward` command
  to connect to `t3://localhost:32015` as shown:

  ```shell
  $ $ORACLE_HOME/oracle_common/common/bin/wlst.sh
  ```
  ```text
  Initializing WebLogic Scripting Tool (WLST) ...

  Welcome to WebLogic Server Administration Scripting Shell

  Type help() for help on available commands

  wls:/offline> connect('myadminuser','myadminpassword','t3://localhost:32015')
  Connecting to t3://localhost:32015 with userid myadminuser ...
  Successfully connected to Admin Server "admin-server" that belongs to domain "base_domain".

  Warning: An insecure protocol was used to connect to the server.
  To ensure on-the-wire security, the SSL port or Admin port should be used instead.

  wls:/base_domain/serverConfig/> exit()
  ```

#### Port forward notes and warnings

- _Security warning_:
  A port-forward connection can expose a WebLogic T3 or administrative channel
  outside the Kubernetes cluster.
For domain security considerations, see [External network access security]({{< relref "/security/domain-security/weblogic-channels.md" >}}).

- _Working with administration or SSL ports_:
  If a WebLogic administration port is configured and enabled on the Administration Server,
  then you will need to forward to this port instead of its pod's other ports.
  In this case, the Administration Console access requires using the secure
  `https` protocol and WLST access requires using `t3s` protocol.
  Similarly, if forwarding to an SSL port, then this requires using
  the `https` and `t3s` protocols for Administration Console and WLST access respectively.

- _Recovering from pod failures and restarts_:
  A port-forward connection terminates after the pod instance fails or restarts.
  You can rerun the same command to establish a new forwarding session and resume forwarding.

- _Using WLST when the local port differs from the remote port_:
  If you are setting up WLST access
  and your port forwarding local port is not going to be the same as the
  port number on the WebLogic Administration Server Pod,
  then see [Enabling WLST access when local and remote ports do not match](#enabling-wlst-access-when-local-and-remote-ports-do-not-match) for an additional required setup step.

- _Specifying a custom local IP address for port forwarding_:

  {{% notice tip %}}
  Specifying a custom local IP address for port forwarding allows
  you to run WLST or your browser on a different machine
  than the port forward command.
  {{% /notice %}}

  You can use the `--address` option of the `kubectl port-forward` command
  to listen on specific IP addresses instead of, or in addition to, `localhost`.

  The `--address` option only accepts numeric IP addresses
  or localhost (comma-separated) as a value.

  For example, to enable Administration Console access at
  `http://my-ip-address:32015/console`, the command:

  ```shell
  kubectl port-forward --address my-ip-address pods/domain1-admin-server -n mynamespace 32015:7001
  ```

  See `kubectl port-forward -h` for help and examples.

- _Optionally let `kubectl port-forward` choose the local port_:

  If you don't specify a local port in your port forward command, for example:

  ```shell
  kubectl port-forward pods/domain1-admin-server -n mynamespace :7001
  ```

  Then the command finds a local port number that is not in use,
  and its output is similar to:

  ```text
  Forwarding from 127.0.0.1:63753 -> 7001
  ```

  And the Administration Console is accessible using the `http://localhost:63753/console` URL.

#### Enabling WLST access when local and remote ports do not match

If a local (forwarded) port number is not the same as the administration port number,
then  WLST access will not work by default and you may see a `BEA-000572` RJVM error
in the Administration Server logs:

```text
<Aug 30, 2021 9:33:24,753 PM GMT> <Error> <RJVM> <BEA-000572> <The server rejected a connection attempt JVMMessage from: '-2661445766084484528C:xx.xx.xx.xxR:-5905806878036317188S:domain1-admin-server:domain1:admin-server' to: '0B:xx.xx.xx.xx:[-1,-1,32015,-1,-1,-1,-1]' cmd: 'CMD_IDENTIFY_REQUEST', QOS: '102', responseId: '-1', invokableId: '-1', flags: 'JVMIDs Sent, TX Context Not Sent, 0x1', abbrev offset: '114' probably due to an incorrect firewall configuration or administrative command.><
```

To work around the problem, configure the `JAVA_OPTIONS` environment variable
for your Administration Server with the `-Dweblogic.rjvm.enableprotocolswitch=true` system property
in your domain resource YAML.
For more information on this switch, refer to [MOS 'Doc 860340.1'](https://support.oracle.com/rs?type=doc&id=860340.1).

#### Terminating port forwarding

A port-forward connection is active only while the `kubectl port-forward` command is running.

You can terminate the port-forward connection by pressing CTRL+C in the terminal where the port forward command is running.

If you run the command in the background, then you can kill the process with the `kill -9 <pid>` command. For example:

```text
$ ps -ef | grep port-forward
oracle   27072 25312  1 21:45 pts/3    00:00:00 kubectl -n mynamespace port-forward pods/domain1-admin-server 32015:7001
oracle   27944 11417  0 21:45 pts/1    00:00:00 grep --color=auto port-forward
$ kill -9 27072
```
