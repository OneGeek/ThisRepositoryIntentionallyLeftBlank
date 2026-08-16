/* TamaWatch web preview — a faithful JS port of the care loop for hands-on
   evaluation on a round 480x480 canvas. Uses the same generated sprites/sounds
   and mirrors the Kotlin engine's rules (decay, calls, poop/sick, care-score
   branching, steps->GP). Time is ACCELERATED so a full lifecycle is watchable
   in a couple of minutes. Assets are injected as MANIFEST/SPRITES/SOUNDS. */
(function () {
  const cv = document.getElementById('screen');
  const ctx = cv.getContext('2d');
  ctx.imageSmoothingEnabled = true;
  const W = 480, H = 480, CX = 240, CY = 240;

  // ---- assets
  const IMG = {};
  let loaded = 0, total = Object.keys(SPRITES).length;
  for (const id in SPRITES) { const im = new Image(); im.onload = () => loaded++; im.src = SPRITES[id]; IMG[id] = im; }
  function meta(id) { return MANIFEST.sprites[id]; }
  function tagFrames(id, name) { const m = meta(id); return (m && m.tags && m.tags[name]) || [0]; }
  function drawSprite(id, frameIdx, dx, dy, dw, dh) {
    const m = meta(id), im = IMG[id]; if (!m || !im) return;
    const i = m.frames > 0 ? (frameIdx % m.frames) : 0;
    ctx.drawImage(im, i * m.frameW, 0, m.frameW, m.frameH, dx, dy, dw, dh);
  }
  function playSfx(id, vol) {
    if (!AUDIO_ON || !SOUNDS[id]) return;
    const a = new Audio(SOUNDS[id]); a.volume = vol == null ? 0.7 : vol; a.play().catch(() => {});
  }

  // ---- accelerated tuning (real ms / per real second)
  const T = {
    stage: { EGG: 6000, BABY: 20000, CHILD: 30000, TEEN: 30000, ADULT: 45000 },
    hungerPerSec: 0.9, happyPerSec: 1.1, energyDecayPerSec: 0.5, energyRegenPerSec: 2.0,
    crit: 15, poopChancePerSec: 1 / 22, sickChancePerSec: 0.01, sickNeglectMult: 6,
    attentionTimeout: 12000, deathCritical: 30000, stepsPerGp: 20, dailyGoal: 6000, heart: 25,
  };
  const hearts = v => Math.max(0, Math.min(4, Math.ceil(v / T.heart)));

  // ---- species tree (mirrors EvolutionEngine)
  const CHILD = { GOOD: 'spr_child_a', AVG: 'spr_child_b', NEG: 'spr_child_c' };
  const NAME = {
    spr_egg: 'Egg', spr_baby: 'Babymon', spr_child_a: 'Kidmon', spr_child_b: 'Pipmon', spr_child_c: 'Grumon',
    spr_teen_a: 'Sunmon', spr_teen_b: 'Bowmon', spr_teen_c: 'Hornmon', spr_teen_d: 'Spikmon', spr_teen_e: 'Wingmon',
    spr_adult_a: 'Angelmon', spr_adult_b: 'Leafmon', spr_adult_c: 'Fairymon', spr_adult_d: 'Aquamon',
    spr_adult_e: 'Embermon', spr_adult_f: 'Mystmon', spr_adult_g: 'Dracomon', spr_adult_h: 'Rockmon',
  };
  function tierOf(c) {
    const s = 3 * c.misses + 2.5 * c.sick + 2 * c.disc - 0.5 * c.games;
    return s <= 2 ? 'GOOD' : (s <= 6 ? 'AVG' : 'NEG');
  }
  function childFor(t) { return CHILD[t]; }
  function teenFor(child, t) {
    if (child === 'spr_child_a') return t === 'GOOD' ? 'spr_teen_a' : 'spr_teen_b';
    if (child === 'spr_child_b') return t === 'GOOD' ? 'spr_teen_b' : (t === 'AVG' ? 'spr_teen_c' : 'spr_teen_d');
    return t === 'NEG' ? 'spr_teen_e' : 'spr_teen_d';
  }
  function adultFor(teen, t) {
    const m = {
      spr_teen_a: ['spr_adult_a', 'spr_adult_b'], spr_teen_b: ['spr_adult_b', 'spr_adult_c'],
      spr_teen_c: ['spr_adult_d', 'spr_adult_e'], spr_teen_d: ['spr_adult_f', 'spr_adult_h'],
      spr_teen_e: ['spr_adult_g', 'spr_adult_h'],
    }[teen] || ['spr_adult_f', 'spr_adult_f'];
    return t === 'GOOD' ? m[0] : m[1];
  }
  const stageOf = id => id === 'spr_egg' ? 'EGG' : id === 'spr_baby' ? 'BABY'
    : id.indexOf('child') >= 0 ? 'CHILD' : id.indexOf('teen') >= 0 ? 'TEEN' : 'ADULT';
  const stagesetId = st => st === 'CHILD' ? 'spr_stageset_child' : st === 'TEEN' ? 'spr_stageset_teen' : st === 'ADULT' ? 'spr_stageset_adult' : null;

  // ---- state
  let AUDIO_ON = true;
  let pet = newEgg('Tama');
  let flash = null, flashUntil = 0, sel = 0;

  function newEgg(name, gen, gp) {
    const now = performance.now();
    return {
      name, species: 'spr_egg', stage: 'EGG', gen: gen || 1, stageStart: now, born: now, last: now,
      lightOn: true, asleep: false, hunger: 70, happy: 70, energy: 90, weight: 20, bond: 20, disc: 30,
      dirty: false, sick: false, gp: gp == null ? 50 : gp, steps: 0, pendingCall: null, alive: true,
      care: { misses: 0, sick: 0, disc: 0, games: 0, mins: 0, crit: 0 },
    };
  }
  function needsAttention(p) {
    return p.alive && !p.asleep && p.stage !== 'EGG' &&
      (p.hunger <= T.crit || p.happy <= T.crit || p.dirty || p.sick);
  }

  // ---- simulation
  let lastTs = performance.now(), simClock = 6; // start "daytime"
  function advance(dtMs) {
    const dt = dtMs / 1000;
    simClock = (simClock + dtMs / 4000) % 24; // fast day cycle for sleep demo
    if (!pet.alive) return;
    // stage progression
    const dur = T.stage[pet.stage];
    if (pet.stage !== 'FAREWELL' && performance.now() - pet.stageStart >= dur) {
      const tier = tierOf(pet.care); pet.stageStart = performance.now();
      if (pet.stage === 'EGG') { pet.stage = 'BABY'; pet.species = 'spr_baby'; setFlash('It hatched!'); playSfx('sfx_hatch'); }
      else if (pet.stage === 'BABY') { pet.species = childFor(tier); pet.stage = 'CHILD'; evolved(); }
      else if (pet.stage === 'CHILD') { pet.species = teenFor(pet.species, tier); pet.stage = 'TEEN'; evolved(); }
      else if (pet.stage === 'TEEN') { pet.species = adultFor(pet.species, tier); pet.stage = 'ADULT'; evolved(); }
      else if (pet.stage === 'ADULT') { pet.stage = 'FAREWELL'; pet.alive = false; setFlash('Farewell…'); playSfx('sfx_farewell'); }
      pet.care = { misses: 0, sick: 0, disc: 0, games: 0, mins: 0, crit: 0 };
    }
    if (pet.stage === 'EGG' || !pet.alive) return;

    const asleep = (simClock >= 22 || simClock < 8);
    if (asleep !== pet.asleep) { pet.asleep = asleep; playSfx(asleep ? 'sfx_sleep' : 'sfx_happy', 0.4); }
    const disturbed = asleep && pet.lightOn;
    const f = asleep ? 0.35 : 1.0, sm = pet.sick ? 1.5 : 1.0;
    pet.hunger = Math.max(0, pet.hunger - T.hungerPerSec * dt * f * sm);
    pet.happy = Math.max(0, pet.happy - T.happyPerSec * dt * f * sm);
    pet.energy = (asleep && !disturbed) ? Math.min(100, pet.energy + T.energyRegenPerSec * dt)
      : Math.max(0, pet.energy - T.energyDecayPerSec * dt);

    if (!asleep && !pet.dirty && Math.random() < T.poopChancePerSec * dt) { pet.dirty = true; playSfx('sfx_sad', 0.5); }
    if (!pet.sick) {
      const neg = pet.hunger <= 0 || pet.happy <= 0 || pet.dirty;
      if (Math.random() < T.sickChancePerSec * dt * (neg ? T.sickNeglectMult : 1)) { pet.sick = true; pet.care.sick++; playSfx('sfx_sick'); }
    }
    if (pet.hunger <= 0 && pet.happy <= 0) pet.care.crit += dtMs; else pet.care.crit = 0;
    if (pet.care.crit >= T.deathCritical) { pet.alive = false; pet.stage = 'FAREWELL'; setFlash('Your Tama passed…'); playSfx('sfx_sad'); return; }

    // attention
    if (needsAttention(pet)) {
      if (pet.pendingCall == null) { pet.pendingCall = performance.now(); playSfx('sfx_call'); }
      else if (performance.now() - pet.pendingCall >= T.attentionTimeout) { pet.care.misses++; pet.pendingCall = performance.now(); }
    } else pet.pendingCall = null;
  }
  function evolved() { setFlash('Evolved into ' + NAME[pet.species] + '!'); playSfx('sfx_evolve'); }
  function setFlash(t) { flash = t; flashUntil = performance.now() + 2200; }

  // ---- actions
  const FOOD = {
    meal_bowl: { hunger: 45, happy: 4, weight: 4, icon: 'item_meal_bowl', meal: true },
    meal_fish: { hunger: 50, happy: 6, weight: 3, icon: 'item_meal_fish', meal: true },
    snack_cake: { hunger: 5, happy: 30, weight: 6, icon: 'item_snack_cake', meal: false },
    snack_icecream: { hunger: 6, happy: 35, weight: 7, icon: 'item_snack_icecream', meal: false },
  };
  function feed(id) {
    const fdef = FOOD[id]; if (!fdef) return;
    if (fdef.meal && pet.hunger >= 95) { playSfx('sfx_refuse'); setFlash('Too full!'); return; }
    pet.hunger = Math.min(100, pet.hunger + fdef.hunger);
    pet.happy = Math.min(100, pet.happy + fdef.happy);
    pet.weight = Math.min(99, pet.weight + fdef.weight);
    if (!needsAttention(pet)) pet.pendingCall = null;
    playSfx(fdef.meal ? 'sfx_eat' : 'sfx_drink');
  }
  function act(name) {
    if (pet.stage === 'EGG' || !pet.alive) { if (!pet.alive) restart(); return; }
    switch (name) {
      case 'Feed': feed('meal_bowl'); break;
      case 'Snack': feed('snack_cake'); break;
      case 'Play': { const sc = 5 + Math.floor(Math.random() * 10); pet.happy = Math.min(100, pet.happy + 8 + sc); pet.energy = Math.max(0, pet.energy - 10); pet.weight = Math.max(5, pet.weight - 1); pet.gp += 5 + sc; pet.care.games++; playSfx('sfx_game_win'); setFlash('+' + (5 + sc) + ' GP'); break; }
      case 'Clean': if (pet.dirty) { pet.dirty = false; pet.bond = Math.min(100, pet.bond + 1); if (!needsAttention(pet)) pet.pendingCall = null; playSfx('sfx_flush'); } break;
      case 'Medicine': if (pet.sick) { pet.sick = false; pet.bond = Math.min(100, pet.bond + 1); if (!needsAttention(pet)) pet.pendingCall = null; playSfx('sfx_heal'); } break;
      case 'Light': pet.lightOn = !pet.lightOn; playSfx('sfx_select'); break;
      case 'Scold': { const ok = !needsAttention(pet); if (ok) pet.disc = Math.min(100, pet.disc + 8); else { pet.care.disc++; pet.bond = Math.max(0, pet.bond - 5); } playSfx(ok ? 'sfx_confirm' : 'sfx_error'); break; }
      case 'Walk': { pet.steps += 800; pet.happy = Math.min(100, pet.happy + 2); pet.weight = Math.max(5, pet.weight - 1); const gp = Math.floor(800 / T.stepsPerGp); pet.gp += gp; if (pet.steps >= T.dailyGoal && pet.steps - 800 < T.dailyGoal) { pet.gp += 200; setFlash('Step goal! +200'); playSfx('sfx_goal'); } else { playSfx('sfx_coin', 0.6); } break; }
      case 'Pet': pet.happy = Math.min(100, pet.happy + 4); pet.bond = Math.min(100, pet.bond + 2); playSfx('sfx_pet'); break;
    }
  }
  function restart() { pet = newEgg('Tama', pet.gen + 1, pet.gp + 150); setFlash('Gen ' + pet.gen); }

  // ---- render
  const RING = [
    { icon: 'ic_feed', label: 'Feed', act: 'Feed', alert: p => p.hunger <= T.crit },
    { icon: 'ic_play', label: 'Play', act: 'Play' },
    { icon: 'ic_bathroom', label: 'Clean', act: 'Clean', alert: p => p.dirty },
    { icon: 'ic_medicine', label: 'Medicine', act: 'Medicine', alert: p => p.sick },
    { icon: 'ic_light', label: 'Light', act: 'Light' },
    { icon: 'ic_status', label: 'Snack', act: 'Snack' },
    { icon: 'ic_shop', label: 'Walk +800', act: 'Walk' },
    { icon: 'ic_discipline', label: 'Scold', act: 'Scold' },
  ];
  function poseFor(p) {
    const set = stagesetId(p.stage) || p.species;
    if (p.stage === 'EGG') return ['spr_egg', 'wiggle'];
    if (!p.alive || p.asleep) return [set, 'sleep'];
    if (p.sick) return [set, 'sick'];
    if (needsAttention(p)) return [set, 'call'];
    if (p.happy >= 80) return [p.species, 'happy'];
    return [p.species, 'idle'];
  }

  let animTick = 0;
  function ringPos(i, r) { const a = (-90 + i * (360 / RING.length)) * Math.PI / 180; return [CX + r * Math.cos(a), CY + r * Math.sin(a)]; }

  function render() {
    animTick++;
    const fi = Math.floor(animTick / 12);
    // background
    let bg = pet.stage === 'EGG' ? 'bg_egg' : (pet.asleep || !pet.lightOn) ? 'bg_room_night' : 'bg_room_day';
    drawSprite(bg, 0, 0, 0, W, H);

    // top status
    ctx.fillStyle = 'rgba(0,0,0,0.35)'; ctx.fillRect(90, 44, 300, 40);
    for (let k = 0; k < 4; k++) drawSprite(k < hearts(pet.hunger) ? 'ui_heart_full' : 'ui_heart_empty', 0, 150 + k * 22, 50, 18, 18);
    for (let k = 0; k < 4; k++) drawSprite(k < hearts(pet.happy) ? 'ui_heart_full' : 'ui_heart_empty', 0, 150 + k * 22, 66, 18, 18);
    drawSprite('ui_gp_coin', fi, 250, 50, 16, 16);
    ctx.fillStyle = '#fff'; ctx.font = '14px monospace'; ctx.textAlign = 'left';
    ctx.fillText(pet.gp + ' GP', 270, 63); ctx.fillText(pet.steps + ' st', 270, 79);

    if (pet.stage !== 'EGG') {
      // ring
      for (let i = 0; i < RING.length; i++) {
        const [x, y] = ringPos(i, 172);
        if (i === sel) drawSprite('ui_selector', fi, x - 24, y - 24, 48, 48);
        if (RING[i].alert && RING[i].alert(pet)) { ctx.fillStyle = 'rgba(223,62,62,0.45)'; ctx.beginPath(); ctx.arc(x, y, 20, 0, 7); ctx.fill(); }
        drawSprite(RING[i].icon, 0, x - 14, y - 14, 28, 28);
      }
    }

    // pet
    const [sid, stag] = poseFor(pet);
    const frames = tagFrames(sid, stag);
    const frame = frames[Math.floor(animTick / 14) % frames.length];
    const size = 150;
    drawSprite(sid, frame, CX - size / 2, CY - size / 2, size, size);
    if (pet.dirty) drawSprite('ov_poop', Math.floor(animTick / 20) % 2, CX - 70, CY + 30, 34, 34);
    if (pet.sick) drawSprite('ov_sick_skull', fi % 2, CX + 40, CY - 60, 26, 26);
    if (pet.asleep) drawSprite('ov_zzz', fi % 2, CX + 40, CY - 60, 30, 30);
    if (needsAttention(pet) && !pet.asleep) drawSprite('ov_call', fi % 2, CX + 44, CY - 64, 22, 22);

    // label
    ctx.fillStyle = '#fff'; ctx.textAlign = 'center'; ctx.font = '16px monospace';
    if (pet.stage === 'EGG') ctx.fillText(pet.name + "'s egg…", CX, CY + 120);
    else if (!pet.alive) ctx.fillText('Tap center for next gen', CX, CY + 120);
    else ctx.fillText(RING[sel].label, CX, CY + 120);

    // bottom info
    ctx.font = '12px monospace'; ctx.fillStyle = '#cde';
    ctx.fillText(NAME[pet.species] + ' · Gen ' + pet.gen + ' · ' + pet.weight + 'g', CX, 430);

    // flash
    if (flash && performance.now() < flashUntil) {
      ctx.fillStyle = 'rgba(0,0,0,0.6)'; ctx.fillRect(60, 200, 360, 60);
      ctx.fillStyle = '#ffe45a'; ctx.font = '20px monospace'; ctx.fillText(flash, CX, 236);
    }
  }

  // ---- loop
  function loop(ts) {
    const dt = Math.min(500, ts - lastTs); lastTs = ts;
    advance(dt); render();
    requestAnimationFrame(loop);
  }
  if (loaded < total) { const wait = setInterval(() => { if (loaded >= total) { clearInterval(wait); requestAnimationFrame(loop); } }, 50); }
  else requestAnimationFrame(loop);

  // ---- input
  cv.addEventListener('click', ev => {
    const rect = cv.getBoundingClientRect();
    const x = (ev.clientX - rect.left) * (W / rect.width);
    const y = (ev.clientY - rect.top) * (H / rect.height);
    const dc = Math.hypot(x - CX, y - CY);
    if (dc < 80) { if (!pet.alive) restart(); else if (pet.stage !== 'EGG') act(RING[sel].act); else act('Pet'); return; }
    // ring hit test
    for (let i = 0; i < RING.length; i++) { const [ix, iy] = ringPos(i, 172); if (Math.hypot(x - ix, y - iy) < 26) { sel = i; act(RING[i].act); return; } }
  });
  window.addEventListener('wheel', ev => { sel = (sel + (ev.deltaY > 0 ? 1 : RING.length - 1)) % RING.length; }, { passive: true });
  window.addEventListener('keydown', ev => {
    if (ev.key === 'ArrowRight') sel = (sel + 1) % RING.length;
    else if (ev.key === 'ArrowLeft') sel = (sel + RING.length - 1) % RING.length;
    else if (ev.key === 'Enter' || ev.key === ' ') act(pet.alive ? RING[sel].act : 'restart');
    else if (ev.key.toLowerCase() === 'm') { AUDIO_ON = !AUDIO_ON; }
  });
  document.getElementById('mute').addEventListener('click', () => { AUDIO_ON = !AUDIO_ON; document.getElementById('mute').textContent = AUDIO_ON ? '🔊' : '🔇'; });
})();
