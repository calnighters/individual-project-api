# Individual Project API

This API is the backend of my project DC3IPR. It interacts with files in AWS S3 to upload, view and modify files. The service also audits requests and can search on them later.

## Building

```shell

mvn clean install -DskipTests

docker build -f docker/build/Dockerfile -t <REGISTRY>/backend-api:<VERSION> .
docker push <REGISTRY>/backend-api:<VERSION>
```