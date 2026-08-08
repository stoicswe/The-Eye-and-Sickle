# 09 — The Network Map: stacks, expansion, and arrangement

**Status: BUILT 2026-08-08.** All five steps of §7 shipped. What follows is the design as written,
with the decisions the implementation had to make marked **⚠ AS BUILT** where it went beyond or
against the proposal. §1's two defects are fixed; §1.1's column arithmetic has changed and the section
records both shapes.

⚠ **Two things in here are now measurements rather than proposals, and one of them inverts §2.** With
`DeckSnapshot -Ddeck.reposition=N -Ddeck.netdump=1` against real generated worlds, layers run **1–5
machines wide** and maps run **4–10 columns deep**. Fan-out — §2's first pressure — does not occur at
reachable depth, and **depth does**. Stacks are built, correct and currently dormant; the map's actual
unreadability is horizontal. See **NM-2** and **NM-5**.

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

#### ⚠ AS BUILT — the clamp is gone, and the corridor is thirteen columns

`NET_MAX_ROWS` is **deleted**, along with `+N MORE`, `NetLayout.CLAMP_MARK`,
`Result.overflowInLastVisibleLayer`, `NetCanvas.trimSeparators` and the `maxRows` parameter that ran
through every signature in the package. A layer nothing folds simply gets tall and the panel scrolls.
`NetLayoutTest.nothingIsEverDropped` holds the replacement rule directly: over every fixture, *drawn +
folded == sightings*.

The routing region is named now, because it was never the gap alone:

```
CORRIDOR_COLS = NET_GAP_COLS(3) + NET_LATERAL_COLS(10)          = 13
BUS_COL       = CORRIDOR_COLS - NET_LATERAL_BUS_COLS(2)         = 11   ← the lateral channel
ARROW_COL     = CORRIDOR_COLS - 1                               = 12   ← against the next box

corridor r:  0 1 2 | 3 4 5 6 7 8 9 10 | 11 | 12 | node box
             gap   |   forward approach       bus  arrow/stub
turn lanes:  r ∈ {1,3,5,7,9}  — odd, inside the run, never the bus
```

⚠ **`NET_MAX_ROWS` also keyed `NetGraph`'s slot map** as `layer * NET_MAX_ROWS + row`, which was only
ever correct while no column could exceed the clamp. Packed into a `long` now; left as it was, two
slots would have collided silently and drawn one machine's cell where another belongs.

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

### 1.3 ⚠ Two open defects in the current renderer — **both fixed 2026-08-08**

**The ten-column space.** A forward edge runs in the 3-column gap and puts its arrowhead at the
gap's last column — but the next layer's node box does not start for another ten columns, because
the lateral strip sits between them. Every forward arrow points into blank space. Extending the run
across the strip was tried and reverted: it routes through the two columns lateral edges use and
merges their arcs into junctions, destroying §1.2's shape distinction. **The fix has to route around
those two columns**, and it should land before §3, because stacks add edges rather than removing them.

> #### ⚠ AS BUILT — "around" means one cell declined, because a grid has no other around
>
> Everything running left to right crosses every column, so a forward edge cannot detour past the
> lateral bracket. Two changes make the crossing harmless instead:
>
> 1. **The bracket moved to the far end of the strip, against the node box** (`NET_LATERAL_BUS_COLS`).
>    That fixes the *mirror-image* defect nobody had reported: the bracket used to sit at the start of
>    the strip and stop **eight columns short** of the machine it joined, so a same-layer link visibly
>    connected to nothing. It also reduces the crossing to a single column.
> 2. **A forward run yields at that column** when it already carries lateral ink (`NetCanvas.merge`).
>    Merging would give `┴` — honest, and still a loss, because the arc is the *only* signal telling a
>    same-layer edge from a hop in greyscale. Skipping leaves the horizontal reading as though it
>    passes behind the vertical, which is the older convention anyway.
>
> ⚠ **The draw order is load-bearing**: laterals first, forwards second. Reversed, the forward run
> claims an empty cell and the *arc* is refused instead — the rule inverted, losing exactly what it
> exists to protect. Negative-tested: removing the yield flattens both arcs in `twoHops` and fires
> `lateralEdgesUseArcs`, `forwardRunsYieldToArcs` and `lateralBracketsTouchTheirBox`.
>
> ⚠ **The arrowhead and the lateral stub share `ARROW_COL`, and the arrowhead wins.** Both mean "this
> joins the box on the right" and an arrowhead says it more precisely; the lateral's own corner is one
> column left, so nothing about that edge is lost. This is why the obvious assertion — "the stub
> column holds `─`" — reads a correct render as a failure, and why the test asserts an *unbroken run
> of ink* from the channel to the box instead.

