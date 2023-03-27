---
title: "Azure Kubernetes Service (AKS)"
date: 2021-10-27T15:27:38-05:00
weight: 12
description: "Deploy WebLogic Server on Azure Kubernetes Service."
---

{{< table_of_contents >}}

### Introduction

{{< readfile file="/samples/azure-kubernetes-service/includes/aks-value-prop.txt" >}}

This document describes the Azure Marketplace offer that makes it easy to get started with WebLogic Server on Azure. The offer handles all the initial setup, creating the AKS cluster, container registry, WebLogic Kubernetes Operator installation, and domain creation using the model-in-image domain home source type. For complete details on domain home source types, see [Choose a domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}).

It is also possible to run the WebLogic Kubernetes Operator manually, without the aid of the Azure Marketplace offer.  The steps for doing so are documented in the sample [Azure Kubernetes Service]({{< relref "/samples/azure-kubernetes-service/_index.md" >}}).

### Basics

Use the **Basics** blade to provide the basic configuration details for deploying an Oracle WebLogic Server configured cluster. To do this, enter the values for the fields listed in the following tables.

#### Project details


| Field | Description |
|-------|-------------|
| Subscription | Select a subscription to use for the charges accrued by this offer. You must have a valid active subscription associated with the Azure account that is currently logged in. If you don’t have it already, follow the steps described in [Associate or add an Azure subscription to your Azure Active Directory tenant](https://docs.microsoft.com/azure/active-directory/fundamentals/active-directory-how-subscriptions-associated-directory).|
| Resource group | A resource group is a container that holds related resources for an Azure solution. The resource group includes those resources that you want to manage as a group. You decide which resources belong in a resource group based on what makes the most sense for your organization. If you have an existing resource group into which you want to deploy this solution, you can enter its name here; however, the resource group must have no pre-existing resources in it. Alternatively, you can click the **Create new**, and enter the name so that Azure creates a new resource group before provisioning the resources.  For more information about resource groups, see the [Azure documentation](https://docs.microsoft.com/azure/azure-resource-manager/resource-group-overview#resource-groups). |

#### Instance details

| Field | Description |
|-------|-------------|
| Region | Select an Azure region from the drop-down list. |

#### Credentials for WebLogic

| Field | Description |
|-------|-------------|
| Username for WebLogic Administrator | Enter a user name to access the WebLogic Server Administration Console which is started automatically after the provisioning. For more information about the WebLogic Server Administration Console, see [Overview of Administration Consoles](https://docs.oracle.com/pls/topic/lookup?ctx=en/middleware/standalone/weblogic-server/wlazu&id=INTRO-GUID-CC01963A-6073-4ABD-BC5F-5C509CA1EA90) in _Understanding Oracle WebLogic Server_. |
| Password for WebLogic Administrator | Enter a password to access the WebLogic Server Administration Console. |
| Confirm password | Re-enter the value of the preceding field. |
| Password for WebLogic Model encryption | Model in Image requires a runtime encryption secret with a secure password key. This secret is used by the operator to encrypt model and domain home artifacts before it adds them to a runtime ConfigMap or log. For more information, see [Required runtime encryption secret]({{< relref "/managing-domains/model-in-image/usage#required-runtime-encryption-secret" >}}).|
| Confirm password | Re-enter the value of the preceding field. |

#### Optional Basic Configuration

| Field | Description |
|-------|-------------|
| Accept defaults for optional configuration? | If you want to retain the default values for the optional configuration, such as **Name prefix for Managed Server**, **WebLogic Domain Name** and others, set the toggle button to **Yes**, and click **Next: Configure AKS cluster**. If you want to specify different values for the optional configuration, set the toggle button to **No**, and enter the following details. |
| Name prefix for Managed Server | Enter a prefix for the Managed Server name. |
| WebLogic Domain Name | Enter the name of the domain that will be created by the offer. |
| Maximum dynamic cluster size | The maximum size of the dynamic WebLogic cluster created. |
|Custom Java Options to start WebLogic Server | Java VM arguments passed to the invocation of WebLogic Server. For more information, see the [FAQ]({{< relref "/faq/resource-settings/_index.md" >}}). |

When you are satisfied with your selections, select **Next : Configure AKS cluster**.

### Configure AKS cluster

Use the **Configure AKS Cluster** blade to configure fundamental details of how Oracle WebLogic Server runs on AKS. To do this, enter the values for the fields listed in the following tables.

#### Azure Kubernetes Service

In this section, you can configure some options about the AKS which will run WebLogic Server.

| Field | Description |
|-------|-------------|
|Create a new AKS cluster| If set to **Yes**, the deployment will create a new AKS cluster resource in the specified resource group. If set to **No**, you have the opportunity to select an existing AKS cluster, into which the deployment is configured. Note: the offer assumes the existing AKS cluster has no WebLogic related deployments. |
|Use latest supported AKS Kubernetes version| The currently supported version is **1.24.3**.  Oracle tracks the AKS release versions in [Supported Kubernetes versions in Azure Kubernetes Service (AKS)](https://docs.microsoft.com/en-us/azure/aks/supported-kubernetes-versions). After a new version emerges, Oracle qualifies WLS on AKS against that version and will update the offer to that version. Please see "WebLogic Kubernetes ToolKit Support Policy (Doc ID 2790123.1)" and "Support for Oracle Fusion Middleware on Azure and Oracle Linux (Doc ID 2914257.1)" in My Oracle Support for the Oracle support policy for WLS on Kubernetes, including AKS.|
| Node count | The initial number of nodes in the AKS cluster. This value can be changed after deployment. For information, see [Scaling]({{< relref "/managing-domains/domain-lifecycle/scaling.md" >}}). |
| Node size | The default VM size is 2x Standard DSv2, 2 vcpus, 7 GB memory. If you want to select a different VM size, select **Change Size**, select the size from the list (for example, A3) on the Select a VM size page, and select **Select**. For more information about sizing the virtual machine, see the [Azure documentation on Sizes](https://docs.microsoft.com/azure/cloud-services/cloud-services-sizes-specs).|
|Enable Container insights| If selected, configure the necessary settings to integrate with Container insights. Container insights gives you performance visibility by collecting memory and processor metrics from controllers, nodes, and containers that are available in Kubernetes through the Metrics API. Container logs are also collected. Metrics are written to the metrics store and log data is written to the logs store associated with your Log Analytics workspace. For more information, see [Container insights overview](https://aka.ms/wls-aks-container-insights).|
|Create Persistent Volume using Azure File share service|If selected, an Azure Storage Account and an Azure Files share will be provisioned; static persistent volume with the Azure Files share will be mounted to the nodes of the AKS cluster. For more information, see [Oracle WebLogic Server persistent storage]({{< relref "/managing-domains/persistent-storage/_index.md" >}}) and [persistent volume with Azure Files share on AKS](https://docs.microsoft.com/azure/aks/azure-files-volume).|

#### Image selection

In this section, you can configure the image that is deployed using the model-in-image domain home source type. There are several options for the WebLogic image and the application image deployed therein.

| Field | Description |
|-------|-------------|
| Use a pre-existing WebLogic Server Docker image from Oracle Container Registry (OCR)? | If set to **Yes**, the subsequent options are constrained to allow only selecting from a set of pre-existing WebLogic Server Docker images stored in the Oracle Container Registry. If set to **No**, the user may refer to a pre-existing Azure Container Registry, and must specify the Docker tag of the WebLogic Server image within that registry that will be used to create the domain. The specified image is assumed to be compatible with the WebLogic Kubernetes Operator. This allows the use of custom images, such as images with a specific one-off patch or Oracle quarterly patches (PSUs). For more about WebLogic Server images, see [WebLogic images]({{< relref "/base-images/_index.md" >}}).|
|Create a new Azure Container Registry to store application images?|If set to **Yes**, the offer will create a new Azure Container Registry (ACR) to hold the images for use in the deployment.  If set to **No**, you must specify an existing ACR. In this case, you must be sure the selected ACR has the admin account enabled. For details, please see [Admin account](https://docs.microsoft.com/azure/container-registry/container-registry-authentication?tabs=azure-cli#admin-account). |
| Select existing ACR instance | This option is shown only if **Use a pre-existing WebLogic Server Docker image from Oracle Container Registry?** is set to **No**. If visible, select an existing Acure Container Registry instance. |
| Please provide the image path | This option is shown only if **Use a pre-existing WebLogic Server Docker image from Oracle Container Registry?** is set to **No**. If visible, the value must be a fully qualified Docker tag of an image within the specified ACR. |
| Username for Oracle Single Sign-On authentication | The Oracle Single Sign-on account user name for which the Terms and Restrictions for the selected WebLogic Server image have been accepted. |
| Password for Oracle Single Sign-On authentication | The password for that account. |
| Confirm password | Re-enter the value of the preceding field. | If 'Yes' is selected; the deployment process will pull from the CPU WebLogic Server image repository in the OCR. If 'No' is selected the deployment process will pull from the WebLogic Server image repository in OCR. |
| Is the specified SSO account associated with an active Oracle support contract? | If set to **Yes**, you must accept the license agreement in the `middleware/weblogic_cpu` repository. If set to **No**, you must accept the license agreement in the `middleware/weblogic`. Steps to accept the license agreement: log in to the [Oracle Container Registry](https://container-registry.oracle.com/); navigate to the `middleware/weblogic_cpu` and `middleware/weblogic` repository; accept license agreement. See this [document](https://aka.ms/wls-aks-ocr-doc) for more information. |
| Select WebLogic Server Docker tag | Select one of the supported images. |

#### Java EE Application

In this section you can deploy a Java EE Application along with the WebLogic Server deployment.

| Field | Description |
|-------|-------------|
| Deploy your application package? | If set to **Yes**, you must specify a Java EE WAR, EAR, or JAR file suitable for deployment with the selected version of WebLogic Server. If set to **No**, no application is deployed.|
| Application package (.war,.ear,.jar) | With the **Browse** button, you can select a file from a pre-existing Azure Storage Account and Storage Container within that account.  To learn how to create a Storage Account and Container, see [Create a storage account](https://docs.microsoft.com/azure/storage/common/storage-account-create?tabs=azure-portal) and [Create a Storage Container and upload application files](https://docs.microsoft.com/azure/storage/blobs/storage-quickstart-blobs-portal). |
| Fail deployment if application does not become ACTIVE. | If selected, the deployment will wait for the deployed application to reach the **ACTIVE** state and fail the deployment if it does not. For more details, see the [Oracle documentation](https://aka.ms/wls-aks-deployment-state). |
| Number of WebLogic Managed Server replicas | The initial value of the `replicas` field of the Domain. For information, see [Scaling]({{< relref "/managing-domains/domain-lifecycle/scaling.md" >}}). |

When you are satisfied with your selections, select **Next : TLS/SSL configuration**.

### TLS/SSL configuration

With the **TLS/SSL configuration** blade, you can configure Oracle WebLogic Server Administration Console on a secure HTTPS port, with your own SSL certificate provided by a Certifying Authority (CA). See [Oracle WebLogic Server Keystores configuration](https://aka.ms/arm-oraclelinux-wls-ssl-configuration) for more information.

Select **Yes** or **No** for the option **Configure WebLogic Server Administration Console, Remote Console, and cluster to use HTTPS (Secure) ports, with your own TLS/SSL certificate.** based on your preference. If you select **No**, you don't have to provide any details, and can proceed by selecting **Next : Networking**. If you select **Yes**, you can choose to provide the required configuration details by either uploading existing keystores or by using keystores stored in Azure Key Vault.

If you want to upload existing keystores, select **Upload existing KeyStores** for the option **How would you like to provide required configuration**, and enter the values for the fields listed in the following table.

#### Upload existing KeyStores

| Field | Description |
|-------|-------------|
|Identity KeyStore Data file(.jks,.p12)| Upload a custom identity keystore data file by doing the following: {{< line_break >}} 1. Click on the file icon. {{< line_break >}} 2. Navigate to the folder where the identity keystore file resides, and select the file. {{< line_break >}} 3. Click Open. |
| Password | Enter the passphrase for the custom identity keystore. |
| Confirm password | Re-enter the value of the preceding field. |
| The Identity KeyStore type (JKS,PKCS12) | Select the type of custom identity keystore. The supported values are JKS and PKCS12. |
| The alias of the server's private key within the Identity KeyStore | Enter the alias for the private key. |
| The passphrase for the server's private key within the Identity KeyStore | Enter the passphrase for the private key. |
| Confirm passphrase | Re-enter the value of the preceding field. |
| Trust KeyStore Data file(.jks,.p12) | Upload a custom trust keystore data file by doing the following: {{< line_break >}} 1. Click on the file icon. {{< line_break >}} 2. Navigate to the folder where the identity keystore file resides, and select the file. {{< line_break >}} 3. Click Open. |
| Password | Enter the password for the custom trust keystore. |
| Confirm password | Re-enter the value of the preceding field. |
| The Trust KeyStore type (JKS,PKCS12) | Select the type of custom trust keystore. The supported values are JKS and PKCS12. |

If you want to use keystores that are stored in Azure Key Vault, select **Use KeyStores stored in Azure Key Vault** for the option **How would you like to provide required configuration**, and enter the values for the fields listed in the following table.

#### Use KeyStores stored in Azure Key Vault

| Field | Description |
|-------|-------------|
| Resource group name in current subscription containing the Key Vault | Enter the name of the Resource Group containing the Key Vault that stores the SSL certificate and the data required for WebLogic SSL termination. |
| Name of the Azure Key Vault containing secrets for the TLS/SSL certificate | Enter the name of the Azure Key Vault that stores the SSL certificate and the data required for WebLogic SSL termination. |
| The name of the secret in the specified Key Vault whose value is the Identity KeyStore Data | Enter the name of the Azure Key Vault secret that holds the value of the identity keystore data. Follow [Store the TLS/SSL certificate in the Key Vault](#store-the-tlsssl-certificate-in-the-key-vault) to upload the certificate to Azure Key Vault. |
| The name of the secret in the specified Key Vault whose value is the passphrase for the Identity KeyStore |  Enter the name of the Azure Key Vault secret that holds the value of the passphrase for the identity keystore. |
| The Identity KeyStore type (JKS,PKCS12) | Select the type of custom identity keystore. The supported values are JKS and PKCS12. |
| The name of the secret in the specified Key Vault whose value is the Private Key Alias | Enter the name of the Azure Key Vault secret that holds the value of the private key alias. |
| The name of the secret in the specified Key Vault whose value is the passphrase for the Private Key | Enter the name of the Azure Key Vault secret that holds the value of the passphrase for the private key. |
| The name of the secret in the specified Key Vault whose value is the Trust KeyStore Data | Enter the name of the Azure Key Vault secret that holds the value of the trust keystore data. Follow [Store the TLS/SSL certificate in the Key Vault](#store-the-tlsssl-certificate-in-the-key-vault) to upload the certificate to Azure Key Vault. |
| The name of the secret in the specified Key Vault whose value is the passphrase for the Trust KeyStore | Enter the name of the Azure Key Vault secret that holds the value of the the passphrase for the trust keystore. |
| The Trust KeyStore type (JKS,PKCS12) | Select the type of custom trust keystore. The supported values are JKS and PKCS12. |

When you are satisfied with your selections, select **Next : Networking**.

### Networking

Use this blade to configure options for load balancing and ingress controller.

#### Standard Load Balancer service

Selecting **Yes** here will cause the offer to provision the Azure Load Balancer as a Kubernetes load balancer service. For more information on the Standard Load Balancer see [Use a public Standard Load Balancer in Azure Kubernetes Service (AKS)](https://aka.ms/wls-aks-standard-load-balancer).  You can still deploy an Azure Application Gateway even if you select **No** here.

If you select **Yes**, you have the option of configuring the Load Balancer as an internal Load Balancer.  For more information on Azure internal load balancers see [Use an internal load balancer with Azure Kubernetes Service (AKS)](https://aka.ms/wls-aks-internal-load-balancer).

If you select **Yes**, you must fill in the following table to map the services to load balancer ports.

**Service name prefix** column:

You can fill in any valid value in this column.

**Target** and **Port** column:

For the ports, the recommended values are the usual 7001 for the **admin-server** and 8001 for the **cluster-1**.

#### Application Gateway Ingress Controller

In this section, you can create an Azure Application Gateway instance as the ingress controller of your WebLogic Server. This Application Gateway is pre-configured for end-to-end-SSL with TLS termination at the gateway using the provided SSL certificate and load balances across your cluster.

Select **Yes** or **No** for the option **Connect to Azure Application Gateway?** based on your preference. If you select **No**, you don't have to provide any details, and can proceed by selecting **Next : DNS Configuration >**. If you select **Yes**, you must specify the details required for the Application Gateway integration by entering the values for the fields as described next.

You can specify a virtual network for the application gateway. To do this, enter the values for the fields listed in the following tables.

**Configure virtual networks**

| Field | Description |
|-------|-------------|
| Virtual network | Select a virtual network in which to place the application gateway. Make sure your virtual network meets the requirements in [Application Gateway virtual network and dedicated subnet](https://docs.microsoft.com/en-us/azure/application-gateway/configuration-infrastructure#virtual-network-and-dedicated-subnet). |
| Subnet | An application gateway is a dedicated deployment in your virtual network. Within your virtual network, a dedicated subnet is required for the application gateway. See [Application Gateway virtual network and dedicated subnet](https://docs.microsoft.com/en-us/azure/application-gateway/configuration-infrastructure#virtual-network-and-dedicated-subnet). |
| Configure frontend IP with private IP address | If set to **Yes**, the Azure Marketplace offer will pick one of the available IP addresses from the application gateway subnet to be the private front-end IP address. For more information, see [Application Gateway front-end IP address configuration](https://docs.microsoft.com/en-us/azure/application-gateway/configuration-front-end-ip). |

You must select one of the following three options, each described in turn.

* Upload a TLS/SSL certificate: Upload the pre-signed certificate now.
* Identify an Azure Key Vault: The Key Vault must already contain the certificate and its password stored as secrets.
* Generate a self-signed front-end certificate: Generate a self-signed front-end certificate and apply it during deployment.

**Upload a TLS/SSL certificate**

| Field | Description |
|-------|-------------|
| Frontend TLS/SSL certificate(.pfx) | For information on how to create a certificate in PFX format, see [Overview of TLS termination and end to end TLS with Application Gateway](https://docs.microsoft.com/azure/application-gateway/ssl-overview). |
| Password | The password for the certificate |
| Confirm password | Re-enter the value of the preceding field. |
| Trusted root certificate(.cer, .cert) | A trusted root certificate is required to allow back-end instances in the application gateway. The root certificate is a Base-64 encoded X.509(.CER) format root certificate. |

**Identify an Azure Key Vault**

| Field | Description |
|-------|-------------|
| Resource group name in current subscription containing the KeyVault | Enter the name of the Resource Group containing the Key Vault that stores the application gateway SSL certificate and the data required for SSL termination. |
| Name of the Azure KeyVault containing secrets for the Certificate for SSL Termination | Enter the name of the Azure Key Vault that stores the application gateway SSL certificate and the data required for SSL termination. |
| The name of the secret in the specified Key Vault whose value is the front-end TLS/SSL certificate data | Enter the name of the Azure Key Vault secret that holds the value of the Application Gateway front-end SSL certificate data. Follow [Store the TLS/SSL certificate in the Key Vault](#store-the-tlsssl-certificate-in-the-key-vault) to upload the certificate to Azure Key Vault. |
| The name of the secret in the specified Key Vault whose value is the password for the front-end TLS/SSL certificate | Enter the name of the Azure Key Vault secret that holds the value of the password for the application gateway front-end SSL certificate. |
| The name of the secret in the specified Key Vault whose value is the trusted root certificate data | A trusted root certificate is required to allow back-end instances in the application gateway. Enter the name of the Azure Key Vault secret that holds the value of the application gateway trusted root certificate data. Follow [Store the TLS/SSL certificate in the Key Vault](#store-the-tlsssl-certificate-in-the-key-vault) to upload the certificate to Azure Key Vault. |

**Generate a self-signed frontend certificate**

| Field | Description |
|-------|-------------|
| Trusted root certificate(.cer, .cert) | A trusted root certificate is required to allow back-end instances in the application gateway. The root certificate is a Base-64 encoded X.509(.CER) format root certificate. |

Regardless of how you provide the certificates, there are several other options when configuring the Application Gateway, as described next.

| Field | Description |
|-------|-------------|
|Enable cookie based affinity | Select this box to enable cookie based affinity (sometimes called "sticky sessions"). For more information, see [Enable Cookie based affinity with an Application Gateway](https://docs.microsoft.com/azure/application-gateway/ingress-controller-cookie-affinity). |
| Create ingress for Administration Console. | Select **Yes** to create an ingress for the Administration Console with the path `/console`. |
| Create ingress for WebLogic Remote Console. | Select **Yes** to create an ingress for the Remote Console with the path `/remoteconsole`. |

When you are satisfied with your selections, select **Next : DNS Configuration**.

### DNS Configuration

With the **DNS Configuration** blade, you can provision the Oracle WebLogic Server Administration Console using a custom DNS name.

Select **Yes** or **No** for the option **Configure Custom DNS Alias?** based on your preference. If you select **No**, you don't have to provide any details, and can proceed by selecting **Next : Database >**. If you select **Yes**, you must choose either to configure a custom DNS alias based on an existing Azure DNS zone, or create an Azure DNS zone and a custom DNS alias. This can be done by selecting **Yes** or **No** for the option **Use an existing Azure DNS Zone**.

{{% notice note %}}
For more information about the DNS zones, see [Overview of DNS zones and records](https://docs.microsoft.com/azure/dns/dns-zones-records).
{{% /notice %}}

If you choose to configure a custom DNS alias based on an existing Azure DNS zone, by selecting **Yes** for the option **Use an existing Azure DNS Zone**, you must specify the DNS configuration details by entering the values for the fields listed in the following table.

| Field | Description |
|-------|-------------|
| DNS Zone Name	| Enter the DNS zone name. |
| Name of the resource group contains the DNS Zone in current subscription | Enter the name of the resource group that contains the DNS zone in the current subscription. |
| Label for Oracle WebLogic Server Administration Console | Enter a label to generate a sub-domain of the Oracle WebLogic Server Administration Console. For example, if the domain is `mycompany.com` and the sub-domain is `admin`, then the WebLogic Server Administration Console URL will be `admin.mycompany.com`. |
| Label for WebLogic Cluster | Specify a label to generate subdomain of WebLogic Cluster. |

If you choose to create an Azure DNS zone and a custom DNS alias, by selecting **No** for the option **Use an existing Azure DNS Zone**, you must specify the values for the following fields:

* DNS Zone Name
* Label for Oracle WebLogic Server Administration Console
* Label for WebLogic Cluster

See the preceding table for the description of these fields.

{{% notice note %}}
In the case of creating an Azure DNS zone and a custom DNS alias, you must perform the DNS domain delegation at your DNS registry post deployment. See [Delegation of DNS zones with Azure DNS](https://docs.microsoft.com/azure/dns/dns-domain-delegation).
{{% /notice %}}

When you are satisfied with your selections, select **Next : Database**.

### Database

Use the Database blade to configure Oracle WebLogic Server to connect to an existing database. Select **Yes** or **No** for the option **Connect to Database?** based on your preference. If you select **No**, you don't have to provide any details, and can proceed by clicking **Next : Review + create >**. If you select **Yes**, you must specify the details of your database by entering the values for the fields listed in the following table.

| Field | Description |
|-------|-------------|
| Choose database type | From the drop-down menu, select an existing database to which you want Oracle WebLogic Server to connect. The available options are:{{< line_break >}}{{< line_break >}} • Azure Database for PostgreSQL (with support for passwordless connection) {{< line_break >}} • Oracle Database {{< line_break >}} • Azure SQL (with support for passwordless connection) {{< line_break >}} • MySQL (with support for passwordless connection) {{< line_break >}} • Other |
| JNDI Name	| Enter the JNDI name for your database JDBC connection. |
| DataSource Connection String | Enter the JDBC connection string for your database. For information about obtaining the JDBC connection string, see [Obtain the JDBC Connection String for Your Database](https://docs.oracle.com/en/middleware/standalone/weblogic-server/wlazu/obtain-jdbc-connection-string-your-database.html#GUID-6523B742-EB68-4AF4-A85C-8B4561C133F3). |
| Global transactions protocol | Determines the transaction protocol (global transaction processing behavior) for the data source. For more information, see [JDBC Data Source Transaction Options](https://docs.oracle.com/en/middleware/standalone/weblogic-server/14.1.1.0/jdbca/transactions.html#GUID-4C929E67-5FD7-477B-A749-1EA0F4FD25D4). **IMPORTANT: The correct value for this parameter depends on the selected database type. For PostgreSQL, select EmulateTwoPhaseCommit**. |
| Use passwordless datasource connection | If you select a database type that supports passwordless connection, then this check box will appear. If selected, configure passwordless connections to the data source. For more information, see [Passwordless connections for Azure services](https://learn.microsoft.com/azure/developer/intro/passwordless-overview). |
| Database Username	| Enter the user name of your database. |
| Database Password	| Enter the password for the database user. |
| Confirm password | Re-enter the value of the preceding field. |
| User assigned managed identity | Select a user assigned identity that is able to connect to your database. {{< line_break >}} For how to create a database user for your managed identity, see https://aka.ms/javaee-db-identity.|

If you select **Other** as the database type, there are some additional values you must provide. WebLogic Server provides support for application data access to any database using a JDBC-compliant driver. Refer to the [documentation for driver requirements](https://aka.ms/wls-aks-dbdriver).

| Field | Description |
|-------|-------------|
| DataSource driver (.jar) | Use the **Browse** button to upload the JAR file for the JDBC driver to a storage container. To learn how to create a Storage Account and Container, see [Create a storage account](https://docs.microsoft.com/azure/storage/common/storage-account-create?tabs=azure-portal). |
| DataSource driver name | The fully qualified Java class name of the JDBC driver. |
| Test table name | The name of the database table to use when testing physical database connections. This value depends on the specified database. Some suggested values include the following. {{< line_break >}}{{< line_break >}} • For Oracle, use `SQL ISVALID`. {{< line_break >}} • For PostgreSQL, SQL Server and MariaDB use `SQL SELECT 1`. {{< line_break >}} • For Informix use `SYSTABLES`.|

When you are satisfied with your selections, select **Next : Review + create**.

### Review + create

In the **Review + create blade**, review the details you provided for deploying Oracle WebLogic Server on AKS. If you want to make changes to any of the fields, click **< previous** or click on the respective blade and update the details.

If you want to use this template to automate the deployment, download it by selecting **Download a template for automation**.

Click **Create** to create this offer. This process may take 30 to 60 minutes.

### Template outputs

After clicking **Create** to create this offer, you will go to the **Deployment is in progress** page. When the deployment is completed, the page shows **Your deployment is complete**. In the left panel, select **Outputs**. These are the outputs from the deployment.  The following table is a reference guide to the deployment outputs.

| Field | Description |
|-------|-------------|
| `aksClusterName` | Name of your AKS cluster that is running the WLS cluster. {{< line_break >}}Sample value: `wlsonaksiyiql2i2o2u2i`. |
| `adminConsoleInternalUrl` | The fully qualified, private link to the Administration Console portal. You can access it only inside the AKS cluster. {{< line_break >}}Sample value: `http://sample-domain1-admin-server.sample-domain1-ns.svc.cluster.local:7001/console`. |
| `adminConsoleExternalUrl` | This output is not always present:{{< line_break >}}You must configure [Networking](#networking) to enable the Azure Load Balancer service or Azure Application Gateway Ingress Controller for the Administration Console.{{< line_break >}} {{< line_break >}}This is a fully qualified, public link to the Administration Console portal. You can access it from the public Internet. {{< line_break >}}Sample value: `http://wlsgw202208-wlsd-aks-2793762585-337-domain1.eastus.cloudapp.azure.com/console`. |
| `adminConsoleExternalSecuredUrl` | This output is not always present:{{< line_break >}}1. You must configure [Networking](#networking) to enable the Azure Load Balancer service or Azure Application Gateway Ingress Controller for the Administration Console.{{< line_break >}}2. You must configure a custom DNS name by filling out [DNS Configuration](#dns-configuration).{{< line_break >}}3. The TLS/SSL certificate used is configured by filling out [TLS/SSL configuration](#tlsssl-configuration).{{< line_break >}}{{< line_break >}}This is a fully qualified, secure, public link to the Administration Console portal. You can access it from the public Internet. {{< line_break >}}Sample value: `https://contoso.com/console`. |
| `adminRemoteConsoleUrl` | This output is not always present:{{< line_break >}}You must configure [Networking](#networking) to enable the Azure Load Balancer service or Azure Application Gateway Ingress Controller for the Administration Console.{{< line_break >}}{{< line_break >}}This is a fully qualified, public link to the [WebLogic Server Remote Console]({{< relref "/managing-domains/accessing-the-domain/remote-admin-console.md" >}}). You can access it from the public Internet.{{< line_break >}}Sample value: `http://wlsgw202208-wlsd-aks-2793762585-337-domain1.eastus.cloudapp.azure.com/remoteconsole`. |
| `adminRemoteConsoleSecuredUrl` | This output is not always present:{{< line_break >}}1. You must configure [Networking](#networking) to enable the Azure Load Balancer service or Azure Application Gateway Ingress Controller for the Administration Console.{{< line_break >}}2. You must configure a custom DNS name following [DNS Configuration](#dns-configuration).{{< line_break >}}3. The TLS/SSL certificate used is configured by filling out [TLS/SSL configuration](#tlsssl-configuration).{{< line_break >}}{{< line_break >}}This is a fully qualified, public link to the [WebLogic Server Remote Console]({{< relref "/managing-domains/accessing-the-domain/remote-admin-console.md" >}}). You can access it from the public Internet.{{< line_break >}}Sample value: `https://contoso.com/remoteconsole`.|
| `adminServerT3InternalUrl` | This output is not always present:{{< line_break >}}1. You must [create/update the WLS cluster with advanced configuration](https://oracle.github.io/weblogic-azure/aks/).{{< line_break >}} 2. You must enable custom T3 channel by setting `enableAdminT3Tunneling=true`.{{< line_break >}}{{< line_break >}}This is a fully qualified, private link to custom T3 channel of the Administration Server.{{< line_break >}}Sample value: `http://sample-domain1-admin-server.sample-domain1-ns.svc.cluster.local:7005/console`.|
| `adminServerT3ExternalUrl` | This output is not always present:{{< line_break >}}1. You must [create/update the WLS cluster with advanced configuration](https://oracle.github.io/weblogic-azure/aks/).{{< line_break >}}2. You must enable custom T3 channel by setting `enableAdminT3Tunneling=true`.{{< line_break >}}3. You must configure [Networking](#networking) to enable the Azure Load Balancer service for the Administration Server.{{< line_break >}}{{< line_break >}}This is a fully qualified, public link to custom T3 channel of the Administration Server.{{< line_break >}}Sample value: `http://20.4.56.3:7005/console/` |
| `clusterInternalUrl` | The fully qualified, private link to the WLS cluster. You are able to access your application with `${clusterInternalUrl}<your-app-path>` inside AKS cluster.{{< line_break >}}Sample value: `http://sample-domain1-cluster-cluster-1.sample-domain1-ns.svc.cluster.local:8001/`. |
| `clusterExternalUrl` | This output is not always present:{{< line_break >}}You must configure [Networking](#networking) to enable the Azure Load Balancer service or Azure Application Gateway Ingress Controller for the WLS cluster.{{< line_break >}}{{< line_break >}}This is a fully qualified, public link to the WLS cluster. You can access your application with `${clusterExternalUrl}<your-app-path>` from the public Internet.{{< line_break >}}Sample value: `http://wlsgw202208-wlsd-aks-2793762585-337-domain1.eastus.cloudapp.azure.com/`. |
| `clusterExternalSecuredUrl` | This output is not always present:{{< line_break >}}1. You must configure [Networking](#networking) to enable the Azure Load Balancer service or Azure Application Gateway Ingress Controller for the WLS cluster.{{< line_break >}}2. The TLS/SSL certificate used is configured by filling out [TLS/SSL configuration](#tlsssl-configuration).{{< line_break >}}{{< line_break >}}This is a fully qualified, public link to the WLS cluster. You can access your application with `${clusterExternalUrl}<your-app-path>` from the public Internet.{{< line_break >}}Sample value: `https://wlsgw202208-wlsd-aks-2793762585-337-domain1.eastus.cloudapp.azure.com/`. |
| `clusterT3InternalUrl` | This output is not always present:{{< line_break >}}1. You must [create/update the WLS cluster with advanced configuration](https://oracle.github.io/weblogic-azure/aks/).{{< line_break >}}2. You must enable custom T3 channel by setting `enableClusterT3Tunneling=true`.{{< line_break >}}{{< line_break >}}This is a fully qualified, private link to custom T3 channel of the WLS cluster. |
| `clusterT3ExternalEndpoint` | This output is not always present:{{< line_break >}}1. You must [create/update the WLS cluster with advanced configuration](https://oracle.github.io/weblogic-azure/aks/).{{< line_break >}}2. You must enable custom T3 channel by setting `enableClusterT3Tunneling=true`.{{< line_break >}}3. You must configure [Networking](#networking) to enable the Azure Load Balancer service for the WLS cluster.{{< line_break >}}{{< line_break >}}This is a fully qualified, public link to custom T3 channel of the WLS cluster.{{< line_break >}}Sample value:`http://20.4.56.3:8005/` |
| `shellCmdtoConnectAks` | AZ CLI command to connect to the AKS cluster.{{< line_break >}}Sample value: {{< line_break >}}`az account set --subscription xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxx; az aks get-credentials --resource-group contoso-rg --name contosoakscluster`|
| `shellCmdtoOutputWlsDomainYaml` | Shell command to display the base64 encoded string of the WLS domain resource definition.{{< line_break >}}Sample value: {{< line_break >}}`echo -e YXBpV...mVCg== \| base64 -d > domain.yaml` |
| `shellCmdtoOutputWlsImageModelYaml` | Shell command to display the base64 encoded string of the WLS [image model]({{< relref "/managing-domains/model-in-image/model-files.md" >}}).{{< line_break >}}Sample value:{{< line_break >}}`echo -e IyBDb...3EnC \| base64 -d > model.yaml`|
| `shellCmdtoOutputWlsImageProperties`|Shell command to display the base64 encoded string of the model properties.{{< line_break >}}Sample value:{{< line_break >}}`echo -e IyBDF...PTUK \| base64 -d > model.properties` |
| `shellCmdtoOutputWlsVersionsandPatches` | Shell command to display the base64 encoded string of the WLS version and patches.{{< line_break >}}Sample value:{{< line_break >}}`echo -e CldlY...gMS4= \| base64 -d > version.info`|

### Useful resources

Review the following useful resources.

#### Store the TLS/SSL certificate in the Key Vault

1. Base 64 encode the certificate file; omit the `-w0` for macOS:

    ```bash
    base64 myIdentity.jks -w0 >mycert.txt
    # base64 myIdentity.p12 -w0 >mycert.txt
    # base64 myTrust.jks -w0 >mycert.txt
    # base64 myTrust.p12 -w0 >mycert.txt
    # base64 root.cert -w0 >mycert.txt
    # base64 gatewayCert.pfx -w0 >mycert.txt
    ```

2. From the Azure portal, open your Key Vault.
3. In the Settings section, select Secrets.
4. Select Generate/Import.
5. Under Upload options, leave the default value.
6. Under Name, enter `myIdentityCertData`, or whatever name you like.
7. Under Value, enter the content of the mycert.txt file.
8. Leave the remaining values at their defaults and select Create.
