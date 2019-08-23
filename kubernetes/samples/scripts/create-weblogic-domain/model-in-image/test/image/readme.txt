general issues: 

   replace one-line scripts with in-lined contents in the readme doc itself

   SIDE ISSUE:  weblogic-image_tool.zip - zip doesn't have a readme? would be nice if it included a pointer to the doc


0. Setup dir env vars and an empty working directory

   export WORKDIR=/scratch/tbarnes/workdir
   export OPDIR=/scratch/tbarnes/github-k8op/weblogic-kubernetes-operator
   export MIISAMPLEDIR=$OPDIR/kubernetes/samples/scripts/create-weblogic-domain/model-in-image

   Make sure WORKDIR DNE or is empty.

   mkdir -p $WORKDIR

2. get imagetool zip in a different directory and follow the quickstart guide on github to setup the imagetool

   mkdir $WORKDIR/imagetool
   cd $WORKDIR/imagetool
   $MIISAMPLEDIR/test/image/latestimagetool.sh # retrieves weblogic-image_tool.zip
   unzip weblogic-image_tool.zip

   Follow setup instructions in https://github.com/oracle/weblogic-image-tool, for Linux:

     source $WORKDIR/imagetool/imagetool-*.*.*/bin/setup.sh

   Verify imagetool loaded:

     imagetool help


3. use imagetoolcreate.sh  to create a base image first. You will need to modify the script first

  - change the tag name
  - change the yourid to your oracle support id - usually your email id
  - create an environment variable containing the password of id

   cat $MIISAMPLEDIR/test/image/imagetoolcreate.sh | sed 's/yourid/tom.barnes@oracle.com/g' > $WORKDIR/imagetool/imagetoolcreate.sh
   chmod +x $WORKDIR/imagetool/imagetoolcreate.sh

   Setup env vars and
     export MYPWD=superdoublesecret
     export WLSIMG_CACHEDIR=$WORKDIR/imagetool/imagetool_cache  #used by imagetool, default is in $HOME
     export WLSIMG_BLDDIR=$WORKDIR/imagetool/imagetool_bldtmp  #tmp directory used by imagetool, default is in $HOME
     mkdir -p $WLSIMG_CACHEDIR
     mkdir -p $WLSIMG_BLDDIR

   cd $WORKDIR/imagetool

   Get WLS and JDK installers:
     https://edelivery.oracle.com
     Sign In
     Search for 'WLS', add first 12.2.1.3 to cart (are others better?)
     Search for 'Oracle JDK', add 1.8 to cart (in this case 1.8.0_202)

     In cart:
        Choose Linux x86-64 as target arch
        Uncheck Oracle Fusion Middleware 12c Infrastructure 12.2.1.3.0 (saves 7GB)  # IS THIS OK?
        Uncheck Oracle JDeveloper 12.2.1.3.0 (saves 2.3 GB) # IS THIS OK?
        Click "continue"
        Click "i accept the terms in the license agreement"

        Downlaod installers to $WORKDIR/installers  (or a similar well known location)
	  Download zip for        Oracle JDK 1.8.0.202 media upload for Linux x86-64, 353.7 MB
          Download zip for        Oracle Fusion Middleware 12c (12.2.1.3.0) WebLogic Server and Coherence, 800.1 MB

          unzip each zip into $WORKDIR/installers

      Put installers in the imagetool cache

        cd $WORKDIR/installers (or your own installer directory)
        imagetool cache addInstaller --type jdk --version 8u202 --path `pwd`/jdk-8u202-linux-x64.tar.gz
        imagetool cache addInstaller --type wls --version 12.2.1.3.0 --path `pwd`/fmw_12.2.1.3.0_wls.jar
        

   ./imagetoolcreate.sh <SSOPASSWORD>

   wait several minutes, should finish with "Successfully tagged model-in-image:12213p2"

   confirm with `docker images`

   ISSUE: we should parameterize imagetoolcreate.sh or in-line into the readme.

   ISSUE: imagetoolcreate.sh should source imagetool-*.*.*/bin/setup.sh  (DONE)
          (instead of requiring external step, and requiring us to source this file)

   ISSUE: imagetool depends on SSO - is there any way to force it to be typed in instead of put in plain-text command/export?
          SOLUTION:  Pass in password for now as a parm, future version of tool will be able to prompt

4. copy all *.yaml, *.properties, and archive.zip into current directory
   copy all Dockerfile, *.yaml, *.properties, and any archive.zip into a new directory


   mkdir $WORKDIR/mii-image
   cd $WORKDIR/mii-image
   $MIISAMPLEDIR/test/image/latestwdt.sh # gets 'weblogic-deploy.zip'
   cp $MIISAMPLEDIR/test/image/Dockerfile $MIISAMPLEDIR/test/image/*.yaml $MIISAMPLEDIR/test/image/*.properties $WORKDIR/mii-image
   cp ../weblogic-deploy.zip $WORKDIR/mii-image

   ISSUE:  No archive.zip supplied with this sample.

5. run create.sh passing in new image 

   OUTDATED
    create.sh <tag> <domain id> <base image tag>  This will copy all the wdt artifacts into the image 
    at the right place and tag the new image

   ISSUE: it's actually createimg.sh
   ISSUE: it's a one-line command, lets just in-line it into the readme

   cd $WORKDIR/mii-image
   cp $MIISAMPLEDIR/test/image/createimg.sh .
   ./createimg.sh model-in-image:m1 model-in-image:12213p2
