./gradlew clean shadowJar --no-daemon
docker buildx build --platform linux/amd64 -t myserver:latest --load .
docker tag myserver:latest ghcr.io/group-h-security/server:latest
docker push ghcr.io/group-h-security/server:latest
