#!/bin/bash
# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
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
#   WDT_VERSION    WDT version
#                  default:  1.9.1
#
#   WDT_INSTALL_ZIP_URL   URL for downloading WDT install zip
#                  default:  https://github.com/oracle/weblogic-deploy-tooling/releases/download/release-$WDT_VERSION/$WDT_INSTALL_ZIP_FILE
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
#
#   DOMAIN_HOME_DIR  Target location for generated domain. 
#

# Initialize globals

export ORACLE_HOME=${ORACLE_HOME:-/u01/oracle}

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

# using "-" instead of ":-" in case proxy vars are explicitly set to "".
https_proxy=${PROXY_VAL-""}
https_proxy2=${https_proxy2-""}

source ${SCRIPTPATH}/wdt-and-wit-utility.sh

# Run

echo "create-domain-script.sh DOMAIN_TYPE is ${DOMAIN_TYPE}"

setup_wdt_shared_dir || exit 1

install_wdt || exit 1

run_wdt "create" || exit 1

echo "Does ${WDT_DIR}/doneExtract exist?"
while [ ! -f "${WDT_DIR}/doneExtract" ]; do
   echo @@ "${WDT_DIR}/doneExtract does not exist yet"
   sleep 20
done
echo "Found it and removing the doneExtract file"
rm -rf ${WDT_DIR}/doneExtract || exit 1

