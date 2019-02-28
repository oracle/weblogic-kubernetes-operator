#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# This is a helper script for running arbitrary shell commands or scripts on 
# a kubernetes cluster using arbitrary images and zero or more arbitrary PVC mounts.
# Run with '-h' to get usage, or see 'usage_exit()' below.
#
# Credit:
#    - This script is an expansion of https://gist.github.com/yuanying/3aa7d59dcce65470804ab43def646ab6
#
# TBD:
#    - Image secret parameter hasn't been tested.
#

IMAGE='store/oracle/serverjre:8'
IMAGEPULLPOLICY='IfNotPresent'
IMAGEPULLSECRETS=''
IPSCOMMA=''

COMMAND='id'
PODNAME='one-off'
NAMESPACE='default'
MAXSECS=30

VOL_MOUNTS=''
VOLS=''
VCOMMA=''
CMFILES=''
VOLUMECOUNT=0

TMPDIR='/tmp'

EXITCODE=0

usage_exit() {
cat << EOF

  Usage: $(basename $0) ...options...

    Runs a 'one-off' command on a kubernetes cluster via a temporary pod.
    Provides options to specify pvc mounts, hostpath mounts, or additional
    files to load.

  Parameters:

    -i imagename           - Image, default is "$IMAGE".
    -l imagepullpolicy     - Image pull policy, default is "$IMAGEPULLPOLICY".
    -s imagesecret         - Image pull secret. No default.

    -n namespace           - Namespace, default is "$NAMESPACE".
    -p podname             - Pod name, default is "$PODNAME".

    -c command             - Command, default is "$COMMAND".

    -m pvc-name:mount-path - PVC name and mount location. No default.
    -m host-path:mount-path- Remote host-path and mount location. No default.
                             Host-path must begin with a '/'. 
    -f /path/filename      - Copies local file to pod at /tmpmount/filename

    -t timeout             - Timeout in seconds, default is $MAXSECS seconds.

    -d tmpdir              - Location to put tmp file(s).  Default is "$TMPDIR".
                             Use this if your command's output might be large.

    You can specify -f, -m, and -s more than once.

  Limitation:

    Concurrent invocations that have the same namespace and podname will
    interfere with each-other. You can change these using '-n' and '-p'.

  Sample usage:

    # Use an alternate image and show the user/uid/gid for that image
    ./krun.sh -i store/oracle/weblogic:12.2.1.3 -c 'id'
 
    # Get the WL version in a WL image
    ./krun.sh -i store/oracle/weblogic:12.2.1.3 -c \\
      'source /u01/oracle/wlserver/server/bin/setWLSEnv.sh && java weblogic.version'

    # Mount an existing pvc to /shared, and jar up a couple of its directories
    [ $? -eq 0 ] && ./krun.sh -m domainonpvwlst-weblogic-sample-pvc:/shared \\
                    -c 'jar cf /shared/pvarchive.jar /shared/domains /shared/logs'
    # ... then copy the jar locally
    [ $? -eq 0 ] && ./krun.sh -m domainonpvwlst-weblogic-sample-pvc:/shared \\
                    -c 'base64 /shared/pvarchive.jar' \\
                    | base64 -di > /tmp/pvarchive.jar
    # ... then delete the remote jar
    [ $? -eq 0 ] && ./krun.sh -m domainonpvwlst-weblogic-sample-pvc:/shared \\
                    -c 'rm -f /shared/pvarchive.jar'

    # Mount a directory on the host to /scratch and 'ls' its contents
    ./krun.sh -m /tmp:/scratch -c 'ls /scratch'
 
    # Copy local files to /tmpmount in the pod, and run one of them as a script
    echo 'hi there from foo.sh!' > /tmp/foo.txt
    echo 'cat /tmpmount/foo.txt' > /tmp/foo.sh
    ./krun.sh -f /tmp/foo.txt -f /tmp/foo.sh -c "sh /tmpmount/foo.sh"
    rm -f /tmp/foo.txt /tmp/foo.sh

EOF
 
  exit 1
}

delete_pod_and_cm() {
  kubectl delete -n ${NAMESPACE} pod ${PODNAME} --ignore-not-found > /dev/null 2>&1
  kubectl delete -n ${NAMESPACE} cm ${PODNAME}-cm --ignore-not-found > /dev/null 2>&1
}