**No render harness can see any of this.** `DeckSnapshot`'s fixture holds one host and runs no sweep,
so no screenshot this project can produce contains a single edge. That is the prerequisite for all of
§3–§6: without it, this is tuned blind, which is how the lane-fit bug survived (two of three routing
lanes turned outside the gap and rendered as stubs, silently, for as long as the token had been wrong).

> #### ⚠ AS BUILT — `DeckSnapshot` sweeps, repositions, and can print the grid
>
> The harness grants the Topology Mapper (ceiling 2, so there is a second column at all) and a deep
> sweep tool (tier 2+, or bridges cannot be found — `Balance.NET_SWEEP_BRIDGE_MIN_TIER`), commissions
> a real sweep through the session, and settles it by winding an **advanceable `Clock`** the engine
> holds. Nothing fakes a discovery: a change that broke discovery shows up as an empty map rather than
> as a render that still looks right.
>
> - **`-Ddeck.reposition=N`** walks the traversal loop N times — plant a foothold on the deepest
>   discovered machine, `connect`, sweep again. The planted foothold is the one shortcut (it stands in
>   for the puzzle, not for the rule); `connect` and the sweep are the real ones. Without it the map
>   can never exceed two columns, because reach is never bought (**I2**).
>   ⚠ It must exclude **the current vantage**, or every step after the first reconnects to where it
>   already is and sweeps from the same place — and a sweep's outcome is frozen at world generation,
>   so it finds nothing. Measured: the flag read `6` and the map stopped growing at three columns.
> - **`-Ddeck.netdump=1`** prints the grid as text. The grid *is* the rendering, so this is the exact
>   and cheapest way to look at one, and it is what makes **NM-2** answerable at all.
>
> ⚠ **Two pre-existing harness defects surfaced on the first real render and are fixed here.** It
> built its engine with `GameEngine.open` rather than `TestSaves.bare`, so the rig was **24 cycles**
> — a starting rig — and `allocateSelfMining(30)` and `scan("thorough")` (35) were both silently
> refused. The deck photographed with an idle compute grid and a SECURITY CENTER reading *"Unaudited
> — no audit has ever run on this rig"*: two states indistinguishable from those features being
> broken. Written against a 100-cycle rig, and nothing re-checked it when the compute ladder landed on
> 2026-08-06. Self-mining is now **10**, because 64 cycles has to carry the scan as well.

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

> #### ⚠ AS BUILT — a member may have NO edge that leaves the group, and that is a third rejection
>
> §3.2's criterion is that "the collapsed edge is a single honest edge rather than a bundle". That is
> only true if every member's drawn edges go to the parent or to another member — so a child is
> **ineligible** if it has a second parent, a lateral link outside the group, or a child of its own.
> Folding one would leave an edge hanging off a box that cannot say which of seven machines it belongs
> to, which is the same lie §3.2 rejects grouping-by-kind for.
>
> ⚠ **This makes §3.4's "an expanded member that is itself a stack parent renders as a stack" vacuous
> rather than unimplemented** — a member with children is not a member. Stated rather than quietly
> dropped, because a later change that loosens eligibility has to answer the hanging-edge question
> first.
>
> ⚠ **The eligible set is a FIXPOINT, not one filtering pass.** Whether a child's neighbour is "outside
> the group" depends on whether that neighbour is itself in the group, so removing one child can
> disqualify another. Peeling to the unique maximal set is deterministic, which the repaint rule
> requires.
>
> ⚠ **Two rules, layered, and each is separately load-bearing.** `soleParent` rejects a machine with
> two parents; the fixpoint rejects one with a child. Neither alone covers both cases, and each
> masked the other during negative testing — so the property that actually holds the line is asserted
> in its general form: `NetLayoutTest.noAdjacencyIsLost`, over every fixture, requires each link to be
> drawn, re-pointed at a stack, or wholly inside one box.

### 3.3 When it collapses

Stack when a parent's children in the next layer exceed `NET_STACK_THRESHOLD` (**proposed 4**).

⚠ **A threshold, not always-on.** Two or three children are more legible drawn than counted, and a
stack that appeared at two would make the common case require a click to see anything.

