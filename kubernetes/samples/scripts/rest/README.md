# Sample to create certificates and keys for the operator

When a user enables the operator's external REST API (by setting
`externalRestEnabled` to `true` when installing the operator Helm chart), the user needs
to provide the certificate and private key for api's SSL identity too (by creating a
`tls secret` before the installation of the operator helm chart).

This sample script generates a self-signed certificate and private key that can be used
for the operator's external REST api when experimenting with the operator.  They should
not be used in a production environment.

The syntax of the script is:
```shell
$ kubernetes/samples/scripts/rest/generate-external-rest-identity.sh <SANs> -n <namespace> [-s <secret-name> ]
```

Where `<SANs>` lists the subject alternative names to put into the generated self-signed 
certificate for the external WebLogic Operator REST HTTPS interface, <namespace> should match
the namespace where the operator will be installed, and optionally the secret name, which defaults
to `weblogic-operator-external-rest-identity`.  Each must be prefaced
by `DNS:` (for a name) or `IP:` (for an address), for example:
```
DNS:myhost,DNS:localhost,IP:127.0.0.1
```

You should include the addresses of all masters and load balancers in this list.  The certificate
cannot be conveniently changed after installation of the operator.

The script creates the secret in the weblogic-operator namespace with the self-signed 
certificate and private key

Example usage:
```shell
$ generate-external-rest-identity.sh IP:127.0.0.1 -n weblogic-operator > my_values.yaml
$ echo "externalRestEnabled: true" >> my_values.yaml
  ...
$ helm install my_operator kubernetes/charts/weblogic-operator --namespace my_operator-ns --values my_values.yaml --wait
```
