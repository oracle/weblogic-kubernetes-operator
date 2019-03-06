#!/bin/bash -x
source $HOME/wlserver/server/bin/setWLSEnv.sh
javac -d . SitConfigTests.java
java -ea -cp $HOME:$CLASSPATH oracle.kubernetes.operator.SitConfigTests $@

