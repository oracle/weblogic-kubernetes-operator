> **WARNING** This documentation is for version 1.1 of the operator.  To view documenation for the current release, [please click here](/site).

# Using the operator's REST services

The operator provides a REST server which can be used to get a list of WebLogic domains and clusters and to initiate scaling operations.  Swagger documentation for the REST API is available [here](https://oracle.github.io/weblogic-kubernetes-operator/v1.1/swagger/index.html).

You can access most of the REST services using `GET`, for example:

* To obtain a list of domains, send a `GET` request to the URL `/operator/latest/domains`.
* To obtain a list of clusters in a domain, send a `GET` request to the URL `/operator/latest/domains/<domainUID>/clusters`.

All of the REST services require authentication.  Callers must pass in a valid token header and a CA certificate file.  Callers should pass in the `Accept:/application/json` header.

To protect against Cross Site Request Forgery (CSRF) attacks, the Operator REST API requires that you send in a `X-Requested-By` header when you invoke a REST endpoint that makes a change (for example when you POST to the `/scale` endpoint).  The value is an arbitrary name such as 'MyClient'. For example, when using curl:

```
curl ... -H X-Requested-By:MyClient ... -X POST .../scaling
```

If you do not pass in the X-Requested-By header, then you'll get a 400 (bad request) response without any details explaining why the request is bad.
The X-Requested-By header is not needed for requests that only read, for example when you GET any of the Operator's REST endpoints.

If using `curl`, you can use the `-k` option to bypass the check to verify that the operator's certificate is trusted (instead of `curl --cacert`).

Here is a small BASH script that may help to prepare the necessary token, certificates, and such, to call the operator's REST services:

```
#!/bin/bash
KUBERNETES_SERVER=$1
URL_TAIL=$2

REST_PORT=`kubectl get services -n weblogic-operator -o jsonpath='{.items[?(@.metadata.name == "external-weblogic-operator-svc")].spec.ports[?(@.name == "rest")].nodePort}'`
REST_ADDR="https://${KUBERNETES_SERVER}:${REST_PORT}"
SECRET=`kubectl get serviceaccount weblogic-operator -n weblogic-operator -o jsonpath='{.secrets[0].name}'`
ENCODED_TOKEN=`kubectl get secret ${SECRET} -n weblogic-operator -o jsonpath='{.data.token}'`
TOKEN=`echo ${ENCODED_TOKEN} | base64 --decode`
OPERATOR_CERT_DATA=`grep externalOperatorCert weblogic-operator.yaml | awk '{ print $2 }'`
OPERATOR_CERT_FILE="/tmp/operator.cert.pem"
echo ${OPERATOR_CERT_DATA} | base64 --decode > ${OPERATOR_CERT_FILE}
cat ${OPERATOR_CERT_FILE}

echo "Ready to call operator REST APIs"

STATUS_CODE=`curl \
-v -k \
--cacert ${OPERATOR_CERT_FILE} \
-H "Authorization: Bearer ${TOKEN}" \
-H Accept:application/json \
-X GET ${REST_ADDR}/${URL_TAIL} \
-o curl.out \
--stderr curl.err \
-w "%{http_code}"`

cat curl.err
cat curl.out | jq .
```

To use this script, pass in the Kubernetes server address and then the URL you want to call.   The script assumes `jq` is installed and uses it to format the response.  This can be removed if desired.  The script also prints out quite a bit of useful debugging information in addition to the response.  Here is an example of the output of this script:

```
$ ./rest.sh kubernetes001 operator/latest/domains/domain1/clusters
Ready to call operator REST APIs
Note: Unnecessary use of -X or --request, GET is already inferred.
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
  0     0    0     0    0     0      0      0 --:--:-- --:--:-- --:--:--     0*   Trying 10.139.151.214...
* TCP_NODELAY set
* Connected to kubernetes001 (10.1.2.3) port 31001 (#0)
* ALPN, offering h2
* ALPN, offering http/1.1
* Cipher selection: ALL:!EXPORT:!EXPORT40:!EXPORT56:!aNULL:!LOW:!RC4:@STRENGTH
* error setting certificate verify locations, continuing anyway:
*   CAfile: /tmp/operator.cert.pem
  CApath: none
* TLSv1.2 (OUT), TLS handshake, Client hello (1):
} [512 bytes data]
* TLSv1.2 (IN), TLS handshake, Server hello (2):
{ [81 bytes data]
* TLSv1.2 (IN), TLS handshake, Certificate (11):
{ [799 bytes data]
* TLSv1.2 (IN), TLS handshake, Server key exchange (12):
{ [413 bytes data]
* TLSv1.2 (IN), TLS handshake, Server finished (14):
{ [4 bytes data]
* TLSv1.2 (OUT), TLS handshake, Client key exchange (16):
} [150 bytes data]
* TLSv1.2 (OUT), TLS change cipher, Client hello (1):
} [1 bytes data]
* TLSv1.2 (OUT), TLS handshake, Finished (20):
} [16 bytes data]
* TLSv1.2 (IN), TLS change cipher, Client hello (1):
{ [1 bytes data]
* TLSv1.2 (IN), TLS handshake, Finished (20):
{ [16 bytes data]
* SSL connection using TLSv1.2 / ECDHE-RSA-AES128-GCM-SHA256
* ALPN, server did not agree to a protocol
* Server certificate:
*  subject: CN=weblogic-operator
*  start date: Jan 18 16:30:01 2018 GMT
*  expire date: Jan 16 16:30:01 2028 GMT
*  issuer: CN=weblogic-operator
*  SSL certificate verify result: unable to get local issuer certificate (20), continuing anyway.
> GET /operator/latest/domains/domain1/clusters HTTP/1.1
> Host: kubernetes001:31001
> User-Agent: curl/7.54.0
> Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJ3ZWJsb2dpYy1vcGVyYXRvciIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQ (truncated) 1vcGVyYXRvcjp3ZWJsb2dpYy1vcGVyYXRvciJ9.NgaGR0NbzbJpVXguQDjRKyDBnNTqwgwPEXv3NjWwMcaf0OlN54apHubdrIx6KYz9ONGz-QeTLnoMChFY7oWA6CBfbvjt-GQX6JvdoJYxsQo1pt-E6sO2YvqTFE4EG-gpEDaiCE_OjZ_bBpJydhIiFReToA3-mxpDAUK2_rUfkWe5YEaLGMWoYQfXPAykzFiH4vqIi_tzzyzNnGxI2tUcBxNh3tzWFPGXKhzG18HswiwlFU5pe7XEYv4gJbvtV5tlGz7YdmH74Rc0dveV-54qHD_VDC5M7JZVh0ZDlyJMAmWe4YcdwNQQNGs91jqo1-JEM0Wj8iQSDE3cZj6MB0wrdg
> Accept:application/json
>
  0     0    0     0    0     0      0      0 --:--:--  0:00:01 --:--:--     0< HTTP/1.1 200 OK
< Content-Type: application/json
< Content-Length: 463
<
{ [463 bytes data]
100   463  100   463    0     0    205      0  0:00:02  0:00:02 --:--:--   205
* Connection #0 to host kubernetes001 left intact
{
  "links": [
    {
      "rel": "self",
      "title": "",
      "href": "/operator/latest/domains/domain1/clusters"
    },
    {
      "rel": "canonical",
      "title": "",
      "href": "/operator/latest/domains/domain1/clusters"
    },
    {
      "rel": "parent",
      "title": "",
      "href": "/operator/latest/domains/domain1"
    }
  ],
  "items": [
    {
      "links": [
        {
          "rel": "self",
          "title": "",
          "href": "/operator/latest/domains/domain1/clusters/cluster-1"
        },
        {
          "rel": "canonical",
          "title": "",
          "href": "/operator/latest/domains/domain1/clusters/cluster-1"
        }
      ],
      "cluster": "cluster-1"
    }
  ]
}
```
