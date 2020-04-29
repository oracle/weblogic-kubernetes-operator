#!/bin/bash
# 1313 is the hugo default port
port=${1:-1313}
hugo server -b http://$(hostname).$(dnsdomainname):$port/weblogic-kubernetes-operator -D --bind=$(hostname).$(dnsdomainname) -p $port
