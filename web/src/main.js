import * as THREE from 'three';
import { PLAYER_SHIPS, ENEMIES, UPGRADES, MAX_LEVEL, upgradeCost, shipStats, PORTS, WORLD, questAt } from './data.js';
import { makeShip, makeIsland, makeWater, makeChest, makeGem } from './models.js';
import { seeded, clamp, rand, randInt, wrapAngle, fmt, waveHeight } from './utils.js';
import { sfx, unlockAudio, audio } from './audio.js';

const $ = (id) => document.getElementById(id);

// ================= Сохранение =================
const SAVE_KEY = 'seawolves.v1';
function defaultSave() {
  return {
    coins: 150, gems: 0, owned: ['sloop'], ship: 'sloop',
    up: { sloop: { hull: 0, sails: 0, guns: 0, reload: 0 } },
    quest: 0, qprog: 0, port: 0, sound: true, kills: 0,
  };
}
function loadSave() {
  try {
    const s = JSON.parse(localStorage.getItem(SAVE_KEY));
    if (s && s.owned) return Object.assign(defaultSave(), s);
  } catch (e) { /* пустое сохранение */ }
  return defaultSave();
}
let save = loadSave();
function persist() {
  try { localStorage.setItem(SAVE_KEY, JSON.stringify(save)); } catch (e) { /* хранилище недоступно */ }
}
audio.enabled = save.sound;

// ================= Сцена =================
const canvas = $('c');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
const FOG = 0x8fc5e8;
const scene = new THREE.Scene();
scene.background = new THREE.Color(FOG);
scene.fog = new THREE.Fog(FOG, 300, 560);
const camera = new THREE.PerspectiveCamera(50, 1, 1, 1400);
scene.add(new THREE.HemisphereLight(0xe3f4ff, 0x35597a, 1.5));
const sun = new THREE.DirectionalLight(0xfff0d0, 2.2);
sun.position.set(80, 160, 70);
scene.add(sun);

let W = innerWidth, H = innerHeight;
function resize() {
  W = innerWidth; H = innerHeight;
  renderer.setSize(W, H, false);
  camera.aspect = W / H;
  camera.fov = W / H < 0.75 ? 54 : 44;
  camera.updateProjectionMatrix();
}
addEventListener('resize', resize);
resize();

// ================= Мир =================
const water = makeWater();
scene.add(water.mesh);

const islands = [];
PORTS.forEach((p, i) => {
  const g = makeIsland(p.x, p.z, p.r, 100 + i, { lighthouse: i === 0 });
  scene.add(g);
  islands.push({ x: p.x, z: p.z, r: p.r * 1.06, port: i, group: g });
});
{
  const wr = seeded(4242);
  let tries = 0;
  while (islands.length < PORTS.length + 18 && tries < 800) {
    tries++;
    const x = (wr() * 2 - 1) * (WORLD - 90);
    const z = (wr() * 2 - 1) * (WORLD - 90);
    const r = 16 + wr() * 40;
    if (islands.some((o) => Math.hypot(o.x - x, o.z - z) < o.r + r + 80)) continue;
    const g = makeIsland(x, z, r, 1000 + tries, null);
    scene.add(g);
    islands.push({ x, z, r: r * 1.06, port: -1, group: g });
  }
}

const labelsEl = $('labels');
const ports = PORTS.map((p, i) => {
  const rx = p.x, rz = p.z + p.r * 1.17 + 14;
  const geo = new THREE.RingGeometry(9, 10.6, 40);
  geo.rotateX(-Math.PI / 2);
  const ring = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.75, depthWrite: false }));
  ring.position.set(rx, 0.9, rz);
  scene.add(ring);
  const label = document.createElement('div');
  label.className = 'portLabel';
  label.textContent = '⚓ ' + p.name;
  labelsEl.appendChild(label);
  return { ...p, i, rx, rz, ring, label };
});

// ================= Частицы =================
const pools = new Map();
function getMesh(geo, material) {
  const key = geo.uuid + material.uuid;
  let arr = pools.get(key);
  if (!arr) { arr = []; pools.set(key, arr); }
  let m = arr.pop();
  if (!m) {
    m = new THREE.Mesh(geo, material);
    m.userData.poolKey = key;
    scene.add(m);
  }
  m.visible = true;
  return m;
}
function releaseMesh(m) {
  m.visible = false;
  pools.get(m.userData.poolKey).push(m);
}

const G = {
  sphere: new THREE.IcosahedronGeometry(1, 0),
  foam: new THREE.CircleGeometry(1, 7).rotateX(-Math.PI / 2),
  box: new THREE.BoxGeometry(1, 1, 1),
  ball: new THREE.SphereGeometry(0.5, 6, 4),
};
const basic = (color, opacity = 1) => new THREE.MeshBasicMaterial({ color, transparent: opacity < 1, opacity, depthWrite: opacity >= 1 });
const M = {
  foam: basic(0xffffff, 0.55),
  splash: basic(0xeaf7ff, 0.9),
  smoke: basic(0xb0b0b0, 0.75),
  dark: basic(0x3a3a3a, 0.8),
  fire: basic(0xff8a1f),
  fire2: basic(0xffd04a),
  wood: new THREE.MeshLambertMaterial({ color: 0x6b4423, flatShading: true }),
  ball: basic(0x161616),
};

// Частицы рисуются инстансингом: одна отрисовка на каждую пару «форма + материал».
const kinds = new Map();
const dummy = new THREE.Object3D();
function kindFor(geo, material) {
  const key = geo.uuid + material.uuid;
  let k = kinds.get(key);
  if (!k) {
    const im = new THREE.InstancedMesh(geo, material, 400);
    im.count = 0;
    im.frustumCulled = false;
    scene.add(im);
    k = { im, list: [], flat: geo === G.foam };
    kinds.set(key, k);
  }
  return k;
}
function particle(geo, material, x, y, z, vx, vy, vz, size, life, mode = 'shrink', g = 0) {
  const k = kindFor(geo, material);
  if (k.list.length >= 400) return;
  k.list.push({ x, y, z, vx, vy, vz, size, life, max: life, mode, g, rx: Math.random() * 3, ry: Math.random() * 3 });
}
function updateParticles(dt) {
  for (const k of kinds.values()) {
    const list = k.list;
    let n = 0;
    for (let i = 0; i < list.length; i++) {
      const p = list[i];
      p.life -= dt;
      p.vy -= p.g * dt;
      p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt;
      if (p.life <= 0 || (p.g && p.y < 0)) continue;
      const f = p.life / p.max;
      let s;
      if (p.mode === 'puff') s = p.size * (0.5 + 1.6 * (1 - f)) * Math.min(1, f * 3);
      else if (p.mode === 'foam') s = p.size * (1 + 1.8 * (1 - f)) * Math.min(1, f * 2);
      else s = p.size * f;
      dummy.position.set(p.x, k.flat ? waveHeight(p.x, p.z, now) * 0.55 + 0.5 : p.y, p.z);
      if (k.flat) dummy.rotation.set(0, p.ry, 0);
      else dummy.rotation.set(p.rx, p.ry, 0);
      dummy.scale.setScalar(Math.max(0.001, s));
      dummy.updateMatrix();
      k.im.setMatrixAt(n, dummy.matrix);
      list[n++] = p;
    }
    list.length = n;
    k.im.count = n;
    k.im.instanceMatrix.needsUpdate = true;
  }
}

