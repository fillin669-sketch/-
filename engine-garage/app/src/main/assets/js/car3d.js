'use strict';
// ================= 3D-ЗАЕЗД (модели машин — в body3d.js) =================
function canvasTex(w, h, draw, rep) {
  const cv = document.createElement('canvas'); cv.width = w; cv.height = h; draw(cv.getContext('2d'), w, h);
  const t = new THREE.CanvasTexture(cv); t.encoding = THREE.sRGBEncoding; t.anisotropy = 4;
  if (rep) { t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(rep[0], rep[1]); }
  return t;
}
class Race3D {
  constructor(me, op, track) {
    const T = track || TRACKS.city;
    const TH = TRACK_THEMES[T.theme] || TRACK_THEMES.city;
    this.track = T; this.dist = T.dist;
    const s = this.scene = new THREE.Scene();
    // небо: градиент на большой сфере + туман того же цвета
    const skyTex = canvasTex(4, 256, (g, w, h) => { const gr = g.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, TH.sky[0]); gr.addColorStop(0.55, TH.sky[1]); gr.addColorStop(1, TH.sky[2]); g.fillStyle = gr; g.fillRect(0, 0, w, h); });
    const sky = new THREE.Mesh(new THREE.SphereGeometry(380, 24, 12), new THREE.MeshBasicMaterial({ map: skyTex, side: THREE.BackSide, fog: false, depthWrite: false }));
    s.add(sky); this.sky = sky;
    s.background = new THREE.Color(TH.sky[2]);
    s.fog = new THREE.Fog(TH.fog, TH.fogNear, TH.fogFar);
    s.environment = G3.env;
    s.add(new THREE.HemisphereLight(TH.hemi[0], TH.hemi[1], TH.hemi[2]));
    const sun = new THREE.DirectionalLight(TH.sun[0], TH.sun[1]); sun.position.set(TH.sun[2], TH.sun[3], TH.sun[4]); s.add(sun);
    if (TH.sunDisc) { const d = new THREE.Mesh(new THREE.CircleGeometry(14, 32), new THREE.MeshBasicMaterial({ color: TH.sunDisc, fog: false })); d.position.set(-330, TH.sunDiscY, 60); d.lookAt(0, 0, 0); s.add(d); }
    this.cam = new THREE.PerspectiveCamera(52, 1, 0.1, 800);
    this.orbit = { tick() { }, apply() { } };
    const DIST = T.dist, LEN = DIST + 320, X0 = 60;
    const mt = new THREE.Matrix4(), q = new THREE.Quaternion().setFromEuler(new THREE.Euler(-Math.PI / 2, 0, 0)), q0 = new THREE.Quaternion();
    const rnd = (() => { let sd = T.dist * 7 + T.theme.length * 31; return () => (sd = (sd * 9301 + 49297) % 233280) / 233280; })();
    // земля вокруг трассы
    const groundTex = canvasTex(128, 128, (g, w, h) => { g.fillStyle = TH.ground; g.fillRect(0, 0, w, h); for (let i = 0; i < 900; i++) { g.fillStyle = Math.random() < 0.5 ? 'rgba(0,0,0,0.12)' : 'rgba(255,255,255,0.07)'; g.fillRect(Math.random() * w, Math.random() * h, 2, 2); } }, [60, 40]);
    const ground = new THREE.Mesh(new THREE.PlaneGeometry(LEN + 600, 600), new THREE.MeshStandardMaterial({ map: groundTex, roughness: 0.95 }));
    ground.rotation.x = -Math.PI / 2; ground.position.set(X0 - LEN / 2, -0.02, 0); s.add(ground);
    // покрытие
    const asph = canvasTex(256, 256, (g, w, h) => { g.fillStyle = TH.road; g.fillRect(0, 0, w, h); for (let i = 0; i < 2600; i++) { const v = Math.random() < 0.5 ? 'rgba(0,0,0,0.18)' : 'rgba(255,255,255,0.06)'; g.fillStyle = v; g.fillRect(Math.random() * w, Math.random() * h, 1.5, 1.5); } if (T.theme === 'airfield') { g.fillStyle = 'rgba(0,0,0,0.25)'; g.fillRect(0, 0, w, 2); } g.fillStyle = 'rgba(0,0,0,0.22)'; g.fillRect(0, h * 0.42, w, h * 0.16); }, [LEN / 6, 3]);
    const roadW = T.theme === 'airfield' ? 30 : 14;
    const road = new THREE.Mesh(new THREE.PlaneGeometry(LEN, roadW), new THREE.MeshStandardMaterial({ map: asph, roughness: TH.roadRough, metalness: 0.05 }));
    road.rotation.x = -Math.PI / 2; road.position.set(X0 - LEN / 2, 0, 0); s.add(road);
    if (T.theme === 'winter') {   // укатанный снег по краям дороги
      const snowM = new THREE.MeshStandardMaterial({ color: 0xe8eef5, roughness: 0.9 });
      [-1, 1].forEach(sd => { const b = new THREE.Mesh(new THREE.BoxGeometry(LEN, 0.6, 3), snowM); b.position.set(X0 - LEN / 2, 0.15, sd * 8.5); b.scale.y = 0.6; s.add(b); });
    }
    // резиновые следы на старте
    const rub = canvasTex(256, 64, (g, w, h) => { const gr = g.createLinearGradient(0, 0, w, 0); gr.addColorStop(0, 'rgba(0,0,0,0.85)'); gr.addColorStop(1, 'rgba(0,0,0,0)'); g.fillStyle = gr; [10, 22, 42, 54].forEach(y => g.fillRect(0, y, w, 6)); });
    [-1.8, 1.8].forEach(z => { const m = new THREE.Mesh(new THREE.PlaneGeometry(60, 1.6), new THREE.MeshBasicMaterial({ map: rub, transparent: true, depthWrite: false })); m.rotation.x = -Math.PI / 2; m.position.set(-30, 0.005, z); m.rotation.z = Math.PI; s.add(m); });
    // разметка
    const lineM = new THREE.MeshBasicMaterial({ color: TH.line });
    [-3.9, 3.9].forEach(z => { const m = new THREE.Mesh(new THREE.PlaneGeometry(LEN, 0.15), lineM); m.rotation.x = -Math.PI / 2; m.position.set(X0 - LEN / 2, 0.006, z); s.add(m); });
    const nDash = Math.ceil(LEN / 6);
    const dash = new THREE.InstancedMesh(new THREE.PlaneGeometry(3, 0.12), lineM, nDash);
    for (let i = 0; i < nDash; i++) { mt.compose(V3(X0 - i * 6, 0.006, 0), q, V3(1, 1, 1)); dash.setMatrixAt(i, mt); } s.add(dash);
    // старт, финиш, арка
    const checker = canvasTex(64, 64, (g) => { for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) { g.fillStyle = (x + y) % 2 ? '#111' : '#f2f2f2'; g.fillRect(x * 8, y * 8, 8, 8); } }, [1, 8]);
    const fin = new THREE.Mesh(new THREE.PlaneGeometry(1.2, 7.8), new THREE.MeshBasicMaterial({ map: checker })); fin.rotation.x = -Math.PI / 2; fin.position.set(-DIST, 0.008, 0); s.add(fin);
    const st = new THREE.Mesh(new THREE.PlaneGeometry(0.25, 7.8), lineM); st.rotation.x = -Math.PI / 2; st.position.set(0, 0.008, 0); s.add(st);
    const archM = std(0x1d1f24, 0.6, 0.4);
    [-4.6, 4.6].forEach(z => { const p = new THREE.Mesh(new THREE.BoxGeometry(0.4, 6.2, 0.4), archM); p.position.set(-DIST, 3.1, z); s.add(p); });
    const sign = canvasTex(512, 96, (g, w, h) => { g.fillStyle = '#111317'; g.fillRect(0, 0, w, h); g.fillStyle = '#f2a93b'; g.font = 'bold 56px sans-serif'; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText('ФИНИШ · ' + DIST + ' м', w / 2, h / 2 + 3); });
    const sg = new THREE.Mesh(new THREE.BoxGeometry(0.3, 1.2, 9.6), [archM, archM, archM, archM, new THREE.MeshBasicMaterial({ map: sign }), new THREE.MeshBasicMaterial({ map: sign })]); sg.position.set(-DIST, 6, 0); s.add(sg);
    // таблички дистанции
    const step = DIST <= 201 ? 50 : DIST <= 402 ? 100 : DIST <= 804 ? 200 : 400;
    for (let m = step; m < DIST; m += step) {
      const t = canvasTex(128, 128, (g, w, h) => { g.fillStyle = '#f4f4f4'; g.fillRect(0, 0, w, h); g.fillStyle = '#111'; g.font = 'bold ' + (m >= 1000 ? 40 : 54) + 'px sans-serif'; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText(String(m), w / 2, h / 2); });
      [-6.2, 6.2].forEach(z => { const b = new THREE.Mesh(new THREE.PlaneGeometry(1.1, 1.1), new THREE.MeshBasicMaterial({ map: t, side: THREE.DoubleSide })); b.position.set(-m, 1.6, z); b.rotation.y = Math.PI / 2; s.add(b); const pole = new THREE.Mesh(new THREE.BoxGeometry(0.08, 1.1, 0.08), archM); pole.position.set(-m, 0.55, z); s.add(pole); });
    }
    // отбойник с дальней стороны
    if (TH.barrier) {
      const nb = Math.ceil(LEN / 4);
      const bar = new THREE.InstancedMesh(new THREE.BoxGeometry(3.9, 0.8, 0.5), std(0x9a9da3, 0.1, 0.8), nb);
      const stripeBar = new THREE.InstancedMesh(new THREE.BoxGeometry(1.9, 0.18, 0.52), std(TH.barrier, 0.1, 0.6), nb);
      for (let i = 0; i < nb; i++) { mt.compose(V3(X0 - i * 4, 0.4, -4.8), q0, V3(1, 1, 1)); bar.setMatrixAt(i, mt); mt.compose(V3(X0 - i * 4 - 1, 0.62, -4.8), q0, V3(1, 1, 1)); stripeBar.setMatrixAt(i, mt); }
      s.add(bar); s.add(stripeBar);
    }
    // ночные фонари
    if (TH.night) {
      const nP = Math.ceil(LEN / 24);
      const poles = new THREE.InstancedMesh(new THREE.CylinderGeometry(0.08, 0.1, 8, 8), archM, nP * 2);
      const heads = new THREE.InstancedMesh(new THREE.BoxGeometry(1.4, 0.15, 0.4), new THREE.MeshBasicMaterial({ color: 0xffe2a8 }), nP * 2);
      const pool = canvasTex(128, 128, (g, w, h) => { const gr = g.createRadialGradient(64, 64, 2, 64, 64, 64); gr.addColorStop(0, 'rgba(255,214,150,0.38)'); gr.addColorStop(1, 'rgba(255,214,150,0)'); g.fillStyle = gr; g.fillRect(0, 0, w, h); });
      const pools = new THREE.InstancedMesh(new THREE.PlaneGeometry(14, 14), new THREE.MeshBasicMaterial({ map: pool, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending }), nP * 2);
      let n = 0; for (let i = 0; i < nP; i++) [-8.5, 8.5].forEach(z => { mt.compose(V3(X0 - i * 24, 4, z), q0, V3(1, 1, 1)); poles.setMatrixAt(n, mt); mt.compose(V3(X0 - i * 24, 8, z * 0.9), q0, V3(1, 1, 1)); heads.setMatrixAt(n, mt); mt.compose(V3(X0 - i * 24, 0.01, z * 0.47), q, V3(1, 1, 1)); pools.setMatrixAt(n, mt); n++; });
      s.add(poles); s.add(heads); s.add(pools);
    }
    // трибуна со зрителями у старта
    if (TH.stand) {
      const stand = new THREE.Mesh(new THREE.BoxGeometry(60, 0.4, 8), archM); stand.position.set(-10, 0.2, -11); s.add(stand);
      for (let r = 0; r < 5; r++) { const stp = new THREE.Mesh(new THREE.BoxGeometry(60, 0.5, 1.4), std(0x2a2d33, 0.2, 0.8)); stp.position.set(-10, 0.5 + r * 0.5, -8.2 - r * 1.4); s.add(stp); }
      const nC = G3.quality === 'low' ? 80 : 260;
      const crowd = new THREE.InstancedMesh(new THREE.BoxGeometry(0.42, 0.75, 0.32), new THREE.MeshStandardMaterial({ roughness: 0.8 }), nC);
      const cc = new THREE.Color();
      for (let i = 0; i < nC; i++) { const r = i % 5; mt.compose(V3(-38 + rnd() * 56, 1.1 + r * 0.5, -8.2 - r * 1.4), q0, V3(1, 0.9 + rnd() * 0.3, 1)); crowd.setMatrixAt(i, mt); cc.setHSL(rnd(), 0.5, 0.3 + rnd() * 0.3); crowd.setColorAt(i, cc); }
      s.add(crowd);
    }
    const lowQ = G3.quality === 'low', K = lowQ ? 0.45 : G3.quality === 'mid' ? 0.7 : 1;
    const scatter = (geo, mat, count, place) => { const im = new THREE.InstancedMesh(geo, mat, count); for (let i = 0; i < count; i++) { place(i); im.setMatrixAt(i, mt); } s.add(im); return im; };
    const sideZ = (near, far) => (rnd() < 0.5 ? -1 : 1) * (near + rnd() * (far - near));
    // ---------- пейзаж по теме ----------
    if (T.theme === 'city' || T.theme === 'port') {
      const win = canvasTex(64, 128, (g, w, h) => { g.fillStyle = '#0d1018'; g.fillRect(0, 0, w, h); for (let y = 4; y < h; y += 8) for (let x = 3; x < w; x += 8) if (Math.random() < 0.38) { g.fillStyle = Math.random() < 0.8 ? '#ffd27a' : '#9cc8ff'; g.fillRect(x, y, 4, 4); } });
      const bm = new THREE.MeshStandardMaterial({ color: 0x222633, emissive: 0xffffff, emissiveMap: win, emissiveIntensity: 0.9, map: win });
      const nB = Math.round((T.theme === 'port' ? 30 : 80) * K * Math.max(1, LEN / 700));
      scatter(new THREE.BoxGeometry(1, 1, 1), bm, nB, () => { const h = 15 + rnd() * 55, w = 8 + rnd() * 14; mt.compose(V3(X0 + 20 - rnd() * LEN, h / 2, (rnd() < 0.5 ? -1 : 1) * (T.theme === 'port' ? 120 + rnd() * 60 : 45 + rnd() * 60)), q0, V3(w, h, 8 + rnd() * 10)); });
    }
    if (T.theme === 'port') {
      // штабели контейнеров и портовые краны
      const cont = new THREE.InstancedMesh(new THREE.BoxGeometry(6, 2.6, 2.45), new THREE.MeshStandardMaterial({ roughness: 0.6, metalness: 0.3 }), Math.round(260 * K));
      const cc = new THREE.Color(), cols = [0xb53a2a, 0x2f6cb3, 0xd8a12a, 0x3a8a52, 0x7d8590, 0xe0e2e6, 0x8a3fa8];
      for (let i = 0; i < cont.count; i++) { const row = Math.floor(rnd() * 3); mt.compose(V3(X0 - rnd() * LEN, 1.3 + row * 2.6, sideZ(14, 60)), new THREE.Quaternion().setFromEuler(new THREE.Euler(0, rnd() < 0.8 ? 0 : Math.PI / 2, 0)), V3(1, 1, 1)); cont.setMatrixAt(i, mt); cc.setHex(cols[Math.floor(rnd() * cols.length)]); cont.setColorAt(i, cc); }
      s.add(cont);
      const craneM = std(0xd8a12a, 0.4, 0.5);
      for (let i = 0; i < 4; i++) { const g = new THREE.Group(); const x = X0 - 60 - i * LEN / 4, z = 80; [-6, 6].forEach(dz => { const l = new THREE.Mesh(new THREE.BoxGeometry(1, 40, 1), craneM); l.position.set(0, 20, dz); g.add(l); }); const boom = new THREE.Mesh(new THREE.BoxGeometry(2, 2, 70), craneM); boom.position.set(0, 40, -18); g.add(boom); g.position.set(x, 0, z); s.add(g); }
    }
    if (T.theme === 'airfield') {
      // ангары, вышка, огни полосы
      const hM = std(0x8c939b, 0.6, 0.45);
      for (let i = 0; i < 6; i++) { const hg = new THREE.Mesh(new THREE.CylinderGeometry(14, 14, 40, 20, 1, false, 0, Math.PI), hM); hg.rotation.z = Math.PI / 2; hg.rotation.y = Math.PI / 2; hg.position.set(X0 - 80 - i * LEN / 6, 0, -70 - (i % 2) * 20); s.add(hg); }
      const tw = new THREE.Mesh(new THREE.CylinderGeometry(2, 2.6, 24, 12), std(0xd9dde2, 0.2, 0.6)); tw.position.set(-120, 12, 60); s.add(tw);
      const cab = new THREE.Mesh(new THREE.CylinderGeometry(5, 4, 4, 12), std(0x203040, 0.8, 0.1)); cab.position.set(-120, 26, 60); s.add(cab);
      const nL = Math.ceil(LEN / 15);
      scatter(new THREE.SphereGeometry(0.18, 8, 6), new THREE.MeshBasicMaterial({ color: 0xffe9a8 }), nL * 2, (i) => { mt.compose(V3(X0 - Math.floor(i / 2) * 15, 0.15, i % 2 ? 15.5 : -15.5), q0, V3(1, 1, 1)); });
      // ветроуказатель
      const ws = new THREE.Mesh(new THREE.ConeGeometry(0.6, 3, 12, 1, true), new THREE.MeshStandardMaterial({ color: 0xff6a1a, side: THREE.DoubleSide })); ws.rotation.z = Math.PI / 2; ws.position.set(-40, 6, 30); s.add(ws);
      const wsp = new THREE.Mesh(new THREE.CylinderGeometry(0.08, 0.08, 6, 6), archM); wsp.position.set(-38.5, 3, 30); s.add(wsp);
      // самолёт на стоянке
      const pM = std(0xe8ebef, 0.3, 0.4), pl = new THREE.Group();
      const fus = new THREE.Mesh(new THREE.CylinderGeometry(1.4, 1.2, 26, 16), pM); fus.rotation.z = Math.PI / 2; pl.add(fus);
      const wing = new THREE.Mesh(new THREE.BoxGeometry(5, 0.3, 30), pM); pl.add(wing);
      const tail = new THREE.Mesh(new THREE.BoxGeometry(3, 6, 0.3), pM); tail.position.set(12, 3, 0); pl.add(tail);
      pl.position.set(-70, 2.2, -45); s.add(pl);
    }
    if (T.theme === 'desert') {
      const rockM = std(0xb06a3a, 0, 0.95), cactM = std(0x4f7a3a, 0, 0.8);
      // столовые горы на горизонте
      for (let i = 0; i < 14; i++) { const w = 30 + rnd() * 60, h = 20 + rnd() * 40; const m = new THREE.Mesh(new THREE.CylinderGeometry(w * 0.7, w, h, 7), rockM); m.position.set(X0 - rnd() * LEN, h / 2, (rnd() < 0.5 ? -1 : 1) * (160 + rnd() * 120)); s.add(m); }
      scatter(new THREE.DodecahedronGeometry(1, 0), rockM, Math.round(120 * K), () => { const k = 0.3 + rnd() * 1.6; mt.compose(V3(X0 - rnd() * LEN, k * 0.3, sideZ(10, 70)), new THREE.Quaternion().setFromEuler(new THREE.Euler(rnd() * 3, rnd() * 3, 0)), V3(k, k * 0.7, k)); });
      const cg = new THREE.CylinderGeometry(0.25, 0.3, 3, 8); cg.translate(0, 1.5, 0);
      scatter(cg, cactM, Math.round(90 * K), () => { const k = 0.7 + rnd() * 0.8; mt.compose(V3(X0 - rnd() * LEN, 0, sideZ(9, 60)), q0, V3(k, k, k)); });
    }
    if (T.theme === 'mountain' || T.theme === 'winter') {
      const snow = T.theme === 'winter';
      // горы на горизонте
      const mM = std(snow ? 0xdfe6ee : 0x5d6b5a, 0, 0.95), capM = std(0xf4f7fa, 0, 0.8);
      for (let i = 0; i < 18; i++) { const h = 60 + rnd() * 110, r = h * (0.8 + rnd() * 0.5); const m = new THREE.Mesh(new THREE.ConeGeometry(r, h, 7), mM); const x = X0 - rnd() * LEN * 1.2, z = (rnd() < 0.5 ? -1 : 1) * (190 + rnd() * 140); m.position.set(x, h / 2 - 2, z); s.add(m); const cap = new THREE.Mesh(new THREE.ConeGeometry(r * 0.32, h * 0.32, 7), capM); cap.position.set(x, h - h * 0.16 - 2, z); s.add(cap); }
      // ёлки
      const nT = Math.round(260 * K);
      const trunk = new THREE.CylinderGeometry(0.15, 0.2, 1.2, 6); trunk.translate(0, 0.6, 0);
      const crown = new THREE.ConeGeometry(1.4, 4.5, 8); crown.translate(0, 3.2, 0);
      const pos = [];
      for (let i = 0; i < nT; i++) pos.push([X0 - rnd() * LEN, sideZ(11, 90), 0.7 + rnd() * 0.9]);
      const place = (i) => { const [x, z, k] = pos[i]; mt.compose(V3(x, 0, z), q0, V3(k, k, k)); };
      scatter(trunk, std(0x4a3426, 0, 0.9), nT, place);
      scatter(crown, std(snow ? 0x2f4a3c : 0x24452c, 0, 0.85), nT, place);
      if (snow) { const sc = new THREE.ConeGeometry(1.0, 1.6, 8); sc.translate(0, 4.6, 0); scatter(sc, std(0xf2f6fa, 0, 0.8), nT, place); }
    }
    if (T.theme === 'winter') {
      // снегопад
      const nS = lowQ ? 300 : 900, sp = new Float32Array(nS * 3);
      for (let i = 0; i < nS; i++) { sp[i * 3] = (Math.random() - 0.5) * 80; sp[i * 3 + 1] = Math.random() * 20; sp[i * 3 + 2] = (Math.random() - 0.5) * 40; }
      const sg2 = new THREE.BufferGeometry(); sg2.setAttribute('position', new THREE.BufferAttribute(sp, 3));
      this.snow = new THREE.Points(sg2, new THREE.PointsMaterial({ color: 0xffffff, size: 0.12, transparent: true, opacity: 0.85, depthWrite: false }));
      s.add(this.snow);
    }
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
    const night = !!(TRACK_THEMES[this.track.theme] || {}).night;
    const hl = new THREE.SpotLight(0xfff1d0, night ? 2.2 : 0, 45, 0.5, 0.6); hl.position.set(-1, 0.8, 0); this.me.group.add(hl); hl.target.position.set(-20, 0, 0); this.me.group.add(hl.target);
    const hl2 = new THREE.SpotLight(0xfff1d0, night ? 1.6 : 0, 40, 0.5, 0.6); hl2.position.set(-1, 0.8, 0); this.op.group.add(hl2); hl2.target.position.set(-20, 0, 0); this.op.group.add(hl2.target);
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
      if (mode === 3) {          // вид сбоку: камера перпендикулярно трассе, обе машины в кадре
        const ox = -op.x + this.opL / 2, mid = (cx + ox) / 2, gap = Math.abs(cx - ox);
        pos = V3(mid, 1.4, this.laneMe + 9 + Math.min(14, gap * 0.9));
        look = V3(mid, 0.8, 0);
      } else if (mode === 0) {          // погоня: близко сзади, чуть сбоку; на скорости камера ниже и ближе к земле
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
    // небо и снег всегда вокруг камеры
    this.sky.position.set(this.cam.position.x, 0, 0);
    if (this.snow) {
      const a = this.snow.geometry.attributes.position, arr = a.array;
      for (let i = 0; i < arr.length; i += 3) { arr[i + 1] -= dt * 2.2; arr[i] += dt * (v * 0.9 + 1.5); if (arr[i + 1] < 0) arr[i + 1] += 20; if (arr[i] > 40) arr[i] -= 80; }
      a.needsUpdate = true; this.snow.position.set(this.cam.position.x, 0, this.cam.position.z * 0.5);
    }
  }
}
