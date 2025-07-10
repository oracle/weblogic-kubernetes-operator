#!/bin/bash

protocol=${1:-t3}
port=${2:-8001}
LOGFILE=/tmp/jms_test_output.log

# Setup WLS environment
. /u01/oracle/wlserver/server/bin/setWLSEnv.sh

# 1. Commit transaction
url="http://domain1-adminserver:7001/webapp/dtx.jsp?remoteurl=${protocol}://domain2-cluster-cluster-2:${port}&action=commit"
curl -j --noproxy "*" "$url" > "$LOGFILE" 2>&1

sleep 5

# 2. Compile and run Java client
javac -d /u01/domains /u01/domains/JmsClient.java >> "$LOGFILE" 2>&1
java -cp /u01/domains:$CLASSPATH JmsClient t3://domain1-adminserver:7001 weblogic jms.admin.Queue recv >> "$LOGFILE" 2>&1

sleep 5

# 3. Receive message from remote distributed destination
url="http://domain1-adminserver:7001/webapp/jms.jsp?remoteurl=${protocol}://domain2-cluster-cluster-2:${port}&action=recv&dest=jms.test.UniformQueue"
curl -j --noproxy "*" "$url" >> "$LOGFILE" 2>&1

# 4. Grep for expected output
echo "Searching for expected strings in $LOGFILE"


# List of strings to check
STRINGS=(
    "User Transation is committed"
    "Message Drained"
    "Total Message(s) Received : 10"
)

# Flag to track if all strings were found
ALL_FOUND=true

echo "Verifying expected output in $LOGFILE..."

cat $LOGFILE

for str in "${STRINGS[@]}"; do
    if grep -qF "$str" "$LOGFILE"; then
        echo "Found: '$str'"
    else
        echo "Missing: '$str'"
        ALL_FOUND=false
    fi
done

if $ALL_FOUND; then
    echo "All expected strings were found in the log."
else
    echo "One or more expected strings were missing."
    exit 1
fi
