'use strict';
// ================= ОТРИСОВКА: ДВИГАТЕЛЬ, ПРИБОРЫ, ГРАФИКИ, МАШИНА =================
const COL = {
  bg: '#15171b', panel: '#1d2026', line: '#2c3038', text: '#e7e9ec', dim: '#8d949e',
  amber: '#f2a93b', boost: '#4fa3ff', red: '#ff4d3d', green: '#53d08a',
  steel: '#8b939e', steelL: '#c9ced6', steelD: '#3b4048', oil: '#2a2418',
};
const BLOCK_COL = { iron: ['#50555d', '#3a3e45'], cgi: ['#5d6168', '#43474e'], alu: ['#a3abb5', '#7c848f'], alusil: ['#b2b9c2', '#87909a'], billet: ['#cfd5dc', '#9aa3ad'] };
const D2R = Math.PI / 180;

function shade(hex, k) {
  const n = parseInt(hex.slice(1), 16);
  let r = (n >> 16) & 255, g = (n >> 8) & 255, b = n & 255;
  if (k > 0) { r += (255 - r) * k; g += (255 - g) * k; b += (255 - b) * k; } else { r *= 1 + k; g *= 1 + k; b *= 1 + k; }
  return 'rgb(' + (r | 0) + ',' + (g | 0) + ',' + (b | 0) + ')';
}
function heatColor(t) { // 0..1: холодная сталь → красный → оранжево-жёлтый
  t = clamp(t, 0, 1);
  const stops = [[0, [74, 78, 86]], [0.45, [120, 60, 50]], [0.7, [205, 60, 30]], [0.88, [250, 130, 40]], [1, [255, 210, 120]]];
  for (let i = 1; i < stops.length; i++) if (t <= stops[i][0]) {
    const a = stops[i - 1], b = stops[i], k = (t - a[0]) / (b[0] - a[0]);
    return 'rgb(' + a[1].map((v, j) => Math.round(v + (b[1][j] - v) * k)).join(',') + ')';
  }
  return 'rgb(255,210,120)';
}
function rrect(ctx, x, y, w, h, r) {
  r = Math.min(r, Math.abs(w) / 2, Math.abs(h) / 2);
  ctx.beginPath(); ctx.moveTo(x + r, y); ctx.lineTo(x + w - r, y); ctx.quadraticCurveTo(x + w, y, x + w, y + r);
  ctx.lineTo(x + w, y + h - r); ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h); ctx.lineTo(x + r, y + h);
  ctx.quadraticCurveTo(x, y + h, x, y + h - r); ctx.lineTo(x, y + r); ctx.quadraticCurveTo(x, y, x + r, y); ctx.closePath();
}
function metalGrad(ctx, x0, y0, x1, y1, c) {
  const g = ctx.createLinearGradient(x0, y0, x1, y1);
  g.addColorStop(0, shade(c, 0.25)); g.addColorStop(0.5, c); g.addColorStop(1, shade(c, -0.35));
  return g;
}

// ---------- Частицы (дым, пламя, искры) ----------
class Particles {
  constructor() { this.list = []; }
  add(p) { if (this.list.length < 260) this.list.push(p); }
  step(dt) {
    for (const p of this.list) { p.x += p.vx * dt; p.y += p.vy * dt; p.life -= dt; p.vy += (p.g || 0) * dt; p.r += (p.grow || 0) * dt; }
    this.list = this.list.filter(p => p.life > 0);
  }
  draw(ctx) {
    for (const p of this.list) {
      const a = clamp(p.life / p.max, 0, 1);
      ctx.globalAlpha = a * (p.alpha || 1);
      ctx.fillStyle = p.color;
      ctx.beginPath(); ctx.arc(p.x, p.y, Math.max(0.5, p.r), 0, Math.PI * 2); ctx.fill();
    }
    ctx.globalAlpha = 1;
  }
}

// ---------- Геометрия рядов ----------
function bankAngles(e) {
  const a = e.vAngle;
  switch (e.type) {
    case 'inline': return [0];
    case 'v': return [-a / 2, a / 2];
    case 'boxer': return [-90, 90];
    case 'vr': return [-7.5, 7.5];
    case 'w': return e.cyl === 18 ? [-60, 0, 60] : [-a / 2 - 7.5, -a / 2 + 7.5, a / 2 - 7.5, a / 2 + 7.5];
    case 'radial': return Array.from({ length: e.cyl }, (_, i) => i * 360 / e.cyl);
  }
  return [0];
}
function pistonDist(r, l, alpha) { const s = r * Math.sin(alpha); return r * Math.cos(alpha) + Math.sqrt(Math.max(0, l * l - s * s)); }

// Состояние отрисовки: угол коленвала (град, 0..720), обороты и прочее
// st: { crank, rpm, throttle, spool, egt(0..1), load, view, highlight, blown, nitrous, t, shake, sc }
function drawEngine(ctx, e, W, H, st, parts) {
  ctx.save();
  ctx.fillStyle = COL.bg; ctx.fillRect(0, 0, W, H);
  // пол стенда
  const floorG = ctx.createLinearGradient(0, H * 0.75, 0, H);
  floorG.addColorStop(0, 'rgba(255,255,255,0)'); floorG.addColorStop(1, 'rgba(255,255,255,0.04)');
  ctx.fillStyle = floorG; ctx.fillRect(0, H * 0.75, W, H * 0.25);
  // сетка
  ctx.strokeStyle = 'rgba(255,255,255,0.035)'; ctx.lineWidth = 1;
  for (let x = 0; x < W; x += 24) { ctx.beginPath(); ctx.moveTo(x + 0.5, 0); ctx.lineTo(x + 0.5, H); ctx.stroke(); }
  for (let y = 0; y < H; y += 24) { ctx.beginPath(); ctx.moveTo(0, y + 0.5); ctx.lineTo(W, y + 0.5); ctx.stroke(); }
  if (st.shake) ctx.translate((Math.random() - 0.5) * st.shake, (Math.random() - 0.5) * st.shake);
  const side = st.view === 'side' && e.type !== 'radial';
  if (isRotary(e)) side ? drawRotarySide(ctx, e, W, H, st, parts) : drawRotaryFront(ctx, e, W, H, st, parts);
  else if (side) drawSide(ctx, e, W, H, st, parts);
  else drawFront(ctx, e, W, H, st, parts);
  if (parts) parts.draw(ctx);
  if (st.blown) {
    ctx.fillStyle = 'rgba(255,40,30,0.10)'; ctx.fillRect(-20, -20, W + 40, H + 40);
  }
  ctx.restore();
}

function engineGeom(e) {
  const r = e.stroke / 2, l = e.rodLen, compH = e.bore * 0.34, wall = Math.max(5, e.bore * 0.1);
  const deck = r + l + compH + 2;
  const headH = e.cycle === 2 ? e.bore * 0.32 : e.valvetrain === 'ohv' ? e.bore * 0.55 : e.valvetrain === 'sohc' ? e.bore * 0.7 : e.bore * 0.85;
  return { r, l, compH, wall, deck, headH, bore: e.bore };
}

