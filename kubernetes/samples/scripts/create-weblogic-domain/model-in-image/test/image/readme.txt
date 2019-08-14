1. run latestimagetool.sh and latestwdt.sh to get the latest wdt and image tool
2. unzip imagetool zip in a different directory and follow the quickstart guide on github
3. use imagetoolcreate.sh  to create a base image first.  read this script for doc
4. copy all *.yaml, *.properties, and archive.zip into current directory
5. run create.sh passing in new image tag, domain id, and base image tag.  This will copy all the wdt artifacts into the image at the right place
