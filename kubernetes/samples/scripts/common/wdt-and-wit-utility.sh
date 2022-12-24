#!/bin/bash
# Copyright (c) 2021, 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# Description:
#
#   This script contains functions for installing WebLogic Deploy Tool (WDT) and
#   WebLogic Image Tool (WIT), and for running WDT.
#
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
#   DOMAIN_HOME_DIR  Target location for generated domain.
#
#   WDT_MODEL_FILE Full path to WDT model file.
#                  default:  the directory that contains this script
#                            plus "/wdt_model.yaml"
#
#   WDT_VAR_FILE   Full path to WDT variable file (java properties format).
#                  default:  the directory that contains this script
#                            plus "/create-domain-inputs.yaml"
#
#   WDT_DIR        Target location to install and run WDT, and to keep a copy of
#                  $WDT_MODEL_FILE and $WDT_MODEL_VARS. Also the location
#                  of WDT log files.
#                  default:  /shared/wdt
#
#   WDT_VERSION    WDT version to download.
#                  default:  LATEST
#
#   WDT_INSTALL_ZIP_FILE  Filename of WDT install zip.
#                  default:  weblogic-deploy.zip
#
#   WDT_INSTALL_ZIP_URL   URL for downloading WDT install zip
#                  default:  https://github.com/oracle/weblogic-deploy-tooling/releases/latest/download/$WDT_INSTALL_ZIP_FILE
#
#   WIT_DIR        Target location to install WIT
#                  default: /shared/imagetool
#
#   WIT_VERSION    WIT version to download.
#                  default:  LATEST
#
#   WIT_INSTALL_ZIP_FILE  Filename of WIT install zip.
#                  default:  imagetool.zip
#
#   WIT_INSTALL_ZIP_URL   URL for downloading WIT install zip
#                  default:  https://github.com/oracle/weblogic-image-tool/releases/latest/download/$WIT_INSTALL_ZIP_FILE
#
#   WLSIMG_BUILDER Image builder binary.
#                  default:  'docker'


# Initialize globals

export ORACLE_HOME=${ORACLE_HOME:-/u01/oracle}

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
WDT_MODEL_FILE=${WDT_MODEL_FILE:-"$SCRIPTPATH/wdt_model.yaml"}
WDT_VAR_FILE=${WDT_VAR_FILE:-"$SCRIPTPATH/create-domain-inputs.yaml"}

WDT_DIR=${WDT_DIR:-/shared/wdt}
WDT_VERSION=${WDT_VERSION:-LATEST}

WIT_DIR=${WIT_DIR:-/shared/imagetool}
WIT_VERSION=${WIT_VERSION:-LATEST}

DOMAIN_TYPE="${DOMAIN_TYPE:-WLS}"

download() {
  local fileUrl="${1}"
  local zipFile="${2}"

  local curl_res=1
  max=20
  count=0
  while [ $curl_res -ne 0 -a $count -lt $max ] ; do
    sleep 1
    count=`expr $count + 1`
    for proxy in "${https_proxy}" "${https_proxy2}"; do
	  echo @@ "Info:  Downloading $fileUrl with https_proxy=\"$proxy\""
	  https_proxy="${proxy}" \
	    curl --silent --show-error --connect-timeout 10 -fL $fileUrl -o $zipFile
	  curl_res=$?
	  [ $curl_res -eq 0 ] && break
	done
  done
  if [ $curl_res -ne 0 ]; then
    echo @@ "Error: Download failed."
    return 1
  fi
}