// ===== ВИД СПЕРЕДИ (разрез) =====
function drawFront(ctx, e, W, H, st, parts) {
  const g = engineGeom(e);
  const banks = bankAngles(e);
  const radial = e.type === 'radial';
  // габарит для масштаба
  let minX = -g.r * 1.9, maxX = g.r * 1.9, minY = -g.r * 1.9, maxY = g.r * 2.2 + (radial ? 0 : 30);
  const ext = g.deck + g.headH + 12, hw = g.bore / 2 + g.wall + 10;
  for (const a of banks) {
    const ra = a * D2R, ux = Math.sin(ra), uy = -Math.cos(ra), px = Math.cos(ra), py = Math.sin(ra);
    for (const [d, w] of [[ext, hw], [ext, -hw], [g.r + g.l * 0.4, hw], [g.r + g.l * 0.4, -hw]]) {
      const x = ux * d + px * w, y = uy * d + py * w;
      minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
    }
  }
  const accW = (hasTurbo(e) || e.induction === 'sc_cent' ? 0.32 : 0.12) * (maxX - minX) + 20;
  const accTop = hasSC(e) && e.induction !== 'sc_cent' ? 0.25 * (maxY - minY) : 0.08 * (maxY - minY);
  const bw = maxX - minX + accW * 2, bh = maxY - minY + accTop + 20;
  const s = Math.min(W * 0.94 / bw, H * 0.9 / bh);
  const cx = W / 2 - (minX + maxX) / 2 * s, cy = H * 0.5 - (minY + maxY - accTop) / 2 * s + 6;
  ctx.save(); ctx.translate(cx, cy); ctx.scale(s, s);
  ctx.lineJoin = 'round';
  const theta = (st.crank || 0) % 720;
  const thR = theta * D2R;
  const bc = BLOCK_COL[e.block] || BLOCK_COL.iron;
  const hl = st.highlight;
  const lw = 1 / s;

  // --- картер / поддон ---
  if (!radial) {
    ctx.fillStyle = metalGrad(ctx, -g.r * 2, 0, g.r * 2, 0, bc[1]);
    const pw = Math.max(g.r * 2.3, (maxX - minX) * 0.35), ph = g.r * 2 + (e.oilSystem === 'dry' ? 8 : 26);
    rrect(ctx, -pw / 2, -g.r * 0.4, pw, ph + g.r * 0.4, 6); ctx.fill();
    ctx.fillStyle = shade(bc[1], -0.3);
    rrect(ctx, -pw / 2 - 3, ph - 4, pw + 6, 5, 2); ctx.fill();
    if (e.oilSystem === 'dry') {
      ctx.fillStyle = '#2f3339'; rrect(ctx, pw / 2 + 6, -g.r * 0.2, 16, ph * 0.9, 6); ctx.fill();
      ctx.strokeStyle = COL.steel; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(pw / 2, ph - 10); ctx.lineTo(pw / 2 + 8, ph - 6); ctx.stroke();
    } else {
      ctx.fillStyle = 'rgba(70,52,20,0.7)'; ctx.fillRect(-pw / 2 + 4, ph - 14, pw - 8, 10);
    }
    if (hl === 'cool') { ctx.strokeStyle = COL.amber; ctx.lineWidth = 2.5 * lw; rrect(ctx, -pw / 2, -g.r * 0.4, pw, ph + g.r * 0.4, 6); ctx.stroke(); }
  } else {
    ctx.fillStyle = metalGrad(ctx, -g.r * 2, -g.r * 2, g.r * 2, g.r * 2, bc[1]);
    ctx.beginPath(); ctx.arc(0, 0, g.r * 1.7 + g.l * 0.42, 0, Math.PI * 2); ctx.fill();
    ctx.strokeStyle = shade(bc[1], -0.4); ctx.lineWidth = 2; ctx.stroke();
  }

  // --- задний план: выпуск и турбины (под рядами) ---
  const exhGlow = clamp(st.egt || 0, 0, 1);
  const headInfo = [];
  banks.forEach((a, bi) => {
    const ra = a * D2R;
    headInfo.push({ a, ra, ux: Math.sin(ra), uy: -Math.cos(ra), px: Math.cos(ra), py: Math.sin(ra) });
  });

  // наружные стороны рядов — для выпуска
  const exhaustSide = (h) => {
    if (e.type === 'inline') return 1;
    if (radial) return 1;
    return h.a >= 0 ? 1 : -1;
  };

  // выпускные трубы
  headInfo.forEach((h, bi) => {
    if (e.type === 'vr' && bi === 0) return;
    if (e.type === 'w' && bi % 2 === 0 && e.cyl !== 18) return;
    const sd = exhaustSide(h) * (e.type === 'vr' || (e.type === 'w' && e.cyl !== 18) ? 1 : 1);
    const d = g.deck + g.headH * 0.35;
    const ox = h.ux * d + h.px * (g.bore / 2 + g.wall) * sd, oy = h.uy * d + h.py * (g.bore / 2 + g.wall) * sd;
    const dia = clamp(e.exhDia * 0.55, 12, 50);
    ctx.strokeStyle = heatColor(exhGlow * 0.95);
    ctx.lineWidth = dia; ctx.lineCap = 'round';
    ctx.beginPath(); ctx.moveTo(ox, oy);
    if (radial) {
      // радиальный: короткие патрубки наружу
      ctx.lineTo(ox + h.px * 22 + h.ux * 8, oy + h.py * 22 + h.uy * 8);
    } else {
      const ex = ox + h.px * 30 * sd + (sd > 0 ? 18 : -18), ey = oy + h.py * 30 * sd + 30;
      ctx.quadraticCurveTo(ox + h.px * 40 * sd, oy + h.py * 40 * sd, ex, Math.max(ey, g.r * 1.5));
      ctx.lineTo(ex, maxY + 4);
    }
    ctx.stroke();
    if (exhGlow > 0.55) {
      ctx.strokeStyle = 'rgba(255,140,50,' + ((exhGlow - 0.55) * 0.6) + ')'; ctx.lineWidth = dia + 10; ctx.stroke();
    }
    if (hl === 'exhaust') { ctx.strokeStyle = COL.amber; ctx.lineWidth = dia + 4; ctx.globalAlpha = 0.35; ctx.stroke(); ctx.globalAlpha = 1; }
    h.exhOut = { x: ox, y: oy, sd };
  });

  // --- ряды цилиндров ---
  // порядок: сначала дальние (для радиальной звезды все равноправны)
  headInfo.forEach((h, bi) => drawBank(ctx, e, g, h, bi, theta, st, bc, lw));

  // общие головки VR/W
  if (e.type === 'vr' || (e.type === 'w' && e.cyl !== 18)) {
    for (let k = 0; k < headInfo.length; k += 2) drawSharedHead(ctx, e, g, headInfo[k], headInfo[k + 1], st, lw, theta);
  }

  // --- коленвал ---
  const pinX = g.r * Math.sin(thR), pinY = -g.r * Math.cos(thR);
  ctx.fillStyle = metalGrad(ctx, -g.r, -g.r, g.r, g.r, '#6d737c');
  ctx.beginPath(); // противовес
  ctx.moveTo(pinX * 0.3, pinY * 0.3);
  ctx.arc(0, 0, g.r * 1.45, thR + Math.PI / 2 + 0.5 - Math.PI / 2 + Math.PI / 2, thR + Math.PI * 1.5 - 0.5 - Math.PI / 2 + Math.PI / 2);
  ctx.closePath(); ctx.fill();
  ctx.save(); ctx.rotate(thR);
  ctx.fillStyle = metalGrad(ctx, -g.r, 0, g.r, 0, e.crank === 'billet' ? '#b9c0c9' : e.crank === 'forged' ? '#8c939c' : '#6c727a');
  ctx.beginPath(); ctx.arc(0, g.r * 0.55, g.r * 1.25, 0.15 * Math.PI, 0.85 * Math.PI); ctx.lineTo(-g.r * 0.4, -g.r); ctx.lineTo(g.r * 0.4, -g.r); ctx.closePath(); ctx.fill();
  ctx.restore();
  // шатуны
  headInfo.forEach((h) => {
    const alpha = thR - h.ra;
    const xp = pistonDist(g.r, g.l, alpha);
    const wx = h.ux * xp, wy = h.uy * xp;
    drawRod(ctx, e, pinX, pinY, wx, wy, g);
  });
  // шейки
  ctx.fillStyle = '#d5dae0'; ctx.beginPath(); ctx.arc(pinX, pinY, g.r * 0.38, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = '#9aa1aa'; ctx.beginPath(); ctx.arc(0, 0, g.r * 0.45, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = '#2b2f35'; ctx.beginPath(); ctx.arc(0, 0, g.r * 0.15, 0, Math.PI * 2); ctx.fill();
  if (hl === 'crank') { ctx.strokeStyle = COL.amber; ctx.lineWidth = 2.5 * lw; ctx.beginPath(); ctx.arc(0, 0, g.r * 1.6, 0, Math.PI * 2); ctx.stroke(); }

  // шкив и ремень привода навесного
  const pulR = g.r * 0.95;
  ctx.strokeStyle = '#1c1e22'; ctx.lineWidth = 4;
  ctx.beginPath(); ctx.arc(0, 0, pulR, 0, Math.PI * 2); ctx.stroke();

  // --- впуск ---
  drawIntakeFront(ctx, e, g, headInfo, st, lw, minY, maxX, minX);

  // --- наддув ---
  drawForcedFront(ctx, e, g, headInfo, st, lw, minX, maxX, minY, maxY, pulR, parts, s, cx, cy);

  // --- закись азота ---
  if (e.nitrous) {
    const bx = minX - 4, by = maxY - 70;
    ctx.fillStyle = st.nitrous ? '#5aa9ff' : '#2a5fb8';
    rrect(ctx, bx - 14, by, 16, 64, 6); ctx.fill();
    ctx.fillStyle = '#c9ced6'; ctx.fillRect(bx - 10, by - 8, 8, 9);
    ctx.fillStyle = '#e33'; ctx.font = 'bold 6px sans-serif'; ctx.save(); ctx.translate(bx - 4, by + 50); ctx.rotate(-Math.PI / 2); ctx.fillText('NOS', 0, 0); ctx.restore();
  }

  // частицы выхлопа
  if (parts && st.emit) {
    headInfo.forEach((h) => {
      if (!h.exhOut) return;
      const tipX = radial ? h.exhOut.x + h.px * 22 : h.exhOut.x + h.px * 30 * h.exhOut.sd + (h.exhOut.sd > 0 ? 18 : -18);
      const tipY = radial ? h.exhOut.y + h.py * 22 : maxY + 4;
      emitExhaust(parts, cx + tipX * s, cy + tipY * s, st, s, radial ? h.px : 0, radial ? h.py : 1);
    });
  }
  ctx.restore();
}

function drawRod(ctx, e, ax, ay, bx, by, g) {
  const col = e.rods === 'titanium' ? '#9fa7b8' : e.rods === 'billet' ? '#c3c9d1' : e.rods === 'forged' ? '#8e959e' : '#737981';
  const dx = bx - ax, dy = by - ay, L = Math.hypot(dx, dy), nx = -dy / L, ny = dx / L;
  const w1 = g.r * 0.32, w2 = g.bore * 0.09;
  ctx.fillStyle = col;
  ctx.beginPath();
  ctx.moveTo(ax + nx * w1, ay + ny * w1); ctx.lineTo(bx + nx * w2, by + ny * w2);
  ctx.lineTo(bx - nx * w2, by - ny * w2); ctx.lineTo(ax - nx * w1, ay - ny * w1); ctx.closePath(); ctx.fill();
  ctx.fillStyle = shade(col, -0.25);
  ctx.beginPath(); ctx.arc(ax, ay, g.r * 0.5, 0, Math.PI * 2); ctx.fill();
  ctx.beginPath(); ctx.arc(bx, by, g.bore * 0.12, 0, Math.PI * 2); ctx.fill();
}

// Один ряд в виде спереди
function drawBank(ctx, e, g, h, bi, theta, st, bc, lw) {
  ctx.save();
  ctx.rotate(h.ra);
  const hb = g.bore / 2, wall = g.wall, hl = st.highlight;
  const shared = e.type === 'vr' || (e.type === 'w' && e.cyl !== 18);
  // стенка блока
  const y0 = -(g.r + g.l * 0.42), y1 = -g.deck;
  ctx.fillStyle = metalGrad(ctx, -hb - wall, 0, hb + wall, 0, bc[0]);
  ctx.fillRect(-hb - wall, y1, g.bore + wall * 2, y0 - y1);
  // водяная рубашка
  if (e.deck === 'open') { ctx.fillStyle = 'rgba(40,110,160,0.35)'; ctx.fillRect(-hb - wall * 0.7, y1, wall * 0.4, (y0 - y1) * 0.6); ctx.fillRect(hb + wall * 0.3, y1, wall * 0.4, (y0 - y1) * 0.6); }
  // гильза
  const bg = ctx.createLinearGradient(-hb, 0, hb, 0);
  bg.addColorStop(0, '#191b1f'); bg.addColorStop(0.5, '#2a2d33'); bg.addColorStop(1, '#16181b');
  ctx.fillStyle = bg; ctx.fillRect(-hb, y1, g.bore, y0 - y1);
  if (hl === 'block') { ctx.strokeStyle = COL.amber; ctx.lineWidth = 2.5 * lw; ctx.strokeRect(-hb - wall, y1, g.bore + wall * 2, y0 - y1); }

  // поршень
  const alpha = theta * D2R - h.ra;
  const xp = pistonDist(g.r, g.l, alpha);
  const crownY = -(xp + g.compH);
  // сгорание
  const cyc = e.cycle === 2 ? 360 : 720;
  let ph = ((theta - h.a) % cyc + cyc) % cyc;
  if (e.cycle === 4 && bi % 2 === 1 && e.type === 'boxer') ph = (ph + 360) % 720;
  const burn = ph < 110 && st.rpm > 50 && !st.blown && !st.off;
  if (burn) {
    const k = 1 - ph / 110;
    const gr = ctx.createLinearGradient(0, y1, 0, crownY);
    gr.addColorStop(0, 'rgba(255,230,140,' + (0.95 * k) + ')'); gr.addColorStop(1, 'rgba(255,90,20,' + (0.55 * k) + ')');
    ctx.fillStyle = gr; ctx.fillRect(-hb, y1, g.bore, crownY - y1);
  } else if (e.cycle === 4 && ph > 360 && ph < 560 && st.rpm > 50) {
    ctx.fillStyle = 'rgba(120,170,255,0.12)'; ctx.fillRect(-hb, y1, g.bore, crownY - y1);
  }
  const pc = e.pistons === 'forged' || e.pistons === 'billet' ? '#c4cad2' : '#a7aeb7';
  ctx.fillStyle = metalGrad(ctx, -hb, 0, hb, 0, pc);
  ctx.fillRect(-hb + 0.8, crownY, g.bore - 1.6, g.compH + g.bore * 0.32);
  ctx.fillStyle = 'rgba(0,0,0,0.35)';
  for (let i = 0; i < 3; i++) ctx.fillRect(-hb + 0.8, crownY + 3 + i * 3.2, g.bore - 1.6, 1.1);
  if (e.cycle === 2) { ctx.fillStyle = 'rgba(0,0,0,0.25)'; ctx.fillRect(-hb, y1 + (y0 - y1) * 0.45, 3, 10); ctx.fillRect(hb - 3, y1 + (y0 - y1) * 0.4, 3, 12); }

  // головка
  if (!shared) drawHead(ctx, e, g, -hb - wall, g.bore + wall * 2, y1, ph, st, lw, h);
  ctx.restore();
}

function drawHead(ctx, e, g, x, w, y1, ph, st, lw, h) {
  const hh = g.headH, hl = st.highlight;
  const hc = e.head === 'iron' ? '#5a5f67' : '#a9b1bb';
  ctx.fillStyle = metalGrad(ctx, x, 0, x + w, 0, hc);
  ctx.fillRect(x - 2, y1 - hh * 0.55, w + 4, hh * 0.55);
  // камера сгорания и клапаны
  const hb = g.bore / 2;
  if (e.cycle === 4) {
    const iv = valveLift(ph, 350, 570), ev = valveLift(ph, 160, 370);
    const vl = g.bore * 0.18 * clamp(e.camLift / 10, 0.6, 1.6);
    drawValve(ctx, -hb * 0.45, y1, iv * vl, g, '#c7ccd3', -1);
    drawValve(ctx, hb * 0.45, y1, ev * vl, g, '#d9a56a', 1);
    // клапанный механизм
    const ct = y1 - hh * 0.55;
    if (e.valvetrain === 'ohv') {
      // коромысла и штанги
      ctx.strokeStyle = '#9aa1aa'; ctx.lineWidth = 2;
      ctx.beginPath(); ctx.moveTo(-hb * 0.45, ct - 2 + iv * 2); ctx.lineTo(0, ct - 6); ctx.lineTo(hb * 0.45, ct - 2 + ev * 2); ctx.stroke();
      ctx.strokeStyle = '#7f868f'; ctx.lineWidth = 1.5;
      ctx.beginPath(); ctx.moveTo(-hb * 0.7, ct - 4); ctx.lineTo(-hb * 0.9, -g.r - g.l * 0.35); ctx.stroke();
    } else {
      const cams = e.valvetrain === 'dohc' ? [-hb * 0.45, hb * 0.45] : [0];
      cams.forEach((cxp, i) => drawCam(ctx, cxp, ct - g.bore * 0.14, g.bore * 0.13, (st.crank || 0) / 2 + i * 90 + h.a, e));
    }
    // крышка клапанов
    const cover = COVER_COLORS[e.coverColor] || '#24262b';
    ctx.fillStyle = metalGrad(ctx, x, 0, x + w, 0, cover);
    const cTop = y1 - hh;
    rrect(ctx, x, cTop, w, hh * 0.47, 6); ctx.fill();
    ctx.fillStyle = 'rgba(255,255,255,0.12)';
    for (let i = 1; i < 4; i++) ctx.fillRect(x + w * i / 4 - 0.6, cTop + 3, 1.2, hh * 0.47 - 6);
  } else {
    const cTop = y1 - hh;
    ctx.fillStyle = metalGrad(ctx, x, 0, x + w, 0, '#9aa2ac');
    for (let i = 0; i < 5; i++) ctx.fillRect(x + 2, cTop + i * hh / 5, w - 4, hh / 10); // рёбра охлаждения
  }
  // свеча
  const fire = ph < 14 && st.rpm > 50 && !st.off && !FUELS[e.fuel].diesel;
  ctx.fillStyle = '#eee'; ctx.fillRect(-1.6, y1 - hh * 0.62, 3.2, hh * 0.3);
  if (fire) {
    ctx.fillStyle = 'rgba(160,200,255,0.95)'; ctx.beginPath(); ctx.arc(0, y1 + 1, 4, 0, Math.PI * 2); ctx.fill();
  }
  if (hl === 'head' || hl === 'ecu') { ctx.strokeStyle = COL.amber; ctx.lineWidth = 2.5 * lw; ctx.strokeRect(x - 2, y1 - hh, w + 4, hh); }
}
function valveLift(ph, open, close) { if (ph < open || ph > close) return 0; return Math.sin((ph - open) / (close - open) * Math.PI); }
function drawValve(ctx, x, y1, lift, g, col, tilt) {
  ctx.save(); ctx.translate(x, y1 + lift); ctx.rotate(tilt * 0.12);
  ctx.fillStyle = col; ctx.fillRect(-0.9, -g.headH * 0.5, 1.8, g.headH * 0.5);
  ctx.beginPath(); ctx.moveTo(-g.bore * 0.16, 0); ctx.lineTo(g.bore * 0.16, 0); ctx.lineTo(1.5, -4); ctx.lineTo(-1.5, -4); ctx.closePath(); ctx.fill();
  ctx.restore();
}
function drawCam(ctx, x, y, r, angDeg, e) {
  ctx.save(); ctx.translate(x, y); ctx.rotate(angDeg * D2R);
  const lobe = 1 + clamp(e.camLift / 10, 0.7, 1.6) * 0.38;
  const dur = clamp((e.camDur - 200) / 120, 0, 1);
  ctx.fillStyle = '#b8bec6';
  ctx.beginPath();
  for (let i = 0; i <= 40; i++) {
    const a = i / 40 * Math.PI * 2;
    const d = Math.cos(a), k = d > 0 ? Math.pow(d, 2.4 - dur * 1.4) : 0;
    const rr = r * (1 + (lobe - 1) * k);
    const px = Math.sin(a) * rr, py = -Math.cos(a) * rr;
    i ? ctx.lineTo(px, py) : ctx.moveTo(px, py);
  }
  ctx.closePath(); ctx.fill();
  ctx.fillStyle = '#5d636b'; ctx.beginPath(); ctx.arc(0, 0, r * 0.35, 0, Math.PI * 2); ctx.fill();
  ctx.restore();
}

function drawSharedHead(ctx, e, g, h1, h2, st, lw, theta) {
  if (!h2) return;
  ctx.save();
  const a = (h1.a + h2.a) / 2;
  ctx.rotate(a * D2R);
  const spread = Math.abs(h2.a - h1.a) * D2R;
  const off = Math.sin(spread / 2) * g.deck;
  const w = g.bore * 2 + g.wall * 2 + off * 2 - g.bore * 0.6;
  const y1 = -g.deck * Math.cos(spread / 2) + 2;
  const ph = ((theta - h1.a) % 720 + 720) % 720;
  drawHead(ctx, e, Object.assign({}, g, { headH: g.headH * 1.05 }), -w / 2, w, y1, ph, st, lw, h1);
  ctx.restore();
}

function drawIntakeFront(ctx, e, g, heads, st, lw, minY, maxX, minX) {
  const hl = st.highlight === 'intake' || st.highlight === 'fuel';
  ctx.save();
  ctx.lineCap = 'round';
  const runnerW = clamp(e.throttle * 0.5, 10, 36);
  const col = e.manifold === 'itb' ? '#b9bfc7' : '#6f767f';
  if (e.type === 'radial') {
    // карбюратор снизу
    ctx.fillStyle = '#7d848d'; rrect(ctx, -18, g.r * 2.1, 36, 26, 4); ctx.fill();
  } else if (e.type === 'inline') {
    const h = heads[0];
    const d = g.deck + g.headH * 0.3, x = -(g.bore / 2 + g.wall);
    ctx.strokeStyle = col; ctx.lineWidth = runnerW;
    ctx.beginPath(); ctx.moveTo(x, -d); ctx.quadraticCurveTo(x - 40, -d - 10, x - 46, -d + 40); ctx.stroke();
    if (e.manifold === 'itb') {
      drawTrumpet(ctx, x - 46, -d + 46, runnerW, Math.PI);
    } else {
      ctx.fillStyle = '#5b6169'; rrect(ctx, x - 70, -d + 30, 46, 52, 8); ctx.fill();
      drawFilter(ctx, e, x - 72, -d + 90);
    }
    if (e.fuelSystem !== 'carb') drawInjector(ctx, x - 14, -d - 10);
    if (hl) { ctx.strokeStyle = COL.amber; ctx.lineWidth = 2.5 * lw; ctx.strokeRect(x - 80, -d - 20, 76, 130); }
  } else if (e.type === 'boxer') {
    // впуск сверху: от каждой головки к ресиверу в центре
    const top = -g.r * 2.4 - 20;
    ctx.strokeStyle = col; ctx.lineWidth = runnerW * 0.8;
    heads.forEach(h => {
      const d = g.deck + g.headH * 0.3;
      const x = h.ux * d, y = h.uy * d - (g.bore / 2 + g.wall);
      ctx.beginPath(); ctx.moveTo(x, y); ctx.quadraticCurveTo(x * 0.6, top, x * 0.25, top); ctx.stroke();
    });
    ctx.fillStyle = '#5b6169'; rrect(ctx, -40, top - 20, 80, 30, 8); ctx.fill();
    if (!hasSC(e)) drawFilter(ctx, e, -14, top - 40);
    if (hl) { ctx.strokeStyle = COL.amber; ctx.lineWidth = 2.5 * lw; ctx.strokeRect(-60, top - 46, 120, 60); }
  } else {
    // V, VR, W: ресивер в развале
    let topY = Infinity;
    heads.forEach(h => { const y = h.uy * (g.deck + g.headH); if (y < topY) topY = y; });
    const inner = heads.length > 1 ? Math.abs(Math.sin(heads[heads.length - 1].ra)) : 0;
    const valleyY = -(g.deck * Math.cos(heads[heads.length - 1].ra));
    const pw = Math.max(40, g.bore * 0.9 + inner * g.deck * 0.6);
    if (e.manifold === 'itb' && e.type === 'v') {
      heads.forEach(h => {
        const d = g.deck + g.headH * 0.2;
        const x = h.ux * d - h.px * (g.bore / 2 + g.wall), y = h.uy * d - h.py * (g.bore / 2 + g.wall);
        ctx.strokeStyle = col; ctx.lineWidth = runnerW * 0.8;
        const tx = x - h.px * 26 + h.ux * 20, ty = y - h.py * 26 + h.uy * 20;
        ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(tx, ty); ctx.stroke();
        drawTrumpet(ctx, tx, ty, runnerW * 0.8, Math.atan2(ty - y, tx - x));
      });
    } else {
      const py = Math.min(valleyY - 10, -g.r - g.l * 0.6);
      ctx.fillStyle = metalGrad(ctx, -pw / 2, 0, pw / 2, 0, '#6a7179');
      rrect(ctx, -pw / 2, py - 34, pw, 40, 10); ctx.fill();
      ctx.fillStyle = 'rgba(255,255,255,0.08)'; ctx.fillRect(-pw / 2 + 6, py - 30, pw - 12, 4);
      // корпус дросселя смотрит на нас
      ctx.fillStyle = '#2a2e34'; ctx.beginPath(); ctx.arc(0, py - 14, runnerW * 0.55, 0, Math.PI * 2); ctx.fill();
      ctx.strokeStyle = '#9aa1aa'; ctx.lineWidth = 2; ctx.stroke();
      const thrA = (st.throttle || 0) * Math.PI / 2;
      ctx.strokeStyle = '#c9ced6'; ctx.lineWidth = 2;
      ctx.beginPath(); ctx.moveTo(-Math.cos(thrA) * runnerW * 0.5, py - 14); ctx.lineTo(Math.cos(thrA) * runnerW * 0.5, py - 14); ctx.stroke();
      if (!hasSC(e) && !hasTurbo(e)) drawFilter(ctx, e, -16, py - 70);
      if (e.fuelSystem !== 'carb') { drawInjector(ctx, -pw / 2 + 8, py + 2); drawInjector(ctx, pw / 2 - 8, py + 2); }
      if (hl) { ctx.strokeStyle = COL.amber; ctx.lineWidth = 2.5 * lw; ctx.strokeRect(-pw / 2 - 4, py - 74, pw + 8, 84); }
    }
  }
  ctx.restore();
}
function drawTrumpet(ctx, x, y, w, ang) {
  ctx.save(); ctx.translate(x, y); ctx.rotate(ang);
  ctx.fillStyle = '#d0d5db';
  ctx.beginPath(); ctx.moveTo(0, -w / 2); ctx.quadraticCurveTo(12, -w / 2, 18, -w * 0.9); ctx.lineTo(18, w * 0.9); ctx.quadraticCurveTo(12, w / 2, 0, w / 2); ctx.closePath(); ctx.fill();
  ctx.restore();
}
function drawFilter(ctx, e, x, y) {
  if (e.airFilter === 'cone') {
    ctx.fillStyle = '#c0392b'; ctx.beginPath(); ctx.moveTo(x, y + 10); ctx.lineTo(x + 34, y); ctx.lineTo(x + 34, y + 30); ctx.lineTo(x, y + 20); ctx.closePath(); ctx.fill();
    ctx.strokeStyle = 'rgba(0,0,0,0.4)'; ctx.lineWidth = 1; for (let i = 1; i < 6; i++) { ctx.beginPath(); ctx.moveTo(x + i * 6, y + 10 - i * 1.7); ctx.lineTo(x + i * 6, y + 20 + i * 1.7); ctx.stroke(); }
  } else if (e.airFilter === 'ram') {
    ctx.fillStyle = '#2d3138'; rrect(ctx, x - 6, y, 44, 28, 10); ctx.fill(); ctx.fillStyle = '#1a8fd8'; ctx.fillRect(x - 4, y + 11, 40, 5);
  } else {
    ctx.fillStyle = '#30343b'; rrect(ctx, x - 2, y, 36, 26, 4); ctx.fill();
  }
}
function drawInjector(ctx, x, y) { ctx.fillStyle = '#2f7de1'; ctx.fillRect(x - 2.5, y - 10, 5, 10); ctx.fillStyle = '#222'; ctx.fillRect(x - 1.5, y, 3, 3); }

function drawForcedFront(ctx, e, g, heads, st, lw, minX, maxX, minY, maxY, pulR, parts, s, cx, cy) {
  const hl = st.highlight === 'boost';
  const spin = st.spool || 0;
  const t = st.t || 0;
  // турбины
  if (hasTurbo(e)) {
    const nT = turboCount(e);
    const rT = clamp(e.turboSize * 0.55, 18, 60) * (nT === 4 ? 0.75 : 1);
    const spots = [];
    const both = e.type === 'v' || e.type === 'boxer' || e.type === 'w' || e.type === 'vr';
    if (both && nT >= 2) {
      spots.push([maxX + rT * 0.3, g.r * 0.9]); spots.push([minX - rT * 0.3, g.r * 0.9]);
      if (nT === 4) { spots.push([maxX + rT * 0.3, g.r * 0.9 - rT * 2.3]); spots.push([minX - rT * 0.3, g.r * 0.9 - rT * 2.3]); }
    } else {
      for (let i = 0; i < nT; i++) spots.push([maxX + rT * 0.4 - (i % 2) * 0, g.r * 0.6 - i * rT * 2.2]);
    }
    spots.forEach(([x, y], i) => {
      const small = e.turboLayout === 'sequential' && i === 0;
      drawTurbo(ctx, x, y, small ? rT * 0.8 : rT, t * (6 + spin * 60) * (small ? 1.4 : 1), spin, st.egt || 0, e, hl, lw);
    });
    // интеркулер
    if (e.intercooler !== 'none') {
      const iw = (maxX - minX) * 0.7, ih = e.intercooler === 'big' ? 26 : 18;
      const iy = maxY + 10;
      ctx.fillStyle = e.intercooler === 'water' ? '#2b4d6e' : '#3a3f46';
      rrect(ctx, -iw / 2, iy, iw, ih, 3); ctx.fill();
      ctx.strokeStyle = 'rgba(255,255,255,0.12)'; ctx.lineWidth = 1;
      for (let x = -iw / 2 + 3; x < iw / 2; x += 3) { ctx.beginPath(); ctx.moveTo(x, iy + 2); ctx.lineTo(x, iy + ih - 2); ctx.stroke(); }
      ctx.strokeStyle = 'rgba(79,163,255,' + (0.25 + spin * 0.6) + ')'; ctx.lineWidth = 6;
      ctx.beginPath(); ctx.moveTo(spots[0][0], spots[0][1] + rT); ctx.quadraticCurveTo(spots[0][0], iy + ih / 2, iw / 2, iy + ih / 2); ctx.stroke();
    }
  }
  if (hasSC(e)) {
    const type = e.induction === 'twincharge' ? 'sc_roots' : e.induction;
    if (type === 'sc_cent') {
      const x = minX - 26, y = g.r * 0.3;
      drawTurbo(ctx, x, y, 26, t * (4 + (st.rpm || 0) / 200), clamp((st.rpm || 0) / 7000, 0, 1), 0, e, hl, lw, true);
      ctx.strokeStyle = '#111'; ctx.lineWidth = 3;
      ctx.beginPath(); ctx.moveTo(0, pulR); ctx.lineTo(x, y + 14); ctx.moveTo(0, -pulR); ctx.lineTo(x, y - 14); ctx.stroke();
    } else {
      let top = Infinity; heads.forEach(h => top = Math.min(top, h.uy * (g.deck + g.headH)));
      if (e.type === 'inline') top = -(g.deck + g.headH);
      const w = Math.max(70, g.bore * 1.5), hgt = 50;
      const x = e.type === 'inline' ? -(g.bore / 2 + g.wall) - 90 : -w / 2;
      const y = e.type === 'inline' ? -g.deck - 30 : Math.min(top, -g.r - g.l) - hgt - 8;
      ctx.fillStyle = metalGrad(ctx, x, 0, x + w, 0, '#a7aeb7');
      rrect(ctx, x, y, w, hgt, 10); ctx.fill();
      // роторы
      const ang = t * (2 + (st.rpm || 0) / 300);
      [-1, 1].forEach((k, i) => {
        ctx.save(); ctx.translate(x + w / 2 + k * w * 0.2, y + hgt / 2); ctx.rotate(k * ang);
        ctx.fillStyle = '#e1e5ea';
        const lobes = type === 'sc_screw' ? 4 : 3;
        ctx.beginPath();
        for (let j = 0; j <= 60; j++) { const a = j / 60 * Math.PI * 2; const rr = hgt * 0.26 * (0.7 + 0.3 * Math.cos(a * lobes)); ctx.lineTo(Math.cos(a) * rr, Math.sin(a) * rr); }
        ctx.closePath(); ctx.fill(); ctx.restore();
      });
      ctx.fillStyle = '#1d2026'; rrect(ctx, x + w / 2 - 10, y - 16, 20, 18, 4); ctx.fill(); // дроссель на крышке
      drawFilter(ctx, e, x + w / 2 - 17, y - 42);
      // приводной ремень
      ctx.strokeStyle = '#111'; ctx.lineWidth = 3;
      ctx.beginPath(); ctx.moveTo(-pulR, 0); ctx.lineTo(x + w * 0.2, y + hgt); ctx.moveTo(pulR, 0); ctx.lineTo(x + w * 0.8, y + hgt); ctx.stroke();
      if (hl) { ctx.strokeStyle = COL.amber; ctx.lineWidth = 2.5 * lw; rrect(ctx, x - 3, y - 3, w + 6, hgt + 6, 10); ctx.stroke(); }
    }
  }
}

function drawTurbo(ctx, x, y, r, ang, spin, egt, e, hl, lw, coldOnly) {
  ctx.save(); ctx.translate(x, y);
  // горячая улитка
  if (!coldOnly) {
    ctx.fillStyle = heatColor(egt * 0.9);
    ctx.beginPath(); ctx.arc(0, r * 0.35, r * 0.72, 0, Math.PI * 2); ctx.fill();
  }
  // холодная улитка: спираль
  ctx.fillStyle = metalGrad(ctx, -r, -r, r, r, '#b6bdc6');
  ctx.beginPath();
  for (let i = 0; i <= 50; i++) { const a = i / 50 * Math.PI * 2; const rr = r * (0.72 + 0.28 * i / 50); ctx.lineTo(Math.cos(a) * rr, Math.sin(a) * rr); }
  ctx.lineTo(r, -r * 0.55); ctx.lineTo(r * 1.35, -r * 0.55); ctx.lineTo(r * 1.35, 0); ctx.closePath(); ctx.fill();
  ctx.fillStyle = '#16181b'; ctx.beginPath(); ctx.arc(0, 0, r * 0.52, 0, Math.PI * 2); ctx.fill();
  // колесо компрессора
  ctx.rotate(ang);
  ctx.strokeStyle = spin > 0.6 ? 'rgba(220,226,232,0.6)' : '#dfe3e8'; ctx.lineWidth = Math.max(1.2, r * 0.06);
  const blades = 10;
  for (let i = 0; i < blades; i++) {
    const a = i / blades * Math.PI * 2;
    ctx.beginPath(); ctx.moveTo(Math.cos(a) * r * 0.1, Math.sin(a) * r * 0.1);
    ctx.quadraticCurveTo(Math.cos(a + 0.3) * r * 0.35, Math.sin(a + 0.3) * r * 0.35, Math.cos(a + 0.55) * r * 0.48, Math.sin(a + 0.55) * r * 0.48); ctx.stroke();
  }
  if (spin > 0.4) { ctx.fillStyle = 'rgba(200,210,220,' + (spin - 0.4) * 0.5 + ')'; ctx.beginPath(); ctx.arc(0, 0, r * 0.5, 0, Math.PI * 2); ctx.fill(); }
  ctx.fillStyle = '#9aa1aa'; ctx.beginPath(); ctx.arc(0, 0, r * 0.09, 0, Math.PI * 2); ctx.fill();
  ctx.restore();
  if (hl) { ctx.strokeStyle = COL.amber; ctx.lineWidth = 2.5 * lw; ctx.beginPath(); ctx.arc(x, y, r * 1.15, 0, Math.PI * 2); ctx.stroke(); }
}

function emitExhaust(parts, x, y, st, s, dx, dy) {
  const load = st.load || 0;
  if (st.pop) {
    for (let i = 0; i < 6; i++) parts.add({ x, y, vx: dx * 160 + (Math.random() - 0.5) * 80, vy: dy * 160 + (Math.random() - 0.5) * 80, r: 3 + Math.random() * 4, grow: 10, life: 0.18, max: 0.18, color: i % 2 ? '#ffb347' : '#ff5a1f' });
  }
  if (st.nitrous && Math.random() < 0.5) parts.add({ x, y, vx: dy * 20, vy: 60, r: 2, grow: 14, life: 0.4, max: 0.4, color: '#bcd8ff', alpha: 0.35 });
  if (Math.random() < 0.15 + load * 0.4) parts.add({ x, y, vx: (Math.random() - 0.5) * 20 + dx * 40, vy: 40 + Math.random() * 20, r: 2, grow: 16 + load * 20, life: 0.9, max: 0.9, color: st.smoke || '#8d949e', alpha: 0.12 + load * 0.15 + (st.blackSmoke ? 0.4 : 0) });
}

// ===== ВИД СБОКУ (продольный разрез) =====
function drawSide(ctx, e, W, H, st, parts) {
  const g = engineGeom(e);
  const nb = e.type === 'inline' ? 1 : e.type === 'w' ? 4 : 2;
  const m = Math.max(1, Math.round(e.cyl / nb));
  const pitch = g.bore + g.wall * 1.4;
  const blockW = pitch * m + g.wall * 2;
  // габариты для вписывания в экран
  const rT0 = hasTurbo(e) ? clamp(e.turboSize * 0.55, 16, 55) : 0;
  const exD0 = clamp(e.exhDia * 0.35, 6, 30);
  const collY0 = g.r * 1.2 + 30;
  const top = -g.deck - g.headH - 18 - 26 - (hasSC(e) && e.induction !== 'sc_cent' ? 92 : 0) - (hasTurbo(e) ? 14 : 0);
  const bottom = Math.max(collY0 + exD0, collY0 - rT0 * 0.6 + rT0 * 1.1) + 6;
  const left = -blockW / 2 - 62, right = blockW / 2 + (hasTurbo(e) ? rT0 * 2.7 : 34);
  const s = Math.min(W * 0.94 / (right - left), H * 0.9 / (bottom - top));
  const cx = W / 2 - (left + right) / 2 * s, cy = H / 2 - (top + bottom) / 2 * s;
  ctx.save(); ctx.translate(cx, cy); ctx.scale(s, s);
  const lw = 1 / s, hl = st.highlight;
  const theta = (st.crank || 0) % 720;
  const x0 = -blockW / 2;
  const bc = BLOCK_COL[e.block] || BLOCK_COL.iron;
  const phases = cylPhases(e);
  const cyc = e.cycle === 2 ? 360 : 720;
  const y1 = -g.deck, y0 = g.r * 1.2;

  // картер
  ctx.fillStyle = metalGrad(ctx, 0, y1, 0, y0 + 40, bc[1]);
  rrect(ctx, x0, -g.r - g.l * 0.45, blockW, g.r + g.l * 0.45 + y0 + (e.oilSystem === 'dry' ? 6 : 24), 8); ctx.fill();
  if (hl === 'cool') { ctx.strokeStyle = COL.amber; ctx.lineWidth = 2.5 * lw; ctx.strokeRect(x0, y0 - 10, blockW, 36); }
  // блок
  ctx.fillStyle = metalGrad(ctx, 0, y1, 0, -g.r, bc[0]);
  ctx.fillRect(x0, y1, blockW, g.deck - g.r - g.l * 0.45);
  if (hl === 'block') { ctx.strokeStyle = COL.amber; ctx.lineWidth = 2.5 * lw; ctx.strokeRect(x0, y1, blockW, g.deck - g.r); }

  // выпуск: трубы вниз к коллектору и назад
  const exhGlow = clamp(st.egt || 0, 0, 1);
  const collY = y0 + 30;
  ctx.lineCap = 'round';
  const exDia = clamp(e.exhDia * 0.35, 6, 30);
  for (let i = 0; i < m; i++) {
    const cxp = x0 + g.wall + pitch * (i + 0.5);
    ctx.strokeStyle = heatColor(exhGlow * 0.95); ctx.lineWidth = exDia;
    ctx.globalAlpha = 0.6;
    ctx.beginPath(); ctx.moveTo(cxp + g.bore * 0.2, y1 - g.headH * 0.25);
    ctx.bezierCurveTo(cxp + g.bore * 0.5, y1 + 30, cxp + pitch * 0.5, collY - 30, blockW / 2 - 6 + (i - m) * 2, collY);
    ctx.stroke();
  }
  ctx.globalAlpha = 1;
  ctx.strokeStyle = heatColor(exhGlow); ctx.lineWidth = exDia * 1.3;
  ctx.beginPath(); ctx.moveTo(x0 + pitch * 0.5, collY); ctx.lineTo(blockW / 2 + 20, collY); ctx.stroke();
  if (hl === 'exhaust') { ctx.strokeStyle = COL.amber; ctx.lineWidth = 2.5 * lw; ctx.strokeRect(x0, y1 - g.headH * 0.4, blockW + 30, collY - y1 + 20); }

  // цилиндры
  for (let i = 0; i < m; i++) {
    const cxp = x0 + g.wall + pitch * (i + 0.5);
    const ph = (((theta - phases[i]) % cyc) + cyc) % cyc;
    const ang = ph * D2R;
    const xp = pistonDist(g.r, g.l, ang);
    const crownY = -(xp + g.compH);
    const bg = ctx.createLinearGradient(cxp - g.bore / 2, 0, cxp + g.bore / 2, 0);
    bg.addColorStop(0, '#17191c'); bg.addColorStop(0.5, '#2a2d33'); bg.addColorStop(1, '#141619');
    ctx.fillStyle = bg; ctx.fillRect(cxp - g.bore / 2, y1, g.bore, g.deck - g.r - g.l * 0.45);
    const burn = ph < 110 && st.rpm > 50 && !st.blown && !st.off;
    if (burn) {
      const k = 1 - ph / 110;
      const gr = ctx.createLinearGradient(0, y1, 0, crownY);
      gr.addColorStop(0, 'rgba(255,230,140,' + 0.95 * k + ')'); gr.addColorStop(1, 'rgba(255,90,20,' + 0.5 * k + ')');
      ctx.fillStyle = gr; ctx.fillRect(cxp - g.bore / 2, y1, g.bore, crownY - y1);
    } else if (e.cycle === 4 && ph > 360 && ph < 560 && st.rpm > 50) { ctx.fillStyle = 'rgba(120,170,255,0.12)'; ctx.fillRect(cxp - g.bore / 2, y1, g.bore, crownY - y1); }
    // шатун и шейка
    const pinX = cxp + g.r * Math.sin(ang) * 0.35, pinY = -g.r * Math.cos(ang);
    drawRod(ctx, e, pinX, pinY, cxp, -xp, g);
    const pcol = e.pistons === 'forged' || e.pistons === 'billet' ? '#c4cad2' : '#a7aeb7';
    ctx.fillStyle = metalGrad(ctx, cxp - g.bore / 2, 0, cxp + g.bore / 2, 0, pcol);
    ctx.fillRect(cxp - g.bore / 2 + 0.8, crownY, g.bore - 1.6, g.compH + g.bore * 0.32);
    ctx.fillStyle = 'rgba(0,0,0,0.35)'; for (let k = 0; k < 3; k++) ctx.fillRect(cxp - g.bore / 2 + 0.8, crownY + 3 + k * 3.2, g.bore - 1.6, 1.1);
    // щёки коленвала
    ctx.fillStyle = '#7a8089';
    ctx.fillRect(cxp - g.bore * 0.32, Math.min(0, pinY) - 5, 7, Math.abs(pinY) + 10);
    ctx.fillRect(cxp + g.bore * 0.32 - 7, Math.min(0, pinY) - 5, 7, Math.abs(pinY) + 10);
    ctx.fillStyle = '#d5dae0'; ctx.beginPath(); ctx.arc(pinX, pinY, g.r * 0.34, 0, Math.PI * 2); ctx.fill();
    // номер цилиндра
    ctx.fillStyle = 'rgba(255,255,255,0.35)'; ctx.font = (g.bore * 0.22) + 'px sans-serif'; ctx.textAlign = 'center';
    ctx.fillText(String(i + 1), cxp, -g.r - g.l * 0.45 - 6);
    // клапаны и свечи
    if (e.cycle === 4) {
      const iv = valveLift(ph, 350, 570), ev = valveLift(ph, 160, 370);
      const vl = g.bore * 0.16;
      ctx.fillStyle = '#c7ccd3'; ctx.fillRect(cxp - g.bore * 0.3 - 1, y1 - g.headH * 0.45 + iv * vl, 2, g.headH * 0.45); ctx.fillRect(cxp - g.bore * 0.42, y1 - 2 + iv * vl, g.bore * 0.24, 2.5);
      ctx.fillStyle = '#d9a56a'; ctx.fillRect(cxp + g.bore * 0.3 - 1, y1 - g.headH * 0.45 + ev * vl, 2, g.headH * 0.45); ctx.fillRect(cxp + g.bore * 0.18, y1 - 2 + ev * vl, g.bore * 0.24, 2.5);
    }
    if (ph < 14 && st.rpm > 50 && !st.off && !FUELS[e.fuel].diesel) { ctx.fillStyle = 'rgba(160,200,255,0.95)'; ctx.beginPath(); ctx.arc(cxp, y1 + 1, 4, 0, Math.PI * 2); ctx.fill(); }
  }
  // коренная ось
  ctx.strokeStyle = '#9aa1aa'; ctx.lineWidth = g.r * 0.5; ctx.beginPath(); ctx.moveTo(x0 - 16, 0); ctx.lineTo(x0 + blockW + 6, 0); ctx.stroke();
  if (hl === 'crank') { ctx.strokeStyle = COL.amber; ctx.lineWidth = 2.5 * lw; ctx.strokeRect(x0, -g.r * 1.5, blockW, g.r * 3); }

  // головка
  const hh = g.headH;
  const hc = e.head === 'iron' ? '#5a5f67' : '#a9b1bb';
  ctx.fillStyle = metalGrad(ctx, 0, y1 - hh, 0, y1, hc);
  ctx.fillRect(x0 - 2, y1 - hh * 0.55, blockW + 4, hh * 0.55);
  if (e.cycle === 4 && e.valvetrain !== 'ohv') {
    const nCams = e.valvetrain === 'dohc' ? 2 : 1;
    for (let i = 0; i < m; i++) {
      const cxp = x0 + g.wall + pitch * (i + 0.5);
      const ph = (((theta - phases[i]) % 720) + 720) % 720;
      drawCam(ctx, cxp - (nCams === 2 ? g.bore * 0.22 : 0), y1 - hh * 0.62, g.bore * 0.1, ph / 2 + 180, e);
      if (nCams === 2) drawCam(ctx, cxp + g.bore * 0.22, y1 - hh * 0.62, g.bore * 0.1, ph / 2 + 90, e);
    }
    // ремень ГРМ спереди
    ctx.strokeStyle = '#1b1d21'; ctx.lineWidth = 5;
    ctx.beginPath(); ctx.moveTo(x0 - 16, -g.r * 0.6); ctx.lineTo(x0 - 16, y1 - hh * 0.75); ctx.moveTo(x0 - 4, -g.r * 0.6); ctx.lineTo(x0 - 4, y1 - hh * 0.75); ctx.stroke();
    ctx.fillStyle = '#c9ced6'; ctx.beginPath(); ctx.arc(x0 - 10, y1 - hh * 0.65, 8, 0, Math.PI * 2); ctx.fill(); ctx.beginPath(); ctx.arc(x0 - 10, 0, 7, 0, Math.PI * 2); ctx.fill();
  }
  if (e.cycle === 4) {
    const cover = COVER_COLORS[e.coverColor] || '#24262b';
    ctx.fillStyle = metalGrad(ctx, 0, y1 - hh, 0, y1 - hh * 0.5, cover);
    rrect(ctx, x0 + 2, y1 - hh, blockW - 4, hh * 0.45, 8); ctx.fill();
    ctx.fillStyle = 'rgba(255,255,255,0.85)'; ctx.font = 'bold ' + (hh * 0.2) + 'px sans-serif'; ctx.textAlign = 'center';
    ctx.fillText(layoutCode(e) + ' ' + displacement(e).toFixed(1), 0, y1 - hh * 0.7);
  } else {
    ctx.fillStyle = metalGrad(ctx, 0, y1 - hh, 0, y1, '#9aa2ac');
    for (let i = 0; i < 5; i++) ctx.fillRect(x0 + 2, y1 - hh + i * hh / 5, blockW - 4, hh / 10);
  }
  if (hl === 'head' || hl === 'ecu') { ctx.strokeStyle = COL.amber; ctx.lineWidth = 2.5 * lw; ctx.strokeRect(x0 - 2, y1 - hh, blockW + 4, hh); }

  // впуск сверху
  const iTop = y1 - hh - 18;
  const rw = clamp(e.throttle * 0.32, 7, 26);
  ctx.strokeStyle = e.manifold === 'itb' ? '#b9bfc7' : '#6f767f'; ctx.lineWidth = rw; ctx.lineCap = 'butt';
  for (let i = 0; i < m; i++) {
    const cxp = x0 + g.wall + pitch * (i + 0.5);
    ctx.beginPath(); ctx.moveTo(cxp - g.bore * 0.25, y1 - hh * 0.5); ctx.lineTo(cxp - g.bore * 0.3, iTop + 6); ctx.stroke();
    if (e.manifold === 'itb') drawTrumpet(ctx, cxp - g.bore * 0.3, iTop + 4, rw, -Math.PI / 2);
    if (e.fuelSystem !== 'carb') drawInjector(ctx, cxp - g.bore * 0.05, y1 - hh * 0.5);
  }
  if (e.manifold !== 'itb') {
    ctx.fillStyle = metalGrad(ctx, 0, iTop - 24, 0, iTop + 6, '#6a7179');
    rrect(ctx, x0 + 6, iTop - 22, blockW - 12, 28, 10); ctx.fill();
    ctx.fillStyle = '#2a2e34'; rrect(ctx, x0 - 22, iTop - 18, 30, 20, 5); ctx.fill(); // дроссель спереди
    if (!hasSC(e)) drawFilter(ctx, e, x0 - 58, iTop - 22);
  }
  if (hl === 'intake' || hl === 'fuel') { ctx.strokeStyle = COL.amber; ctx.lineWidth = 2.5 * lw; ctx.strokeRect(x0 - 60, iTop - 30, blockW + 64, 40 + hh * 0.5); }
  // компрессор
  if (hasSC(e) && e.induction !== 'sc_cent') {
    const w = Math.min(blockW * 0.6, 160), y = iTop - 62;
    ctx.fillStyle = metalGrad(ctx, 0, y, 0, y + 40, '#a7aeb7'); rrect(ctx, -w / 2, y, w, 40, 10); ctx.fill();
    ctx.strokeStyle = 'rgba(0,0,0,0.25)'; ctx.lineWidth = 2; for (let x = -w / 2 + 8; x < w / 2; x += 8) { ctx.beginPath(); ctx.moveTo(x, y + 4); ctx.lineTo(x, y + 36); ctx.stroke(); }
    drawFilter(ctx, e, -17, y - 28);
    ctx.strokeStyle = '#111'; ctx.lineWidth = 3; ctx.beginPath(); ctx.moveTo(x0 - 24, 0); ctx.lineTo(x0 - 24, y + 20); ctx.lineTo(-w / 2, y + 20); ctx.stroke(); ctx.fillStyle = '#c9ced6'; ctx.beginPath(); ctx.arc(x0 - 24, y + 20, 9, 0, Math.PI * 2); ctx.fill();
    if (hl === 'boost') { ctx.strokeStyle = COL.amber; ctx.lineWidth = 2.5 * lw; rrect(ctx, -w / 2 - 3, y - 3, w + 6, 46, 10); ctx.stroke(); }
  }
  // турбина сзади
  if (hasTurbo(e) || e.induction === 'sc_cent') {
    const rT = hasTurbo(e) ? clamp(e.turboSize * 0.55, 16, 55) : 22;
    const tx = blockW / 2 + rT * 1.25, ty = collY - rT * 0.6;
    if (hasTurbo(e)) drawTurbo(ctx, tx, ty, rT, (st.t || 0) * (6 + (st.spool || 0) * 60), st.spool || 0, st.egt || 0, e, hl === 'boost', lw);
    else drawTurbo(ctx, x0 - 40, 10, rT, (st.t || 0) * (4 + (st.rpm || 0) / 200), 0.5, 0, e, hl === 'boost', lw, true);
    if (hasTurbo(e)) {
      ctx.strokeStyle = 'rgba(79,163,255,' + (0.3 + (st.spool || 0) * 0.6) + ')'; ctx.lineWidth = rw * 0.8; ctx.lineCap = 'round';
      ctx.beginPath(); ctx.moveTo(tx, ty - rT); ctx.quadraticCurveTo(tx, iTop - 30, blockW / 2 - 10, iTop - 8); ctx.stroke();
      if (e.intercooler !== 'none') { ctx.fillStyle = e.intercooler === 'water' ? '#2b4d6e' : '#3a3f46'; rrect(ctx, tx - 10, (ty - rT + iTop) / 2 - 18, 20, 36, 3); ctx.fill(); }
    }
    if (parts && st.emit) emitExhaust(parts, cx + (tx + rT * 0.2) * s, cy + (ty + rT * 1.1) * s, st, s, 0.3, 1);
  } else if (parts && st.emit) emitExhaust(parts, cx + (blockW / 2 + 20) * s, cy + collY * s, st, s, 1, 0.2);
  // закись
  if (e.nitrous) { ctx.fillStyle = st.nitrous ? '#5aa9ff' : '#2a5fb8'; rrect(ctx, x0 - 50, y0 - 20, 60, 16, 6); ctx.fill(); }
  ctx.restore();
}

// ===== РОТОРНЫЙ ДВИГАТЕЛЬ =====
function drawRotorUnit(ctx, R, ecc, shaftA, st, e, idx, hl, lw, small) {
  // корпус: эпитрохоида
  ctx.fillStyle = metalGrad(ctx, -R * 1.4, 0, R * 1.4, 0, '#8e959e');
  ctx.beginPath(); ctx.ellipse(0, 0, R * 1.55, R * 1.35, 0, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = '#17191c';
  ctx.beginPath();
  for (let i = 0; i <= 80; i++) { const t = i / 80 * Math.PI * 2; const x = ecc * Math.cos(2 * t) + R * Math.cos(t), y = ecc * Math.sin(2 * t) * 0 + (R - ecc * 0.3) * Math.sin(t) * 0.86; i ? ctx.lineTo(x * 1.08, y) : ctx.moveTo(x * 1.08, y); }
  ctx.closePath(); ctx.fill();
  // ротор
  const rotA = shaftA / 3;
  const ox = ecc * Math.cos(shaftA), oy = ecc * Math.sin(shaftA);
  const pts = [];
  for (let k = 0; k < 3; k++) { const a = rotA + k * Math.PI * 2 / 3; pts.push([ox + Math.cos(a) * R * 0.98, oy + Math.sin(a) * R * 0.74]); }
  // камера сгорания справа: свечение
  const firePh = ((shaftA / D2R) % 360 + 360) % 360;
  if (firePh < 130 && st.rpm > 50 && !st.off && !st.blown) {
    const k = 1 - firePh / 130;
    const gr = ctx.createRadialGradient(R * 0.8, 0, 2, R * 0.8, 0, R * 0.9);
    gr.addColorStop(0, 'rgba(255,220,130,' + 0.9 * k + ')'); gr.addColorStop(1, 'rgba(255,80,20,0)');
    ctx.fillStyle = gr; ctx.fillRect(0, -R, R * 1.3, R * 2);
  }
  ctx.fillStyle = metalGrad(ctx, -R, -R, R, R, '#c3c9d1');
  ctx.beginPath();
  for (let k = 0; k < 3; k++) {
    const p = pts[k], q = pts[(k + 1) % 3];
    const mx = (p[0] + q[0]) / 2, my = (p[1] + q[1]) / 2;
    const bx = mx + (mx - ox) * 0.18, by = my + (my - oy) * 0.18;
    if (k === 0) ctx.moveTo(p[0], p[1]);
    ctx.quadraticCurveTo(bx, by, q[0], q[1]);
  }
  ctx.closePath(); ctx.fill();
  // внутренняя шестерня и вал
  ctx.fillStyle = '#7a8089'; ctx.beginPath(); ctx.arc(ox, oy, R * 0.3, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = '#d5dae0'; ctx.beginPath(); ctx.arc(0, 0, R * 0.16, 0, Math.PI * 2); ctx.fill();
  // апекс-уплотнения
  ctx.fillStyle = '#f2a93b'; pts.forEach(p => { ctx.beginPath(); ctx.arc(p[0], p[1], R * 0.04, 0, Math.PI * 2); ctx.fill(); });
  // свечи справа
  ctx.fillStyle = '#eee'; ctx.fillRect(R * 1.12, -R * 0.25, R * 0.25, R * 0.08); ctx.fillRect(R * 1.12, R * 0.15, R * 0.25, R * 0.08);
  if (!small) {
    ctx.fillStyle = 'rgba(255,255,255,0.5)'; ctx.font = (R * 0.16) + 'px sans-serif'; ctx.textAlign = 'center';
    ctx.fillText('впуск', -R * 1.2, -R * 1.15); ctx.fillText('выпуск', -R * 1.1, R * 1.3); ctx.fillText('свечи', R * 1.25, -R * 0.4);
  }
  if (hl === 'block' || hl === 'crank') { ctx.strokeStyle = COL.amber; ctx.lineWidth = 2.5 * lw; ctx.beginPath(); ctx.ellipse(0, 0, R * 1.55, R * 1.35, 0, 0, Math.PI * 2); ctx.stroke(); }
}
function drawRotaryFront(ctx, e, W, H, st, parts) {
  const R = Math.min(W, H) * 0.26 * clamp(Math.pow(e.rotorVol / 654, 0.25), 0.8, 1.2);
  const cx = W / 2 - (hasTurbo(e) ? R * 0.35 : 0), cy = H / 2 + 4;
  ctx.save(); ctx.translate(cx, cy);
  const egt = clamp(st.egt || 0, 0, 1);
  // выпуск
  ctx.strokeStyle = heatColor(egt); ctx.lineWidth = clamp(e.exhDia * 0.4, 10, 30); ctx.lineCap = 'round';
  ctx.beginPath(); ctx.moveTo(-R * 0.9, R * 1.0); ctx.quadraticCurveTo(-R * 0.6, R * 1.6, R * 1.6, R * 1.55); ctx.stroke();
  if (st.highlight === 'exhaust') { ctx.strokeStyle = COL.amber; ctx.lineWidth = 2; ctx.strokeRect(-R, R * 0.9, R * 2.7, R * 0.8); }
  drawRotorUnit(ctx, R, R * 0.13, (st.crank || 0) * D2R, st, e, 0, st.highlight, 1);
  // впуск
  ctx.strokeStyle = '#6f767f'; ctx.lineWidth = clamp(e.throttle * 0.4, 10, 30);
  ctx.beginPath(); ctx.moveTo(-R * 1.0, -R * 0.9); ctx.quadraticCurveTo(-R * 1.3, -R * 1.6, -R * 0.2, -R * 1.65); ctx.stroke();
  drawFilter(ctx, e, -R * 0.2, -R * 1.85);
  if (hasTurbo(e)) drawTurbo(ctx, R * 1.75, R * 0.9, clamp(e.turboSize * 0.45, 16, 40), (st.t || 0) * (6 + (st.spool || 0) * 60), st.spool || 0, egt, e, st.highlight === 'boost', 1);
  if (hasSC(e)) { ctx.fillStyle = '#a7aeb7'; rrect(ctx, -R * 0.3, -R * 2.0, R * 0.9, R * 0.4, 8); ctx.fill(); }
  ctx.restore();
  if (parts && st.emit) emitExhaust(parts, cx + R * 1.6, cy + R * 1.55, st, 1, 1, 0.2);
}
function drawRotarySide(ctx, e, W, H, st, parts) {
  const n = e.cyl;
  const R = Math.min(W / (n * 3.3 + 0.6), H * 0.3);
  ctx.save(); ctx.translate(W / 2 - (n - 1) * R * 1.6, H / 2);
  for (let i = 0; i < n; i++) {
    ctx.save(); ctx.translate(i * R * 3.2, 0);
    drawRotorUnit(ctx, R, R * 0.13, (st.crank || 0) * D2R + i * Math.PI * 2 / n, st, e, i, st.highlight, 1, true);
    ctx.fillStyle = 'rgba(255,255,255,0.4)'; ctx.font = (R * 0.3) + 'px sans-serif'; ctx.textAlign = 'center'; ctx.fillText('Ротор ' + (i + 1), 0, R * 1.75);
    ctx.restore();
  }
  ctx.strokeStyle = '#9aa1aa'; ctx.lineWidth = R * 0.18; ctx.beginPath(); ctx.moveTo(-R * 1.8, 0); ctx.lineTo((n - 1) * R * 3.2 + R * 1.8, 0); ctx.stroke();
  ctx.restore();
}

// ---------- ПРИБОРЫ ----------
function drawGauge(ctx, cx, cy, R, value, min, max, opt) {
  opt = opt || {};
  const a0 = Math.PI * 0.75, a1 = Math.PI * 2.25;
  const toA = (v) => a0 + (a1 - a0) * clamp((v - min) / (max - min), 0, 1);
  ctx.save();
  // корпус
  const bg = ctx.createRadialGradient(cx, cy - R * 0.3, R * 0.1, cx, cy, R);
  bg.addColorStop(0, '#22252b'); bg.addColorStop(1, '#101114');
  ctx.fillStyle = bg; ctx.beginPath(); ctx.arc(cx, cy, R, 0, Math.PI * 2); ctx.fill();
  ctx.strokeStyle = '#3a3f47'; ctx.lineWidth = R * 0.05; ctx.stroke();
  // красная зона
  if (opt.red != null) {
    ctx.strokeStyle = 'rgba(255,77,61,0.85)'; ctx.lineWidth = R * 0.09;
    ctx.beginPath(); ctx.arc(cx, cy, R * 0.86, toA(opt.red), a1); ctx.stroke();
  }
  if (opt.zones) opt.zones.forEach(([from, to, c]) => { ctx.strokeStyle = c; ctx.lineWidth = R * 0.09; ctx.beginPath(); ctx.arc(cx, cy, R * 0.86, toA(from), toA(to)); ctx.stroke(); });
  // деления
  const major = opt.major || (max - min) / 8, minor = opt.minor || major / 2;
  ctx.fillStyle = COL.text; ctx.font = '600 ' + Math.round(R * 0.16) + 'px system-ui, sans-serif'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  for (let v = min; v <= max + 1e-6; v += minor) {
    const a = toA(v), isMaj = Math.abs((v - min) / major - Math.round((v - min) / major)) < 1e-6;
    ctx.strokeStyle = isMaj ? '#e7e9ec' : '#6b727c'; ctx.lineWidth = isMaj ? R * 0.03 : R * 0.015;
    const r1 = R * (isMaj ? 0.72 : 0.78), r2 = R * 0.82;
    ctx.beginPath(); ctx.moveTo(cx + Math.cos(a) * r1, cy + Math.sin(a) * r1); ctx.lineTo(cx + Math.cos(a) * r2, cy + Math.sin(a) * r2); ctx.stroke();
    if (isMaj) ctx.fillText(opt.fmt ? opt.fmt(v) : String(Math.round(v)), cx + Math.cos(a) * R * 0.58, cy + Math.sin(a) * R * 0.58);
  }
  ctx.fillStyle = COL.dim; ctx.font = Math.round(R * 0.11) + 'px system-ui, sans-serif';
  if (opt.label) ctx.fillText(opt.label, cx, cy + R * 0.32);
  if (opt.digital) { ctx.fillStyle = opt.digitalColor || COL.amber; ctx.font = '700 ' + Math.round(R * 0.22) + 'px ui-monospace, monospace'; ctx.fillText(opt.digital, cx, cy + R * 0.58); }
  // стрелка
  const a = toA(value);
  ctx.strokeStyle = opt.needle || '#ff5a3d'; ctx.lineWidth = R * 0.045; ctx.lineCap = 'round';
  ctx.beginPath(); ctx.moveTo(cx - Math.cos(a) * R * 0.12, cy - Math.sin(a) * R * 0.12); ctx.lineTo(cx + Math.cos(a) * R * 0.8, cy + Math.sin(a) * R * 0.8); ctx.stroke();
  ctx.fillStyle = '#2c3038'; ctx.beginPath(); ctx.arc(cx, cy, R * 0.09, 0, Math.PI * 2); ctx.fill();
  if (opt.lamp) { ctx.fillStyle = opt.lamp; ctx.beginPath(); ctx.arc(cx, cy - R * 0.38, R * 0.07, 0, Math.PI * 2); ctx.fill(); }
  ctx.restore();
}

function drawBar(ctx, x, y, w, h, frac, label, valueText, color) {
  ctx.fillStyle = '#101114'; rrect(ctx, x, y, w, h, 3); ctx.fill();
  ctx.fillStyle = color; rrect(ctx, x, y, Math.max(2, w * clamp(frac, 0, 1)), h, 3); ctx.fill();
  ctx.fillStyle = COL.dim; ctx.font = '10px system-ui, sans-serif'; ctx.textAlign = 'left'; ctx.textBaseline = 'bottom';
  ctx.fillText(label, x, y - 2);
  ctx.fillStyle = COL.text; ctx.textAlign = 'right'; ctx.fillText(valueText, x + w, y - 2);
}

// ---------- ГРАФИК МОЩНОСТИ ----------
// curves: [{pts:[{rpm,nm,hp}], dash, alpha}]
function drawCurves(ctx, W, H, curves, opt) {
  opt = opt || {};
  ctx.save();
  ctx.fillStyle = opt.bg || COL.panel; ctx.fillRect(0, 0, W, H);
  const pad = { l: 34, r: 34, t: 10, b: 20 };
  let maxR = 1000, maxHp = 50, maxNm = 50;
  curves.forEach(c => c.pts.forEach(p => { maxR = Math.max(maxR, p.rpm); maxHp = Math.max(maxHp, p.hp); maxNm = Math.max(maxNm, p.nm); }));
  if (opt.maxRpm) maxR = Math.max(maxR, opt.maxRpm);
  const niceMax = (v) => { const m = Math.pow(10, Math.floor(Math.log10(v))); const f = v / m; return (f <= 1.5 ? 1.5 : f <= 2 ? 2 : f <= 3 ? 3 : f <= 5 ? 5 : f <= 7.5 ? 7.5 : 10) * m; };
  const mx = Math.ceil(maxR / 1000) * 1000, mh = niceMax(maxHp * 1.08), mn = niceMax(maxNm * 1.08);
  const X = (r) => pad.l + (W - pad.l - pad.r) * r / mx;
  const Yh = (v) => H - pad.b - (H - pad.t - pad.b) * v / mh;
  const Yn = (v) => H - pad.b - (H - pad.t - pad.b) * v / mn;
  ctx.strokeStyle = 'rgba(255,255,255,0.06)'; ctx.lineWidth = 1;
  ctx.font = '10px system-ui, sans-serif'; ctx.textBaseline = 'middle';
  for (let i = 0; i <= 4; i++) {
    const y = pad.t + (H - pad.t - pad.b) * i / 4;
    ctx.beginPath(); ctx.moveTo(pad.l, y); ctx.lineTo(W - pad.r, y); ctx.stroke();
    ctx.fillStyle = COL.amber; ctx.textAlign = 'right'; ctx.fillText(Math.round(mn * (1 - i / 4)), pad.l - 4, y);
    ctx.fillStyle = '#58c4ff'; ctx.textAlign = 'left'; ctx.fillText(Math.round(mh * (1 - i / 4)), W - pad.r + 4, y);
  }
  const stepR = mx > 12000 ? 2000 : 1000;
  ctx.fillStyle = COL.dim; ctx.textAlign = 'center'; ctx.textBaseline = 'top';
  for (let r = 0; r <= mx; r += stepR) { ctx.fillText(String(r / 1000), X(r), H - pad.b + 4); ctx.beginPath(); ctx.moveTo(X(r), pad.t); ctx.lineTo(X(r), H - pad.b); ctx.stroke(); }
  ctx.textAlign = 'right'; ctx.fillText('×1000 об/мин', W - pad.r, H - pad.b + 4 - 12 < pad.t ? pad.t : H - pad.b - 12);
  curves.forEach(c => {
    ctx.globalAlpha = c.alpha == null ? 1 : c.alpha;
    ctx.setLineDash(c.dash ? [5, 4] : []);
    ctx.lineWidth = c.width || 2.2; ctx.lineJoin = 'round';
    ctx.strokeStyle = COL.amber; ctx.beginPath(); c.pts.forEach((p, i) => i ? ctx.lineTo(X(p.rpm), Yn(p.nm)) : ctx.moveTo(X(p.rpm), Yn(p.nm))); ctx.stroke();
    ctx.strokeStyle = '#58c4ff'; ctx.beginPath(); c.pts.forEach((p, i) => i ? ctx.lineTo(X(p.rpm), Yh(p.hp)) : ctx.moveTo(X(p.rpm), Yh(p.hp))); ctx.stroke();
    if (c.boost) {
      ctx.strokeStyle = 'rgba(180,140,255,0.8)'; ctx.lineWidth = 1.2; ctx.setLineDash([2, 3]);
      const mb = Math.max(1, ...c.pts.map(p => p.boost || 0));
      ctx.beginPath(); c.pts.forEach((p, i) => { const y = H - pad.b - (H - pad.t - pad.b) * 0.35 * (p.boost || 0) / mb; i ? ctx.lineTo(X(p.rpm), y) : ctx.moveTo(X(p.rpm), y); }); ctx.stroke();
    }
  });
  ctx.setLineDash([]); ctx.globalAlpha = 1;
  if (opt.marker != null) { ctx.strokeStyle = 'rgba(255,255,255,0.5)'; ctx.beginPath(); ctx.moveTo(X(opt.marker), pad.t); ctx.lineTo(X(opt.marker), H - pad.b); ctx.stroke(); }
  if (opt.redline) { ctx.fillStyle = 'rgba(255,77,61,0.12)'; ctx.fillRect(X(opt.redline), pad.t, W - pad.r - X(opt.redline), H - pad.t - pad.b); }
  ctx.fillStyle = COL.amber; ctx.textAlign = 'left'; ctx.textBaseline = 'top'; ctx.font = '600 10px system-ui, sans-serif';
  ctx.fillText('Н·м', pad.l + 4, pad.t + 2);
  ctx.fillStyle = '#58c4ff'; ctx.textAlign = 'right'; ctx.fillText('л.с.', W - pad.r - 4, pad.t + 2);
  ctx.restore();
}

// ---------- МАШИНА (вид сбоку) ----------
const BODY_SHAPE = {
  hatch:    { L: 3.9, wb: [0.72, 3.18], pts: [[0, .32], [0, .62], [.12, .74], [1.05, .86], [1.6, 1.36], [2.9, 1.42], [3.72, 1.24], [3.9, .98], [3.9, .32]], win: [[1.7, 1.32], [2.82, 1.37], [3.58, 1.2], [3.68, .98], [1.42, .92]] },
  sedan:    { L: 4.6, wb: [0.88, 3.6], pts: [[0, .32], [0, .62], [.15, .72], [1.45, .86], [2.0, 1.38], [3.15, 1.4], [3.8, .98], [4.55, .92], [4.6, .62], [4.6, .32]], win: [[2.08, 1.33], [3.08, 1.35], [3.6, .98], [1.8, .92]] },
  coupe:    { L: 4.4, wb: [0.85, 3.45], pts: [[0, .3], [0, .56], [.15, .66], [1.5, .8], [2.05, 1.26], [2.95, 1.3], [3.9, .92], [4.4, .86], [4.4, .3]], win: [[2.12, 1.22], [2.9, 1.25], [3.6, .94], [1.85, .86]] },
  wagon:    { L: 4.7, wb: [0.9, 3.7], pts: [[0, .33], [0, .64], [.15, .74], [1.4, .88], [1.95, 1.42], [4.45, 1.42], [4.68, 1.0], [4.7, .33]], win: [[2.04, 1.36], [4.4, 1.37], [4.55, .98], [1.75, .94]] },
  roadster: { L: 4.0, wb: [0.82, 3.2], pts: [[0, .3], [0, .54], [.15, .64], [1.55, .78], [1.85, 1.08], [1.95, 1.08], [2.1, .84], [3.5, .86], [4.0, .84], [4.0, .3]], win: [[1.85, 1.05], [1.93, 1.05], [1.98, .84], [1.68, .82]] },
  pickup:   { L: 5.2, wb: [1.0, 4.1], pts: [[0, .45], [0, .85], [.15, .98], [1.5, 1.1], [2.0, 1.75], [2.9, 1.78], [3.05, 1.16], [5.2, 1.16], [5.2, .45]], win: [[2.08, 1.7], [2.85, 1.72], [2.95, 1.2], [1.8, 1.16]] },
  super:    { L: 4.5, wb: [0.95, 3.6], pts: [[0, .26], [0, .44], [.2, .54], [1.3, .7], [1.85, 1.12], [2.65, 1.14], [4.2, .86], [4.5, .78], [4.5, .26]], win: [[1.92, 1.08], [2.6, 1.1], [3.05, .92], [1.7, .76]] },
  dragster: { L: 6.0, wb: [0.5, 5.2], pts: [[0, .3], [0, .38], [3.6, .5], [4.1, .9], [4.6, .9], [4.8, .6], [6.0, .55], [6.0, .3]], win: [[4.15, .88], [4.5, .88], [4.6, .62], [4.0, .6]] },
};
function drawCar(ctx, c, W, H, opt) {
  opt = opt || {};
  const B = BODY_SHAPE[c.body] || BODY_SHAPE.hatch;
  const st = opt.stats;
  const scale = opt.scale || Math.min(W * 0.86 / B.L, H * 0.7 / 2.0);
  const ox = opt.x != null ? opt.x : (W - B.L * scale) / 2, groundY = opt.ground != null ? opt.ground : H * 0.84;
  const rWheel = st ? st.rWheel : 0.3;
  const ride = (c.rideHeight || 0) / 1000;
  const lift = ride + (rWheel - 0.3) * 0.6;
  const X = (x) => opt.flip ? ox + (B.L - x) * scale : ox + x * scale;
  const Y = (y) => groundY - (y + lift) * scale;
  ctx.save();
  // тень
  ctx.fillStyle = 'rgba(0,0,0,0.35)'; ctx.beginPath(); ctx.ellipse(X(B.L / 2), groundY + 2, B.L * scale * 0.5, 6, 0, 0, Math.PI * 2); ctx.fill();
  // кузов
  const body = c.color || '#c8312b';
  const gr = ctx.createLinearGradient(0, Y(1.5), 0, Y(0.3));
  gr.addColorStop(0, shade(body, 0.25)); gr.addColorStop(0.55, body); gr.addColorStop(1, shade(body, -0.45));
  ctx.fillStyle = gr;
  smoothPoly(ctx, B.pts.map(p => [X(p[0]), Y(p[1])]));
  ctx.fill();
  // колёсные арки
  ctx.fillStyle = '#0d0e10';
  B.wb.forEach(wx => { ctx.beginPath(); ctx.arc(X(wx), Y(rWheel - lift + ride * 0 + 0.0) , (rWheel + 0.05) * scale, Math.PI, 0); ctx.fill(); });
  // полоса
  if (c.stripe) { ctx.fillStyle = 'rgba(255,255,255,0.85)'; ctx.fillRect(Math.min(X(0.2), X(B.L - 0.2)), Y(0.62), Math.abs(X(B.L - 0.2) - X(0.2)), 0.06 * scale); }
  // окна
  if (B.win) { ctx.fillStyle = 'rgba(25,35,48,0.92)'; smoothPoly(ctx, B.win.map(p => [X(p[0]), Y(p[1])]), true); ctx.fill(); ctx.fillStyle = 'rgba(255,255,255,0.08)'; }
  // фары
  ctx.fillStyle = '#ffe9b0'; ctx.fillRect(Math.min(X(0.02), X(0.14)), Y(0.66), Math.abs(X(0.14) - X(0.02)), 0.07 * scale);
  ctx.fillStyle = '#c21d1d'; ctx.fillRect(Math.min(X(B.L - 0.1), X(B.L)), Y(0.78), Math.abs(X(B.L) - X(B.L - 0.1)), 0.07 * scale);
  // аэро
  if (c.spoiler !== 'none') {
    const top = B.pts.reduce((a, p) => p[0] > B.L * 0.8 ? Math.max(a, p[1]) : a, 0);
    const h = ({ lip: 0.03, wing: 0.22, big: 0.38 })[c.spoiler];
    ctx.fillStyle = '#18191c';
    if (c.spoiler === 'lip') ctx.fillRect(Math.min(X(B.L - 0.35), X(B.L - 0.02)), Y(top + 0.03), Math.abs(X(B.L - 0.02) - X(B.L - 0.35)), 0.04 * scale);
    else {
      ctx.fillRect(Math.min(X(B.L - 0.35), X(B.L - 0.3)), Y(top + h), 0.04 * scale, h * scale);
      rrect(ctx, Math.min(X(B.L - 0.55), X(B.L)), Y(top + h + 0.05), Math.abs(X(B.L) - X(B.L - 0.55)), 0.07 * scale, 3); ctx.fill();
    }
  }
  if (c.splitter) { ctx.fillStyle = '#18191c'; ctx.fillRect(Math.min(X(-0.05), X(0.5)), Y(0.3), Math.abs(X(0.5) - X(-0.05)), 0.04 * scale); }
  if (c.diffuser) { ctx.fillStyle = '#18191c'; ctx.fillRect(Math.min(X(B.L - 0.6), X(B.L)), Y(0.32), Math.abs(X(B.L) - X(B.L - 0.6)), 0.05 * scale); }
  // колёса
  B.wb.forEach((wx, i) => drawWheel(ctx, X(wx), groundY - rWheel * scale, rWheel * scale, c, (opt.wheelRot || 0), c.body === 'dragster' && i === 0 ? 0.55 : 1, c.body === 'dragster' && i === 1 ? 1.35 : 1));
  ctx.restore();
  return { scale, ox, groundY, B };
}
function smoothPoly(ctx, pts, sharp) {
  ctx.beginPath();
  if (sharp) { pts.forEach((p, i) => i ? ctx.lineTo(p[0], p[1]) : ctx.moveTo(p[0], p[1])); ctx.closePath(); return; }
  const n = pts.length;
  ctx.moveTo((pts[0][0] + pts[n - 1][0]) / 2, (pts[0][1] + pts[n - 1][1]) / 2);
  for (let i = 0; i < n; i++) { const p = pts[i], q = pts[(i + 1) % n]; ctx.quadraticCurveTo(p[0], p[1], (p[0] + q[0]) / 2, (p[1] + q[1]) / 2); }
  ctx.closePath();
}
function drawWheel(ctx, x, y, r, c, rot, k, kk) {
  r = r * (k || 1) * (kk || 1);
  y = y - (r - r / ((k || 1) * (kk || 1)));
  const rimR = r * (c.rim * 25.4 / 2) / (c.rim * 25.4 / 2 + c.tireWidth * c.profile / 100);
  ctx.fillStyle = '#121315'; ctx.beginPath(); ctx.arc(x, y, r, 0, Math.PI * 2); ctx.fill();
  if (c.tire === 'drag' || c.tire === 'slick') { ctx.strokeStyle = '#26282c'; ctx.lineWidth = 1; ctx.beginPath(); ctx.arc(x, y, r * 0.92, 0, Math.PI * 2); ctx.stroke(); }
  else { ctx.strokeStyle = '#26282c'; ctx.lineWidth = r * 0.05; ctx.setLineDash([r * 0.12, r * 0.08]); ctx.beginPath(); ctx.arc(x, y, r * 0.93, rot, rot + Math.PI * 2); ctx.stroke(); ctx.setLineDash([]); }
  const style = c.rimStyle;
  ctx.fillStyle = style === 'steel' ? '#3c4046' : style === 'black' ? '#1f2125' : style === 'gold' ? '#c9a34a' : '#c6ccd3';
  ctx.beginPath(); ctx.arc(x, y, rimR, 0, Math.PI * 2); ctx.fill();
  ctx.save(); ctx.translate(x, y); ctx.rotate(rot);
  const spokes = style === 'steel' ? 0 : style === 'forged' ? 10 : style === 'mesh' ? 14 : 5;
  ctx.fillStyle = '#15171a';
  if (spokes === 0) { for (let i = 0; i < 6; i++) { const a = i / 6 * Math.PI * 2; ctx.beginPath(); ctx.arc(Math.cos(a) * rimR * 0.6, Math.sin(a) * rimR * 0.6, rimR * 0.12, 0, Math.PI * 2); ctx.fill(); } }
  else for (let i = 0; i < spokes; i++) {
    const a = i / spokes * Math.PI * 2, a2 = a + Math.PI / spokes;
    ctx.beginPath(); ctx.moveTo(Math.cos(a + 0.12) * rimR * 0.25, Math.sin(a + 0.12) * rimR * 0.25);
    ctx.lineTo(Math.cos(a + 0.05) * rimR * 0.88, Math.sin(a + 0.05) * rimR * 0.88);
    ctx.lineTo(Math.cos(a2 - 0.05) * rimR * 0.88, Math.sin(a2 - 0.05) * rimR * 0.88);
    ctx.lineTo(Math.cos(a2 - 0.12) * rimR * 0.25, Math.sin(a2 - 0.12) * rimR * 0.25); ctx.closePath(); ctx.fill();
  }
  ctx.fillStyle = '#8b939e'; ctx.beginPath(); ctx.arc(0, 0, rimR * 0.18, 0, Math.PI * 2); ctx.fill();
  ctx.restore();
}
