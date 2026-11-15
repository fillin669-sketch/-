'use strict';
// ================= 3D-ГРАФИКА (Three.js) =================
// Один WebGL-рендерер на всё приложение: его холст переносится между вкладками.
const G3 = { ok: false };
const V3 = (x, y, z) => new THREE.Vector3(x, y, z);
function rotX(v, a) { const c = Math.cos(a), s = Math.sin(a); return V3(v.x, v.y * c - v.z * s, v.y * s + v.z * c); }

function init3D() {
  if (G3.disabled || G3.lost) return false;
  if (G3.tried) return G3.ok;
  G3.tried = true;
  try {
    if (typeof THREE === 'undefined') return false;
    const r = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' });
    r.setPixelRatio(Math.min(window.devicePixelRatio || 1, ({ high: 1.75, mid: 1.25, low: 0.9 })[G3.quality || 'high']));
    r.outputEncoding = THREE.sRGBEncoding;
    r.toneMapping = THREE.ACESFilmicToneMapping;
    r.toneMappingExposure = 0.78;
    r.setClearColor(0x000000, 0);
    G3.r = r;
    G3.canvas = r.domElement;
    G3.canvas.className = 'gl';
    G3.pmrem = new THREE.PMREMGenerator(r);
    G3.env = makeStudioEnv();
    G3.shadowTex = makeShadowTex();
    bindOrbitEvents(G3.canvas);
    G3.canvas.addEventListener('webglcontextlost', (ev) => {
      ev.preventDefault(); G3.lost = true; G3.ok = false;
      if (G3.canvas.parentNode) G3.canvas.style.display = 'none';
      if (typeof toast === 'function') toast('3D-графика недоступна, включён 2D-режим');
    });
    G3.ok = true;
  } catch (err) { console.warn('3D недоступно', err); G3.ok = false; }
  return G3.ok;
}

// студийное окружение для отражений: тёмный зал, софтбоксы сверху и цветные полосы по бокам
function makeStudioEnv() {
  const s = new THREE.Scene();
  s.add(new THREE.Mesh(new THREE.BoxGeometry(30, 14, 30), new THREE.MeshBasicMaterial({ color: 0x0e0f12, side: THREE.BackSide })));
  const panel = (w, h, col, k, pos, rot) => {
    const m = new THREE.MeshBasicMaterial({ color: new THREE.Color(col).multiplyScalar(k), side: THREE.DoubleSide });
    const p = new THREE.Mesh(new THREE.PlaneGeometry(w, h), m); p.position.copy(pos); p.rotation.set(rot[0], rot[1], rot[2]); s.add(p);
  };
  panel(10, 3, 0xffffff, 2.0, V3(0, 6.8, 0), [Math.PI / 2, 0, 0]);
  panel(2, 9, 0xffd9a0, 1.3, V3(-14.5, 2, 0), [0, Math.PI / 2, 0]);
  panel(2, 9, 0xa8ccff, 1.1, V3(14.5, 2, 0), [0, -Math.PI / 2, 0]);
  panel(14, 0.8, 0xffffff, 0.9, V3(0, 3, -14.5), [0, 0, 0]);
  panel(14, 0.8, 0xffffff, 0.6, V3(0, 1.5, 14.5), [0, Math.PI, 0]);
  const fl = new THREE.Mesh(new THREE.PlaneGeometry(30, 30), new THREE.MeshBasicMaterial({ color: 0x0c0d10 })); fl.rotation.x = -Math.PI / 2; fl.position.y = -6.9; s.add(fl);
  return G3.pmrem.fromScene(s, 0.035).texture;
}
function makeShadowTex() {
  const c = document.createElement('canvas'); c.width = c.height = 256;
  const g = c.getContext('2d'); const gr = g.createRadialGradient(128, 128, 10, 128, 128, 128);
  gr.addColorStop(0, 'rgba(0,0,0,0.75)'); gr.addColorStop(0.55, 'rgba(0,0,0,0.35)'); gr.addColorStop(1, 'rgba(0,0,0,0)');
  g.fillStyle = gr; g.fillRect(0, 0, 256, 256);
  const t = new THREE.CanvasTexture(c); return t;
}
function stripeTex(a, b, n, vertical) {
  const c = document.createElement('canvas'); c.width = c.height = 128;
  const g = c.getContext('2d'); g.fillStyle = a; g.fillRect(0, 0, 128, 128); g.fillStyle = b;
  for (let i = 0; i < n; i++) { const p = i * 128 / n; vertical ? g.fillRect(p, 0, 128 / n * 0.45, 128) : g.fillRect(0, p, 128, 128 / n * 0.45); }
  const t = new THREE.CanvasTexture(c); t.wrapS = t.wrapT = THREE.RepeatWrapping; t.encoding = THREE.sRGBEncoding; return t;
}
function carbonTex() {
  const c = document.createElement('canvas'); c.width = c.height = 64; const g = c.getContext('2d');
  for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) { g.fillStyle = (x + y) % 2 ? '#1c1d20' : '#2b2d31'; g.fillRect(x * 8, y * 8, 8, 8); g.fillStyle = 'rgba(255,255,255,0.05)'; g.fillRect(x * 8, y * 8, (x + y) % 2 ? 8 : 2, (x + y) % 2 ? 2 : 8); }
  const t = new THREE.CanvasTexture(c); t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(6, 6); t.encoding = THREE.sRGBEncoding; return t;
}

// ---------- Управление камерой: вращение пальцем и щипок ----------
class Orbit {
  constructor(az, el, dist) { this.az = az; this.el = el; this.dist = dist; this.base = dist; this.target = V3(0, 0, 0); this.idle = 0; this.auto = 0.18; }
  apply(cam) {
    const ce = Math.cos(this.el);
    cam.position.set(this.target.x + this.dist * ce * Math.sin(this.az), this.target.y + this.dist * Math.sin(this.el), this.target.z + this.dist * ce * Math.cos(this.az));
    cam.lookAt(this.target);
  }
  tick(dt) { this.idle += dt; if (this.idle > 4 && this.auto) this.az += this.auto * dt * Math.min(1, (this.idle - 4) / 2); }
}
function bindOrbitEvents(cv) {
  const pts = new Map(); let pinch0 = 0, dist0 = 0, lastTap = 0;
  cv.style.touchAction = 'none';
  cv.addEventListener('pointerdown', (ev) => {
    pts.set(ev.pointerId, { x: ev.clientX, y: ev.clientY }); try { cv.setPointerCapture(ev.pointerId); } catch (_) { }
    const o = G3.active && G3.active.orbit; if (!o) return; o.idle = 0;
    if (pts.size === 2) { const [a, b] = [...pts.values()]; pinch0 = Math.hypot(a.x - b.x, a.y - b.y); dist0 = o.dist; }
    const now = performance.now(); if (now - lastTap < 300 && pts.size === 1) { o.dist = o.base; o.el = 0.32; } lastTap = now;
  });
  cv.addEventListener('pointermove', (ev) => {
    const p = pts.get(ev.pointerId); if (!p) return;
    const o = G3.active && G3.active.orbit; if (!o) return;
    o.idle = 0;
    if (pts.size === 1) {
      o.az -= (ev.clientX - p.x) * 0.0105; o.el = clamp(o.el + (ev.clientY - p.y) * 0.008, -0.25, 1.35);
    }
    p.x = ev.clientX; p.y = ev.clientY;
    if (pts.size === 2) { const [a, b] = [...pts.values()]; const d = Math.hypot(a.x - b.x, a.y - b.y); if (pinch0 > 0) o.dist = clamp(dist0 * pinch0 / d, o.base * 0.35, o.base * 2.2); }
  });
  const up = (ev) => { pts.delete(ev.pointerId); if (pts.size < 2) pinch0 = 0; };
  cv.addEventListener('pointerup', up); cv.addEventListener('pointercancel', up);
  cv.addEventListener('wheel', (ev) => { const o = G3.active && G3.active.orbit; if (!o) return; ev.preventDefault(); o.idle = 0; o.dist = clamp(o.dist * (1 + ev.deltaY * 0.001), o.base * 0.35, o.base * 2.2); }, { passive: false });
}

// показать 3D-сцену в контейнере
function show3D(host, view) {
  if (!init3D()) { if (G3.canvas) G3.canvas.style.display = 'none'; return false; }
  if (G3.canvas.parentNode !== host) host.prepend(G3.canvas);
  G3.active = view;
  return true;
}
function render3D(view, dt) {
  if (!G3.ok || G3.active !== view) return;
  const host = G3.canvas.parentNode; if (!host) return;
  const w = host.clientWidth, h = host.clientHeight;
  if (!w || !h) return;
  if (G3.w !== w || G3.h !== h) { G3.r.setSize(w, h, false); G3.canvas.style.width = w + 'px'; G3.canvas.style.height = h + 'px'; G3.w = w; G3.h = h; }
  view.cam.aspect = w / h; view.cam.updateProjectionMatrix();
  view.orbit.tick(dt); view.orbit.apply(view.cam);
  G3.r.render(view.scene, view.cam);
}

function disposeTree(o) {
  o.traverse((n) => { if (n.geometry) n.geometry.dispose(); if (n.material) { (Array.isArray(n.material) ? n.material : [n.material]).forEach(m => { if (m.map && m.map._own) m.map.dispose(); m.dispose(); }); } });
}
function baseScene() {
  const scene = new THREE.Scene();
  scene.environment = G3.env;
  scene.add(new THREE.HemisphereLight(0xdfe8ff, 0x22201c, 0.3));
  const key = new THREE.DirectionalLight(0xffffff, 1.15); key.position.set(3, 6, 4); scene.add(key);
  const rim = new THREE.DirectionalLight(0xffc787, 0.7); rim.position.set(-5, 2.5, -4); scene.add(rim);
  return scene;
}

// ================= ДВИГАТЕЛЬ =================
const M3 = {}; // фабрика материалов (для каждой группы деталей — свои копии, чтобы подсвечивать)
function std(color, metal, rough, extra) { return new THREE.MeshStandardMaterial(Object.assign({ color, metalness: metal, roughness: rough }, extra || {})); }

