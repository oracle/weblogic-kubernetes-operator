#!/bin/bash
# Copyright (c) 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

prop() {
    grep "${1}" ${propsFile}| grep -v "#" | cut -d'=' -f2
}


generateTFVarFile() {
    tfVarsFiletfVarsFile=${terraformVarDir}/${clusterTFVarsFile}.tfvars
    rm -f ${tfVarsFiletfVarsFile}
    cp ${terraformVarDir}/template.tfvars $tfVarsFiletfVarsFile
    chmod 777 ${terraformVarDir}/template.tfvars $tfVarsFiletfVarsFile
    sed -i -e "s:@TENANCYOCID@:${tenancy_ocid}:g" ${tfVarsFiletfVarsFile}
    sed -i -e "s:@USEROCID@:${user_ocid}:g" ${tfVarsFiletfVarsFile}
    sed -i -e "s:@COMPOCID@:${compartment_ocid}:g" ${tfVarsFiletfVarsFile}
    sed -i -e "s:@SUBCOMPARTMENTOCID@:${sub_compartment_ocid}:g" ${tfVarsFiletfVarsFile}
    sed -i -e "s:@COMPARTMENTNAME@:${compartment_name}:g" ${tfVarsFiletfVarsFile}
    sed -i -e "s:@OKECLUSTERNAME@:${okeclustername}:g" ${tfVarsFiletfVarsFile}
    sed -i -e "s/@OCIAPIPUBKEYFINGERPRINT@/"${ociapi_pubkey_fingerprint}"/g" ${tfVarsFiletfVarsFile}
    sed -i -e "s:@OCIPRIVATEKEYPATH@:${ocipk_path}:g" ${tfVarsFiletfVarsFile}
    sed -i -e "s:@VCNCIDRPREFIX@:${vcn_cidr_prefix}:g" ${tfVarsFiletfVarsFile}
    sed -i -e "s:@VCNOCID@:${vcn_ocid}:g" ${tfVarsFiletfVarsFile}
    sed -i -e "s:@PUBSUBNETOCID@:${pub_subnet_ocid}:g" ${tfVarsFiletfVarsFile}
    sed -i -e "s:@PRIVATESUBNETOCID@:${private_subnet_ocid}:g" ${tfVarsFiletfVarsFile}
    sed -i -e "s:@VCNCIDR@:${vcn_cidr_prefix}.0.0/16:g" ${tfVarsFiletfVarsFile}
    sed -i -e "s:@OKEK8SVERSION@:${k8s_version}:g" ${tfVarsFiletfVarsFile}
    sed -i -e "s:@NODEPOOLSHAPE@:${nodepool_shape}:g" ${tfVarsFiletfVarsFile}
    sed -i -e "s:@NODEPOOLIMAGENAME@:${nodepool_imagename}:g" ${tfVarsFiletfVarsFile}
    sed -i -e "s:@NODEPOOLSSHPUBKEY@:${nodepool_ssh_pubkeypath}:g" ${tfVarsFiletfVarsFile}
    sed -i -e "s:@NODEPOOLSSHPK@:${nodepool_ssh_pkpath}:g" ${tfVarsFiletfVarsFile}
    sed -i -e "s:@MOUNTTARGETOCID@:${mount_target_ocid}:g" ${tfVarsFiletfVarsFile}
    sed -i -e "s:@REGION@:${region}:g" ${tfVarsFiletfVarsFile}
    sed -i -e "s:@REGIONSHORT@:${region_short}:g" ${tfVarsFiletfVarsFile}
    echo "Generated TFVars file [${tfVarsFiletfVarsFile}]"
}

setupTerraform() {
    mkdir ${terraformDir}
    cd ${terraformDir}
    if [[ "${OSTYPE}" == "darwin"* ]]; then
      os_type="darwin"
    elif [[ "${OSTYPE}" == "linux"* ]]; then
       os_type="linux"
    else
       echo "Unsupported OS"
    fi
    curl -O https://releases.hashicorp.com/terraform/1.8.1/terraform_1.8.1_${os_type}_${platform}64.zip
    unzip terraform_1.8.1_${os_type}_${platform}64.zip
    chmod +x ${terraformDir}/terraform
    export PATH=${terraformDir}:${PATH}
}

