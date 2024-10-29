# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# The version of this inputs file. Do not modify.
export version=create-domain-on-aks-inputs-v1

#
# Parameters that may optionally be changed.
#

# Number of azure kubernetes nodes, used to create azure kubernetes cluster.
export azureKubernetesNodeCount=2

# VM size of azure kubernetes node.
export azureKubernetesNodeVMSize=Standard_DS2_v2

# The suffix of azure kubernetes node pool name, the azure kubernetes node pool name will be${azureKubernetesNodepoolNamePrefix} ${namePrefix}.
export azureKubernetesNodepoolNamePrefix=pool1

#Java Option for WebLogic Server
export javaOptions="-Dweblogic.StdoutDebugEnabled=false -XX:InitialRAMPercentage=25.0 -XX:MaxRAMPercentage=50.0"

# Resource request for each server pod (Memory and CPU). This is minimum amount of compute
# resources required for each server pod. Edit value(s) below as per pod sizing requirements.
# These are optional 
# Please refer to the kubernetes documentation on Managing Compute
# Resources for Containers for details.
# Parameter "serverPodMemoryRequest" and "serverPodCpuRequest" will be overwritten with this field in kubernetes/samples/scripts/create-weblogic-domain/domain-home-on-pv/create-domain-inputs.yaml
export serverPodMemoryRequest="1.5Gi"
export serverPodCpuRequest="250m"

# Uncomment and edit value(s) below to specify the maximum amount of compute resources allowed 
# for each server pod.
# These are optional. 
# Please refer to the kubernetes documentation on Managing Compute
# Resources for Containers for details.
# Parameter "serverPodMemoryLimit" and "serverPodCpuLimit" will be overwritten with this field in kubernetes/samples/scripts/create-weblogic-domain/domain-home-on-pv/create-domain-inputs.yaml
export serverPodMemoryLimit="1.5Gi"
export serverPodCpuLimit="250m"

# WebLogic Server image.
# Parameter "image" will be overwritten with this field in kubernetes/samples/scripts/create-weblogic-domain/domain-home-on-pv/create-domain-inputs.yaml
# **NOTE**:
# This sample uses General Availability (GA) images. GA images are suitable for demonstration and
# development purposes only where the environments are not available from the public Internet;
# they are not acceptable for production use. In production, you should always use CPU (patched)
# images from OCR or create your images using the WebLogic Image Tool.
# Please refer to the `OCR` and `WebLogic Images` pages in the WebLogic Kubernetes Operator
# documentation for details.
export weblogicDockerImage=container-registry.oracle.com/middleware/weblogic:12.2.1.4



