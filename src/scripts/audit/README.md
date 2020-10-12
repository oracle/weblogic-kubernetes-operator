# Audit Tool Script

Audit tool script is used by the audit team to scan container images and gather data on images containing Oracle products. This tool accepts a file containing a list of container images to scan as an input. If an input file containing a list of images to scan is not provided, the tool scans all images pulled on the current machine where the tool is run. Tool also accepts a node information file containing mapping of nodes to images as an input, it is used to display names of nodes where images are pulled.

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
-d Disable validation check of images that are present in the node information file but have not been pulled on the current machine.
-s Silent mode.
-h Help
```

## Validations
- The tool performs validation to ensure that all images present in the node information file are pulled on the current machine where the tool is running. If there are any missing images, the tool generates a file `work/missing-images.txt` containing a list of images that are missing. The validation is NOT performed for images matching a pattern specified in `util/exclude/exclude.txt` file. Tool will create a file `work/validation-skipped-images.txt` containing a list of images for which validation was skipped. This validation can be disabled using the `-d` option.
- The tool verifies that the hash-id of images pulled on the current machine matches with the hash-id present in the node information file. If hash verification fails for a particular image, the tool provides a warning message and does not scan that image. The tool continues to scan other images. Optionally tool can verify image sizes when size verification is enabled using `-e` option.

## Notes
1. The tool will NOT scan images matching a pattern specified in `util/exclude/exclude.txt` file. This is to avoid scanning of known non-Oracle product images such as Kubernetes images. The list of images to exclude from scanning can be overridden by using the `-x` option and providing a file with a list of images to exclude. For e.g. ` -x ignoreImages.txt` option will make the tool to exclude scanning of all images that match the pattern specified in `ignoreImages.txt` file.
2. The tool will NOT scan images smaller than 200MB in size. The size threshold for skipping image scan can be changed by setting `SCAN_THRESHOLD` environment variable. For e.g. `export SCAN_THRESHOLD=500M` will exclude scan of images smaller than 500MB in size.
3. The tool will generate a file `work/scan-excluded-images.txt` containing a list of images that were NOT scanned.
4. The tool can be run with different container CLIs including `docker` and `podman`. Use the `-m` option to run the tool with a different container CLI (e.g. `-m podman`).
5. The output from the current execution of the tool is stored in `work/tool.log` file.

## Tool Output
Tool displays artifact name, repository name, tag and digest (hash-id) for images containing Oracle products. If node information file containing nodes to images mapping is provided (using `-k` option), the tool also displays node names.
```
Node(s)               Artifact      Respository                                    Tag     Digest
phx32822d1|phx3283857 weblogic.jar  phx.ocir.io/weblogick8s/domain-in-image-wdt-1  latest  sha256:a7b4b6608c