deleteOlderVersionTerraformOCIProvider() {
    if [ -d ~/.terraform.d/plugins ]; then
        echo "Deleting older version of terraform plugins dir"
        rm -rf ~/.terraform.d/plugins
    fi
    if [ -d ${terraformVarDir}/.terraform ]; then
        rm -rf ${terraformVarDir}/.terraform
    fi
    if [ -e ~/.terraformrc ]; then
      rm ~/.terraformrc
    fi
}

createCluster () {
    cd ${terraformVarDir}
    echo "terraform init -var-file=${terraformVarDir}/${clusterTFVarsFile}.tfvars"
    terraform init -var-file=${terraformVarDir}/${clusterTFVarsFile}.tfvars > /dev/null
    echo "terraform plan -var-file=${terraformVarDir}/${clusterTFVarsFile}.tfvars"
    terraform plan -var-file=${terraformVarDir}/${clusterTFVarsFile}.tfvars > /dev/null
    echo "terraform apply -var-file=${terraformVarDir}/${clusterTFVarsFile}.tfvars"
    terraform apply -auto-approve -var-file=${terraformVarDir}/${clusterTFVarsFile}.tfvars
}

createRoleBindings () {
    ${KUBERNETES_CLI:-kubectl} -n kube-system create serviceaccount $okeclustername-sa
    ${KUBERNETES_CLI:-kubectl} create clusterrolebinding add-on-cluster-admin --clusterrole=cluster-admin --serviceaccount=kube-system:$okeclustername-sa
    TOKENNAME=`${KUBERNETES_CLI:-kubectl} -n kube-system get serviceaccount/$okeclustername-sa -o jsonpath='{.secrets[0].name}'`
    TOKEN=`${KUBERNETES_CLI:-kubectl} -n kube-system get secret $TOKENNAME -o jsonpath='{.data.token}'| base64 --decode`
    ${KUBERNETES_CLI:-kubectl} config set-credentials $okeclustername-sa --token=$TOKEN
    ${KUBERNETES_CLI:-kubectl} config set-context --current --user=$okeclustername-sa
}
checkKubernetesCliConnection() {
    echo "Confirming ${KUBERNETES_CLI:-kubectl} can connect to the server..."

    # Get the cluster public IP
    clusterPublicIP=$(oci ce cluster list --compartment-id="${compartment_ocid}" | jq -r '.data[] | select(."name" == "'"${okeclustername}"'" and (."lifecycle-state" == "ACTIVE")) | ."endpoints" | ."public-endpoint" | split(":")[0]')

    # Check if clusterPublicIP is empty or not
    if [ -z "$clusterPublicIP" ]; then
        echo "[ERROR] No active cluster found with name ${okeclustername}."
        exit 1
    fi
    echo "clusterPublicIP: ###$clusterPublicIP###"
    echo " NO_PROXY=#$NO_PROXY# "
    export NO_PROXY=$NO_PROXY,localhost,127.0.0.1,$clusterPublicIP
    echo "export NO_PROXY=:$NO_PROXY"

    # Maximum number of retries
    max_retries=10

    # Initial retry count
    retry_count=0

    # Command to get cluster info
    while [[ $retry_count -lt $max_retries ]]; do
      echo "Attempt $((retry_count+1)) of $max_retries to connect to Kubernetes cluster..."

      # Try to execute kubectl cluster-info
      ${KUBERNETES_CLI:-kubectl} cluster-info
      if [[ $? -eq 0 ]]; then
        echo "Connected to Kubernetes cluster successfully!"
        break
      else
        echo "Connection refused or failed, retrying..."
        retry_count=$((retry_count + 1))
        sleep 5  # Wait 5 seconds before retrying
      fi
    done

    # Check if retries were exhausted
    if [[ $retry_count -eq $max_retries ]]; then
      echo "Failed to connect to Kubernetes cluster after $max_retries attempts."
      cd "${terraformVarDir}"
      terraform destroy -auto-approve -var-file="${terraformVarDir}/${clusterTFVarsFile}.tfvars" > /dev/null
      createCluster
    fi

    local myline_output=$(${KUBERNETES_CLI:-kubectl} get nodes -o wide 2>&1)

    if echo "$myline_output" | grep -q "Unable to connect to the server: net/http: TLS handshake timeout"; then
        echo "[ERROR] Unable to connect to the server: net/http: TLS handshake timeout"
        echo '- could not talk to OKE cluster, aborting'
        unset http_proxy
        unset https_proxy
        myline_output=$(${KUBERNETES_CLI:-kubectl} get nodes -o wide 2>&1)
        if echo "$myline_output" | grep -q "Unable to connect to the server: net/http: TLS handshake timeout"; then
          cd "${terraformVarDir}"
          terraform destroy -auto-approve -var-file="${terraformVarDir}/${clusterTFVarsFile}.tfvars" > /dev/null
          exit 1
        fi
    fi
    if echo "$myline_output" | grep -q "couldn't get current server API group"; then
            echo "[ERROR] Unable to connect to the server: couldn't get current server API group, connection refused"
            echo '- check errors during OKE cluster creation'
            echo '- could not talk to OKE cluster, aborting'

            cd "${terraformVarDir}"
            terraform destroy -auto-approve -var-file="${terraformVarDir}/${clusterTFVarsFile}.tfvars" > /dev/null
            exit 1
    fi

}

