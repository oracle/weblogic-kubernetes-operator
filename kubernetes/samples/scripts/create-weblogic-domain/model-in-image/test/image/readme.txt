1. run latestimagetool.sh and latestwdt.sh to get the latest wdt and image tool
2. unzip imagetool zip in a different directory and follow the quickstart guide on github to setup the imagetool
3. use imagetoolcreate.sh  to create a base image first. You will need to modify the script first

  - change the tag name
  - change the yourid to your oracle support id - usually your email id
  - create an environment variable containing the password of id

4. copy all *.yaml, *.properties, and archive.zip into current directory
5. run create.sh passing in new image 

    create.sh <tag> <domain id> <base image tag>  This will copy all the wdt artifacts into the image 
    at the right place and tag the new image
