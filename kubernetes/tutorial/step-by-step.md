# Run with Step-By-Step

## Install the Operator
## Install WebLogic Domains
### Prepare the DomainHome Dolder
- Option One: burn the domainHome into the docker image.
```
./domainHomeBuilder/build.sh domainName adminUser adminPwd
```
- Option Two: write to a host folder via docker volume.
```
./domainHomeBuilder/generate.sh domainName adminUser adminPwd
```
### Prepare the Domain Resource File
## Install the Ingress Controller
## Install Ingress resouces

