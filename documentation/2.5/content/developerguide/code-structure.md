---
title: "Code structure"
date: 2019-02-23T17:25:04-05:00
draft: false
weight: 6
---

This project has the following directory structure:

* `docs`: Generated Javadoc and Swagger
* `integration-tests`: Integration test suite
* `json-schema`: Java model to JSON schema generator
* `json-schema-maven-plugin`: Maven plugin for schema generator
* `kubernetes/charts`: Helm charts
* `kubernetes/samples`: All samples, including for WebLogic domain creation
* `model`: Domain resource Java model
* `operator`: Operator runtime
* `site`: This documentation
* `src/scripts`: Scripts operator injects into WebLogic server instance Pods
* `swagger`: Swagger files for the Kubernetes API server and domain resource

### Watch package

The Watch API in the Kubernetes Java client provides a watch capability across a specific list of resources for a limited amount of time. As such, it is not ideally suited for our use case, where a continuous stream of watches is desired, with watch events generated in real time. The watch-wrapper in this repository extends the default Watch API to provide a continuous stream of watch events until the stream is specifically closed. It also provides `resourceVersion` tracking to exclude events that have already been seen.  The watch-wrapper provides callbacks so events, as they occur, can trigger actions.
