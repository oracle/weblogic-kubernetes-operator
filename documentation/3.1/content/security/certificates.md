---
title: "Certificates"
date: 2019-03-06T21:14:18-05:00
weight: 1
description: "Operator SSL/TLS certificate handling"
---

#### Updating operator external certificates

If the operator needs to update the external certificate and key currently
being used or was installed without an external REST API SSL/TLS identity,
the `helm upgrade` command is used to restart the operator
with the new or updated Kubernetes `tls secret` that contains
the desired certificates.

The operator _requires_ a restart in order to begin using the new or updated external
certificate. The Helm `--recreate-pods` flag is used to cause the existing
Kubernetes Pod to be terminated and a new pod to be started with the updated configuration.

For example, if the operator was installed with the Helm release name `weblogic-operator`
in the namespace `weblogic-operator-ns` and the Kubernetes `tls secret` is named
`weblogic-operator-cert`, the following commands can be used to update the operator
certificates and key:

```bash
$ kubectl create secret tls weblogic-operator-cert -n weblogic-operator-ns \
  --cert=<path-to-certificate> --key=<path-to-private-key>
```

```bash
$ helm get values weblogic-operator -n weblogic-operator-ns

$ helm -n weblogic-operator-ns upgrade weblogic-operator kubernetes/charts/weblogic-operator \
  --wait --recreate-pods --reuse-values \
  --set externalRestEnabled=true \
  --set externalRestIdentitySecret=weblogic-operator-cert
```


#### Additional reading
* [Configure the external REST interface SSL/TLS identity]({{<relref "/userguide/managing-operators/_index.md#optional-configure-the-operators-external-rest-https-interface">}})
* [REST interface configuration settings]({{<relref "/userguide/managing-operators/using-the-operator/using-helm/_index.md#rest-interface-configuration">}})
* [Sample to create external certificate and key]({{<relref "/samples/simple/rest/_index.md#sample-to-create-certificate-and-key">}})
