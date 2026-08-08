# 09 — The Network Map: stacks, expansion, and arrangement

**Status: [PROPOSAL].** Nothing in §3–§7 is built. §1 and §2 describe what exists today and are the
constraints everything else is written against.

The map is the client's only spatial surface. Every other window is a table, a console or a form; this
one is the single place a player reasons about *shape* — what is next to what, how far out something
is, where a route goes. It is also the surface that degrades fastest as a world fills up, because a
character grid has no zoom.

---

## 1. What exists, and the geometry everything must fit

`view/NetMapView` hosts three tabs (GRAPH, LIST, FOLDERS). The graph is three classes:

| | |
|---|---|
| `netmap/NetLayout` | assigns every sighting a **layer** (column) and **row**. Pure, no JavaFX. |
| `netmap/NetCanvas` | paints the character grid — node boxes, edges, the packet dot |
| `netmap/NetGraph` | the thin JavaFX layer: focus, keyboard, accessible text, hit-testing |

### 1.1 The column arithmetic, which is unforgiving

```
LAYER_COLS = NET_LATERAL_COLS(10) + NET_NODE_COLS(18) = 28
PITCH      = LAYER_COLS + NET_GAP_COLS(3)             = 31

layer L:  [ lateral 0..9 | node box 10..27 ] [ gap 28..30 ] [ layer L+1 … ]
```

A node box is **18 × `NET_NODE_LINES`(5)** character cells. A layer may hold up to
`NET_MAX_ROWS`(60) rows before it clamps and the header gains a `+N MORE` suffix.

⚠ **That clamp is the problem this document exists to replace.** A layer wider than the clamp does
not shrink, scroll or summarise — it draws the first *N* and puts the remainder in a header count.
The machines past the cut are on the map's data and absent from its picture, which is the one thing a
map may not do.

### 1.2 Rules the renderer already holds, and which §3 may not break

- **Columns are hop distance from the player's own rig** (`Sighting.hopsFromRig`), so the frame does
  not move when the player repositions. Changed 2026-08-07; see `design/15` §3.
- **One barycentre pass, never iterated**, because the packet animation repaints on a timer and the
  layout must be *identical on every repaint*.
- **Edges merge, they never overwrite** — `AsciiCanvas.junction` ORs direction bits, so two edges
  crossing produce `┼`. Cells written by a box, header, stub or arrowhead are `occupied` and refuse
  routing.
- **Forward and lateral edges are told apart by shape** — sharp junctions and a `→` for a hop,
  rounded arcs for a same-layer link. `NetGraphTest.lateralEdgesUseArcs` holds it.
- **The vantage carries the only heavy frame on the map.**

### 1.3 ⚠ Two open defects in the current renderer

**The ten-column space.** A forward edge runs in the 3-column gap and puts its arrowhead at the
gap's last column — but the next layer's node box does not start for another ten columns, because
the lateral strip sits between them. Every forward arrow points into blank space. Extending the run
across the strip was tried and reverted: it routes through the two columns lateral edges use and
merges their arcs into junctions, destroying §1.2's shape distinction. **The fix has to route around
those two columns**, and it should land before §3, because stacks add edges rather than removing them.

**No render harness can see any of this.** `DeckSnapshot`'s fixture holds one host and runs no sweep,
so no screenshot this project can produce contains a single edge. That is the prerequisite for all of
§3–§6: without it, this is tuned blind, which is how the lane-fit bug survived (two of three routing
lanes turned outside the gap and rendered as stubs, silently, for as long as the token had been wrong).

---

## 2. The pressure the design has to relieve

Three things make the map unreadable, and they arrive in this order:

1. **Fan-out.** A gateway or bridge links to everything on its server. One parent with fifteen
   children is fifteen rows in the next column and fifteen edges through a 3-column gap.
2. **Depth.** Every bridge crossed adds a column. The map is already `1400px` at three columns.
3. **Density.** `docs/design` puts up to fifty machines on a server.

⚠ These compound: the wide layer is *also* the one whose edges all originate at one node, so the
routing gap saturates at exactly the row range that is hardest to read.

---