function fxSplash(x, z, big = 1) {
  for (let i = 0; i < 7 * big; i++) {
    particle(G.sphere, M.splash, x + rand(-1, 1), 0.5, z + rand(-1, 1), rand(-3, 3), rand(7, 13) * big, rand(-3, 3), rand(0.4, 0.8) * big, rand(0.6, 0.9), 'shrink', 26);
  }
  particle(G.foam, M.foam, x, 0.7, z, 0, 0, 0, 1.8 * big, 1.1, 'foam');
}
function fxExplosion(x, y, z, big = 1) {
  for (let i = 0; i < 6 * big; i++) {
    particle(G.sphere, i % 2 ? M.fire : M.fire2, x + rand(-1.5, 1.5), y + rand(0, 2), z + rand(-1.5, 1.5), rand(-2, 2), rand(1, 4), rand(-2, 2), rand(1, 2) * big, rand(0.3, 0.55), 'puff');
  }
  for (let i = 0; i < 4 * big; i++) {
    particle(G.sphere, M.dark, x + rand(-1, 1), y + 1, z + rand(-1, 1), rand(-1, 1), rand(3, 6), rand(-1, 1), rand(1.2, 2) * big, rand(0.8, 1.3), 'puff');
  }
  for (let i = 0; i < 4 * big; i++) {
    particle(G.box, M.wood, x, y + 1, z, rand(-8, 8), rand(6, 14), rand(-8, 8), rand(0.4, 0.9), rand(0.8, 1.3), 'shrink', 25);
  }
}
function fxMuzzle(x, z, dx, dz) {
  particle(G.sphere, M.fire2, x, 2.4, z, dx * 4, 0, dz * 4, 0.9, 0.15, 'shrink');
  for (let i = 0; i < 2; i++) {
    particle(G.sphere, M.smoke, x + dx * 1.5, 2.6, z + dz * 1.5, dx * rand(3, 6), rand(0.5, 2), dz * rand(3, 6), rand(1, 1.6), rand(0.7, 1.1), 'puff');
  }
}

// ================= Корабли =================
const ships = [];
let player = null;
let nextId = 1;
let now = 0;

function makeHpBar(cls) {
  const el = document.createElement('div');
  el.className = 'hp ' + cls;
  el.innerHTML = '<div></div>';
  labelsEl.appendChild(el);
  return el;
}

function createShip(def, stats, faction, x, z, h) {
  const mesh = makeShip(def.look);
  mesh.rotation.order = 'YXZ';
  scene.add(mesh);
  const s = {
    id: nextId++, def, stats, faction, type: null,
    x, z, h, desired: h, speed: 0, throttle: 0,
    hp: stats.hp, maxHp: stats.hp,
    len: def.look.len, width: def.look.width, radius: def.look.len * 0.36,
    reload: { '-1': rand(0.3, 1.2), '1': rand(0.3, 1.2) },
    alive: true, sinkT: 0, boostT: 0, boostCd: 0, lastHit: -99, wakeT: 0, smokeT: 0,
    ramT: {},
    ai: { state: 'wander', wx: x, wz: z, t: 0, side: Math.random() < 0.5 ? -1 : 1 },
    mesh,
    hpEl: makeHpBar(faction === 'player' ? 'me' : def.boss ? 'boss' : ''),
  };
  ships.push(s);
  return s;
}

function removeShip(s) {
  scene.remove(s.mesh);
  s.hpEl.remove();
  const i = ships.indexOf(s);
  if (i >= 0) ships.splice(i, 1);
}

const playerDef = () => PLAYER_SHIPS.find((s) => s.id === save.ship) || PLAYER_SHIPS[0];
const ensureUp = (id) => (save.up[id] = save.up[id] || { hull: 0, sails: 0, guns: 0, reload: 0 });

function spawnPlayer() {
  const p = ports[save.port] || ports[0];
  if (player) removeShip(player);
  const def = playerDef();
  player = createShip(def, shipStats(def, ensureUp(def.id)), 'player', p.rx, p.rz + 4, Math.PI);
  player.inPort = true;
  camSnap = true;
}

function rebuildPlayer() {
  const { x, z, h } = player;
  removeShip(player);
  const def = playerDef();
  player = createShip(def, shipStats(def, ensureUp(def.id)), 'player', x, z, h);
  player.inPort = true;
}

function spawnEnemy(type, x, z) {
  const def = ENEMIES[type];
  const s = createShip(def, { ...def }, 'pirate', x, z, rand(-Math.PI, Math.PI));
  s.type = type;
  return s;
}

function isWater(x, z, margin) {
  if (Math.abs(x) > WORLD - 30 || Math.abs(z) > WORLD - 30) return false;
  return !islands.some((o) => Math.hypot(o.x - x, o.z - z) < o.r + margin);
}

function findSpawnPoint(minD, maxD) {
  for (let k = 0; k < 40; k++) {
    const a = rand(0, Math.PI * 2), d = rand(minD, maxD);
    const x = player.x + Math.sin(a) * d, z = player.z + Math.cos(a) * d;
    if (!isWater(x, z, 25)) continue;
    if (ports.some((p) => Math.hypot(p.x - x, p.z - z) < p.r + 160)) continue;
    return { x, z };
  }
  return null;
}

function pickEnemyType(x, z) {
  const zone = Math.hypot(x, z) / WORLD;
  const w = {
    sloop: Math.max(0.15, 1 - zone * 1.3),
    brig: zone < 0.2 ? 0.15 : 0.7,
    frigate: zone > 0.4 ? (zone - 0.3) * 1.6 : 0,
    galleon: zone > 0.8 ? 0.06 : 0,
  };
  let sum = 0;
  for (const k in w) sum += w[k];
  let r = Math.random() * sum;
  for (const k in w) { r -= w[k]; if (r <= 0) return k; }
  return 'sloop';
}

