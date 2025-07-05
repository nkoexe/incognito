# Incognito

CS13 Group members:
- Lorenzo Donadello (@w8rst)
- Tahsan Jamal Jilan (@tahsanJilan)
- Niccolò Ragazzi (@nkoexe)


This project's aim is to be a secure chat application that allows
users to communicate without revealing their identities. It utilizes a 
custom-made E2EE (end-to-end encryption) implementation to ensure message privacy.
It also features a server that can be run locally or on remote machines.

## How to run

To run the client on the default hosted server, you can use the following command:
```bash
gradle client:run
```

### Running on local server

Otherwise, if you want to run the client on a custom server or locally, 
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


## Important Programming Techniques
These are some of the key programming techniques and features used in this project:
### Asynchronous Programming
The client uses asynchronous programming to handle message sending and receiving without blocking the user interface. 
This is achieved through the use of separate threads for reading and writing messages, allowing for a responsive chat experience.
### End-to-End Encryption (E2EE)
The application implements a custom E2EE protocol to ensure that messages are encrypted on the sender's side and decrypted only on the receiver's side.
This is done using a combination of symmetric and asymmetric encryption techniques, in specific:
- AES (Advanced Encryption Standard) for symmetric encryption of messages
- RSA (Rivest-Shamir-Adleman) for asymmetric encryption of the AES key
Also a encryption test is performed after manual key exchange to ensure that the encryption and decryption processes work correctly, which status is saved in the logs. (present in MenuPage lines 357-368)
### QR Code Generation and Scanning
The application includes functionality for generating and scanning QR codes to facilitate secure key exchange between users.
It uses the ZXing library for QR code generation and scanning, allowing users to easily share encryption keys.


## Experience and Challenges
👤 Niccoló Ragazzi – 
Handled the full setup of the project including Gradle configuration, directory structuring, and Git initialization. Implemented core client-server socket communication with threading, message queues, and logging. Developed key features like automatic key exchange, lobby system, and username validation. Led refactoring efforts for cleaner, consistent code and managed deployment and ongoing maintenance.

👤 Lorenzo Donadello – 
Bootstrapped the project with initial repository structure and foundational Java classes. Implemented client-side components such as the read/write thread model, ClientHandler, and E2EE encryption. Led development of a modern GUI, merged feature branches, and resolved critical bugs including username reuse, connection failures, and manual key exchange issues. Contributed to code cleanup, file removals, and built chat session logging tools.

Tahsan J Jilan –
Enhanced application stability by implementing comprehensive error handling on both client and server sides. Added user-friendly features like a “leave chat” button and improved username case sensitivity to prevent conflicts. Regularly fixed minor bugs and refined error prompts, ensuring smoother and more reliable user interactions.