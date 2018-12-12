> **WARNING** This documentation is for version 1.1 of the operator.  To view documenation for the current release, [please click here](/site).

# Recent changes to the Oracle WebLogic Server Kubernetes Operator

This document tracks recent changes to the operator, especially ones that introduce backward incompatibilities.

## Release 1.1

###### Changes:
* Improvements to documentation that describe how to scale a WebLogic cluster. 
* Add documentation to list steps needed to restart domain when changes have been made to the domain properties. 
* Operator resumes processing after Domain resource deleted and recreated.
* Corrected WebLogic cluster instability when `startupControl` is set to `"ALL"`.
* Expose server name via javaOptions.
* Update Java Kubernetes client to 2.0.0.
* Add validation for Apache loadBalancerVolumePath.
* Upgrade Jackson databinding version to 2.9.6.
* Create headless Services per WebLogic server instance.
* Reduce number of warning messages when reading WebLogic domain configuration.
* Resolve memory continuously growing by preventing the request parameters list from growing indefinitely.
* Document recommendation to use NFS version 3.0 for running WebLogic Server on OCI Container Engine for Kubernetes.
* Add validation of legal DNS names for `domainUID`, `adminServerName`, `managedServerNameBase`, and `clusterName`.

## Release 1.0

###### Changes:
* Added support for dynamic clusters. 
* Added support for Apache HTTP Server, the Voyager Ingress Controller.
* Added support for PV in NFS storage for multi-node environments.

## Release 0.2

###### Changes:
* Several files and input parameters have been renamed.  This affects how operators and domains are created.  It also changes generated Kubernetes artifacts, therefore customers must recreate their operators and domains.  `Introduces Backward Incompatibility`
* Many Kubernetes artifact names and labels have changed. Also, the names of generated YAML files for creating a domain's PV and PVC have changed.  Because of these changes, customers must recreate their operators and domains. `Introduces Backward Incompatibility`
