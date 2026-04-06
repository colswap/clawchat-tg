# Telegram Codebase Patterns

## Code Style (match existing)
- Java primary, Kotlin for new ClawChat code
- No dependency injection framework — static accessors everywhere
- `AccountInstance.getInstance(currentAccount).getXxxController()`
- UI: Custom Views with Canvas drawing (not XML layout inflation for performance-critical cells)
- Threading: `AndroidUtilities.runOnUIThread()`, `Utilities.globalQueue`

## Common Patterns
```java
// Getting a controller
MessagesController controller = MessagesController.getInstance(currentAccount);

// Running on UI thread
AndroidUtilities.runOnUIThread(() -> { ... });

// Density-independent pixels
int dp20 = AndroidUtilities.dp(20);

// Theme colors
int color = Theme.getColor(Theme.key_chat_messagePanelText);
```

## Danger Zones
- `ChatMessageCell.onDraw()` — monolithic Canvas draw, very fragile
- `TLRPC.java` — auto-generated, 100K+ lines
- `ConnectionsManager` — native JNI bridge
- `SecretChatHelper` — encryption, legal implications
