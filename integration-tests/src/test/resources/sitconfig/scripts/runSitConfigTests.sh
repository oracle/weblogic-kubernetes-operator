#!/bin/bash

## The script is copied to the administration server weblogic server
## pod along with the SitConfigTests.java source file by the JUnit test 
## ITSitConfigTests.java running in the integration test suite.
## The integration test suite does not have access to the weblogic.jar file
## needed for building and running the test client and hence it needs to be 
## built and run inside the weblogic server pod.
## The SitConfigTests is recursively called by the JUnit tests in  ITSitConfigTests.java
## to run different overrride tests
## The SitConfigTests uses java assertions to assert the expected values and 
## exits with status 1 if the assertions are not passed.

source $ORACLE_HOME/wlserver/server/bin/setWLSEnv.sh
javac -d . SitConfigTests.java
java -ea -cp $ORACLE_HOME:$CLASSPATH oracle.kubernetes.operator.SitConfigTests $@

