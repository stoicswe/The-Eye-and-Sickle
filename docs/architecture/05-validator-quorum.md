# 05 — Validator Quorum & Reputation

**Status:** Established (Tech Chat 2)
**Depends on:** `02-identity-and-auth.md` (validators are DID-identified servers), `03-server-and-federation.md`, `04-item-provenance.md`
**Depended on by:** `../design/13` (duels)

How the federation adjudicates a cross-server duel outcome with **no single arbiter** (Invariant I15): sample a committee of opted-in servers, weight them by reputation, require a Byzantine-fault-tolerant supermajority to sign, and update each validator's reputation based on how it voted. This is the negative/consensus half of the anti-cheat model.

---

## 1. The BFT threshold

Consensus requires **`2f+1` of `3f+1`** weighted validator power — the standard Byzantine-fault-tolerant supermajority, tolerating `f` malicious validators out of `3f+1`.

With the recommended committee size **N = 7**: `3f+1 = 7` → `f = 2`, so **5 of 7 must agree** (tolerating 2 Byzantine validators). Weight is by reputation (§3), not one-server-one-vote.

## 2. Validator sampling — weighted random, without replacement

Given the BFT threshold, the sampling step decides **which** opted-in servers are asked to vote on a given duel.

### 2.1 Inputs per candidate validator (opted-in server)

- **`reputation[i]`** — running score, bounded e.g. `[0, 1]`, updated by §3 (diverging from quorum consensus → penalized).
- **`uptime[i]`** — rolling availability. A validator that's usually offline is useless even with good reputation.
- **`stake`** — *optional*; skip unless adding an economic bonding layer later. **Not necessary for a first version.**

### 2.2 Sampling weight

```
weight[i] = reputation[i] * uptime[i]
```

### 2.3 Selection algorithm

**Weighted random sampling without replacement**, drawing a fixed committee size **N** (e.g. N = 7 → f = 2 → 5-of-7 must agree).

Use the **A-Res algorithm** (weighted reservoir sampling): proven, O(N) per draw, and it avoids the bias of naive "sort by weight, take top N" (which would pick the same top servers every duel and centralize trust).

### 2.4 Why random-weighted, not deterministic top-N

Deterministic selection makes the validator set **predictable and colludable** — an attacker who compromises or bribes the top 7 reputation-holders owns every duel outcome. Randomness with reputation-weighting gets the best of both: high-reputation servers are *more likely* to be picked (quality still matters), but *which specific 7* are picked each duel is not knowable in advance.

### 2.5 The cold-start floor (required)

If `reputation[i]` starts at 0 for new servers, they can never be sampled → they can never earn reputation → **cold-start deadlock.** Fix: **initialize new validators at a reputation floor (e.g. 0.3–0.5, not 0)** so they get sampled occasionally and can build a track record, while still being outweighed by proven validators until they do.

## 3. Reputation update rule — asymmetric (AIMD-style)

The production-proven pattern (Tendermint/Cosmos-style BFT): **small, gradual reward for correct participation; disproportionately harsh, sometimes immediate, penalty for provable bad behavior.** This mirrors **AIMD** (additive-increase / multiplicative-decrease) from TCP congestion control — the proven "reward slowly, punish fast" shape for adversarial systems.

Three cases need **different** treatment, not one formula:

### 3.1 Correct vote (matches the final quorum-agreed outcome)

```
reputation[i] = reputation[i] + α * (1 - reputation[i])
```

Asymptotic increase toward 1.0 (EMA-style). **Recommended α = 0.05**, so reputation builds over *dozens* of duels, not instantly. Slow trust-building is intentional — it stops a newly-honest-looking validator from rocketing to high trust right before defecting.

### 3.2 Divergent vote (signed, but disagreed with the threshold-reached majority)

```
reputation[i] = reputation[i] * (1 - β)
```

Multiplicative decrease, **β = 0.2–0.3.** This covers honest disagreement/staleness, **not necessarily malice** — a validator can genuinely diverge due to a race condition or lag — so it shouldn't be catastrophic on its own.

### 3.3 Equivocation (signed two conflicting outcomes for the same duel)

```
reputation[i] = floor_value   (e.g. 0.1),  or immediate ejection from the validator pool
```

The **hard slash.** Equivocation is **cryptographically provable** — both contradicting signatures exist — so it's proof of dishonesty, not a plausible accident. Tendermint treats double-signing exactly this way: proof exists, penalty is immediate and severe, no benefit-of-the-doubt averaging. This is the case that feeds federation-wide non-recognition (`03` §4): a provably-equivocating server is flagged automatically.

## 4. Liveness tracked separately from correctness

A validator that was **sampled but didn't respond (offline)** must **not** get the same penalty as one that actively signed wrong — one is unavailability, the other is a trust signal. Apply a **lighter, separate multiplicative decay on `uptime[i]` for no-shows** (**γ ≈ 0.1**), keeping the two failure modes from being conflated in a single score. (Correctness lives in `reputation[i]`; availability lives in `uptime[i]`; both feed the sampling weight in §2.2.)

## 5. The complete loop

1. A cross-server duel needs adjudication (`../design/13` §3).
2. **Sample** a committee of N opted-in validators by `weight = reputation × uptime`, weighted-random without replacement (A-Res), floor-protected for newcomers (§2).
3. Validators evaluate and **sign** the outcome; consensus requires `2f+1`-of-`3f+1` weighted power (§1).
4. The signed outcome becomes a **multi-signature provenance event** (`04` §3.1) — a `duel_grant` with `issuerDid = duel:<duelId>` and the array of validator signatures.
5. **Update reputation** for every sampled validator by §3 (correct/divergent/equivocation) and `uptime` by §4 (responded/no-show).
6. Verifiers later confirm the outcome by checking sampled-membership + weighted threshold + chain integrity (`04` §7).

## 6. What's deferred

- **Stake/bonding layer** — explicitly optional and **not** in v1 (§2.1). An economic-bonding layer can be added later if reputation alone proves insufficient. (Open A-2, `../design/15`.)
- Concrete values of α (0.05), β (0.2–0.3), γ (0.1), floor (0.1), newcomer init (0.3–0.5), and N (7) are **recommended starting points** from Tech Chat 2 — tune against real federation behavior.

## 7. Design status note (from Tech Chat 2)

Tech Chat 2 closed with: *"That completes the loop: signed provenance chain, weighted random validator sampling, and a reputation update rule with a floor to avoid cold-start deadlock and a hard slash for provable equivocation. This is now a coherent, implementable spec end to end — nothing structurally open unless you want to add stake/bonding later."* Treat this layer as **implementation-ready**; the only structural extension considered was the deferred stake layer.

## 8. Cross-references

- The signed records this produces: `04-item-provenance.md` §3.1, §7
- Validator identities: `02-identity-and-auth.md`
- Non-recognition of equivocating servers: `03-server-and-federation.md` §4
- What a "duel" is at the play level, and which engagements need quorum: `../design/13` §3 (open D-1)
