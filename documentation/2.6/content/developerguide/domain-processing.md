---
title: "Domain processing"
date: 2019-02-23T17:20:20-05:00
draft: false
weight: 8
---


When the operator starts, it lists all existing Domain resources and processes these domains to create the necessary Kubernetes resources, such as Pods and Services, if they don't already exist.  This initialization also includes looking for any stranded resources that, while created by the operator, no longer correlate with a Domain resource.

After this, the operator starts watches for changes to Domain resources and any changes to other resources created by the operator.  When a watch event is received, the operator processes the modified Domain resource to again bring the runtime presence in to alignment with the desired state.

The operator ensures that at most one `Fiber` is running for any given Domain.  For instance, if the customer modifies a Domain resource to trigger a rolling restart, then the operator will create a `Fiber` to process this activity.  However, if while the rolling restart is in process, the customer makes another change to the Domain resource, such as to increase the `replicas` field for a cluster, then the operator will cancel the in-flight `Fiber` and replace it with a new `Fiber`.  This replacement processing must be able to handle taking over for the cancelled work regardless of where the earlier processing may have been in its flow.  Therefore, domain processing always starts at the beginning of the "make right" flow without any state other than the current Domain resource.

Finally, the operator periodically lists all Domains and rechecks them.  This is a backstop against the possibility that a watch event is missed, such as because of a temporary network outage.  Recheck activities will not interrupt already running processes for a given Domain.
