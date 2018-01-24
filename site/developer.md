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

The Javadoc is also available in the GitHub repository at [https://oracle.github.io/weblogic-kubernetes-operator/apidocs/index.html](https://oracle.github.io/weblogic-kubernetes-operator/apidocs/index.html).

## Running integration tests

The project includes integration tests that can be run against a Kubernetes *cluster*.  If you want to use these tests, you will need to provide your own Kubernetes *cluster*.  You will need to obtain the kube.config file for an admin user and make it available on the machine running the build.  Tests will run against Kubernetes 1.7.x and 1.8.x currently.  There are some issues with 1.9, which are being worked on.

To run the tests, uncomment the following `execution` in the `pom.xml` and update the `KUBECONFIG` to point to your kube config file.

```
<!--
<execution>
  <id>kubernetes-config</id>
  <phase>test</phase>
  <goals>
      <goal>test</goal>
  </goals>
  <configuration>
      <argLine>${surefireArgLine} -Xms512m -Xmx1500m</argLine>
      <environmentVariables>
          <KUBECONFIG>
              ${project.basedir}/your.kube.config
          </KUBECONFIG>
      </environmentVariables>
  </configuration>
</execution>
-->
```

These test assume that the RBAC definitions exist on the Kubernetes *cluster*.  To create them, update the inputs file and run the *operator* installation script with the "generate only" option as shown below (see the [installation](installation.md) page for details about this script and the inputs):

```
./create-weblogic-operator.sh -g -i create-operator-inputs.yaml
```

This will create a file called `rbac.yaml` which you will need to apply to your cluster:

```
kubectl apply -f rbac.yaml
```

Once this is done, and the `execution` is uncommented, the tests will run against your cluster.

## Running the operator from an IDE

The operator can be run from an IDE, which is useful for debugging.  In order to do so, the machine running the IDE must be configured with a Kubernetes configuration file in `~/.kube/config` or in a location pointed to by the `KUBECONFIG` environment variable.

Configure the IDE to run the class `oracle.kubernetes.operator.Main`.

You may need to create a directory called `/operator` on your machine.  Please be aware that the *operator* code is targeted to Linux, and while it will run fine on macOS, it will probably not run on other operating systems.  If you develop on another operating system, you should deploy the operator to a Kubernetes *cluster* and use remote debugging instead.

## Running the operator in a Kubernetes cluster

To run the *operator* in a Kubernetes *cluster* you need to build the Docker image and then deploy it to your *cluster*.

After you have run the build (i.e. `mvn clean install`), create the Docker image as follows:

```
docker build -t weblogic-kubernetes-operator:markxnelson --no-cache=true .
```

We recommend that you use a tag other than `latest` to make it easy to tell your image apart from the "real" one.  In the example above, we just put in the github ID of the developer.

Next upload your image to your Kubernetes server as follows:

```
# on your build machine
docker save weblogic-kubernetes-operator:markxnelson > operator.tar
scp operator.tar YOUR_USER@YOUR_SERVER:/some/path/operator.tar
# on the Kubernetes server
docker load < /some/path/operator.tar
```

Verify you have the right image by running `docker images | grep webloogic-kubernetes-operator` on both machines and comparing the image ID.

To create the Kuberentes YAML file to deploy the *operator*, update the inputs file (`create-operator-inputs.yaml`) and make sure the `imagePullPolicy` is set to `Never` and the `image` matches the name you used in your `docker build` command.  Then run the *operator* installation script to deploy the *operator*:

```
./create-weblogic-operator.sh -i create-operator-inputs.yaml
```


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

### Watch package

The Watch API in the Kubernetes Java client provides a watch capability across a specific list of resources for a limited amount of time. As such it is not ideally suited our use case, where a continuous stream of watches was desired, with watch events generated in real-time. The watch-wrapper in this repository extends the default Watch API to provide a continuous stream of watch events until the stream is specifically closed. It also provides `resourceVersion` tracking to exclude events that have already been seen.  The Watch-wrapper provides callbacks so events, as they occur, can trigger actions.

## Asynchronous call model

Write me
