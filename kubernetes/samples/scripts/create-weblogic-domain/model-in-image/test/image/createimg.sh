#!/bin/bash
set -x
docker build --tag $1 --build-arg domain_uid=$2 --build-arg base_img=$3 .