// ================= Ввод =================
const joy = { active: false, id: null, ox: 0, oy: 0, jx: 0, jy: 0, mag: 0 };
const JR = 85;
const joyEl = $('joy'), knobEl = $('joyKnob'), triEl = $('joyTri');
const keys = new Set();

canvas.addEventListener('pointerdown', (e) => {
  unlockAudio();
  if (state !== 'play' || joy.active) return;
  joy.active = true; joy.id = e.pointerId;
  joy.ox = e.clientX; joy.oy = e.clientY;
  joy.mag = 0;
  joyEl.style.left = joy.ox + 'px';
  joyEl.style.top = joy.oy + 'px';
  knobEl.style.transform = 'translate(0px, 0px)';
  triEl.style.display = 'none';
  joyEl.style.display = 'block';
});
addEventListener('pointermove', (e) => {
  if (!joy.active || e.pointerId !== joy.id) return;
  let dx = e.clientX - joy.ox, dy = e.clientY - joy.oy;
  const d = Math.hypot(dx, dy);
  joy.mag = Math.min(1, d / JR);
  if (d > 0) { joy.jx = dx / d; joy.jy = dy / d; }
  if (d > JR) { dx *= JR / d; dy *= JR / d; }
  knobEl.style.transform = `translate(${dx}px, ${dy}px)`;
  const deg = (Math.atan2(joy.jx, -joy.jy) * 180) / Math.PI;
  triEl.style.display = joy.mag > 0.12 ? 'block' : 'none';
  triEl.style.transform = `rotate(${deg}deg) translate(-26px, -150px)`;
});
function endJoy(e) {
  if (!joy.active || e.pointerId !== joy.id) return;
  joy.active = false; joy.mag = 0;
  joyEl.style.display = 'none';
}
addEventListener('pointerup', endJoy);
addEventListener('pointercancel', endJoy);
addEventListener('keydown', (e) => { keys.add(e.key.toLowerCase()); if (e.key === ' ') tryBoost(); if (e.key === 'Escape') onBack(); });
addEventListener('keyup', (e) => keys.delete(e.key.toLowerCase()));

$('boostBtn').addEventListener('pointerdown', (e) => { e.stopPropagation(); unlockAudio(); tryBoost(); });
$('pauseBtn').addEventListener('click', () => { unlockAudio(); openPause(); });

function tryBoost() {
  if (state !== 'play' || !player || player.sinkT > 0 || player.boostCd > 0) return;
  player.boostT = 2.2;
  player.boostCd = 7;
  sfx.boost();
}

function controlPlayer() {
  if (!player || player.sinkT > 0) return;
  let jx = 0, jy = 0, mag = 0;
  if (joy.active && joy.mag > 0.12) { jx = joy.jx; jy = joy.jy; mag = joy.mag; }
  const kx = (keys.has('d') || keys.has('arrowright') ? 1 : 0) - (keys.has('a') || keys.has('arrowleft') ? 1 : 0);
  const ky = (keys.has('s') || keys.has('arrowdown') ? 1 : 0) - (keys.has('w') || keys.has('arrowup') ? 1 : 0);
  if (kx || ky) { const d = Math.hypot(kx, ky); jx = kx / d; jy = ky / d; mag = 1; }
  if (mag > 0) {
    player.desired = Math.atan2(jx, -jy);
    player.throttle = mag;
  } else {
    player.throttle = 0;
  }
}

// ================= Физика кораблей =================
function updateShip(s, dt) {
  const st = s.stats;
  if (s.boostT > 0) s.boostT -= dt;
  if (s.boostCd > 0) s.boostCd -= dt;

  if (s.sinkT > 0) {
    s.sinkT += dt;
    s.speed *= 1 - dt;
  } else {
    const diff = wrapAngle(s.desired - s.h);
    const turnRate = st.turn * (0.45 + 0.55 * Math.min(1, Math.abs(s.speed) / (st.speed * 0.6)));
    s.h = wrapAngle(s.h + clamp(diff, -turnRate * dt, turnRate * dt));
    const maxSp = st.speed * (s.boostT > 0 ? 1.7 : 1);
    const targetSp = maxSp * s.throttle * (Math.abs(diff) > 1.8 ? 0.55 : 1);
    s.speed += (targetSp - s.speed) * Math.min(1, dt * (targetSp > s.speed ? (s.boostT > 0 ? 3 : 0.9) : 0.7));
  }
  s.x += Math.sin(s.h) * s.speed * dt;
  s.z += -Math.cos(s.h) * s.speed * dt;

  // Границы мира
  const lim = WORLD - 20;
  if (Math.abs(s.x) > lim) { s.x = Math.sign(s.x) * lim; s.speed *= 0.9; }
  if (Math.abs(s.z) > lim) { s.z = Math.sign(s.z) * lim; s.speed *= 0.9; }

  // Острова
  for (const isl of islands) {
    const dx = s.x - isl.x, dz = s.z - isl.z;
    const d = Math.hypot(dx, dz);
    const min = isl.r + s.radius * 0.55;
    if (d < min && d > 0.01) {
      const k = (min - d) / d;
      s.x += dx * k; s.z += dz * k;
      if (s === player && s.speed > 9 && s.sinkT === 0) damage(s, s.speed * 0.6, null, false);
      s.speed *= 0.6;
    }
  }

  // Регенерация вне боя
  if (s.sinkT === 0 && now - s.lastHit > 6 && s.hp < s.maxHp) s.hp = Math.min(s.maxHp, s.hp + s.maxHp * 0.02 * dt);

  // Положение модели: качка на волнах
  const m = s.mesh;
  const bob = waveHeight(s.x, s.z, now) * 0.55;
  const turnLean = clamp(wrapAngle(s.desired - s.h), -1, 1) * Math.min(1, s.speed / 10) * 0.09;
  m.position.set(s.x, bob - (s.sinkT > 0 ? s.sinkT * s.sinkT * 1.6 : 0), s.z);
  m.rotation.y = -s.h;
  m.rotation.z = Math.sin(now * 1.3 + s.id) * 0.045 - turnLean + (s.sinkT > 0 ? Math.min(0.9, s.sinkT * 0.35) : 0);
  m.rotation.x = Math.sin(now * 1.05 + s.id * 2) * 0.03 + (s.sinkT > 0 ? Math.min(0.35, s.sinkT * 0.12) : 0);

  // След за кормой
  s.wakeT -= dt;
  if (s.speed > 3 && s.wakeT <= 0 && s.sinkT === 0) {
    s.wakeT = 0.06;
    const bx = s.x - Math.sin(s.h) * s.len * 0.48, bz = s.z + Math.cos(s.h) * s.len * 0.48;
    particle(G.foam, M.foam, bx + rand(-0.6, 0.6), 0.7, bz + rand(-0.6, 0.6), 0, 0, 0, s.width * 0.32, 1.4, 'foam');
    const sx = s.x + Math.sin(s.h) * s.len * 0.45, sz = s.z - Math.cos(s.h) * s.len * 0.45;
    if (Math.random() < 0.5) particle(G.foam, M.foam, sx + rand(-1, 1), 0.7, sz + rand(-1, 1), 0, 0, 0, s.width * 0.18, 0.7, 'foam');
  }
  // Дым от повреждений и пожар при потоплении
  s.smokeT -= dt;
  if (s.smokeT <= 0 && (s.hp < s.maxHp * 0.35 || s.sinkT > 0)) {
    s.smokeT = s.sinkT > 0 ? 0.05 : 0.18;
    const y = m.position.y + s.width * 0.6;
    particle(G.sphere, M.dark, s.x + rand(-2, 2), y, s.z + rand(-2, 2), rand(-0.5, 0.5), rand(3, 5), rand(-0.5, 0.5), rand(1, 1.8), 1.4, 'puff');
    if (s.sinkT > 0 || Math.random() < 0.4) particle(G.sphere, M.fire, s.x + rand(-2, 2), y, s.z + rand(-2, 2), 0, rand(2, 4), 0, rand(0.6, 1.1), 0.45, 'puff');
  }
}

