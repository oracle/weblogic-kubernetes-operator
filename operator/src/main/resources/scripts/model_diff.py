import sets
class ModelDiffer(object):

    def __init__(self, current_dict, past_dict):

        self.final_changed_model=dict()
        self.current_dict = self._eval_file(current_dict)
        self.past_dict = self._eval_file(past_dict)

        #self.current_dict, self.past_dict = current_dict, past_dict
        self.set_current = sets.Set()
        self.set_past = sets.Set()
        for item in current_dict.keys():
            self.set_current.add(item)
        for item in past_dict.keys():
            self.set_past.add(item)
        #set(current_dict.keys()), set(past_dict.keys())
        self.intersect = self.set_current.intersection(self.set_past)

    def _eval_file(self, file):
        fh = open(file, 'r')
        content = fh.read()
        return eval(content)

    def added(self):
        return self.set_current - self.intersect

    def removed(self):
        return self.set_past - self.intersect

    def changed(self):
        result = sets.Set()
        for o in self.intersect:
            if self.past_dict[o] != self.current_dict[o]:
                result.add(o)
        return result
        #return set(o for o in self.intersect if self.past_dict[o] != self.current_dict[o])

    def unchanged(self):
        result = sets.Set()
        for o in self.intersect:
            if self.past_dict[o] == self.current_dict[o]:
                result.add(o)
        return result
        #return set(o for o in self.intersect if self.past_dict[o] == self.current_dict[o])

    def print_diff(self,s, category):
        print category
        if len(s) > 0:
            print s

    def recursive_changed_detail(self, key, token, root):
        a=ModelDiffer(self.current_dict[key], self.past_dict[key])
        diff=a.changed()
        if len(diff) > 0:
            for o in diff:
                token=token+'.'+o
                if a.is_dict(o):
                    a.recursive_changed_detail(o,token, root)
                    last=token.rfind('.')
                    token=root
                else:
                    all_changes.append(token)
                    last=token.rfind('.')
                    token=root
        #token = root

    def is_dict(self,key):
        if isinstance(self.current_dict[key],dict):
            return 1
        else:
            return 0

    def calculate_changed_model(self):
        result = dict()
        changed=self.changed()
        #print 'changed '
        #print changed
        for s in changed:
            token=s
            x=obj.recursive_changed_detail(s, token, s)
            #print ' all changes '
            #print all_changes
            #print "token=" + str(x)
            for item in all_changes:
                splitted=item.split('.',1)
                n=len(splitted)
                result=dict()
                walked=[]

                while n > 1:
                    tmp=dict()
                    tmp[splitted[0]]=dict()
                    if len(result) > 0:
                        # traverse to the leaf
                        leaf=result
                        for k in walked:
                            leaf = leaf[k]
                        leaf[splitted[0]]=dict()
                        walked.append(splitted[0])
                    else:
                        result=tmp
                        walked.append(splitted[0])
                    splitted=splitted[1].split('.',1)
                    n=len(splitted)

                leaf=result
                value_tree=self.current_dict
                for k in walked:
                    leaf = leaf[k]
                    value_tree=value_tree[k]

                leaf[splitted[0]] =value_tree[splitted[0]]
                self.merge_dictionaries(self.final_changed_model, result)
                #print changed_model

    def merge_dictionaries(self, dictionary, new_dictionary):
        """
        Merge the values from the new dictionary to the existing one.
        :param dictionary: the existing dictionary
        :param new_dictionary: the new dictionary to be merged
        """
        for key in new_dictionary:
            new_value = new_dictionary[key]
            if key not in dictionary:
                dictionary[key] = new_value
            else:
                value = dictionary[key]
                if isinstance(value, dict) and isinstance(new_value, dict):
                    self.merge_dictionaries(value, new_value)
                else:
                    dictionary[key] = new_value

    def is_safe_diff(self, model):
        if model.has_key('appDeployments'):
            return 0

    def get_final_changed_model(self):
        return self.final_changed_model

# current={'domainInfo': \
# {'AdminUserName': 'weblogic', 'AdminPassword': '{AES}OWNNcVhRUXVnNFZJTjdCUWZ3QkMwdnJYNlltUUk3Mjc6MXNDY1BrNzJRbnNZM1JLQTppS2NuenBpWDNSYz0=', 'ServerStartMode': 'prod'}, \
# 'topology': {'Name': 'domain1', 'AdminServerName': 'admin-server', 'SecurityConfiguration': \
# {'NodeManagerUsername': 'weblogic', 'NodeManagerPasswordEncrypted': '{AES}N0I0Rk5wZ0ZVWHh0RE1LMWY5Q3NrWFNsR3hYQndHdDU6MVJONG9mRFIzUHdqbzd4bTpWY2w1bSsvRE10WT0='}, \
# 'Cluster': {'cluster-1': {'DynamicServers': {'ServerTemplate': 'cluster-1-template', 'ServerNamePrefix': 'managed-server', 'DynamicClusterSize': 5L, 'MaxDynamicClusterSize': 5L}}}, \
#  'Server': {'admin-server': {'ListenPort': 7003L}}, \
#  'ServerTemplate': {'cluster-1-template': {'Cluster': 'cluster-1', 'ListenPort': 8003L}}}}