class Engine3D {
  constructor() {
    this.scene = baseScene();
    this.cam = new THREE.PerspectiveCamera(35, 1, 0.01, 50);
    this.orbit = new Orbit(0.75, 0.32, 1.2);
    this.root = null; this.key = ''; this.xray = true;
    const sh = new THREE.Mesh(new THREE.PlaneGeometry(1, 1), new THREE.MeshBasicMaterial({ map: G3.shadowTex, transparent: true, depthWrite: false }));
    sh.rotation.x = -Math.PI / 2; this.shadow = sh; this.scene.add(sh);
    this.hlT = 0;
  }
  setEngine(e) {
    const key = JSON.stringify(e) + this.xray;
    if (key === this.key) return;
    this.key = key;
    if (this.root) { this.scene.remove(this.root); disposeTree(this.root); }
    this.e = Object.assign({}, e);
    this.mats = {};
    this.root = new THREE.Group();
    this.parts = {}; this.anim = { pistons: [], rods: [], glows: [], wheels: [], hot: [], rotors: [], pulleys: [] };
    try { isRotary(e) ? this.buildRotary(e) : this.buildPiston(e); } catch (err) { console.error(err); }
    this.flames = [];
    (this.exhaustTips || []).forEach(t => {
      const f = new THREE.Mesh(new THREE.ConeGeometry(0.03, 0.22, 12), new THREE.MeshBasicMaterial({ color: 0xff7a1a, transparent: true, opacity: 0, blending: THREE.AdditiveBlending, depthWrite: false }));
      f.geometry.translate(0, 0.11, 0); f.rotation.z = -Math.PI / 2; f.position.copy(t); this.root.add(f); this.flames.push(f);
    });
    this.exhaustTips = null; this.turbos = null; this.plenum = null; this.frontX = null; this.fwX = null;
    this.scene.add(this.root);
    // кадрирование
    const box = new THREE.Box3().setFromObject(this.root), size = box.getSize(V3()), c = box.getCenter(V3());
    const rad = size.length() / 2;
    const first = !this.framed; this.framed = true;
    this.orbit.target.copy(c);
    const nb = rad / Math.tan(this.cam.fov * Math.PI / 360) * 0.78;
    if (first || Math.abs(nb - this.orbit.base) / this.orbit.base > 0.08) { this.orbit.dist = nb * (this.orbit.dist / (this.orbit.base || nb)); this.orbit.base = nb; }
    this.shadow.position.set(c.x, box.min.y - 0.002, c.z); this.shadow.scale.set(size.x * 1.6 + 0.2, size.z * 1.8 + 0.2, 1);
    this.floorY = box.min.y;
  }
  mat(group, kind, color) {
    const k = group + ':' + kind + ':' + (color || '');
    if (this.mats && this.mats[k]) return this.mats[k];
    this.mats = this.mats || {};
    let m;
    const xr = this.xray;
    switch (kind) {
      case 'iron': m = std(0x5b6067, 0.55, 0.6); break;
      case 'cgi': m = std(0x676b72, 0.6, 0.5); break;
      case 'alu': m = std(0xb9c0c9, 0.85, 0.38); break;
      case 'alusil': m = std(0xc3c9d1, 0.85, 0.32); break;
      case 'billet': m = std(0xdde2e8, 1, 0.16); break;
      case 'steel': m = std(0xd6dbe1, 1, 0.14); break;
      case 'forged': m = std(0xa7adb5, 1, 0.3); break;
      case 'ti': m = std(0x9aa4b8, 1, 0.22); break;
      case 'dark': m = std(0x1f2125, 0.4, 0.55); break;
      case 'black': m = std(0x101114, 0.2, 0.7); break;
      case 'rubber': m = std(0x121314, 0, 0.92); break;
      case 'liner': m = std(0x2a2c31, 0.8, 0.35, { side: THREE.DoubleSide }); break;
      case 'paint': m = new THREE.MeshPhysicalMaterial({ color: new THREE.Color(color), metalness: 0.45, roughness: 0.32, clearcoat: 1, clearcoatRoughness: 0.08 }); break;
      case 'anod': m = std(color || 0x2f6fd8, 0.8, 0.3); break;
      case 'exhaust': m = std(0x77706a, 0.85, 0.42, { emissive: new THREE.Color(0x000000) }); break;
      case 'gold': m = std(0xd6a640, 1, 0.25); break;
      case 'copper': m = std(0xc87a4a, 1, 0.3); break;
      case 'red': m = std(0xb8312a, 0.3, 0.45); break;
      case 'fins': m = std(0x8a929c, 0.7, 0.4, { map: stripeTex('#9aa2ac', '#2a2d32', 16, true) }); m.map._own = true; break;
      case 'glow': m = new THREE.MeshBasicMaterial({ color: 0xff8a2a, transparent: true, opacity: 0, blending: THREE.AdditiveBlending, depthWrite: false }); break;
      case 'belt': m = std(0x15161a, 0, 0.8); break;
      default: m = std(0x888888, 0.5, 0.5);
    }
    if (xr && ['iron', 'cgi', 'alu', 'alusil', 'billet'].includes(kind) && (group === 'block')) { m.transparent = true; m.opacity = 0.26; m.depthWrite = false; m.side = THREE.DoubleSide; }
    m.userData.group = group;
    this.mats[k] = m;
    return m;
  }
  add(group, geo, mat, pos, rot, parent) {
    const m = new THREE.Mesh(geo, mat);
    if (pos) m.position.copy(pos);
    if (rot) m.rotation.set(rot[0] || 0, rot[1] || 0, rot[2] || 0);
    (parent || this.root).add(m);
    return m;
  }
  tube(group, pts, rad, mat, segs) {
    const curve = new THREE.CatmullRomCurve3(pts, false, 'catmullrom', 0.3);
    return this.add(group, new THREE.TubeGeometry(curve, segs || 24, rad, 10, false), mat);
  }

  buildPiston(e) {
    const mm = 1 / 1000;
    const B = e.bore * mm, r = e.stroke / 2 * mm, l = e.rodLen * mm, compH = B * 0.34, wall = Math.max(0.005, B * 0.1);
    const deck = r + l + compH + 0.002;
    const headH = e.cycle === 2 ? B * 0.35 : e.valvetrain === 'ohv' ? B * 0.6 : e.valvetrain === 'sohc' ? B * 0.75 : B * 0.9;
    const radial = e.type === 'radial';
    const angs = bankAngles(e).map(a => a * Math.PI / 180);
    const nb = radial ? 1 : angs.length;
    const m = radial ? 1 : Math.round(e.cyl / nb);
    const pitch = B * 1.22 + 0.006;
    const len = m * pitch + wall * 2;
    const ph = cylPhases(e);
    const blockMat = this.mat('block', e.block);
    const cylBot = r + l * 0.38;
    this.geo = { B, r, l, compH, deck, headH, pitch, len, angs, m };
    const xs = (i, b) => (i - (m - 1) / 2) * pitch + (nb === 2 && e.type !== 'vr' && !radial ? (b ? 1 : -1) * pitch * 0.18 : e.type === 'vr' || e.type === 'w' ? (b % 2 ? 1 : -1) * pitch * 0.25 : 0);
    const banks = radial ? angs.map((a, i) => ({ a, cyls: [i] })) : angs.map((a, b) => ({ a, cyls: Array.from({ length: m }, (_, i) => b * m + i) }));
    const xMin = -len / 2 - pitch * 0.3, xMax = len / 2 + pitch * 0.3;

    // --- ряды: блок, гильзы, поршни, шатуны ---
    banks.forEach((bk, bi) => {
      const g = new THREE.Group(); g.rotation.x = bk.a; this.root.add(g);
      if (!radial) {
        const h = deck - cylBot;
        this.add('block', new THREE.BoxGeometry(len, h, B + wall * 2), blockMat, V3(xs(0, bi) + (m - 1) / 2 * pitch, cylBot + h / 2, 0), null, g);
      }
      bk.cyls.forEach((c, i) => {
        const x = radial ? 0 : xs(i, bi);
        const h = deck - cylBot;
        if (radial) {
          // цилиндр звезды: рёбра охлаждения
          this.add('block', new THREE.CylinderGeometry(B / 2 + wall, B / 2 + wall, h, 20), this.mat('block', 'fins'), V3(x, cylBot + h / 2, 0), null, g);
        }
        this.add('block', new THREE.CylinderGeometry(B / 2, B / 2, h, 24, 1, true), this.mat('block', 'liner'), V3(x, cylBot + h / 2, 0), null, g);
        // поршень
        const pg = new THREE.Group(); g.add(pg);
        const pm = this.mat('crank', e.pistons === 'forged' || e.pistons === 'billet' ? 'steel' : 'forged');
        const pist = new THREE.Mesh(new THREE.CylinderGeometry(B / 2 * 0.985, B / 2 * 0.975, compH + B * 0.3, 24), pm);
        pist.position.y = (compH - B * 0.3) / 2; pg.add(pist);
        for (let k = 0; k < 3; k++) { const ring = new THREE.Mesh(new THREE.CylinderGeometry(B / 2 * 0.99, B / 2 * 0.99, 0.0015, 24, 1, true), this.mat('crank', 'dark')); ring.position.y = compH - 0.004 - k * 0.0035; pg.add(ring); }
        const pin = new THREE.Mesh(new THREE.CylinderGeometry(B * 0.11, B * 0.11, B * 0.8, 12), this.mat('crank', 'steel')); pin.rotation.z = Math.PI / 2; pg.add(pin);
        pg.position.x = x;
        // шатун
        const rodMat = this.mat('crank', e.rods === 'titanium' ? 'ti' : e.rods === 'billet' ? 'billet' : e.rods === 'forged' ? 'forged' : 'iron');
        const rod = new THREE.Group(); g.add(rod);
        const beam = new THREE.Mesh(new THREE.BoxGeometry(B * 0.16, l, B * 0.12), rodMat); rod.add(beam);
        const big = new THREE.Mesh(new THREE.CylinderGeometry(r * 0.48, r * 0.48, B * 0.2, 16), rodMat); big.rotation.z = Math.PI / 2; big.position.y = -l / 2; rod.add(big);
        const small = new THREE.Mesh(new THREE.CylinderGeometry(B * 0.15, B * 0.15, B * 0.18, 12), rodMat); small.rotation.z = Math.PI / 2; small.position.y = l / 2; rod.add(small);
        rod.position.x = x;
        // вспышка
        const glow = new THREE.Mesh(new THREE.CylinderGeometry(B / 2 * 0.96, B / 2 * 0.96, 1, 20), this.mat('glow' + c, 'glow')); glow.position.x = x; g.add(glow);
        const off = radial ? 0 : (bk.a * 180 / Math.PI - ph[c]) * Math.PI / 180;
        this.anim.pistons.push({ pg, rod, glow, a: bk.a, off, ph: ph[c], x });
      });
      // головка
      if (!(e.type === 'vr' || (e.type === 'w' && e.cyl !== 18))) this.buildHead(e, g, radial ? 0 : xs(0, bi) + (m - 1) / 2 * pitch, radial ? B + wall * 2 : len, B + wall * 2, deck, headH, bk.cyls.length, radial);
    });
    // общие головки VR/W
    if (e.type === 'vr' || (e.type === 'w' && e.cyl !== 18)) {
      for (let k = 0; k < angs.length; k += 2) {
        const a1 = angs[k], a2 = angs[k + 1], mid = (a1 + a2) / 2, spread = Math.abs(a2 - a1);
        const g = new THREE.Group(); g.rotation.x = mid; this.root.add(g);
        const w = (B + wall * 2) * 2 + Math.sin(spread / 2) * deck * 2 - B * 0.6;
        this.buildHead(e, g, 0, len + pitch * 0.5, w, deck * Math.cos(spread / 2) - 0.002, headH, m * 2, false);
      }
    }
    // --- картер и поддон ---
    if (!radial) {
      let halfZ = 0, yTop = r * 1.3;
      angs.forEach(a => { halfZ = Math.max(halfZ, Math.abs(Math.sin(a)) * cylBot + (B / 2 + wall) * Math.abs(Math.cos(a))); yTop = Math.max(yTop, Math.cos(a) * cylBot); });
      const yBot = -r * 1.7 - 0.01;
      this.add('block', new THREE.BoxGeometry(len + pitch * 0.5, yTop - yBot, halfZ * 2), blockMat, V3(0, (yTop + yBot) / 2, 0));
      const panH = e.oilSystem === 'dry' ? 0.03 : 0.085;
      const pan = this.add('cool', new THREE.BoxGeometry(len * 0.92, panH, halfZ * 1.7), this.mat('cool', 'dark'), V3(0, yBot - panH / 2, 0));
      this.add('cool', new THREE.BoxGeometry(len * 0.96, 0.006, halfZ * 1.85), this.mat('cool', 'alu'), V3(0, yBot - 0.003, 0));
      if (e.oilSystem === 'dry') {
        this.add('cool', new THREE.CylinderGeometry(0.05, 0.05, 0.28, 20), this.mat('cool', 'anod', 0x2f6fd8), V3(xMax + 0.06, yBot + 0.1, -halfZ - 0.07));
        this.tube('cool', [V3(xMax, yBot - 0.02, 0), V3(xMax + 0.05, yBot, -halfZ * 0.5), V3(xMax + 0.06, yBot - 0.03, -halfZ - 0.07)], 0.008, this.mat('cool', 'anod', 0x2f6fd8));
      }
      this.geo.halfZ = halfZ; this.geo.yBot = yBot - panH; this.geo.yTop = yTop;
      // передняя крышка ГРМ
      this.add('head', new THREE.BoxGeometry(0.012, deck * 0.9 + headH, halfZ * 1.5), this.mat('head', 'dark'), V3(xMin - 0.004, deck * 0.4, 0));
    } else {
      this.add('block', new THREE.CylinderGeometry(cylBot * 1.05, cylBot * 1.05, B * 1.6, 32), this.mat('block', 'alu'), null, [0, 0, Math.PI / 2]);
      this.add('block', new THREE.CylinderGeometry(cylBot * 0.5, cylBot * 0.9, B * 1.4, 24), this.mat('block', 'alu'), V3(-B * 1.5, 0, 0), [0, 0, Math.PI / 2]);
      // винт (ступица)
      this.add('crank', new THREE.ConeGeometry(cylBot * 0.45, B * 1.6, 24), this.mat('crank', 'paint', '#c8312b'), V3(-B * 2.9, 0, 0), [0, 0, Math.PI / 2]);
      this.geo.halfZ = deck + headH; this.geo.yBot = -(deck + headH); this.geo.yTop = deck + headH;
    }
    // --- коленвал ---
    const crank = new THREE.Group(); this.root.add(crank); this.crank = crank;
    const cm = this.mat('crank', e.crank === 'billet' ? 'billet' : e.crank === 'forged' ? 'forged' : 'iron');
    const shaftL = radial ? B * 3 : len + pitch * 0.6;
    this.add('crank', new THREE.CylinderGeometry(r * 0.38, r * 0.38, shaftL, 16), cm, V3(radial ? -B * 0.5 : 0, 0, 0), [0, 0, Math.PI / 2], crank);
    const pins = radial ? [{ x: 0, off: 0 }] : this.anim.pistons.map(p => ({ x: p.x, off: p.off }));
    pins.forEach(p => {
      const pg = new THREE.Group(); pg.rotation.x = p.off; pg.position.x = p.x; crank.add(pg);
      this.add('crank', new THREE.CylinderGeometry(r * 0.36, r * 0.36, B * 0.24, 14), cm, V3(0, r, 0), [0, 0, Math.PI / 2], pg);
      [-1, 1].forEach(sd => {
        const w = this.add('crank', new THREE.BoxGeometry(0.008, r * 2.3, r * 1.25), cm, V3(sd * B * 0.15, r * 0.25, 0), null, pg);
        const cw = this.add('crank', new THREE.CylinderGeometry(r * 1.35, r * 1.35, 0.008, 20, 1, false, Math.PI / 2, Math.PI), cm, V3(sd * B * 0.15, 0, 0), [0, 0, Math.PI / 2], pg);
        cw.rotation.set(Math.PI, 0, Math.PI / 2);
      });
    });
    // маховик сзади
    const fwT = ({ heavy: 0.03, stock: 0.022, light: 0.014, carbon: 0.01 })[e.flywheel];
    const fwR = Math.max(0.12, r * 3.2);
    this.add('crank', new THREE.CylinderGeometry(fwR, fwR, fwT, 40), this.mat('crank', e.flywheel === 'carbon' ? 'black' : 'steel'), V3(xMax + 0.02 + fwT / 2, 0, 0), [0, 0, Math.PI / 2], crank);
    this.add('crank', new THREE.TorusGeometry(fwR, 0.006, 6, 60), this.mat('crank', 'gold'), V3(xMax + 0.02 + fwT / 2, 0, 0), [0, Math.PI / 2, 0], crank);
    this.fwX = xMax + 0.02;
    // --- навесное спереди: шкивы и ремень ---
    if (!radial) this.buildFrontDrive(e, xMin - 0.02);
    // --- впуск, выпуск, наддув ---
    this.buildIntake(e, banks, xs);
    this.buildExhaust(e, banks, xs);
    if (e.nitrous) this.buildNitrous(e);
    this.buildStand(e);
  }

