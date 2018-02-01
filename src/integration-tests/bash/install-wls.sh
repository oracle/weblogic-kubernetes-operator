#!/bin/bash
#  Copyright 2018, Oracle Corporation and/or affiliates.  All rights reserved.

echo 'Checking/installing WLS'
mkdir -p /scratch/$USER/weblogic/inventory

pushd /scratch/$USER/weblogic

# create test script to exit WLST
rm -f exit.py
cat <<EOF > exit.py
exit()
EOF

function test_wls {
    if [ -f /scratch/$USER/weblogic/Oracle_Home/wlserver/server/bin/setWLSEnv.sh ]; then
        # Set the PATH and CLASSPATH for WLS
        . /scratch/$USER/weblogic/Oracle_Home/wlserver/server/bin/setWLSEnv.sh
        if java weblogic.WLST exit.py; then
            echo WLS installed and weblogic.WLST can be run
            INSTALL_OK=true
        fi
    else
        echo "Install failed."
    fi
}

# Get the latest install jar unless we already have it
INSTALL_JAR='fmw_12.2.1.3.0_wls_generic.jar'
# if it's not new, then check whether we've installed it and the install works for WLST
if [ -f $INSTALL_JAR ]; then
    CURRENT_TS=`stat -c %y $INSTALL_JAR`
fi
wget -nv -N http://slc07jcz.us.oracle.com/ga-kits/12.2.1.3.0/$INSTALL_JAR --no-proxy
if [ -f $INSTALL_JAR ]; then
    NEW_TS=`stat -c %y $INSTALL_JAR`
fi
INSTALL_OK=false
# Test our previous install to make sure it's ok 
if [[ $CURRENT_TS == $NEW_TS ]]; then
    echo "Testing existing install"
    test_wls
fi
if [[ $CURRENT_TS != $NEW_TS || $INSTALL_OK == false ]]; then
    echo "Installing WLS"
    rm -f response-file
    cat <<EOF > response-file
ORACLE_HOME=/scratch/$USER/weblogic/Oracle_Home
DECLINE_SECURITY_UPDATES=true
INSTALL_TYPE=WebLogic Server
EOF

    rm -f oraInst.loc
    cat <<EOF > oraInst.loc 
inventory_loc=/scratch/$USER/weblogic/inventory
install_group=dba
EOF

    java -jar fmw_12.2.1.3.0_wls_generic.jar -silent -responseFile /scratch/$USER/weblogic/response-file -invPtrLoc /scratch/$USER/weblogic/oraInst.loc
    # make sure it works
    test_wls
fi
popd

if [[ $INSTALL_OK != 'true' ]]; then
    exit 1
fi
