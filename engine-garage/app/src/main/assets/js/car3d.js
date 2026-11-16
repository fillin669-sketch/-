'use strict';
// ================= 3D-ЗАЕЗД (модели машин — в body3d.js) =================
function canvasTex(w, h, draw, rep) {
  const cv = document.createElement('canvas'); cv.width = w; cv.height = h; draw(cv.getContext('2d'), w, h);
  const t = new THREE.CanvasTexture(cv); t.encoding = THREE.sRGBEncoding; t.anisotropy = 4;
  if (rep) { t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(rep[0], rep[1]); }
  return t;
}
class Race3D {
  constructor(me, op) {
    const s = this.scene = new THREE.Scene();
    s.background = new THREE.Color(0x070a14);
    s.fog = new THREE.Fog(0x0b1020, 35, 230);
    s.environment = G3.env;
    s.add(new THREE.HemisphereLight(0x9fb4e0, 0x2a2620, 0.75));
    const moon = new THREE.DirectionalLight(0xbfd0ff, 0.95); moon.position.set(-20, 40, 15); s.add(moon);
    this.cam = new THREE.PerspectiveCamera(52, 1, 0.1, 400);
    this.orbit = { tick() { }, apply() { } };
    const LEN = 700, X0 = 40;
    // асфальт
    const asph = canvasTex(256, 256, (g, w, h) => { g.fillStyle = '#26272b'; g.fillRect(0, 0, w, h); for (let i = 0; i < 2600; i++) { const v = 25 + Math.random() * 30; g.fillStyle = `rgb(${v},${v},${v + 2})`; g.fillRect(Math.random() * w, Math.random() * h, 1.5, 1.5); } g.fillStyle = 'rgba(0,0,0,0.25)'; g.fillRect(0, h * 0.45, w, h * 0.1); }, [LEN / 6, 3]);
    const road = new THREE.Mesh(new THREE.PlaneGeometry(LEN, 14), new THREE.MeshStandardMaterial({ map: asph, roughness: 0.85, metalness: 0.05 }));
    road.rotation.x = -Math.PI / 2; road.position.set(X0 - LEN / 2, 0, 0); s.add(road);
    // резиновые следы на старте
    const rub = canvasTex(256, 64, (g, w, h) => { const gr = g.createLinearGradient(0, 0, w, 0); gr.addColorStop(0, 'rgba(0,0,0,0.85)'); gr.addColorStop(1, 'rgba(0,0,0,0)'); g.fillStyle = gr; [10, 22, 42, 54].forEach(y => g.fillRect(0, y, w, 6)); });
    [-1.8, 1.8].forEach(z => { const m = new THREE.Mesh(new THREE.PlaneGeometry(60, 1.6), new THREE.MeshBasicMaterial({ map: rub, transparent: true, depthWrite: false })); m.rotation.x = -Math.PI / 2; m.position.set(-30, 0.005, z); m.rotation.z = Math.PI; s.add(m); });
    // обочины и разметка
    const lineM = new THREE.MeshBasicMaterial({ color: 0xdedfe2 });
    [-3.9, 3.9].forEach(z => { const m = new THREE.Mesh(new THREE.PlaneGeometry(LEN, 0.15), lineM); m.rotation.x = -Math.PI / 2; m.position.set(X0 - LEN / 2, 0.006, z); s.add(m); });
    const dash = new THREE.InstancedMesh(new THREE.PlaneGeometry(3, 0.12), lineM, 120);
    const mt = new THREE.Matrix4(), q = new THREE.Quaternion().setFromEuler(new THREE.Euler(-Math.PI / 2, 0, 0));
    for (let i = 0; i < 120; i++) { mt.compose(V3(X0 - i * 6, 0.006, 0), q, V3(1, 1, 1)); dash.setMatrixAt(i, mt); } s.add(dash);
    // старт и финиш
    const checker = canvasTex(64, 64, (g) => { for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) { g.fillStyle = (x + y) % 2 ? '#111' : '#f2f2f2'; g.fillRect(x * 8, y * 8, 8, 8); } }, [1, 8]);
    const fin = new THREE.Mesh(new THREE.PlaneGeometry(1.2, 7.8), new THREE.MeshBasicMaterial({ map: checker })); fin.rotation.x = -Math.PI / 2; fin.position.set(-402, 0.008, 0); s.add(fin);
    const st = new THREE.Mesh(new THREE.PlaneGeometry(0.25, 7.8), lineM); st.rotation.x = -Math.PI / 2; st.position.set(0, 0.008, 0); s.add(st);
    // финишная арка
    const archM = std(0x1d1f24, 0.6, 0.4);
    [-4.6, 4.6].forEach(z => { const p = new THREE.Mesh(new THREE.BoxGeometry(0.4, 6.2, 0.4), archM); p.position.set(-402, 3.1, z); s.add(p); });
    const sign = canvasTex(512, 96, (g, w, h) => { g.fillStyle = '#111317'; g.fillRect(0, 0, w, h); g.fillStyle = '#f2a93b'; g.font = 'bold 60px sans-serif'; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText('ФИНИШ · 402 м', w / 2, h / 2 + 3); });
    const sg = new THREE.Mesh(new THREE.BoxGeometry(0.3, 1.2, 9.6), [archM, archM, archM, archM, new THREE.MeshBasicMaterial({ map: sign }), new THREE.MeshBasicMaterial({ map: sign })]); sg.position.set(-402, 6, 0); s.add(sg);
    // таблички дистанции
    [100, 200, 300].forEach(m => {
      const t = canvasTex(128, 128, (g, w, h) => { g.fillStyle = '#f4f4f4'; g.fillRect(0, 0, w, h); g.fillStyle = '#111'; g.font = 'bold 54px sans-serif'; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText(String(m), w / 2, h / 2); });
      [-5.2, 5.2].forEach(z => { const b = new THREE.Mesh(new THREE.PlaneGeometry(1.1, 1.1), new THREE.MeshBasicMaterial({ map: t, side: THREE.DoubleSide })); b.position.set(-m, 1.6, z); b.rotation.y = Math.PI / 2; s.add(b); const pole = new THREE.Mesh(new THREE.BoxGeometry(0.08, 1.1, 0.08), archM); pole.position.set(-m, 0.55, z); s.add(pole); });
    });
    // отбойники
    const bar = new THREE.InstancedMesh(new THREE.BoxGeometry(3.9, 0.8, 0.5), std(0x9a9da3, 0.1, 0.8), 2 * 170);
    let n = 0; [-4.8].forEach(z => { for (let i = 0; i < 170; i++) { mt.compose(V3(X0 - i * 4, 0.4, z), new THREE.Quaternion(), V3(1, 1, 1)); bar.setMatrixAt(n++, mt); } }); s.add(bar);
    const stripeBar = new THREE.InstancedMesh(new THREE.BoxGeometry(1.9, 0.18, 0.52), std(0xc8312b, 0.1, 0.6), 2 * 170);
    n = 0; [-4.8].forEach(z => { for (let i = 0; i < 170; i++) { mt.compose(V3(X0 - i * 4 - 1, 0.62, z), new THREE.Quaternion(), V3(1, 1, 1)); stripeBar.setMatrixAt(n++, mt); } }); s.add(stripeBar);
    // фонари
    const poleG = new THREE.CylinderGeometry(0.08, 0.1, 8, 8); const poles = new THREE.InstancedMesh(poleG, archM, 60);
    const headG = new THREE.BoxGeometry(1.4, 0.15, 0.4); const heads = new THREE.InstancedMesh(headG, new THREE.MeshBasicMaterial({ color: 0xffe2a8 }), 60);
    n = 0; for (let i = 0; i < 30; i++) [-8.5, 8.5].forEach(z => { mt.compose(V3(X0 - i * 24, 4, z), new THREE.Quaternion(), V3(1, 1, 1)); poles.setMatrixAt(n, mt); mt.compose(V3(X0 - i * 24, 8, z * 0.9), new THREE.Quaternion(), V3(1, 1, 1)); heads.setMatrixAt(n, mt); n++; });
    s.add(poles); s.add(heads);
    // световые пятна под фонарями
    const pool = canvasTex(128, 128, (g, w, h) => { const gr = g.createRadialGradient(64, 64, 2, 64, 64, 64); gr.addColorStop(0, 'rgba(255,214,150,0.38)'); gr.addColorStop(1, 'rgba(255,214,150,0)'); g.fillStyle = gr; g.fillRect(0, 0, w, h); });
    const pools = new THREE.InstancedMesh(new THREE.PlaneGeometry(14, 14), new THREE.MeshBasicMaterial({ map: pool, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending }), 60);
    n = 0; for (let i = 0; i < 30; i++) [-4, 4].forEach(z => { mt.compose(V3(X0 - i * 24, 0.01, z), q, V3(1, 1, 1)); pools.setMatrixAt(n++, mt); }); s.add(pools);
    // трибуна со зрителями у старта
    const stand = new THREE.Mesh(new THREE.BoxGeometry(60, 0.4, 8), archM); stand.position.set(-10, 0.2, -11); s.add(stand);
    for (let r = 0; r < 5; r++) { const step = new THREE.Mesh(new THREE.BoxGeometry(60, 0.5, 1.4), std(0x2a2d33, 0.2, 0.8)); step.position.set(-10, 0.5 + r * 0.5, -8.2 - r * 1.4); s.add(step); }
    const nC = G3.quality === 'low' ? 80 : 300;
    const crowd = new THREE.InstancedMesh(new THREE.BoxGeometry(0.42, 0.75, 0.32), new THREE.MeshStandardMaterial({ roughness: 0.8 }), nC);
    const cc = new THREE.Color();
    for (let i = 0; i < nC; i++) { const r = i % 5; mt.compose(V3(-38 + Math.random() * 56, 1.1 + r * 0.5, -8.2 - r * 1.4), new THREE.Quaternion(), V3(1, 0.9 + Math.random() * 0.3, 1)); crowd.setMatrixAt(i, mt); cc.setHSL(Math.random(), 0.5, 0.3 + Math.random() * 0.3); crowd.setColorAt(i, cc); }
    s.add(crowd);
    // город на горизонте
    const win = canvasTex(64, 128, (g, w, h) => { g.fillStyle = '#0d1018'; g.fillRect(0, 0, w, h); for (let y = 4; y < h; y += 8) for (let x = 3; x < w; x += 8) if (Math.random() < 0.38) { g.fillStyle = Math.random() < 0.8 ? '#ffd27a' : '#9cc8ff'; g.fillRect(x, y, 4, 4); } });
    const bm = new THREE.MeshStandardMaterial({ color: 0x222633, emissive: 0xffffff, emissiveMap: win, emissiveIntensity: 0.9, map: win });
    const nB = G3.quality === 'low' ? 30 : G3.quality === 'mid' ? 55 : 80;
    const bld = new THREE.InstancedMesh(new THREE.BoxGeometry(1, 1, 1), bm, nB * 2);
    n = 0; for (let i = 0; i < nB; i++) [-1, 1].forEach(sd => { const h = 15 + Math.random() * 55, w = 8 + Math.random() * 14; mt.compose(V3(X0 + 20 - i * 9.5, h / 2, sd * (45 + Math.random() * 60)), new THREE.Quaternion(), V3(w, h, 8 + Math.random() * 10)); bld.setMatrixAt(n++, mt); }); s.add(bld);
    // «ёлка» стартового светофора
    const tree = new THREE.Group(); tree.position.set(1.2, 0, 0); s.add(tree);
    const tp = new THREE.Mesh(new THREE.BoxGeometry(0.2, 2.6, 0.2), archM); tp.position.y = 1.3; tree.add(tp);
    const box = new THREE.Mesh(new THREE.BoxGeometry(0.35, 1.5, 0.7), std(0x111215, 0.3, 0.6)); box.position.y = 2.6; tree.add(box);
    this.lamps = [];
    for (let k = 0; k < 4; k++) [-0.2, 0.2].forEach(z => {
      const m = new THREE.Mesh(new THREE.SphereGeometry(0.11, 12, 8), new THREE.MeshBasicMaterial({ color: k < 3 ? 0x3a2a08 : 0x0b2a14 }));
      m.position.set(0.18, 3.15 - k * 0.36, z); tree.add(m); this.lamps.push({ m, k });
    });
    // машины
    this.me = buildCarModel(me.c, me.st, me.e); this.op = buildCarModel(op.c, op.st, null);
    this.laneMe = 1.8; this.laneOp = -1.8;
    s.add(this.me.group); s.add(this.op.group);
    this.meL = (CAR_PROFILES[me.c.body] || CAR_PROFILES.hatch).L; this.opL = (CAR_PROFILES[op.c.body] || CAR_PROFILES.hatch).L;
    // свет фар
    const hl = new THREE.SpotLight(0xfff1d0, 2.2, 45, 0.5, 0.6); hl.position.set(-1, 0.8, 0); this.me.group.add(hl); hl.target.position.set(-20, 0, 0); this.me.group.add(hl.target);
    const hl2 = new THREE.SpotLight(0xfff1d0, 1.6, 40, 0.5, 0.6); hl2.position.set(-1, 0.8, 0); this.op.group.add(hl2); hl2.target.position.set(-20, 0, 0); this.op.group.add(hl2.target);
    // дым и огонь
    const smokeTex = canvasTex(64, 64, (g) => { const gr = g.createRadialGradient(32, 32, 2, 32, 32, 32); gr.addColorStop(0, 'rgba(230,232,236,0.9)'); gr.addColorStop(1, 'rgba(230,232,236,0)'); g.fillStyle = gr; g.fillRect(0, 0, 64, 64); });
    this.puffs = [];
    for (let i = 0; i < 70; i++) { const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: smokeTex, transparent: true, depthWrite: false, opacity: 0 })); sp.visible = false; s.add(sp); this.puffs.push({ sp, life: 0 }); }
    this.flames = this.me.tips.map(() => { const f = new THREE.Mesh(new THREE.ConeGeometry(0.06, 0.45, 12), new THREE.MeshBasicMaterial({ color: 0x7ab8ff, transparent: true, opacity: 0, blending: THREE.AdditiveBlending, depthWrite: false })); f.geometry.translate(0, 0.22, 0); f.rotation.z = -Math.PI / 2; this.me.group.add(f); return f; });
    this.me.tips.forEach((p, i) => this.flames[i].position.copy(p));
    this.camX = 0; this.shake = 0; this.t = 0; this.pIdx = 0;
  }
  // r: состояние гонки из ui (R)
  update(R, dt) {
    this.t += dt;
    const me = R.me, op = R.op;
    const place = (car, x, lane, L, pitch) => {
      car.group.position.set(-x + L / 2, 0, lane);
      car.group.rotation.z = (car.group.rotation.z * 0.9 + pitch * 0.1);
    };
    place(this.me, me.x, this.laneMe, this.meL, clamp(-(me.acc || 0) * 0.0035, -0.03, 0.03));
    place(this.op, op.x, this.laneOp, this.opL, op.launched && op.shiftT <= 0 ? -0.01 : 0);
    const spin = (car, x) => car.wheels.forEach(w => { const rr = w.position.y; w.userData.inner.rotation.z = x / rr; });
    spin(this.me, me.x + (me.wheelspin > 0.05 ? this.t * 25 : 0)); spin(this.op, op.x);
    // огни ёлки
    const lit = R.phase === 'tree' ? Math.floor(R.treeT / (R.lightT / 3.2)) : -1;
    this.lamps.forEach(l => {
      if (l.k < 3) l.m.material.color.setHex(lit > l.k && R.green == null ? 0xffb020 : 0x3a2a08);
      else l.m.material.color.setHex(R.green != null ? 0x3dff7a : 0x0b2a14);
    });
    // дым из-под колёс
    if (me.wheelspin > 0.05 || (R.phase === 'tree' && R.thr > 0.6 && Math.random() < 0.15)) {
      for (let k = 0; k < 2; k++) {
        const p = this.puffs[this.pIdx++ % this.puffs.length];
        const rearX = -me.x + this.meL / 2 - (CAR_PROFILES[R.c.body] || CAR_PROFILES.hatch).wb[1] + this.meL / 2;
        p.sp.position.set(-me.x + this.meL - 0.6 + (Math.random() - 0.5) * 0.4, 0.3, this.laneMe + (Math.random() < 0.5 ? -0.8 : 0.8));
        p.life = 1.6; p.v = V3(2 + Math.random() * 2, 0.6 + Math.random() * 0.6, (Math.random() - 0.5) * 1.5); p.sp.visible = true; p.sp.scale.set(0.8, 0.8, 1);
      }
    }
    this.puffs.forEach(p => { if (p.life <= 0) { p.sp.visible = false; return; } p.life -= dt; p.sp.position.addScaledVector(p.v, dt); const k = 1 + (1.6 - p.life) * 3; p.sp.scale.set(k, k, 1); p.sp.material.opacity = clamp(p.life / 1.6, 0, 1) * 0.55; });
    // пламя/закись
    this.flames.forEach(f => { const on = (me.shiftT > 0 && me.shiftT > me.st.shift - 0.08) || me.nitrous; f.material.opacity = on ? 0.9 : f.material.opacity * Math.pow(0.001, dt); f.material.color.setHex(me.nitrous ? 0x7ab8ff : 0xff8a2a); f.scale.set(1, 0.7 + Math.random() * 0.6, 1); });
    // камера: перед стартом — облёт сбоку спереди, затем погоня сзади-сбоку
    const v = me.v || 0;
    this.cam.fov = 50 + clamp(v * 0.28, 0, 22); this.cam.updateProjectionMatrix();
    const cx = -me.x + this.meL / 2;
    let pos, look;
    if (R.phase === 'stage' || R.phase === 'tree') {
      const a = clamp(R.t / 3.2, 0, 1);
      pos = V3(cx - 9 + a * 19, 1.5 + a * 1.1, this.laneMe + 8.5 - a * 3.5);
      look = V3(cx - 1, 0.7, (this.laneMe + this.laneOp) / 2 + 0.6);
    } else {
      const mode = R.camMode || 0, k = clamp(v / 70, 0, 1);
      if (mode === 0) {          // погоня: близко сзади, чуть сбоку; на скорости камера ниже и ближе к земле
        pos = V3(cx + 6.6 - k * 0.8, 1.8 - k * 0.3, this.laneMe + 0.15);
        look = V3(cx - 5, 0.8, this.laneMe - 0.6);
      } else if (mode === 1) {   // «телевизионная»: сбоку, проезд мимо
        pos = V3(cx + 2.5, 1.2, this.laneMe + 7.6);
        look = V3(cx - 1.5, 0.7, (this.laneMe + this.laneOp) / 2);
      } else {                   // с капота
        pos = V3(cx - this.meL * 0.15, 1.25, this.laneMe);
        look = V3(cx - 30, 0.9, this.laneMe * 0.8);
      }
    }
    if (!this.camPos) this.camPos = pos.clone();
    // по ходу движения камера держится жёстко (без отставания), по высоте и сбоку — плавно
    this.camPos.x = R.phase === 'run' || R.phase === 'done' ? pos.x : this.camPos.x + (pos.x - this.camPos.x) * Math.min(1, dt * 4);
    this.camPos.y += (pos.y - this.camPos.y) * Math.min(1, dt * 4); this.camPos.z += (pos.z - this.camPos.z) * Math.min(1, dt * 4);
    const sh = clamp(v / 60, 0, 1) * 0.04 + (me.wheelspin > 0.1 ? 0.03 : 0);
    this.cam.position.copy(this.camPos).add(V3((Math.random() - 0.5) * sh, (Math.random() - 0.5) * sh, 0));
    this.cam.lookAt(look);
  }
}
