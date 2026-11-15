'use strict';
// ================= КУЗОВ «ЛОФТОМ», ВИДИМЫЕ ДОРАБОТКИ И 3D-ЗАЕЗД =================
// Кузов строится как гладкая поверхность из поперечных сечений: плечевая линия, завал боковин,
// расширения над колёсами и вырезы арок. Фары, фонари, решётка, карбон и полосы — «наклейки»,
// которые повторяют поверхность кузова.

function lookupFn(list, n) {
  const pts = profilePts(list, n || 160);
  const xs = [], ys = [];
  pts.forEach(p => { if (!xs.length || p.x > xs[xs.length - 1] + 1e-4) { xs.push(p.x); ys.push(p.y); } else ys[ys.length - 1] = Math.max(ys[ys.length - 1], p.y); });
  return (x) => {
    if (x <= xs[0]) return ys[0];
    if (x >= xs[xs.length - 1]) return ys[ys.length - 1];
    let lo = 0, hi = xs.length - 1;
    while (hi - lo > 1) { const m = (lo + hi) >> 1; if (xs[m] <= x) lo = m; else hi = m; }
    const t = (x - xs[lo]) / (xs[hi] - xs[lo]);
    return ys[lo] + (ys[hi] - ys[lo]) * t;
  };
}
function chaikin(pts, it) {
  for (let k = 0; k < it; k++) {
    const out = [pts[0]];
    for (let i = 0; i < pts.length - 1; i++) {
      const a = pts[i], b = pts[i + 1];
      out.push([a[0] * 0.75 + b[0] * 0.25, a[1] * 0.75 + b[1] * 0.25]);
      out.push([a[0] * 0.25 + b[0] * 0.75, a[1] * 0.25 + b[1] * 0.75]);
    }
    out.push(pts[pts.length - 1]);
    pts = out;
  }
  return pts;
}

// Описание формы кузова для машины c
function bodyForm(P, c, rW) {
  const lift = (c.rideHeight || 0) / 1000 + (rW - 0.3) * 0.7;
  const wide = c.kit === 'wide';
  const L = P.L, W = P.W + (wide ? 0.14 : 0);
  const topF = lookupFn(P.top.filter((p, i) => i === 0 || p[0] > P.top[i - 1][0] - 1e-6));
  const cabF = lookupFn(P.cab);
  const cab0 = P.cab[0][0], cab1 = P.cab[P.cab.length - 1][0];
  const roofF = P.cab[Math.min(2, P.cab.length - 1)][0], roofR = P.cab[Math.max(0, P.cab.length - 3)][0];
  const archR = rW + 0.04;
  const top = (x) => topF(x) + lift;
  const roof = (x) => (x > cab0 && x < cab1) ? Math.max(top(x), cabF(x) + lift) : top(x);
  const cabin = (x) => x > cab0 + 0.01 && x < cab1 - 0.01 && roof(x) > top(x) + 0.03;
  const sill = P.sill + lift;
  const bot = (x) => {
    let b = sill;
    const d = Math.min(x, L - x);
    if (d < 0.35) b += 0.1 * Math.pow(1 - d / 0.35, 2);
    if (!P.dragster) for (const ax of P.wb) { const dx = x - ax; if (Math.abs(dx) < archR) b = Math.max(b, rW + Math.sqrt(archR * archR - dx * dx)); }
    return Math.min(b, top(x) - 0.06);
  };
  const halfW = (x) => {
    const d = Math.min(x, L - x);
    let p = d < 0.55 ? 0.8 + 0.2 * Math.sqrt(1 - Math.pow(1 - d / 0.55, 2)) : 1;
    if (P.dragster) p = d < 2.2 ? 0.35 + 0.65 * Math.sqrt(1 - Math.pow(1 - d / 2.2, 2)) : 1;
    return W / 2 * p;
  };
  const bulge = (x) => { let b = 0; if (!P.dragster) for (const ax of P.wb) b += Math.exp(-Math.pow((x - ax) / 0.5, 2)); return b * (wide ? 0.07 : 0.025); };
  return { L, W, lift, top, roof, cabin, bot, halfW, bulge, cab0, cab1, roofF, roofR, sill, archR, rW };
}

// Половина поперечного сечения: от нижнего центра до центра крыши, точки [z, y]
function sectionHalf(F, x) {
  const top = F.top(x), bot = F.bot(x), hw = F.halfW(x), roof = F.roof(x), cab = F.cabin(x);
  const span = Math.max(0.05, top - bot);
  const bz = hw * (1 + F.bulge(x));
  const pts = [[0, bot], [hw * 0.82, bot], [hw * 0.97, bot + Math.min(0.06, span * 0.2)], [bz, bot + span * 0.45], [bz * 0.99, top - span * 0.18], [hw * 0.955, top - 0.02], [hw * 0.9, top]];
  if (cab) {
    const gw = hw * 0.84, rw = hw * 0.64;
    pts.push([gw, top + 0.015], [gw * 0.93 + rw * 0.07, top + (roof - top) * 0.55], [rw * 1.05, roof - 0.045], [rw * 0.8, roof - 0.01], [0, roof]);
  } else {
    pts.push([hw * 0.78, top + 0.006], [hw * 0.55, top + 0.014], [hw * 0.32, top + 0.019], [hw * 0.15, top + 0.021], [0, top + 0.022]);
  }
  return chaikin(pts, 2);
}

