'use strict';
// ================= ФИЗИКА ДВИГАТЕЛЯ И МАШИНЫ =================
// Упрощённая, но связная модель: наполнение (VE), степень сжатия, наддув с охлаждением воздуха,
// состав смеси, угол зажигания, детонация, трение, механические пределы деталей.

const FUELS = {
  ai92:   { name: 'АИ-92', oct: 92, kLim: 11.0, stoich: 14.7, pow: 1.00, flow: 1.00, price: 0 },
  ai95:   { name: 'АИ-95', oct: 95, kLim: 11.6, stoich: 14.7, pow: 1.00, flow: 1.00, price: 150 },
  ai98:   { name: 'АИ-98', oct: 98, kLim: 12.2, stoich: 14.7, pow: 1.01, flow: 1.00, price: 400 },
  race:   { name: 'Гоночный 102', oct: 102, kLim: 13.0, stoich: 14.6, pow: 1.02, flow: 1.00, price: 1500 },
  e85:    { name: 'Этанол E85', oct: 105, kLim: 14.2, stoich: 9.8, pow: 1.05, flow: 1.42, price: 900 },
  meth:   { name: 'Метанол', oct: 112, kLim: 15.5, stoich: 6.4, pow: 1.08, flow: 2.25, price: 2500 },
  diesel: { name: 'Дизель', oct: 0, kLim: 99, stoich: 14.5, pow: 0.82, flow: 0.85, price: 0, diesel: true },
};

// Прочность деталей: предел средней скорости поршня (м/с) и давления (бар эфф.)
const STRENGTH = {
  crank:   { cast: { vp: 21, p: 30 }, forged: { vp: 25, p: 42 }, billet: { vp: 29, p: 55 } },
  rods:    { cast: { vp: 21, p: 26 }, forged: { vp: 25, p: 40 }, titanium: { vp: 28, p: 46 }, billet: { vp: 27, p: 60 } },
  pistons: { cast: { vp: 21, p: 24 }, hyper: { vp: 23, p: 28 }, forged: { vp: 27, p: 42 }, billet: { vp: 28, p: 55 } },
  block:   { iron: { p: 45, w: 1.0 }, cgi: { p: 60, w: 0.92 }, alu: { p: 32, w: 0.55 }, alusil: { p: 36, w: 0.5 }, billet: { p: 70, w: 0.62 } },
};

const BANKS = { inline: 1, v: 2, boxer: 2, vr: 2, w: 4, radial: 0, rotary: 1 };
const CYL_OPTIONS = {
  inline: [1, 2, 3, 4, 5, 6, 8], v: [2, 4, 6, 8, 10, 12, 16], boxer: [2, 4, 6, 8, 12],
  vr: [5, 6], w: [8, 12, 16, 18], radial: [3, 5, 7, 9], rotary: [1, 2, 3, 4],
};

