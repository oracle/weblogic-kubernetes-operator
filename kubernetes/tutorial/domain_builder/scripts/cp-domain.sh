#!/bin/bash
if [ "$#" != 1 ] ; then
  echo "usage: $0 domainName"
  exit 1 
fi

mkdir -p /shared/domains/$1
cp -rf /u01/oracle/user-projects/domains/* /shared/domains/$1
chown -R oracle:oracle /shared

