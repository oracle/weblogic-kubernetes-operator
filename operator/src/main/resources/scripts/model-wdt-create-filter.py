# Copyright (c) 2018, 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# ------------
# Description:
# ------------
#  This is a WDT filter for primordial domain creation. It filters out all resources and
#  apps deployments, leaving only the domainInfo and admin server in topology.
#
def filter_model(model):
	if model and 'topology' in model:
            topology = model['topology']
            if 'AdminServerName' in model['topology']:
                admin_server = topology['AdminServerName']
            else:
                # weblogic default
                admin_server = 'AdminServer'
            model['topology'] = {}
            model['topology']['AdminServerName'] = admin_server
            model['topology']['Server'] = {}
            if admin_server in topology['Server']:
                model['topology']['Server'][admin_server] = topology['Server'][admin_server]
            else:
                model['topology']['Server'][admin_server] = {}

            if 'Name' in topology:
                model['topology']['Name'] = topology['Name']

            if 'Security' in topology:
                model['topology']['Security'] = topology['Security']

	if model and 'appDeployments' in model:
            model['appDeployments'] = {}

	if model and 'resources' in model:
            model['resources'] = {}

