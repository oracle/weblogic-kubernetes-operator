# Creating RCU credentials for a Fusion Middleware domain

This sample demonstrates how to create a Kubernetes secret containing the
RCU credentials for a Fusion Middleware domain.  The operator expects this secret to be
named following the pattern `domainUID-rcu-credentials`, where `domainUID`
is the unique identifier of the domain.  It must be in the same namespace
that the domain will run in.

To use the sample, run the command:

```
$ ./create-rcu-credentials.sh \
  -u username \
  -p password \
  -a sys_username \
  -q sys_password \
  -d domainUID \
  -n namespace \
  -s secretName
```

The parameters are as follows:

```  
  -u username for schema owner (regular user), must be specified.
  -p password for schema owner (regular user), must be specified.
  -a username for SYSDBA user, must be specified.
  -q password for SYSDBA user, must be specified.
  -d domainUID, optional. The default value is domain1. If specified, the secret will be labeled with the domainUID unless the given value is an empty string.
  -n namespace, optional. Use the default namespace if not specified.
  -s secretName, optional. If not specified, the secret name will be determined based on the domainUID value.
```

This creates a `generic` secret containing the user name and password as literal values.

You can check the secret with the `kubectl describe secret` command.  An example is shown below,
including the output:

```
$ kubectl -n domain-namespace-1 describe secret domain1-rcu-credentials -o yaml
Name:         domain1-rcu-credentials
Namespace:    default
Labels:       weblogic.domainName=domain1
              weblogic.domainUID=domain1
Annotations:  <none>

Type:  Opaque

Data
====
password:      12 bytes
sys_password:  12 bytes
sys_username:  3 bytes
username:      4 bytes
```
