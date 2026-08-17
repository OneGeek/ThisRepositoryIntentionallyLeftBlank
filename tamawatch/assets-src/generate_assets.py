"""TamaWatch asset build. Runs the art + sound generators, writes
assets_manifest.json, and asserts the output matches the Authoritative
Generation Set in docs/04-asset-validation.md so art/code never drift.

Usage: python3 generate_assets.py [app_main_dir]
Defaults to ../app/src/main relative to this file."""

import os
import sys
import json
import generate_art
import generate_sound

HERE = os.path.dirname(os.path.abspath(__file__))
MAIN = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "app", "src", "main")

ART_DIR = os.path.join(MAIN, "res", "drawable-nodpi")
SND_DIR = os.path.join(MAIN, "res", "raw")
MAN_DIR = os.path.join(MAIN, "assets", "tamawatch")

# ---- Authoritative Generation Set (docs/04 §4) --------------------------------
EXPECT_ART = set()
EXPECT_ART |= {"spr_egg", "spr_baby", "spr_child_a", "spr_child_b", "spr_child_c",
               "spr_teen_a", "spr_teen_b", "spr_teen_c", "spr_teen_d", "spr_teen_e",
               "spr_adult_a", "spr_adult_b", "spr_adult_c", "spr_adult_d",
               "spr_adult_e", "spr_adult_f", "spr_adult_g", "spr_adult_h",
               "spr_stageset_child", "spr_stageset_teen", "spr_stageset_adult"}
EXPECT_ART |= {"ov_poop", "ov_sick_skull", "ov_zzz", "ov_call",
               "ov_heart_particle", "ov_sweat", "ov_note"}
EXPECT_ART |= {"ic_feed", "ic_light", "ic_play", "ic_bathroom", "ic_medicine",
               "ic_status", "ic_discipline", "ic_shop", "ic_steps",
               "ic_settings", "ic_back", "ic_confirm",
               "ic_hunger", "ic_hunger_empty", "ic_mood", "ic_mood_empty"}
EXPECT_ART |= {"ui_heart_full", "ui_heart_empty", "ui_gp_coin", "ui_step_shoe",
               "ui_selector", "ic_app_launcher_fg", "ic_app_launcher_bg",
               "ic_notification", "img_splash"}
EXPECT_ART |= {"bg_room_day", "bg_room_night", "bg_egg", "bg_shop",
               "bg_game_jump", "bg_game_guess", "bg_game_catch", "bg_ambient",
               "cos_bg_beach", "cos_bg_space"}
EXPECT_ART |= {"item_meal_bread", "item_meal_bowl", "item_meal_fish", "item_meal_veg",
               "item_snack_cake", "item_snack_candy", "item_snack_juice",
               "item_snack_icecream", "item_medicine"}
EXPECT_ART |= {"game_obstacle", "game_arrow_l", "game_arrow_r", "game_basket",
               "game_treat_good", "game_treat_bad"}
EXPECT_ART |= {"cut_hatch", "cut_evolve", "cut_farewell", "fx_star", "fx_goal"}
EXPECT_ART |= {"fx_shadow", "rug_rose", "rug_sky", "rug_moss", "rug_cream",
               "rug_royal", "rug_night"}

EXPECT_SND = {"sfx_select", "sfx_confirm", "sfx_cancel", "sfx_error",
              "sfx_eat", "sfx_drink", "sfx_refuse", "sfx_flush", "sfx_heal", "sfx_pet",
              "sfx_happy", "sfx_sad", "sfx_sick", "sfx_sleep", "sfx_call", "sfx_talk_react",
              "sfx_hatch", "sfx_evolve", "sfx_goal", "sfx_coin", "sfx_buy",
              "sfx_farewell", "sfx_levelup",
              "sfx_game_start", "sfx_jump", "sfx_score", "sfx_miss",
              "sfx_game_win", "sfx_game_lose", "mus_game_loop"}


def main():
    art = generate_art.generate(ART_DIR)
    snd = generate_sound.generate(SND_DIR)

    got_art, got_snd = set(art), set(snd)
    missing_art, extra_art = EXPECT_ART - got_art, got_art - EXPECT_ART
    missing_snd, extra_snd = EXPECT_SND - got_snd, got_snd - EXPECT_SND

    ok = not (missing_art or extra_art or missing_snd or extra_snd)
    if missing_art:
        print("MISSING art:", sorted(missing_art))
    if extra_art:
        print("UNEXPECTED art:", sorted(extra_art))
    if missing_snd:
        print("MISSING sound:", sorted(missing_snd))
    if extra_snd:
        print("UNEXPECTED sound:", sorted(extra_snd))

    os.makedirs(MAN_DIR, exist_ok=True)
    manifest = {"version": 1, "sprites": art, "sounds": snd}
    with open(os.path.join(MAN_DIR, "assets_manifest.json"), "w") as f:
        json.dump(manifest, f, indent=1, sort_keys=True)

    print(f"\nArt: {len(got_art)} files  (expected {len(EXPECT_ART)})")
    print(f"Sound: {len(got_snd)} files  (expected {len(EXPECT_SND)})")
    print("Manifest ->", os.path.join(MAN_DIR, "assets_manifest.json"))
    print("VALIDATION:", "PASS ✅" if ok else "FAIL ❌")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