// Построить сетку кузова. Возвращает geometry с группами: 0 краска, 1 стекло, 2 тёмная отделка
function buildBodyGeometry(P, c, F, paintRGB) {
  const N = 150;
  const xsArr = [];
  for (let i = 0; i <= N; i++) { const t = i / N; xsArr.push(F.L * (0.5 - 0.5 * Math.cos(t * Math.PI)) * 0.0 + F.L * t); }
  const half0 = sectionHalf(F, 0.5);
  const K = half0.length;
  const ringN = K * 2 - 2;
  const pos = [], col = [], cls = [], meta = [];
  const seamXs = P.dragster ? [] : [F.cab0 + 0.05, (F.roofF + F.roofR) / 2 + 0.04, Math.min(F.roofR - 0.1, F.cab1 - 0.3)];
  xsArr.forEach((x) => {
    let half = sectionHalf(F, x);
    // скругление носа и кормы
    const d = Math.min(x, F.L - x), RN = 0.14;
    let s = 1;
    if (d < RN) s = 0.55 + 0.45 * Math.sqrt(1 - Math.pow(1 - d / RN, 2));
    const midY = (F.bot(x) + F.top(x)) / 2;
    half = half.map(([z, y]) => [z * s, midY + (y - midY) * (0.75 + 0.25 * s)]);
    const ring = [];
    for (let k = K - 1; k >= 1; k--) ring.push([-half[k][0], half[k][1]]);
    for (let k = 0; k < K - 1; k++) ring.push([half[k][0], half[k][1]]);
    const gw = F.halfW(x) * 0.84;
    const top = F.top(x), roof = F.roof(x);
    ring.forEach(([z, y]) => {
      pos.push(x - F.L / 2, y, z);
      // классификация
      let k = 0;
      const az = Math.abs(z);
      if (F.cabin(x) && y > top + 0.012 && y < roof - 0.032) {
        let glass = true;
        if (x < F.roofF - 0.02) glass = az < gw * 0.8;                       // лобовое: стойки по краям
        else if (x > F.roofR + 0.02) glass = az < gw * (c.body === 'hatch' || c.body === 'wagon' ? 0.7 : 0.5);   // заднее стекло
        else { const bx = (F.roofF + F.roofR) / 2 + 0.04; if (Math.abs(x - bx) < 0.035) k = 2; if (x > F.roofR - (c.body === 'hatch' ? 0.06 : 0.16) && az > gw * 0.7) glass = false; }
        if (glass && k === 0) k = 1;
      }
      if (y < F.bot(x) + 0.008 && az < F.halfW(x) * 0.84) k = 2; // днище
      cls.push(k);
      // цвет краски с лёгкой тенью у порогов и швами дверей
      let sh = 1;
      if (k === 0 && !P.dragster) {
        for (const sx of seamXs) if (Math.abs(x - sx) < 0.006 && y < top - 0.01 && y > F.bot(x) + 0.03 && az > F.halfW(x) * 0.8) sh = 0.25;
        if (y < F.sill + 0.12 && az > F.halfW(x) * 0.8) sh *= 0.82;
      }
      col.push(paintRGB[0] * sh, paintRGB[1] * sh, paintRGB[2] * sh);
      meta.push(x);
    });
  });
  const idx = [[], [], []];
  const R = ringN;
  for (let i = 0; i < N; i++) for (let j = 0; j < R - 1; j++) {
    const a = i * R + j, b = a + 1, cc = a + R, dd = cc + 1;
    const tri = (p, q, r) => { const ks = [cls[p], cls[q], cls[r]]; const g = ks.includes(2) ? 2 : (ks[0] === 1 && ks[1] === 1 && ks[2] === 1) ? 1 : 0; idx[g].push(p, q, r); };
    tri(a, cc, b); tri(b, cc, dd);
  }
  // торцы
  const capStart = pos.length / 3; pos.push(-F.L / 2, (F.bot(0) + F.top(0)) / 2, 0); col.push(...paintRGB); cls.push(0);
  const capEnd = pos.length / 3; pos.push(F.L / 2, (F.bot(F.L) + F.top(F.L)) / 2, 0); col.push(...paintRGB); cls.push(0);
  for (let j = 0; j < R - 1; j++) { idx[0].push(capStart, j + 1, j); const o = N * R; idx[0].push(capEnd, o + j, o + j + 1); }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute('color', new THREE.Float32BufferAttribute(col, 3));
  const all = idx[0].concat(idx[1], idx[2]);
  g.setIndex(all);
  g.addGroup(0, idx[0].length, 0); g.addGroup(idx[0].length, idx[1].length, 1); g.addGroup(idx[0].length + idx[1].length, idx[2].length, 2);
  g.computeVertexNormals();
  return g;
}

