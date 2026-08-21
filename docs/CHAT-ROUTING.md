# Party chat capture, and the one thing it gets wrong

Capture routes a player's ordinary chat to their party until they switch it
off — `/pc` with no message toggles it, `/lq party capture on|off` is the
explicit form. It exists because typing `/pc` before every line is fine for a
remark and miserable for a conversation.

This file is about the seam it sits on, because that seam is shared with
Standards and one part of it is knowingly wrong.

## How it works

There are two delivery paths and exactly one is ever live.

**With Standards** — the normal case. `ChatSupport` registers a `ChatRouter`
as `legendquest:party` at priority 0, and Standards' `onChat` runs in order:
`Afk.onActivity`, mute gate, offer to routers by priority, default broadcast if
nobody claimed it. By the time `route` is called the sender is known not to be
muted and their AFK is already cleared, so party chat inherits both for free.
Registering also sets `PartyChat.routed`, which stands the listener below down.

**Without Standards** — `PartyChat.onChat` on `ServerChatEvent` at `HIGH`.
Standards is a soft dependency and capture is not allowed to need it. On a
plain NeoForge server there is no mute to respect and nothing to collide with.

Both call `PartyChat.claim`, and nothing else decides, so the two paths cannot
disagree about what capture means. Both are registered in the mod constructor,
long before a player exists to type, so there is no window where both are armed.

```java
public interface ChatRouter {
    String id();
    default int priority() { return 0; }
    /** True = claimed and delivered; Standards will not broadcast it. */
    boolean route(ServerPlayer sender, String message);
}
Chat.registerRouter(ChatRouter);
```

### Priority runs the opposite way to NameDecorator

**Higher wins here.** A router is offered the message before lower-priority
ones and the first to claim it ends the matter, so LegendQuest sits at 0 — the
weak end — and a staffchat or dedicated channel mod registering at 100 takes a
message ahead of the party. That is deliberate: a party is a mild claim.

In `NameDecorator`, higher priority means *nearer the name*. Neither is a
mistake. There, priority orders a list everyone appears in; here it decides who
is asked first, and a message has exactly one destination.

For the same reason routing is **first-claimant-wins** rather than additive: a
name can carry a rank and a faction tag without contradiction, but two routers
claiming one message is a conflict to resolve, not a merge — the rule the
economy provider already uses.

### What this fixed

The first implementation cancelled `ServerChatEvent` at `HIGH` to beat
Standards to it, which skipped the rest of their handler — the mute gate and
`Afk.onActivity` along with the delivery. **A muted player could talk to their
party.** That was a defect by Standards' own semantics, not a grey area:
`MessageCommands` gates `/msg` on mutes at two separate call sites, so a mute
is plainly meant to silence every channel.

Worth recording that `/pc` had that hole from the day it was written — it never
touched `ServerChatEvent` at all. Capture did not create the bypass; it turned
a command you had to remember into the default destination for everything you
type, which is what made it worth fixing rather than noting.

It could not be closed from this side: `Mutes` lives in
`com.sablednah.standards.neoforge`, not `...standards.api`, and reaching into
another mod's internals to enforce that mod's own rule is the arrangement that
breaks silently on their next refactor — with a moderation control as the thing
that breaks.

## `/ignore` does not apply inside party chat

A claimed message never reaches Standards' `deliver()`, so their `/ignore` has
no effect on party chat. That is deliberate, and as of 2026-08-21 it is a
ruling rather than an oversight — Sable's: *"if you want to /ignore a player in
party, boot them from the party."*

The reasoning holds up. A party is small, opt-in, and has a leader with a kick
command; ignoring someone you chose to team up with, while still sharing their
XP and their teleports, is a state that solves nothing. `/ignore` exists for
people you cannot get away from in public chat. In a party you can.

This is also why the seam takes the message rather than an audience — the
decision stays on LegendQuest's side of the line. If the ruling ever reverses,
the change is a call to Standards' ignore check from inside `route()`, and the
seam does not change either way.

## Unrelated fixes this work forced

Routing player text through the party line put *player-authored* strings
through machinery that had only ever seen our own lang templates:

- `Feedback.colored` was a blind `replace('&', '§')`. Correct for templates we
  wrote, wrong the moment a player typed "Tom & Jerry" and got "Tom § Jerry",
  the following space eaten as though it were a code. It now only translates an
  ampersand followed by a real format character.
- `PartyChat.strip` removes format codes from what a player typed, because
  `Lang.fmt` substitutes before `colored` runs — which made an untreated
  message a formatting injection. The obvious half is griefing: `&k` for
  unreadable text, a wall of `&l&n`. The worse half is impersonation — `&r`
  and a plausible prefix dresses your words up as somebody else's or as a
  server message. Server text keeps its codes because the server wrote it;
  player text does not, because the player did.
- The literal section sign is stripped too, not just the ampersand form. A
  client cannot type one, but this text also arrives from books, signs and
  command blocks, and "the client cannot send that" is the sort of assumption
  that quietly stops being true.

Standards had the identical blind replace and the identical ordering trap
across chat, `/me`, `/msg`, mail and moderation reasons. Both were found by
comparing notes rather than by either mod's tests, because a path that has only
ever run on text we wrote ourselves is a path that has never really run.

## One thing to know about signed chat

Standards only takes over delivery when a decorator actually returns something
**for that player, on that message**. A player with no class title and neutral
karma is undecorated, so their line is left entirely alone — they keep vanilla
signed chat and its hover cards, while a decorated player loses both.

So "everyone loses signed chat" is not what happens. Which players fall on
which side depends on where your class title and karma epithet bands start.
