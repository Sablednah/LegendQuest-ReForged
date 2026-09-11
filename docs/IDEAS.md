# Parked ideas

Things wanted but not built, written down at the moment they were thought of so
they are not lost to a chat log. Nothing here is committed work, and nothing
here has a release attached to it.

Each entry says what the idea *is*, and — where it is already known — what would
make it hard, so the next person picking it up starts from the real problem
rather than rediscovering it.

---

## A dice tray

**Wanted:** a screen with a pool of dice down the left that can be dragged into
a tray, a way to click a stat to roll against, and a Roll button that throws
what is in the tray and shows the result. Physical dice, handled, rather than a
command typed.

**Where it sits.** `/roll` already does the mechanics — `Dice` parses tabletop
notation, rolls it, and reports every die rather than just the total. The tray
is a *control surface* over that, exactly as the action buttons are over `/st`.
It should therefore drive the same code and produce the same broadcast, so a
tray roll and a typed roll are indistinguishable to everybody else at the table.

**Vanilla-first still holds.** The tray is a modded-client luxury. `/roll`
remains the whole feature for an unmodded client, and the tray must never be the
only way to reach something.

**The natural home is Standards' inventory-panel seam**, the one `CharacterPane`
already uses — a panel beside the inventory rather than a fullscreen GUI, so it
coexists with everything else that wants that space instead of fighting it.

### On the 3D dice, since that is the part that sounds impossible

It is feasible, and the way to make it feasible is to stop thinking of it as a
physics problem.

- **The server decides the result. The animation lands on it.** `Dice.roll`
  produces the number; the client then plays a tumble that finishes with that
  face up. Every digital dice roller works this way, and it is the only version
  that keeps the roll authoritative — which matters, because `/roll` is
  broadcast to the whole server and a client-decided number would be a client
  deciding what everyone sees.
- **A tumble is a canned animation, not a simulation.** Spin the model on two
  axes, decay the rotation, and end on the transform that shows the right face.
  No collision, no bounce, no rigid-body maths, and no risk of a die settling on
  an edge and hanging the screen.
- **Rendering the die is ordinary.** A d20 is twenty triangles; a GUI screen can
  draw arbitrary geometry through the matrix stack. The work is in the art and
  the easing, not the graphics.
- **Real physics is the trap.** Dice that actually bounce around a tray need a
  physics step, a fixed timestep, and a rule for what happens when one lands
  cocked — a great deal of work to reach the same number the server already
  chose. If it is ever wanted, it should be wanted for its own sake and costed
  as its own feature.
- **Keep it in one class.** 26.x reworked GUI rendering wholesale; anything that
  draws belongs where a version drop finds it in one file. Standards ported its
  entire client half by touching one file for that reason.

### Open questions

- Does the pool hold the dice a character *has*, or every die? A pool that is
  just a palette is simpler; a pool that means something is a new mechanic.
- Does a tray roll broadcast like `/roll` does, or only to a party? Broadcast is
  the honest default, since the point of rolling in the open is being seen.
- What does clicking a stat do to dice already in the tray — add the modifier,
  or replace the tray with `d20 + modifier`?
