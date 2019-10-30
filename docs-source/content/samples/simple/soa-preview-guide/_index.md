---
title: "SOA Preview Guide"
date: 2019-10-14T11:21:31-05:00
weight: 7
description: "End-to-end guide for SOA Suite preview testers."
---

### End-to-end guide for Oracle SOA Suite preview testers

This document provides detailed instructions for testing the Oracle SOA Suite preview. 
This guide uses the WebLogic Kubernetes operator version 2.3.0 and SOA Suite 12.2.1.3.0.
SOA Suite has also been tested using the WebLogic Kubernetes operator version 2.2.1.
SOA Suite is currently a *preview*, meaning that everything is tested and should work,
but official support is not available yet. 
You can, however, come to [our public Slack](https://weblogic-slack-inviter.herokuapp.com/) to ask questions
and provide feedback.
At Oracle OpenWorld 2019, we did announce our *intention* to provide official
support for SOA Suite running on Kubernetes in 2020 (subject to the standard Safe Harbor statement).
For planning purposes, it would be reasonable to assume that the production support would
likely be for Oracle SOA Suite 12.2.1.4.0.

{{% notice warning %}}
Oracle SOA Suite is currently only supported for non-production use in Docker and Kubernetes.  The information provided
in this document is a *preview* for early adopters who wish to experiment with Oracle SOA Suite in Kubernetes before
it is supported for production use.
{{% /notice %}}

#### Overview

This guide will help you to test the Oracle SOA Suite preview in Kubernetes.  The guide presents
a complete end-to-end example of setting up and using SOA Suite in Kubernetes including:

* [Preparing your Kubernetes cluster](#preparing-your-kubernetes-cluster).
* [Obtaining the necessary Docker images](#obtaining-the-necessary-docker-images).
* [Installing the WebLogic Kubernetes operator](#installing-the-weblogic-kubernetes-operator).
* [Preparing your database for the SOAINFRA schemas](#preparing-your-database-for-the-soainfra-schemas).
* [Running the Repository Creation Utility to populate the database](#running-the-repository-creation-utility-to-populate-the-database).
* [Creating a SOA domain](#creating-a-soa-domain).
* [Starting the SOA domain in Kubernetes](#starting-the-soa-domain-in-kubernetes).
* [Setting up a load balancer to access various SOA endpoints](#setting-up-a-load-balancer-to-access-various-soa-endpoints).
* [Configuring the SOA cluster for access through a load balancer](#configuring-the-soa-cluster-for-access-through-a-load-balancer).
* [Deploying a SCA composite to the domain](#deploying-a-sca-composite-to-the-domain).
* [Accessing the SCA composite and various SOA web interfaces](#accessing-the-sca-composite-and-various-soa-web-interfaces). 
* [Configuring the domain to send logs to Elasticsearch](#configuring-the-domain-to-send-logs-to-elasticsearch).
* [Using Kibana to view logs for the domain](#using-kibana-to-view-logs-for-the-domain).
* [Configuring the domain to send metrics to Prometheus](#configuring-the-domain-to-send-metrics-to-prometheus).
* [Using the Grafana dashboards to view metrics for the domain](#using-the-grafana-dashboards-to-view-metrics-for-the-domain).

{{% notice note %}}
**Feedback**  
If you find any issues with this guide, please [open an issue in our GitHub repository](https://github.com/oracle/weblogic-kubernetes-operator/issues/new)
or report it on [our public Slack](https://weblogic-slack-inviter.herokuapp.com/).  Thanks!
{{% /notice %}}

#### Preparing your Kubernetes cluster


#### Obtaining the necessary Docker images


#### Installing the WebLogic Kubernetes operator


#### Preparing your database for the SOAINFRA schemas


#### Running the Repository Creation Utility to populate the database


#### Creating a SOA domain


#### Starting the SOA domain in Kubernetes


#### Setting up a load balancer to access various SOA endpoints


#### Configuring the SOA cluster for access through a load balancer


#### Deploying a SCA composite to the domain


#### Accessing the SCA composite and various SOA web interfaces


#### Configuring the domain to send logs to Elasticsearch


#### Using Kibana to view logs for the domain


#### Configuring the domain to send metrics to Prometheus


#### Using the Grafana dashboards to view metrics for the domain


