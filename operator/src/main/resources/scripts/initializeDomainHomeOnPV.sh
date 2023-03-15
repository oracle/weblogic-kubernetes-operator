#!/bin/sh
# Copyright (c) 2023, Oracle and/or its affiliates.

scriptDir="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
if [ "${debug}" == "true" ]; then set -x; fi;

. ${scriptDir}/utils_base.sh
[ $? -ne 0 ] && echo "[SEVERE] Missing file ${scriptDir}/utils_base.sh" && exit 1
UNKNOWN_SHELL=true

checkEnv DOMAIN_HOME || exit 1

ORIGIFS=$IFS
trace "DOMAIN HOME is " $DOMAIN_HOME
trace "Running the script as "`id`

if [[ -f $DOMAIN_HOME ]]; then
  if [[ -f $DOMAIN_HOME/config/config.xml ]] ; then
    trace INFO "DOMAIN_HOME "$DOMAIN_HOME" already exists, no operation. Exiting with 0 return code"
    exit 0
  else
    trace SEVERE "DOMAIN_HOME "$DOMAIN_HOME" is not empty and does not contain any WebLogic Domain. Please use an empty directory for domainHome"
    exit 1
  fi
fi

IFS='/'
# domain home is tokenized by '/' now
read -a dh_array <<< $DOMAIN_HOME
IFS=$OLDIFS

number_of_tokens=${#dh_array[@]}
trace "Number of tokens in domain home "$number_of_tokens

# walk through the list of tokens and reconstruct the path from beginning to find the
# base path
for (( i=1 ; i<=number_of_tokens; i++))
do
   temp_dir=${dh_array[@]:1:i}
   # parameter substitution turns spece to slash
   test_dir="/${temp_dir// //}"
   trace "Testing base mount path at "$test_dir
   if [ -d $test_dir ] ; then
       root_dir=test_dir
       trace "Found base mount path at "$test_dir
       break
   fi
done

if [ -z $root_dir ] ; then
   echo "Error: Cannot initial domain home directory: domain home "$DOAMIN_HOME" not under mountPath in any of the serverPod.volumeMounts"
   exit 1
fi

SHARE_ROOT=$root_dir

trace "Creating domain home and setting the permission from share root "$SHARE_ROOT
mkdir -p $DOMAIN_HOME || trace SEVERE "Failed to create domain home"
cd $SHARE_ROOT || trace SEVERE "Failed to cd into share root "$SHARE_ROOT
find * -prune -exec chown -R 1000:0 {} \; || trace SEVERE "Failed to change directory permission"

trace "Creating domain home completed"
ls -Rl $DOMAIN_HOME
exit



