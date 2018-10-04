#!/bin/sh

request_body=$(cat <<EOF
<?xml version='1.0' encoding='UTF-8'?>
<d:domain xmlns:d="http://xmlns.oracle.com/weblogic/domain" xmlns:f="http://xmlns.oracle.com/weblogic/domain-fragment" xmlns:s="http://xmlns.oracle.com/weblogic/situational-config">
  <s:expiration> 2020-07-16T19:20+01:00 </s:expiration>
  <d:server>
      <d:name>admin-server</d:name>
      <d:log f:combine-mode="replace">
           <d:file-name>/shared/logs/admin-server-with-sit-config-override-name.log</d:file-name>
      </d:log>
  </d:server>
</d:domain>
EOF
)

echo "$request_body"
exit 0
