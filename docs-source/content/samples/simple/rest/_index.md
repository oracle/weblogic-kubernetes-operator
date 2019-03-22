---
title: "REST APIs"
date: 2019-02-23T17:32:31-05:00
weight: 4
description: "Sample for generating a self-signed certificate and private key that can be used for the operator's external REST API."
---

### Sample to create certificate and key

When a user enables the operator's external REST API (by setting
`externalRestEnabled` to `true` when installing or upgrading the operator Helm chart), the user also needs
to provide the certificate(s) and private key used for the SSL/TLS identity on the external REST API endpoint by creating a
kubernetes `tls secret` and using that secret's name with the operator Helm chart values.

This sample script generates a self-signed certificate and private key that can be used
for the operator's external REST API when experimenting with the operator.

{{% notice warning %}}
The certificate and key generated with this script should ***not*** be used in a production environment.
{{% /notice %}}

The syntax of the script is:
```
$ kubernetes/samples/scripts/rest/generate-external-rest-identity.sh \
  -a <SANs> -n <operator-namespace> [-s <secret-name>]
```

Where `<SANs>` lists the subject alternative names to put into the generated self-signed
certificate for the external WebLogic Operator REST HTTPS interface, `<operator-namespace>` should match
the namespace where the operator will be installed, and optionally the secret name, which defaults
to `weblogic-operator-external-rest-identity`.

You should include the addresses of all masters and load balancers
(i.e. what a client specifies to access the external REST endpoint)
in the subject alternative name list. In addition, each name must be prefaced
by `DNS:` for a host name, or `IP:` for an address, as with this example:
```
-a "DNS:myhost,DNS:localhost,IP:127.0.0.1"
```

The external certificate and key can be changed after installation of the operator. For
more information, see [updating operator external certificate]({{<relref "/security/certificates.md#updating-operator-external-certificate">}})
in the ***Security*** section.

The script as used below will create the `tls secret` named `weblogic-operator-identity` in the namespace `weblogic-operator-ns` using a self-signed
certificate and private key:
```
$ echo "externalRestEnabled: true" > my_values.yaml
$ generate-external-rest-identity.sh \
  -a "DNS:${HOSTNAME},DNS:localhost,IP:127.0.0.1" \
  -n weblogic-operator-ns -s weblogic-operator-identity >> my_values.yaml
#
$ kubectl -n weblogic-operator-ns describe secret weblogic-operator-identity
#
$ helm install kubernetes/charts/weblogic-operator --name my_operator \
  --namespace weblogic-operator-ns --values my_values.yaml --wait
```
