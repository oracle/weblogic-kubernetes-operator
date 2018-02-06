#!/bin/sh
#
# run with "sudo sh ./this_script.sh"
# 
# based off of install/configure instructions at
# main qa wiki
# http://aseng-wiki.us.oracle.com/asengwiki/display/ASQA/Installing+Kubernetes+on+Linux+with+kubeadm
# Mike Cico's notes
# http://aseng-wiki.us.oracle.com/asengwiki/pages/viewpage.action?pageId=5232561024
#

basedir=$( cd $(dirname $0) ; pwd -P )

warning=`cat << EOF
 WARNING:  this script is intended for a clean re-imaged machine
 if you have been using docker and/or k8s already on your machine
 then you should back up your Docker and K8s configs
EOF
`
echo 
echo $warning |sed "s#RTN#\\n#g"| fold -s -80
echo 
read -p "Continue (y/n)?" CONT
if [ "$CONT" = "y" ]; then
  echo "Continuing...";
else
  echo "Aborting...";
  exit 1
fi
# customize these dirs as needed
docker_dir=/scratch/docker
k8s_dir=/scratch/k8s_dir

id=`id -u`
if [ $id -ne 0 ] ; then
  echo "ERROR you must run this script with sudo: id = $id"
  exit 1
fi

out=`egrep "Oracle Linux Server release 7.(2|3|4)" /etc/oracle-release`
if [ "$out" = "" ] ; then
   echo "ERROR /etc/oracle-release not an approved OEL 7.x version"
   exit 1
fi

set -e
#set real_user in environment to skip this.  In Jenkins, export real_user=wls
if [ "$real_user" = "" ] ; then
  export real_user=`who am i | awk '{print $1}'`
fi
echo Regular user = ${real_user:?}
export real_group=`groups $real_user | awk '{print $3}'`
echo Regular user group = ${real_group:?}
export real_user_home=`eval echo "~$real_user"`
echo Regular user home = ${real_user_home:?}
set +e

# generate a shell script to append to users .bashrc 
# so that proxy variables, etc are set on login
cat > /tmp/dockerk8senv  <<EOF
export PATH=\$PATH:/sbin:/usr/sbin
pod_network_cidr="10.244.0.0/16"

k8s_dir=$k8s_dir

