cp ../scaling/scalingAction.sh .
chmod +x bin/webhook

docker rmi webhook:latest
docker build -t webhook:latest -f Dockerfile.webhook .
