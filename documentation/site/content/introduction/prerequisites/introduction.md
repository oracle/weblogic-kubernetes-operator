---
title: "Operator prerequisites"
date: 2019-02-23T16:40:54-05:00
description: "Review the prerequisites for the current release of the operator."
weight: 5
---

For the current production release {{< latestVersion >}}:

* Kubernetes 1.24.0+, 1.25.0+, 1.26.2+, 1.27.2+, 1.28.2+, 1.29.1+, 1.30.1+, and 1.31.1+ (check with `kubectl version`).
* Flannel networking v0.13.0-amd64 or later (check with `docker images | grep flannel`), Calico networking v3.16.1 or later,
 *or* OpenShift SDN on OpenShift 4.3 systems.
* Docker 19.03.1+ (check with `docker version`) *or* CRI-O 1.20.2+ (check with `crictl version | grep RuntimeVersion`).
* Helm 3.3.4+ (check with `helm version --client --short`).
* For domain home source type `Model in Image`, WebLogic Deploy Tooling 1.9.11+.
* Oracle WebLogic Server 12.2.1.4.0, Oracle WebLogic Server 14.1.1.0.0, or Oracle WebLogic Server 14.1.2.0.0.
   * **NOTE**:

      * As of June, 2023, Oracle WebLogic Server 12.2.1.3 is no longer supported. The last Critical Patch Updates (CPU) images for WebLogic Server 12.2.1.3 were published in April, 2023.
      * As of December, 2022, Fusion Middleware 12.2.1.3 is no longer supported.  The last CPU images for FMW Infrastructure 12.2.1.3 were published in October, 2022.

   {{% notice warning %}}
   Throughout the documentation, the sample images are General Availability (GA) images. GA images are suitable for demonstration and development purposes _only_ where the environments are not available from the public Internet; they are **not acceptable for production use**. In production, you should always use CPU (patched) images from [OCR]({{< relref "/base-images/ocr-images.md" >}}) or create your images using the [WebLogic Image Tool]({{< relref "/base-images/custom-images#create-a-custom-base-image" >}}) (WIT) with the `--recommendedPatches` option. For more guidance, see [Apply the Latest Patches and Updates](https://www.oracle.com/pls/topic/lookup?ctx=en/middleware/standalone/weblogic-server/14.1.1.0&id=LOCKD-GUID-2DA84185-46BA-4D7A-80D2-9D577A4E8DE2) in _Securing a Production Environment for Oracle WebLogic Server_. For details on how to obtain or create images, see [WebLogic images]({{< relref "/base-images/_index.md" >}}).
   {{% /notice %}}
   * Check the WLS version and patches using the [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/userguide/tools/inspect-image/) `inspect` command: `imagetool inspect --image=container-registry.oracle.com/middleware/weblogic:12.2.1.4 --patches`. For more information, see [Inspect images]({{< relref "/base-images/ocr-images#inspect-images" >}}).
* Container images based on Oracle Linux 8 are now supported. The Oracle Container Registry hosts container images
  based on both Oracle Linux 7 and 8, including Oracle WebLogic Server 14.1.1.0.0 images based on Java 8 and 11 and Oracle WebLogic Server 14.1.2.0.0 images based on Java 17 and 21.
* Container images based on Oracle Linux 9 are now supported. The Oracle Container Registry hosts container images
  based on Oracle Linux 9, including Oracle WebLogic Server 14.1.2.0.0 images based on Java 17 and 21.
* You must have the `cluster-admin` role to install the operator.  The operator does
  not need the `cluster-admin` role at runtime. For more information,
  see the role-based access control, operator
  [RBAC]({{< relref "/managing-operators/rbac.md" >}}) documentation.
* We do not currently support running WebLogic in non-Linux containers.

See also [Supported environments]({{< relref "/introduction/platforms/environments.md" >}}) for environment and licensing requirements.
