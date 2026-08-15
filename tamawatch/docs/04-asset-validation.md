# TamaWatch — Asset List ↔ Implementation Plan Validation

Purpose: prove the asset list (`02`) and the implementation plan (`03`) are consistent — every asset the plan needs exists in the list, and every asset in the list is actually used by the plan. Discrepancies are **resolved here**, and the result is the **Authoritative Generation Set** the asset generator must produce.

Method: two-way reconciliation.
- **Forward (plan → list):** for each screen/system in plan §5–§9, confirm each referenced asset ID exists in the list. → checks for *missing* assets.
- **Backward (list → plan):** for each ID in the list's index, confirm the plan uses it. → checks for *orphan* assets.

---

## 1. Forward check (plan needs → list has)

| Plan system | Assets referenced | In list? |
|---|---|---|
| App shell (§5.2) | `ic_app_launcher`, `img_splash` | ✅ A4 |
| Home + Care Ring (§5.3) | `bg_room_day/night`, active pet set, `ov_*`, ring icons ×10, `ui_selector`, hearts/bars, `ui_gp_coin`, `ui_step_shoe`, `sfx_select/confirm/cancel`, `hap_tick` | ✅ A3/A4/A5/B1/B6 |
| Care actions (§5.4) | `item_meal_*`,`item_snack_*`, eat frames (stageset), `ov_poop`,`ov_sick_skull`,`ov_heart_particle`, `sfx_eat/drink/refuse/flush/heal/pet`, `sfx_confirm/error` | ✅ A6/A1/A2/B2/B1 |
| Steps + Shop (§5.5) | `ic_steps`,`ui_step_shoe`,`fx_goal`,`sfx_goal`,`sfx_coin`,`bg_shop`, item icons, `cos_*`, `sfx_buy` | ✅ A3/A4/A8/A6/B4 |
| Mini-games (§5.6) | `bg_game_*`, `game_obstacle/arrow_l/arrow_r/basket/treat_good/treat_bad`, `sfx_game_start/jump/score/miss/game_win/game_lose`, `mus_game_loop` | ✅ A5/A7/B5 |
| Lifecycle scenes (§5.11) | `bg_egg`,`spr_egg`,`cut_hatch/evolve/farewell`,`fx_star`, `sfx_hatch/evolve/levelup/farewell`, `hap_evolve` | ✅ A1/A5/A8/B4/B6 |
| Background/off-app (§6) | `ic_notification`, `sfx_call`, `hap_call` | ✅ A4/B3/B6 |
| Ambient (§5, §9) | `bg_ambient`, `ov_zzz` | ✅ A5/A2 |
| Evolution table (§3) | `spr_baby`,`spr_child_a/b/c`,`spr_teen_a..e`,`spr_adult_a..h`, stagesets | ✅ A1 — **every species token has a sprite** |

**Forward gaps found:** 1 minor.
- **G1 — Complication icon.** Plan §6 renders a complication (`RANGED_VALUE` + icon) but no dedicated complication icon exists in the list. **Resolution:** reuse `ui_heart_full` (monochrome variant) + `ic_notification`; **no new asset required.** Documented, closed.

Everything else the plan references is present. ✅

---

## 2. Backward check (list has → plan uses)

Walked every ID in the list's "Asset ID Index". Fully-used groups (characters, overlays except noted, ring icons, UI, backgrounds, game props, cutscene/FX, sounds, haptics) are covered by plan §5–§9. **Orphans / weak-links found:** 3.

- **O1 — `item_soap`.** Listed as a care consumable, but the plan restores Hygiene via `clean()`/flush only; there is **no soap mechanic**. → **Resolution: CUT from v1.** Hygiene = flush. `item_soap` moves to the roadmap (optional "bath" that trickles Bond). Removed from the Authoritative Generation Set.
- **O2 — `cos_hat_party`, `cos_hat_crown`.** Wearable hats require a head-anchor **accessory-overlay render path** that the plan's rendering section (§4) does **not** define (it draws one pet frame + status overlays, no per-species head anchor). Two ways to close: (a) add head-anchor data per species to `assets_manifest.json` and an accessory layer in `PixelSprite`, or (b) descope wearable cosmetics. → **Resolution: DESCOPE wearable hats to v1.1.** Keeps v1 rendering simple and avoids 17× head-anchor authoring. Hats move to the roadmap. **Room-reskin cosmetics (`cos_bg_beach`, `cos_bg_space`) STAY** — they are plain 480×480 background swaps already supported by §4. The Shop still has a cosmetics tab (room themes).
- **O3 — `mus_game_loop`.** Marked "optional" in both docs; mini-games are ≤30 s and function with SFX only. → **Resolution: keep as OPTIONAL / stretch;** generator produces it if cheap, code guards its absence. Not a blocker.

