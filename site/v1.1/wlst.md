> **WARNING** This documentation is for version 1.1 of the operator.  To view documenation for the current release, [please click here](/site).

# Using WLST

Note that a video demonstration of using WLST to create a data source is available [here](https://youtu.be/eY-KXEk8rI4).

You can use the WebLogic Scripting Tool (WLST) to manage a domain running in Kubernetes.  If the domain was configured to expose a T3 channel using the `exposeAdminT3Channel` setting when creating the domain, then the matching T3 service can be used to connect.  For example, if the `domainUID` is `domain1`, and the Administration Server name is `admin-server`, then the service would be called:

```
domain1-admin-server-extchannel-t3channel  
```

This service will be in the same namespace as the domain.  The external port number can be obtained by checking this service’s `nodePort`:

```
$ kubectl get service domain1-admin-server-extchannel-t3channel -n domain1 -o jsonpath='{.spec.ports[0].nodePort}'
30012
```

In this example, the `nodePort` is `30012`.  If the Kubernetes server’s address was `kubernetes001`, then WLST can connect to `t3://kubernetes001:30012` as shown below:

```
$ ~/wls/oracle_common/common/bin/wlst.sh

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
