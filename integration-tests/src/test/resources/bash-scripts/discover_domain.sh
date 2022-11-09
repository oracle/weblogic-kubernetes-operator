#!/bin/bash
# Copyright (c) 2021,2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# Description:
#
#   This script generates model files and variables files by calling WebLogic Deploy Tool (WDT)
#   bin/discoverDomain.sh script on an on-prem domain.
#
#   It first installs WDT using the given download URLs, and then runs it using the
#   given Oracle home. It then generates WDT model file, WDT vars file, etc.
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
#                  default:  https://github.com/oracle/weblogic-deploy-tooling/releases/download/release-1.9.7/$WDT_INSTALL_ZIP_FILE
#
#   https_proxy    Proxy for downloading WDT_INSTALL_ZIP_URL.
#                  default: ""
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
#   WDT_VERSION    WDT version to download.
#                  default:  latest
#
#   DOMAIN_HOME_DIR  Target location for generated domain.
#
#   DOMAIN_TYPE    Domain type. It can be WLS, JRF or RestrictedJRF. Default is WLS
#

# Initialize globals

# using "-" instead of ":-" in case proxy vars are explicitly set to "".
https_proxy=${https_proxy-""}
https_proxy2=${https_proxy2-""}
domain_src=${DOMAIN_SRC-""}
discover_domain_output_dir=${DISCOVER_DOMAIN_OUTPUT_DIR-""}
app=${APP-""}

export ORACLE_HOME=${ORACLE_HOME:-/u01/oracle}

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

WDT_DIR=${WDT_DIR:-/u01/wdt}
WDT_INSTALL_ZIP_FILE="${WDT_INSTALL_ZIP_FILE:-weblogic-deploy.zip}"
DOMAIN_TYPE="${DOMAIN_TYPE:-WLS}"
TARGET_TYPE="${TARGET_TYPE:-wko}"

# Define functions

setup_wdt_shared_dir() {
  mkdir -p $WDT_DIR || return 1
}

install_wdt() {
  #
  # Download $WDT_INSTALL_ZIP_FILE from $WDT_INSTALL_ZIP_URL into $WDT_DIR using
  # proxies https_proxy or https_proxy2, then unzip the zip file in $WDT_DIR.
  #

  local save_dir=`pwd`
  cd $WDT_DIR || return 1

  local curl_res=1
  max=20
  count=0
  while [ $curl_res -ne 0 -a $count -lt $max ] ; do
    sleep 10
    count=`expr $count + 1`
    for proxy in "${https_proxy}" "${https_proxy2}"; do
	  echo @@ "Info:  Downloading $WDT_INSTALL_ZIP_URL with https_proxy=\"$proxy\""
	  echo @@ "Info: calling curl: curl --show-error --connect-timeout 10 -L $WDT_INSTALL_ZIP_URL -o $WDT_INSTALL_ZIP_FILE"
	  https_proxy="${proxy}" \
	    curl --show-error --connect-timeout 10 -L $WDT_INSTALL_ZIP_URL -o $WDT_INSTALL_ZIP_FILE
	  curl_res=$?
	  [ $curl_res -eq 0 ] && break
	done
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

run_wdt() {
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
  local wdt_discoverDomain_script="$wdt_bin_dir/discoverDomain.sh"

  local domain_home_dir="/u01/$domain_src"
  if [ -z "${domain_home_dir}" ]; then
    local domain_dir="/shared/domains"
    local domain_uid=`grep -E 'domainUID' $inputs_orig | awk '{print $2}'`
    local domain_home_dir=$domain_dir/$domain_uid
  fi 

  echo domain_home_dir = $domain_home_dir
  mkdir -p $domain_home_dir
  unzip /u01/${domain_src}.zip -d $domain_home_dir

  # Output files and directories.

  local variable_file=${domain_src}.properties
  local model_file=${domain_src}.yaml
  local archive_file=${app}.zip
  local output_dir="/u01/${discover_domain_output_dir}"
  mkdir -p $output_dir
  local out_file=$WDT_DIR/discoverDomain.sh.out
  local wdt_log_dir="$WDT_DIR/weblogic-deploy/logs"

  local domain_type="$DOMAIN_TYPE"
  local target_type="$TARGET_TYPE"

  echo @@ "Info:  About to run WDT discoverDomain.sh to generate required files to create $domain_type Domain"

  for directory in wdt_bin_dir SCRIPTPATH WDT_DIR oracle_home; do
    if [ ! -d "${!directory}" ]; then
       echo @@ "Error:  Could not find ${directory} directory ${!directory}."    
       return 1
    fi
  done

  local save_dir=`pwd`
  cd $output_dir || return 1

  echo @@ "Info:  WDT discoverDomain.sh output will be in $out_file and $wdt_log_dir"

  $wdt_discoverDomain_script \
     -oracle_home $oracle_home \
     -target $target_type \
     -domain_home $domain_home_dir \
     -model_file $model_file \
     -variable_file $variable_file \
     -archive_file $archive_file \
     -output_dir $output_dir > $out_file 2>&1

  local wdt_res=$?

  cd $save_dir

  if [ $wdt_res -ne 0 ]; then
    cat $WDT_DIR/discoverDomain.sh.out
    echo @@ "Info:  WDT discoverDomain.sh output is in $out_file and $wdt_log_dir"
    echo @@ "Error:  WDT discoverDomain.sh failed."
    return 1
  fi

  echo @@ "Info:  WDT createDomain.sh succeeded."
  return 0
}

# Run

setup_wdt_shared_dir || exit 1

install_wdt || exit 1

run_wdt || exit 1
