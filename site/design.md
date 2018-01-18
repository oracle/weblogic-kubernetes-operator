
# Design philosophy

The Oracle WebLogic Server Kubernetes Operator (the “operator”) is designed to fulfil a similar role to that which a human operator would fill in a traditional data center deployment.  It contains a set of useful built-in knowledge about how to perform various lifecycle operations on a domain correctly.

Human operators are normally responsible for starting and stopping environments, initiating backups, performing scaling operations, performing manual tasks associated with Disaster Recovery and High Availability needs and coordinating actions with other operators in other data centers.  It is envisaged that the operator will have similar responsibilities in a Kubernetes environment.  The initial “Technology Preview” version of the operator does not have the capability to take on all of those responsibilities, but enumerating them here gives insight into the background context for making various design choices.

It is important to note the distinction between an Operator and an Administrator.  A WebLogic Administrator typically has different responsibilities centered around managing the detailed configuration of the WebLogic Domains.  The operator has only limited interest in the domain configuration, with its main concern being the high-level topology of the Domain, e.g. how many clusters and servers, and information about network access points, e.g. channels.

Human operators may manage more than one domain, and the operator is also designed to be able to manage more than one domain.  Like its human counterpart, the operator will only take actions against Domains that it is told to manage, and will ignore any other domains that may be present in the same environment.

Like a human operator, the operator is designed to be event-based.  It waits for a significant event to occur, or for a scheduled time to perform some action, and then takes the appropriate action.  Examples of significant events include being made aware of a new domain that needs to be managed, receiving a request to scale up a WebLogic Cluster, or receiving a request to perform a backup of a Domain.

The operator is designed with security in mind from the outset.  Some examples of the specific security practices we follow are:

*	During the deployment of the operator, Kubernetes roles are defined and assigned to the operator.  These roles are designed to give the operator the minimum amount of privileges that it requires to perform its task.  
*	The code base is regularly scanned with security auditing tools and any issues that are identified are promptly resolved.  
*	All HTTP communications – between the operator and an external client, between the operator and WebLogic administration servers, and so on – are configured to require SSL and TLS 1.2.  
*	Unused code is pruned from the code base regularly.  
*	Dependencies are kept as up to date as possible and are regularly reviewed for security vulnerabilities.

The operator is designed to avoid imposing any arbitrary restriction on how WebLogic may be configured or used in Kubernetes.  Where there are restrictions, these are based on the availability of some specific feature in Kubernetes, for example multicast support.
