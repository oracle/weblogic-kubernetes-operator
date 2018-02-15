# Oracle WebLogic Server Kubernetes Operator Name Changes

The initial version of the WebLogic Operator did not use consistent naming conventions (e.g. for file names, property names, enum values, Kubernetes artifact names).  We're addressing this issue now.  This means that a significant number of customer visible names are changing.  We're not providing an upgrade tool or backwards compatibility with the previous names.  Instead, customers will need to recreate their operators and domains.  This document lists the customer visible naming changes.

## Customer Visible Files

### Files for Creating Operators and Domains

The following files are used to create the operator and to create domains.

| Previous File Name | New File Name |
| kubernetes/create-weblogic-operator.sh | same |
| kubernetes/create-domain-job.sh | kubernetes/create-weblogic-domain.sh |
| kubernetes/create-operator-inputs.yaml | kubernetes/create-weblogic-operator-inputs.yaml |
| kubernetes/create-domain-job-inputs.yaml | kubernetes/create-weblogic-domain-inputs.yaml |

### Generated YAML Files for Operators and Domains

The create scripts generate a number of yaml files that are used to configure the corresponding Kubernetes artifacts for the operator and the domains.
Normally, customers do not use these yaml files.  However, customers can look at them.  They can also change the operator and domain configuration by editing these files and reapplying them.

#### Directory for the Generated YAML Files

Previously, these files were placed in the kubernetes directory (e.g. kubernetes/weblogic-operator.yaml).  Now, they are placed in per-operator and per-domain directories (since a Kubernetes cluster can have more than one operator and an operator can manage more than one domain).

The customer can control the name of the directory by specifying the -o option to the create script, for example:
  create-weblogic-operator.sh -o /scratch/operator1
The pathname can either be a full path name, or a relative pathname.  If it's a relative pathname, then it's relative to the kubernetes directory.
So, if you specify 'create-weblogic-operator.sh -o operator1', the directory will be 'kubernetes/operator'.

If the customer does not specify the -o option, then default directory names are chosen based on the values in the inputs yaml file.
create-weblogic-operator.sh will default the directory name to the operator's Kubernetes namespace name (i.e. the 'namespace' parameter in the inputs yaml file, which defaults to 'weblogic-operator'). So, if you just call 'create-weblogic-operator.sh', the generated files will be put in the 'kubernetes/weblogic-operator' directory.

Similarly, create-weblogic-domain.sh will default the directory name to the domain's UID (i.e. the 'domainUID' parameter in the inputs file, which must be explicitly configured by the customer).  So, if you set 'domainUID' to 'domain1.development-stage.yourcompany.com' in kubernetes/create-weblogic-domain-inputs.yaml, then call 'create-weblogic-domain.sh', the generated files will be put in the 'kubernetes/domain1.development-stage.yourcompany.com' directory.

#### File Names of the Generated YAML File

The names of several of the generated YAML files have changed.

| Previous File Name | New File Name |
| weblogic-operator.yaml | same |
| rbac.yaml | weblogic-operator-security.yaml |
| domain-custom-resource.yaml | same |
| domain-job.yaml | create-weblogic-domain-job.yaml |
| persistent-volume.yaml | weblogic-domain-persistent-volume.yaml |
| persistent-volume-claim.yaml | weblogic-domain-persistent-volume-claim.yaml |
| traefik-deployment.yaml | traefik.yaml |
| traefik-rbac.yaml | traefik-security.yaml |