checkClusterRunning() {
    kubeconfig_file=${terraformVarDir}/${okeclustername}_kubeconfig
    export KUBECONFIG=${terraformVarDir}/${okeclustername}_kubeconfig
    echo "Kubeconfig file : $KUBECONFIG"
    ls -al $KUBECONFIG

    if [ -f "$kubeconfig_file" ] && [ -s "$kubeconfig_file" ]; then
      echo "Kubeconfig file exists and is not empty."
    else
      if [ ! -f "$kubeconfig_file" ]; then
        echo "Kubeconfig file does not exist."
        cd "${terraformVarDir}"
        terraform destroy -auto-approve -var-file="${terraformVarDir}/${clusterTFVarsFile}.tfvars" > /dev/null
        createCluster
      else
        echo "Kubeconfig file exists but is empty."
        cd "${terraformVarDir}"
        terraform destroy -auto-approve -var-file="${terraformVarDir}/${clusterTFVarsFile}.tfvars" > /dev/null
        createCluster
      fi
    fi
    checkKubernetesCliConnection

    local privateIP=${vcn_cidr_prefix}
    declare -a myline=($(${KUBERNETES_CLI:-kubectl} get nodes -o wide | grep "${privateIP}" | awk '{print $2}'))
    local NODE_IP=$(${KUBERNETES_CLI:-kubectl} get nodes -o wide | grep "${privateIP}" | awk '{print $7}')

    local status=${myline[0]}
    local max=100
    local count=1

    for i in {0..1}; do
        while [ "${myline[i]}" != "Ready" ] && [ $count -le $max ]; do
            echo "[ERROR] Some Nodes in the Cluster are not in the Ready Status, sleeping for 10s..."
            sleep 10
            myline=($(${KUBERNETES_CLI:-kubectl} get nodes -o wide | grep "${privateIP}" | awk '{print $2}'))
            NODE_IP=$(${KUBERNETES_CLI:-kubectl} get nodes -o wide | grep "${privateIP}" | awk '{print $7}')
            echo "myline[i]: ${myline[i]}"
            echo "Status is ${myline[i]} Iter [$count/$max]"
            count=$((count + 1))
        done
    done

    local NODES=$(${KUBERNETES_CLI:-kubectl} get nodes -o wide | grep "${privateIP}" | wc -l)

    if [ "$NODES" -eq 2 ]; then
        echo '- looks good'
    else
        echo '- could not talk to OKE cluster, aborting'
        cd "${terraformVarDir}"
        terraform destroy -auto-approve -var-file="${terraformVarDir}/${clusterTFVarsFile}.tfvars" > /dev/null
        exit 1
    fi

    if [ $count -gt $max ]; then
        echo "[ERROR] Unable to start the nodes in the OKE cluster after 200s"
        cd "${terraformVarDir}"
        terraform destroy -auto-approve -var-file="${terraformVarDir}/${clusterTFVarsFile}.tfvars" > /dev/null
        exit 1
    fi
}