# past={'domainInfo': {'AdminUserName': 'weblogic', 'AdminPassword': '{AES}OWNNcVhRUXVnNFZJTjdCUWZ3QkMwdnJYNlltUUk3Mjc6MXNDY1BrNzJRbnNZM1JLQTppS2NuenBpWDNSYz0=', 'ServerStartMode': 'prod'}, \
# 'topology': {'Name': 'domain1', 'AdminServerName': 'admin-server', 'SecurityConfiguration': \
# {'NodeManagerUsername': 'weblogic', 'NodeManagerPasswordEncrypted': '{AES}N0I0Rk5wZ0ZVWHh0RE1LMWY5Q3NrWFNsR3hYQndHdDU6MVJONG9mRFIzUHdqbzd4bTpWY2w1bSsvRE10WT0='}, \
# 'Cluster': {'cluster-1': {'DynamicServers': {'ServerTemplate': 'cluster-1-template', 'ServerNamePrefix': 'managed-server', 'DynamicClusterSize': 5L, 'MaxDynamicClusterSize': 5L}}}, \
# 'Server': {'admin-server': {'ListenPort': 7001L}}, \
#  'ServerTemplate': {'cluster-1-template': {'Cluster': 'cluster-1', 'ListenPort': 8001L}}}}

current={'domainInfo': {'AdminUserName': 'weblogic', 'AdminPassword': 'welcome1', 'ServerStartMode': 'prod'}, \
         'topology': {'Name': 'simple_domain', 'SecurityConfiguration': {'NodeManagerUsername': 'weblogic', \
                                                                         'NodeManagerPasswordEncrypted': 'welcome1'}, 'Cluster': {'mycluster': {'MulticastTTL': 3L}}, \
                      'Server': {'AdminServer': {'ListenPort': 7002L, 'ListenAddress': 'localhost', 'Machine': 'machine1'}, \
                                 's1': {'ListenPort': 8001L, 'ListenAddress': 'localhost', 'Cluster': 'mycluster', 'Machine': 'machine1'}}, \
                      'Machine': {'machine1': {'NodeManager': {'ListenPort': 5556L, 'ListenAddress': 'localhost'}}}}, \
         'resources': {'JDBCSystemResource': {'Generic2': {'Target': 'mycluster', 'JdbcResource': \
             {'JDBCDataSourceParams': {'JNDIName': ['jdbc/generic2', 'jdbc/special2'], 'GlobalTransactionsProtocol': \
                 'TwoPhaseCommit'}, 'JDBCDriverParams': {'DriverName': 'oracle.jdbc.xa.client.OracleXADataSource', \
                                                         'URL': 'jdbc:oracle:thin:@//localhost:1522/ORCLPDB1.localdomain', 'PasswordEncrypted': 'Oradoc_db1', \
                                                         'Properties': {'user': {'Value': 'sys as sysdba'}, 'oracle.net.CONNECT_TIMEOUT': {'Value': 5000L}, \
                                                                        'oracle.jdbc.ReadTimeout': {'Value': 30000L}}}, 'JDBCConnectionPoolParams': {'InitialCapacity': 3L, '\
     MaxCapacity': 15L, 'TestTableName': 'SQL ISVALID', 'TestConnectionsOnReserve': 'True'}}}}}}

past={'domainInfo': {'AdminUserName': 'weblogic', 'AdminPassword': 'welcome1', 'ServerStartMode': 'prod'}, \
      'topology': {'Name': 'simple_domain', 'SecurityConfiguration': {'NodeManagerUsername': 'weblogic', \
                                                                      'NodeManagerPasswordEncrypted': 'welcome1'}, 'Cluster': {'mycluster': {'MulticastTTL': 3L}}, \
                   'Server': {'AdminServer': {'ListenPort': 7001L, 'ListenAddress': 'localhost', 'Machine': 'machine1'}, \
                              's1': {'ListenPort': 8001L, 'ListenAddress': 'localhost', 'Cluster': 'mycluster', 'Machine': 'machine1'}}, \
                   'Machine': {'machine1': {'NodeManager': {'ListenPort': 5556L, 'ListenAddress': 'localhost'}}}}, \
      'resources': {'JDBCSystemResource': {'Generic2': {'Target': 'mycluster', 'JdbcResource': \
          {'JDBCDataSourceParams': {'JNDIName': ['jdbc/generic2', 'jdbc/special2'], 'GlobalTransactionsProtocol': \
              'TwoPhaseCommit'}, 'JDBCDriverParams': {'DriverName': 'oracle.jdbc.xa.client.OracleXADataSource', \
                                                      'URL': 'jdbc:oracle:thin:@//localhost:1522/ORCLPDB2.localdomain', 'PasswordEncrypted': 'Oradoc_db1', \
                                                      'Properties': {'user': {'Value': 'sys as sysdba'}, 'oracle.net.CONNECT_TIMEOUT': {'Value': 5000L}, \
                                                                     'oracle.jdbc.ReadTimeout': {'Value': 30000L}}}, 'JDBCConnectionPoolParams': {'InitialCapacity': 3L, '\
     MaxCapacity': 15L, 'TestTableName': 'SQL ISVALID', 'TestConnectionsOnReserve': 'True'}}}}}}

obj=ModelDiffer(current, past)
obj.calculate_changed_model()
print obj.get_final_changed_model()