  buildHead(e, g, cx, len, w, deck, headH, ncyl, radial) {
    const mh = this.mat('head', e.head === 'iron' ? 'iron' : 'alu');
    if (e.cycle === 2 || radial) {
      const n = 7;
      for (let k = 0; k < n; k++) this.add('head', new THREE.BoxGeometry(len * (radial ? 1 : 1.02), headH / n * 0.5, w * (1.25 - k * 0.04)), mh, V3(cx, deck + headH * (k + 0.5) / n, 0), null, g);
      this.add('ecu', new THREE.CylinderGeometry(0.006, 0.006, 0.05, 8), this.mat('ecu', 'steel'), V3(cx, deck + headH + 0.02, 0), null, g);
      return;
    }
    this.add('head', new THREE.BoxGeometry(len + 0.004, headH * 0.55, w + 0.006), mh, V3(cx, deck + headH * 0.275, 0), null, g);
    // крышка клапанов: скруглённый профиль
    const col = COVER_COLORS[e.coverColor] || '#24262b';
    const cw = w * 0.96, chh = headH * 0.45;
    const sh = new THREE.Shape(); const rr = Math.min(cw, chh) * 0.35;
    sh.moveTo(-cw / 2, 0); sh.lineTo(cw / 2, 0); sh.lineTo(cw / 2, chh - rr); sh.quadraticCurveTo(cw / 2, chh, cw / 2 - rr, chh); sh.lineTo(-cw / 2 + rr, chh); sh.quadraticCurveTo(-cw / 2, chh, -cw / 2, chh - rr); sh.closePath();
    const cg = new THREE.ExtrudeGeometry(sh, { depth: len - 0.01, bevelEnabled: true, bevelThickness: 0.004, bevelSize: 0.004, bevelSegments: 2, curveSegments: 8 });
    cg.translate(0, 0, -(len - 0.01) / 2); cg.rotateY(Math.PI / 2);
    this.add('head', cg, this.mat('head', 'paint', col), V3(cx, deck + headH * 0.55, 0), null, g);
    // рёбра на крышке
    for (let k = -1; k <= 1; k++) this.add('head', new THREE.BoxGeometry(len * 0.8, 0.004, 0.004), this.mat('head', 'alu'), V3(cx, deck + headH * 0.55 + chh + 0.002, k * cw * 0.22), null, g);
    // катушки зажигания / свечи
    const n = ncyl;
    for (let i = 0; i < n; i++) {
      const x = cx - len / 2 + (i + 0.5) * len / n;
      if (FUELS[e.fuel].diesel) { this.add('ecu', new THREE.CylinderGeometry(0.005, 0.005, 0.03, 8), this.mat('ecu', 'steel'), V3(x, deck + headH + 0.012, cw * 0.25), null, g); continue; }
      if (e.valvetrain === 'ohv') { this.add('ecu', new THREE.CylinderGeometry(0.005, 0.005, 0.04, 8), this.mat('ecu', 'red'), V3(x, deck + headH * 0.35, w / 2 + 0.012), [Math.PI / 2.4, 0, 0], g); continue; }
      this.add('ecu', new THREE.BoxGeometry(0.022, 0.016, 0.03), this.mat('ecu', e.coils === 'race' ? 'anod' : 'black', e.coils === 'race' ? 0xd23b2a : undefined), V3(x, deck + headH * 0.55 + chh + 0.012, 0), null, g);
    }
    // крышка маслозаливной горловины
    this.add('head', new THREE.CylinderGeometry(0.013, 0.013, 0.012, 16), this.mat('head', 'gold'), V3(cx - len * 0.3, deck + headH * 0.55 + chh + 0.008, cw * 0.25), null, g);
  }

  // шкивы и приводной ремень спереди
  buildFrontDrive(e, x) {
    const r = this.geo.r, halfZ = this.geo.halfZ;
    const pul = [];
    const add = (y, z, rad, kind) => { const p = this.add('cool', new THREE.CylinderGeometry(rad, rad, 0.022, 28), this.mat('cool', kind || 'alu'), V3(x, y, z), [0, 0, Math.PI / 2]); this.anim.pulleys.push({ m: p, k: 0.08 / rad }); pul.push(V3(x, y, z)); pul[pul.length - 1].rad = rad; return p; };
    add(0, 0, Math.max(0.06, r * 1.7), 'dark');
    add(this.geo.deck * 0.45, halfZ * 0.55, 0.045);
    add(this.geo.deck * 0.75, -halfZ * 0.6, 0.04);
    this.add('cool', new THREE.CylinderGeometry(0.06, 0.06, 0.12, 20), this.mat('cool', 'alu'), V3(x + 0.07, this.geo.deck * 0.75, -halfZ * 0.6), [0, 0, Math.PI / 2]); // генератор
    const loop = []; pul.forEach(p => loop.push(V3(x, p.y, p.z)));
    const curve = new THREE.CatmullRomCurve3(loop.concat([loop[0]]), true, 'centripetal');
    this.add('cool', new THREE.TubeGeometry(curve, 60, 0.006, 6, true), this.mat('cool', 'belt'));
    this.frontX = x;
  }

