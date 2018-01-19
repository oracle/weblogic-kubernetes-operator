# Using the operator's REST services

Write me
REST api to list the domains and clusters
basically GET .../operator/latest/domains and GET .../operator/latest/domains/<domainUid>/clusters
need to pass in the same token header and CA cert file, don't need to pass in the X-Requested-By header, should pass in Accept:/application/json header
Accept:application/json
And can use curl -k to bypass the check to verify that the operator's cert is trusted (instead of curl --cacert)
