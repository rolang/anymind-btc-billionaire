## Btc billionaire

Start required services with:
```shell
docker-compose up -d
```

Start http api server
```shell
sbt 'runMain dev.rolang.wallet.WalletApp'
```

Start events consumer to update the dataset for querying:
```shell
sbt 'runMain dev.rolang.wallet.WalletEventsConsumer'
```

Top-up wallet with btc amount
```shell
curl -i -d '{"datetime": "2022-07-07T03:00:00+01:00", "amount": 3.0}' http://localhost:8080/wallet/v1/top-up
```

Retrieve hourly snapshots for given date-time range
```shell
curl -i -d '{"from": "2022-07-07T00:00:00Z", "to": "2022-07-08T00:00:00Z"}' http://localhost:8080/wallet/v1/hourly-balance
```