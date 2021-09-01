---
title: "Use WLST"
date: 2019-02-23T17:39:19-05:00
draft: false
weight: 3
description: "You can use the WebLogic Scripting Tool (WLST) to manage a domain running in Kubernetes."
---


You can use the WebLogic Scripting Tool (WLST) to manage a domain running in Kubernetes. To use WLST for a domain running in Kubernetes, you can:

- Configure the Administration Server to [expose a T3 channel](#configure-the-administration-server-to-expose-a-t3-channel).
- Use a [kubectl port-forward connection](#use-a-kubectl-port-forward-connection).


#### Configure the Administration Server to expose a T3 channel

You can use the `exposeAdminT3Channel` setting when creating the domain to configure the Administration Server to expose a T3 channel.  For example, if the `domainUID` is `domain1`, and the Administration Server name is `admin-server`, then the service would be called:

```
domain1-admin-server-ext
```

This service will be in the same namespace as the domain.  The external port number can be obtained by checking this service’s `nodePort`:

```shell
$ kubectl get service domain1-admin-server-ext -n domain1 -o jsonpath='{.spec.ports[0].nodePort}'
```
```
30012
```

In this example, the `nodePort` is `30012`.  If the Kubernetes server’s address was `kubernetes001`, then WLST can connect to `t3://kubernetes001:30012` as shown below:

```shell
$ ~/wls/oracle_common/common/bin/wlst.sh
```
```
Initializing WebLogic Scripting Tool (WLST) ...

Welcome to WebLogic Server Administration Scripting Shell

Type help() for help on available commands

wls:/offline> connect('weblogic','*password*','t3:// kubernetes001:30012')
Connecting to t3:// kubernetes001:30012 with userid weblogic ...
Successfully connected to Admin Server "admin-server" that belongs to domain "base_domain".

Warning: An insecure protocol was used to connect to the server.
To ensure on-the-wire security, the SSL port or Admin port should be used instead.

wls:/base_domain/serverConfig/> exit()


Exiting WebLogic Scripting Tool.
```

#### Use a kubectl port-forward connection
1. Forward a local port to the administration port of the Administration Server Pod according to these [instructions.]({{< relref "/userguide/managing-domains/accessing-the-domain/port-forward.md#forward-a-local-port-to-an-administration-port-on-the-administration-server-pod" >}}).
{{% notice note %}}
If the local (forwarded) port number is not the same as the Administration port number, then the WLST access will not work by default and you will see below `BEA-000572` RJVM error in the administration server logs. In this case, you will need to add the `-Dweblogic.rjvm.enableprotocolswitch=true` argument to the command line JAVA_OPTIONS for the Administration Server to enable WLST access. Refer to [MOS 'Doc 860340.1'](https://support.oracle.com/rs?type=doc&id=860340.1) for more information on this switch.
```text
<Aug 30, 2021 9:33:24,753 PM GMT> <Error> <RJVM> <BEA-000572> <The server rejected a connection attempt JVMMessage from: '-2661445766084484528C:xx.xx.xx.xxR:-5905806878036317188S:domain1-admin-server:domain1:admin-server' to: '0B:xx.xx.xx.xx:[-1,-1,32015,-1,-1,-1,-1]' cmd: 'CMD_IDENTIFY_REQUEST', QOS: '102', responseId: '-1', invokableId: '-1', flags: 'JVMIDs Sent, TX Context Not Sent, 0x1', abbrev offset: '114' probably due to an incorrect firewall configuration or administrative command.></pre>
```
{{% /notice %}}

2. The WLST can connect using the hostname or the defined local IP address and the local port in the previous step. For example:

   ```
   t3://${HOSTNAME}:${LOCAL_PORT}/
   ```
   Where:

     * `${HOSTNAME}` is the hostname or the defined IP address on the machine where the kubectl port-forward command is running.

     * `${LOCAL_PORT}` is the local port where the kubectl port-forward command is running.
