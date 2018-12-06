# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.a

import sys
import os

#
# This program verifies that bean attr values are expected values.
# 
# Usage:
#
#   Create a file with lines of format:
#      bean-path,attr-name,original-val,overridden-val
#
#   For example:
#      /Servers/admin-server,ListenAddress,,domain1-admin-server
#
#   The program will check that the original-val matches the value
#   in the 'domainConfig' mbean tree and that the overridden-val
#   matches the value in the 'serverConfig' mbean tree.
#
#   The program exits with a non-zero exit code on any failure
#   (including an unexpected attribute value).
# 
#    

admin_username = sys.argv[1]
admin_password = sys.argv[2]
input_file = sys.argv[3]

admin_port=os.getenv('LOCAL_SERVER_DEFAULT_PORT')

service_name=os.getenv('SERVICE_NAME')

connect(admin_username,admin_password,"t3://" + service_name + ":" + admin_port)

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
  if (len(words) == 1 and line != '') or len(words) != 4:
    addError("Error in line syntax line#=" + str(line_no) + " line='"+line+"'")
    continue
  bean_path=words[0]
  attr=words[1]
  originalExpected=words[2]
  overriddenExpected=words[3]

  print(
      "Info: Checking bean_path='" + bean_path + "'"
    + " attr='" + attr + "'"
    + " originalExpected='" + originalExpected + "'"
    + " overriddenExpected='" + overriddenExpected + "'"
  )

  domainConfig()
  cd(bean_path)
  originalActual=get(attr)
  serverConfig()
  cd(bean_path)
  overriddenActual=get(attr)

  print("Info: originalActual='" + originalActual + "'")
  print("Info: overriddenActual='" + overriddenActual + "'")

  if originalActual != originalExpected:
    addError(
      "Error: got '" + originalActual + "'"
             + " but expected value '" + originalExpected + "'"
             + " for bean_path=domainConfig/" + bean_path 
             + " attr='" + attr + "'. "
    )

  if overriddenActual != overriddenExpected:
    addError(
      "Error: got '" + overriddenActual + "'"
             + " but expected value '" + overriddenExpected + "'"
             + " for bean_path=serverConfig/" + bean_path 
             + " attr='" + attr + "'. "
    )
file.close()		

if len(errors) > 0:
  print "Found " + str(len(errors)) + " errors:"
  for err in errors:
    print " --> " + err
  exit(exitcode=1)
