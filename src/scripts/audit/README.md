# Audit Tool Script

Audit tool script is used by audit team to scan container images and gather data on images containing Oracle products. This tool accepts a file containing list of container images to scan as an input. If input file containing list of images to scan is not provided, tool scans all the images pulled on current machine where tool is run. Tool also accepts a node information file containing mapping of nodes to images which is used to display node names where images are pulled.

## Installation

Unzip the tool.zip file.

```bash
unzip tool.zip
```

## Usage

```bash
$ tool.sh [-i <filename>] [-k <filename>] [-m <container_cli>] [-s] [-w <dirname>] [-d] [-h]

-i File containing list of images to scan in [Repository]:[Tag]@[Hash] format with each image on a separate line.
-k Node information file containing mapping of nodes to images. For Kubernetes use-case, this is the output of 'kubectl get nodes -o json' command.
-m Container CLI command e.g. 'docker' or 'podman'. Defaults to 'docker'.
-w Workspace directory, generated files will be stored in this directory. Defaults to './work' dir.
-d Disable validation check of images that are present in Node information file but have not been pulled on current machine.
-s Silent mode.
-h Help
```

## Validations
- The tool performs validation to ensure that all images present in Node information file are pulled on current machine where tool is running. If there are any missing images, tool generates a file `work/missing-images.txt` containing list of images that are missing. The validation is NOT performed for images matching a pattern specified in `util/exclude/exclude.txt` file. Tool will create a file `work/validation-skipped-images.txt` containing list of images for which validation was skipped.
- The tool verifies that hash-id of images pulled on current machine matches with hash-id present in Node information file. If hash verification fails for a particular image, tool provides a warning message and does not scan that image. The tool continues to scan other images. Optionally tool can verify image size when size verification is enabled using `-e` option. 

## Notes
1. The tool will NOT scan images matching a pattern specified in `util/exclude/exclude.txt` file. This is to avoid scanning of known non-Oracle product images such as Kubernetes images or JDK image. The list of images to exclude from scanning can be overridden by using `-x` option and providing a file with list of images to exclude. For e.g. ` -x ignoreImages.txt` option will make tool to exclude all images that match pattern specified in `ignoreImages.txt` file. 
2. The tool will NOT scan of images smaller than 200M. The size threshold for skipping image scan can be changed by setting `SCAN_THRESHOLD` environment variable e.g. `export SCAN_THRESHOLD=500M" will exclude images smaller than 500M from scanning.
3. The tool will generate a file `work/scan-excluded-images.txt` containing list of images that were NOT scanned.
4. The tool can be run with different container CLIs including `docker` and `podman`. Use `-m` option to run the tool with different container CLI (e.g. `-m podman`).
5. The output from current execution of tool is stored in `work/tool.log` file.

## Tool Output
Tool displays artifact name, repository, tag and digest information for images containing Oracle products. If node information file containing node to images mapping is provided (using `-k` option), tool also displays node names.
```
Node(s)               Artifact      Respository                                    Tag     Digest
phx32822d1|phx3283857 weblogic.jar  phx.ocir.io/weblogick8s/domain-in-image-wdt-1  latest  sha256:a7b4b6608c
