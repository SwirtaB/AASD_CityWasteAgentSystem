# AASD_CityWasteAgentSystem

## Starting the project
This is a normal sbt project. You can compile code with `sbt compile`, run it with `sbt run`, and `sbt console` will start a Scala 2 REPL.

## Api
This project also contains a simple api to monitor and interact with the application.
Implemented requests:
- Read simulation status
```bash
curl -X GET --location "http://localhost:8080/status"
```
- Create new garbage collector
```bash
  curl -X POST --location "http://localhost:8080/collector/spawn" \
    -H "Content-Type: application/json" \
    -d "{\"location\":  {\"x\":  2, \"y\":  3}, \"speed\":  20, \"capacity\":  10}"```