---
title: "Delete domain resources"
date: 2019-02-23T17:32:31-05:00
weight: 4
description: "Delete the domain resources created while executing the samples."
---


After running the samples, you will need to release domain resources that
can then be used for other purposes. The script in this sample demonstrates one approach to releasing
domain resources.

#### Use this script to delete domain resources

```
$ ./delete-weblogic-domain-resources.sh \
  -d  domain-uid[,domain-uid...] \
  [-s max-seconds] \
  [-t]
```
The required option `-d` takes `domain-uid` values (separated
 by commas and no spaces) to identify the domain resources that should be deleted.

To limit the amount of time spent on attempting to delete domain resources, use `-s`.
The option must be followed by an integer that represents the total number of seconds
that will be spent attempting to delete resources. The default number of seconds is 120.

The optional option `-t` shows what the script will delete without executing the deletion.

To see the help associated with the script:
```
$ ./delete-weblogic-domain-resources.sh -h
```
