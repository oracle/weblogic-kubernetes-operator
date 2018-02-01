#!/bin/sh
# this does a quick uninstall and rm -rf of things that may get in the way
# of running install_docker_k8s.sh
# 
# edit the /scratch/* dirs as needed for your installation
#
PATH=/sbin:/usr/sbin:/bin:/usr/bin:$PATH
export PATH

warning=`cat << EOF
 WARNING:  This script will run the following commands. Please
 examine them and abort if needed.  Especially note the /scratch/\*
 directories that will be removed
RTN
RTN
kubeadm reset
RTN
yum erase -y docker-engine* kubelet* kubeadm* kubectl* kubernetes-cni*
RTN
RTN
umount /var/run/docker
RTN
rm -rf /scratch/docker /scratch/k8s /etc/kubernetes /etc/docker

EOF
`
echo 
echo $warning |sed "s#RTN#\\n#g" | fold -s -80
echo 
read -p "Continue (y/n)?" CONT
if [ "$CONT" = "y" ]; then
  echo "Continuing...";
else
  echo "Aborting...";
  exit 1
fi

id=`id -u`
if [ $id -ne 0 ] ; then
  echo "ERROR you must run this script with sudo: id = $id"
  exit 1
fi
kubeadm reset
yum erase -y docker-engine* kubelet* kubeadm* kubectl* kubernetes-cni*

# unmount all mount points with /docker in the name
findmnt -o TARGET -l | egrep "/(docker|kubelet)" | xargs -r -n1 umount

rm -rf /scratch/docker /scratch/k8s_dir /etc/kubernetes /etc/docker /var/run/docker* /var/lib/cni /var/lib/kubelet
# clean up after old test runs to make sure new ones can create files if
# the old ones were owned by root, but we're running as wls now
rm -rf /tmp/operator* /tmp/integration-test* /tmp/internal /tmp/*.sh /tmp/*.yml /tmp/*.yaml

rm -f /etc/systemd/system/kubelet.service.d/90-local-extras.conf

# take down and delete the docker/k8s interfaces
ip link set docker0 down
ip link delete docker0
ip link set flannel.1 down
ip link delete flannel.1
ip link set cni0 down
ip link delete cni0

# set all iptables rules to wide open
iptables -P INPUT ACCEPT
iptables -P FORWARD ACCEPT
iptables -P OUTPUT ACCEPT
iptables -t nat -F
iptables -t mangle -F
iptables -F
iptables -X
