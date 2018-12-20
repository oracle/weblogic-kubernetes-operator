# Sample to create certificates and keys for the operator

When a user enables the operator's external REST API (by setting
`externalRestEnabled` to `true` when installing the operator Helm chart), the user needs
to provide the certificate and private key for the API's SSL identity (by setting
`externalOperatorCert` and `externalOperatorKey` to the base64 encoded PEM of the cert and
key when installing the operator Helm chart).

This sample script generates a self-signed certificate and private key that can be used
for the operator's external REST API when experimenting with the operator.  They should
not be used in a production environment.

The syntax of the script is:
```
$ kubernetes/samples/scripts/generate-external-rest-identity.sh <subject alternative names>
```

Where `<subject alternative names>` lists the subject alternative names to put into the generated
self-signed certificate for the external WebLogic Operator REST HTTPS interface.  Each must be prefaced
by `DNS:` (for a name) or `IP:` (for an address), for example:
```
DNS:myhost,DNS:localhost,IP:127.0.0.1
```

You should include the addresses of all masters and load balancers in this list.  The certificate
cannot be conveniently changed after installation of the operator.

The script prints out the base64 encoded PEM of the generated certificate and private key
in the same format that the operator Helm chart's `values.yaml` requires.

Example usage:
```
$  generate-external-rest-identity.sh IP:127.0.0.1 > my_values.yaml
$  echo "externalRestEnabled: true" >> my_values.yaml
   ...
$  helm install kubernetes/charts/weblogic-operator --name my_operator --namespace my_operator-ns --values my_values.yaml --wait
```