// «Наклейка» по поверхности: копирует треугольники, у которых все вершины подходят под условие
function surfaceDecal(geo, F, pred, mat, offset) {
  const p = geo.attributes.position, n = geo.attributes.normal, ix = geo.index.array;
  const out = [], nor = [];
  for (let t = 0; t < ix.length; t += 3) {
    let ok = true;
    for (let k = 0; k < 3 && ok; k++) { const v = ix[t + k]; ok = pred(p.getX(v) + F.L / 2, p.getY(v), p.getZ(v), n.getX(v), n.getY(v), n.getZ(v)); }
    if (!ok) continue;
    for (let k = 0; k < 3; k++) { const v = ix[t + k]; const o = offset || 0.004; out.push(p.getX(v) + n.getX(v) * o, p.getY(v) + n.getY(v) * o, p.getZ(v) + n.getZ(v) * o); nor.push(n.getX(v), n.getY(v), n.getZ(v)); }
  }
  if (!out.length) return null;
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(out, 3));
  g.setAttribute('normal', new THREE.Float32BufferAttribute(nor, 3));
  // UV по проекции сверху/сбоку для карбона
  const uv = []; for (let i = 0; i < out.length; i += 3) uv.push(out[i] * 2, out[i + 2] * 2 + out[i + 1]);
  g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
  return new THREE.Mesh(g, mat);
}

let CARBON_TEX = null;
function carbonShared() { if (!CARBON_TEX) { CARBON_TEX = carbonTex(); CARBON_TEX.repeat.set(3, 3); } return CARBON_TEX; }

