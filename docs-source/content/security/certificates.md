---
title: "Certificates"
date: 2019-03-06T21:14:18-05:00
weight: 1
description: "SSL/TLS certificate handling for the WebLogic operator."
---

#### Updating operator external certificate

If the operator needs to update the external certificate and key currently
being used or was installed without an external REST API SSL/TLS identity,
the `helm upgrade` command is used to re-start the operator
with the new or updated kubernetes `tls secret` that contains
the desired certificate(s).

The operator _requires_ a re-start in order to begin using the new or udpated external
certificate. The Helm `--recreate-pods` flag is used to cause the existing
kubernetes pod to be terminated and a new pod to be started with the updated configuration.

For example, if the operator was installed with the Helm release name `weblogic-operator`
in the namespace `weblogic-operator-ns` and the kubernetes `tls secret` is named
`weblogic-operator-cert`, the following commands can be used to update the operator
certificate(s) and key:
```bash
$ kubectl create secret tls weblogic-operator-cert -n weblogic-operator-ns \
  --cert=<path-to-certificate> --key=<path-to-private-key>

$ helm get values weblogic-operator

$ helm upgrade --wait --recreate-pods --reuse-values \
  --set externalRestEnabled=true \
  --set externalRestIdentitySecret=weblogic-operator-cert \
  weblogic-operator kubernetes/charts/weblogic-operator
```
#### Additional reading
* [Configure the external REST interface SSL/TLS identity]({{<relref "/userguide/managing-operators/_index.md#optional-configure-the-operator-s-external-rest-https-interface">}})
* [REST interface configuration settings]({{<relref "/userguide/managing-operators/using-the-operator/using-helm/_index.md#rest-interface-configuration">}})
* [Sample to create external certificate and key]({{<relref "/samples/simple/rest/_index.md#sample-to-create-certificate-and-key">}})
