# Creating credentials for a WebLogic domain

This sample demonstrates how to create a Kubernetes secret containing the
credentials for a WebLogic domain.  The operator expects this secret to be
named following the pattern `domainUID-weblogic-credentials`, where `domainUID`
is the unique identifier of the domain.  It must be in the same namespace
that the domain will run in.

To use the sample, run the command:

```
$ ./create-weblogic-credentials.sh -u username -p password -d domainUID -n namespace -s secretName
```

The parameters are as follows:

```  
  -u user name, must be specified.
  -p password, must be specified.
  -d domainUID, optional. The default value is domain1. If specified, the secret will be labeled with the domainUID unless the given value is an empty string.
  -n namespace, optional. Use the default namespace if not specified.
  -s secretName, optional. If not specified, the secret name will be determined based on the domainUID value.
```

This creates a `generic` secret containing the user name and password as literal values.

You can check the secret with the `kubectl get secret` command.  An example is shown below,
including the output:

```
$ kubectl -n domain-namespace-1 get secret domain1-weblogic-credentials -o yaml
apiVersion: v1
data:
  password: d2VsY29tZTE=
  username: d2VibG9naWM=
kind: Secret
metadata:
  creationTimestamp: 2018-12-12T20:25:20Z
  labels:
    weblogic.domainName: domain1
    weblogic.domainUID: domain1
  name: domain1-weblogic-credentials
  namespace: domain-namespace-1
  resourceVersion: "5680"
  selfLink: /api/v1/namespaces/domain-namespace-1/secrets/domain1-weblogic-credentials
  uid: 0c2b3510-fe4c-11e8-994d-00001700101d
type: Opaque

```
