#!/usr/bin/env bash
# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# Description
#  This sample script creates a WebLogic domain home in docker image, and generates the domain resource
#  yaml file, which can be used to restart the Kubernetes artifacts of the corresponding domain.
#
#  The domain creation inputs can be customized by editing create-domain-inputs.yaml
#
#  The following pre-requisites must be handled prior to running this script:
#    * The WDT sample requires that JAVA_HOME is set to a java JDK version 1.8 or greater
#    * The kubernetes namespace must already be created
#    * The kubernetes secrets 'username' and 'password' of the admin account have been created in the namespace
#    * The host directory that will be used as the persistent volume must already exist
#      and have the appropriate file permissions set.
#    * If logHomeOnPV is enabled, the kubernetes persistent volume must already be created
#    * If logHomeOnPV is enabled, the kubernetes persistent volume claim must already be created
#

# Initialize
script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/../../common/utility.sh
source ${scriptDir}/../../common/wdt-and-wit-utility.sh
source ${scriptDir}/../../common/validate.sh

function usage {
  echo usage: ${script} -o dir -i file -u username -p password [-q rcuSchemaPassword] [-b buildNetworkParam] [-n encryption-key] [-e] [-v] [-h]
  echo "  -i Parameter inputs file, must be specified."
  echo "  -o Output directory for the generated properties and YAML files, must be specified."
  echo "  -u WebLogic administrator user name for the WebLogic domain."
  echo "  -p WebLogic administrator password for the WebLogic domain."
  echo "  -q Password for the RCU schema. Required for JRF FMW domain type."
  echo "  -e Also create the resources in the generated YAML files, optional."
  echo "  -v Validate the existence of persistentVolumeClaim, optional."
  echo "  -n Encryption key for encrypting passwords in the WDT model and properties files, optional."
  echo "  -b Value to be used in the buildNetwork parameter when invoking WebLogic Image Tool, optional."
  echo "  -h Help"
  exit $1
}

#
# Parse the command line options
#
doValidation=false
executeIt=false
while getopts "evhi:o:u:p:n:b:q:" opt; do
  case $opt in
    i) valuesInputFile="${OPTARG}"
    ;;
    o) outputDir="${OPTARG}"
    ;;
    v) doValidation=true
    ;;
    e) executeIt=true
    ;;
    u) username="${OPTARG}"
    ;;
    p) password="${OPTARG}"
    ;;
    q) rcuSchemaPassword="${OPTARG}"
    ;;
    n) wdtEncryptKey="${OPTARG}"
    ;;
    b) buildNetwork="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

if [ -z ${valuesInputFile} ]; then
  echo "${script}: -i must be specified."
  missingRequiredOption="true"
fi

if [ -z ${username} ]; then
  echo "${script}: -u must be specified."
  missingRequiredOption="true"
fi

if [ -z ${password} ]; then
  echo "${script}: -p must be specified."
  missingRequiredOption="true"
fi

if [ -z ${outputDir} ]; then
  echo "${script}: -o must be specified."
  missingRequiredOption="true"
fi

if [ "${missingRequiredOption}" == "true" ]; then
  usage 1
fi

#
# Function to initialize and validate the output directory
# for the generated properties and yaml files for this domain.
#
function initOutputDir {
  domainOutputDir="${outputDir}/weblogic-domains/${domainUID}"
  # Create a directory for this domain's output files
  mkdir -p ${domainOutputDir}

  removeFileIfExists ${domainOutputDir}/${valuesInputFile}
  removeFileIfExists ${domainOutputDir}/create-domain-inputs.yaml
  removeFileIfExists ${domainOutputDir}/domain.properties
  removeFileIfExists ${domainOutputDir}/domain.yaml
}

# try to execute docker to see whether docker is available
function validateDockerAvailable {
  if ! [ -x "$(command -v docker)" ]; then
    validationError "docker is not installed"
  fi
}