function shipCollisions() {
  for (let i = 0; i < ships.length; i++) {
    const a = ships[i];
    if (a.sinkT > 0) continue;
    for (let j = i + 1; j < ships.length; j++) {
      const b = ships[j];
      if (b.sinkT > 0) continue;
      const dx = b.x - a.x, dz = b.z - a.z;
      const d = Math.hypot(dx, dz);
      const min = a.radius + b.radius;
      if (d >= min || d < 0.01) continue;
      const nx = dx / d, nz = dz / d;
      const ma = a.stats.mass, mb = b.stats.mass, total = ma + mb;
      const overlap = min - d;
      a.x -= nx * overlap * (mb / total); a.z -= nz * overlap * (mb / total);
      b.x += nx * overlap * (ma / total); b.z += nz * overlap * (ma / total);

      const fax = Math.sin(a.h), faz = -Math.cos(a.h), fbx = Math.sin(b.h), fbz = -Math.cos(b.h);
      const rel = (fax * a.speed - fbx * b.speed) * nx + (faz * a.speed - fbz * b.speed) * nz;
      if (rel < 3) continue;
      if ((a.ramT[b.id] || 0) > now) continue;
      a.ramT[b.id] = now + 0.8;
      if (a.faction === b.faction) { a.speed *= 0.5; b.speed *= 0.7; continue; }
      const base = rel * 3.2;
      const ramA = fax * nx + faz * nz > 0.5;
      const ramB = -(fbx * nx + fbz * nz) > 0.5;
      if (ramA) damage(b, (base * ma * (a.boostT > 0 ? 2 : 1)) / Math.sqrt(mb), a, true);
      if (ramB) damage(a, (base * mb * (b.boostT > 0 ? 2 : 1)) / Math.sqrt(ma), b, true);
      if (ramA) damage(a, base * 0.2, null, false);
      if (ramB) damage(b, base * 0.2, null, false);
      if (!ramA && !ramB) { damage(a, base * 0.35, b, true); damage(b, base * 0.35, a, true); }
      fxExplosion((a.x + b.x) / 2, 2, (a.z + b.z) / 2, 0.7);
      sfx.hit(volumeAt(a.x, a.z));
      shake = Math.max(shake, a === player || b === player ? 0.5 : 0);
      a.speed *= 0.35;
      b.speed = b.speed * 0.5 + rel * 0.2 * (ma / total);
    }
  }
}

// ================= Бой =================
const projectiles = [];

function volumeAt(x, z) {
  if (!player) return 0.5;
  const d = Math.hypot(player.x - x, player.z - z);
  return clamp(1 - d / 260, 0.05, 1);
}

function fireBroadside(s, side, target) {
  const st = s.stats;
  s.reload[side] = st.reload;
  const fx = Math.sin(s.h), fz = -Math.cos(s.h);
  const rx = Math.cos(s.h) * side, rz = Math.sin(s.h) * side;
  const n = st.cannons;
  for (let i = 0; i < n; i++) {
    const off = (n === 1 ? 0 : i / (n - 1) - 0.5) * s.len * 0.55;
    const sx = s.x + rx * s.width * 0.6 + fx * off;
    const sz = s.z + rz * s.width * 0.6 + fz * off;
    const d = Math.hypot(target.x - sx, target.z - sz);
    const T = clamp(d / 55, 0.35, 1.3);
    const spread = (s.faction === 'player' ? 1.5 : 2.5) + d * 0.045;
    const tx = target.x + Math.sin(target.h) * target.speed * T + rand(-spread, spread);
    const tz = target.z - Math.cos(target.h) * target.speed * T + rand(-spread, spread);
    projectiles.push({
      owner: s, faction: s.faction, sx, sz, tx, tz, dx: rx, dz: rz,
      t: -(i * 0.07 + rand(0, 0.05)), T, arc: 2 + T * 7,
      dmg: st.dmg * rand(0.85, 1.15), mesh: null,
    });
  }
  sfx.cannon(volumeAt(s.x, s.z));
}

function updateFiring(s, dt) {
  s.reload['-1'] -= dt;
  s.reload['1'] -= dt;
  if (s.sinkT > 0) return;
  let foes;
  if (s.faction === 'player') foes = ships.filter((o) => o.faction === 'pirate' && o.sinkT === 0);
  else foes = player && player.sinkT === 0 && !player.safe && s.ai.state === 'attack' ? [player] : [];
  for (const side of [-1, 1]) {
    if (s.reload[side] > 0) continue;
    const rx = Math.cos(s.h) * side, rz = Math.sin(s.h) * side;
    let best = null, bd = Infinity;
    for (const o of foes) {
      const dx = o.x - s.x, dz = o.z - s.z, d = Math.hypot(dx, dz);
      if (d > s.stats.range || d < 2) continue;
      if ((dx * rx + dz * rz) / d < 0.42) continue;
      if (d < bd) { bd = d; best = o; }
    }
    if (best) fireBroadside(s, side, best);
  }
}