// Новый строитель машины
Car3D.prototype.build = function (c, stats, eng) {
  const P = CAR_PROFILES[c.body] || CAR_PROFILES.hatch;
  const root = new THREE.Group();
  const rW = stats ? stats.rWheel : 0.3;
  const F = bodyForm(P, c, rW);
  const L = F.L, W = F.W, X = (x) => x - L / 2;
  const col = new THREE.Color(c.color || '#c8312b');
  col.convertSRGBToLinear();
  const fin = PAINT_FIN[c.finish || 'gloss'] || PAINT_FIN.gloss;
  const paint = new THREE.MeshPhysicalMaterial({ color: 0xffffff, vertexColors: true, metalness: fin[0], roughness: fin[1], clearcoat: fin[2], clearcoatRoughness: 0.04, envMapIntensity: 1.25 });
  if (c.finish === 'pearl') { paint.sheen = 1; paint.sheenColor = new THREE.Color(0xffffff); paint.sheenRoughness = 0.35; }
  const paintSolid = new THREE.MeshPhysicalMaterial({ color: new THREE.Color(c.color || '#c8312b'), metalness: fin[0], roughness: fin[1], clearcoat: fin[2], clearcoatRoughness: 0.04 });
  const tint = { none: 0.5, light: 0.75, dark: 0.9, limo: 0.97 }[c.tint || 'light'];
  const glass = new THREE.MeshPhysicalMaterial({ color: 0x04070b, metalness: 0.5, roughness: 0.02, clearcoat: 1, transparent: true, opacity: tint, envMapIntensity: 1.5, side: THREE.DoubleSide });
  const trim = std(0x0d0e10, 0.3, 0.55);
  const black = std(0x111215, 0.3, 0.45), chrome = std(0xffffff, 1, 0.05);
  const carbon = std(0x2a2c30, 0.5, 0.18, { map: carbonShared() });
  const add = (geo, mat, pos, rot, parent) => { const m = new THREE.Mesh(geo, mat); if (pos) m.position.copy(pos); if (rot) m.rotation.set(rot[0] || 0, rot[1] || 0, rot[2] || 0); (parent || root).add(m); return m; };
  const decal = (pred, mat, off) => { const m = surfaceDecal(bodyGeo, F, pred, mat, off); if (m) root.add(m); return m; };

  // ---- кузов ----
  const bodyGeo = buildBodyGeometry(P, c, F, [col.r, col.g, col.b]);
  const body = new THREE.Mesh(bodyGeo, [paint, glass, trim]);
  root.add(body);
  const top = F.top, hw = F.halfW;
  const red = c.reduction || 0;

  // ---- фары, фонари, решётка (по поверхности) ----
  const led = c.lights === 'led';
  const lampM = new THREE.MeshStandardMaterial({ color: 0xffffff, emissive: led ? 0xd8e8ff : 0xfff0c8, emissiveIntensity: led ? 2.4 : 1.5, roughness: 0.05, metalness: 0.3 });
  const lampHouse = std(0x0a0b0d, 0.9, 0.08);
  const tailM = new THREE.MeshStandardMaterial({ color: 0x400404, emissive: 0xff1d14, emissiveIntensity: 1.8, roughness: 0.15 });
  const ly = P.lights[0] + F.lift, lyT = P.lights[1] + F.lift;
  if (!P.dragster) {
    // корпус фары, линза и (для LED) световая полоса
    decal((x, y, z, nx) => x < 0.42 && Math.abs(y - ly) < 0.065 && Math.abs(z) > hw(x) * 0.42 && Math.abs(z) < hw(x) * 0.93 && nx < 0.35, lampHouse, 0.003);
    decal((x, y, z, nx) => x < 0.4 && Math.abs(y - ly - 0.005) < (led ? 0.018 : 0.04) && Math.abs(z) > hw(x) * (led ? 0.45 : 0.55) && Math.abs(z) < hw(x) * 0.88 && nx < 0.35, lampM, 0.006);
    // решётка и нижний воздухозаборник
    const grilleM = std(0x08090a, 0.6, 0.35, { map: stripeTex('#202226', '#030304', 10, false) });
    decal((x, y, z, nx) => x < 0.2 && nx < -0.3 && Math.abs(y - (ly - 0.02)) < 0.05 && Math.abs(z) < hw(x) * 0.4, grilleM, 0.004);
    decal((x, y, z, nx) => x < 0.22 && nx < -0.25 && y > F.sill + 0.06 && y < F.sill + 0.17 && Math.abs(z) < hw(x) * 0.7, std(0x050506, 0.4, 0.5), 0.004);
    // задние фонари
    decal((x, y, z, nx) => x > L - 0.32 && Math.abs(y - lyT) < 0.045 && Math.abs(z) > hw(x) * 0.48 && Math.abs(z) < hw(x) * 0.95 && nx > 0.2, tailM, 0.004);
    if (led || c.body === 'coupe' || c.body === 'super') decal((x, y, z, nx) => x > L - 0.2 && Math.abs(y - lyT - 0.035) < 0.01 && nx > 0.5, tailM, 0.004);
    // номерной знак
    add(new THREE.BoxGeometry(0.01, 0.11, 0.5), std(0xeeeeee, 0, 0.5), V3(X(L) + 0.006, F.sill + 0.25, 0));
    add(new THREE.BoxGeometry(0.012, 0.1, 0.5), std(0xeeeeee, 0, 0.5), V3(X(0) - 0.01, F.sill + 0.16, 0));
  }
  // ---- гоночные полосы ----
  if (c.stripe) decal((x, y, z, nx, ny) => ny > 0.2 && Math.abs(Math.abs(z) - 0.16) < 0.07 && y > F.sill + 0.25, new THREE.MeshPhysicalMaterial({ color: 0xf2f3f5, roughness: 0.3, clearcoat: 1 }), 0.002);
  // ---- карбон: капот, крыша, багажник (облегчение) ----
  const hoodEnd = F.cab0 - 0.02;
  const carbonHood = c.hood === 'carbon' || red >= 3;
  if (carbonHood && !P.dragster) decal((x, y, z, nx, ny) => x > 0.25 && x < hoodEnd && ny > 0.55 && Math.abs(z) < hw(x) * 0.8, carbon, 0.003);
  if ((c.roof === 'carbon' || red >= 3) && !P.open && !P.dragster) decal((x, y, z, nx, ny) => x > F.roofF && x < F.roofR && y > F.roof(x) - 0.03 && ny > 0.6, carbon, 0.003);
  if (red >= 2 && !P.dragster && !P.bed) decal((x, y, z, nx, ny) => x > F.cab1 + 0.05 && x < L - 0.1 && ny > 0.5 && Math.abs(z) < hw(x) * 0.8, carbon, 0.003);
  // ---- зеркала и ручки ----
  if (!P.dragster) [1, -1].forEach(s => {
    const mx = X(F.cab0 + 0.22), my = top(F.cab0 + 0.22) + 0.07, mz = s * (hw(F.cab0 + 0.22) * 0.95);
    add(new THREE.BoxGeometry(0.08, 0.025, 0.08), black, V3(mx + 0.02, my - 0.03, mz - s * 0.03));
    const m = add(new THREE.SphereGeometry(0.07, 16, 10), paintSolid, V3(mx, my, mz + s * 0.03)); m.scale.set(1.1, 0.68, 0.55);
    const hx = (F.roofF + F.roofR) / 2 - 0.25;
    add(new THREE.BoxGeometry(0.15, 0.022, 0.02), chrome, V3(X(hx), top(hx) - 0.08, s * (hw(hx) * (1 + F.bulge(hx)) * 0.985)));
  });
  // ---- обвес ----
  if ((c.kit === 'sport' || c.kit === 'wide') && !P.dragster) {
    [1, -1].forEach(s => { const x0 = P.wb[0] + F.archR + 0.04, x1 = P.wb[1] - F.archR - 0.04; add(new THREE.BoxGeometry(x1 - x0, 0.07, 0.05), carbon, V3(X((x0 + x1) / 2), F.sill + 0.0, s * (hw((x0 + x1) / 2) * (1 + F.bulge((x0 + x1) / 2)) - 0.005))); });
    add(new THREE.BoxGeometry(0.3, 0.02, W * 0.85), carbon, V3(X(0.1), F.sill - 0.005, 0));
  }
  if (c.kit === 'wide' && !P.dragster) P.wb.forEach(ax => [1, -1].forEach(s => {
    const t = add(new THREE.TorusGeometry(F.archR + 0.015, 0.035, 8, 28, Math.PI), black, V3(X(ax), rW, s * (hw(ax) * (1 + F.bulge(ax)) - 0.01)));
    t.scale.set(1, 1, 1.4);
  }));
  // ---- капот: воздухозаборник, «горб» под большой мотор, компрессор сквозь капот ----
  const hx = (0.35 + hoodEnd) / 2;
  const bigEngine = eng && (eng.cyl >= 8 || displacement(eng) > 4.2);
  const roots = eng && (eng.induction === 'sc_roots' || eng.induction === 'sc_screw' || eng.induction === 'twincharge');
  if (!P.dragster && P.wb[0] < hoodEnd) {
    const hy = top(hx);
    const slope = Math.atan2(top(hoodEnd) - top(0.4), hoodEnd - 0.4);
    if (bigEngine && !roots) { const b = add(new THREE.SphereGeometry(1, 24, 12, 0, Math.PI * 2, 0, Math.PI / 2), carbonHood ? carbon : paintSolid, V3(X(hx), hy - 0.005, 0), [0, 0, slope]); b.scale.set((hoodEnd - 0.4) * 0.42, 0.06, hw(hx) * 0.32); }
    if (c.hood === 'scoop' || (eng && hasTurbo(eng) && c.hood !== 'stock')) {
      const sc = add(new THREE.BoxGeometry(0.42, 0.07, W * 0.26), carbonHood ? carbon : paintSolid, V3(X(hx + 0.15), hy + 0.03, 0), [0, 0, slope]);
      add(new THREE.BoxGeometry(0.02, 0.05, W * 0.22), trim, V3(X(hx - 0.06), hy + 0.035, 0), [0, 0, slope]);
    }
    if (eng && hasTurbo(eng)) [1, -1].forEach(s => decal((x, y, z, nx, ny) => x > hx + 0.05 && x < hx + 0.35 && ny > 0.6 && Math.abs(Math.abs(z) - hw(x) * 0.5) < 0.07 && Math.floor(x * 40) % 2 === 0, trim, 0.004));
    if (roots) {
      // компрессор торчит из капота
      const by = hy + 0.06;
      add(new THREE.BoxGeometry(0.42, 0.13, 0.32), std(0xc9ced5, 1, 0.2), V3(X(hx - 0.05), by, 0), [0, 0, slope]);
      for (let k = -3; k <= 3; k++) add(new THREE.BoxGeometry(0.4, 0.008, 0.005), std(0x9aa1aa, 1, 0.3), V3(X(hx - 0.05), by, 0.16 + 0.002), [0, 0, slope]);
      add(new THREE.BoxGeometry(0.3, 0.12, 0.26), std(0x16171a, 0.5, 0.3), V3(X(hx - 0.05), by + 0.12, 0), [0, 0, slope]);
      add(new THREE.BoxGeometry(0.02, 0.09, 0.22), std(0x050506, 0, 0.9), V3(X(hx - 0.2), by + 0.12, 0), [0, 0, slope]);
    }
    // интеркулер в бампере
    if (eng && isForced(eng) && eng.intercooler !== 'none') add(new THREE.BoxGeometry(0.03, 0.12, W * 0.55), std(0x8f979f, 0.8, 0.35, { map: stripeTex('#a8afb7', '#2c2f34', 18, true) }), V3(X(0.06), F.sill + 0.13, 0));
  }
  // ---- салон: сиденья, руль, каркас ----
  const seatX = (F.cab0 + F.cab1) / 2 + (P.open ? 0.2 : 0.05);
  const belt = top(seatX);
  const rows = P.dragster ? [0] : (red >= 2 || P.open || c.body === 'coupe' || c.body === 'super' ? [0] : [0, 0.85]);
  rows.forEach((dx, ri) => [1, -1].forEach(s => {
    if (P.dragster && s < 0) return;
    const sz = P.dragster ? 0 : s * W * 0.21, sx = seatX + dx;
    const bucket = c.seats === 'bucket' && ri === 0;
    const sm = bucket ? std(0x1b1c20, 0.1, 0.85) : std(0x2b2c31, 0.05, 0.9);
    const cy = belt - 0.4, roofHere = (P.open || P.dragster) ? belt + 0.5 : F.roof(sx + 0.3);
    const bh = Math.max(0.3, roofHere - 0.13 - cy);
    add(new THREE.BoxGeometry(0.48, 0.1, 0.46), sm, V3(X(sx), cy, sz));
    add(new THREE.BoxGeometry(0.11, bh, 0.46), sm, V3(X(sx + 0.29), cy + bh / 2, sz), [0, 0, 0.18]);
    if (bucket) [1, -1].forEach(q => add(new THREE.BoxGeometry(0.12, bh * 0.8, 0.05), std(0xc8241f, 0.1, 0.7), V3(X(sx + 0.28), cy + bh * 0.42, sz + q * 0.22), [0, 0, 0.18]));
    else add(new THREE.BoxGeometry(0.1, 0.14, 0.26), sm, V3(X(sx + 0.33), cy + bh + 0.02, sz), [0, 0, 0.18]);
  }));
  add(new THREE.BoxGeometry(0.4, 0.14, W * 0.8), std(0x15161a, 0.1, 0.8), V3(X(F.cab0 + 0.2), top(F.cab0 + 0.2) - 0.06, 0)); // торпедо
  add(new THREE.TorusGeometry(0.17, 0.02, 8, 28), std(0x111214, 0.2, 0.6), V3(X(seatX - 0.45), belt - 0.05, P.dragster ? 0 : W * 0.21), [0, Math.PI / 2 + 0.45, 0]);
  if (c.cage || P.dragster || P.open) {
    const cm = std(c.cage ? 0xd23b2a : 0x2a2b30, 0.6, 0.35);
    const hx2 = X(seatX + 0.42), hz = P.dragster ? 0.3 : hw(seatX) * 0.62, hy2 = P.open || P.dragster ? belt + 0.45 : F.roof(seatX + 0.42) - 0.07;
    add(new THREE.TubeGeometry(new THREE.CatmullRomCurve3([V3(hx2, belt - 0.22, -hz), V3(hx2, hy2, -hz * 0.85), V3(hx2, hy2, hz * 0.85), V3(hx2, belt - 0.22, hz)]), 20, 0.025, 6), cm);
    if (c.cage && !P.open && !P.dragster) [1, -1].forEach(s => add(new THREE.TubeGeometry(new THREE.CatmullRomCurve3([V3(hx2, hy2, s * hz * 0.85), V3(X(F.roofF + 0.1), F.roof(F.roofF + 0.1) - 0.08, s * hz * 0.82), V3(X(F.cab0 + 0.12), top(F.cab0 + 0.12) + 0.02, s * hz)]), 16, 0.022, 6), cm));
  }
  // ---- аэродинамика ----
  const rear = X(L), front = X(0);
  if (c.splitter) add(new THREE.BoxGeometry(0.22, 0.018, W * 0.88), carbon, V3(front + 0.06, F.sill - 0.01 + 0.05, 0));
  if (c.diffuser) { add(new THREE.BoxGeometry(0.5, 0.02, W * 0.72), black, V3(rear - 0.2, F.sill + 0.04, 0), [0, 0, 0.16]); for (let k = -2; k <= 2; k++) add(new THREE.BoxGeometry(0.42, 0.09, 0.012), black, V3(rear - 0.22, F.sill + 0.06, k * W * 0.13), [0, 0, 0.16]); }
  const tailX = P.bed ? L - 0.15 : L - 0.18;
  const tailTop = top(tailX);
  if (c.spoiler === 'lip') add(new THREE.BoxGeometry(0.1, 0.025, W * 0.78), carbonHood ? carbon : paintSolid, V3(rear - 0.08, top(L - 0.08) + 0.015, 0), [0, 0, -0.3]);
  if (c.spoiler === 'wing' || c.spoiler === 'big' || P.dragster) {
    const big = c.spoiler === 'big' || P.dragster, hgt = P.dragster ? 0.95 : big ? 0.32 : 0.17, chord = big ? 0.36 : 0.26;
    const af = new THREE.Shape(); af.moveTo(0, 0); af.quadraticCurveTo(chord * 0.3, 0.05, chord, 0.012); af.lineTo(chord, -0.004); af.quadraticCurveTo(chord * 0.3, -0.012, 0, 0);
    const wd = P.dragster ? 1.3 : W * 0.94;
    const wg = new THREE.ExtrudeGeometry(af, { depth: wd, bevelEnabled: false, curveSegments: 10 }); wg.translate(0, 0, -wd / 2);
    add(wg, P.dragster ? paintSolid : carbon, V3(rear - chord - 0.06, tailTop + hgt, 0), [0, 0, -0.1]);
    [1, -1].forEach(s => { add(new THREE.BoxGeometry(0.08, hgt, 0.018), black, V3(rear - chord * 0.5 - 0.06, tailTop + hgt / 2, s * wd * 0.28)); add(new THREE.BoxGeometry(chord * 1.15, 0.15, 0.012), black, V3(rear - chord * 0.5 - 0.06, tailTop + hgt + 0.02, s * wd / 2)); });
  }
  // платформа пикапа
  if (P.bed) {
    const bx0 = X(P.bed[0]), bx1 = X(P.bed[1]), by = top((P.bed[0] + P.bed[1]) / 2) + 0.02;
    add(new THREE.BoxGeometry(bx1 - bx0, 0.012, W * 0.82), std(0x15161a, 0.2, 0.8, { map: stripeTex('#1d1e22', '#0f1012', 10, false) }), V3((bx0 + bx1) / 2, by, 0));
    [1, -1].forEach(s => add(new THREE.BoxGeometry(bx1 - bx0, 0.05, 0.05), paintSolid, V3((bx0 + bx1) / 2, by + 0.02, s * (hw((P.bed[0] + P.bed[1]) / 2) * 0.86))));
  }
  // выхлоп: диаметр насадок зависит от трубы двигателя
  const tipR = clamp(((eng && eng.exhDia) || 50) / 1000 * 0.75, 0.03, 0.07);
  const tips = c.tips === 'quad' ? [-0.38, -0.27, 0.27, 0.38] : c.tips === 'dual' ? [-0.33, 0.33] : c.tips === 'center' ? [-0.06, 0.06] : [0.3];
  this.tipPos = [];
  tips.forEach(z => { const p = V3(rear + 0.03, F.sill + 0.06, z * W * 0.9); add(new THREE.CylinderGeometry(tipR, tipR * 0.92, 0.16, 24, 1, true), chrome, p, [0, 0, Math.PI / 2]); add(new THREE.CircleGeometry(tipR * 0.9, 20), std(0x050505, 0, 1), p.clone().add(V3(0.07, 0, 0)), [0, Math.PI / 2, 0]); this.tipPos.push(p.clone().add(V3(0.1, 0, 0))); });
  // ---- колёса ----
  this.wheels = [];
  const tw = (stats ? c.tireWidth : 195) / 1000;
  const rimR = c.rim * 0.0254 / 2;
  P.wb.forEach((ax, i) => [1, -1].forEach(s => {
    let rr = rW, ww = tw;
    if (P.dragster) { if (i === 0) { rr = 0.24; ww = 0.09; } else { rr = 0.46; ww = 0.43; } }
    const outer = P.dragster ? (i === 0 ? W / 2 + 0.25 : W / 2 + ww / 2 + 0.12) : hw(ax) * (1 + F.bulge(ax)) - ww / 2 - 0.02 + (c.kit === 'wide' ? 0.02 : 0);
    const wg = this.wheel(c, rr, ww, P.dragster ? (i === 0 ? 0.17 : 0.29) : rimR, s, P.dragster && i === 0);
    wg.position.set(X(ax), rr, s * outer);
    root.add(wg); this.wheels.push(wg);
  }));
  if (P.dragster) [1, -1].forEach(s => add(new THREE.CylinderGeometry(0.02, 0.02, W / 2 + 0.25, 8), black, V3(X(P.wb[0]), 0.24, s * (W / 4 + 0.12)), [Math.PI / 2, 0, 0]));
  // дрэг-подвеска: нос чуть приподнят
  if (c.suspension === 'drag') root.rotation.z = -0.012;
  return root;
};

