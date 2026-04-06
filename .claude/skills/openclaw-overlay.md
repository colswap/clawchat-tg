# OpenClaw Bot Overlay Pattern

## Bot Detection
```java
// In ClawChatUtils.java
public static boolean isClawBotMessage(MessageObject msg) {
    // Check sender user_id against configured bot IDs
    return ClawChatConfig.getBotIds().contains(msg.getFromChatId());
}
```

## Rendering Pipeline
1. `ChatActivity` receives message → checks `isClawBotMessage()`
2. If true → route to `ClawMessageOverlay` instead of default rendering
3. `ClawMessageOverlay` delegates to:
   - `MarkdownRenderer` — enhanced markdown (tables, math, footnotes)
   - `CodeBlockView` — syntax highlighted code blocks
   - `ArtifactView` — WebView for HTML/interactive artifacts

## Adding New Bot Features
1. Add detection logic in `ClawChatUtils`
2. Create renderer in `com/clawchat/render/`
3. Hook in `ChatActivity` (if-branch, max 3 lines)
4. Test with non-bot messages to ensure no regression

## SubAgent Panel
- `SubagentPanelView` — shows active subagent status
- Triggered by `subagent.*` WebSocket events from GW
- Attached to ChatActivity as floating overlay (not in message cell)