function updateProjectiles(dt) {
  for (let i = projectiles.length - 1; i >= 0; i--) {
    const p = projectiles[i];
    p.t += dt;
    if (p.t < 0) continue;
    if (!p.mesh) {
      p.mesh = getMesh(G.ball, M.ball);
      p.mesh.scale.setScalar(1);
      fxMuzzle(p.sx, p.sz, p.dx, p.dz);
    }
    const k = Math.min(1, p.t / p.T);
    p.mesh.position.set(p.sx + (p.tx - p.sx) * k, 2.4 * (1 - k) + 4 * p.arc * k * (1 - k), p.sz + (p.tz - p.sz) * k);
    if (k < 1) continue;
    releaseMesh(p.mesh);
    projectiles.splice(i, 1);
    let hit = null;
    for (const s of ships) {
      if (s.faction === p.faction || s.sinkT > 0) continue;
      if (Math.hypot(s.x - p.tx, s.z - p.tz) < s.radius + 1.3) { hit = s; break; }
    }
    if (hit) {
      fxExplosion(p.tx, 2, p.tz, 0.6);
      damage(hit, p.dmg, p.owner, false);
      if (Math.random() < 0.4) sfx.hit(volumeAt(p.tx, p.tz) * 0.7);
    } else {
      fxSplash(p.tx, p.tz);
      if (Math.random() < 0.3) sfx.splash(volumeAt(p.tx, p.tz));
    }
  }
}

function damage(s, amount, src, ram) {
  if (s.sinkT > 0) return;
  amount = Math.max(1, Math.round(amount));
  s.hp -= amount;
  s.lastHit = now;
  floatText(s.x, 4 + s.width * 0.5, s.z, '-' + amount, s === player ? 'bad' : 'dmg');
  if (s === player) {
    $('damageFlash').style.opacity = 1;
    setTimeout(() => ($('damageFlash').style.opacity = 0), 140);
    shake = Math.max(shake, 0.25);
  }
  if (s.hp <= 0) {
    s.hp = 0;
    sink(s, src, ram);
  }
}

function sink(s, src, ram) {
  s.sinkT = 0.001;
  s.alive = false;
  fxExplosion(s.x, 3, s.z, 1.5);
  sfx.sink();
  if (s.faction === 'pirate') {
    const def = s.def;
    dropLoot(s.x, s.z, randInt(def.coins[0], def.coins[1]), def.gems);
    if (src === player) {
      save.kills++;
      questEvent('kill', s.type, ram);
      if (ram) toast('💥 Таран!');
    }
  } else {
    setTimeout(showDeath, 2000);
  }
}

// ================= Добыча =================
const loot = [];
function dropLoot(x, z, coins, gems) {
  const n = clamp(Math.ceil(coins / 70), 1, 7);
  for (let i = 0; i < n; i++) {
    const mesh = makeChest();
    scene.add(mesh);
    loot.push({ kind: 'coin', value: Math.round(coins / n), x: x + rand(-7, 7), z: z + rand(-7, 7), mesh, t: rand(0, 6), age: 0 });
  }
  let g = gems >= 1 ? Math.round(gems) : Math.random() < gems ? 1 : 0;
  for (let i = 0; i < g; i++) {
    const mesh = makeGem();
    scene.add(mesh);
    loot.push({ kind: 'gem', value: 1, x: x + rand(-6, 6), z: z + rand(-6, 6), mesh, t: rand(0, 6), age: 0 });
  }
}

function updateLoot(dt) {
  for (let i = loot.length - 1; i >= 0; i--) {
    const l = loot[i];
    l.t += dt;
    l.age += dt;
    if (player && player.sinkT === 0) {
      const dx = player.x - l.x, dz = player.z - l.z, d = Math.hypot(dx, dz);
      if (d < 24) {
        const sp = 30 + (24 - d) * 3;
        l.x += (dx / d) * sp * dt;
        l.z += (dz / d) * sp * dt;
      }
      if (d < player.radius * 0.6 + 2) {
        collect(l);
        scene.remove(l.mesh);
        loot.splice(i, 1);
        continue;
      }
    }
    if (l.age > 120) {
      scene.remove(l.mesh);
      loot.splice(i, 1);
      continue;
    }
    l.mesh.position.set(l.x, waveHeight(l.x, l.z, now) * 0.6 + 0.9 + Math.sin(l.t * 3) * 0.3, l.z);
    l.mesh.rotation.y += dt * 1.5;
  }
}

function collect(l) {
  if (l.kind === 'coin') {
    save.coins += l.value;
    floatText(l.x, 4, l.z, '+' + l.value, 'gold');
    sfx.coin();
    questEvent('collect', l.value);
  } else {
    save.gems += 1;
    floatText(l.x, 4, l.z, '+1 💎', 'gemt');
    sfx.gem();
  }
}

// ================= ИИ пиратов =================
function updateAI(s, dt) {
  if (s.sinkT > 0) return;
  const ai = s.ai;
  ai.t -= dt;
  const p = player;
  const canAttack = p && p.sinkT === 0 && !p.safe;
  const dx = p.x - s.x, dz = p.z - s.z, d = Math.hypot(dx, dz);
  const aggro = s.def.boss ? 280 : 175;
  if (canAttack && (d < aggro || (s.lastHit > now - 10 && d < aggro * 2))) ai.state = 'attack';
  else if (ai.state === 'attack' && (!canAttack || d > aggro * 1.8)) ai.state = 'wander';

  if (ai.state === 'attack') {
    const toP = Math.atan2(dx, -dz);
    const R = s.stats.range * 0.7;
    if (d > R * 1.3) { s.desired = toP; s.throttle = 1; }
    else { s.desired = toP + ai.side * (Math.PI / 2) * (d < R * 0.6 ? 1.3 : 1); s.throttle = 0.8; }
    if (Math.random() < dt * 0.05) ai.side *= -1;
  } else {
    if (ai.t <= 0 || Math.hypot(ai.wx - s.x, ai.wz - s.z) < 25) {
      ai.t = rand(8, 16);
      ai.wx = clamp(s.x + rand(-250, 250), -WORLD + 80, WORLD - 80);
      ai.wz = clamp(s.z + rand(-250, 250), -WORLD + 80, WORLD - 80);
    }
    s.desired = Math.atan2(ai.wx - s.x, -(ai.wz - s.z));
    s.throttle = 0.45;
  }
  // Пираты не заходят в гавани
  for (const port of ports) {
    if (Math.hypot(s.x - port.x, s.z - port.z) < port.r + 120) {
      s.desired = Math.atan2(s.x - port.x, -(s.z - port.z));
      ai.state = 'wander';
      ai.t = 0;
    }
  }
  // Обход островов
  const fx = Math.sin(s.h), fz = -Math.cos(s.h);
  for (const isl of islands) {
    const lx = s.x + fx * 32, lz = s.z + fz * 32;
    if (Math.hypot(lx - isl.x, lz - isl.z) < isl.r + 14) {
      const cross = fx * (isl.z - s.z) - fz * (isl.x - s.x);
      s.desired = s.h + (cross > 0 ? 1 : -1) * 1.2;
      break;
    }
  }
}