  buildIntake(e, banks, xs) {
    const { B, deck, headH, pitch, len, angs } = this.geo;
    const wall = Math.max(0.005, B * 0.1);
    const runR = clamp(e.throttle / 1000 * 0.42, 0.012, 0.04) * (e.manifold === 'itb' ? 1 : 0.8);
    const im = this.mat('intake', e.manifold === 'itb' ? 'steel' : 'alu');
    const radial = e.type === 'radial', inline = e.type === 'inline';
    // точка входа в головку для каждого цилиндра (снаружи головки, на стороне впуска)
    const ports = [];
    banks.forEach((bk, bi) => {
      const sideIn = inline ? -1 : radial ? 0 : (bk.a >= 0 ? -1 : 1);
      bk.cyls.forEach((c, i) => {
        const x = radial ? 0 : xs(i, bi);
        const local = radial ? V3(x - B * 0.6, deck * 0.92, 0) : V3(x, deck + headH * 0.3, sideIn * (B / 2 + wall + 0.004));
        const out = radial ? V3(x - B * 0.6, deck * 0.92, 0) : V3(x, deck + headH * 0.3 + 0.02, sideIn * (B / 2 + wall + 0.05));
        ports.push({ p: rotX(local, bk.a), o: rotX(out, bk.a), a: bk.a, x });
      });
    });
    const pp = this.anim;
    let plenum = null;
    const sc = hasSC(e) && e.induction !== 'sc_cent';
    if (radial) {
      // кольцевой коллектор сзади
      const R = this.geo.deck * 0.8;
      this.add('intake', new THREE.TorusGeometry(R * 0.55, runR * 1.2, 10, 40), im, V3(-B * 0.6 + B * 1.4, 0, 0), [0, Math.PI / 2, 0]);
      ports.forEach(p => this.tube('intake', [p.p, V3(B * 0.8, p.p.y * 0.6, p.p.z * 0.6)], runR * 0.7, im, 10));
      plenum = V3(B * 0.8, -R * 0.55, 0);
    } else if (e.manifold === 'itb') {
      ports.forEach(p => {
        const dir = p.o.clone().sub(p.p).normalize();
        const up = V3(0, 1, 0).multiplyScalar(0.04);
        const end = p.o.clone().add(dir.multiplyScalar(0.05)).add(up);
        this.tube('intake', [p.p, p.o, end], runR, im, 12);
        // раструб
        const pts = []; for (let k = 0; k <= 8; k++) { const t = k / 8; pts.push(new THREE.Vector2(runR * (1 + t * t * 0.9), t * 0.04)); }
        const tr = this.add('intake', new THREE.LatheGeometry(pts, 20), this.mat('intake', 'steel'), end);
        tr.lookAt(end.clone().add(end.clone().sub(p.o)));
        tr.rotateX(Math.PI / 2);
      });
    } else {
      let pc;
      if (inline) pc = V3(0, deck + headH * 0.1, -(B / 2 + wall + 0.11));
      else if (e.type === 'boxer') pc = V3(0, this.geo.r * 2.4 + 0.07, 0);
      else { let yy = 0; angs.forEach(a => yy = Math.max(yy, Math.cos(a) * (deck + headH * 0.25))); pc = V3(0, yy + 0.02, 0); }
      const pl = len * 0.95, pr = Math.max(0.03, B * 0.5);
      const pg = new THREE.CapsuleGeometry(pr, pl - pr * 2, 8, 20); pg.rotateZ(Math.PI / 2);
      this.add('intake', pg, im, pc);
      ports.forEach(p => this.tube('intake', [p.p, p.o, V3(p.x, (p.o.y + pc.y) / 2 + 0.02, (p.o.z + pc.z) / 2), V3(p.x, pc.y, pc.z)], runR, im, 16));
      // корпус дросселя
      const tb = this.add('intake', new THREE.CylinderGeometry(e.throttle / 2000 + 0.006, e.throttle / 2000 + 0.006, 0.05, 24), this.mat('intake', 'steel'), V3(-pl / 2 - 0.02, pc.y, pc.z), [0, 0, Math.PI / 2]);
      this.add('intake', new THREE.CylinderGeometry(e.throttle / 2000, e.throttle / 2000, 0.052, 24), this.mat('intake', 'black'), V3(-pl / 2 - 0.02, pc.y, pc.z), [0, 0, Math.PI / 2]);
      plenum = V3(-pl / 2 - 0.05, pc.y, pc.z);
      if (e.fuelSystem !== 'carb') ports.forEach(p => this.add('fuel', new THREE.CylinderGeometry(0.004, 0.004, 0.035, 8), this.mat('fuel', 'anod', 0x2f6fd8), p.o.clone().add(V3(0, 0.02, 0)), [0.3, 0, 0]));
      if (e.fuelSystem !== 'carb') this.tube('fuel', [V3(-len / 2, pc.y + pr + 0.012, pc.z * 0.5 + (ports[0] ? ports[0].o.z * 0.5 : 0)), V3(len / 2, pc.y + pr + 0.012, pc.z * 0.5 + (ports[0] ? ports[0].o.z * 0.5 : 0))], 0.006, this.mat('fuel', 'anod', 0xd23b2a), 4);
    }
    this.plenum = plenum;
    // нагнетатель сверху
    if (sc && plenum && !radial) {
      const w = Math.max(0.14, len * 0.55), hh = 0.11, d = 0.13;
      const top = V3(0, plenum.y + (B * 0.5) + hh / 2 + 0.01, plenum.z);
      if (inline) top.set(0, plenum.y + 0.11, plenum.z - 0.02);
      const body = this.add('boost', new THREE.BoxGeometry(w, hh, d), this.mat('boost', e.induction === 'sc_screw' ? 'billet' : 'alu'), top);
      for (let k = -3; k <= 3; k++) this.add('boost', new THREE.BoxGeometry(w * 0.9, 0.006, 0.004), this.mat('boost', 'alu'), V3(top.x, top.y - hh * 0.1 + k * 0.012, top.z + d / 2 + 0.002));
      // шкив и ремень
      const px = -w / 2 - 0.03;
      const pul = this.add('boost', new THREE.CylinderGeometry(0.035, 0.035, 0.02, 24), this.mat('boost', 'steel'), V3(this.frontX || px, top.y, top.z), [0, 0, Math.PI / 2]);
      this.anim.pulleys.push({ m: pul, k: 2.2 });
      this.add('boost', new THREE.BoxGeometry(Math.abs((this.frontX || px) - (-w / 2)), 0.03, 0.03), this.mat('boost', 'alu'), V3(((this.frontX || px) - w / 2) / 2, top.y, top.z));
      this.tube('boost', [V3(this.frontX || px, top.y + 0.035, top.z), V3(this.frontX || px, 0.05, 0)], 0.005, this.mat('boost', 'belt'), 6);
      // заслонка сверху и фильтр
      this.add('boost', new THREE.CylinderGeometry(0.04, 0.04, 0.04, 20), this.mat('boost', 'black'), V3(top.x, top.y + hh / 2 + 0.02, top.z));
      this.airFilter(e, V3(top.x, top.y + hh / 2 + 0.07, top.z), V3(0, 1, 0));
      if (e.induction === 'twincharge') this.buildTurbo(e);
    } else if (hasTurbo(e)) {
      this.buildTurbo(e);
    } else if (e.induction === 'sc_cent') {
      const p = V3((this.frontX || -len / 2) + 0.06, this.geo.r * 0.8, (this.geo.halfZ || 0.1) + 0.08);
      this.snail('boost', p, 0.05, false, 1);
      this.tube('boost', [p.clone().add(V3(0, 0.07, 0)), V3(p.x, plenum.y + 0.05, p.z), plenum.clone()], 0.025, this.mat('boost', 'alu'), 16);
      this.airFilter(e, p.clone().add(V3(-0.07, 0, 0)), V3(-1, 0, 0));
    } else if (plenum && !radial) {
      this.airFilter(e, plenum.clone().add(V3(-0.06, 0, 0)), V3(-1, 0, 0));
    }
  }
  airFilter(e, p, dir) {
    let g;
    if (e.airFilter === 'cone') { g = new THREE.CylinderGeometry(0.035, 0.055, 0.1, 20); this.add('intake', g, this.mat('intake', 'red'), p.clone().add(dir.clone().multiplyScalar(0.05)), dir.y ? null : [0, 0, Math.PI / 2]); }
    else if (e.airFilter === 'ram') { this.tube('intake', [p, p.clone().add(dir.clone().multiplyScalar(0.12)), p.clone().add(dir.clone().multiplyScalar(0.2)).add(V3(0, -0.08, 0))], 0.035, this.mat('intake', 'black'), 12); }
    else { this.add('intake', new THREE.BoxGeometry(0.12, 0.07, 0.14), this.mat('intake', 'black'), p.clone().add(dir.clone().multiplyScalar(0.07))); }
  }
  // «улитка» турбины/компрессора, ось по X
  snail(group, p, R, hot, side) {
    const mat = hot ? this.mat('exhaust', 'exhaust') : this.mat(group, 'alu');
    const sh = new THREE.Shape(); const N = 40;
    for (let i = 0; i <= N; i++) { const a = i / N * Math.PI * 2; const rr = R * (0.7 + 0.3 * i / N); i ? sh.lineTo(Math.cos(a) * rr, Math.sin(a) * rr) : sh.moveTo(Math.cos(a) * rr, Math.sin(a) * rr); }
    sh.lineTo(R, -R * 0.1); sh.lineTo(R * 1.25, -R * 0.1); sh.lineTo(R * 1.25, 0.0); sh.closePath();
    const g = new THREE.ExtrudeGeometry(sh, { depth: R * 0.7, bevelEnabled: true, bevelThickness: R * 0.15, bevelSize: R * 0.12, bevelSegments: 3, curveSegments: 12 });
    g.translate(0, 0, -R * 0.35); g.rotateY(Math.PI / 2 * side);
    const m = this.add(group, g, mat, p);
    if (hot) this.anim.hot.push(mat);
    return m;
  }
  buildTurbo(e) {
    const n = turboCount(e) || 1;
    const R = clamp(e.turboSize / 1000 * 0.95, 0.035, 0.1) * (n === 4 ? 0.8 : 1);
    const { halfZ, deck, r } = this.geo;
    const xr = this.fwX || 0.2;
    const both = ['v', 'boxer', 'w', 'vr'].includes(e.type) && n >= 2;
    const spots = [];
    for (let i = 0; i < n; i++) {
      const side = both ? (i % 2 ? -1 : 1) : 1;
      const lvl = both ? Math.floor(i / 2) : i;
      spots.push(V3(xr - 0.05 - lvl * R * 2.6, r * 0.6 + lvl * R * 0.3, side * (halfZ + R * 1.6)));
    }
    this.turbos = spots;
    spots.forEach((p, i) => {
      const small = e.turboLayout === 'sequential' && i === 0;
      const RR = small ? R * 0.8 : R;
      this.snail('exhaust', p.clone().add(V3(RR * 0.75, 0, 0)), RR * 0.85, true, 1);
      this.add('boost', new THREE.CylinderGeometry(RR * 0.38, RR * 0.38, RR * 0.6, 16), this.mat('boost', 'steel'), p, [0, 0, Math.PI / 2]);
      this.snail('boost', p.clone().add(V3(-RR * 0.75, 0, 0)), RR, false, -1);
      // входной патрубок и колесо компрессора
      const inl = p.clone().add(V3(-RR * 1.55, 0, 0));
      this.add('boost', new THREE.CylinderGeometry(RR * 0.55, RR * 0.6, RR * 0.5, 24, 1, true), this.mat('boost', 'alu'), inl, [0, 0, Math.PI / 2]);
      const wheel = new THREE.Group(); wheel.position.copy(inl); this.root.add(wheel);
      for (let k = 0; k < 10; k++) { const bl = new THREE.Mesh(new THREE.BoxGeometry(RR * 0.25, RR * 0.48, 0.0015), this.mat('boost', 'steel')); bl.rotation.x = k / 10 * Math.PI * 2; bl.position.set(0, 0, 0); bl.geometry.translate(0, RR * 0.24, 0); bl.rotation.y = 0.35; wheel.add(bl); }
      const nut = new THREE.Mesh(new THREE.CylinderGeometry(RR * 0.08, RR * 0.08, RR * 0.3, 8), this.mat('boost', 'steel')); nut.rotation.z = Math.PI / 2; wheel.add(nut);
      this.anim.wheels.push(wheel);
      this.airFilter(e, inl.clone().add(V3(-RR * 0.3, 0, 0)), V3(-1, 0, 0));
      // вестгейт и BOV
      if (e.wastegate === 'external') this.add('boost', new THREE.CylinderGeometry(0.02, 0.02, 0.04, 16), this.mat('boost', 'anod', 0xd23b2a), p.clone().add(V3(RR, -RR * 1.2, 0)));
    });
    // нагнетательная магистраль → интеркулер → дроссель
    const front = (this.frontX || -0.3) - 0.05;
    const ic = e.intercooler !== 'none';
    const icY = (this.geo.yBot || -0.15) + 0.05;
    const pipeM = this.mat('boost', 'alu');
    const pr = clamp(e.turboSize / 1000 * 0.35, 0.02, 0.035);
    const p0 = spots[0].clone().add(V3(-spots[0].x + spots[0].x - 0.02, R * 1.1, 0));
    if (ic) {
      const iw = Math.max(0.3, (this.geo.halfZ || 0.15) * 2.4), ih = e.intercooler === 'big' ? 0.2 : 0.14;
      const icPos = V3(front - 0.06, icY + ih / 2, 0);
      const icm = this.mat('boost', 'fins');
      this.add('boost', new THREE.BoxGeometry(0.05, ih, iw), e.intercooler === 'water' ? this.mat('boost', 'anod', 0x2a4f78) : icm, icPos);
      this.add('boost', new THREE.BoxGeometry(0.06, ih * 1.05, 0.03), this.mat('boost', 'alu'), icPos.clone().add(V3(0, 0, iw / 2)));
      this.add('boost', new THREE.BoxGeometry(0.06, ih * 1.05, 0.03), this.mat('boost', 'alu'), icPos.clone().add(V3(0, 0, -iw / 2)));
      this.tube('boost', [p0, V3(front + 0.05, p0.y, p0.z), V3(icPos.x, icPos.y, iw / 2 + 0.02)], pr, pipeM, 24);
      if (this.plenum) this.tube('boost', [V3(icPos.x, icPos.y, -iw / 2 - 0.02), V3(front + 0.02, this.plenum.y, this.plenum.z - 0.05), this.plenum.clone()], pr, pipeM, 24);
    } else if (this.plenum) this.tube('boost', [p0, V3(front + 0.03, p0.y + 0.05, p0.z * 0.5), this.plenum.clone()], pr, pipeM, 24);
    if (e.bov) this.add('boost', new THREE.CylinderGeometry(0.018, 0.018, 0.035, 14), this.mat('boost', 'anod', 0x2f6fd8), V3(front + 0.06, p0.y + 0.04, p0.z));
  }
  buildExhaust(e, banks, xs) {
    const { B, deck, headH, angs } = this.geo;
    const wall = Math.max(0.005, B * 0.1);
    const radial = e.type === 'radial', inline = e.type === 'inline';
    const em = this.mat('exhaust', 'exhaust'); this.anim.hot.push(em);
    const pr = clamp(e.exhDia / 1000 * 0.33, 0.009, 0.03);
    const yLow = (this.geo.yBot || -0.2) + 0.03;
    const outX = (this.fwX || 0.3) + 0.25;
    const groups = {};
    banks.forEach((bk, bi) => {
      let sideOut = inline ? 1 : (bk.a >= 0 ? 1 : -1);
      if (e.type === 'vr' || e.type === 'w') sideOut = bk.a >= 0 ? 1 : -1;
      if ((e.type === 'vr' || (e.type === 'w' && e.cyl !== 18)) && ((bk.a >= 0 && bi % 2 === 0) || (bk.a < 0 && bi % 2 === 1))) return; // внутренний ряд VR
      bk.cyls.forEach((c, i) => {
        const x = radial ? 0 : xs(i, bi);
        if (radial) {
          const p = rotX(V3(x + B * 0.5, deck + headH * 0.3, 0), bk.a), q = rotX(V3(x + B * 0.9, deck + headH * 0.7, 0), bk.a);
          this.tube('exhaust', [p, q, q.clone().add(V3(0.05, 0, 0))], pr * 0.8, em, 8);
          return;
        }
        const ext = (B / 2 + wall + (e.type === 'vr' || e.type === 'w' ? B * 0.6 : 0)) * sideOut;
        const p = rotX(V3(x, deck + headH * 0.25, ext), bk.a);
        const q = rotX(V3(x, deck + headH * 0.25, ext + sideOut * 0.05), bk.a);
        const sideZ = Math.sign(q.z || sideOut) || 1;
        const key = sideZ > 0 ? 'R' : 'L';
        const zc = (this.geo.halfZ || 0.12) * sideZ + sideZ * 0.06;
        const coll = V3((this.fwX || 0.2) - 0.06, Math.min(q.y - 0.05, this.geo.r * 0.2), zc);
        groups[key] = coll;
        this.tube('exhaust', [p, q, V3(x, (q.y + coll.y) / 2, (q.z + zc) / 2 + sideZ * 0.02), V3((x + coll.x) / 2, coll.y + 0.01, zc), coll], pr, em, 26);
      });
    });
    // коллекторы → турбина или выпускная труба
    const keys = Object.keys(groups);
    keys.forEach((k, i) => {
      const c = groups[k];
      let t = this.turbos ? this.turbos.reduce((a, b) => (Math.abs(b.z - c.z) < Math.abs(a.z - c.z) ? b : a)) : null;
      if (t) {
        this.tube('exhaust', [c, V3(c.x, c.y + 0.02, (c.z + t.z) / 2), t.clone().add(V3(t.x > c.x ? 0 : 0.06, 0, 0))], pr * 1.2, em, 12);
      }
    });
    // выпускные трубы назад
    const tails = (this.turbos && this.turbos.length) ? this.turbos.slice(0, 2).map(t => t.clone().add(V3(0.07, -0.05, 0))) : keys.map(k => groups[k]);
    tails.forEach(c => {
      const end = V3(outX, yLow, c.z * 0.7);
      this.tube('exhaust', [c, V3(c.x + 0.08, yLow + 0.02, c.z), end], pr * 1.3, em, 16);
      this.add('exhaust', new THREE.CylinderGeometry(pr * 1.6, pr * 1.6, 0.06, 20, 1, true), this.mat('exhaust', 'steel'), end.clone().add(V3(0.03, 0, 0)), [0, 0, Math.PI / 2]);
      this.exhaustTips = (this.exhaustTips || []).concat([end.clone().add(V3(0.06, 0, 0))]);
    });
  }
  buildNitrous(e) {
    const z = -(this.geo.halfZ || 0.15) - 0.13, y = (this.geo.yBot || -0.15) + 0.06;
    this.add('fuel', new THREE.CapsuleGeometry(0.045, 0.3, 8, 20), this.mat('fuel', 'anod', 0x2f62c8), V3(0, y, z), [0, 0, Math.PI / 2]);
    this.add('fuel', new THREE.CylinderGeometry(0.018, 0.018, 0.04, 12), this.mat('fuel', 'steel'), V3(-0.21, y, z), [0, 0, Math.PI / 2]);
    if (this.plenum) this.tube('fuel', [V3(-0.23, y, z), V3(-0.25, this.plenum.y, z * 0.6), this.plenum.clone()], 0.004, this.mat('fuel', 'anod', 0x2f62c8), 16);
  }
  buildStand(e) {
    const y = this.geo.yBot || -0.2;
    const z = (this.geo.halfZ || 0.15) + 0.05;
    const sm = this.mat('stand', 'paint', '#8a4a12');
    const L = (this.fwX || 0.3) * 2;
    [-1, 1].forEach(s => {
      this.add('stand', new THREE.BoxGeometry(0.04, 0.04, z * 2.2), sm, V3(s * L * 0.38, y - 0.12, 0));
      this.add('stand', new THREE.BoxGeometry(0.035, 0.12, 0.035), sm, V3(s * L * 0.38, y - 0.06, 0));
    });
    this.add('stand', new THREE.BoxGeometry(L * 0.9, 0.04, 0.04), sm, V3(0, y - 0.12, z * 1.1));
    this.add('stand', new THREE.BoxGeometry(L * 0.9, 0.04, 0.04), sm, V3(0, y - 0.12, -z * 1.1));
  }