#
# Function to setup the environment to create domain
#
function initialize {

  # Validate the required files exist
  validateErrors=false

  validateDockerAvailable
  validateKubectlAvailable

  if [ -z "${valuesInputFile}" ]; then
    validationError "You must use the -i option to specify the name of the inputs parameter file (a modified copy of kubernetes/samples/scripts/create-fmw-infrastructure-domain/domain-home-in-image/create-domain-inputs.yaml)."
  else
    if [ ! -f ${valuesInputFile} ]; then
      validationError "Unable to locate the input parameters file ${valuesInputFile}"
    fi
  fi

  if [ -z "${outputDir}" ]; then
    validationError "You must use the -o option to specify the name of an existing directory to store the generated properties and yaml files in."
  fi

  domainPropertiesInput="${scriptDir}/properties-template.properties"
  if [ ! -f ${domainPropertiesInput} ]; then
    validationError "The template file ${domainPropertiesInput} for creating a WebLogic domain was not found"
  fi

  dcrInput="${scriptDir}/../../common/jrf-domain-template.yaml"
  if [ ! -f ${dcrInput} ]; then
    validationError "The template file ${dcrInput} for creating the domain resource was not found"
  fi

  failIfValidationErrors

  validateCommonInputs "fmw-domain-home-in-image"

  mode="${mode:-wdt}"
  if [ ! "${mode}" == "wdt" ] && [ ! "${mode}" == "wlst" ]; then
    validationError "mode must be either 'wdt' or 'wlst'."
  fi

  if [ "${mode}" == "wlst" ]; then
    if [ "${fmwDomainType}" == "RestrictedJRF" ]; then
      createDomainWlstScript="${createDomainWlstScript:-../../common/createFMWRestrictedJRFDomain.py}"
    else
      createDomainWlstScript="${createDomainWlstScript:-../../common/createFMWJRFDomain.py}"
    fi
    if [ ! -f ${scriptDir}/${createDomainWlstScript} ]; then
      validationError "The create domain WLST script file ${createDomainWlstScript} was not found"
    fi
    echo @@ "Info: Using WLST script at ${createDomainWlstScript} to create a WebLogic domain home."
    if [ -n "${wdtEncryptKey}" ]; then
      echo @@ "Info: ${script}: -n is ignored for wlst mode."
    fi
  fi

  if [ "${mode}" == "wdt" ]; then
    # fmwDomainType is either JRF or RestrictedJRF. JRF does not support Dynamic Cluster Model
    if [ "${fmwDomainType}" == "RestrictedJRF" ]; then
      createDomainWdtModel="${createDomainWdtModel:-wdt/wdt_model_restricted_jrf_configured.yaml}"
      wdtDomainType=RestrictedJRF
    else
      createDomainWdtModel="${createDomainWdtModel:-wdt/wdt_model_configured.yaml}"
      wdtDomainType=JRF
    fi
    if [ ! -f ${scriptDir}/${createDomainWdtModel} ]; then
      validationError "The create domain WDT model file ${createDomainWdtModel} was not found"
    fi
    echo @@ "Info: Using WDT model YAML file at ${createDomainWdtModel} to create a WebLogic domain home."
  fi

  validateBooleanInputParamsSpecified logHomeOnPV
  failIfValidationErrors

  initOutputDir

  export WDT_DIR=${toolsDir:-"/tmp/dhii-sample/tools"}
  export WIT_DIR="${WDT_DIR}"

  export WDT_VERSION=${wdtVersion:-LATEST}
  export WIT_VERSION=${witVersion:-LATEST}

  install_wit_if_needed || exit 1
}

#
# Function to get the dependency docker sample
#
function getDockerSample {
  dockerImagesDir=${domainHomeImageBuildPath%/OracleFMWInfrastructure*}
  rm -rf ${dockerImagesDir}
  git clone https://github.com/oracle/docker-images.git ${dockerImagesDir}
}

