---
title: "Backward compatibility"
date: 2019-02-23T17:26:09-05:00
draft: false
weight: 9
---

Starting with the 2.0.1 release, operator releases must be backward compatible with respect to the domain resource schema, operator Helm chart input values, configuration overrides template, Kubernetes resources created by the operator Helm chart, Kubernetes resources created by the operator, and the operator REST interface.  We will maintain compatibility for three releases, except in the case of a clearly communicated deprecated feature, which will be maintained for one release after a replacement is available.
