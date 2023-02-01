---
title: "Pod and container security"
date: 2019-03-08T19:00:49-05:00
weight: 1
description: "Pod and container security."
---

The WebLogic Kubernetes Operator [enforces pod and container security best practices](https://kubernetes.io/docs/concepts/security/pod-security-standards/)
for the pods and containers that the operator creates for WebLogic Server instances, the init container for
auxiliary images, sidecar containers for Fluentd or the WebLogic Monitoring Exporter, and the introspection job.

Beginning with operator version 4.0.5, the operator adds the following pod-level `securityContext` content:

```yaml
securityContext:
  seccompProfile:
    type: RuntimeDefault 
```

The operator also adds the following container-level `securityContext` content to each container:

```yaml
securityContext:
  runAsUser: 1000
  runAsNonRoot: true           
  privileged: false
  allowPrivilegeEscalation: false
  capabilities:
    drop:
    - ALL
```

On OpenShift environments, the operator omits the `runAsUser` element.

Customers can [configure pod and container generation](https://oracle.github.io/weblogic-kubernetes-operator/managing-domains/domain-resource/#domain-and-cluster-spec-elements)
for WebLogic Server instances using the `serverPod` element in the Domain resource. If specified, the operator will use the
`serverPod.podSecurityContext` or `serverPod.containerSecurityContext` content from the Domain resource rather than using the default content shown previously.