  buildRotary(e) {
    const n = e.cyl;
    const Rr = 0.1 * Math.pow(e.rotorVol / 654, 0.33), ecc = Rr * 0.15, w = 0.08 * Math.pow(e.rotorVol / 654, 0.33);
    const gap = w + 0.03;
    this.geo = { B: 0.08, r: ecc, l: 0, deck: Rr * 1.3, headH: 0.02, pitch: gap, len: n * gap, angs: [0], m: n, halfZ: Rr * 1.45, yBot: -Rr * 1.5 - 0.06, yTop: Rr * 1.4 };
    const housingM = this.mat('block', 'alu');
    const crank = new THREE.Group(); this.root.add(crank); this.crank = crank;
    this.add('crank', new THREE.CylinderGeometry(0.018, 0.018, n * gap + 0.2, 16), this.mat('crank', 'steel'), null, [0, 0, Math.PI / 2], crank);
    for (let i = 0; i < n; i++) {
      const x = (i - (n - 1) / 2) * gap;
      // корпус: прямоугольник с эпитрохоидным отверстием
      const sh = new THREE.Shape(); const W2 = Rr * 1.55, H2 = Rr * 1.35;
      sh.moveTo(-W2, -H2); sh.lineTo(W2, -H2); sh.lineTo(W2, H2); sh.lineTo(-W2, H2); sh.closePath();
      const hole = new THREE.Path();
      for (let k = 0; k <= 64; k++) { const t = k / 64 * Math.PI * 2; const px = ecc * Math.cos(3 * t) + Rr * Math.cos(t), py = ecc * Math.sin(3 * t) + Rr * 0.86 * Math.sin(t); k ? hole.lineTo(px * 1.06, py * 1.06) : hole.moveTo(px * 1.06, py * 1.06); }
      sh.holes.push(hole);
      const hg = new THREE.ExtrudeGeometry(sh, { depth: w, bevelEnabled: true, bevelSize: 0.004, bevelThickness: 0.004, bevelSegments: 2, curveSegments: 8 });
      hg.translate(0, 0, -w / 2); hg.rotateY(Math.PI / 2);
      this.add('block', hg, housingM, V3(x, 0, 0));
      // боковые плиты
      this.add('block', new THREE.BoxGeometry(0.012, H2 * 2, W2 * 2), this.mat('block', 'iron'), V3(x - gap / 2, 0, 0));
      // ротор
      const rs = new THREE.Shape(); const pts = [0, 1, 2].map(k => [Math.cos(k * Math.PI * 2 / 3) * Rr * 0.97, Math.sin(k * Math.PI * 2 / 3) * Rr * 0.97]);
      rs.moveTo(pts[0][0], pts[0][1]);
      for (let k = 0; k < 3; k++) { const p = pts[k], q = pts[(k + 1) % 3]; const mx = (p[0] + q[0]) / 2 * 1.32, my = (p[1] + q[1]) / 2 * 1.32; rs.quadraticCurveTo(mx, my, q[0], q[1]); }
      const rg = new THREE.ExtrudeGeometry(rs, { depth: w * 0.9, bevelEnabled: false, curveSegments: 16 }); rg.translate(0, 0, -w * 0.45); rg.rotateY(Math.PI / 2);
      const rotor = this.add('crank', rg, this.mat('crank', 'forged'), V3(x, 0, 0));
      const glow = this.add('glow' + i, new THREE.SphereGeometry(Rr * 0.45, 16, 12), this.mat('glow' + i, 'glow'), V3(x, 0, Rr * 0.75));
      this.anim.rotors.push({ m: rotor, x, ph: i * 360 / n, glow, ecc });
      // свечи
      [-0.25, 0.2].forEach(dy => this.add('ecu', new THREE.CylinderGeometry(0.006, 0.006, 0.04, 8), this.mat('ecu', 'black'), V3(x, dy * Rr, W2 + 0.015), [Math.PI / 2, 0, 0]));
    }
    const L = n * gap;
    this.fwX = L / 2 + 0.02;
    this.add('crank', new THREE.CylinderGeometry(0.12, 0.12, 0.02, 40), this.mat('crank', 'steel'), V3(this.fwX + 0.01, 0, 0), [0, 0, Math.PI / 2], crank);
    this.add('cool', new THREE.BoxGeometry(L * 0.9, 0.06, Rr * 2.2), this.mat('cool', 'dark'), V3(0, -Rr * 1.35 - 0.03, 0));
    this.buildFrontDrive(e, -L / 2 - 0.03);
    // впуск сверху, выпуск сбоку
    const pc = V3(0, Rr * 1.55 + 0.05, -Rr * 0.4);
    const pg = new THREE.CapsuleGeometry(0.04, L * 0.8, 8, 20); pg.rotateZ(Math.PI / 2);
    this.add('intake', pg, this.mat('intake', 'alu'), pc);
    this.anim.rotors.forEach(rt => this.tube('intake', [V3(rt.x, Rr * 1.3, -Rr * 0.9), V3(rt.x, Rr * 1.5, -Rr * 0.8), V3(rt.x, pc.y, pc.z)], 0.018, this.mat('intake', 'alu'), 12));
    this.plenum = V3(-L / 2 - 0.06, pc.y, pc.z);
    this.add('intake', new THREE.CylinderGeometry(0.03, 0.03, 0.05, 20), this.mat('intake', 'black'), this.plenum.clone().add(V3(0.03, 0, 0)), [0, 0, Math.PI / 2]);
    if (hasTurbo(e)) this.buildTurbo(e); else this.airFilter(e, this.plenum, V3(-1, 0, 0));
    const em = this.mat('exhaust', 'exhaust'); this.anim.hot.push(em);
    const coll = this.turbos ? this.turbos[0].clone().add(V3(0.06, 0, 0)) : V3(L / 2 + 0.1, -Rr, Rr * 2);
    this.anim.rotors.forEach(rt => this.tube('exhaust', [V3(rt.x, -Rr * 0.6, Rr * 1.5), V3(rt.x, -Rr * 0.6, Rr * 1.8), coll], 0.016, em, 16));
    this.tube('exhaust', [coll, V3(coll.x + 0.1, this.geo.yBot, coll.z * 0.7), V3(L / 2 + 0.4, this.geo.yBot, coll.z * 0.5)], 0.022, em, 16);
    this.exhaustTips = [V3(L / 2 + 0.4, this.geo.yBot, coll.z * 0.5)];
    if (e.nitrous) this.buildNitrous(e);
    this.buildStand(e);
  }

  // st: { crank (град), rpm, spool, egt (0..1), throttle, off, highlight, shake, t }
  update(st, dt) {
    if (!this.root) return;
    const e = this.e, th = (st.crank || 0) * Math.PI / 180;
    const cyc = e.cycle === 2 ? 360 : 720;
    if (this.crank) this.crank.rotation.x = th;
    const g = this.geo;
    for (const p of this.anim.pistons) {
      const pinAng = th + p.off;
      const al = pinAng - p.a;
      const xp = pistonDist(g.r, g.l, al);
      p.pg.position.y = xp;
      const py = g.r * Math.cos(al), pz = g.r * Math.sin(al);
      p.rod.position.set(p.x, (py + xp) / 2, pz / 2);
      p.rod.rotation.x = Math.atan2(-pz, xp - py);
      const crown = xp + g.compH;
      const hgt = Math.max(0.001, g.deck - crown);
      p.glow.scale.y = hgt; p.glow.position.y = crown + hgt / 2;
      const psi = (((st.crank || 0) - (e.type === 'radial' ? p.a * 180 / Math.PI : p.ph)) % cyc + cyc) % cyc;
      p.glow.material.opacity = (!st.off && st.rpm > 50 && psi < 110) ? 0.85 * (1 - psi / 110) : 0;
    }
    for (const rt of this.anim.rotors) {
      const a = th + rt.ph * Math.PI / 180;
      rt.m.position.set(rt.x, rt.ecc * Math.sin(a), rt.ecc * Math.cos(a));
      rt.m.rotation.x = a / 3;
      const ps = ((a * 180 / Math.PI) % 360 + 360) % 360;
      rt.glow.material.opacity = (!st.off && st.rpm > 50 && ps < 130) ? 0.6 * (1 - ps / 130) : 0;
    }
    const spin = (st.spool || 0);
    this.anim.wheels.forEach(w => w.rotation.x += dt * (4 + spin * 70));
    this.anim.pulleys.forEach(p => p.m.rotation.x = th * p.k);
    const heat = clamp(st.egt || 0, 0, 1);
    const hc = heatRGB(heat);
    this.anim.hot.forEach(m => { m.emissive.setRGB(hc[0], hc[1], hc[2]); });
    // подсветка выбранной группы
    this.hlT += dt;
    const hl = st.highlight;
    if (this.mats) for (const k in this.mats) {
      const m = this.mats[k]; if (!m.emissive || m.userData.group === 'exhaust' && !hl) continue;
      if (m.userData.group === 'exhaust' && hl !== 'exhaust') continue;
      if (hl && m.userData.group === hl) { const v = 0.18 + 0.12 * Math.sin(this.hlT * 5); m.emissive.setRGB(0.95 * v, 0.62 * v, 0.2 * v); }
      else if (m.userData.group !== 'exhaust') m.emissive.setRGB(0, 0, 0);
    }
    if (this.flames) this.flames.forEach(f => { if (st.pop) { f.material.opacity = 1; f.scale.set(1, 0.7 + Math.random() * 0.8, 1); } else f.material.opacity *= Math.pow(0.002, dt); });
    // тряска
    if (st.shake) { this.root.position.set((Math.random() - 0.5) * st.shake * 0.0015, (Math.random() - 0.5) * st.shake * 0.0015, (Math.random() - 0.5) * st.shake * 0.0015); }
    else this.root.position.set(0, 0, 0);
  }
}
function heatRGB(t) {
  if (t < 0.4) return [0, 0, 0];
  const k = (t - 0.4) / 0.6;
  return [Math.min(1.6, k * 1.8), Math.max(0, k * k * 0.55), k * k * k * 0.12];
}

