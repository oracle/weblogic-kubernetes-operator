# Developer guide

This page provides information for developers who wish to understand or contribute to the code.

## Requirements

The following software are required to obtain and build the operator:

*	git (1.8 or later recommended)
*	Apache Maven (3.3 or later recommended)
*	Java Developer Kit (1.8u131 or later recommended, not 1.9)
*	Docker 17.03.1.ce

The operator is written primarily in Java and BASH shell scripts.  The Java code uses features introduced in Java 1.8, for example closures, but does not use any Java 1.9 feature.

Since the target runtime environment for operator is Oracle Linux, no particular effort has been made to ensure the build or tests run on any other operating system.  Please be aware that Oracle will not provide support for, or accept pull requests to add support for other operating systems.

## Obtaining the operator source code

The operator source code is published on GitHub at https://github.com/oracle/weblogic-operator.  Developers may clone this repository to a local machine, or if desired create a fork in their personal namespace and clone the fork.  Developers who are planning to submit a pull request are advised to create a fork.

To clone the repository from GitHub, issue this command:

```
git clone https://github.com/oracle/weblogic-operator
```

## Building the operator

The operator is built using [Apache Maven](http://maven.apache.org).  The build machine will also need to have Docker installed.  

To build the operator issue the following command in the project directory:

```
mvn clean install
```

This will compile the source files, build JAR files containing the compiled classes and libraries needed to run the Operator and will also execute all of the unit tests.

## Building Javadoc

To build the Javadoc for the operator, issue the following command:

```
mvn javadoc:javadoc
```

The Javadoc is also available in the GitHub repository at [https://oracle.github.io/weblogic-kubernetes-operator/docs/apidocs/index.html](https://oracle.github.io/weblogic-kubernetes-operator/docs/apidocs/index.html).

## Running integration tests

Write me

## Manually testing the operator

Write me

## Running the operator from an IDE

The operator can be run from an IDE, which is useful for debugging.  In order to do so, the machine running the IDE must be configured with a Kubernetes configuration file in `~/.kube/config` or in a location pointed to by the `KUBECONFIG` environment variable.
Configure the IDE to run the class `oracle.kubernetes.operator.Main`.

## Running the operator in a Kubernetes cluster

Write me

## Attaching a remote debugger to the operator

Write me

## Coding standards

This project has adopted the following coding standards:

* All indents are two spaces.
* Javadoc must be provided for all public packages, classes and methods and must include all parameters and returns.
* All non-trivial methods should include `LOGGER.entering()` and `LOGGER.exiting()` calls.
* The `LOGGER.exiting()` call should include the value that is going to be returned from the method, unless that value includes a credential or other sensitive information.
* Before throwing an exception, there should be a call to `LOGGER.throwing(e)` to log the exception.
* write me

## Source code structure

Write me

## Asynchronous call model

Write me