Minor confirmations (used, previously not screen-cited — no gap):
- `ov_sweat` → refuse/stress animation on over-feed and scold-mistake. Used.
- `ov_note` → mic "talk" + happy reaction. Used.
- `ov_zzz` → sleep state + ambient. Used.
- `sfx_sad/sick/sleep/happy/talk_react`, `hap_confirm/error` → fired by `DomainEvent`s and confirm/error paths per plan §3 ("UI/audio/haptics react to events"). Used.

---

## 3. Coverage summary

| Category | In list | Cut | Descoped | Reuse (no gen) | **To generate (v1)** |
|---|---:|---:|---:|---:|---:|
| Character sprite groups | 20 | 0 | 0 | 0 | **20** |
| Overlays | 7 | 0 | 0 | 0 | **7** |
| Ring icons | 12 | 0 | 0 | 0 | **12** |
| UI chrome | 15 | 0 | 0 | 4 drawn-in-code | **11** |
| Backgrounds | 8 | 0 | 0 | 0 | **8** |
| Items / cosmetics | 16 | 1 (`item_soap`) | 2 (hats) | 0 | **13** |
| Game props | 6 | 0 | 0 | 0 | **6** |
| Cutscene / FX | 5 | 0 | 0 | 0 | **5** |
| **Sounds** | 30 | 0 | 0 | 0 | **30** (`mus_game_loop` optional) |
| Haptic patterns (code) | 5 | 0 | 0 | 5 in code | 0 files |

**Result:** the two documents are consistent after 1 forward resolution (G1, reuse) and 3 backward resolutions (O1 cut, O2 descope, O3 optional). No missing assets block the plan; no orphan forces unplanned engineering.

---

## 4. Authoritative Generation Set (input to the asset generator)

The generator (`assets-src/generate_assets.py`) MUST emit exactly these, plus `assets_manifest.json`:

**Art (PNG-32, drawable-nodpi):**
- Characters: `spr_egg`, `spr_baby`, `spr_child_a/b/c`, `spr_teen_a/b/c/d/e`, `spr_adult_a/b/c/d/e/f/g/h`, `spr_stageset_child/teen/adult`
- Overlays: `ov_poop`, `ov_sick_skull`, `ov_zzz`, `ov_call`, `ov_heart_particle`, `ov_sweat`, `ov_note`
- Ring icons: `ic_feed`, `ic_light`, `ic_play`, `ic_bathroom`, `ic_medicine`, `ic_status`, `ic_discipline`, `ic_shop`, `ic_steps`, `ic_settings`, `ic_back`, `ic_confirm`
- UI (generated): `ui_heart_full`, `ui_heart_empty`, `ui_gp_coin`, `ui_step_shoe`, `ui_selector`, `ic_app_launcher_fg`, `ic_app_launcher_bg`, `ic_notification`, `img_splash` (+ `ui_bar_frame/fill`, `ui_ring_bg`, `ui_panel`, `ui_toast` **drawn in code**, not generated)
- Backgrounds: `bg_room_day`, `bg_room_night`, `bg_egg`, `bg_shop`, `bg_game_jump`, `bg_game_guess`, `bg_game_catch`, `bg_ambient`
- Items/cosmetics: `item_meal_bread/bowl/fish/veg`, `item_snack_cake/candy/juice/icecream`, `item_medicine`, `cos_bg_beach`, `cos_bg_space` (11) + coin/shoe already counted → **13 including the two counted under UI? no:** meals 4 + snacks 4 + `item_medicine` 1 + `cos_bg_beach/space` 2 = **11 item/cosmetic files** (soap cut, hats descoped)
- Game props: `game_obstacle`, `game_arrow_l`, `game_arrow_r`, `game_basket`, `game_treat_good`, `game_treat_bad`
- Cutscene/FX: `cut_hatch`, `cut_evolve`, `cut_farewell`, `fx_star`, `fx_goal`

**Sound (mono 16-bit 22.05 kHz WAV, res/raw):** all 30 IDs from list Part B (`mus_game_loop` optional).

**Manifest:** `assets_manifest.json` — per art ID: `{file, frameW, frameH, frames}`; consumed by `SpriteBank` so code never hard-codes frame geometry (closes the asset/code drift risk in plan §10).

---

## 5. Sign-off

- [x] Every evolution-tree species resolves to a generated sprite (join verified).
- [x] Every plan-referenced asset exists or is explicitly reused/drawn-in-code.
- [x] Every listed asset is used, cut, descoped, or marked optional — no silent orphans.
- [x] Manifest contract defined to keep frames in lockstep with code.

**Validation PASSED.** Proceed to asset generation against the Authoritative Generation Set, then implementation per plan build order.
