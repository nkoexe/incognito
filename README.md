# Incognito

CS13 Group members:
- Lorenzo Donadello (@w8rst)
- Tahsan Jamal Jilan (@tahsanJilan)
- Niccolò Ragazzi (@nkoexe)


This project's aim is to be a secure chat application that allows
users to communicate without revealing their identities. It uses
custom made E2EE (end-to-end encryption) implementation to ensure message privacy.
It also features a server that can be run locally or on remote machines.

## How to run

To run the client on the default hosted server, you can use the following command:
```bash
gradle client:run
```

### Running on local server

Otherwise if you want to run the client on a custom server or locally, 
first change the constant variable in:
`client/src/main/java/org/incognito/Connection.java`:
```java
private String host = "YOUR_SERVER"; // e.g. "localhost"
```

Run the server with:
```bash
gradle server:run
```

Then run the client with:
```bash
gradle client:run
``` 

## Brief User Guide

After running the client, you will be prompted to enter a username. The username is temporary, it will be discarded after the client is closed.

The server will validate the username chosen, after which you will be able to select the user you want to chat with. Optionally, you can press the "Manual Key Exchange" button to follow manually the encryption key exchange procedure.

Once both users have accessed the chat, you can start sending messages. The application will notify if a user disconnects, or if the connection is lost.

Closing the client or pressing the "Exit Chat" button will return you to the user "lobby", where you can select another user to chat with.



## Directories Overview

### Client Side
- MainApplication.java: Entry point with app initialization
- User interface components:
  - UserSelectionPage.java: first window for user selection, connection to the server with username validation
  - MenuPage.java: Manual key exchange interface
  - UI.java: Main chat interface with user list, signal handling,
  - Theme directory: Custom colors and style
- Crypto package: Encryption/decryption handler, key management, QR code generation and scanning utilities
- Network components:
  - Connection.java: TCP/IP Socket connection management
  - ReadThread/WriteThread: Asynchronous message handling, including message sending and receiving

### Server Side
- Server.java: Main entry point for server init
- Connection.java: Connection creation, username validation, general broadcasting, client creation
- ClientHandler.java: Individual messages handling
