#### Checking online update status

During an online update, the Operator will rerun the introspector job, attempting online updates on the running domain. This feature is useful for changing any dynamic attribute of the WebLogic Domain. No pod restarts are necessary, and the changes immediately take effect. 
Once the job is completed, you can check the domain status to view the online update status: `kubectl -n <namespace> describe domain <domain uid>`.  Upon success, each WebLogic pod will have a `weblogic.introspectVersion` label that matches the `domain.spec.introspectVersion` that you specified.
 
When the introspector job finished, the domain status will be updated according to the result.

You can use the command to display the domain status

`kubectl -n <ns> describe domain <domain name>`

|Scenarios|Domain status ||
  |---------------------|-------------|-------|
  |Successful updates when there is no non-dynamic changes|Final domain status is Available with ServerReady reason. updated introspectVersion in the pods|
  |When onNonDynamicChanges=CommitUpdateAndRoll and there is non dynamic changes |Final domain status is Available with ServerReady reason. updated introspectVersion in the pods |
  |When onNonDynamicChanges=CommitUpdateOnly and there is non dynamic changes | Domain status will have a condition 'ConfigChangesPendingRestart'. Each pod will have a label 'weblogic.configurationChangedPendingRestart=true'|
  |Cancel changes per request|Domain status will have a failed status with message, no introspectVersion updated in the pods|
  |Any other errors| Domain status message will display the error message. No condition is set in the status condition|

For example, after a successful online update, you will see this in the `Domain Status` section

```
Status:
  Clusters:
    Cluster Name:      cluster-1
    Maximum Replicas:  5
    Minimum Replicas:  0
    Ready Replicas:    2
    Replicas:          2
    Replicas Goal:     2
  Conditions:
    Last Transition Time:        2020-11-18T15:19:11.867Z
    Reason:                      ServersReady
    Status:                      True
    Type:                        Available

```

If the changes involve non-dynamic mbean attributes, and you have specified 'CancelUpdate' under `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges`, you will see this

```
Conditions:
    Last Transition Time:  2021-01-20T15:07:29.427Z
    Message:               Online update completed successfully, but the changes require restart and the domain resource  specified 'spec.configuration.model.onlineUpdate.onNonDynamicChanges=CancelUpdate'  option to cancel all changes if restart require. The changes are:

Server re-start is REQUIRED for the set of changes in progress.

The following non-dynamic attribute(s) have been changed on MBeans
that require server re-start:
MBean Changed : com.bea:Name=oracle.jdbc.fanEnabled,Type=weblogic.j2ee.descriptor.wl.JDBCPropertyBean,Parent=[sample-domain1]/JDBCSystemResources[Bubba-DS],Path=JDBCResource[Bubba-DS]/JDBCDriverParams/Properties/Properties[oracle.jdbc.fanEnabled]
Attributes changed : Value
    Reason:                      BackoffLimitExceeded
    Status:                      True
    Type:                        Failed
  Introspect Job Failure Count:  3
  Message:                       Online update completed successfully, but the changes require restart and the domain resource  specified 'spec.configuration.model.onlineUpdate.onNonDynamicChanges=CancelUpdate'  option to cancel all changes if restart require. The changes are:

Server re-start is REQUIRED for the set of changes in progress.

The following non-dynamic attribute(s) have been changed on MBeans
that require server re-start:
MBean Changed : com.bea:Name=oracle.jdbc.fanEnabled,Type=weblogic.j2ee.descriptor.wl.JDBCPropertyBean,Parent=[sample-domain1]/JDBCSystemResources[Bubba-DS],Path=JDBCResource[Bubba-DS]/JDBCDriverParams/Properties/Properties[oracle.jdbc.fanEnabled]
Attributes changed : Value
  Reason:    BackoffLimitExceeded

```

If the changes involve non-dynamic mbean attributes, and you have specified 'CommitUpdateOnly' under `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` or not set, you will see this               
      
```
  Conditions:
    Last Transition Time:  2021-01-20T15:09:15.209Z
    Message:               Online update completed successfully, but the changes require restart and the domain resource specified 'spec.configuration.model.onlineUpdate.onNonDynamicChanges=CommitUpdateOnly' or not set. The changes are committed but the domain require manually restart to  make the changes effective. The changes are: Server re-start is REQUIRED for the set of changes in progress.

The following non-dynamic attribute(s) have been changed on MBeans 
that require server re-start:
MBean Changed : com.bea:Name=oracle.jdbc.fanEnabled,Type=weblogic.j2ee.descriptor.wl.JDBCPropertyBean,Parent=[sample-domain1]/JDBCSystemResources[Bubba-DS],Path=JDBCResource[Bubba-DS]/JDBCDriverParams/Properties/Properties[oracle.jdbc.fanEnabled]
Attributes changed : Value
    Reason:                      Online update applied, introspectVersion updated to 82
    Status:                      True
    Type:                        ConfigChangesPendingRestart
```
and you will see the label in each pod  

```Labels:       weblogic.configurationChangedPendingRestart=true
                 
```

Once the domain and all the pods are recycled, the condition `ConfigChangesPendingRestart` and label are cleared