def filter_model(model):
	if model and 'topology' in model:
            topology = model['topology']
            if model['topology']['AdminServerName'] != None:
                admin_server = topology['AdminServerName']
                model['topology'] = {}
                model['topology']['AdminServerName'] = admin_server
                model['topology']['Server'] = {}
                model['topology']['Server'][admin_server] = topology['Server'][admin_server]   
            else:
                model['topology'] = {}

            if 'Security' in topology:
                model['topology']['Security'] = topology['Security']


	if model and 'appDeployments' in model:
            model['appDeployments'] = {}

	if model and 'resources' in model:
            model['resources'] = {}

        print model