// ================= Задания =================
const QUEST_ICONS = { kill: '☠️', ram: '💥', collect: '💰', reach: '⚓', upgrade: '🔧', buy: '⛵' };
const quest = () => questAt(save.quest);

function questEvent(kind, a, b) {
  const q = quest();
  let inc = 0;
  if (kind === 'kill' && q.type === 'kill' && q.enemy === a) inc = 1;
  if (kind === 'kill' && q.type === 'ram' && q.enemy === a && b) inc = 1;
  if (kind === 'collect' && q.type === 'collect') inc = a;
  if (kind === 'reach' && q.type === 'reach' && q.port === a) inc = 1;
  if (kind === 'upgrade' && q.type === 'upgrade') inc = 1;
  if (kind === 'buy' && q.type === 'buy') inc = 1;
  if (!inc) return;
  save.qprog += inc;
  if (save.qprog >= (q.n || 1)) {
    const r = q.reward;
    save.coins += r.coins || 0;
    save.gems += r.gems || 0;
    save.quest++;
    save.qprog = 0;
    const parts = [];
    if (r.coins) parts.push('+' + r.coins + ' 🪙');
    if (r.gems) parts.push('+' + r.gems + ' 💎');
    toast('✅ Задание выполнено!\n' + parts.join('   '), 2600);
    sfx.quest();
    $('quest').classList.remove('done');
    void $('quest').offsetWidth;
    $('quest').classList.add('done');
  }
  persist();
  questUI();
}

function questUI() {
  const q = quest();
  $('questIcon').textContent = QUEST_ICONS[q.type];
  $('questText').textContent = q.text;
  $('questBar').style.width = Math.min(100, (save.qprog / (q.n || 1)) * 100) + '%';
}

function questTarget() {
  const q = quest();
  if (!player) return null;
  if (q.type === 'kill' || q.type === 'ram') {
    let best = null, bd = Infinity;
    for (const s of ships) {
      if (s.faction !== 'pirate' || s.sinkT > 0 || s.type !== q.enemy) continue;
      const d = Math.hypot(s.x - player.x, s.z - player.z);
      if (d < bd) { bd = d; best = s; }
    }
    return best ? { x: best.x, z: best.z } : null;
  }
  if (q.type === 'reach') return { x: ports[q.port].rx, z: ports[q.port].rz };
  if (q.type === 'upgrade' || q.type === 'buy') {
    let best = null, bd = Infinity;
    for (const p of ports) {
      const d = Math.hypot(p.rx - player.x, p.rz - player.z);
      if (d < bd) { bd = d; best = p; }
    }
    return { x: best.rx, z: best.rz };
  }
  if (q.type === 'collect') {
    let best = null, bd = 500;
    for (const l of loot) {
      const d = Math.hypot(l.x - player.x, l.z - player.z);
      if (d < bd) { bd = d; best = l; }
    }
    if (best) return { x: best.x, z: best.z };
    let bs = null; bd = Infinity;
    for (const s of ships) {
      if (s.faction !== 'pirate' || s.sinkT > 0) continue;
      const d = Math.hypot(s.x - player.x, s.z - player.z);
      if (d < bd) { bd = d; bs = s; }
    }
    return bs ? { x: bs.x, z: bs.z } : null;
  }
  return null;
}

// ================= Появление пиратов =================
let spawnTimer = 0;
function manageSpawns(dt) {
  spawnTimer -= dt;
  if (spawnTimer > 0 || !player) return;
  spawnTimer = 1;
  // Убираем далёких
  for (const s of [...ships]) {
    if (s.faction === 'pirate' && Math.hypot(s.x - player.x, s.z - player.z) > 650) removeShip(s);
  }
  const pirates = ships.filter((s) => s.faction === 'pirate' && s.sinkT === 0);
  const zone = Math.hypot(player.x, player.z) / WORLD;
  const want = 5 + Math.round(zone * 3);
  if (pirates.length < want) {
    const pt = findSpawnPoint(240, 400);
    if (pt) spawnEnemy(pickEnemyType(pt.x, pt.z), pt.x, pt.z);
  }
  // Цель задания должна существовать
  const q = quest();
  if ((q.type === 'kill' || q.type === 'ram') && !pirates.some((s) => s.type === q.enemy)) {
    const pt = findSpawnPoint(260, 380);
    if (pt) spawnEnemy(q.enemy, pt.x, pt.z);
  }
}

// ================= Интерфейс =================
const tmpV = new THREE.Vector3();
function toScreen(x, y, z) {
  tmpV.set(x, y, z).project(camera);
  return { x: ((tmpV.x + 1) / 2) * W, y: ((1 - tmpV.y) / 2) * H, ok: tmpV.z < 1 };
}

function floatText(x, y, z, text, cls) {
  const p = toScreen(x, y, z);
  if (!p.ok || p.x < -50 || p.x > W + 50 || p.y < -50 || p.y > H + 50) return;
  const el = document.createElement('div');
  el.className = 'float ' + cls;
  el.textContent = text;
  el.style.left = p.x + rand(-12, 12) + 'px';
  el.style.top = p.y + 'px';
  labelsEl.appendChild(el);
  setTimeout(() => el.remove(), 1000);
}

let toastTimer = null;
function toast(text, ms = 1600) {
  const el = $('toast');
  el.textContent = text;
  el.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), ms);
}

