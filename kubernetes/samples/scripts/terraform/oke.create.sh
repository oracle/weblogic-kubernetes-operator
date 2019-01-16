#!/bin/bash
# Copyright 2017, Oracle Corporation and/or its affiliates. All rights reserved.


function prop {
    grep "${1}" ${propsFile}|cut -d'=' -f2
}
function generateTFVarFile {

tfVarsFiletfVarsFile=${terraformVarDir}/${clusterTFVarsFile}.tfvars
rm -f ${tfVarsFiletfVarsFile}
cp ${terraformVarDir}/template.tfvars $tfVarsFiletfVarsFile
chmod 777 ${terraformVarDir}/template.tfvars $tfVarsFiletfVarsFile

sed -i -e "s:@TENANCYOCID@:${tenancy_ocid}:g" ${tfVarsFiletfVarsFile}
sed -i -e "s:@USEROCID@:${user_ocid}:g" ${tfVarsFiletfVarsFile}
sed -i -e "s:@COMPARTMENTOCID@:${compartment_ocid}:g" ${tfVarsFiletfVarsFile}
sed -i -e "s:@COMPARTMENTNAME@:${compartment_name}:g" ${tfVarsFiletfVarsFile}
sed -i -e "s:@OKECLUSTERNAME@:${okeclustername}:g" ${tfVarsFiletfVarsFile}
sed -i -e "s:@OCIAPIPUBKEYFINGERPRINT@:"${ociapi_pubkey_fingerprint}":g" ${tfVarsFiletfVarsFile}
sed -i -e "s:@OCIPRIVATEKEYPATH@:${ocipk_path}:g" ${tfVarsFiletfVarsFile}
sed -i -e "s:@VCNCIDRPREFIX@:${vcn_cidr_prefix}:g" ${tfVarsFiletfVarsFile}
sed -i -e "s:@VCNCIDR@:${vcn_cidr_prefix}.0.0/16:g" ${tfVarsFiletfVarsFile}
sed -i -e "s:@OKEK8SVERSION@:${k8s_version}:g" ${tfVarsFiletfVarsFile}
sed -i -e "s:@NODEPOOLSHAPE@:${nodepool_shape}:g" ${tfVarsFiletfVarsFile}
sed -i -e "s:@NODEPOOLSSHPUBKEY@:${nodepool_ssh_pubkey}:g" ${tfVarsFiletfVarsFile}
echo "Generated TFVars file [${tfVarsFiletfVarsFile}]"

}

function setupTerraform () {
mkdir ${terraformDir}
cd ${terraformDir}
if [[ "${OSTYPE}" == "darwin"* ]]; then
  curl -O https://releases.hashicorp.com/terraform/0.11.10/terraform_0.11.10_darwin_amd64.zip
  unzip terraform_0.11.10_darwin_amd64.zip
elif [[ "${OSTYPE}" == "linux"* ]]; then
   curl -O https://releases.hashicorp.com/terraform/0.11.8/terraform_0.11.8_linux_amd64.zip
   unzip terraform_0.11.8_linux_amd64.zip
else
   echo "Unsupported OS"
fi
chmod 777 ${terraformDir}/terraform
export PATH=${PATH}:${terraformDir}

}

function buildTerraformOCIProvider() {
mkdir -p ${goDir}/go/src/github.com/terraform-providers; cd ${goDir}/go/src/github.com/terraform-providers
git clone https://github.com/terraform-providers/terraform-provider-oci.git
cd ${goDir}/go/src/github.com/terraform-providers/terraform-provider-oci
#somehow it does not build first time need to run gofmt and rebuild
make gofmt
make fmt
make build
if ! [ -d ~/.terraform.d ]; then
echo "Creating terraform plugins dir"
mkdir ~/.terraform.d
fi
if ! [ -d ~/.terraform.d/plugins ]; then
mkdir ~/.terraform.d/plugins
fi
cp ${goDir}/go/bin/terraform-provider-oci ~/.terraform.d/plugins/
if [ -e ~/.terraformrc ]; then
  rm ~/.terraformrc
fi
cat ${terraformVarDir}/terraformrc >> ~/.terraformrc
}

function createCluster () {
cd ${terraformVarDir}
echo "terraform init -var-file=${terraformVarDir}/${clusterTFVarsFile}.tfvars"
terraform init -var-file=${terraformVarDir}/${clusterTFVarsFile}.tfvars
terraform plan -var-file=${terraformVarDir}/${clusterTFVarsFile}.tfvars
terraform apply -auto-approve -var-file=${terraformVarDir}/${clusterTFVarsFile}.tfvars
}

function setupGo () {
mkdir ${goDir}
cd ${goDir}
if [[ "${OSTYPE}" == "darwin"* ]]; then
  curl -O https://dl.google.com/go/go1.11.2.darwin-amd64.tar.gz
  tar -xvf go1.11.2.darwin-amd64.tar.gz
elif [[ "${OSTYPE}" == "linux"* ]]; then
   curl -O https://dl.google.com/go/go1.11.linux-amd64.tar.gz
   tar -xvf go1.11.linux-amd64.tar.gz
else
   echo "Unsupported OS"
fi
chmod 777 ${goDir}/go/bin
export PATH=${PATH}:${goDir}/go/bin

}



#MAIN 
terraformVarDir=${2:-$PWD}
propsFile=${1:-$PWD/oci.props}


clusterTFVarsFile=$(prop 'tfvars.filename')
tenancy_ocid=$(prop 'tenancy.ocid')
user_ocid=$(prop 'user.ocid')
compartment_ocid=$(prop 'compartment.ocid')
compartment_name=$(prop 'compartment.name')
okeclustername=$(prop 'okeclustername')
ociapi_pubkey_fingerprint=$(prop 'ociapi.pubkey.fingerprint')
ocipk_path=$(prop 'ocipk.path')
vcn_cidr_prefix=$(prop 'vcn.cidr.prefix')
k8s_version=$(prop 'k8s.version')
nodepool_shape=$(prop 'nodepool.shape')
nodepool_ssh_pubkey=$(prop 'nodepool.ssh.pubkey')
terraformDir=$(prop 'terraform.installdir')
goDir=$(prop 'go.installdir')
generateTFVarFile
rm -rf ${goDir} ${terraformDir}
setupTerraform
setupGo
buildTerraformOCIProvider
chmod 600 ${ocipk_path}
createCluster
export KUBECONFIG=${terraformVarDir}/${okeclustername}_kubeconfig



