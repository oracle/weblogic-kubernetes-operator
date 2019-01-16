# Sample to create OKE cluster using Terraform scripts

The provided sample will create:

	A new Virtual Cloud Network (VCN) for the cluster

	2 LoadBalancer subnets with seclists

	3 Worker subnets with seclists

	A Kubernetes Cluster with one NodePool

	A kubeconfig file to allow access using kubectl

Nodes and network settings will be configured to allow SSH access, and the cluster Networking policies will allow NodePort services to be exposed.

By default all OCI Container Engine for Kubernetes Cluster masters are Highly Available (HA) and fronted by load balancers.



Prerequisites

To use these Terraform scripts, you will need fulfill the following prerequisites:

	Have an existing tenancy with enough compute and networking resources available for the desired cluster

	Have an OCI Container Engine for Kubernetes policy in place within that tenancy to allow the OCI Container Engine for Kubernetes service to manage tenancy resources

	Install Terraform with the OCI plugin as described here.

	Have a user defined within that tenancy

	Have an API key defined for use with the OCI API, as documented here

	Have an SSH key pair with file permission 600 ready for configuring SSH access to the nodes in the cluster


Copy provided oci.props.template file to oci.props and add all required values.

The syntax of the script is:
```
$ kubernetes/samples/scripts/terraform/oke.create.sh oci.props
```
The scripts collects the values from oci.props file and performs the following steps:
Create a new tfvars file based on the values from the provided oci.props file.
Downloads and installs all needed binaries for Terraform, Terraform OCI Provider and Go, based on OS system ( Mac or Linux)
Apply the configuration and creates OKE Cluster using Terraform