run_wdt() {
  #
  # Run WDT using WDT_VAR_FILE, WDT_MODEL_FILE, and ORACLE_HOME.  
  # Output:
  # - result domain will be in DOMAIN_HOME_DIR
  # - logging output is in $WDT_DIR/createDomain.sh.out and $WDT_DIR/weblogic-deploy/logs
  # - WDT_VAR_FILE & WDT_MODEL_FILE will be copied to WDT_DIR.
  #

  local action="${1}"

  # Input files and directories.

  local inputs_orig="$WDT_VAR_FILE"
  local model_orig="$WDT_MODEL_FILE"
  local oracle_home="$ORACLE_HOME"
  local domain_type="$DOMAIN_TYPE"
  local wdt_bin_dir="$WDT_DIR/weblogic-deploy/bin"
  local wdt_createDomain_script="$wdt_bin_dir/createDomain.sh"

  if [ ${action} = "create" ]; then
    local wdt_domain_script="$wdt_bin_dir/createDomain.sh"
  else
    local wdt_domain_script="$wdt_bin_dir/updateDomain.sh"
  fi

  local domain_home_dir="$DOMAIN_HOME_DIR"
  if [ -z "${domain_home_dir}" ]; then
    local domain_dir="/shared/domains"
    local domain_uid=`grep -E 'domainUID' $inputs_orig | awk '{print $2}'`
    local domain_home_dir=$domain_dir/$domain_uid
  fi 

  mkdir -p $domain_home_dir

  # Output files and directories.

  local inputs_final=$WDT_DIR/$(basename "$inputs_orig")
  local model_final=$WDT_DIR/$(basename "$model_orig")
  if [ ${action} = "create" ]; then
    local out_file=$WDT_DIR/createDomain.sh.out
  else
    local out_file=$WDT_DIR/updateDomain.sh.out
  fi
  local wdt_log_dir="$WDT_DIR/weblogic-deploy/logs"

  echo @@ "Info:  About to run WDT ${wdt_domain_script}"

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

  cmd="
  $wdt_domain_script
     -oracle_home $oracle_home
     -domain_type $domain_type
     -domain_home $domain_home_dir
     -model_file $model_final
     -variable_file $inputs_final
  "

  echo @@ "Info: About to run the following WDT command:"
  echo "${cmd}"
  echo @@ "Info: WDT output will be in $out_file and $wdt_log_dir"
  eval $cmd > $out_file 2>&1
  local wdt_res=$?

  cd $save_dir

  if [ $wdt_res -ne 0 ]; then
    if [ ${action} = "create" ]; then
      cat $WDT_DIR/createDomain.sh.out
      echo @@ "Info:  WDT createDomain.sh output is in $out_file and $wdt_log_dir"
      echo @@ "Error:  WDT createDomain.sh failed."
      return 1
    else
      cat $WDT_DIR/updateDomain.sh.out
      echo @@ "Info:  WDT updateDomain.sh output is in $out_file and $wdt_log_dir"
      echo @@ "Error:  WDT updateDomain.sh failed."
      return 1
    fi
  fi

  cd $WDT_DIR || return 1

  mkdir ${action}

  cmd="
  $wdt_bin_dir/extractDomainResource.sh
     -oracle_home $oracle_home
     -output_dir ./${action}
     -domain_home $domain_home_dir
     -model_file $model_final
     -variable_file $inputs_final
     -target wko4
  "
  echo @@ "Info: About to run the following WDT command:"
  echo "${cmd}"
  echo @@ "Info: WDT output will be in extract${action}.out and $wdt_log_dir"
  eval $cmd > extract${action}.out 2>&1
  local wdt_res=$?

  cd $save_dir

  if [ $wdt_res -ne 0 ]; then
    cat $WDT_DIR/extract${action}.out
    echo @@ "Info:  WDT extractDomainResource output is in extract${action}.out and $wdt_log_dir"
    echo @@ "Error:  WDT createDomain.sh failed."
    return 1
  fi

  if [ ${action} = "create" ]; then
    # chmod -R g+w $domain_home_dir || return 1
    echo @@ "Info:  WDT createDomain.sh succeeded."
  else
    echo @@ "Info:  WDT updateDomain.sh succeeded."
  fi

  return 0
}

setup_wdt_shared_dir() {
  mkdir -p $WDT_DIR || return 1
}

