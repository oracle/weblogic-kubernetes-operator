# New Tests

On this page, we will build up a list of "rules" for how 
new tests are to be built.  Here is a very first cut which
needs to be improved: 

* assertions must have the message
* must use our logging, timer, actions, assertions
* actions must use api, not spawn kubectl process
* diagnostics!!! 
* document how we inject the k8s cluster
* document how tags are to be used
* tests must not assume anything - they need to set up for themselves
* tests do not need to clean/delete (unless that is part of the actual test) 
  since we will just throw away the cluster at the end anyway
* no need for cleanup script - since every test will have a new cluster
