---
title: "Known limitations"
date: 2019-02-23T08:14:59-05:00
weight: 12
draft: false
---

The following sections describe known limitations for WebLogic Kubernetes Operator. Each issue may contain a workaround or an associated issue number.

#### NGINX SSL passthrough ingress service does not work with Kubernetes headless service

**ISSUE**:
When installing NGINX ingress controller with SSL passthrough enabled `--set "controller.extraArgs.enable-ssl-passthrough=true"`, any ingress rule created subsequently, using SSL passthrough to the individual server service, will fail.   

```
$ kubectl -n nginx get services
NAME                                                TYPE           CLUSTER-IP      EXTERNAL-IP     PORT(S)                      AGE
nginx-operator-ingress-nginx-controller-admission   ClusterIP      10.43.234.82    <none>          443/TCP                      3m3s
nginx-operator-ingress-nginx-controller             LoadBalancer   10.43.193.149   192.168.106.2   80:32315/TCP,443:31710/TCP   3m3s
```

For example, after creating the domain, the operator creates a headless Kubernetes service for each server and a headed service for the cluster.  The individual service for each server is headless as the `CLUSTER-IP` is `None`;  the cluster service is headed as the `CLUSTER-IP` has a valid IP address.

```
$ kubectl -n sample-domain1-ns get services
NAME                                 TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)             AGE
sample-domain1-admin-server          ClusterIP   None            <none>        7001/TCP,7002/TCP   23h
sample-domain1-cluster-cluster-1     ClusterIP   10.43.108.163   <none>        8001/TCP,7002/TCP   23h
sample-domain1-managed-server1       ClusterIP   None            <none>        8001/TCP,7002/TCP   23h
```

If you create a passthrough ingress rule to use SSL passthrough to access the admin server, for example:

```
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: console-ssl-passthru
  namespace: sample-domain1-ns
  annotations:
    nginx.ingress.kubernetes.io/ssl-passthrough: 'true'
spec:
  ingressClassName: nginx
  rules:
    - http:
        paths:
          - backend:
              service:
                name: sample-domain1-admin-server
                port:
                  number: 7002
            path: /
            pathType: Prefix
      host: localk8s.com
```

Accessing the WebLogic Console on the admin server, through the ingress controller, will result in an error.

```
curl -k -v -L https://localk8s.com:31710/console
*   Trying 192.168.106.2:31710...
* Connected to localk8s.com (192.168.106.2) port 31710 (#0)
* ALPN: offers h2,http/1.1
* (304) (OUT), TLS handshake, Client hello (1):
* LibreSSL SSL_connect: SSL_ERROR_SYSCALL in connection to localk8s.com:31710
* Closing connection 0
curl: (35) LibreSSL SSL_connect: SSL_ERROR_SYSCALL in connection to localk8s.com:31710
```

This is currently reported as an NGINX bug in https://github.com/kubernetes/ingress-nginx/issues/1718
