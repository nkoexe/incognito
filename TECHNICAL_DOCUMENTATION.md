# Incognito Chat Application - Technical Documentation

## Overview
The Incognito Chat Application is a secure peer-to-peer chat system with both automatic and manual key exchange capabilities. It features a modern GUI with Apple/macOS-inspired design and robust end-to-end encryption.

## Architecture

### Client-Server Communication Flow

#### 1. Initial Connection and Authentication
**Location:** `UserSelectionPage.java` lines 268-390

**Process:**
1. Client connects to server on port 58239
2. Client sends `USERLIST:<username>` message
3. Server responds with:
   - `USERNAME_ACCEPTED` - Username is available
   - `USERNAME_TAKEN` - Username already in use (client must retry)
4. Server broadcasts updated user list to all connected clients

**Code Flow:**
```
UserSelectionPage.connectToServerAndLoadUsers() →
UserSelectionPage.initializeServerCommunication() →
Server: ClientHandler.run() →
Server: Connection.registerUser()
```

#### 2. Automatic Key Exchange Flow
**Location:** `AutoKeyExchange.java` lines 17-185

**Process:**
1. User A selects User B from contact list
2. Client A initiates key exchange: `INITIATE_EXCHANGE` message
3. Client B responds with `PUBLIC_KEY_OFFER` containing RSA public key
4. Client A generates AES session key, encrypts it with B's public key, sends `SESSION_KEY_OFFER`
5. Client B decrypts session key, sends `EXCHANGE_COMPLETE` confirmation
6. Both clients enable chat interface with "✅ Chat is ready!" message

**Code Flow:**
```
GUITest.initializeConnectionWithUsername() →
AutoKeyExchange.performKeyExchange() →
ReadThread.handleIncomingKeyExchange() →
GUITest.enableChatInterface()
```

#### 3. Manual Key Exchange Flow
**Location:** `MenuPage.java` lines 1-362

**Process:**
1. Users exchange QR codes containing RSA public keys
2. Host generates AES session key, encrypts it with guest's public key
3. Guest imports encrypted AES key file, decrypts with private key
4. Both users proceed to chat with pre-shared session key

**Code Flow:**
```
MenuPage.initComponents() →
MenuPage.generateAndShareAESKey() / MenuPage.importEncryptedAESKey() →
MainApplication.userSelectionListener.onKeysExchangedAndProceed()
```

#### 4. Message Exchange
**Location:** `WriteThread.java` lines 1-119, `ReadThread.java` lines 1-202

**Process:**
1. User types message and presses Send
2. Message encrypted with AES-GCM
3. Encrypted message sent as `ChatMessage` object to server
4. Server forwards to appropriate recipient
5. Recipient decrypts and displays message

**Code Flow:**
```
GUITest.sendMessage() →
WriteThread.sendMessage() →
Server: Connection.forwardPrivateMessage() →
ReadThread.run() → GUITest.appendMessage()
```

### Server-Side Architecture

#### Connection Management
**Location:** `server/Connection.java` lines 1-400+

**Key Components:**
- `ExecutorService clientHandlerPool` - Manages client connections
- `Map<String, ClientHandler> usersClientMap` - Username to client mapping
- `Map<String, PrivateChatSession> activePrivateSessions` - Active chat sessions

#### Message Routing
**Location:** `server/ClientHandler.java` lines 1-200+

**Process:**
1. Each client gets dedicated `ClientHandler` thread
2. Messages routed based on type:
   - `USERLIST:` → User registration
   - `ChatMessage` → Private message forwarding
   - `KeyExchangeMessage` → Key exchange forwarding
   - `DISCONNECT:` → User removal

### Security Implementation

#### Encryption Details
**Location:** `crypto/CryptoManager.java` lines 1-165

**RSA Key Exchange:**
- 2048-bit RSA keys for public key cryptography
- Used to securely exchange AES session keys

**AES Encryption:**
- AES-256-GCM for message encryption
- 12-byte random IV per message
- Authenticated encryption prevents tampering

**Key Management:**
- Fresh AES session key per chat session
- Keys reset when starting new chat (`CryptoManager.resetSession()`)
- Key exchange tracking cleaned up on disconnect

#### Security Features
1. **Perfect Forward Secrecy** - New session keys for each chat
2. **Message Authentication** - GCM mode prevents tampering
3. **Key Isolation** - Each user pair has unique session key
4. **Secure Random** - Cryptographically secure random number generation

## Application Functionalities

### 1. User Authentication and Management
**Implementation:** `UserSelectionPage.java` lines 268-516
- Username registration with uniqueness validation
- Real-time user list updates
- Connection status monitoring
- Automatic reconnection handling

### 2. Contact Selection and Chat Initiation
**Implementation:** `UserSelectionPage.java` lines 170-190
- Interactive user list with modern styling
- Double-click to start chat
- Refresh functionality for updated user lists
- Exit and manual key exchange options

### 3. Automatic Key Exchange
**Implementation:** `AutoKeyExchange.java` lines 17-185
- **Key tracking:** Lines 15 (`activeExchanges` set)
- **Exchange initiation:** Lines 22-56 (`performKeyExchange()`)
- **Message handling:** Lines 58-175 (`handleIncomingKeyExchange()`)
- **Cleanup:** Lines 177-185 (`cleanupExchange()`)

### 4. Manual Key Exchange (QR Code)
**Implementation:** `MenuPage.java` lines 1-362
- **QR Code generation:** Lines 200-220 (`updateQRCodeImage()`)
- **Key sharing:** Lines 250-290 (`generateAndShareAESKey()`)
- **Key import:** Lines 320-360 (`importEncryptedAESKey()`)
- **File operations:** Lines 220-250 (`saveMyQRCodeToFile()`, `scanContactQRCodeFromFile()`)

