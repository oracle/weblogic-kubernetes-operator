The integration tests are not completely independent of the environment.

You may run into one or more of the following errors when you attempt to execute the command
```
mvn clean verify -P java-integration-tests 2>&1 | tee log.txt
```
1. `[ERROR] No permision to create directory /scratch/...`  
there are a couple ways to resolve this issue:
* create a world writable directory named /scratch
* create some other world writable directory and then define the environment variables RESULT_ROOT and PV_ROOT to point to that directory. If you want, you can create two directories to keep things separated.
