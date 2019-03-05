#!/bin/bash
# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# Description:
#
#   This script generates a domain by calling the WebLogic Deploy Tool (WDT)
#   bin/createDomain.sh script.
#
#   It first installs WDT using the given download URLs, and then runs it using the
#   given Oracle home, WDT model file, WDT vars file, etc.
#
# Usage:
#
#   Export customized values for the input shell environment variables as needed
#   before calling this script.   
#
# Outputs:
#
#   WDT install:           WDT_DIR/weblogic-deploy/...
#
#   Copy of wdt model:     WDT_DIR/$(basename WDT_MODEL_FILE)
#   Copy of wdt vars:      WDT_DIR/$(basename WDT_VAR_FILE)
#
#   WDT logs:              WDT_DIR/weblogic-deploy/logs/...
#   WDT stdout:            WDT_DIR/createDomain.sh.out
#
#   WebLogic domain home:  DOMAIN_HOME_DIR
#                          default: /shared/domains/<domainUID>
#
# Input environment variables:
#
#   ORACLE_HOME    Oracle home with a WebLogic install.
#                  default:  /u01/oracle
#
#   WDT_MODEL_FILE Full path to WDT model file.
#                  default:  the directory that contains this script
#                            plus "/wdt_model.yaml"
#
#   WDT_VAR_FILE   Full path to WDT variable file (java properties format).
#                  default:  the directory that contains this script
#                            plus "/create-domain-inputs.yaml"
#
#   WDT_INSTALL_ZIP_FILE  Filename of WDT install zip.
#                  default:  weblogic-deploy.zip
#
#   WDT_INSTALL_ZIP_URL   URL for downloading WDT install zip
#                  default:  https://github.com/oracle/weblogic-deploy-tooling/releases/download/weblogic-deploy-tooling-0.17/$WDT_INSTALL_ZIP_FILE
#
#   https_proxy    Proxy for downloading WDT_INSTALL_ZIP_URL.
#                  default: "http://www-proxy-hqdc.us.oracle.com:80"
#                  (If set to empty the script will try with no proxy.)
#
#   https_proxy2   Alternate proxy for downloading WDT_INSTALL_ZIP_URL
#                  default: ""
#                  (If set to empty the script will try with no proxy.)
#
#   WDT_DIR        Target location to install and run WDT, and to keep a copy of
#                  $WDT_MODEL_FILE and $WDT_MODEL_VARS. Also the location
#                  of WDT log files.
#                  default:  /shared/wdt
#
#   DOMAIN_HOME_DIR  Target location for generated domain. 
#

# Initialize globals

export ORACLE_HOME=${ORACLE_HOME:-/u01/oracle}

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
WDT_MODEL_FILE=${WDT_MODEL_FILE:-"$SCRIPTPATH/wdt_model.yaml"}
WDT_VAR_FILE=${WDT_VAR_FILE:-"$SCRIPTPATH/create-domain-inputs.yaml"}

WDT_DIR=${WDT_DIR:-/shared/wdt}

WDT_INSTALL_ZIP_FILE="${WDT_INSTALL_ZIP_FILE:-weblogic-deploy.zip}"
WDT_INSTALL_ZIP_URL=${WDT_INSTALL_ZIP_URL:-"https://github.com/oracle/weblogic-deploy-tooling/releases/download/weblogic-deploy-tooling-0.17/$WDT_INSTALL_ZIP_FILE"}

# using "-" instead of ":-" in case proxy vars are explicitly set to "".
https_proxy=${https_proxy-""}
https_proxy2=${https_proxy2-"http://www-proxy-hqdc.us.oracle.com:80"}

# Define functions

function setup_wdt_shared_dir {
  mkdir -p $WDT_DIR || return 1
}