### 5. Secure Messaging
**Implementation:** `GUITest.java` lines 600-630 (`sendMessage()`)
- **Message encryption:** `WriteThread.java` lines 40-70
- **Message decryption:** `ReadThread.java` lines 80-100
- **Real-time display:** `GUITest.java` lines 535-540 (`appendMessage()`)

### 6. Chat Interface Management
**Implementation:** `GUITest.java` lines 57-190 (constructor)
- **Modern UI:** Lines 75-140 (chat area, user list, input panel)
- **Theme application:** Uses `ModernTheme.java` throughout
- **Window management:** Lines 182-190 (exit handling)
- **Session state:** Lines 700-710 (`enableChatInterface()`)

### 7. Connection Management
**Implementation:** `GUITest.java` lines 545-600 (`disconnect()`)
- **Resource cleanup:** Lines 550-570 (threads, connections)
- **State reset:** Lines 575-590 (UI elements)
- **Key exchange cleanup:** Lines 552-556 (exchange tracking)

### 8. Error Handling and Recovery
**Implementation:** Distributed across multiple files
- **Connection errors:** `UserSelectionPage.java` lines 300-320
- **Crypto errors:** `CryptoManager.java` exception handling
- **Thread safety:** `SwingUtilities.invokeLater()` usage throughout
- **Graceful degradation:** Fallback mechanisms in all major operations

### 9. Theme and UI Consistency
**Implementation:** `ModernTheme.java` lines 1-200+
- **Color scheme:** Lines 25-45 (Apple-inspired colors)
- **Typography:** Lines 50-70 (SF Pro fonts with fallbacks)
- **Component styling:** Lines 100-150 (buttons, panels, labels)
- **Consistent spacing:** Lines 75-85 (layout constants)

### 10. Return to Main Menu
**Implementation:** `GUITest.java` lines 715-747 (`returnToMainMenu()`)
- **Confirmation dialog:** Lines 720-725
- **Resource cleanup:** Line 730 (`disconnect()`)
- **State preservation:** Lines 735-745 (username preservation)

## Message Flow Summary

### Client → Server Messages
1. `USERLIST:<username>` - User registration
2. `REQUEST_USERLIST` - Request updated user list
3. `DISCONNECT:<username>` - User disconnection
4. `ChatMessage` object - Encrypted chat message
5. `KeyExchangeMessage` object - Key exchange data

### Server → Client Messages
1. `USERNAME_ACCEPTED` / `USERNAME_TAKEN` - Registration response
2. `USERLIST:<comma-separated-users>` - Updated user list
3. `CONNECT:<username>` - User joined notification
4. `DISCONNECT:<username>` - User left notification
5. `PEER_CONNECTED:<username>:<sessionId>` - Chat partner connected
6. `ChatMessage` object - Forwarded encrypted message
7. `KeyExchangeMessage` object - Forwarded key exchange data

## Key Files and Responsibilities

### Client-Side Core Files
- **`MainApplication.java`** - Application entry point and user flow coordination
- **`GUITest.java`** - Main chat window and session management
- **`UserSelectionPage.java`** - Contact selection and server connection
- **`MenuPage.java`** - Manual key exchange interface
- **`AutoKeyExchange.java`** - Automatic key exchange protocol
- **`ReadThread.java`** - Incoming message handling
- **`WriteThread.java`** - Outgoing message handling
- **`Connection.java`** - Network connection management

### Cryptography Files
- **`CryptoManager.java`** - Core encryption/decryption operations
- **`KeyExchangeUtil.java`** - Key exchange utilities
- **`QRUtil.java`** - QR code generation and parsing

### UI and Theme Files
- **`ModernTheme.java`** - Centralized styling and theming

### Server-Side Files
- **`server/Connection.java`** - Server main class and client management
- **`server/ClientHandler.java`** - Individual client connection handling
- **`server/PrivateChatSession.java`** - Chat session management

### Shared Components
- **`shared/ChatMessage.java`** - Message data structure
- **`shared/KeyExchangeMessage.java`** - Key exchange data structure
- **`shared/LocalLogger.java`** - Logging utilities
- **`shared/ChatSessionLogger.java`** - Session-specific logging

## Recent Bug Fixes

### Issue: Chat Rejoin Bug
**Problem:** After leaving and rejoining a chat, users couldn't send messages and didn't see "chat is ready" message.

**Root Cause:** `AutoKeyExchange.activeExchanges` static set retained exchange keys from previous sessions, blocking new key exchanges.

**Solution:** 
1. **AutoKeyExchange.java lines 30-35:** Modified to clear and restart exchanges for rejoining scenarios
2. **AutoKeyExchange.java lines 177-185:** Added `cleanupExchange()` method
3. **GUITest.java lines 552-556:** Call cleanup on disconnect
4. **GUITest.java line 206:** Track chat partner for cleanup
5. **CryptoManager.java lines 37-43:** Added `resetSession()` to clear stale keys

## Configuration and Dependencies

### Build Configuration
**File:** `client/build.gradle.kts`
- **Java 21** target
- **FlatLaf** for modern UI (lines 15-16)
- **ZXing** for QR codes (lines 13-14)
- **JSON** for data serialization (line 12)

### Network Configuration
- **Server Port:** 58239 (defined in `Connection.java`)
- **Host:** 127.0.0.1 (localhost for testing)

This documentation provides a complete technical overview of the Incognito Chat application, including all major functionalities, communication flows, and implementation details with specific code line references for easy navigation and future modifications.