// тормоза зависят от уровня
const _wheelOrig = Car3D.prototype.wheel;
Car3D.prototype.wheel = function (c, R, w, rimR, side, wire) {
  const outer = _wheelOrig.call(this, c, R, w, rimR, side, wire);
  const g = outer.userData.inner;
  const lvl = { stock: 0, sport: 1, race: 2, ceramic: 3 }[c.brakes || 'stock'];
  const cal = outer.children[1];
  if (cal) { cal.scale.set(1 + lvl * 0.25, 1 + lvl * 0.3, 1 + lvl * 0.15); if (lvl === 0 && c.caliper === 'black') cal.material.color.setHex(0x3a3c40); }
  // диск: размер и цвет (керамика — серо-золотой), сверловка — тёмные точки
  g.children.forEach(m => {
    if (m.geometry && m.geometry.type === 'CylinderGeometry' && m.geometry.parameters.height === 0.025) {
      m.scale.set(0.88 + lvl * 0.05, 1, 0.88 + lvl * 0.05);
      if (lvl === 3) m.material = std(0x6b6255, 0.6, 0.45);
      if (lvl >= 1) for (let k = 0; k < 12; k++) { const a = k / 12 * Math.PI * 2; const h = new THREE.Mesh(new THREE.CircleGeometry(0.006, 6), std(0x111111, 0, 1)); h.position.set(Math.cos(a) * rimR * 0.62, Math.sin(a) * rimR * 0.62, m.position.z + side * 0.0135); h.rotation.y = side > 0 ? 0 : Math.PI; g.add(h); }
    }
  });
  // надписи на шинах спортивного уровня — белая полоса на боковине
  if (c.tire === 'sport' || c.tire === 'semi' || c.tire === 'drag') {
    const ring = new THREE.Mesh(new THREE.RingGeometry(R * 0.86, R * 0.9, 48, 1, 0, Math.PI * 0.35), new THREE.MeshBasicMaterial({ color: c.tire === 'drag' ? 0xf2a93b : 0xdddddd }));
    ring.position.z = side * (w / 2 + 0.004); if (side < 0) ring.rotation.y = Math.PI; g.add(ring);
    const ring2 = ring.clone(); ring2.rotation.z = Math.PI; g.add(ring2);
  }
  return outer;
};

