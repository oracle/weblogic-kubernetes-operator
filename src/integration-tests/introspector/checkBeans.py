# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.a

import sys
import os

#
# This program verifies that bean attr values are expected values,
# it can be used to demonstrate situational config overrides.
# 
# Usage:
#
#   Create a file with lines of format:
#      bean-path,attr-name,original-val,overridden-val
#
#   For example:
#      /Servers/admin-server,ListenAddress,,domain1-admin-server
#
#   Special values '*' and '!':
# 
#      original-val=*
#      If you don't want to check the original-val is a specific
#      expected value, then specify an asterisk (*) instead
#      of the expected value.
#
#      overridden-val=!
#      If you don't want to check that the overridden-val is a specific 
#      expected val, but only want to assert that it's different than
#      the original-val, then specify a bang (!) instead of the 
#      expected value.
#
#   Then run this program:
#      wlst.sh checkBeans admin_name admin_pass url input_file_name
#
#   The program will:
#      Check that the original-val matches the value in the
#      'domainConfig' mbean tree (or matches any value if
#      original-val is '*').
#
#      Check if the overridden-val matches the value in the
#      'serverConfig' mbean tree (or simply assert the
#      serverConfig and the domainConfig values differ
#      if the overridden-val is '!').
#
#   The program exits with a non-zero exit code on any failure
#   (including an unexpected attribute value).
# 
# Sample usage in an Operator k8s WL pod:
#   Assumptions:  
#        Assumes a configuration with 'admin-server' listen-address of localhost, and 
#        'managed-server1' listen-address of localhost, and assumes that these have
#         been overridden by sit-cfg to be 'domain1-admin-server' and 
#        'domain1-managed-server1' respectively.
#
#   test_home=/tmp/introspect
#   mypod=domain1-admin-server
#   infile=$test_home/checkBeans.input
#   outfile=$test_home/checkBeans.output
#   myns=default
#   url=t3://domain1-admin-server:7001
#   username=weblogic
#   password=welcome1
#
#   echo "Info: Checking beans to see if sit-cfg took effect.  Input file '$infile', output file '$outfile'."
#
#   mkdir $test_home
#   echo '/Servers/admin-server,ListenAddress,localhost,domain1-admin-server' > $infile
#   echo '/Servers/managed-server1,ListenAddress,localhost,domain1-managed-server1' >> $infile
#
#   kubectl -n $myns cp $infile $mypod:/shared/checkBeans.input || exit 1
#   kubectl -n $myns cp checkBeans.py $mypod:/shared/checkBeans.py || exit 1
#   kubectl -n $myns exec -it $mypod \
#     wlst.sh /shared/checkBeans.py \
#       $username $password $url \
#       /shared/checkBeans.input \
#       > $outfile 2>&1
#   if [ $? -ne 0 ]; then
#     echo "Error:  checkBeans failed, see '$outfile'."
#     exit 1
#   fi
#

admin_username = sys.argv[1]
admin_password = sys.argv[2]
url = sys.argv[3]
input_file = sys.argv[4]

connect(admin_username,admin_password,url)

errors=[]
def addError(err):
  errors.append(err)

file = open(input_file, 'r')

line_no=0
for line in file:
  line=line.strip()
  line_no+=1
  print "Info:  input file line# " + str(line_no) + " ='"+line+"'"
  if len(line)>0 and line[0]=='#':
    continue
  words=line.split(',')
  if len(words) != 4:
    if line != '':
      addError("Error in line syntax line#=" + str(line_no) + " line='"+line+"'")
    continue
  bean_path=words[0]
  attr=words[1]
  originalExpected=words[2]
  overriddenExpected=words[3]

  print(
      "Info: Checking line#=" + str(line_no) + " bean_path='" + bean_path + "'"
    + " attr='" + attr + "'"
    + " originalExpected='" + originalExpected + "'"
    + " overriddenExpected='" + overriddenExpected + "'"
  )

  domainConfig()
  cd(bean_path)
  originalActual=str(get(attr))
  serverConfig()
  cd(bean_path)
  overriddenActual=str(get(attr))
  checkStatus='SUCCESS'

  if originalExpected != '*' and originalActual != originalExpected:
    checkStatus='FAILED'
    addError(
      "Error: got '" + originalActual + "'"
             + " but expected value '" + originalExpected + "'"
             + " for bean_path=domainConfig/" + bean_path 
             + " attr='" + attr + "'. "
    )

  if overriddenExpected != '!' and overriddenActual != overriddenExpected:
    checkStatus='FAILED'
    addError(
      "Error: got '" + overriddenActual + "'"
             + " but expected value '" + overriddenExpected + "'"
             + " for bean_path=serverConfig/" + bean_path 
             + " attr='" + attr + "'. "
    )

  if overriddenExpected == '!' and overriddenActual == originalActual:
    checkStatus='FAILED'
    addError(
      "Error: expected original value and actual value to differ "
             + " but got value '" + originalActual + "' for both"
             + " for bean_path=serverConfig/" + bean_path 
             + " attr='" + attr + "'. "
    )

  print(
      "Info: Checked line#=" + str(line_no) + " status=" + checkStatus + " bean_path='" + bean_path + "'"
    + " attr='" + attr + "'"
    + " originalExpected/Actual='" + originalExpected + "'/'" + originalActual + "'"
    + " overriddenExpected/Actual='" + overriddenExpected + "'/'" + overriddenActual + "'"
  )
file.close()		

if len(errors) > 0:
  print "Found " + str(len(errors)) + " errors:"
  for err in errors:
    print " --> " + err
  exit(exitcode=1)

print "Test Passed!"
