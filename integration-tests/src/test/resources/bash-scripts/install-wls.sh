#!/bin/bash
# Copyright (c) 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

echo $JAVA_HOME
if [ -n "$1" ] || [ -n "$result_root" ]; then
    echo "RESULT_ROOT or result_root is set"
else
    echo "no RESULT_ROOT or result_root is set, exiting wls installation."
    exit 0
fi

if [ -n "$2" ] || [ -n "$SHIPHOME_DOWNLOAD_SERVER" ]; then
      echo "SHIPHOME_DOWNLOAD_SERVER is set"      
    else
      echo "no SHIPHOME_DOWNLOAD_SERVER is set, exiting wls installation."
      exit 0
    fi

if [ -z "$1" ]; then
    echo "running in Jenkins, result_root is set"
else
    echo "running in localhost, RESULT_ROOT is set : $1"
    result_root=$1
    SHIPHOME_DOWNLOAD_SERVER=$2
    
fi
echo $result_root
echo $SHIPHOME_DOWNLOAD_SERVER
MW_HOME="$result_root/mwhome"
SILENT_RESPONSE_FILE=$result_root/silent.response
ORAINVENTORYPOINTER_LOC=$result_root/oraInv.loc
ORAINVENTORY_LOC=$result_root/oraInventory
WLS_SHIPHOME=$result_root/fmw_wls_generic.jar
DOWNLOAD_URL="http://$SHIPHOME_DOWNLOAD_SERVER/results/release/src141200/fmw_14.1.2.0.0_wls_generic.jar"
SUCCESS="The\ installation\ of\ Oracle\ Fusion\ Middleware.*completed\ successfully"

rm -rf $MW_HOME/*
rm -rf $SILENT_RESPONSE_FILE
rm -rf $ORAINVENTORY_LOC/*
rm -rf $ORAINVENTORYPOINTER_LOC
mkdir -p $MW_HOME
mkdir -p $ORAINVENTORY_LOC

echo "creating $SILENT_RESPONSE_FILE file with contents"

cat <<EOF > $SILENT_RESPONSE_FILE
[ENGINE]
Response File Version=1.0.0.0.0
[GENERIC]
ORACLE_HOME=$MW_HOME
INSTALL_TYPE=WebLogic Server
EOF

cat $SILENT_RESPONSE_FILE

echo "creating $ORAINVENTORYPOINTER_LOC file with contents"

cat <<EOF > $ORAINVENTORYPOINTER_LOC
inventory_loc=$ORAINVENTORY_LOC
inst_group=opc
EOF

cat $ORAINVENTORYPOINTER_LOC

#download WebLogic shiphome installer
curl -Lo $WLS_SHIPHOME $DOWNLOAD_URL
ls -l $WLS_SHIPHOME
md5sum $WLS_SHIPHOME

#install WebLogic
echo "Running java -jar $WLS_SHIPHOME -silent -responseFile $SILENT_RESPONSE_FILE -invPtrLoc $ORAINVENTORYPOINTER_LOC"
install_log=$(java -jar $WLS_SHIPHOME -silent -responseFile $SILENT_RESPONSE_FILE -invPtrLoc $ORAINVENTORYPOINTER_LOC)
if [[ "$install_log" =~ $SUCCESS ]]; then
  echo "The installation of WebLogic completed successfully."
  . $MW_HOME/wlserver/server/bin/setWLSEnv.sh
  java weblogic.version
else
  echo "The installation of WebLogic failed."
fi