let shownCoins = -1, shownGems = -1;
function updateHUD() {
  if (shownCoins !== save.coins) { shownCoins = save.coins; $('coins').textContent = fmt(save.coins); }
  if (shownGems !== save.gems) { shownGems = save.gems; $('gems').textContent = fmt(save.gems); }
  const cd = player ? Math.max(0, player.boostCd) / 7 : 0;
  $('boostCd').style.setProperty('--p', cd * 100 + '%');
  $('boostBtn').classList.toggle('active', !!player && player.boostT > 0);

  // HP-полоски
  for (const s of ships) {
    const show = s.sinkT === 0 && (s === player || s.hp < s.maxHp || s.ai.state === 'attack');
    const p = show ? toScreen(s.x, 6 + s.len * 0.75, s.z) : null;
    if (!p || !p.ok || p.x < -60 || p.x > W + 60 || p.y < -20 || p.y > H + 20) {
      s.hpEl.style.display = 'none';
      continue;
    }
    s.hpEl.style.display = 'block';
    s.hpEl.style.transform = `translate(${p.x}px, ${p.y}px)`;
    s.hpEl.firstChild.style.width = (s.hp / s.maxHp) * 100 + '%';
  }

  // Подписи портов
  for (const port of ports) {
    const d = player ? Math.hypot(port.rx - player.x, port.rz - player.z) : 999;
    const p = toScreen(port.rx, 8, port.rz);
    const vis = d < 300 && p.ok && p.x > -100 && p.x < W + 100 && p.y > 0 && p.y < H;
    port.label.style.display = vis ? 'block' : 'none';
    if (vis) port.label.style.transform = `translate(${p.x}px, ${p.y}px) translate(-50%, -100%)`;
  }

  // Указатель на цель задания
  const t = questTarget();
  const arrow = $('arrow');
  if (t && player && player.sinkT === 0) {
    const d = Math.hypot(t.x - player.x, t.z - player.z);
    $('targetDist').textContent = Math.round(d) + ' м';
    $('targetIcon').textContent = QUEST_ICONS[quest().type];
    $('target').style.display = 'block';
    const ps = toScreen(player.x, 0, player.z);
    const ts = toScreen(t.x, 0, t.z);
    let ax = ts.x - ps.x, ay = ts.y - ps.y;
    if (!ts.ok) { ax = -ax; ay = -ay; }
    const al = Math.hypot(ax, ay) || 1;
    if (d > 35) {
      const r = 70 + player.len * 1.5;
      arrow.style.display = 'block';
      arrow.style.left = ps.x + (ax / al) * r - 14 + 'px';
      arrow.style.top = ps.y + (ay / al) * r - 15 + 'px';
      arrow.style.transform = `rotate(${(Math.atan2(ax, -ay) * 180) / Math.PI}deg)`;
    } else arrow.style.display = 'none';
  } else {
    $('target').style.display = 'none';
    arrow.style.display = 'none';
  }
}

// ================= Порт =================
let portIndex = 0;
let portTab = 'ships';

function openPort(i) {
  state = 'port';
  portIndex = i;
  joy.active = false;
  $('joy').style.display = 'none';
  player.speed = 0;
  player.inPort = true;
  player.hp = player.maxHp;
  save.port = i;
  questEvent('reach', i);
  persist();
  renderPort();
  $('portMenu').classList.remove('hidden');
}

function closePort() {
  $('portMenu').classList.add('hidden');
  state = 'play';
  persist();
}
$('sailBtn').addEventListener('click', () => { sfx.click(); closePort(); });
document.querySelectorAll('.tab').forEach((t) =>
  t.addEventListener('click', () => {
    sfx.click();
    portTab = t.dataset.tab;
    renderPort();
  })
);

const MAXS = { hp: 1000, speed: 19, cannons: 12, dmg: 24 };
function statRow(name, v, max, shown) {
  return `<div class="stat"><span>${name}</span><div class="sb"><div style="width:${Math.min(100, (v / max) * 100)}%"></div></div><b>${shown}</b></div>`;
}

function renderPort() {
  $('portName').textContent = '⚓ ' + ports[portIndex].name;
  $('portSub').innerHTML = `<i class="coin sm"></i> ${fmt(save.coins)} &nbsp; <i class="gem sm"></i> ${fmt(save.gems)} &nbsp;·&nbsp; корабль отремонтирован`;
  document.querySelectorAll('.tab').forEach((t) => t.classList.toggle('on', t.dataset.tab === portTab));
  const c = $('portContent');
  let html = '';
  if (portTab === 'ships') {
    for (const sdef of PLAYER_SHIPS) {
      const owned = save.owned.includes(sdef.id);
      const current = save.ship === sdef.id;
      const st = shipStats(sdef, owned ? save.up[sdef.id] : null);
      let btn;
      if (current) btn = `<button class="big gray" disabled>✔ Ваш корабль</button>`;
      else if (owned) btn = `<button class="big blue" data-act="select" data-id="${sdef.id}">Выбрать</button>`;
      else {
        const can = save.coins >= sdef.price && save.gems >= sdef.gems;
        btn = `<button class="big ${can ? '' : 'gray'}" data-act="buy" data-id="${sdef.id}">Купить ${fmt(sdef.price)} <i class="coin sm"></i>${sdef.gems ? ` + ${sdef.gems} <i class="gem sm"></i>` : ''}</button>`;
      }
      html += `<div class="card ${current ? 'current' : ''}">
        <div class="cls">${sdef.cls}</div><h3>${sdef.name}</h3><p>${sdef.desc}</p>
        ${statRow('Прочность', st.hp, MAXS.hp, st.hp)}
        ${statRow('Скорость', st.speed, MAXS.speed, st.speed.toFixed(1))}
        ${statRow('Пушки', st.cannons, MAXS.cannons, st.cannons + '×2')}
        ${statRow('Урон', st.dmg, MAXS.dmg, Math.round(st.dmg))}
        ${statRow('Таран', sdef.mass, 5, sdef.mass.toFixed(1))}
        ${btn}</div>`;
    }
  } else {
    const def = playerDef();
    const up = ensureUp(def.id);
    html += `<div class="card"><div class="cls">${def.cls}</div><h3>${def.name}</h3><p>Улучшения действуют только на этот корабль.</p></div>`;
    for (const u of UPGRADES) {
      const lvl = up[u.id];
      const pips = Array.from({ length: MAX_LEVEL }, (_, k) => `<i class="${k < lvl ? 'on' : ''}"></i>`).join('');
      let btn;
      if (lvl >= MAX_LEVEL) btn = `<button class="big gray" disabled>МАКС</button>`;
      else {
        const cost = upgradeCost(def, lvl);
        btn = `<button class="big ${save.coins >= cost ? '' : 'gray'}" data-act="up" data-id="${u.id}">${fmt(cost)} <i class="coin sm"></i></button>`;
      }
      html += `<div class="card uprow"><div class="ic">${u.icon}</div><div class="info"><h3>${u.name}</h3>
        <div class="pips">${pips}</div><p style="margin:0">+${Math.round(u.per * 100)}% ${u.text} за уровень</p></div>${btn}</div>`;
    }
  }
  c.innerHTML = html;
  c.querySelectorAll('button[data-act]').forEach((b) => b.addEventListener('click', () => portAction(b.dataset.act, b.dataset.id)));
}

function portAction(act, id) {
  if (act === 'select') {
    save.ship = id;
    rebuildPlayer();
    sfx.click();
  } else if (act === 'buy') {
    const def = PLAYER_SHIPS.find((s) => s.id === id);
    if (save.coins < def.price) return toast('Не хватает монет');
    if (save.gems < def.gems) return toast('Не хватает кристаллов');
    save.coins -= def.price;
    save.gems -= def.gems;
    save.owned.push(id);
    ensureUp(id);
    save.ship = id;
    rebuildPlayer();
    sfx.quest();
    toast('⛵ Новый корабль: ' + def.name + '!');
    questEvent('buy');
  } else if (act === 'up') {
    const def = playerDef();
    const up = ensureUp(def.id);
    const cost = upgradeCost(def, up[id]);
    if (up[id] >= MAX_LEVEL) return;
    if (save.coins < cost) return toast('Не хватает монет');
    save.coins -= cost;
    up[id]++;
    rebuildPlayer();
    sfx.coin();
    questEvent('upgrade');
  }
  persist();
  renderPort();
}