#MAIN
propsFile=${1:-$PWD/oci.props}
terraformVarDir=${2:-$PWD}
platform=${3:-amd}

#grep props's values from oci.props file

clusterTFVarsFile=$(prop 'tfvars.filename')
tenancy_ocid=$(prop 'tenancy.ocid')
user_ocid=$(prop 'user.ocid')
compartment_ocid=$(prop 'compartment.ocid')
sub_compartment_ocid=$(prop 'sub.comp.ocid')
compartment_name=$(prop 'compartment.name')
okeclustername=$(prop 'okeclustername')
ociapi_pubkey_fingerprint=$(prop 'ociapi.pubkey.fingerprint')
ocipk_path=$(prop 'ocipk.path')
vcn_cidr_prefix=$(prop 'vcn.cidr.prefix')
vcn_ocid=$(prop 'vcn.ocid')
pub_subnet_ocid=$(prop 'pub.subnet.ocid')
private_subnet_ocid=$(prop 'private.subnet.ocid')
k8s_version=$(prop 'k8s.version')
nodepool_shape=$(prop 'nodepool.shape')
nodepool_imagename=$(prop 'nodepool.imagename')
nodepool_ssh_pubkeypath=$(prop 'nodepool.ssh.pubkeypath')
nodepool_ssh_pkpath=$(prop 'nodepool.ssh.pkpath')
region=$(prop 'region')
terraformDir=$(prop 'terraform.installdir')
mount_target_ocid=$(prop 'mounttarget.ocid')
region_short=$(echo "$region" | sed 's/.*-\([a-z]*\)-.*/\1/')

# generate terraform configuration file with name $(clusterTFVarsFile).tfvar
#generateTFVarFile
generateTFVarFile

# cleanup previously installed terraform binaries
rm -rf ${terraformDir}

# download terraform binaries into ${terraformDir}
setupTerraform

# clean previous versions of terraform oci provider
deleteOlderVersionTerraformOCIProvider

chmod 600 ${ocipk_path}
sudo yum reinstall ca-certificates -y
sudo iptables -A OUTPUT -p tcp --dport 6443 -j ACCEPT
export TF_LOG=ERROR

# run terraform init,plan,apply to create OKE cluster based on the provided tfvar file ${clusterTFVarsFile).tfvar
createCluster
#check status of OKE cluster nodes, destroy if can not access them
export KUBECONFIG=${terraformVarDir}/${okeclustername}_kubeconfig


#export okeclustername=\"${okeclustername}\"


#echo " oci ce cluster list --compartment-id=${compartment_ocid} | jq '.data[]  | select(."name" == '"${okeclustername}"' and (."lifecycle-state" == "ACTIVE"))' | jq ' ."endpoints" | ."public-endpoint"'"

#clusterIP=$(oci ce cluster list --compartment-id=${compartment_ocid} | jq '.data[]  | select(."name" == '"${okeclustername}"' and (."lifecycle-state" == "ACTIVE"))' | jq ' ."endpoints" | ."public-endpoint"')
#echo "clusterIp : $clusterIP"
#clusterPublicIP=${clusterIP:1:-6}
#echo " clusterPublicIP : ${clusterPublicIP}"
#echo "NO_PROXY before : ${NO_PROXY}"
#export NO_PROXY=${clusterPublicIP}
#echo "NO_PROXY:" $NO_PROXY

checkClusterRunning
echo "${okeclustername} is up and running"