// ================= МАШИНА =================
// Силуэты кузовов (x от переднего бампера, y от земли, метры): верх нижней части кузова и линия остекления
const CAR_PROFILES = {
  hatch:    { L: 3.95, W: 1.74, wb: [0.80, 3.28], sill: 0.2, top: [[0, 0.36], [0.02, 0.52], [0.2, 0.70], [0.8, 0.80], [1.32, 0.90], [2.4, 0.95], [3.6, 0.97], [3.88, 0.95], [3.95, 0.72], [3.95, 0.42]], cab: [[1.32, 0.90], [1.75, 1.22], [2.05, 1.41], [2.7, 1.46], [3.3, 1.43], [3.7, 1.25], [3.88, 0.97]], lights: [0.66, 0.86] },
  sedan:    { L: 4.75, W: 1.82, wb: [0.95, 3.70], sill: 0.2, top: [[0, 0.38], [0.03, 0.55], [0.25, 0.70], [1.0, 0.80], [1.62, 0.90], [3.0, 0.95], [3.75, 0.98], [4.6, 0.99], [4.73, 0.85], [4.75, 0.45]], cab: [[1.62, 0.90], [2.05, 1.20], [2.35, 1.40], [3.15, 1.42], [3.6, 1.30], [3.9, 1.02], [3.97, 0.98]], lights: [0.66, 0.88] },
  coupe:    { L: 4.50, W: 1.86, wb: [0.92, 3.55], sill: 0.18, top: [[0, 0.34], [0.03, 0.5], [0.25, 0.64], [1.1, 0.74], [1.7, 0.86], [3.2, 0.92], [4.35, 0.93], [4.48, 0.8], [4.5, 0.42]], cab: [[1.7, 0.86], [2.2, 1.15], [2.5, 1.30], [3.0, 1.31], [3.6, 1.12], [4.1, 0.95], [4.2, 0.93]], lights: [0.6, 0.82] },
  wagon:    { L: 4.80, W: 1.82, wb: [0.95, 3.75], sill: 0.2, top: [[0, 0.38], [0.03, 0.55], [0.25, 0.70], [1.0, 0.80], [1.6, 0.90], [4.6, 0.98], [4.78, 0.92], [4.8, 0.45]], cab: [[1.6, 0.90], [2.05, 1.20], [2.35, 1.42], [4.45, 1.44], [4.66, 1.30], [4.76, 0.99]], lights: [0.66, 0.88] },
  roadster: { L: 4.05, W: 1.76, wb: [0.86, 3.25], sill: 0.18, top: [[0, 0.32], [0.03, 0.48], [0.25, 0.62], [1.2, 0.74], [1.75, 0.84], [2.7, 0.86], [3.9, 0.88], [4.03, 0.75], [4.05, 0.40]], cab: [[1.75, 0.84], [1.95, 1.06], [2.04, 1.13], [2.1, 0.95], [2.2, 0.86]], lights: [0.58, 0.8], open: true },
  pickup:   { L: 5.30, W: 1.96, wb: [1.05, 4.25], sill: 0.36, top: [[0, 0.5], [0.03, 0.75], [0.2, 0.98], [1.5, 1.08], [2.0, 1.12], [3.08, 1.15], [5.22, 1.15], [5.3, 1.1], [5.3, 0.6]], cab: [[2.0, 1.12], [2.4, 1.55], [2.6, 1.80], [3.02, 1.82], [3.1, 1.6], [3.12, 1.15]], lights: [0.85, 1.02], bed: [3.2, 5.2] },
  super:    { L: 4.55, W: 2.0, wb: [1.02, 3.65], sill: 0.14, top: [[0, 0.26], [0.03, 0.40], [0.3, 0.55], [1.2, 0.68], [1.75, 0.80], [3.0, 0.92], [4.3, 0.95], [4.52, 0.85], [4.55, 0.38]], cab: [[1.75, 0.80], [2.15, 1.06], [2.4, 1.17], [2.8, 1.18], [3.3, 1.05], [3.9, 0.94], [4.0, 0.92]], lights: [0.46, 0.64], intakes: true },
  dragster: { L: 6.2, W: 0.86, wb: [0.45, 5.55], sill: 0.16, top: [[0, 0.20], [0.05, 0.28], [2.5, 0.42], [4.0, 0.56], [4.3, 0.62], [5.4, 0.66], [6.2, 0.62], [6.2, 0.3]], cab: [[4.2, 0.6], [4.45, 0.92], [4.75, 0.95], [4.95, 0.68]], lights: [0.25, 0.3], dragster: true },
};
const PAINT_FIN = { gloss: [0.15, 0.28, 1], metal: [0.65, 0.3, 1], matte: [0.1, 0.62, 0], pearl: [0.45, 0.18, 1], candy: [0.8, 0.12, 1] };
const RIM_COL = { steel: 0x4a4f56, alloy: 0xcfd5dc, mesh: 0xc4cad1, forged: 0xe5e9ee, black: 0x17181b, gold: 0xc9a14a };
const CALIPER = { red: 0xc8241f, yellow: 0xe0b21a, blue: 0x2a5fd0, black: 0x1a1b1e, green: 0x2a8f4a, orange: 0xe36414 };

function profilePts(list, n) {
  const c = new THREE.SplineCurve(list.map(p => new THREE.Vector2(p[0], p[1])));
  return c.getPoints(n);
}

