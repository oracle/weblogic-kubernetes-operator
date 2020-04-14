TBD update and copyright

These are wrapper scripts for running the WL Operator MII sample.  To use:

(1) Setup env.

    Modify env.sh to suit your current environment.  (WORKDIR should point to an empty directory, etc.)

    vi env.sh
    source env.sh


(2) Cleanup.

    Cleanup any old k8s resources via the integration test's cleanup script.

    ./cleanup.sh


(3) Setup Operator.

    Build and deploy the operator, plus ensure it's watching the expected namespace.

    ./oper_build.sh
    ./oper_deploy.sh


(4) Setup Traefik Load Balancer and Associated Ingress (optional).
    Assumes managed servers are listening on port 8001.

    ./traefik_deploy.sh 


(5) Setup MII image.

    Copy sample to $WORKDIR, download image tool, download WDT, and run image tool to create the MII image.

    ./sample_build.sh


(6) Deploy MII K8S resources.

    Deploy sample's resources (including the domain).

    ./sample_run.sh


(7) Now try an update (not part of the official sample yet).

    Redeploy configmap with an added data source, deploy
    new secret with data source URL/pass, patch domain
    to add the new secret:

    ./add_data_source.sh

    Force a restart:

    ./restart.sh

    Don't worry if you don't have a DB up - the DS will still show up in config.

    Once the restart finishes, you should see the DS on the console:

     http://$(hostname).$(dnsdomainname):30305/console  (weblogic/welcome1)
