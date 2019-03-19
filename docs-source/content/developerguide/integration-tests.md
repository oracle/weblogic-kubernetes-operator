---
title: "Integration tests"
date: 2019-02-23T17:23:22-05:00
draft: false
weight: 4
---


The project includes integration tests that can be run against a Kubernetes cluster.  If you want to use these tests, you will need to provide your own Kubernetes cluster.  The Kubernetes cluster must meet the version number requirements and have Helm installed.  Ensure that the operator Docker image is in a Docker registry visible to the Kubernetes cluster.


You will need to obtain the `kube.config` file for an administrative user and make it available on the machine running the build.  To run the tests, update the `KUBECONFIG` environment variable to point to your config file and then execute:

```bash
$ mvn clean verify -P java-integration-tests
```

{{% notice note %}}
When you run the integrations tests, they do a cleanup of any operator or domains on that cluster.   
{{% /notice %}}
