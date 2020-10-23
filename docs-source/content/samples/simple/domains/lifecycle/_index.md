---
title: "Start and stop managed server, cluster and domain"
date: 2019-02-23T17:32:31-05:00
weight: 8
description: "Start and stop managed server, clusters in a deployed domain and the entire deployed domain."
---

### Domain lifecycle sample scripts
Beginning with operator version 3.1.0, the WebLogic Server Kubernetes Operator project provides a set of sample scripts to shut-down or start-up a specific managed-server or cluster in a deployed Domain or the entire deployed Domain. Please note that Domain must have been created and deployed before these scripts can start or stop managed servers, clusters or the Domain. These sample scripts are located in the `kubernetes/samples/scripts/domain-lifecycle` directory. They can be helpful when scripting the lifecycle of a WebLogic Server Domain. Please see the [README](https://github.com/oracle/weblogic-kubernetes-operator/tree/master/kubernetes/samples/scripts/domain-lifecycle/README.md) associated with these sample scripts for more details.