function install_wdt {
  #
  # Download $WDT_INSTALL_ZIP_FILE from $WDT_INSTALL_ZIP_URL into $WDT_DIR using
  # proxies https_proxy or https_proxy2, then unzip the zip file in $WDT_DIR.
  #

  local save_dir=`pwd`
  cd $WDT_DIR || return 1

  local curl_res=1
  for proxy in "${https_proxy}" "${https_proxy2}"; do
    echo @@ "Info:  Downloading $WDT_INSTALL_ZIP_URL with https_proxy=\"$proxy\""
    https_proxy="${proxy}" \
      curl --silent --show-error --connect-timeout 10 -O -L $WDT_INSTALL_ZIP_URL 
    curl_res=$?
    [ $curl_res -eq 0 ] && break
  done

  if [ $curl_res -ne 0 ] || [ ! -f $WDT_INSTALL_ZIP_FILE ]; then
    cd $save_dir
    echo @@ "Error: Download failed or $WDT_INSTALL_ZIP_FILE not found."
    return 1
  fi

  echo @@ "Info: Archive downloaded to $WDT_DIR/$WDT_INSTALL_ZIP_FILE, about to unzip via 'jar xf'."

  jar xf $WDT_INSTALL_ZIP_FILE
  local jar_res=$?

  cd $save_dir

  if [ $jar_res -ne 0 ]; then
    echo @@ "Error: Install failed while unzipping $WDT_DIR/$WDT_INSTALL_ZIP_FILE"
    return $jar_res
  fi

  if [ ! -d "$WDT_DIR/weblogic-deploy/bin" ]; then
    echo @@ "Error: Install failed: directory '$WDT_DIR/weblogic-deploy/bin' not found."
    return 1
  fi

  chmod 775 $WDT_DIR/weblogic-deploy/bin/* || return 1

  echo @@ "Info: Install succeeded, wdt install is in the $WDT_DIR/weblogic-deploy directory."
  return 0
}

function run_wdt {
  #
  # Run WDT using WDT_VAR_FILE, WDT_MODEL_FILE, and ORACLE_HOME.  
  # Output:
  # - result domain will be in DOMAIN_HOME_DIR
  # - logging output is in $WDT_DIR/createDomain.sh.out and $WDT_DIR/weblogic-deploy/logs
  # - WDT_VAR_FILE & WDT_MODEL_FILE will be copied to WDT_DIR.
  #

  # Input files and directories.

  local inputs_orig="$WDT_VAR_FILE"
  local model_orig="$WDT_MODEL_FILE"
  local oracle_home="$ORACLE_HOME"
  local wdt_bin_dir="$WDT_DIR/weblogic-deploy/bin"
  local wdt_createDomain_script="$wdt_bin_dir/createDomain.sh"

  local domain_home_dir="$DOMAIN_HOME_DIR"
  if [ -z "${domain_home_dir}" ]; then
    local domain_dir="/shared/domains"
    local domain_uid=`egrep 'domainUID' $inputs_orig | awk '{print $2}'`
    local domain_home_dir=$domain_dir/$domain_uid
  fi 

  echo domain_home_dir = $domain_home_dir

  # Output files and directories.

  local inputs_final=$WDT_DIR/$(basename "$inputs_orig")
  local model_final=$WDT_DIR/$(basename "$model_orig")
  local out_file=$WDT_DIR/createDomain.sh.out
  local wdt_log_dir="$WDT_DIR/weblogic-deploy/logs"

  echo @@ "Info:  About to run WDT createDomain.sh"

  for directory in wdt_bin_dir SCRIPTPATH WDT_DIR oracle_home; do
    if [ ! -d "${!directory}" ]; then
       echo @@ "Error:  Could not find ${directory} directory ${!directory}."    
       return 1
    fi
  done

  for fil in inputs_orig model_orig wdt_createDomain_script; do
    if [ ! -f "${!fil}" ]; then
       echo @@ "Error:  Could not find ${fil} file ${!fil}."
       return 1
    fi
  done

  cp $model_orig $model_final   || return 1
  cp $inputs_orig $inputs_final || return 1

  local save_dir=`pwd`
  cd $WDT_DIR || return 1

  echo @@ "Info:  WDT createDomain.sh output will be in $out_file and $wdt_log_dir"

  $wdt_createDomain_script \
     -oracle_home $oracle_home \
     -domain_type WLS \
     -domain_home $domain_home_dir \
     -model_file $model_final \
     -variable_file $inputs_final > $out_file 2>&1

  local wdt_res=$?

  cd $save_dir

  if [ $wdt_res -ne 0 ]; then
    cat $WDT_DIR/createDomain.sh.out
    echo @@ "Info:  WDT createDomain.sh output is in $out_file and $wdt_log_dir"
    echo @@ "Error:  WDT createDomain.sh failed." 
    return 1
  fi

  echo @@ "Info:  WDT createDomain.sh succeeded."
  return 0
}

# Run

setup_wdt_shared_dir || exit 1

install_wdt || exit 1

run_wdt || exit 1
