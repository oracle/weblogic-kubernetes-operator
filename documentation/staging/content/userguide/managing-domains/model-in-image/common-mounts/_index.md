+++
title = "Common Mounts"
date = 2019-02-23T16:45:16-05:00
weight = 70
pre = "<b> </b>"
+++

Use common mounts to automatically include the directory content from additional images. This is a useful alternative for including Model in Image model files, or other types of files, in a Pod without requiring modifications to the Pod's base image specified using `domain.spec.image`. This feature internally uses a Kubernetes [emptyDir](https://kubernetes.io/docs/concepts/storage/volumes/#emptydir) volume and Kubernetes [init containers](https://kubernetes.io/docs/concepts/workloads/pods/init-containers/) to share the files from the additional images with the Pod.

When using the common mounts feature with Model In Image use case, the WebLogic image would be the Domain resource's `domain.spec.image` field. The model files, application archives, and the WDT installation files can be provided in a separate image specified as part of the common mounts. The application archive could also be provided by a second container image in the common mounts section. Specific advantages of the common mounts feature for Model In Image domains are:
  - Use a WebLogic install image, or patch a WebLogic install image, without needing to include model artifacts within the image.
  - Share one large WebLogic install image with multiple different model configurations.
  - Distribute model files, application archives, and WebLogic Deploy Tooling executable using very small images instead of a large image that also contains a WebLogic install.

### Configuration
Here's an example configuration for the Common Mounts. 

```
  serverPod:
    commonMounts:
    - image: model-in-image-files:1
      imagePullPolicy: IfNotPresent
      command: cp -R $COMMON_MOUNT_PATH/* $COMMON_MOUNT_TARGET_PATH
      volume: commonMountsVolume1
```

The `volume` field in the common mounts refers to the name of a common mount volume defined in `spec.commonMountVolumes` section. Here's an example configuration for the common mount volumes.
```
  spec:
    commonMountVolumes:
    - name: commonMountsVolume1
      mountPath: /common
      medium: Memory
      sizeLimit: 100G
```
The below tables describe various fields of the common mounts and common mount volumes sections.

#### Common Mounts Fields
| Name |  Description
| --- | --- 
| image | The name of an image with files located in the directory specified by `spec.commonMountVolumes.mountPath` of the common mount volume referenced by `serverPod.commonMounts.volume` (which defaults to '/common').
| imagePullPolicy | The image pull policy for the common mounts container image. Legal values are Always, Never, and IfNotPresent. Defaults to Always if image ends in ":latest"; IfNotPresent, otherwise.
| command | The command for this init container. Defaults to `cp -R $COMMON_MOUNT_PATH/* $TARGET_MOUNT_PATH`. This is an advanced setting for customizing the container command for copying files from the container image to the common mounts emptyDir volume. Use the `$COMMON_MOUNT_PATH` environment variable to reference the value configured in `commonMountVolumes.mountPath` (which defaults to '/common'). Use `$TARGET_MOUNT_PATH` to refer to the temporary directory created by the Operator that resolves to the common mount's internal emptyDir volume.
| volume | The name of common mount volume defined in `spec.commonMountVolumes`. Required.

#### Common Mount Volumes Fields
| Name |  Description
| --- | --- 
| Name | The name of the common mount volume. Required.
| mountPath | The common mount path. The files in the path are populated from the same-named directory in the images supplied by each container in 'serverPod.commonMounts'. Each common mount volume must be configured with a different common mount path. Required.
| medium | The [emptyDir](https://kubernetes.io/docs/concepts/storage/volumes/#emptydir) volume medium. This is an advanced setting that rarely needs to be configured. Defaults to unset, which means the volume's files are stored on the local node's file system for the life of the pod.
| sizeLimit | The [emptyDir](https://kubernetes.io/docs/concepts/storage/volumes/#emptydir) volume size limit. Defaults to unset.


{{% notice note %}}  The `imagePullSecrets` required for pulling the common mounts images should be specified at the Pod level using `spec.imagePullSecrets`.
{{% /notice %}}
