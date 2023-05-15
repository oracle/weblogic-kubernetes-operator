---
title: "Prerequisites"
date: 2019-02-23T17:32:31-05:00
weight: 1
description: "Follow these prerequisite steps for all domain types."
---

### Contents

- [Prerequisites for WLS and JRF domain types](#prerequisites-for-wls-and-jrf-domain-types)
- [Additional prerequisites for JRF domains](#additional-prerequisites-for-jrf-domains)


### Prerequisites for WLS and JRF domain types


{{< readfile file="/samples/domains/includes/copy-samples-prerequisites.txt" >}}

4. Copy the Domain on PV sample to a new directory; for example, use directory /tmp/sample.
   ```
   $ mkdir -p /tmp/sample
   ```

   ```
   $ cp -r /tmp/weblogic-kubernetes-operator/kubernetes/samples/scripts/create-weblogic-domain/domain-on-pv/* /tmp/sample
   ```
   **Note**: We will refer to this working copy of the sample as `/tmp/sample`; however, you can use a different location.
   {{< rawhtml >}}
   <a name="resume"></a>
   {{< /rawhtml >}}

{{< readfile file="/samples/domains/includes/image-creation-prerequisites.txt" >}}

{{< readfile file="/samples/domains/includes/prerequisites.txt" >}}
