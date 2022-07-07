## Btc billionaire

Start server
```shell
sbt run
```

Add btc
```shell
curl -i -d '{"datetime": "2022-07-07T00:00:00Z", "amount", 0.5}' http://localhost:8080/wallet/add
```