#
# Function to build docker image and create WebLogic domain home
#
function createDomainHome {

  echo @@ "Info: WIT_DIR is ${WIT_DIR}"

  domainPropertiesOutput="${domainOutputDir}/domain.properties"

  echo @@ "Info: Invoking WebLogic Image Tool to create a WebLogic domain at '${domainHome}' from image '${domainHomeImageBase}' and tagging the resulting image as '${BUILD_IMAGE_TAG}'."

  if [ "${mode}" == "wlst" ]; then
      additionalBuildCommandsOutput="${domainOutputDir}/additional-build-commands"
      additionalBuildCommandsTemplate="wlst/additional-build-commands-template"

      # Generate the additional-build-commands file that will be used when creating the weblogic domain
      echo @@ "Info: Generating ${additionalBuildCommandsOutput} from ${additionalBuildCommandsTemplate}"

      cp ${scriptDir}/${additionalBuildCommandsTemplate} ${additionalBuildCommandsOutput} || exit 1
      sed -i -e "s:%DOMAIN_HOME%:${domainHome}:g" ${additionalBuildCommandsOutput}

      createDomainWlstScriptCopy="${domainOutputDir}/createFMWDomain.py"
      cp ${scriptDir}/${createDomainWlstScript} ${createDomainWlstScriptCopy} || exit 1

      cmd="
        $WIT_DIR/imagetool/bin/imagetool.sh update
          --fromImage \"$domainHomeImageBase\"
          --tag \"${BUILD_IMAGE_TAG}\"
          --wdtOperation CREATE
          --wdtVersion ${WDT_VERSION}
          --wdtDomainHome \"${domainHome}\"
          --additionalBuildCommands ${additionalBuildCommandsOutput}
          --additionalBuildFiles \"${scriptDir}/wlst/createFMWDomain.sh,${createDomainWlstScriptCopy},${domainPropertiesOutput}\"
          --chown=oracle:root
        "
  else # wdt
    createDomainWdtModelCopy="${domainOutputDir}/wdt_model.yaml"
    cp ${scriptDir}/${createDomainWdtModel} ${createDomainWdtModelCopy} || exit 1

    if [ -n "${wdtEncryptKey}" ]; then
      echo @@ "Info: An encryption key is provided, encrypting passwords in WDT properties file"
      wdtEncryptionKeyFile=${domainOutputDir}/wdt_encrypt_key
      echo  -e "${wdtEncryptKey}" > "${wdtEncryptionKeyFile}"
      domainOutputDirFullPath="$( cd "$( dirname "${domainPropertiesOutput}" )" && pwd)"
      encrypt_model ${domainOutputDirFullPath} wdt_model.yaml wdt_encrypt_key domain.properties || exit 1
    fi

    echo @@ "Info: dumping output of ${domainPropertiesOutput}"
    sed 's/ADMIN_USER_PASS=[^{].*/ADMIN_USER_PASS=********/g;s/RCU_SCHEMA_PASSWORD=[^{].*/RCU_SCHEMA_PASSWORD=********/g' ${domainPropertiesOutput}

    cmd="
      $WIT_DIR/imagetool/bin/imagetool.sh update
        --fromImage \"$domainHomeImageBase\"
        --tag ${BUILD_IMAGE_TAG}
        --wdtModel \"${createDomainWdtModelCopy}\"
        --wdtVariables \"${domainPropertiesOutput}\"
        --wdtOperation CREATE
        --wdtVersion ${WDT_VERSION}
        --wdtDomainType ${wdtDomainType}
        --wdtDomainHome \"${domainHome}\"
        --additionalBuildCommands ${scriptDir}/wdt/additional-build-commands
        --chown=oracle:root
    "
    if [ -n "${wdtEncryptKey}" ]; then
      cmd="$cmd  --wdtEncryptionKeyFile \"${wdtEncryptionKeyFile}\"
      "
    fi
  fi

  if [ -n "${buildNetwork}" ]; then
    cmd="$cmd    --buildNetwork ${buildNetwork}
    "
  fi

  echo @@ "Info: About to run the following WIT command:"
  echo "$cmd"
  echo
  eval $cmd

  if [ "$?" != "0" ]; then
    fail "Create domain ${domainName} failed."
  fi

  # clean up the generated domain.properties file
  rm -f ${domainPropertiesOutput} ${createDomainWlstScriptCopy} ${createDomainWdtModelCopy} ${wdtEncryptionKeyFile} ${additionalBuildCommandsOutput}

  echo ""
  echo @@ "Info: Create domain ${domainName} successfully."
}

#
# Function to output to the console a summary of the work completed
#
function printSummary {

  # Get the IP address of the kubernetes cluster (into K8S_IP)
  getKubernetesClusterIP

  echo ""
  echo @@ "Info: Domain ${domainName} was created and will be started by the WebLogic Kubernetes Operator"
  echo ""
  if [ "${exposeAdminNodePort}" = true ]; then
    echo @@ "Info: Administration console access is available at http://${K8S_IP}:${adminNodePort}/console"
  fi
  if [ "${exposeAdminT3Channel}" = true ]; then
    echo @@ "Info: T3 access is available at t3://${K8S_IP}:${t3ChannelPort}"
  fi
  echo @@ "Info: The following files were generated:"
  echo "  ${domainOutputDir}/create-domain-inputs.yaml"
  echo "  ${dcrOutput}"
  echo ""
  echo @@ "Info: Completed"
}

# Perform the sequence of steps to create a domain
createDomain true
