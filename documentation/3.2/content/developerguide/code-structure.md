---
title: "Code structure"
date: 2019-02-23T17:25:04-05:00
draft: false
weight: 6
---

This project has the following directory structure:

* `documentation/latest`: This documentation
* `documentation/<numbered directory>`: The archived documentation for a previous release
* `documentation/charts`: Helm chart repository
* `documentation/swagger`: The operator REST API swagger
* `documentation/domains`: Reference for Domain resource schema 
* `json-schema-generator`: Java model to JSON schema generator
* `json-schema-maven-plugin`: Maven plugin for schema generator
* `kubernetes/charts`: Helm charts
* `kubernetes/samples`: All samples, including for WebLogic domain creation
* `integration-tests`: JUnit 5 integration test suite
* `operator`: Operator runtime
* `swagger-generator`: Swagger file generator for the Kubernetes API server and Domain type

### Watch package

The Watch API in the Kubernetes Java client provides a watch capability across a specific list of resources for a limited amount of time. As such, it is not ideally suited for our use case, where a continuous stream of watches is desired, with watch events generated in real time. The watch-wrapper in this repository extends the default Watch API to provide a continuous stream of watch events until the stream is specifically closed. It also provides `resourceVersion` tracking to exclude events that have already been seen.  The watch-wrapper provides callbacks so events, as they occur, can trigger actions.