## 3. Stacks — **[PROPOSAL]**

> A machine whose onward links exceed a threshold renders them as a single **stack** — one node-sized
> box carrying a count — instead of one box per machine. Clicking the stack expands it in place.

### 3.1 ⚠ A STACK COUNTS ONLY MACHINES THE PLAYER HAS FOUND

This is the invariant most easily broken here and it is worth stating before anything else.
`NetRules` is explicit:

> *"Undiscovered hosts do not exist in `knownNodes`, and the map draws nothing where they are. **No
> placeholder, no count**, no 'three contacts nearby'."*

A stack is a **folding of things already on the map**, never a hint about things that are not. A
stack reading `7` means seven discovered machines are collapsed behind it. It must never mean "this
node has seven links, of which you have found two" — that would publish a count of undiscovered
machines on the one surface that rule was written for, and it would make the map a cheaper sweep.

⚠ The bridge peer count (`PortScanTarget.PEERS`) is the *sanctioned* exception and stays where it is:
a port-scan finding on a machine the player has scanned, shown in its report, not on the graph.

### 3.2 What groups

**By parent, in the next layer.** A stack belongs to exactly one node in layer *k* and holds its
children in layer *k+1*. Grouping by parent is what makes the count answer a question the player is
actually asking — *how much is behind this machine* — and it is the only grouping under which the
collapsed edge is a single honest edge rather than a bundle.

Rejected alternatives, with reasons:

- **By layer** (collapse a wide column's tail) — the count then answers nothing; it is "some machines
  that happened to sort last", and which ones is an artefact of the row ordering.
- **By kind or server** — cuts across the link graph, so the stack's single edge would be a lie. It is
  also the Passive Sniffer's product (`design/07` §1) leaking into the map for free.

### 3.3 When it collapses

Stack when a parent's children in the next layer exceed `NET_STACK_THRESHOLD` (**proposed 4**).

⚠ **A threshold, not always-on.** Two or three children are more legible drawn than counted, and a
stack that appeared at two would make the common case require a click to see anything.

⚠ **Nothing is ever hidden without a mark.** A collapsed group is always visibly a stack — see §5 —
and the count is always exact. The current `+N MORE` header, which is the only thing that hides
machines today, is deleted by this feature rather than kept alongside it.

### 3.4 Expansion

- Click, `Enter`, or `→` on a focused stack expands it. `←` or a second click collapses.
- Expanded members occupy rows **inserted at the stack's own row**, pushing subsequent rows down.

⚠ **The insertion rule is the important half, and the obvious implementation gets it wrong.**
Re-running the barycentre pass with the members present would re-sort the whole layer, so expanding
one stack moves unrelated machines the player was looking at. That is the same defect as the vantage
re-rooting the graph, one level down: **the frame must not move when the player explores it.** Rows
above the stack keep their row index; rows below shift by `members - 1`.

⚠ **Expansion is not recursive by default.** An expanded member that is itself a stack parent renders
as a stack. Expanding everything at once is the state the feature exists to avoid.

### 3.5 State

Expansion is **client-side, per window, session-scoped** — a `Set<String>` of expanded stack ids
inside `NetMapView`, not in the save and not in `deskLayout`.

- It is not game state; the engine does not know a stack exists.
- It is exploration, not arrangement. A window reopened is a fresh look.

⚠ Open (**NM-1**): should it survive a window close, the way window *size* now does? The argument for
is that a player mid-exploration who closes the map loses their place. The argument against is that
expansion state keyed by stack id goes stale the moment a sweep changes the grouping, and a restored
expansion that no longer matches the graph is worse than none.

---

## 4. Arrangement — **[PROPOSAL]**

### 4.1 What must not change

The layout is **one pass, never iterated to convergence**, because it must be byte-identical on every
repaint — the packet dot repaints on a timer, and a layout that settled differently would make the
whole graph shimmer. Any improvement must be a **fixed** number of passes.

### 4.2 Proposed: two-pass barycentre with a stable tiebreak

Today: one forward pass, each layer sorted by the mean row of already-placed neighbours one layer
back, ties broken by address.

Proposed: **forward pass, then one backward pass**, then stop. A backward pass lets a node's *children*
influence its row, which is what removes the characteristic failure of a single forward pass — a
parent sitting at the top of its column with all its children at the bottom, dragging one long edge
diagonally across every other edge in the gap.

⚠ Two passes, not "until stable". Deterministic by construction, bounded cost, and it captures most of
the available crossing reduction; iterating buys diminishing returns for an unbounded and
repaint-visible cost.

⚠ Ties still break on **address**, never on anything derived. A tiebreak on tier, kind or name would
make the row order leak a recon finding — and would reshuffle the map when a scan lands.

### 4.3 Stacks change the arithmetic in the layout's favour

A stack is one row and one edge. So the layer widths this algorithm has to arrange are bounded by the
number of *parents*, not the number of machines — which is what makes a two-pass heuristic sufficient
rather than merely better.

---

## 5. Rendering — **[PROPOSAL]**

A stack is a node box drawn with a **stacked-plate motif**: the box, plus one or two offset rules
behind its top and right edges, suggesting sheets under it.

```
   ┌────────────┐┐┐        ┌────────────┐
   │ ▓▓ ×7      │││        │ ▓▓ ---- [#]│
   └────────────┘┘┘        └────────────┘
      a stack of 7            one machine
```

- **The count is inside the box**, as `×7`, in the same cell row as the kind marker.
- ⚠ **No heavy frame.** §1.2 reserves it for the vantage. The stack reads as a stack by its *offset
  plates*, which is a shape nothing else on this map uses.
- ⚠ **Every glyph must be in a bundled font.** `GlyphCoverageTest` fails the build on anything
  outside them, and it has already rejected four block elements and `U+26A0` in this project. The
  plates are box-drawing (`┐│┘`), which `NetCanvas` already draws; `×` is Latin-1.
- ⚠ **Not amber.** §2.1 spends amber on cycles doing work and income. A count of machines is neither.

### 5.1 The collapsed edge

One edge from parent to stack, with the arrowhead the same `→` a single hop uses. ⚠ **Not thickened
and not multiplied** — a bundle of seven edges into one box is precisely the tangle stacking exists
to remove, and a heavier line would collide with §1.2's weight reservation.

---

## 6. Accessibility — **[PROPOSAL]**

Held against [`07-accessibility.md`](07-accessibility.md).

- **Keyboard-complete.** `→` expands, `←` collapses, `Tab`/arrows traverse. A stack that could only
  be opened with a pointer would put content behind a mouse.
- **Announced as what it is.** `NetGraph`'s accessible text for a stack: *"stack of seven machines
  behind 10.0.0.2, collapsed. Right arrow to expand."* ⚠ The count and the state both go in the
  text — §4.4 requires the state survive greyscale and a screen reader, and the offset plates are a
  shape a reader cannot see.
- **The expanded/collapsed state is never carried by colour alone.**

---

## 7. Sequencing

1. **Give `DeckSnapshot` a swept world.** Nothing below can be seen without it. (§1.3)
2. **Fix the ten-column space.** Route forward edges around the lateral columns. Stacks add edges;
   fixing routing afterwards means doing it twice. (§1.3)
3. **Stacks, collapsed only** — grouping, threshold, rendering, the collapsed edge. No expansion.
   This alone deletes the `+N MORE` clamp and is independently shippable.
4. **Expansion** — state, insertion rule, keyboard, accessible text.
5. **Two-pass barycentre.** Last, because §4.3 means its job is much smaller once stacks exist.

---

## 8. Open questions

- **NM-1** — does expansion state survive a window close? (§3.5)
- **NM-2** — `NET_STACK_THRESHOLD` = 4 is proposed, not measured. It should be set against real
  generated worlds once §7.1 makes them visible.
- **NM-3** — what happens when an expanded stack's membership changes under the player because a
  sweep landed? Recommended: the stack stays expanded and the new machine appears in it, because the
  alternative is the map collapsing under someone mid-read.
- **NM-4** — do stacks apply in the LIST tab? Recommended no: a list is already linear and scrollable,
  and the pressure this relieves is spatial.
