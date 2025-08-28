{{< table_of_contents >}}

### Introduction

The operator allows you to use external vault for the secrets that are managed by the operator.
The secrets managed by operator include

```yaml
spec:
  webLogicCredentialsSecret:
    name: sample-domain1-weblogic-credentials
    
  configuration:
    secrets: [ sample-domain1-rcu-access ]      
    model:
      runtimeEncryptionSecret: sample-domain1-runtime-encryption-secret
    opss:
      walletPasswordSecret: sample-domain1-opss-wallet-password-secret
      walletFileSecret: sample-domain1-opss-walletfile-secret

```

During runtime the server pods will pull the secrets from the external vault configured.
Note:  if you have setup your own Kubernetes native secrets to be mounted on a path of inject
into an environment variable, operator will not be able to change the standard behavior of the
Kubernetes platform.

# Prerequiste

Currently, the operator only supports Hashicorp and OCI vault.

In order to integrate with external vault, you must have

1. The external vault must support REST API.
2. The external vault must be able to authenticate with Kubernetes service account.
2. The same service account also must have permission to read and list secrets on the vault.
3. The operator service account must be granted the authenticate, read access for the secrets, at least for reading the `webLogicCredentialsSecret` for the domain.
4. The server pod of the domain must be granted the authenticate, read and list access for the secrets.
5. You must specify the `serviceAccountName` for the all `serverPod`.
6. The service account used for the `serverPod` must also have privilege to read the domain resource, the operator use it to get the name of the secrets.

# Set up

For HashiCorp vault:

```yaml
  spec:
    serverPod:
       serviceAccountName: external-secrets-sa

    externalSecrets:
      hashiCorpVault:
         enabled: true
         url: "http://vault.vault.svc.cluster.local:8200"
         secretPath: "secret/data"
         role: "eso-role"

```

| field              | value                                                                                                                         |
|--------------------|-------------------------------------------------------------------------------------------------------------------------------|
| serviceAccountName | The name of the service account for the pod, the service account that has the permission to authenticate and access the vault |
| url                | The base url for accessing the vault.                                                                                         |
| secretPath         | The path to access the secret in the vault, without any leading slash.                                                        |
| role               | The role that has been granted the access.                                                                                    |

The above example shows the Hashicorp vault has been set up with:

```
vault write auth/kubernetes/config \
  kubernetes_host="https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT" \
  kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt \
  issuer="https://kubernetes.default.svc.cluster.local"

vault policy write eso-policy - <<EOF
  path "secret/data/*" {
    capabilities = ["read", "list"]
  }
 EOF

vault write auth/kubernetes/role/eso-role \
  bound_service_account_names=external-secrets-sa, sample-weblogic-operator-sa \
  bound_service_account_namespaces=sample-domain1-ns, sample-weblogic-operator-ns \
  policies=eso-policy \
  ttl=1h

vault kv put secret/sample-domain1-weblogic-credentials username=***** password=******
vault kv put secret/sample-domain1-runtime-encryption-secret password=*****
vault kv put secret/sample-domain1-rcu-access ....
...
vault kv get secret/data/sample-domain1-weblogic-credentials

```

The url is the access point of the vault service, for example

```
kubectl -n vault get services

NAME                       TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)             AGE
vault                      ClusterIP   10.43.250.194   <none>        8200/TCP,8201/TCP   29d

```

