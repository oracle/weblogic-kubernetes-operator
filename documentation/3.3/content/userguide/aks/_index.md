---
title: "Azure Kubernetes Service (AKS)"
date: 2021-10-27T15:27:38-05:00
weight: 9
description: "Deploying WebLogic Server on Azure Kubernetes Service."
---

### Contents

   - [Introduction](#introduction)
   - [Basics](#basics)
   - [Configure AKS Cluster](#configure-aks-cluster)
   - [TLS/SSL Configuration](#tlsssl-configuration)
   - [Networking](#networking)
   - [DNS Configuration](#dns-configuration)
   - [Database](#database)
   - [Review + create](#review--create)
   

#### Introduction

{{< readfile file="/samples/azure-kubernetes-service/includes/aks-value-prop.txt" >}}

This section documents the Azure Marketplace offer that makes it easy to get started with WebLogic Server on Azure. The offer handles all the initial setup, creating the AKS cluster, container registry, WebLogic Kubernetes Operator installation, and domain creation using the Model in Image domain home source type. For complete details on domain home source types, see [Choose a domain home source type]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}).

It is also possible to run the WebLogic Kubernetes Operator manually, without the aid of the Azure Marketplace offer.  This steps for doing so are documented in the sample [Azure Kubernetes Service]({{< relref "/samples/azure-kubernetes-service/_index.md" >}}).

#### Basics

Use the Basics blade to provide the basic configuration details for deploying Oracle WebLogic Server configured cluster. To do this, enter the values for the fields listed in the following tables

##### Project details


| Field | Description |
|-------|-------------|
| Subscription | Select a subscription to use for the charges accrued by this offer. You must have a valid active subscription associated with the Azure account that is currently logged in. If you don’t have it already, follow the steps described in [Associate or add an Azure subscription to your Azure Active Directory tenant](https://docs.microsoft.com/en-us/azure/active-directory/fundamentals/active-directory-how-subscriptions-associated-directory).| 
| Resource group | A resource group is a container that holds related resources for an Azure solution. The resource group includes those resources that you want to manage as a group. You decide which resources belong in a resource group based on what makes the most sense for your organization. If you have an existing resource group into which you want to deploy this solution, you can enter its name here; however, the resource group must have no pre-existing resources in it. Alternatively, you can click the **Create new**, and enter the name so that Azure creates a new resource group before provisioning the resources.  For more information about resource groups, see [Azure documentation](https://docs.microsoft.com/en-us/azure/azure-resource-manager/resource-group-overview#resource-groups). |

##### Instance details

| Field | Description |
|-------|-------------|
| Region | Select an Azure region from the drop-down list. |

##### Credentials for WebLogic

| Field | Description |
|-------|-------------|
| Username for WebLogic Administrator | Enter a user name to access the WebLogic Administration Console which is started automatically after the provisioning. For more information about the WebLogic Administration Console, see [Overview of Administration Consoles](https://docs.oracle.com/pls/topic/lookup?ctx=en/middleware/standalone/weblogic-server/wlazu&id=INTRO-GUID-CC01963A-6073-4ABD-BC5F-5C509CA1EA90) in _Understanding Oracle WebLogic Server_. |
| Password for WebLogic Administrator | Enter a password to access the WebLogic Administration Console. |
| Confirm password | Re-enter the value of the preceding field. |
| Password for WebLogic Deploy Tooling runtime encrytion | The deployment uses Weblogic Deploy Tooling, including the capability to encrypt the model. This password is used for that encrption. For more information, see [Encrypt Model Tool](https://oracle.github.io/weblogic-deploy-tooling/userguide/tools/encrypt/). |
| Confirm password | Re-enter the value of the preceding field. |
| User assigned managed identity | The deployment requires a user-assigned managed identity with the **Contributor** or **Owner** role in the subscription referenced above.  For more information please see [Create, list, delete, or assign a role to a user-assigned managed identity using the Azure portal](https://docs.microsoft.com/en-us/azure/active-directory/managed-identities-azure-resources/how-to-manage-ua-identity-portal). |

##### Optional Basic Configuration

| Field | Description |
|-------|-------------|
| Accept defaults for optional configuration? | If you want to retain the default values for the optional configuration, such as **Name prefix for Managed Server**, **WebLogic Domain Name** and others, set the toggle button to **Yes**, and click **Next: Configure AKS cluster**. If you want to specify different values for the optional configuration, set the toggle button to **No**, and enter the following details. |
| 





Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.

#### Configure AKS Cluster

Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.

#### TLS/SSL Configuration

Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.

#### Networking

Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.

#### DNS Configuration

Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.

#### Database

Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.

#### Review + create

Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.

