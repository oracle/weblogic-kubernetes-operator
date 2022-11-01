---
title: "Operator prerequisites"
date: 2019-02-23T16:40:54-05:00
description: "Review the prerequisites for the current release of the operator."
weight: 2
---

For the current production release {{< latestVersion >}}:

* Kubernetes 1.19.15+, 1.20.11+, 1.21.5+, 1.22.5+, 1.23.4+, and 1.24.0+ (check with `kubectl version`).
* Flannel networking v0.13.0-amd64 or later (check with `docker images | grep flannel`), Calico networking v3.16.1 or later,
 *or* OpenShift SDN on OpenShift 4.3 systems.
* Docker 19.03.1+ (check with `docker version`) *or* CRI-O 1.20.2+ (check with `crictl version | grep RuntimeVersion`).
* Helm 3.3.4+ (check with `helm version --client --short`).
* For domain home source type `Model in Image`, WebLogic Deploy Tooling 1.9.11+.
* Either Oracle WebLogic Server 12.2.1.3.0 with patch 29135930, Oracle WebLogic Server 12.2.1.4.0, or Oracle WebLogic Server 14.1.1.0.0.
   * The existing WebLogic Server General Availability image, `container-registry.oracle.com/middleware/weblogic:12.2.1.3`,
   has this patch applied.
   {{% notice warning %}}
   The sample image names throughout the documentation are General Availability (GA) images. GA images are suitable for demonstration and development purposes _only_ where the environments are not available from the public Internet; they are **not acceptable for production use**. In production, you should always use CPU (patched) images from [OCR]({{< relref "/userguide/base-images/ocr-images.md" >}}) or create your images using the [WebLogic Image Tool]({{< relref "/userguide/base-images/custom-images#create-a-custom-base-image" >}}) (WIT) with the `--recommendedPatches` option. For more guidance, see [Apply the Latest Patches and Updates](https://www.oracle.com/pls/topic/lookup?ctx=en/middleware/standalone/weblogic-server/14.1.1.0&id=LOCKD-GUID-2DA84185-46BA-4D7A-80D2-9D577A4E8DE2) in _Securing a Production Environment for Oracle WebLogic Server_. For details on how to obtain or create the image, see [WebLogic images]({{< relref "/userguide/base-images/_index.md" >}}).
   {{% /notice %}}
   * Check the WLS version and patches using the [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/userguide/tools/inspect-image/) `inspect` command: `imagetool inspect --image=container-registry.oracle.com/middleware/weblogic:12.2.1.3 --patches`. For more information, see [Inspect images]({{< relref "/userguide/base-images/ocr-images#inspect-images" >}}).
* Container images based on Oracle Linux 8 are now supported. The Oracle Container Registry hosts container images
  based on both Oracle Linux 7 and 8, including Oracle WebLogic Server 14.1.1.0.0 images based on Java 8 and 11.
* You must have the `cluster-admin` role to install the operator.  The operator does
  not need the `cluster-admin` role at runtime. For more information,
  see the role-based access control, operator
  [RBAC]({{< relref "/userguide/managing-operators/rbac.md" >}}) documentation.
* We do not currently support running WebLogic in non-Linux containers.

See also [Supported environments]({{< relref "userguide/platforms/environments.md" >}}) for environment and licensing requirements.
