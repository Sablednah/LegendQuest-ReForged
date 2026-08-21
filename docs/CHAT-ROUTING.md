# Party chat capture, and the one thing it gets wrong

Capture routes a player's ordinary chat to their party until they switch it
off — `/pc` with no message toggles it, `/lq party capture on|off` is the
explicit form. It exists because typing `/pc` before every line is fine for a
remark and miserable for a conversation.

This file is about the seam it sits on, because that seam is shared with
Standards and one part of it is knowingly wrong.

## How it works today

`PartyChat.onChat` subscribes to `ServerChatEvent` at `HIGH`, cancels the
event, and hands the text to `PartyChat.send` — the same path `/pc <message>`
uses, so both produce the same line, the same spy copy, and the same log entry.

`HIGH` is not a preference. Standards' `onChat` sits at `NORMAL`, and once any
registered decorator has something to say about the speaker it cancels the
event and delivers the formatted line itself. After that, redirecting is too
late. Getting in first is the only lever the event model offers.

## The gap

Cancelling ahead of Standards skips the rest of Standards' handler:

| What is skipped | Consequence |
|---|---|
| `Mutes` gate | **A muted player can talk to their party.** |
| `Afk.onActivity` | They stay flagged AFK while chatting. |

The mute one is the real defect, and it is a defect by Standards' own
semantics rather than a matter of opinion: `MessageCommands` gates `/msg` on
mutes at two separate call sites, so a mute is plainly meant to silence every
channel, not just public chat.

Two things worth being precise about:

- **Capture did not create this.** `/pc` has never gone through
  `ServerChatEvent` at all, so party chat has always escaped mutes. Capture
  makes the hole far easier to reach — it turns a command you must remember
  into the default destination for everything you type.
- **It cannot be closed from this side.** `Mutes` lives in
  `com.sablednah.standards.neoforge`, not in `...standards.api`. Reaching into
  another mod's internals to enforce its own rule is exactly the arrangement
  that breaks silently on their next refactor, and the thing it would break is
  a moderation control.

## The fix, agreed in principle

Standards proposed and prefers a routing seam. Their shape, not ours:

```java
public interface ChatRouter {
    String id();
    /** True = claimed and delivered; Standards will not broadcast it. */
    boolean route(ServerPlayer sender, String message);
}
Chat.registerRouter(ChatRouter);   // plus priority; first claimant wins
```

Standards' `onChat` then runs unchanged and in order — AFK, mute gate, offer to
routers by priority, default broadcast if nobody claimed it. LegendQuest
renders its own `[Party]` line and spy copy exactly as it does now, and deletes
`PartyChat.onChat` entirely.

Two details from that exchange worth keeping:

- **It returns a boolean, not an audience.** A party line *should* look
  different from public chat, so handing Standards a Component would be
  Standards holding a value it cannot improve on.
- **First-claimant-wins, unlike `NameDecorator`.** Decorators are additive
  because a name can carry a rank and a faction tag without contradiction. A
  message goes to exactly one audience, so two routers claiming it is a
  conflict to resolve, not a merge — the same rule the economy provider uses.

It also leaves `/ignore` where it belongs: LegendQuest decides whether an
ignore applies to party chat, because that is an open design question, and a
seam should not pre-empt it.

**Not built.** Sable decides whether Standards grows this, and until he does,
the `HIGH`-priority listener above stays. It is deliberately one method behind
one call so that swapping it costs almost nothing.

## Unrelated fixes this work forced

Routing player text through the party line put *player-authored* strings
through machinery that had only ever seen our own lang templates:

- `Feedback.colored` was a blind `replace('&', '§')`. Correct for templates we
  wrote, wrong the moment a player typed "Tom & Jerry" and got "Tom § Jerry",
  the following space eaten as though it were a code. It now only translates an
  ampersand followed by a real format character.
- `PartyChat.strip` removes format codes from what a player typed, because
  `Lang.fmt` substitutes before `colored` runs — which made an untreated
  message a formatting injection, `&k` and all. Server text keeps its codes
  because the server wrote it; player text does not, because the player did.

## One thing to know about signed chat

Standards only takes over delivery when a decorator actually returns something
**for that player, on that message**. A player with no class title and neutral
karma is undecorated, so their line is left entirely alone — they keep vanilla
signed chat and its hover cards, while a decorated player loses both.

So "everyone loses signed chat" is not what happens. Which players fall on
which side depends on where your class title and karma epithet bands start.
