#!/bin/bash
hugo server -b http://$(hostname).$(dnsdomainname):1313/weblogic-kubernetes-operator -D --bind=$(hostname).$(dnsdomainname)
