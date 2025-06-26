# Incognito

This project is an "original" idea of a chat application that allows
users to communicate without revealing their identities. It uses a 
a custom made E2EE (end-to-end encryption) to ensure chats privacy.
It also features a server that can be run locally or on remote servers.

## How to run

To run the client on the default server, you can use the following command:
```bash
gradle client:run
``` 

## Custom server/Local server

Otherwise if you want to run the client on a custom server or locally, you need to 
firstly change the constant variable in:
`client/src/main/java/org/incognito/Connection.java`:
```java
private String host = "YOUR_SERVER_IP";
```

To run the server on a machine(if changed ip in client to 
localhost, run the server on the local machine before running 
the app), by using the following command:
```bash
gradle server:run
```

Then run the client with the following command:
```bash
gradle client:run
``` 