#
# Install Weblogic Server Deploy Tooling to ${WDT_DIR}
#
install_wdt() {

  WDT_INSTALL_ZIP_FILE="${WDT_INSTALL_ZIP_FILE:-weblogic-deploy.zip}"

  echo @@ " Info: WDT_INSTALL_ZIP_URL is '$WDT_INSTALL_ZIP_URL'"
  if [ -z ${WDT_INSTALL_ZIP_URL} ]; then
    echo @@ "WDT_INSTALL_ZIP_URL is not set"
    if [ "$WDT_VERSION" == "LATEST" ]; then
      WDT_INSTALL_ZIP_URL=${WDT_INSTALL_ZIP_URL:-"https://github.com/oracle/weblogic-deploy-tooling/releases/latest/download/$WDT_INSTALL_ZIP_FILE"}
    else
      WDT_INSTALL_ZIP_URL=${WDT_INSTALL_ZIP_URL:-"https://github.com/oracle/weblogic-deploy-tooling/releases/download/release-$WDT_VERSION/$WDT_INSTALL_ZIP_FILE"}
    fi
  fi

  local save_dir=`pwd`
  cd $WDT_DIR || return 1

  echo @@ "Info:  Downloading $WDT_INSTALL_ZIP_URL "
  download $WDT_INSTALL_ZIP_URL  $WDT_INSTALL_ZIP_FILE || return 1

  if [ ! -f $WDT_INSTALL_ZIP_FILE ]; then
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

#
# Install WebLogic Image Tool to ${WIT_DIR}. Used by install_wit_if_needed.
# Do not call this function directory.
#
install_wit() {

  WIT_INSTALL_ZIP_FILE="${WIT_INSTALL_ZIP_FILE:-imagetool.zip}"

  echo @@ " Info: WIT_INSTALL_ZIP_URL is '$WIT_INSTALL_ZIP_URL'"
  if [ -z ${WIT_INSTALL_ZIP_URL} ]; then
    echo @@ "WIT_INSTALL_ZIP_URL is not set"
    if [ "$WIT_VERSION" == "LATEST" ]; then
      WIT_INSTALL_ZIP_URL=${WDT_INSTALL_ZIP_URL:-"https://github.com/oracle/weblogic-image-tool/releases/latest/download/$WIT_INSTALL_ZIP_FILE"}
    else
      WIT_INSTALL_ZIP_URL=${WIT_INSTALL_ZIP_URL:-"https://github.com/oracle/weblogic-image-tool/releases/download/release-$WIT_VERSION/$WIT_INSTALL_ZIP_FILE"}
    fi
  fi

  local save_dir=`pwd`

  echo @@ "imagetool.sh not found in ${imagetoolBinDir}. Installing imagetool..."

  echo @@ "Info:  Downloading $WIT_INSTALL_ZIP_URL "
  download $WIT_INSTALL_ZIP_URL $WIT_INSTALL_ZIP_FILE || return 1

  if [ ! -f $WIT_INSTALL_ZIP_FILE ]; then
    cd $save_dir
    echo @@ "Error: Download failed or $WIT_INSTALL_ZIP_FILE not found."
    return 1
  fi
  echo @@ "Info: Archive downloaded to $WIT_DIR/$WIT_INSTALL_ZIP_FILE, about to unzip via 'jar xf'."

  jar xf $WIT_INSTALL_ZIP_FILE
  local jar_res=$?

  cd $save_dir

  if [ $jar_res -ne 0 ]; then
    echo @@ "Error: Install failed while unzipping $WIT_DIR/$WIT_INSTALL_ZIP_FILE"
    return $jar_res
  fi

  if [ ! -d "$WIT_DIR/imagetool/bin" ]; then
    echo @@ "Error: Install failed: directory '$WIT_DIR/imagetool/bin' not found."
    return 1
  fi

  chmod 775 $WIT_DIR/imagetool/bin/* || return 1
}

#
# Checks whether WebLogic Image Tool is already installed under ${WIT_DIR}, and install
# it if not.
#
install_wit_if_needed() {

  local save_dir=`pwd`

  mkdir -p $WIT_DIR || return 1
  cd $WIT_DIR || return 1

  imagetoolBinDir=$WIT_DIR/imagetool/bin
  if [ -f $imagetoolBinDir/imagetool.sh ]; then
    echo @@ "Info: imagetool.sh already exist in ${imagetoolBinDir}. Skipping WIT installation."
  else
    install_wit
  fi

  export WLSIMG_CACHEDIR="$WIT_DIR/imagetool-cache"

  # Check existing imageTool cache entry for WDT:
  # - if there is already an entry, and the WDT installer file specified in the cache entry exists, skip WDT installation
  # - if file in cache entry doesn't exist, delete cache entry, install WDT, and add WDT installer to cache
  # - if entry does not exist, install WDT, and add WDT installer to cache
  if [ "$WDT_VERSION" == "LATEST" ]; then
    wdtCacheVersion="latest"
  else
    wdtCacheVersion=$WDT_VERSION
  fi

  local listItems=$( ${imagetoolBinDir}/imagetool.sh cache listItems | grep "wdt_${wdtCacheVersion}" )

  if [ ! -z "$listItems" ]; then
    local wdt_file_path_in_cache=$(echo $listItems | sed 's/.*=\(.*\)/\1/')
    if [ -f "$wdt_file_path_in_cache" ]; then
      skip_wdt_install=true
    else
      echo @@ "Info: imageTool cache contains an entry for WDT zip at $wdt_file_path_in_cache which does not exist. Removing from cache entry."
      ${imagetoolBinDir}/imagetool.sh cache deleteEntry \
         --key wdt_${wdtCacheVersion}
    fi
  fi

  if [ -z "$skip_wdt_install" ]; then
    echo @@ "Info: imageTool cache does not contain a valid entry for wdt_${wdtCacheVersion}. Installing WDT"
    setup_wdt_shared_dir || return 1
    install_wdt || return 1
    ${imagetoolBinDir}/imagetool.sh cache addInstaller \
      --type wdt \
      --version $WDT_VERSION \
      --path $WDT_DIR/$WDT_INSTALL_ZIP_FILE  || return 1
  else
    echo @@ "Info: imageTool cache already contains entry ${listItems}. Skipping WDT installation."
  fi

  cd $save_dir

  echo @@ "Info: Install succeeded, imagetool install is in the $WIT_DIR/imagetool directory."
  return 0
}

encrypt_model() {
  #
  # run encryptModel.sh from WDT to encrypt model and properties files
  #
  local domainOutputDirFullPath=${1} # full path to directory where the model, encrypt file, and domain properties files are
  local model_file=${2} # path to file containing encryption key relative to ${domainOutputDirFullPath}
  local encrypt_key_file=${3} # path to file containing encryption key relative to ${domainOutputDirFullPath}
  local domain_properties_file=${4} # path to domain properties file relative to ${domainOutputDirFullPath}
  local oracle_home="$ORACLE_HOME"

  echo @@ "Info: encrypt passwords in the variables file at ${domainOutputDirFullPath}/${domain_properties_file} using encryption key from create-domain.sh argument written to file: ${encrypt_key_file}"

  cmd="
    cat /shared/${encrypt_key_file} /shared/${encrypt_key_file} |
    /wdt/bin/encryptModel.sh \
    -oracle_home ${oracle_home} \
    -model_file /shared/${model_file} \
    -variable_file /shared/${domain_properties_file}
  "
  echo $cmd > ${domainOutputDirFullPath}/cmd.sh
  chmod 755 ${domainOutputDirFullPath}/cmd.sh
  echo @@ "Info: Encrypt Model: About to run the following command in container with image ${domainHomeImageBase}:"
  cat ${domainOutputDirFullPath}/cmd.sh

  chmod 766 ${domainOutputDirFullPath}/${domain_properties_file}
  ${WLSIMG_BUILDER:-docker} run -it --rm -v ${domainOutputDirFullPath}:/shared -v ${WDT_DIR}/weblogic-deploy:/wdt ${domainHomeImageBase} /bin/bash -c /shared/cmd.sh || return 1

  # clean up the generated files
  rm ${domainOutputDirFullPath}/cmd.sh

  echo @@ "Info: encrypt_model Completed"
}