class Car3D {
  constructor() {
    this.scene = baseScene();
    this.cam = new THREE.PerspectiveCamera(30, 1, 0.05, 80);
    this.orbit = new Orbit(-2.35, 0.16, 9);
    this.orbit.auto = 0.12;
    this.key = '';
    // подиум
    const disc = new THREE.Mesh(new THREE.CylinderGeometry(3.6, 3.7, 0.08, 64), std(0x0d0e10, 0.5, 0.32));
    disc.position.y = -0.04; this.scene.add(disc);
    const ring = new THREE.Mesh(new THREE.TorusGeometry(3.62, 0.012, 6, 96), new THREE.MeshBasicMaterial({ color: 0xf2a93b }));
    ring.rotation.x = Math.PI / 2; ring.position.y = 0.001; this.scene.add(ring);
    this.disc = disc; this.ring = ring;
    const sh = new THREE.Mesh(new THREE.PlaneGeometry(1, 1), new THREE.MeshBasicMaterial({ map: G3.shadowTex, transparent: true, depthWrite: false }));
    sh.rotation.x = -Math.PI / 2; sh.position.y = 0.003; this.shadow = sh; this.scene.add(sh);
    this.wheels = [];
  }
  setCar(c, stats) {
    const key = JSON.stringify(c) + (stats ? stats.rWheel : '');
    if (key === this.key) return;
    this.key = key;
    if (this.root) { this.scene.remove(this.root); disposeTree(this.root); }
    this.root = this.build(c, stats);
    this.scene.add(this.root);
    const P = CAR_PROFILES[c.body] || CAR_PROFILES.hatch;
    this.shadow.scale.set(P.L * 1.25, P.W * 1.5, 1);
    this.orbit.target.set(0, 0.6, 0);
    this.orbit.base = Math.max(7.5, P.L * 2.15); if (!this.framed) { this.orbit.dist = this.orbit.base; this.framed = true; }
  }
  build(c, stats) {
    const P = CAR_PROFILES[c.body] || CAR_PROFILES.hatch;
    const root = new THREE.Group();
    const rW = stats ? stats.rWheel : 0.3;
    const wide = c.kit === 'wide';
    const lift = (c.rideHeight || 0) / 1000 + (rW - 0.3) * 0.7;
    const L = P.L, W = P.W + (wide ? 0.12 : 0), X = (x) => x - L / 2;
    const fin = PAINT_FIN[c.finish || 'gloss'] || PAINT_FIN.gloss;
    const paint = new THREE.MeshPhysicalMaterial({ color: new THREE.Color(c.color || '#c8312b'), metalness: fin[0], roughness: fin[1], clearcoat: fin[2], clearcoatRoughness: 0.05, envMapIntensity: 1.2 });
    if (c.finish === 'pearl') { paint.sheen = 1; paint.sheenColor = new THREE.Color(0xffffff); paint.sheenRoughness = 0.3; }
    const black = std(0x111215, 0.3, 0.45), chrome = std(0xffffff, 1, 0.05), trim = std(0x1a1b1e, 0.2, 0.6);
    const tint = { none: 0.55, light: 0.7, dark: 0.86, limo: 0.95 }[c.tint || 'light'];
    const glass = new THREE.MeshPhysicalMaterial({ color: 0x05080c, metalness: 0.6, roughness: 0.03, clearcoat: 1, transparent: true, opacity: Math.min(0.97, tint + 0.12), envMapIntensity: 1.3 });
    const add = (geo, mat, pos, rot, parent) => { const m = new THREE.Mesh(geo, mat); if (pos) m.position.copy(pos); if (rot) m.rotation.set(rot[0] || 0, rot[1] || 0, rot[2] || 0); (parent || root).add(m); return m; };
    const archR = rW + 0.035;
    // ---- нижняя часть кузова ----
    const top = profilePts(P.top, 90).map(p => new THREE.Vector2(p.x, p.y + lift));
    const sill = P.sill + lift;
    const sh = new THREE.Shape();
    sh.moveTo(top[0].x, top[0].y);
    top.forEach(p => sh.lineTo(p.x, p.y));
    sh.lineTo(L - 0.05, sill + 0.04); sh.lineTo(L - 0.1, sill);
    const arch = (ax) => {
      if (P.dragster) return;
      const cy = rW, t0 = Math.asin(clamp((sill - cy) / archR, -1, 1));
      // идём справа налево: от угла t0 (правая точка) до π - t0
      for (let k = 0; k <= 24; k++) { const t = t0 + (Math.PI - 2 * t0) * k / 24; sh.lineTo(ax + archR * Math.cos(t), cy + archR * Math.sin(t)); }
    };
    sh.lineTo(P.wb[1] + archR + 0.02, sill); arch(P.wb[1]);
    sh.lineTo(P.wb[0] + archR + 0.02, sill); arch(P.wb[0]);
    sh.lineTo(0.08, sill); sh.lineTo(0.02, sill + 0.06); sh.closePath();
    const bev = P.dragster ? 0.04 : 0.07;
    const bodyD = W - bev * 2;
    const g = new THREE.ExtrudeGeometry(sh, { depth: bodyD, bevelEnabled: true, bevelThickness: bev, bevelSize: bev * 0.8, bevelSegments: 5, curveSegments: 4, steps: 1 });
    g.translate(-L / 2, 0, -bodyD / 2);
    const belt = P.cab[0][1] + lift;
    const pos = g.attributes.position;
    for (let i = 0; i < pos.count; i++) {
      const x = pos.getX(i) + L / 2, y = pos.getY(i); let z = pos.getZ(i);
      const dEnd = Math.min(x, L - x), Rend = P.dragster ? 1.6 : 0.42;
      const u = clamp((Rend - dEnd) / Rend, 0, 1);
      let f = 1 - (P.dragster ? 0.55 : 0.09) * Math.pow(u, 2.5);
      if (y > belt - 0.14) f *= 1 - 0.07 * clamp((y - belt + 0.14) / 0.2, 0, 1);
      if (y < sill + 0.12) f *= 1 - 0.05 * clamp((sill + 0.12 - y) / 0.12, 0, 1);
      if (wide && !P.dragster) { const near = Math.min(Math.abs(x - P.wb[0]), Math.abs(x - P.wb[1])); if (near < archR + 0.25 && y < rW + archR) f *= 1 + 0.035 * (1 - near / (archR + 0.25)); }
      pos.setZ(i, z * f);
    }
    g.computeVertexNormals();
    add(g, paint, null);
    // ---- салон и стёкла ----
    const cabPts = profilePts(P.cab, 40).map(p => new THREE.Vector2(p.x, p.y + lift));
    const cs = new THREE.Shape(); cs.moveTo(cabPts[0].x, cabPts[0].y - 0.02); cabPts.forEach(p => cs.lineTo(p.x, p.y)); cs.lineTo(cabPts[cabPts.length - 1].x, cabPts[0].y - 0.02); cs.closePath();
    const cabD = (P.dragster ? W * 0.55 : W * 0.8);
    const roofY = Math.max(...cabPts.map(p => p.y));
    const taper = (geo, base, k) => { const ps = geo.attributes.position; for (let i = 0; i < ps.count; i++) { const y = ps.getY(i); ps.setZ(i, ps.getZ(i) * (1 - k * clamp((y - base) / (roofY - base + 1e-3), 0, 1))); } geo.computeVertexNormals(); };
    const cg = new THREE.ExtrudeGeometry(cs, { depth: cabD, bevelEnabled: true, bevelThickness: 0.03, bevelSize: 0.03, bevelSegments: 3, curveSegments: 2 });
    cg.translate(-L / 2, 0, -cabD / 2); taper(cg, belt, 0.2);
    add(cg, glass);
    // крыша
    if (!P.open && !P.dragster) {
      const i0 = Math.floor(cabPts.length * 0.22), i1 = Math.ceil(cabPts.length * 0.8);
      const seg = cabPts.slice(i0, i1 + 1);
      const rs = new THREE.Shape(); rs.moveTo(seg[0].x, seg[0].y - 0.04); seg.forEach(p => rs.lineTo(p.x, p.y + 0.012)); for (let k = seg.length - 1; k >= 0; k--) rs.lineTo(seg[k].x, seg[k].y - 0.045); rs.closePath();
      const rd = cabD * 0.8 + 0.02;
      const rg = new THREE.ExtrudeGeometry(rs, { depth: rd, bevelEnabled: true, bevelThickness: 0.02, bevelSize: 0.015, bevelSegments: 3, curveSegments: 2 });
      rg.translate(-L / 2, 0, -rd / 2);
      add(rg, c.roof === 'carbon' ? std(0x222327, 0.4, 0.22, { map: carbonTex() }) : paint);
      // стойки: передняя и задняя
      [1, -1].forEach(s => {
        const zAt = (y) => s * (cabD / 2 + 0.03) * (1 - 0.2 * clamp((y - belt) / (roofY - belt), 0, 1));
        const ap = cabPts.slice(0, i0 + 1).map(p => V3(X(p.x), p.y, zAt(p.y)));
        if (ap.length > 1) add(new THREE.TubeGeometry(new THREE.CatmullRomCurve3(ap), 12, 0.035, 6), paint);
        const cp = cabPts.slice(i1).map(p => V3(X(p.x), p.y, zAt(p.y)));
        if (cp.length > 1) add(new THREE.TubeGeometry(new THREE.CatmullRomCurve3(cp), 12, P.top.length > 9 && c.body === 'hatch' ? 0.05 : 0.07, 6), paint);
        // средняя стойка
        const bx = (cabPts[i0].x + cabPts[i1].x) / 2 + 0.1;
        add(new THREE.BoxGeometry(0.07, roofY - belt - 0.04, 0.02), trim, V3(X(bx), (roofY + belt) / 2 - 0.02, zAt((roofY + belt) / 2) * 0.99));
      });
    }
    // салон: сиденья, руль, каркас
    const seatX = (P.cab[0][0] + P.cab[P.cab.length - 1][0]) / 2 + (P.open ? 0.2 : 0.05);
    [1, -1].forEach(s => {
      if (P.dragster && s < 0) return;
      const sz = P.dragster ? 0 : s * W * 0.22;
      add(new THREE.BoxGeometry(0.5, 0.12, 0.45), trim, V3(X(seatX), belt - 0.18, sz));
      add(new THREE.BoxGeometry(0.12, 0.62, 0.45), c.seats === 'bucket' ? std(0x2a2b30, 0.1, 0.8) : trim, V3(X(seatX + 0.3), belt + 0.08, sz), [0, 0, 0.18]);
      if (c.seats === 'bucket' || P.open) add(new THREE.BoxGeometry(0.1, 0.18, 0.28), std(0xc8241f, 0.1, 0.7), V3(X(seatX + 0.36), belt + 0.4, sz), [0, 0, 0.18]);
    });
    add(new THREE.TorusGeometry(0.17, 0.02, 8, 24), trim, V3(X(seatX - 0.45), belt + 0.1, P.dragster ? 0 : W * 0.22), [0, Math.PI / 2 + 0.4, 0]);
    if (c.cage || P.dragster || P.open) {
      const cm = std(c.cage ? 0xd23b2a : 0x2a2b30, 0.6, 0.35);
      const hx = X(seatX + 0.45), hz = P.dragster ? 0.3 : cabD * 0.38, hy = (P.open ? belt + 0.45 : roofY - 0.08);
      const hoop = new THREE.CatmullRomCurve3([V3(hx, belt - 0.2, -hz), V3(hx, hy, -hz * 0.85), V3(hx, hy, hz * 0.85), V3(hx, belt - 0.2, hz)]);
      add(new THREE.TubeGeometry(hoop, 20, 0.025, 6), cm);
      if (c.cage && !P.open) { [1, -1].forEach(s => add(new THREE.TubeGeometry(new THREE.CatmullRomCurve3([V3(hx, hy, s * hz * 0.85), V3(X(P.cab[1][0] + 0.15), hy - 0.05, s * hz * 0.8), V3(X(P.cab[0][0] + 0.1), belt, s * hz)]), 16, 0.022, 6), cm)); }
    }
    // ---- кузов: детали ----
    const front = X(0), rear = X(L);
    const ly = P.lights[0] + lift, lyT = P.lights[1] + lift;
    const zHalf = (W / 2 - 0.12);
    // фары
    const led = c.lights === 'led';
    const lampM = new THREE.MeshStandardMaterial({ color: 0xffffff, emissive: led ? 0xcfe4ff : 0xfff1c8, emissiveIntensity: led ? 2.2 : 1.4, roughness: 0.1, metalness: 0.2 });
    if (!P.dragster) [1, -1].forEach(s => {
      add(new THREE.BoxGeometry(0.16, 0.085, 0.36), black, V3(front + 0.1, ly + 0.02, s * zHalf * 0.72));
      add(new THREE.BoxGeometry(0.15, led ? 0.03 : 0.06, led ? 0.3 : 0.12), lampM, V3(front + 0.095, ly + 0.03, s * zHalf * (led ? 0.72 : 0.8)));
      if (!led) add(new THREE.BoxGeometry(0.15, 0.05, 0.1), lampM, V3(front + 0.095, ly + 0.03, s * zHalf * 0.6));
    });
    // решётка и воздухозаборник
    if (!P.dragster) {
      add(new THREE.BoxGeometry(0.1, 0.1, W * 0.36), std(0x0c0d0f, 0.4, 0.5, { map: stripeTex('#1c1d20', '#050506', 8, false) }), V3(front + 0.07, ly, 0));
      add(new THREE.BoxGeometry(0.1, 0.09, W * 0.6), std(0x0c0d0f, 0.3, 0.6), V3(front + 0.09, sill + 0.13, 0));
    }
    // задние фонари
    const tailM = new THREE.MeshStandardMaterial({ color: 0x5a0606, emissive: 0xff1a12, emissiveIntensity: 1.6, roughness: 0.2 });
    if (!P.dragster) {
      [1, -1].forEach(s => add(new THREE.BoxGeometry(0.1, 0.07, 0.4), tailM, V3(rear - 0.075, lyT, s * zHalf * 0.72)));
      if (c.body === 'coupe' || c.body === 'super' || led) add(new THREE.BoxGeometry(0.09, 0.025, W * 0.5), tailM, V3(rear - 0.07, lyT + 0.01, 0));
      add(new THREE.BoxGeometry(0.04, 0.12, 0.52), std(0xf2f2f2, 0, 0.5), V3(rear - 0.03, sill + 0.24, 0)); // номер
    }
    // зеркала и ручки дверей
    if (!P.dragster) [1, -1].forEach(s => {
      const mx = X(P.cab[0][0] + 0.18), mz = s * (W / 2 - 0.02);
      add(new THREE.BoxGeometry(0.06, 0.03, 0.1), paint, V3(mx + 0.03, belt + 0.05, mz - s * 0.05));
      add(new THREE.SphereGeometry(0.07, 12, 8), paint, V3(mx, belt + 0.1, mz), null).scale.set(1, 0.7, 0.6);
      add(new THREE.BoxGeometry(0.14, 0.025, 0.02), chrome, V3(X(seatX), belt - 0.07, s * (W / 2 - 0.05)));
    });
    // обвес
    if (c.kit === 'sport' || wide) [1, -1].forEach(s => add(new THREE.BoxGeometry(P.wb[1] - P.wb[0] - archR * 2 - 0.05, 0.08, 0.06), black, V3(X((P.wb[0] + P.wb[1]) / 2), sill + 0.01, s * (W / 2 - 0.04))));
    if (wide && !P.dragster) P.wb.forEach(ax => [1, -1].forEach(s => { const t = add(new THREE.TorusGeometry(archR + 0.02, 0.04, 8, 24, Math.PI * 0.86), paint, V3(X(ax), rW, s * (W / 2 - 0.05)), [0, 0, Math.PI * 0.07]); t.scale.set(1, 1, 1.6); }));
    // капот
    const hood = P.top.find(p => p[0] > 0.15);
    const hood1 = P.cab[0];
    if (!P.dragster && c.hood && c.hood !== 'stock') {
      const hx0 = X(0.35), hx1 = X(hood1[0] - 0.05), hy0 = hood[1] + lift + 0.03, hy1 = hood1[1] + lift + 0.01;
      const hl = Math.hypot(hx1 - hx0, hy1 - hy0), ang = Math.atan2(hy1 - hy0, hx1 - hx0);
      if (c.hood === 'carbon') add(new THREE.BoxGeometry(hl, 0.012, W * 0.62), std(0x222327, 0.4, 0.2, { map: carbonTex() }), V3((hx0 + hx1) / 2, (hy0 + hy1) / 2 + 0.01, 0), [0, 0, ang]);
      if (c.hood === 'scoop' || c.hood === 'carbon') {
        const sc = add(new THREE.BoxGeometry(hl * 0.4, 0.08, W * 0.26), c.hood === 'carbon' ? std(0x222327, 0.4, 0.2, { map: carbonTex() }) : paint, V3((hx0 + hx1) / 2 + hl * 0.05, (hy0 + hy1) / 2 + 0.04, 0), [0, 0, ang]);
        add(new THREE.BoxGeometry(0.02, 0.05, W * 0.22), black, V3((hx0 + hx1) / 2 - hl * 0.15 + 0.005, (hy0 + hy1) / 2 + 0.04, 0), [0, 0, ang]);
      }
    }
    if (P.intakes) [1, -1].forEach(s => add(new THREE.BoxGeometry(0.45, 0.22, 0.05), black, V3(X(P.wb[1] - 0.75), sill + 0.38, s * (W / 2 - 0.03))));
    // аэродинамика
    if (c.splitter) add(new THREE.BoxGeometry(0.22, 0.02, W * 0.86), std(0x1a1b1e, 0.4, 0.25, { map: carbonTex() }), V3(front + 0.04, sill - 0.01, 0));
    if (c.diffuser) { add(new THREE.BoxGeometry(0.45, 0.02, W * 0.7), black, V3(rear - 0.18, sill - 0.01, 0), [0, 0, 0.18]); for (let k = -2; k <= 2; k++) add(new THREE.BoxGeometry(0.4, 0.08, 0.01), black, V3(rear - 0.2, sill + 0.02, k * W * 0.14), [0, 0, 0.18]); }
    const tailTop = Math.max(...P.top.filter(p => p[0] > L * 0.8).map(p => p[1])) + lift;
    if (c.spoiler === 'lip') add(new THREE.BoxGeometry(0.12, 0.03, W * 0.8), paint, V3(rear - 0.1, tailTop + 0.02, 0), [0, 0, -0.25]);
    if (c.spoiler === 'wing' || c.spoiler === 'big' || P.dragster) {
      const big = c.spoiler === 'big' || P.dragster, hgt = P.dragster ? 0.9 : big ? 0.34 : 0.18, chord = big ? 0.36 : 0.26;
      const af = new THREE.Shape(); af.moveTo(0, 0); af.quadraticCurveTo(chord * 0.3, 0.05, chord, 0.012); af.lineTo(chord, -0.005); af.quadraticCurveTo(chord * 0.3, -0.012, 0, 0); 
      const wd = W * (P.dragster ? 1.25 : 0.95);
      const wg = new THREE.ExtrudeGeometry(af, { depth: wd, bevelEnabled: false, curveSegments: 8 }); wg.translate(0, 0, -wd / 2);
      const wing = add(wg, P.dragster ? paint : std(0x1c1d20, 0.4, 0.25, { map: carbonTex() }), V3(rear - chord - 0.05, tailTop + hgt, 0), [0, 0, -0.12]);
      [1, -1].forEach(s => { add(new THREE.BoxGeometry(0.1, hgt, 0.02), black, V3(rear - chord * 0.5 - 0.05, tailTop + hgt / 2, s * wd * 0.3)); add(new THREE.BoxGeometry(chord * 1.1, 0.14, 0.012), black, V3(rear - chord * 0.5 - 0.05, tailTop + hgt + 0.02, s * wd / 2)); });
    }
    // кузов пикапа: открытая платформа
    if (P.bed) {
      const bx0 = X(P.bed[0]), bx1 = X(P.bed[1]), by = 1.15 + lift;
      add(new THREE.BoxGeometry(bx1 - bx0, 0.012, W * 0.84), std(0x15161a, 0.2, 0.8, { map: stripeTex('#1d1e22', '#0f1012', 10, false) }), V3((bx0 + bx1) / 2, by + 0.008, 0));
      [1, -1].forEach(s => add(new THREE.BoxGeometry(bx1 - bx0, 0.05, 0.06), paint, V3((bx0 + bx1) / 2, by + 0.03, s * (W / 2 - 0.08))));
    }
    // выхлоп
    const tips = c.tips === 'quad' ? [-0.38, -0.27, 0.27, 0.38] : c.tips === 'dual' ? [-0.32, 0.32] : c.tips === 'center' ? [-0.05, 0.05] : [0.3];
    tips.forEach(z => add(new THREE.CylinderGeometry(0.045, 0.045, 0.14, 20, 1, true), chrome, V3(rear - 0.02, sill + 0.03, z * W * 0.9), [0, 0, Math.PI / 2]));
    // ---- колёса ----
    this.wheels = [];
    const tw = (stats ? c.tireWidth : 195) / 1000;
    const rimR = c.rim * 0.0254 / 2;
    P.wb.forEach((ax, i) => [1, -1].forEach(s => {
      let rr = rW, ww = tw;
      if (P.dragster) { if (i === 0) { rr = 0.24; ww = 0.09; } else { rr = 0.46; ww = 0.43; } }
      const zz = s * (P.dragster ? (i === 0 ? W / 2 + 0.25 : W / 2 + ww / 2 + 0.12) : W / 2 - ww / 2 + 0.005 + (wide ? 0.03 : 0));
      const wg = this.wheel(c, rr, ww, P.dragster ? (i === 0 ? 0.17 : 0.29) : rimR, s, P.dragster && i === 0);
      wg.position.set(X(ax), rr, zz);
      root.add(wg); this.wheels.push(wg);
    }));
    if (P.dragster) [1, -1].forEach(s => add(new THREE.CylinderGeometry(0.02, 0.02, W / 2 + 0.25, 8), black, V3(X(P.wb[0]), 0.24, s * (W / 4 + 0.12)), [Math.PI / 2, 0, 0]));
    return root;
  }
  wheel(c, R, w, rimR, side, wire) {
    const g = new THREE.Group();
    // шина: профиль вращения
    const ri = Math.min(rimR + 0.01, R - 0.03), pts = [];
    const sidewall = R - ri, rnd = Math.min(sidewall * 0.45, w * 0.25);
    pts.push(new THREE.Vector2(ri, -w / 2 + 0.005));
    pts.push(new THREE.Vector2(ri + sidewall * 0.4, -w / 2 - 0.003));
    for (let k = 0; k <= 6; k++) { const a = -Math.PI / 2 + k / 6 * Math.PI / 2; pts.push(new THREE.Vector2(R - rnd + Math.cos(a) * rnd, -w / 2 + rnd + Math.sin(a) * rnd)); }
    for (let k = 0; k <= 6; k++) { const a = k / 6 * Math.PI / 2; pts.push(new THREE.Vector2(R - rnd + Math.cos(a) * rnd, w / 2 - rnd + Math.sin(a) * rnd)); }
    pts.push(new THREE.Vector2(ri + sidewall * 0.4, w / 2 + 0.003)); pts.push(new THREE.Vector2(ri, w / 2 - 0.005));
    const tg = new THREE.LatheGeometry(pts, 40); tg.rotateX(Math.PI / 2);
    const slick = c.tire === 'slick' || c.tire === 'drag';
    g.add(new THREE.Mesh(tg, std(0x121314, 0, slick ? 0.55 : 0.9, slick ? {} : { map: stripeTex('#1a1b1d', '#0b0b0c', 24, true) })));
    // обод
    const rimM = std(RIM_COL[c.rimStyle] || 0xcfd5dc, c.rimStyle === 'steel' ? 0.4 : 1, c.rimStyle === 'forged' ? 0.08 : c.rimStyle === 'black' ? 0.3 : 0.18);
    const barrel = new THREE.Mesh(new THREE.CylinderGeometry(ri, ri, w * 0.9, 36, 1, true), rimM); barrel.rotation.x = Math.PI / 2; g.add(barrel);
    const lip = new THREE.Mesh(new THREE.TorusGeometry(ri, 0.01, 6, 40), rimM); lip.position.z = side * w * 0.45; g.add(lip);
    const face = new THREE.Group(); face.position.z = side * w * 0.36; g.add(face);
    // тормоз
    const disc = new THREE.Mesh(new THREE.CylinderGeometry(ri * 0.82, ri * 0.82, 0.025, 30), std(0x8d939a, 1, 0.35)); disc.rotation.x = Math.PI / 2; disc.position.z = side * w * 0.12; g.add(disc);
    const cal = new THREE.Mesh(new THREE.BoxGeometry(0.1, ri * 0.55, 0.06), std(CALIPER[c.caliper || 'black'] || 0x1a1b1e, 0.3, 0.35)); cal.position.set(-ri * 0.62, ri * 0.25, side * w * 0.2); cal.rotation.z = 0.5; this.calipers = (this.calipers || []).concat(cal);
    const spokes = new THREE.Group(); face.add(spokes);
    const style = wire ? 'wire' : c.rimStyle;
    let nSp = { steel: 0, alloy: 5, mesh: 14, forged: 10, black: 6, gold: 8, wire: 24 }[style]; if (nSp === undefined) nSp = 5;
    if (nSp === 0) {
      const d = new THREE.Mesh(new THREE.CylinderGeometry(ri * 0.96, ri * 0.96, 0.02, 32), rimM); d.rotation.x = Math.PI / 2; spokes.add(d);
      for (let k = 0; k < 6; k++) { const h = new THREE.Mesh(new THREE.CylinderGeometry(ri * 0.13, ri * 0.13, 0.022, 12), std(0x0b0b0c, 0, 0.9)); h.rotation.x = Math.PI / 2; h.position.set(Math.cos(k / 6 * Math.PI * 2) * ri * 0.6, Math.sin(k / 6 * Math.PI * 2) * ri * 0.6, side * 0.002); spokes.add(h); }
    } else for (let k = 0; k < nSp; k++) {
      const a = k / nSp * Math.PI * 2;
      const thick = style === 'alloy' ? 0.055 : style === 'black' ? 0.045 : style === 'wire' ? 0.004 : 0.022;
      const sp = new THREE.Mesh(new THREE.BoxGeometry(thick, ri * 0.82, 0.03), rimM);
      sp.geometry.translate(0, ri * 0.5, 0);
      sp.rotation.z = a + (style === 'mesh' ? (k % 2 ? 0.25 : -0.25) : style === 'forged' ? (k % 2 ? 0.08 : -0.08) : 0);
      spokes.add(sp);
    }
    const hub = new THREE.Mesh(new THREE.CylinderGeometry(ri * 0.2, ri * 0.24, 0.05, 20), rimM); hub.rotation.x = Math.PI / 2; spokes.add(hub);
    const cap = new THREE.Mesh(new THREE.CylinderGeometry(ri * 0.1, ri * 0.1, 0.055, 16), std(0xf2a93b, 0.6, 0.3)); cap.rotation.x = Math.PI / 2; spokes.add(cap);
    g.userData.spin = [spokes, g.children[0], barrel];
    const outer = new THREE.Group(); outer.add(g); outer.add(cal); outer.userData.inner = g;
    return outer;
  }
  update(dt, wheelSpin) {
    if (this.wheels) this.wheels.forEach(w => { w.userData.inner.rotation.z -= (wheelSpin || 0) * dt; });
  }
  // спрайт вида сбоку для заезда: кузов без колёс + одно колесо
  sprite(c, stats) {
    this.setCar(c, stats);
    const P = CAR_PROFILES[c.body] || CAR_PROFILES.hatch;
    const ppm = 120, Wpx = Math.ceil(P.L * ppm + 60), Hpx = Math.ceil(2.4 * ppm);
    const cam = new THREE.OrthographicCamera(-Wpx / 2 / ppm, Wpx / 2 / ppm, Hpx / ppm - 0.15, -0.15, 0.1, 30);
    cam.position.set(0, 0, 10); cam.lookAt(0, 0, 0); cam.position.y = 0; cam.updateProjectionMatrix();
    const r = G3.r, prevSize = r.getSize(new THREE.Vector2()), prevPR = r.getPixelRatio();
    r.setPixelRatio(1); r.setSize(Wpx, Hpx, false);
    this.disc.visible = false; this.ring.visible = false; this.shadow.visible = false;
    this.wheels.forEach(w => w.visible = false);
    r.render(this.scene, cam);
    const body = document.createElement('canvas'); body.width = Wpx; body.height = Hpx; body.getContext('2d').drawImage(r.domElement, 0, 0);
    // колесо
    this.root.children.forEach(ch => ch.visible = false);
    const w0 = this.wheels.find(w => w.position.z > 0) || this.wheels[0];
    w0.visible = true; const wx = w0.position.x, wy = w0.position.y;
    const wr = (P.dragster ? 0.46 : stats.rWheel) + 0.02;
    const cam2 = new THREE.OrthographicCamera(-wr, wr, wr, -wr, 0.1, 30); cam2.position.set(wx, wy, 10); cam2.lookAt(wx, wy, 0);
    const wpx = Math.ceil(wr * 2 * ppm);
    r.setSize(wpx, wpx, false); r.render(this.scene, cam2);
    const wheel = document.createElement('canvas'); wheel.width = wheel.height = wpx; wheel.getContext('2d').drawImage(r.domElement, 0, 0);
    let wheelF = null;
    if (P.dragster) {
      const w1 = this.wheels.find(w => w !== w0 && w.position.z > 0); w0.visible = false; w1.visible = true;
      const fr = 0.26; const cam3 = new THREE.OrthographicCamera(-fr, fr, fr, -fr, 0.1, 30); cam3.position.set(w1.position.x, w1.position.y, 10); cam3.lookAt(w1.position.x, w1.position.y, 0);
      const fpx = Math.ceil(fr * 2 * ppm); r.setSize(fpx, fpx, false); r.render(this.scene, cam3);
      wheelF = document.createElement('canvas'); wheelF.width = wheelF.height = fpx; wheelF.getContext('2d').drawImage(r.domElement, 0, 0);
    }
    this.root.children.forEach(ch => ch.visible = true);
    this.wheels.forEach(w => w.visible = true);
    this.disc.visible = true; this.ring.visible = true; this.shadow.visible = true;
    r.setPixelRatio(prevPR); r.setSize(prevSize.x, prevSize.y, false); G3.w = 0;
    return { body, wheel, wheelF, ppm, L: P.L, axles: P.wb, rW: P.dragster ? 0.46 : stats.rWheel, rF: P.dragster ? 0.24 : stats.rWheel, ox: Wpx / 2 - P.L / 2 * ppm, groundPx: Hpx - 0.15 * ppm };
  }
}