## grab my IP address to pass into  kubeadm init, and to add to no_proxy vars
# assume ipv4 and eth0
ip_addr=\`ip -f inet addr show eth0  | egrep inet | awk  '{print \$2}' | awk -F/ '{print \$1}'\`
hostname=\$(uname -n | awk -F. '{print \$1}')
ext_ip_addr=\$(host \$hostname | awk '{print \$NF}')
fqdn=\$hostname.\`domainname\`


export HTTPS_PROXY=http://www-proxy-hqdc.us.oracle.com:80
export https_proxy=http://www-proxy-hqdc.us.oracle.com:80
export NO_PROXY=localhost,127.0.0.1,.us.oracle.com,.oraclecorp.com,.oracle.com,/var/run/docker.sock,\$pod_network_cidr,\$ip_addr,\$ext_ip_addr
export no_proxy=localhost,127.0.0.1,.us.oracle.com,.oraclecorp.com,.oracle.com,/var/run/docker.sock,\$pod_network_cidr,\$ip_addr,\$ext_ip_addr
export HTTP_PROXY=http://www-proxy-hqdc.us.oracle.com:80
export http_proxy=http://www-proxy-hqdc.us.oracle.com:80

export KUBECONFIG=\$k8s_dir/admin.conf
EOF

# source the script we just generated
. /tmp/dockerk8senv

# update the script to add command completion
cat >> /tmp/dockerk8senv <<EOF
[ -f /usr/share/bash-completion/bash_completion ] && . /usr/share/bash-completion/bash_completion
source <(kubectl completion bash)
EOF

# copy the script into place in users $HOME dir
sudo -u $real_user cp /tmp/dockerk8senv ${real_user_home}/.dockerk8senv 
cp ${real_user_home}/.bashrc /tmp/${real_user}.bashrc

# if the users .bashrc does not alread include this script, include it at the end
echo ". ${real_user_home}/.dockerk8senv" >> /tmp/${real_user}.bashrc
grep -q -F "${real_user_home}/.dockerk8senv" ${real_user_home}/.bashrc || sudo -u $real_user cp /tmp/${real_user}.bashrc ${real_user_home}/.bashrc

mkdir -p $docker_dir $k8s_dir/kubelet
ln -s $k8s_dir/kubelet /var/lib/kubelet

docker_version="17.03.1.ce"

### install docker and curl-devel (for git if needed)
yum-config-manager --enable ol7_addons ol7_latest
# we are going to just uninstall any docker-engine that is installed
yum -y erase docker-engine docker-engine-selinux
# now install the docker-engine at our specified version
yum -y install docker-engine-$docker_version curl-devel

# edit /etc/sysconfig/docker to add custom OPTIONS
cat /etc/sysconfig/docker | sed "s#^OPTIONS=.*#OPTIONS='--selinux-enabled --group=docker -g $docker_dir'#g" > /tmp/docker.out
echo INSECURE_REGISTRY='--insecure-registry wlsldi.docker.oraclecorp.com --insecure-registry wlsldi-v2.docker.oraclecorp.com --insecure-registry burge26.us.oracle.com:6000 --insecure-registry wls-docker-dev-local.dockerhub-den.oraclecorp.com' >> /tmp/docker.out
diff /etc/sysconfig/docker /tmp/docker.out
mv /tmp/docker.out /etc/sysconfig/docker

# generate a custom /setc/sysconfig/docker-network
 cat <<EOF > /etc/sysconfig/docker-network
# /etc/sysconfig/docker-network
DOCKER_NETWORK_OPTIONS="-H tcp://0.0.0.0:4243 -H unix:///var/run/docker.sock"
HTTP_PROXY="http://www-proxy-hqdc.us.oracle.com:80"
HTTPS_PROXY="http://www-proxy-hqdc.us.oracle.com:80"
NO_PROXY="localhost,127.0.0.0/8,.us.oracle.com,.oraclecorp.com,.oracle.com,/var/run/docker.sock"
EOF

# Add the regular user to the 'docker' group
usermod -aG docker $real_user

# enable and start docker service we just installed and configured
systemctl enable docker && systemctl start docker


### install kubernetes packages
# generate the yum repo config
cat <<EOF > /etc/yum.repos.d/kubernetes.repo
[kubernetes]
name=Kubernetes
baseurl=http://yum.kubernetes.io/repos/kubernetes-el7-x86_64
enabled=1
gpgcheck=1
repo_gpgcheck=1
gpgkey=https://packages.cloud.google.com/yum/doc/yum-key.gpg
       https://packages.cloud.google.com/yum/doc/rpm-package-key.gpg
EOF
setenforce 0
# install kube* packages
v=${1:-1.8.4-0}
old_ver=`echo $v | egrep "^1.7"`
yum install -y kubelet-$v kubeadm-$v kubectl-$v kubernetes-cni

# change the cgroup-driver to match what docker is using
cgroup=`docker info 2>&1 | egrep Cgroup | awk '{print $NF}'`
[ "$cgroup" == "" ] && echo "cgroup not detected!" && exit 1

cat /etc/systemd/system/kubelet.service.d/10-kubeadm.conf | sed "s#KUBELET_CGROUP_ARGS=--cgroup-driver=.*#KUBELET_CGROUP_ARGS=--cgroup-driver=$cgroup\"#"> /etc/systemd/system/kubelet.service.d/10-kubeadm.conf.out
diff  /etc/systemd/system/kubelet.service.d/10-kubeadm.conf  /etc/systemd/system/kubelet.service.d/10-kubeadm.conf.out
mv  /etc/systemd/system/kubelet.service.d/10-kubeadm.conf.out  /etc/systemd/system/kubelet.service.d/10-kubeadm.conf

if [ "$old_ver" = "" ] ; then

# run with swap if not in version 1.7* (starting in 1.8, kubelet
# fails to start with swap enabled)
#
cat <<EOF > /etc/systemd/system/kubelet.service.d/90-local-extras.conf
[Service]
Environment="KUBELET_EXTRA_ARGS=--fail-swap-on=false"
EOF

fi

# enable and start service
systemctl enable kubelet && systemctl start kubelet

######  this is the custom part.  This assumes we are going to install and use Flannel for CNI

# run kubeadm init as root
echo Running kubeadm init --skip-preflight-checks --apiserver-advertise-address=$ip_addr --apiserver-cert-extra-sans="$ext_ip_addr,$hostname,$ip_addr,$fqdn"  --pod-network-cidr=$pod_network_cidr
echo " see /tmp/kubeadm-init.out for output"
kubeadm init --skip-preflight-checks --apiserver-advertise-address=$ip_addr  --apiserver-cert-extra-sans="$ext_ip_addr,$hostname,$ip_addr,$fqdn" --pod-network-cidr=$pod_network_cidr > /tmp/kubeadm-init.out 2>&1
if [ $? -ne 0 ] ; then
  echo "ERROR: kubeadm init returned non 0"
  chmod a+r  /tmp/kubeadm-init.out
  exit 1
else
  echo; echo "kubeadm init complete" ; echo
  # tail the log to get the "join" token
  tail -6 /tmp/kubeadm-init.out 
fi 

cp /etc/kubernetes/admin.conf  $KUBECONFIG
chown $real_user:$real_group $KUBECONFIG
chmod 644 $KUBECONFIG

echo Created KUBECONFIG at $KUBECONFIG

#now run these commands as $real_user
# these commands should work at this point.  Need more error checking during and after execution
#
echo Running commands as user=$real_user to configure CNI
set -x
sudo -u $real_user kubectl create clusterrolebinding permissive-binding --clusterrole=cluster-admin --user=admin --user=kubelet --group=system:serviceaccounts
# this file from github is the head and has been unstable for us to just apply, the line after is a checked in stable version
sudo -u $real_user kubectl apply -f https://raw.githubusercontent.com/coreos/flannel/master/Documentation/kube-flannel.yml
#sudo -u $real_user kubectl apply -f $basedir/files/kube-flannel.yml
set +x

echo "Waiting for kubectl get nodes to show Ready for thist host"
host=`hostname | awk -F. '{print $1}'`
status="NotReady"
max=10
count=1
while [ ${status:=Error} != "Ready" -a $count -lt $max ] ; do
  sleep 30
  status=`sudo -u $real_user kubectl get nodes | egrep $host | awk '{print $2}'`
  echo "kubectl status is ${status:=Error}, iteration $count of $max"
  count=`expr $count + 1`
done

status=`sudo -u $real_user kubectl get nodes | egrep $host | awk '{print $2}'`
if [ ${status:=Error} != "Ready" ] ; then
  echo "ERROR: kubectl get nodes reports status=${status:=Error} after configuration, exiting!"
  exit 1
fi

set -x 
sudo -u $real_user kubectl taint nodes --all node-role.kubernetes.io/master-
sudo -u $real_user kubectl get nodes
sudo -u $real_user kubeadm version
set +x

echo
echo
echo "Docker and Kubernetes are now configured."
echo
echo "NOTE:   Please exit your shell and re-enter bash to source the new environment variables from .bashrc"
echo