// setCar теперь учитывает двигатель (видимые доработки)
Car3D.prototype.setCar = function (c, stats, eng) {
  const ek = eng ? [eng.cyl, eng.induction, eng.intercooler, eng.exhDia, eng.type, Math.round(displacement(eng) * 10)].join(',') : '';
  const key = JSON.stringify(c) + (stats ? stats.rWheel : '') + ek;
  if (key === this.key) return;
  this.key = key;
  if (this.root) { this.scene.remove(this.root); disposeTree(this.root); }
  this.root = this.build(c, stats, eng);
  this.scene.add(this.root);
  const P = CAR_PROFILES[c.body] || CAR_PROFILES.hatch;
  this.shadow.scale.set(P.L * 1.25, P.W * 1.6, 1);
  this.orbit.target.set(0, 0.62, 0);
  this.orbit.base = Math.max(7.5, P.L * 2.1); if (!this.framed) { this.orbit.dist = this.orbit.base; this.framed = true; }
};

// Готовая модель машины для заезда
function buildCarModel(c, stats, eng) {
  const b = Object.create(Car3D.prototype);
  b.wheels = []; b.calipers = [];
  const g = b.build(c, stats, eng);
  return { group: g, wheels: b.wheels, tips: b.tipPos || [] };
}

// ================= 3D-ЗАЕЗД =================
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
    s.add(new THREE.HemisphereLight(0x8fa6d8, 0x1a1712, 0.45));
    const moon = new THREE.DirectionalLight(0xbfd0ff, 0.6); moon.position.set(-20, 40, 15); s.add(moon);
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
    n = 0; for (let i = 0; i < 30; i++) [-6, 6].forEach(z => { mt.compose(V3(X0 - i * 24, 4, z), new THREE.Quaternion(), V3(1, 1, 1)); poles.setMatrixAt(n, mt); mt.compose(V3(X0 - i * 24, 8, z * 0.86), new THREE.Quaternion(), V3(1, 1, 1)); heads.setMatrixAt(n, mt); n++; });
    s.add(poles); s.add(heads);
    // световые пятна под фонарями
    const pool = canvasTex(128, 128, (g, w, h) => { const gr = g.createRadialGradient(64, 64, 2, 64, 64, 64); gr.addColorStop(0, 'rgba(255,214,150,0.38)'); gr.addColorStop(1, 'rgba(255,214,150,0)'); g.fillStyle = gr; g.fillRect(0, 0, w, h); });
    const pools = new THREE.InstancedMesh(new THREE.PlaneGeometry(14, 14), new THREE.MeshBasicMaterial({ map: pool, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending }), 60);
    n = 0; for (let i = 0; i < 30; i++) [-4, 4].forEach(z => { mt.compose(V3(X0 - i * 24, 0.01, z), q, V3(1, 1, 1)); pools.setMatrixAt(n++, mt); }); s.add(pools);
    // трибуна со зрителями у старта
    const stand = new THREE.Mesh(new THREE.BoxGeometry(60, 0.4, 8), archM); stand.position.set(-10, 0.2, -11); s.add(stand);
    for (let r = 0; r < 5; r++) { const step = new THREE.Mesh(new THREE.BoxGeometry(60, 0.5, 1.4), std(0x2a2d33, 0.2, 0.8)); step.position.set(-10, 0.5 + r * 0.5, -8.2 - r * 1.4); s.add(step); }
    const crowd = new THREE.InstancedMesh(new THREE.BoxGeometry(0.42, 0.75, 0.32), new THREE.MeshStandardMaterial({ roughness: 0.8 }), 300);
    const cc = new THREE.Color();
    for (let i = 0; i < 300; i++) { const r = i % 5; mt.compose(V3(-38 + Math.random() * 56, 1.1 + r * 0.5, -8.2 - r * 1.4), new THREE.Quaternion(), V3(1, 0.9 + Math.random() * 0.3, 1)); crowd.setMatrixAt(i, mt); cc.setHSL(Math.random(), 0.5, 0.3 + Math.random() * 0.3); crowd.setColorAt(i, cc); }
    s.add(crowd);
    // город на горизонте
    const win = canvasTex(64, 128, (g, w, h) => { g.fillStyle = '#0d1018'; g.fillRect(0, 0, w, h); for (let y = 4; y < h; y += 8) for (let x = 3; x < w; x += 8) if (Math.random() < 0.38) { g.fillStyle = Math.random() < 0.8 ? '#ffd27a' : '#9cc8ff'; g.fillRect(x, y, 4, 4); } });
    const bm = new THREE.MeshStandardMaterial({ color: 0x222633, emissive: 0xffffff, emissiveMap: win, emissiveIntensity: 0.9, map: win });
    const bld = new THREE.InstancedMesh(new THREE.BoxGeometry(1, 1, 1), bm, 160);
    n = 0; for (let i = 0; i < 80; i++) [-1, 1].forEach(sd => { const h = 15 + Math.random() * 55, w = 8 + Math.random() * 14; mt.compose(V3(X0 + 20 - i * 9.5, h / 2, sd * (45 + Math.random() * 60)), new THREE.Quaternion(), V3(w, h, 8 + Math.random() * 10)); bld.setMatrixAt(n++, mt); }); s.add(bld);
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
      pos = V3(cx + 10, 2.6, this.laneMe + 4.6);
      look = V3(cx - 9, 0.5, -0.4);
    }
    this.camPos = this.camPos ? this.camPos.lerp(pos, Math.min(1, dt * 4)) : pos;
    const sh = clamp(v / 60, 0, 1) * 0.04 + (me.wheelspin > 0.1 ? 0.03 : 0);
    this.cam.position.copy(this.camPos).add(V3((Math.random() - 0.5) * sh, (Math.random() - 0.5) * sh, 0));
    this.cam.lookAt(look);
  }
}
