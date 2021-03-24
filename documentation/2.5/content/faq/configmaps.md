---
title: "Providing access to a Config Map"
date: 2020-01-07T15:02:28-05:00
draft: false
weight: 11
---
> I need to provide an instance with access to a Config Map.

Configuration files can be supplied to Kubernetes pods and jobs by a 
[ConfigMap](https://kubernetes.io/docs/tasks/configure-pod-container/configure-pod-configmap/#create-a-configmap), 
which consists of a set of key-value pairs. Each entry may be accessed by one or more operator-managed nodes
as a read-only text file. Access can be provided across the domain, within a single cluster, or for a single server.
In each case, the access is configured within the `serverPod` element of the desired scope. 

For example, given
a ConfigMap named `my-map` with entries `key-1` and `key-2`, you can provide access to both values as separate files
in the same directory within the `cluster-1` cluster with the following
in your [domain resource](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/documentation/domains/Domain.md):
 

```
  clusters:
  - clusterName: cluster-1
    serverPod:
      volumes:
      - name: my-volume-1
        configMap:
          name: my-map
          items: 
          - key: key-1
            path: first
          - key: key-2
            path: second
      volumeMounts:
      - name: my-volume-1
        mountPath: /weblogic-operator/my

```
This provides access to two files, found at paths `/weblogic-operator/my/first` and `/weblogic-operator/my/second`. 
Both a `volume` and a `volumeMount` entry are required, and must have the same name. The name of the `ConfigMap` is 
specified in the `name` field under the `configMap` entry. The `items` entry is an array,
in which each entry maps a `ConfigMap` key to a file name under the directory specified as `mountPath` under a `volumeMount`.

