#!/bin/bash
set -x
docker build --tag $1 --build-arg base_img=$2 .