// ================= Пауза и гибель =================
function openPause() {
  if (state !== 'play') return;
  state = 'pause';
  joy.active = false;
  $('joy').style.display = 'none';
  $('pauseStats').textContent = `Потоплено пиратов: ${save.kills} · Заданий выполнено: ${save.quest}`;
  $('soundBtn').textContent = audio.enabled ? '🔊 Звук: вкл' : '🔇 Звук: выкл';
  $('pauseMenu').classList.remove('hidden');
  persist();
}
function closePause() {
  $('pauseMenu').classList.add('hidden');
  state = 'play';
}
$('resumeBtn').addEventListener('click', () => { sfx.click(); closePause(); });
$('soundBtn').addEventListener('click', () => {
  audio.enabled = !audio.enabled;
  save.sound = audio.enabled;
  $('soundBtn').textContent = audio.enabled ? '🔊 Звук: вкл' : '🔇 Звук: выкл';
  persist();
});
let resetArmed = false;
$('resetBtn').addEventListener('click', () => {
  if (!resetArmed) {
    resetArmed = true;
    $('resetBtn').textContent = '⚠ Нажмите ещё раз для сброса';
    setTimeout(() => { resetArmed = false; $('resetBtn').textContent = '↺ Начать заново'; }, 3000);
    return;
  }
  save = defaultSave();
  persist();
  location.reload();
});

let deathLoss = 0;
function showDeath() {
  state = 'dead';
  deathLoss = Math.floor(save.coins * 0.15);
  $('deathText').textContent = `Вы потеряли ${deathLoss} монет. Корабль отбуксируют в порт «${ports[save.port].name}».`;
  $('deathMenu').classList.remove('hidden');
}
$('respawnBtn').addEventListener('click', () => {
  save.coins -= deathLoss;
  $('deathMenu').classList.add('hidden');
  spawnPlayer();
  for (const s of [...ships]) {
    if (s.faction === 'pirate' && Math.hypot(s.x - player.x, s.z - player.z) < 280) removeShip(s);
  }
  state = 'play';
  persist();
});

function onBack() {
  if (state === 'port') { closePort(); return true; }
  if (state === 'pause') { closePause(); return true; }
  if (state === 'play') { openPause(); return true; }
  return true;
}
window.onAndroidBack = onBack;
window.gamePause = () => { persist(); if (state === 'play') openPause(); };
document.addEventListener('visibilitychange', () => { if (document.hidden) window.gamePause(); });

// ================= Камера и цикл =================
let state = 'play';
let shake = 0;
let camSnap = true;
const camPos = new THREE.Vector3();
const camOff = new THREE.Vector3(0, 118, 78);

function updateCamera(dt) {
  if (!player) return;
  const k = Math.pow(player.len / 12, 0.45) * (W / H > 1 ? 0.8 : 1);
  const tx = player.x + Math.sin(player.h) * player.speed * 0.6;
  const tz = player.z - Math.cos(player.h) * player.speed * 0.6;
  const target = new THREE.Vector3(tx + camOff.x * k, camOff.y * k, tz + camOff.z * k);
  if (camSnap) { camPos.copy(target); camSnap = false; }
  camPos.lerp(target, 1 - Math.exp(-dt * 3.5));
  camera.position.copy(camPos);
  if (shake > 0) {
    shake = Math.max(0, shake - dt);
    camera.position.x += rand(-1, 1) * shake * 3;
    camera.position.z += rand(-1, 1) * shake * 3;
  }
  camera.lookAt(camPos.x - camOff.x * k, 0, camPos.z - camOff.z * k - 8);
}

function updatePorts(dt) {
  if (!player) return;
  player.safe = false;
  for (const port of ports) {
    port.ring.scale.setScalar(1 + Math.sin(now * 3) * 0.05);
    port.ring.material.opacity = 0.55 + Math.sin(now * 3) * 0.2;
    port.ring.position.y = waveHeight(port.rx, port.rz, now) * 0.55 + 0.9;
    if (Math.hypot(player.x - port.x, player.z - port.z) < port.r + 110) player.safe = true;
    const d = Math.hypot(player.x - port.rx, player.z - port.rz);
    if (state === 'play' && player.sinkT === 0 && !player.inPort && d < 9) openPort(port.i);
    if (player.inPort && port.i === save.port && d > 18) player.inPort = false;
  }
}

function update(dt) {
  now += dt;
  controlPlayer();
  for (const s of ships) if (s.faction === 'pirate') updateAI(s, dt);
  for (const s of ships) updateShip(s, dt);
  shipCollisions();
  for (const s of ships) updateFiring(s, dt);
  updateProjectiles(dt);
  updateLoot(dt);
  manageSpawns(dt);
  updatePorts(dt);
  for (const s of [...ships]) if (s !== player && s.sinkT > 3.5) removeShip(s);
}

let last = performance.now();
let saveTimer = 0;
function frame(t) {
  const dt = Math.min(0.05, (t - last) / 1000);
  last = t;
  if (state === 'play') update(dt);
  else now += dt * 0.3; // волны шевелятся и на паузе
  updateParticles(dt);
  updateCamera(dt);
  water.material.uniforms.uTime.value = now;
  water.material.uniforms.uCam.value.copy(camera.position);
  water.mesh.position.set(Math.round(camPos.x / 10) * 10, 0, Math.round((camPos.z - 60) / 10) * 10);
  for (let i = 0; i < islands.length; i++) {
    islands[i].group.userData.foam.material.opacity = 0.25 + Math.sin(now * 1.6 + i) * 0.12;
  }
  updateHUD();
  renderer.render(scene, camera);
  saveTimer += dt;
  if (saveTimer > 5) { saveTimer = 0; persist(); }
  requestAnimationFrame(frame);
}

// ================= Старт =================
spawnPlayer();
for (let i = 0; i < 4; i++) {
  const pt = findSpawnPoint(230, 380);
  if (pt) spawnEnemy(i < 3 ? 'sloop' : 'brig', pt.x, pt.z);
}
questUI();
requestAnimationFrame((t) => {
  last = t;
  frame(t);
  $('loading').style.display = 'none';
});

// Для автоматических проверок
window.__game = { renderer,
  get player() { return player; }, get ships() { return ships; }, get save() { return save; },
  get state() { return state; }, get loot() { return loot; }, spawnEnemy, openPort, damage, ports,
};
