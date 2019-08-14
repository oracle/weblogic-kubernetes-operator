#!/bin/bash
echo "$http_proxy="$http_proxy $HTTP_PROXY
echo "$https_proxy="$https_proxy $HTTPS_PROXY
downloadlink=$(curl -sL https://github.com/oracle/weblogic-image-tool/releases/latest | grep "/oracle/weblogic-image-tool/releases/download" | awk '{ split($0,a,/href="/); print a[2]}' | cut -d\" -f 1)
echo $downloadlink
curl -L  https://github.com$downloadlink -o weblogic-image_tool.zip