function clamp(x, a, b) { return x < a ? a : x > b ? b : x; }
function smooth(a, b, x) { const t = clamp((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); }
function lerp(a, b, t) { return a + (b - a) * t; }

function defaultEngine() {
  return {
    name: 'Новый двигатель', type: 'inline', cyl: 4, vAngle: 90, crankPlane: 'cross', cycle: 4,
    block: 'iron', deck: 'open', bore: 81, stroke: 77, rotorVol: 654,
    crank: 'cast', rods: 'cast', rodLen: 130, pistons: 'cast', cr: 10.0, flywheel: 'stock', balance: false,
    valvetrain: 'dohc', valves: 4, camDur: 240, camLift: 9.5, vvt: 'none', springs: 'stock', head: 'alu', porting: 'stock',
    ports: 'stock', tunedPipe: false,
    airFilter: 'stock', manifold: 'stock', throttle: 56,
    induction: 'na', turboLayout: 'single', turboSize: 50, bearing: 'journal', wastegate: 'internal', intercooler: 'none',
    bov: true, antilag: false, nitrous: 0, coils: 'stock',
    fuel: 'ai95', fuelSystem: 'mpi', injectors: 250, pump: 'stock',
    ecu: 'stock', timing: 28, lambda: 0.88, boost: 0.8, limiter: 6800, idle: 850, knockSensor: true, launch: 4000,
    exhManifold: 'cast', exhDia: 50, cat: true, muffler: 'stock',
    radiator: 'stock', oilCooler: false, oilSystem: 'wet', oil: 'std',
    coverColor: 'black', health: 100, blown: null, lastDyno: null, built: null,
  };
}

// Поля, которые относятся к настройке ЭБУ (меняются бесплатно, без пересборки)
const TUNE_KEYS = ['timing', 'lambda', 'boost', 'limiter', 'idle', 'knockSensor', 'launch', 'coverColor', 'name'];

function isForced(e) { return e.induction !== 'na'; }
function hasTurbo(e) { return e.induction === 'turbo' || e.induction === 'twincharge' || e.induction === 'eturbo'; }
function hasSC(e) { return e.induction.startsWith('sc_') || e.induction === 'twincharge'; }
function turboCount(e) { return !hasTurbo(e) ? 0 : ({ single: 1, twin: 2, sequential: 2, quad: 4 })[e.turboLayout] || 1; }
function isRotary(e) { return e.type === 'rotary'; }
function cylLabel(e) { return isRotary(e) ? 'Роторов' : 'Цилиндров'; }

// Рабочий объём, л
function displacement(e) {
  if (isRotary(e)) return e.rotorVol * e.cyl / 1000;
  return Math.PI / 4 * e.bore * e.bore * e.stroke * e.cyl / 1e6;
}

function layoutCode(e) {
  const n = e.cyl;
  switch (e.type) {
    case 'inline': return 'R' + n;
    case 'v': return 'V' + n;
    case 'boxer': return 'B' + n;
    case 'vr': return 'VR' + n;
    case 'w': return 'W' + n;
    case 'radial': return n + '-звезда';
    case 'rotary': return n + '-ротор';
  }
  return '?';
}

// Порядок работы цилиндров
function firingOrder(e) {
  const n = e.cyl, t = e.type;
  const T = {
    1: [1], 2: [1, 2], 3: [1, 2, 3], 4: [1, 3, 4, 2], 5: [1, 2, 4, 5, 3], 6: [1, 5, 3, 6, 2, 4], 8: [1, 6, 2, 5, 8, 3, 7, 4],
  };
  if (isRotary(e)) return Array.from({ length: n }, (_, i) => i + 1);
  if (t === 'radial') { const a = []; for (let i = 1; i <= n; i += 2) a.push(i); for (let i = 2; i <= n; i += 2) a.push(i); return a; }
  if (t === 'inline') return T[n] || seq(n);
  if (t === 'boxer') return ({ 2: [1, 2], 4: [1, 3, 2, 4], 6: [1, 6, 2, 4, 3, 5], 8: [1, 8, 3, 6, 4, 5, 2, 7], 12: [1, 12, 5, 8, 3, 10, 6, 7, 2, 11, 4, 9] })[n] || seq(n);
  if (t === 'v' && n === 8) return e.crankPlane === 'flat' ? [1, 5, 3, 7, 4, 8, 2, 6] : [1, 8, 4, 3, 6, 5, 7, 2];
  const V = { 2: [1, 2], 4: [1, 3, 2, 4], 6: [1, 4, 2, 5, 3, 6], 10: [1, 6, 5, 10, 2, 7, 3, 8, 4, 9], 12: [1, 7, 5, 11, 3, 9, 6, 12, 2, 8, 4, 10] };
  if ((t === 'v' || t === 'vr') && V[n]) return V[n];
  if (t === 'vr' && n === 5) return [1, 2, 4, 5, 3];
  // общий случай: чередуем ряды
  const half = n / 2, a = [];
  for (let i = 0; i < half; i++) { a.push(i + 1); a.push(i + 1 + half); }
  return a;
  function seq(k) { return Array.from({ length: k }, (_, i) => i + 1); }
}

// Фаза (град. коленвала в 720°-цикле) для каждого цилиндра по порядку работы
function cylPhases(e) {
  const fo = firingOrder(e), n = e.cyl, cycleDeg = (e.cycle === 2 || isRotary(e)) ? 360 : 720;
  const ph = new Array(n).fill(0);
  fo.forEach((c, k) => { ph[c - 1] = cycleDeg * k / n; });
  // неравномерная работа: крестообразный V8 «булькает» за счёт выпуска по рядам — фазы равномерные, но звук рваный
  return ph;
}

function balanceInfo(e) {
  const n = e.cyl, t = e.type;
  let score = 70, text = 'Средняя';
  if (isRotary(e)) { score = 96; text = 'Отличная (ротор)'; }
  else if (t === 'inline') {
    score = ({ 1: 20, 2: 35, 3: 55, 4: 62, 5: 75, 6: 98, 8: 94 })[n] || 60;
    if (e.balance && n <= 4) score += 22;
  } else if (t === 'boxer') score = ({ 2: 70, 4: 86, 6: 98, 8: 95, 12: 99 })[n] || 85;
  else if (t === 'v') {
    score = ({ 2: 40, 4: 60, 6: 78, 8: 90, 10: 84, 12: 99, 16: 99 })[n] || 80;
    if (n === 8 && e.crankPlane === 'flat') score = 72;
    if (n === 6 && Math.abs(e.vAngle - 60) < 3) score = 90;
    if (n === 2 && Math.abs(e.vAngle - 90) < 3) score = 70;
  } else if (t === 'vr') score = n === 6 ? 88 : 76;
  else if (t === 'w') score = 92;
  else if (t === 'radial') score = 85;
  if (e.cycle === 2) score = Math.min(100, score + 8);
  score = clamp(score, 0, 100);
  text = score >= 95 ? 'Идеальная' : score >= 85 ? 'Отличная' : score >= 72 ? 'Хорошая' : score >= 55 ? 'Средняя' : score >= 40 ? 'Вибрации' : 'Сильная тряска';
  return { score, text };
}

// ---------- Механические пределы ----------
function limits(e) {
  const strokeM = (isRotary(e) ? 70 : e.stroke) / 1000;
  const rodRatio = isRotary(e) ? 2 : e.rodLen / e.stroke;
  const rr = clamp(0.75 + (rodRatio - 1.4) * 0.6, 0.6, 1.1);
  let vpLim = Math.min(STRENGTH.crank[e.crank].vp, STRENGTH.rods[e.rods].vp, STRENGTH.pistons[e.pistons].vp) * rr;
  if (e.oilSystem === 'dry') vpLim *= 1.04;
  let mech = isRotary(e) ? 9800 * ({ cast: 0.95, forged: 1.05, billet: 1.15 })[e.crank] : vpLim * 60 / (2 * strokeM);
  if (e.cycle === 2) mech *= 1.08;
  let valveFloat = Infinity;
  if (!isRotary(e) && e.cycle === 4) {
    const base = { ohv: 6200, sohc: 7300, dohc: 8300 }[e.valvetrain];
    const sp = { stock: 1, sport: 1.12, race: 1.25, pneumatic: 1.9 }[e.springs];
    const vF = { 2: 0.96, 3: 1.0, 4: 1.06, 5: 1.09 }[e.valves];
    valveFloat = base * sp * vF * (1 - (e.camLift - 10) * 0.022) * Math.pow(86 / Math.max(60, e.bore), 0.15);
  }
  // давление в цилиндре: самая слабая деталь
  const parts = [
    ['pistons', STRENGTH.pistons[e.pistons].p, 'поршни'],
    ['rods', STRENGTH.rods[e.rods].p, 'шатуны'],
    ['crank', STRENGTH.crank[e.crank].p, 'коленвал'],
    ['block', STRENGTH.block[e.block].p * (e.deck === 'closed' ? 1.25 : 1), 'блок цилиндров'],
  ];
  if (isRotary(e)) parts[0] = ['pistons', ({ cast: 28, hyper: 32, forged: 40, billet: 48 })[e.pistons], 'апекс-уплотнения'];
  parts.sort((a, b) => a[1] - b[1]);
  return { mech, valveFloat, vpLim, pLim: parts[0][1], weakest: parts[0][2], rodRatio };
}

// ---------- Наддув ----------
function turboParams(e) {
  const nT = turboCount(e), d = e.turboSize;
  const disp = displacement(e) * (isRotary(e) ? 2 : 1) * (e.cycle === 2 ? 1.6 : 1);
  const perT = disp / Math.max(1, nT);
  let dOn = e.turboLayout === 'sequential' ? d * 0.72 : d;
  let onset = 2000 * Math.pow(dOn / 50, 2) / Math.pow(perT / 2, 0.6);
  if (e.bearing === 'ball') onset *= 0.85;
  if (e.induction === 'eturbo') onset *= 0.4;
  if (e.antilag) onset *= 0.9;
  const qMax = Math.max(1, nT) * 5.3 * d * d;
  const maxPR = 1 + d / 22;
  const tau = (0.25 + Math.pow(d / 50, 2) * 0.55) * (e.bearing === 'ball' ? 0.75 : 1) * (nT > 1 ? 0.85 : 1) * (e.induction === 'eturbo' ? 0.35 : 1);
  return { nT, onset, full: onset * 1.65, qMax, maxPR, tau };
}

// Давление наддува (абс., отношение) и КПД компрессора на оборотах; spool — доля раскрутки турбины 0..1
function boostAt(e, rpm, spool, throttle) {
  const target = 1 + Math.max(0, e.boost) / 1.013;
  const res = { pr: 1, eta: 1, scLoss: 0, choke: 0, spoolStatic: 0, turboPR: 1, scPR: 1 };
  if (!isForced(e)) return res;
  const disp = displacement(e) * (isRotary(e) ? 2 : 1) * (e.cycle === 2 ? 1.6 : 1);
  let pr = 1, eta = 0.72;
  if (hasTurbo(e)) {
    const tp = turboParams(e);
    const tgt = Math.min(target, tp.maxPR);
    const st = smooth(tp.onset, tp.full, rpm);
    res.spoolStatic = st;
    const s = spool == null ? st : spool;
    let tpr = 1 + (tgt - 1) * s;
    const q = disp * rpm * 0.95 * tpr / 2;
    const ratio = q / tp.qMax;
    if (ratio > 0.9) { tpr = 1 + (tpr - 1) * clamp(1 - (ratio - 0.9) * 1.1, 0.25, 1); res.choke = ratio - 0.9; }
    eta = 0.74 - 0.28 * Math.max(0, ratio - 0.85);
    res.turboPR = tpr; pr = tpr;
    if (target > tp.maxPR + 0.01) res.overTarget = true;
  }
  if (hasSC(e)) {
    let spr, seta;
    const t = e.induction === 'twincharge' ? 'sc_roots' : e.induction;
    const thr = throttle == null ? 1 : throttle;
    if (t === 'sc_roots') { spr = 1 + (target - 1) * smooth(700, 2200, rpm); seta = 0.55; }
    else if (t === 'sc_screw') { spr = 1 + (target - 1) * smooth(700, 2000, rpm); seta = 0.68; }
    else { spr = 1 + (target - 1) * Math.pow(clamp(rpm / Math.max(3000, e.limiter), 0, 1.1), 2); seta = 0.72; }
    spr = 1 + (spr - 1) * clamp(thr * 1.3, 0, 1);
    res.scPR = spr;
    if (e.induction === 'twincharge') {
      if (spr > pr) { pr = spr; eta = seta; } else spr = 1; // байпас компрессора при работе турбины
    } else { pr = spr; eta = seta; }
    // мощность на привод нагнетателя, кВт
    if (spr > 1) {
      const qin = disp * rpm * 0.95 * spr / 2 / 60000; // м³/с
      res.scLoss = qin * 1.2 * 1.005 * 300 * (Math.pow(spr, 0.286) - 1) / seta;
    }
  }
  res.pr = pr; res.eta = eta;
  return res;
}

// ---------- Наполнение цилиндров ----------
function veParams(e) {
  let peak, veMax, sigma;
  if (isRotary(e)) {
    peak = { stock: 6500, stage1: 7300, stage2: 8200 }[e.porting] + (e.camDur - 240) * 18;
    veMax = { stock: 0.9, stage1: 0.95, stage2: 1.0 }[e.porting];
    sigma = 0.5;
  } else if (e.cycle === 2) {
    peak = 7000 * Math.sqrt(66 / e.stroke) * ({ stock: 1, stage1: 1.1, stage2: 1.22 }[e.porting]);
    veMax = 0.72 + (e.tunedPipe ? 0.16 : 0) + ({ stock: 0, stage1: 0.03, stage2: 0.06 }[e.porting]);
    sigma = e.tunedPipe ? 0.28 : 0.42;
  } else {
    peak = { ohv: 4300, sohc: 5000, dohc: 5600 }[e.valvetrain];
    peak += (e.camDur - 240) * 28 + ({ 2: 0, 3: 250, 4: 550, 5: 750 })[e.valves];
    peak += ({ stock: 0, stage1: 250, stage2: 600 })[e.porting];
    veMax = ({ 2: 0.86, 3: 0.89, 4: 0.95, 5: 0.96 })[e.valves] + ({ stock: 0, stage1: 0.025, stage2: 0.05 })[e.porting];
    veMax += (e.camLift - 9.5) * 0.008;
    sigma = 0.55 - (e.camDur - 240) * 0.0016;
    if (e.vvt === 'intake') sigma *= 1.18;
    if (e.vvt === 'dual') sigma *= 1.32;
    if (e.vvt === 'vtec') { sigma *= 1.4; veMax += 0.015; }
    peak *= Math.pow(86 / clamp(e.stroke, 40, 140), 0.45) * Math.pow(clamp(e.bore, 40, 140) / 86, 0.15);
  }
  peak += ({ stock: 0, long: -700, short: 700, variable: 0, itb: 900 })[e.manifold];
  veMax += ({ stock: 0, long: 0.015, short: 0.01, variable: 0.025, itb: 0.045 })[e.manifold];
  if (e.manifold === 'variable') sigma *= 1.25;
  veMax += ({ stock: 0, cone: 0.012, ram: 0.022 })[e.airFilter];
  veMax += ({ cast: 0, tubular: 0.02, equal: 0.035 })[e.exhManifold];
  if (e.fuelSystem === 'carb') veMax -= 0.02;
  return { peak: Math.max(1500, peak), veMax, sigma: clamp(sigma, 0.22, 0.9) };
}

function veAt(e, rpm, vp, out) {
  vp = vp || veParams(e);
  const s = vp.peak * vp.sigma;
  const x = (rpm - vp.peak) / (rpm < vp.peak ? s * 1.25 : s * 0.85);
  let ve = vp.veMax * (0.42 + 0.58 * Math.exp(-0.5 * x * x));
  if (rpm < vp.peak) ve = Math.max(ve, vp.veMax * 0.55);
  // ограничение пропускной способностью: дроссель, выпуск, глушитель
  const disp = displacement(e) * (isRotary(e) ? 2 : 1) * (e.cycle === 2 ? 1.6 : 1);
  const flowNeed = disp * rpm / 2; // л/мин при VE=1, атм.
  const thrN = e.manifold === 'itb' ? e.cyl : 1;
  const thrCap = thrN * Math.pow(e.throttle, 2) * 3.4;
  const exhCount = (BANKS[e.type] >= 2 && e.type !== 'vr') ? 2 : 1;
  let exhCap = exhCount * Math.pow(e.exhDia, 2) * 5.0;
  exhCap *= ({ stock: 0.72, sport: 0.9, straight: 1.0, none: 1.06 })[e.muffler] * (e.cat ? 0.88 : 1);
  const boostMul = isForced(e) ? 1.6 : 1;
  const rT = flowNeed * boostMul / thrCap, rE = flowNeed * (isForced(e) ? 1.25 : 1) / exhCap;
  ve /= 1 + 0.12 * Math.pow(rT, 2.2) + 0.12 * Math.pow(rE, 2.2);
  if (out) { out.rT = rT; out.rE = rE; }
  return ve;
}

function mbtTiming(e, rpm, pr) {
  if (FUELS[e.fuel].diesel) return 12 + rpm / 1000;
  let m = 22 + 7 * clamp(rpm / 7000, 0, 1.4) - 6 * (pr - 1) - (e.cr - 10) * 0.8;
  if (e.fuelSystem === 'di') m -= 1;
  if (isRotary(e)) m -= 4;
  if (e.cycle === 2) m -= 8;
  return clamp(m, 4, 40);
}

// ---------- Главная функция: состояние двигателя на оборотах ----------
// opt: { throttle, spool, timingRetard, coolT, nitrous }
function engineAt(e, rpm, opt) {
  opt = opt || {};
  const F = FUELS[e.fuel], diesel = !!F.diesel;
  const thr = opt.throttle == null ? 1 : opt.throttle;
  const disp = displacement(e);
  const lim = opt.lim || limits(e);
  const vp = opt.vp || veParams(e);
  const b = boostAt(e, rpm, opt.spool, thr);
  const out = { rpm, torque: 0, power: 0, boost: b.pr - 1, knock: 0, egt: 0, lambda: e.lambda, flags: [], stress: 0, fuelOk: true, ve: 0, scLoss: b.scLoss };

  // плотность заряда
  const icEff = ({ none: 0, air: 0.72, big: 0.82, water: 0.86 })[e.intercooler] || 0;
  const tr = 1 + (Math.pow(b.pr, 0.286) - 1) / b.eta;
  const trAfter = 1 + (tr - 1) * (1 - (isForced(e) ? icEff : 0));
  let trA = trAfter;
  if (F.flow > 1.3) trA = 1 + (trAfter - 1) * (F.flow > 2 ? 0.45 : 0.75) - (F.flow > 2 ? 0.03 : 0.012); // испарение спирта охлаждает заряд
  let density = b.pr / trA;
  const intakeC = 25 + 300 * (trA - 1);
  out.iat = intakeC;

  let ve = veAt(e, rpm, vp, out);
  if (out.rT > 1.25) out.flags.push('throttle');
  if (out.rE > 1.25) out.flags.push('exhaust');
  if (hasTurbo(e)) ve *= 1 - 0.035 * (b.pr - 1) * (50 / e.turboSize);
  // зависание клапанов
  if (rpm > lim.valveFloat) { ve *= Math.max(0.35, 1 - (rpm - lim.valveFloat) / lim.valveFloat * 4); out.flags.push('float'); }
  // дизель: медленное сгорание на высоких оборотах
  if (diesel && rpm > 3800) ve *= Math.exp(-Math.pow((rpm - 3800) / 1900, 2));
  out.ve = ve;

  // состав смеси: топливная система может не успевать
  let lam = e.lambda;
  const thermal = (1 - Math.pow(e.cr, -0.28)) / (1 - Math.pow(10, -0.28));
  // грубая оценка мощности для расчёта расхода
  const estKW = disp * 7.96 * 15 * ve * density * rpm / 9549 * (isRotary(e) ? 1.5 : 1) * (e.cycle === 2 ? 1.55 : 1);
  if (e.fuelSystem !== 'carb') {
    const nInj = e.cyl * (isRotary(e) ? 2 : 1);
    const need = estKW * (diesel ? 0.19 : 0.245) * 1000 / 60 / 0.74 * F.flow * (0.88 / lam) / nInj / 0.85;
    const pumpCap = ({ stock: 2500, sport: 4500, race: 7500, dual: 13000, mech: 45000 })[e.pump] * (F.flow > 1.5 ? 0.85 : 1);
    const totalNeed = need * nInj * 0.85;
    const cap = Math.min(e.injectors, need > 0 ? e.injectors : 1);
    let ratio = Math.max(need / e.injectors, totalNeed / pumpCap);
    out.injDuty = need / e.injectors;
    if (ratio > 1) { lam = lam * ratio; out.fuelOk = false; out.flags.push('lean'); }
  } else {
    // карбюратор: под наддувом и на высоких оборотах смесь «гуляет»
    lam = lam + 0.04 * Math.sin(rpm / 900) + (b.pr - 1) * 0.06;
  }
  out.lambda = lam;

  let lf;
  if (diesel) lf = lam < 1.15 ? 1 - (1.15 - lam) * 0.2 : 1 - 0.55 * Math.pow(lam - 1.25, 2) - (lam > 1.25 ? 0 : 0);
  else lf = lam < 0.87 ? 1 - 0.9 * Math.pow(0.87 - lam, 2) * 6 : 1 - 2.2 * Math.pow(lam - 0.87, 2) * 4.5;
  lf = clamp(lf, 0.2, 1.02);

  // зажигание и детонация
  const mbt = mbtTiming(e, rpm, b.pr);
  let adv = e.timing - (opt.timingRetard || 0);
  let K = 0, knock = 0;
  if (!diesel) {
    const coolT = opt.coolT == null ? 90 : opt.coolT;
    K = e.cr + 2.6 * (b.pr - 1) * (1 - 0.5 * (isForced(e) ? icEff : 0)) + (adv - mbt) * 0.12 + (lam - 0.88) * 4
      + (coolT - 90) * 0.03 + (e.bore - 86) * 0.015 + Math.max(0, (3000 - rpm) / 3000) * 0.6 * (b.pr > 1.2 ? 1.5 : 0.4)
      - (e.fuelSystem === 'di' ? 0.8 : 0) - (isRotary(e) ? 0.8 : 0) + (opt.nitrous ? 1.2 : 0) + (intakeC - 40) * 0.006;
    knock = Math.max(0, K - F.kLim);
    if (knock > 0 && e.knockSensor) {
      const pull = Math.min(14, knock / 0.12 + 1);
      adv -= pull; out.ecuRetard = pull;
      knock = Math.max(0, knock - pull * 0.12 - 0.1);
    }
  }
  out.knock = knock; out.timing = adv; out.mbt = mbt;
  const tf = clamp(1 - 0.0016 * Math.pow(adv - mbt, 2), 0.3, 1);

  let imep = 15.0 * ve * density * thermal * lf * tf * F.pow * (1 - knock * 0.04);
  if (e.fuelSystem === 'di') imep *= 1.03;
  if (e.fuelSystem === 'tbi') imep *= 0.97;
  if (e.coils === 'stock' && b.pr > 2.4 && !diesel) { imep *= 0.9; out.flags.push('misfire'); }
  if (diesel && e.cr < 14) { imep = 0; out.flags.push('nostart'); }
  if (!diesel && e.cr > 16) { knock += (e.cr - 16) * 1.5; out.knock = knock; }

  // трение
  const n = rpm / 1000;
  let fmep = 0.6 + 0.12 * n + 0.03 * n * n * ((isRotary(e) ? 70 : e.stroke) / 86);
  fmep *= ({ ohv: 0.93, sohc: 0.97, dohc: 1.0 })[e.valvetrain] || 1;
  if (e.oilSystem === 'dry') fmep *= 0.9;
  if (e.oil === 'race') fmep *= 0.96;
  if (diesel) fmep += 0.4;
  if (e.balance) fmep += 0.08;
  fmep *= 1 + Math.max(0, 4 - e.cyl) * 0.0; // без поправки
  const mult = isRotary(e) ? 1.5 : (e.cycle === 2 ? 1.55 : 1);

  const tInd = 7.96 * imep * disp * mult;
  const tFric = 7.96 * fmep * disp * mult;
  out.tFric = tFric; out.tInd = tInd;

  // открытие дросселя
  const map = clamp(0.14 + 0.86 * (1 - Math.pow(1 - thr, 2.3)) * (1 - 0.3 * (1 - thr) * rpm / 8000), 0.1, 1);
  let tq = tInd * map - tFric;
  // нагнетатель отбирает мощность
  if (b.scLoss > 0 && rpm > 0) tq -= b.scLoss * 9549 / rpm;
  // закись азота
  if (opt.nitrous && e.nitrous > 0 && rpm > 2500 && thr > 0.8) tq += e.nitrous * 735.5 / (rpm * Math.PI / 30) * 0.9;
  out.torque = tq;
  out.power = tq * rpm / 7023;
  out.map = map;

  // нагрузка на детали
  out.stress = imep * map * (opt.nitrous && e.nitrous ? 1 + e.nitrous / (disp * 120) : 1) * (diesel ? 1.35 : 1);
  out.egt = 620 + 230 * map * clamp(rpm / 7000, 0.3, 1.2) + (lam - 0.87) * (diesel ? 300 : 1100) + Math.max(0, mbt - adv) * 9 + (b.pr - 1) * 60;
  if (diesel) out.egt -= 120;
  if (b.choke > 0.15) out.flags.push('turbo');
  if (b.overTarget) out.flags.push('turbomax');
  return out;
}

// Расчётная кривая на полном газу (для конструктора и машины)
function engineCurve(e) {
  const lim = limits(e), vp = veParams(e);
  const maxR = Math.min(e.limiter, lim.mech * 1.02);
  const pts = [];
  let pk = { hp: 0, hpRpm: 0, nm: 0, nmRpm: 0, boost: 0 };
  let warn = new Set();
  const step = Math.max(50, Math.round(maxR / 90 / 50) * 50);
  for (let r = 1000; r <= maxR + 1; r += step) {
    const o = engineAt(e, r, { lim, vp });
    pts.push({ rpm: r, nm: Math.max(0, o.torque), hp: Math.max(0, o.power), boost: o.boost, knock: o.knock, egt: o.egt, stress: o.stress, lambda: o.lambda });
    if (o.power > pk.hp) { pk.hp = o.power; pk.hpRpm = r; }
    if (o.torque > pk.nm) { pk.nm = o.torque; pk.nmRpm = r; }
    if (o.boost > pk.boost) pk.boost = o.boost;
    if (o.knock > 0.3) warn.add('knock');
    if (!o.fuelOk) warn.add('lean');
    if (o.stress > lim.pLim) warn.add('stress');
    if (o.egt > 980) warn.add('egt');
    o.flags.forEach(f => warn.add(f));
  }
  if (e.limiter > lim.mech) warn.add('overrev');
  if (e.limiter > lim.valveFloat) warn.add('floatlim');
  return { pts, peak: pk, lim, warn: [...warn], maxRpm: maxR };
}

const WARN_TEXT = {
  knock: 'Детонация: снизьте наддув/сжатие, уберите угол или возьмите топливо с бо́льшим октаном',
  lean: 'Бедная смесь: не хватает форсунок или топливного насоса',
  stress: 'Давление в цилиндрах выше прочности деталей',
  egt: 'Перегрев выхлопа: обогатите смесь или увеличьте угол',
  float: 'Зависание клапанов на высоких оборотах',
  turbo: 'Турбина задыхается: мала для такого расхода воздуха',
  turbomax: 'Турбина не может дать столько наддува',
  throttle: 'Дроссель мал для такого объёма и оборотов',
  exhaust: 'Выпуск слишком узкий: увеличьте диаметр трубы',
  misfire: 'Пропуски зажигания: нужны усиленные катушки',
  nostart: 'Дизелю нужна степень сжатия от 14',
  overrev: 'Отсечка выше механического предела: двигатель разрушится',
  floatlim: 'Отсечка выше предела пружин клапанов',
};

function engineMass(e) {
  const disp = displacement(e), mat = STRENGTH.block[e.block].w;
  let m;
  if (isRotary(e)) m = 45 + e.cyl * 22;
  else m = 25 + disp * 28 * mat + e.cyl * (4.5 + (e.cycle === 2 ? -2 : 0)) * (e.head === 'iron' ? 1.4 : 1);
  if (e.cycle === 4 && !isRotary(e)) m += ({ ohv: 0, sohc: 4, dohc: 8 })[e.valvetrain] * BANKS_HEADS(e);
  m += turboCount(e) * 11 + (hasSC(e) ? 18 : 0) + ({ none: 0, air: 9, big: 14, water: 12 })[e.intercooler];
  m += ({ heavy: 14, stock: 9, light: 5, carbon: 3 })[e.flywheel];
  m += ({ cast: 0, forged: -1, billet: -2 })[e.crank] * e.cyl * 0.3 + ({ cast: 0, forged: -0.2, titanium: -0.5, billet: -0.3 })[e.rods] * e.cyl;
  if (e.oilSystem === 'dry') m += 6;
  if (FUELS[e.fuel].diesel) m *= 1.25;
  if (e.nitrous) m += 12;
  if (e.balance) m += 5;
  return m;
}
function BANKS_HEADS(e) { return e.type === 'inline' ? 1 : e.type === 'radial' ? e.cyl * 0.25 : e.type === 'vr' ? 1 : e.type === 'w' ? 2 : 2; }

function inertia(e) {
  const disp = displacement(e);
  return 0.06 + disp * 0.035 + ({ heavy: 0.16, stock: 0.1, light: 0.05, carbon: 0.03 })[e.flywheel] * (0.6 + disp * 0.2);
}

// ---------- МАШИНА ----------
const BODIES = {
  hatch:    { name: 'Хэтчбек', mass: 880, cd: 0.34, area: 2.0, front: 0.62, price: 3000, len: 3.9 },
  sedan:    { name: 'Седан', mass: 1120, cd: 0.30, area: 2.1, front: 0.56, price: 6000, len: 4.6 },
  coupe:    { name: 'Купе', mass: 1060, cd: 0.29, area: 1.95, front: 0.54, price: 9000, len: 4.4 },
  wagon:    { name: 'Универсал', mass: 1220, cd: 0.32, area: 2.2, front: 0.55, price: 7000, len: 4.7 },
  roadster: { name: 'Родстер', mass: 920, cd: 0.36, area: 1.8, front: 0.52, price: 12000, len: 4.0 },
  pickup:   { name: 'Пикап', mass: 1480, cd: 0.45, area: 2.7, front: 0.58, price: 8000, len: 5.2 },
  super:    { name: 'Суперкар (мотор в базе)', mass: 1100, cd: 0.30, area: 1.85, front: 0.42, price: 40000, len: 4.5 },
  dragster: { name: 'Дрэгстер', mass: 650, cd: 0.55, area: 1.5, front: 0.25, price: 60000, len: 6.0 },
};
const GEARBOXES = {
  man4: { name: 'Механика 4 ст.', g: [3.6, 2.1, 1.4, 1.0], shift: 0.5, price: 0 },
  man5: { name: 'Механика 5 ст.', g: [3.5, 2.06, 1.4, 1.03, 0.82], shift: 0.45, price: 1500 },
  man6: { name: 'Механика 6 ст.', g: [3.4, 2.16, 1.6, 1.25, 1.0, 0.83], shift: 0.42, price: 3500 },
  auto4: { name: 'Автомат 4 ст.', g: [2.8, 1.55, 1.0, 0.7], shift: 0.35, price: 2500, auto: true },
  auto8: { name: 'Автомат 8 ст.', g: [4.7, 3.1, 2.1, 1.67, 1.29, 1.0, 0.84, 0.67], shift: 0.2, price: 9000, auto: true },
  seq6: { name: 'Секвентальная 6 ст.', g: [3.0, 2.1, 1.65, 1.35, 1.15, 1.0], shift: 0.12, price: 14000 },
  dct7: { name: 'Робот DCT 7 ст.', g: [3.6, 2.4, 1.75, 1.3, 1.03, 0.84, 0.68], shift: 0.08, price: 18000 },
  drag2: { name: 'Дрэг 2 ст. (Powerglide)', g: [1.8, 1.0], shift: 0.15, price: 7000, auto: true },
};
const TIRES = {
  eco: { name: 'Эко', mu: 0.85, price: 0 }, street: { name: 'Дорожные', mu: 0.95, price: 800 },
  sport: { name: 'Спортивные', mu: 1.08, price: 2500 }, semi: { name: 'Полуслики', mu: 1.22, price: 5000 },
  slick: { name: 'Слики', mu: 1.35, price: 8000 }, drag: { name: 'Дрэг-слики', mu: 1.65, price: 12000 },
};

function defaultCar() {
  return {
    name: 'Моя машина', body: 'hatch', color: '#c8312b', stripe: false, reduction: 0,
    drive: 'fwd', gearbox: 'man5', finalDrive: 4.1, gearSpread: 1.0, clutch: 'stock', diff: 'open',
    rim: 15, tire: 'street', tireWidth: 195, profile: 55, rimStyle: 'steel',
    suspension: 'stock', rideHeight: 0, brakes: 'stock', spoiler: 'none', splitter: false, diffuser: false,
    engineId: null,
  };
}

function carStats(c, e) {
  const B = BODIES[c.body];
  let mass = B.mass + (e ? engineMass(e) : 120);
  mass -= [0, 60, 140, 240][c.reduction] * (B.mass / 1100);
  mass += ({ stock: 0, sport: -5, coilover: -8, drag: -15 })[c.suspension];
  mass += ({ stock: 0, sport: 4, race: -6, ceramic: -18 })[c.brakes];
  mass += c.drive === 'awd' ? 70 : 0;
  mass += ({ none: 0, lip: 3, wing: 9, big: 16 })[c.spoiler] + (c.splitter ? 4 : 0) + (c.diffuser ? 5 : 0);
  mass += (c.rim - 15) * 3 * (c.rimStyle === 'forged' ? 0.5 : c.rimStyle === 'steel' ? 1.3 : 1) + (c.tireWidth - 195) * 0.06;
  let cd = B.cd + ({ none: 0, lip: -0.005, wing: 0.02, big: 0.05 })[c.spoiler] + (c.splitter ? -0.005 : 0) + (c.diffuser ? -0.01 : 0) - c.rideHeight * 0.0004;
  let down = ({ none: 0, lip: 0.05, wing: 0.25, big: 0.55 })[c.spoiler] + (c.splitter ? 0.12 : 0) + (c.diffuser ? 0.15 : 0); // Cl
  const rWheel = (c.rim * 25.4 / 2 + c.tireWidth * c.profile / 100) / 1000;
  let mu = TIRES[c.tire].mu * Math.pow(c.tireWidth / 205, 0.22) * ({ stock: 1, sport: 1.03, coilover: 1.05, drag: 1.1 })[c.suspension];
  const gb = GEARBOXES[c.gearbox];
  const gears = gb.g.map((g, i) => i === 0 ? g * c.gearSpread : g * Math.pow(c.gearSpread, (gb.g.length - 1 - i) / (gb.g.length - 1)));
  const clutchCap = ({ stock: 380, sport: 650, race: 1100, triple: 2200 })[c.clutch];
  return { mass, cd, down, area: B.area, rWheel, mu, gears, shift: gb.shift, auto: !!gb.auto, clutchCap, front: B.front };
}

// Одна симуляция заезда (используется и в гонке, и для расчёта разгона).
// state хранит всё; stepCar двигает на dt
function newCarState(c, e) {
  const st = carStats(c, e);
  return { c, e, st, v: 0, x: 0, gear: 0, rpm: e ? e.idle : 900, spool: 0, shiftT: 0, t: 0, launched: false, wheelspin: 0, clutch: 0, finished: false, lim: e ? limits(e) : null, vp: e ? veParams(e) : null, nitrous: false, brokeShift: 0, maxV: 0 };
}

function stepCar(s, dt, throttle, opt) {
  opt = opt || {};
  const { st, e } = s;
  const g = 9.81;
  const ratio = st.gears[s.gear] * s.c.finalDrive;
  const wheelRpm = s.v / st.rWheel * 60 / (2 * Math.PI);
  const coupledRpm = wheelRpm * ratio;
  const maxR = Math.min(e.limiter, s.lim.mech);
  // турбина
  if (hasTurbo(e)) {
    const tp = turboParams(e);
    const tgt = smooth(tp.onset, tp.full, s.rpm) * clamp(throttle * 1.2, 0, 1);
    const tau = tgt > s.spool ? tp.tau : tp.tau * 0.5;
    s.spool += (tgt - s.spool) * Math.min(1, dt / tau);
    if (e.antilag && throttle < 0.2) s.spool = Math.max(s.spool, tgt * 0.7);
  }
  const cut = s.rpm >= maxR;
  const o = engineAt(e, Math.max(800, s.rpm), { throttle: cut ? 0 : throttle, spool: s.spool, lim: s.lim, vp: s.vp, nitrous: s.nitrous });
  s.out = o;
  let engT = o.torque;
  const I = inertia(e);
  const drag = 0.5 * 1.2 * st.cd * st.area * s.v * s.v + 0.013 * st.mass * g;
  const downF = 0.5 * 1.2 * st.down * st.area * s.v * s.v;
  // нагрузка на ведущие колёса с переносом веса
  const accEst = Math.max(0, s.acc || 0);
  const transfer = accEst / g * 0.12;
  let driveFrac = s.c.drive === 'awd' ? 1 : s.c.drive === 'fwd' ? clamp(st.front - transfer, 0.2, 1) : clamp(1 - st.front + transfer, 0.2, 1);
  const diffF = s.c.drive === 'awd' ? 1 : ({ open: 0.88, lsd: 0.97, locked: 1.0 })[s.c.diff];
  const gripF = (st.mu * (st.mass * g * driveFrac + downF * 0.5)) * diffF;

  let F = 0;
  s.wheelspin = 0;
  if (s.shiftT > 0) {
    s.shiftT -= dt;
    // во время переключения сцепление выжато: обороты падают
    s.rpm += (engT - 0) / I * dt * 9.55 * 0.3;
    s.rpm += (coupledRpm - s.rpm) * Math.min(1, dt * 8);
    if (s.shiftT <= 0) s.rpm = Math.max(coupledRpm, e.idle * 0.8);
  } else if (!s.launched) {
    // стоим: свободная раскрутка на нейтрали / до старта
    s.rpm += engT / I * dt * 9.55;
    if (s.rpm < e.idle) s.rpm += (e.idle - s.rpm) * Math.min(1, dt * 5);
  } else {
    // сцепление: пока обороты колёс ниже, сцепление проскальзывает
    if (coupledRpm < s.rpm - 50 && s.clutch < 1) {
      s.clutch = Math.min(1, s.clutch + dt / 0.35);
      const tClutch = Math.min(st.clutchCap * (0.4 + 0.6 * s.clutch), Math.max(0, engT + (s.rpm - coupledRpm) * 0.15));
      F = tClutch * ratio * 0.9 / st.rWheel;
      s.rpm += (engT - tClutch) / I * dt * 9.55;
      if (s.rpm < coupledRpm) s.rpm = coupledRpm;
    } else {
      s.clutch = 1;
      let tq = engT;
      if (tq > st.clutchCap) { tq = st.clutchCap; s.slipClutch = true; }
      F = tq * ratio * 0.9 / st.rWheel;
      s.rpm = coupledRpm;
    }
    if (F > gripF) {
      s.wheelspin = (F - gripF) / gripF;
      F = gripF * 0.82;
      // пробуксовка: двигатель раскручивается выше скорости колёс
      s.rpm = Math.max(s.rpm, coupledRpm + Math.min(2500, s.wheelspin * 1500));
    }
    if (s.rpm > maxR) s.rpm = maxR;
  }
  if (s.rpm > maxR) s.rpm = maxR - 80;
  if (s.rpm < 600) s.rpm = 600;
  const a = s.launched ? (F - drag) / st.mass : 0;
  s.acc = a;
  s.v = Math.max(0, s.v + a * dt);
  s.x += s.v * dt;
  s.t += s.launched ? dt : 0;
  s.maxV = Math.max(s.maxV, s.v);
  // автопереключение
  if (opt.autoShift && s.launched && s.shiftT <= 0 && s.gear < st.gears.length - 1) {
    const up = opt.shiftRpm || maxR * 0.97;
    if (s.rpm >= up) shiftUp(s);
  }
  return s;
}
function shiftUp(s) {
  if (s.gear >= s.st.gears.length - 1 || s.shiftT > 0) return false;
  s.gear++; s.shiftT = s.st.shift; return true;
}

// Расчёт разгона: 0–100, 0–200, 402 м, макс. скорость
function carPerformance(c, e) {
  if (!e) return null;
  const curve = engineCurve(e);
  let best = null;
  // оптимальная точка переключения — около максимума мощности или отсечки
  const shiftRpm = Math.min(curve.maxRpm * 0.98, Math.max(curve.peak.hpRpm * 1.08, curve.peak.hpRpm + 300));
  const s = newCarState(c, e);
  s.rpm = Math.min(e.launch, curve.maxRpm * 0.8);
  s.spool = 0.5;
  s.launched = true;
  const dt = 1 / 120;
  let t100 = null, t200 = null, q = null, qv = 0;
  for (let i = 0; i < 120 * 90; i++) {
    stepCar(s, dt, 1, { autoShift: true, shiftRpm });
    if (t100 === null && s.v >= 100 / 3.6) t100 = s.t;
    if (t200 === null && s.v >= 200 / 3.6) t200 = s.t;
    if (q === null && s.x >= 402) { q = s.t; qv = s.v * 3.6; }
    if (q !== null && s.t > 60) break;
  }
  best = { t100, t200, quarter: q, quarterV: qv, vmax: s.maxV * 3.6, shiftRpm };
  return best;
}

// Автонастройка ЭБУ: состав смеси, угол без детонации, при необходимости — снижение наддува
function autoTune(e) {
  const diesel = !!FUELS[e.fuel].diesel;
  e.lambda = diesel ? 1.3 : (isForced(e) ? (e.boost > 1.5 ? 0.76 : 0.8) : 0.88);
  if (e.ecu === 'stock') e.lambda = clamp(e.lambda, 0.8, 1.0);
  const lim = limits(e);
  const safeMax = Math.min(lim.mech, lim.valveFloat) * 0.97;
  if (e.limiter > safeMax) e.limiter = Math.floor(safeMax / 100) * 100;
  const pk = engineCurve(e).peak;
  e.timing = Math.round(mbtTiming(e, pk.hpRpm || 5000, 1 + pk.boost / 1.013) * 2) / 2;
  const maxKnock = () => Math.max(0, ...engineCurve(e).pts.map(p => p.knock));
  const ks = e.knockSensor; e.knockSensor = false;
  let guard = 0;
  while (maxKnock() > 0.02 && guard++ < 200) {
    if (e.timing > 6 && (e.timing > mbtTiming(e, pk.hpRpm || 5000, 1 + pk.boost / 1.013) - 14 || !isForced(e))) e.timing -= 0.5;
    else if (isForced(e) && e.boost > 0.15) e.boost = Math.round((e.boost - 0.05) * 100) / 100;
    else if (!diesel && e.lambda > 0.72) e.lambda = Math.round((e.lambda - 0.01) * 100) / 100;
    else break;
  }
  e.knockSensor = ks;
  return e;
}