while getopts m:t:i:c:n:f:p:l:s:d: OPT
do
  case $OPT in
  i) IMAGE=$OPTARG
     ;;
  l) IMAGEPULLPOLICY=$OPTARG
     ;;
  s) IMAGEPULLSECRETS="${IMAGEPULLSECRETS}${IPSCOMMA}{\"name\": \"$OPTARG\"}"
     IPSCOMMA=","
     ;;
  p) PODNAME=$OPTARG
     ;;
  c) COMMAND=$OPTARG
     ;;
  n) NAMESPACE=$OPTARG
     ;;
  t) MAXSECS=$OPTARG
     ;;
  d) TMPDIR=$OPTARG
     ;;
  f) # local file to make available in /tmpmount
     if [ "$CMFILES" = "" ]; then
       VOL_MOUNTS="${VOL_MOUNTS}${VCOMMA}{\"name\": \"my-config-map-vol\",\"mountPath\": \"/tmpmount\"}"
       VOLS="${VOLS}${VCOMMA}{\"name\": \"my-config-map-vol\",\"configMap\": {\"name\": \"${PODNAME}-cm\"}}"
       VCOMMA=","
     fi
     CMFILES="--from-file $OPTARG $CMFILES"
     ;;
  m) # pvc or hostPath volume mount
     
     # parse "arg1:arg2" into arg1 and arg2
     arg1="`echo $OPTARG | sed 's/:.*//g'`"
     arg2="`echo $OPTARG | sed 's/.*://g'`"
     [ "$arg1" = "" ] && usage_exit
     [ "$arg2" = "" ] && usage_exit

     mountPath="$arg2"
     VOLUMECOUNT=$((VOLUMECOUNT + 1))
     volName="volume${VOLUMECOUNT}"
     VOL_MOUNTS="${VOL_MOUNTS}${VCOMMA}{\"name\": \"${volName}\",\"mountPath\": \"${mountPath}\"}"

     if [ "`echo $arg1 | grep -c '^/'`" = "1" ]; then
       # hostPath mount (since arg1 starts with '/')
       VOLS="${VOLS}${VCOMMA}{\"name\": \"${volName}\",\"hostPath\": {\"path\": \"${arg1}\"}}"
     else
       # pvc mount (since arg1 starts without a '/')
       VOLS="${VOLS}${VCOMMA}{\"name\": \"${volName}\",\"persistentVolumeClaim\": {\"claimName\": \"${arg1}\"}}"
     fi

     VCOMMA=","
     ;;
  *) usage_exit
     ;;
  esac
done
shift $(($OPTIND - 1))
[ ! -z "$1" ] && usage_exit

# setup a tmp file /tmp/[script-file-name].[name-space].[pod-name].tmp.[pid]

[ ! -d "$TMPDIR" ] && echo "Error: temp dir \"$TMPDIR\" not found. Check parameters." && exit 1

TEMPFILE="${TMPDIR}/$(basename $0).${NAMESPACE}.${PODNAME}.tmp.$$"  

# cleanup anything from previous run

delete_pod_and_cm

# create cm that contains any files the user specified on the command line:

if [ ! -z "$CMFILES" ]; then
  kubectl create cm ${PODNAME}-cm -n ${NAMESPACE} ${CMFILES} > $TEMPFILE 2>&1
  EXITCODE=$?
  if [ $EXITCODE -ne 0 ]; then
    echo "Error: kubectl create cm failed."
    # Since EXITCODE is non-zero, the script will skip 
    # doing more stuff and cat the contents of $TEMPFILE below.
  fi
fi

# run teh command, honoring any mounts specified on the command line

[ $EXITCODE -eq 0 ] && kubectl run -it --rm --restart=Never --tty --image=${IMAGE} ${PODNAME} -n ${NAMESPACE} --overrides "
{
  \"spec\": {
    \"hostNetwork\": true,
    \"containers\":[
      {
        \"command\": [\"bash\"],
        \"args\": [\"-c\",\"${COMMAND} ; echo -n {EXITCODE=\$?}\"],
        \"stdin\": true,
        \"tty\": true,
        \"name\": \"${PODNAME}\",
        \"image\": \"${IMAGE}\",
        \"imagePullPolicy\": \"${IMAGEPULLPOLICY}\",
        \"volumeMounts\": [
          ${VOL_MOUNTS}
        ]
      }
    ],
    \"imagePullSecrets\": [
      ${IMAGEPULLSECRETS}
    ],
    \"volumes\": [
      ${VOLS}
    ]
  }
}
" > $TEMPFILE 2>&1 &

# If the background task doesn't complete within MAXSECS
# then report a timeout and try kubectl describe the pod.

STARTSECS=$SECONDS
while [ $EXITCODE -eq 0 ]
do
  JOBS="`jobs -pr`"
  [ "$JOBS" = "" ] && break
  if [ $((SECONDS - STARTSECS)) -gt $MAXSECS ]; then
    EXITCODE=98
    echo "Error: Commmand timed out after $MAXSECS seconds."
    kubectl describe -n ${NAMESPACE} pod ${PODNAME} 
    break
  fi
  sleep 0.1
done

# If background task's out file doesn't contain '{EXITCODE=0}', 
# then it didn't finish or it failed...

if [ $EXITCODE -eq 0 ]; then
  if [ ! "`grep -c '{EXITCODE=0}' $TEMPFILE`" -eq 1 ]; then
    EXITCODE=99
  fi
fi

# Delete pod in case it timed out and is still running, plus delete
# the config map of files (if any).  This should also complete the
# background task (which waits on the pod to finish running).

delete_pod_and_cm

# Show output from pod (or from failing 'kubectl create cm' command above)

cat $TEMPFILE | sed 's/{EXITCODE=0}//' \
              | grep -v "Unable to use a TTY - input is not a terminal or the right kind of file" \
              | grep -v "If you don't see a command prompt, try pressing enter." \
              | grep -v "Error attaching, falling back to logs:"

# Delete output file from pod

rm -f $TEMPFILE

exit $EXITCODE