> #### ⚠ AS BUILT — a second bound the design did not have: `NET_STACK_MIN_LAYER` = 2
>
> **Layer 1 is never folded, however many machines are in it.** Measured on a generated world before
> this bound existed: with a one-hop ceiling every machine a fresh sweep finds hangs off the rig and
> links only to the rig or to its siblings, so the eligibility rule alone folded the *entire*
> neighbourhood into one box and left the headline surface reading `rig → ×7`.
>
> Layer 1 is what the panel is **for** — `NetGraph`'s charter is "the answer to what is next to me" —
> and it is the whole map a new character has. The pressure §2 describes is fan-out **times** depth,
> and this is the half of it a threshold cannot express: a machine in layer 1 is not "behind" anything
> except the player. `NetLayoutTest.layerOneIsNeverFolded`, negative-tested at `MIN_LAYER = 1`.

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

> #### ⚠ AS BUILT — what a node with no neighbour on the far side does, and it differs per pass
>
> On the **forward** pass a node with no already-placed neighbour sorts **last** (unchanged): it is
> reachable only laterally, and hanging it off the bottom keeps it next to the bracket that joins it.
>
> On the **backward** pass it **keeps its current row** instead. Pushing childless machines to the
> bottom of every column would undo the forward pass for the whole of the last layer but one — which,
> on a map measured at four to ten columns with most machines childless, is most of the map. The
> obvious symmetric implementation is the wrong one.
>
> ⚠ **Both passes run over collapsed units, always, whatever is expanded.** That is what makes §3.4's
> insertion rule hold: expansion cannot reach the arrangement, so it cannot move a machine the player
> was not looking at. Rows below the opened fold shift within *that layer only*; every other layer's
> numbering is computed independently and does not move at all.

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

## 7. Sequencing — **all five done 2026-08-08**

1. ✅ **Give `DeckSnapshot` a swept world.** Nothing below can be seen without it. (§1.3)
2. ✅ **Fix the ten-column space.** Route forward edges around the lateral columns. Stacks add edges;
   fixing routing afterwards means doing it twice. (§1.3)
3. ✅ **Stacks, collapsed only** — grouping, threshold, rendering, the collapsed edge. No expansion.
   This alone deletes the `+N MORE` clamp and is independently shippable.
4. ✅ **Expansion** — state, insertion rule, keyboard, accessible text.
5. ✅ **Two-pass barycentre.** Last, because §4.3 means its job is much smaller once stacks exist.

---

## 8. Open questions

- ~~**NM-1** — does expansion state survive a window close?~~ ✅ **Resolved 2026-08-08: no.** A
  `Set<String>` in `NetGraph`, per window, session-scoped; not in the save and not in `deskLayout`. The
  argument against carried: expansion keyed by stack id goes stale the moment a sweep changes the
  grouping, and a restored expansion that no longer matches the graph is worse than none. Unknown ids
  are ignored rather than pruned or rejected, so a sweep landing mid-exploration cannot collapse the
  map (`staleExpansionIsHarmless`).
- **NM-2** — `NET_STACK_THRESHOLD` = 4 is proposed, not measured. ⚠ **NOW MEASURED, AND IT DOES NOT
  FIRE.** Over seven generated worlds (`-Ddeck.reposition=1` and `=8`), layer widths run **1–5** and
  are usually split across two or more parents, so no fold ever reaches five eligible siblings under
  one parent. Stacking is built, correct and **dormant in reachable worlds**. The 50-machines-a-server
  figure §2 quotes is a fact about the *topology*, not about the *discovered subgraph*: the hop
  ceiling is 2, so a sweep only ever sees a machine's immediate surroundings. **Left at 4 rather than
  tuned down on three samples** — a proposed balance number moved on that evidence is worse than one
  left proposed. Re-measure before changing it, with `-Ddeck.netdump=1`.
- ~~**NM-3** — what happens when an expanded stack's membership changes under the player?~~ ✅
  **Resolved 2026-08-08: it stays expanded and the new machine appears in it**, as recommended. It
  falls out of the design rather than being implemented: the id is derived from the parent's address,
  so a fold that still exists keeps its key across any sweep.
- **NM-4** — do stacks apply in the LIST tab? **No, and unchanged.** A list is already linear and
  scrollable, and the pressure this relieves is spatial.
- **NM-5** *(new, 2026-08-08)* — ⚠ **DEPTH IS THE DOMINANT PRESSURE, AND §2 HAS IT SECOND.** Measured:
  maps run **4 to 10 columns** after repositioning, at 31 characters a column — well past any window,
  so the graph scrolls horizontally and the player loses the left-hand end of their own route. Fan-out
  is the pressure this document is written against and it does not occur; depth is the one that does,
  and nothing here addresses it. Candidates, none designed: fold a *run* of single-child layers the way
  a stack folds a fan; a minimap or an overview scale; anchoring the vantage's column in view the way a
  frozen table column works. This wants its own pass.
