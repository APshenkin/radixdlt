
docker build -f system-tests/docker/Dockerfile -t radix-system-test .
docker ps -a
docker create  --pid=host --privileged  -v /var/run/docker.sock:/var/run/docker.sock --network=host --cap-add=NET_ADMIN  --name=test-executor radix-system-test ./gradlew clean dockerSystemTests --tests "com.radixdlt.test.SlowNodeTests.Tests[*]"
#docker create  --pid=host --privileged  -v /var/run/docker.sock:/var/run/docker.sock --network=host --cap-add=NET_ADMIN --name=test-executor radix-system-test tail -f /dev/null
#docker network connect DID-test test-executor
docker start -a test-executor
