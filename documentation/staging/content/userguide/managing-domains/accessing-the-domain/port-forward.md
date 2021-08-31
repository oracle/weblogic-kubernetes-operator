---
title: "Use Port Forwarding"
date: 2019-02-23T17:39:15-05:00
draft: false
weight: 4
description: "You can use Port Forwarding to access the Administration console and WLST."
---


Beginning with operator version 4.0.0, you can use the `kubectl port-forward` command to allow external access to the WebLogic Administration Console and WLST access to manage a domain running in Kubernetes. You can use this method to access the Administration console locally or connect using WLST for investigating the issues without exposing them to the public internet. See [port-forward](https://kubernetes.io/docs/reference/generated/kubectl/kubectl-commands#port-forward) in the Kubernetes reference documentation for `kubectl port-forward` usage and examples.

The operator automatically adds the required network channels with a 'localhost' address for each existing admin protocol capable port to enable this access. See [Additional network channels created for kubectl port-forward](#additional-network-channels-created-for-kubectl-port-forward) for network channels created by the operator.  You can disable this behavior by setting the `domain.spec.adminServer.adminChannelPortForwardingEnabled` attribute in the domain resource to `false`. Run the `kubectl explain domain.spec.adminServer.adminChannelPortForwardingEnabled` command or see in the domain resource [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/documentation/domains/Domain.md) for more details.

#### Forward a local port to an administration port on the Administration Server Pod
The `kubectl port-forward` command allows using the resource type/name to select a matching pod to port forward to. You also need to define the local and remote port numbers:

```
kubectl port-forward TYPE/NAME [options] LOCAL_PORT:REMOTE_PORT
```

For the WebLogic Administration Console access, you need to forward a local port (on the machine where `port-forward` command is run) to the Administration Server Pod. For example, if you have a WebLogic domain with UID `domain1` running in namespace `mynamespace` and the Administration Server name is `admin-server` with non-SSL listen port 7001, then you can run the below command to forward a local port (port 32015 in this example) to the admin port (port 7001 in this example) of the Administration Server Pod. In this scenario, the Kubernetes API listens on local port 32015 and forwards data to port 7001 on the Administration Server Pod.

```shell
kubectl port-forward pods/domain1-admin-server -n mynamespace 32015:7001
```
or

```shell
kubectl port-forward service/domain1-admin-server -n mynamespace 32015:7001
```
The output is similar to this:

```
Forwarding from 127.0.0.1:32015 -> 7001
```

You can access the Administration console at `http://localhost:32015/console` URL on the machine where the above 'kubectl port-forward' command is run and you can use WLST to connect to `t3://localhost:32015` as shown below:

```shell
$ ~/wls/oracle_common/common/bin/wlst.sh
```
```
Initializing WebLogic Scripting Tool (WLST) ...

Welcome to WebLogic Server Administration Scripting Shell

Type help() for help on available commands

wls:/offline> connect('weblogic','*password*','t3://localhost:32015')
Connecting to t3://localhost:32015 with userid weblogic ...
Successfully connected to Admin Server "admin-server" that belongs to domain "base_domain".

Warning: An insecure protocol was used to connect to the server.
To ensure on-the-wire security, the SSL port or Admin port should be used instead.

wls:/base_domain/serverConfig/> exit()
```

If the WebLogic administration port is enabled on the Administration Server, then you will need to forward the local port to the Administration port. In this case, the Administration Console access will require using `https` protocol and WLST access will require using `t3s` protocol. Similarly, when the SSL port is enabled, using the SSL port requires using the `https` and `t3s` protocol for Console and WLST access respectively.

{{% notice note %}}
A port-forward session ends once the Pod instance fails or restarts. You can rerun the same command to establish a new port forwarding session and resume forwarding.
{{% /notice %}}

{{% notice note %}}
If the local (forwarded) port number is not the same as the Administration port number, then the WLST access will not work by default and you will see below `BEA-000572` RJVM error in the administration server logs. You can add `-Dweblogic.rjvm.enableprotocolswitch=true` argument to the command line JAVA_OPTIONS for the Administration Server to enable this access. Refer to [MOS 'Doc 860340.1'](https://support.oracle.com/rs?type=doc&id=860340.1) for more information on this switch.
```text
<Aug 30, 2021 9:33:24,753 PM GMT> <Error> <RJVM> <BEA-000572> <The server rejected a connection attempt JVMMessage from: '-2661445766084484528C:xx.xx.xx.xxR:-5905806878036317188S:domain1-admin-server:domain1:admin-server' to: '0B:xx.xx.xx.xx:[-1,-1,32015,-1,-1,-1,-1]' cmd: 'CMD_IDENTIFY_REQUEST', QOS: '102', responseId: '-1', invokableId: '-1', flags: 'JVMIDs Sent, TX Context Not Sent, 0x1', abbrev offset: '114' probably due to an incorrect firewall configuration or administrative command.></pre>
```
{{% /notice %}}

#### Specify Local IP Address for Port Forwarding
You can use the `--address` option of the `kubectl port-forward` command to listen on the localhost using the defined IP address. The `--address` option only accepts IP addresses or localhost (comma-separated) as a value. See `kubectl port-forward -h` for help and examples.

Below is an example command that uses a defined IP address.

```shell
kubectl port-forward --address my-ip-address pods/domain1-admin-server -n mynamespace 32015:7001
```
The Administration console can be accessed at `http://my-ip-address:32015/console` URL after running the above command. 

#### Optionally let kubectl choose the local port
If you don't need a specific local port, then you can let kubectl choose and allocate the local port, with the below syntax:

```shell
kubectl port-forward pods/domain1-admin-server -n mynamespace :7001
```
The kubectl tool finds a local port number that is not in use. The output is similar to:

```
Forwarding from 127.0.0.1:63753 -> 7001
```
In this example, the Administration console is accessible using `http://localhost:63753/console` URL.

#### Additional network channels created for `kubectl port-forward`
When the Administrative channel port forwarding is enabled (default), the operator automatically adds the following network channels using configuration overrides during introspection for 'kubectl port-forward'. Set the `domain.spec.adminServer.adminChannelPortForwardingEnabled` attribute in the domain resource to `false` if you want to disable this behavior.

For domains with default channel using non-SSL traffic:
Name | Listen Address | Port | Protocol
--- | --- | --- | ---
internal-t3 | localhost | Server listening port | t3

For domains with default channel for non-SSL traffic and default secure channel for SSL traffic:
Name | Listen Address | Port | Protocol
--- | --- | --- | ---
internal-t3 | localhost | Server listening port | t3
internal-t3s | localhost | Server SSL listening port | t3s

If the WebLogic administration port is enabled on the Administration Server:
Name | Listen Address | Port | Protocol
--- | --- | --- | ---
internal-admin | localhost | WebLogic administration port | admin

If a custom admin channel is configured on the Administration Server:
Name | Listen Address | Port | Protocol
--- | --- | --- | ---
internal-admin | localhost | Custom administration port | admin

**NOTE:** The additional network channels are created only for the Administration Server (and not for the managed servers).

#### Istio Enabled Domains
For the Istio enabled domains, the operator already adds a network channel with localhost listen address. Hence additional network channels are not created for `kubectl port-forward` when Istio support is enabled. See [How Istio-enabled domains differ from regular domains]({{< relref "/userguide/istio/istio#how-istio-enabled-domains-differ-from-regular-domains" >}}) for more details.

