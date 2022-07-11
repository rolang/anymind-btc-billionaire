## BTC billionaire

It's time to get rich!  
This awesome API provides endpoints for everyone to top up your BTC wallet and retrieve hourly balance snapshots 
to be amazed and jealous about your growing balance.  
Minimum top-up amount is `0.00000001` BTC, there is no maximum limit though and no way for withdrawal.

## Architecture and technology
The service embraces CQRS and separates writes and reads to enable scalability of each.  
To accomplish this we are using Kafka as the event storage for writes/top-up operations and run a separate
process for collecting and storing the data in an optimized way for querying.

### Code structure
Following the concepts of `Domain Driven Design (DDD)` and `Onion (or Hexagonal) Architecture` we are trying to separate
the business logic from technical implementations.  
In the `domain` we are going to find data models, repositories and services describing the domain.  
In the `infrastructure` package we are going to find all the technical implementations with certain technologies 
which are not relevant to the domain like Protobuf/JSON serialization, HTTP server implementation, communication with Kafka and Postgres etc.  
The `config` package includes all the general configuration for the service.
The root package includes processes which can be run as independent executables.

### Testing
We have some test coverage with some automated unit and integration tests for most critical parts. All tests can be run by:
```shell
sbt test
```
For integration tests an embedded Kafka service and a dockerized Postgres version will be booted up automatically.  
For a coverage report a tool like [sbt-scoverage](https://github.com/scoverage/sbt-scoverage) can be integrated.

### Code inspection / quality
We use Scalafix, Scalafmt and Scala compiler configuration integrated via the SBT build tool to keep a high code quality standard.
A check on the code is performed on each build in CI/CD.

### Continuous Integration (CI) / Continuous Delivery (CD)
For CI/CD pipeline we have a GitHub actions configurations which currently just runs the code inspection and all automated tests on commits
or pull requests to master. For an actual release a configuration which publishes a new package/container can be added.

### Deployment and scalability
The service can be packaged and deployed to run in multiple independent instances with individual configuration behind a load balancer
connecting to a Kafka cluster or a Postgres database.  
To avoid downtimes or loss of data during deployment of new versions we can start the release by booting up new instances
which can be discovered by the load balancer performing a health check and take over incoming requests.  
After a successful boot of the new instance, we can initiate a shut-down of the instances with older version.  
This can be achieved in automated way for example by configuring the deployment in a managed cluster like Kubernetes over
a cloud provider like AWS or Google.  
  
For centralized services like Kafka and Postgres we can achieve scalability after reaching limits of single instances
by adding new brokers and replicate or mirror topics across brokers, in Postgres we can introduce clustering and/or
partitioning of tables and/or boot independent instances which will be hydrated by the data from the Kafka cluster.
This way we can also have deployments in different regions. The client would be able to choose
the region closest to him to keep response times as low as possible.

### Used technologies
 - [Akka](https://akka.io/docs) for streaming and communication with Kafka which includes GRPC for serialization of events
 - [Tapir](https://tapir.softwaremill.com) for description and documentation of the HTTP API
 - [ZIO](https://zio.dev) for a typ-safe, composable functional interface with good testability support
 - [HTTP4s](https://http4s.org) for the underlying HTTP API implementation described with tapir
 - [Circe](https://circe.github.io/circe) for json serialization
 - [Skunk](https://tpolecat.github.io/skunk) for non-blocking, JDBC free access to Postgres with a functional interface
 - [Kafka](https://kafka.apache.org) for distributed storage of events
 - [Postgres](https://www.postgresql.org) for organizing and optimizing data for querying

## TODOs
- add load tests
- add more logging
- add metrics
- add environment configuration documentation

## Running locally

Ensure docker with [docker-compose](https://docs.docker.com/compose/install/) is installed.
Start required services with:
```shell
docker-compose up -d
```

Start the REST API server with sbt by running:
```shell
sbt 'runMain dev.rolang.wallet.WalletApp'
```
View the api documentation at `http://localhost:8080/docs`.

Start events consumer in a separate process to update the dataset for querying:
```shell
sbt 'runMain dev.rolang.wallet.WalletEventsConsumer'
```

Run health check:
```shell
curl -i http://localhost:8080/health
```

Top-up wallet with BTC amount:
```shell
curl -i -d '{"datetime": "2022-07-07T03:00:00+01:00", "amount": 3.0}' http://localhost:8080/wallet/v1/top-up
curl -i -d '{"datetime": "2022-07-07T05:00:00+01:00", "amount": 5.0}' http://localhost:8080/wallet/v1/top-up
```

Retrieve hourly snapshots for given date-time range:
```shell
curl -i -d '{"from": "2022-07-07T00:00:00Z", "to": "2022-07-08T00:00:00Z"}' http://localhost:8080/wallet/v1/hourly-balance
```