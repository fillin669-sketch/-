'use strict';
var APP_VERSION = '4.2';
// ================= ИНТЕРФЕЙС И ИГРОВОЙ ЦИКЛ =================
const $ = (s, r) => (r || document).querySelector(s);
const $$ = (s, r) => [...(r || document).querySelectorAll(s)];
function h(tag, attrs, ...kids) {
  const n = document.createElement(tag);
  if (attrs) for (const k in attrs) {
    const v = attrs[k];
    if (k === 'class') n.className = v; else if (k === 'html') n.innerHTML = v;
    else if (k.startsWith('on')) n.addEventListener(k.slice(2), v); else if (v !== false && v != null) n.setAttribute(k, v);
  }
  for (const c of [].concat.apply([], kids)) if (c != null && c !== false) n.append(c.nodeType ? c : document.createTextNode(String(c)));
  return n;
}
function money(n) { n = Math.round(n); const s = Math.abs(n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ' '); return (n < 0 ? '−' : '') + '₽ ' + s; }
function num(n, d) { return (d ? n.toFixed(d) : Math.round(n).toString()).replace('.', ','); }
function toast(t) { const el = $('#toast'); el.textContent = t; el.classList.add('show'); clearTimeout(toast.t); toast.t = setTimeout(() => el.classList.remove('show'), 2200); }
function vibrate(ms) { if (!S.settings.vibro) return; try { if (window.Android && Android.vibrate) Android.vibrate(ms); else if (navigator.vibrate) navigator.vibrate(ms); } catch (_) { } }
function modal(build, cls) {
  const box = $('#modalBox'); box.innerHTML = ''; box.className = 'modal-box ' + (cls || '');
  build(box, closeModal); $('#modal').hidden = false;
}
function closeModal() { $('#modal').hidden = true; }
$('#modal').addEventListener('click', (ev) => { if (ev.target.id === 'modal') closeModal(); });

// ---------- Сохранение ----------
const SAVE_KEY = 'motorGarage.v1';
let S;
let uid = 1;
function newId() { return 'id' + Date.now().toString(36) + (uid++); }
function freshState() {
  const e = makePreset(0); e.id = newId();
  e.built = hwSnapshot(e);
  const c = defaultCar(); c.id = newId(); c.engineId = e.id; c.built = carHw(c);
  return { rivalsV: 2, money: 20000, sandbox: false, engines: [e], cars: [c], curE: e.id, curC: c.id, done: {}, unlocked: 0, best: {}, settings: { vol: 0.8, vibro: true, gfx: 'auto', autoShift: true }, view: '3d', xray: true, openSec: 'layout', openCarSec: 'body' };
}
let loadProblem = null;
// перенос побед и рекордов на новый список соперников (с трассами)
function migrateRivals() {
  if (S.rivalsV === 2) return;
  const idx = (old) => RIVALS.findIndex(r => r.name === RIVALS_V1[old]);
  const best = {}, beaten = {};
  Object.keys(S.best || {}).forEach(k => { const j = idx(+k); if (j >= 0) best[j] = S.best[k]; });
  Object.keys(S.beaten || {}).forEach(k => { const j = idx(+k); if (j >= 0 && S.beaten[k]) beaten[j] = true; });
  // открыты все соперники до самого сильного побеждённого + следующий
  let maxBeaten = -1; Object.keys(beaten).forEach(k => { maxBeaten = Math.max(maxBeaten, +k); });
  const oldUnl = idx(Math.min(RIVALS_V1.length - 1, S.unlocked || 0));
  S.best = best; S.beaten = beaten; S.unlocked = Math.max(maxBeaten + 1, Math.min(oldUnl, maxBeaten + 1), 0);
  S.rivalsV = 2;
}
function load() {
  let raw = null;
  try { raw = localStorage.getItem(SAVE_KEY) || localStorage.getItem(SAVE_KEY + '.bak'); } catch (_) { }
  try {
    if (raw) {
      const d = JSON.parse(raw);
      if (d && d.engines && d.engines.length) {
        // дополняем новыми полями из версии к версии
        d.engines = d.engines.map(e => Object.assign(defaultEngine(), e));
        d.cars = (d.cars || []).map(c => Object.assign(defaultCar(), c));
        S = Object.assign(freshState(), d);
        S.settings = Object.assign(freshState().settings, d.settings || {});
        migrateRivals();
        return;
      }
    }
  } catch (err) {
    // повреждённое сохранение не стираем: откладываем копию и сообщаем
    try { localStorage.setItem(SAVE_KEY + '.broken', raw || ''); } catch (_) { }
    loadProblem = 'Сохранение не прочиталось (' + (err && err.message) + '). Копия отложена, игра начата заново.';
  }
  S = freshState();
}
let saveT = 0;
function save() {
  clearTimeout(saveT);
  saveT = setTimeout(() => {
    try {
      const js = JSON.stringify(S);
      const old = localStorage.getItem(SAVE_KEY);
      if (old) localStorage.setItem(SAVE_KEY + '.bak', old);
      localStorage.setItem(SAVE_KEY, js);
    } catch (err) { if (!save.warned) { save.warned = true; toast('Не удалось сохранить игру: ' + (err && err.message)); } }
  }, 300);
}

// ---------- «Железо» и сборка ----------
const ENGINE_META = ['id', 'health', 'blown', 'lastDyno', 'prevDyno', 'built'];
function hwKeys(e) { return Object.keys(defaultEngine()).filter(k => !TUNE_KEYS.includes(k) && !ENGINE_META.includes(k)); }
function hwSnapshot(e) { const o = {}; hwKeys(e).forEach(k => o[k] = e[k]); return o; }
function builtEngine(e) { if (!e || !e.built) return null; return Object.assign({}, e, e.built); }
function hwChanged(e) { if (!e.built) return true; return hwKeys(e).some(k => e[k] !== e.built[k]); }
const CAR_META = ['id', 'built', 'name', 'color', 'stripe', 'engineId', 'finish', 'tint', 'caliper'];
function carHw(c) { const o = {}; Object.keys(defaultCar()).filter(k => !CAR_META.includes(k)).forEach(k => o[k] = c[k]); return o; }
function builtCar(c) { if (!c || !c.built) return null; return Object.assign({}, c, c.built); }
function carChanged(c) { if (!c.built) return true; const hw = carHw(c); return Object.keys(hw).some(k => hw[k] !== c.built[k]); }
function curEngine() { return S.engines.find(e => e.id === S.curE) || S.engines[0]; }
function curCar() { return S.cars.find(c => c.id === S.curC) || S.cars[0]; }
function canPay(n) { return S.sandbox || S.money >= n; }
function pay(n) { if (!S.sandbox) S.money -= n; updateMoney(); save(); }
function earn(n) { if (!S.sandbox) S.money += n; updateMoney(); save(); }
function updateMoney() { $('#money').textContent = S.sandbox ? '₽ ∞' : money(S.money); }
function buildPrice(e) {
  const nc = engineCost(e);
  if (!e.built) return nc;
  const oc = engineCost(builtEngine(e));
  const d = nc - oc;
  return d >= 0 ? d + 100 : Math.round(d * 0.5) + 100;
}

// ---------- Холсты ----------
const DPR = Math.min(2, window.devicePixelRatio || 1);
function fit(cv) {
  const r = cv.getBoundingClientRect();
  const w = Math.max(1, Math.round(r.width)), hh = Math.max(1, Math.round(r.height));
  if (cv._w !== w || cv._h !== hh) { cv._w = w; cv._h = hh; cv.width = w * DPR; cv.height = hh * DPR; }
  const ctx = cv.getContext('2d'); ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
  return { ctx, W: w, H: hh };
}

// ---------- Иконки секций ----------
const ICONS = {
  layout: 'M4 4h7v7H4zm9 0h7v7h-7zM4 13h7v7H4zm9 0h7v7h-7z',
  block: 'M5 3h14v4h-2v3h2v11H5V10h2V7H5zm4 4v3h2V7zm4 0v3h2V7z',
  crank: 'M11 2h2v6h4l2 2-2 2h-4v4l-1 1-1-1v-4H7l-2-2 2-2h4zm1 15a3 3 0 1 1 0 6 3 3 0 0 1 0-6z',
  head: 'M3 6h18v6H3zm2 8h3v6H5zm5 0h4v6h-4zm6 0h3v6h-3z',
  intake: 'M3 10h4V6h3v4h4V6h3v4h4v4h-4v4h-3v-4h-4v4H7v-4H3z',
  turbo: 'M12 2a10 10 0 1 1 0 20 10 10 0 0 1 0-20zm0 4a2 2 0 0 0-2 2c0 1 .6 1.6 1 2-2-.4-3.3-.4-4 .6s.2 2.4 1.4 2.4c.9 0 1.7-.5 2.2-1.1-.8 1.9-1 3.2 0 3.9 1 .7 2.2-.4 2.2-1.6 0-.9-.5-1.7-1.1-2.2 1.9.8 3.2 1 3.9 0s-.4-2.2-1.6-2.2c-.9 0-1.6.4-2 1 .4-2 .4-3.3-.4-4z',
  fuel: 'M5 3h9v8h1a2 2 0 0 1 2 2v4a1 1 0 0 0 2 0V9l-2-2 1.4-1.4L21 8.2V17a3 3 0 0 1-6 0v-4h-1v8H5zm2 2v5h5V5z',
  ecu: 'M7 3h10v2h2v2h2v2h-2v2h2v2h-2v2h2v2h-2v2h-2v2H7v-2H5v-2H3v-2h2v-2H3v-2h2V9H3V7h2V5h2zm1 4v10h8V7z',
  exhaust: 'M2 14h10l3-5h7v4h-5l-3 5H2zm4-8a2 2 0 1 1 0 4 2 2 0 0 1 0-4z',
  cool: 'M11 2h2v4.6l2.8-2.8 1.4 1.4L13 9.4V11h1.6l4.2-4.2 1.4 1.4L17.4 11H22v2h-4.6l2.8 2.8-1.4 1.4-4.2-4.2H13v1.6l4.2 4.2-1.4 1.4L13 17.4V22h-2v-4.6l-2.8 2.8-1.4-1.4 4.2-4.2V13H9.4l-4.2 4.2-1.4-1.4L6.6 13H2v-2h4.6L3.8 8.2l1.4-1.4L9.4 11H11V9.4L6.8 5.2l1.4-1.4L11 6.6z',
  paint: 'M18 3a3 3 0 0 1 3 3v3h-2v4H9v-3H3V6a3 3 0 0 1 3-3zM10 15h4v6h-4z',
  body: 'M5 11l2-5h10l2 5h1a1 1 0 0 1 1 1v5h-2a2 2 0 0 1-4 0H9a2 2 0 0 1-4 0H3v-5a1 1 0 0 1 1-1z',
  gear: 'M12 8a4 4 0 1 1 0 8 4 4 0 0 1 0-8zm-1-6h2l.5 3 2 .8 2.5-1.8 1.4 1.4-1.8 2.5.8 2 3 .5v2l-3 .5-.8 2 1.8 2.5-1.4 1.4-2.5-1.8-2 .8-.5 3h-2l-.5-3-2-.8-2.5 1.8-1.4-1.4 1.8-2.5-.8-2-3-.5v-2l3-.5.8-2-1.8-2.5 1.4-1.4 2.5 1.8 2-.8z',
  wheel: 'M12 2a10 10 0 1 1 0 20 10 10 0 0 1 0-20zm0 3a7 7 0 1 0 0 14 7 7 0 0 0 0-14zm0 4a3 3 0 1 1 0 6 3 3 0 0 1 0-6z',
  susp: 'M11 2h2v3h3v2h-3v2h3v2h-3v2h3v2h-3v2h3v2h-3v3h-2v-3H8v-2h3v-2H8v-2h3v-2H8V9h3V7H8V5h3z',
  aero: 'M2 9h20v3h-3v6h-2v-6H7v6H5v-6H2z',
};
const SEC_HL = { block: 'block', crank: 'crank', head: 'head', intake: 'intake', boost: 'boost', fuel: 'fuel', ecu: 'ecu', exhaust: 'exhaust', cooling: 'cool' };
function icon(name) { const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg'); s.setAttribute('viewBox', '0 0 24 24'); s.innerHTML = '<path d="' + (ICONS[name] || ICONS.layout) + '"/>'; return s; }

// ---------- Подсказки к полям ----------
function fieldInfo(k, e) {
  const F = FUELS[e.fuel];
  switch (k) {
    case 'bore': case 'stroke': {
      const lim = limits(e), vp = 2 * e.stroke / 1000 * e.limiter / 60;
      return 'Объём ' + num(displacement(e), 2) + ' л · ' + (e.bore > e.stroke * 1.05 ? 'короткоходный' : e.bore < e.stroke * 0.95 ? 'длинноходный' : 'квадратный') + ' · поршень на отсечке ' + num(vp, 1) + ' м/с (предел ' + num(lim.vpLim, 1) + ')';
    }
    case 'rotorVol': return 'Объём ' + num(displacement(e), 2) + ' л';
    case 'rodLen': return 'Шатун/ход: ' + num(e.rodLen / e.stroke, 2);
    case 'cr': return F.diesel ? 'Дизель заводится от 14:1' : 'Без наддува на «' + F.name + '» без детонации до ≈' + num(F.kLim, 1) + ':1';
    case 'turboSize': { const tp = turboParams(e); return 'Наддув с ~' + Math.round(tp.onset / 100) * 100 + ' об/мин, полный к ~' + Math.round(tp.full / 100) * 100 + ', максимум ~' + num(tp.maxPR - 1, 1) + ' бар'; }
    case 'injectors': {
      const nInj = e.cyl * (isRotary(e) ? 2 : 1);
      const kw = e.injectors * nInj * 0.85 * 0.74 * 60 / 1000 / (F.diesel ? 0.19 : 0.245) / F.flow * (e.lambda / 0.88);
      return 'Хватит примерно на ' + Math.round(kw * 0.8 / 0.7355 / 10) * 10 + ' л.с. (' + nInj + ' шт.)';
    }
    case 'limiter': { const lim = limits(e); return 'Предел деталей ' + Math.round(lim.mech / 100) * 100 + (isFinite(lim.valveFloat) ? ', пружин клапанов ' + Math.round(lim.valveFloat / 100) * 100 : '') + ' об/мин'; }
    case 'timing': { const c = engineCurve(e); return 'Оптимум на пике мощности ≈ ' + num(mbtTiming(e, c.peak.hpRpm || 5000, 1 + c.peak.boost / 1.013), 1) + '°'; }
    case 'boost': { if (hasTurbo(e)) { const tp = turboParams(e); return 'Турбина может дать до ' + num(tp.maxPR - 1, 1) + ' бар'; } return 'Компрессор: размер шкива задаёт давление'; }
    case 'camDur': return 'Пик наполнения ≈ ' + Math.round(veParams(e).peak / 100) * 100 + ' об/мин';
    case 'vAngle': return e.type === 'v' && e.cyl === 6 ? (Math.abs(e.vAngle - 60) < 3 ? 'Идеальный угол для V6' : 'Для V6 лучше 60°') : e.type === 'v' && e.cyl === 8 ? (Math.abs(e.vAngle - 90) < 3 ? 'Идеальный угол для V8' : 'Для V8 лучше 90°') : '';
  }
  return '';
}

// ---------- Универсальная отрисовка списка деталей ----------
// spec: [{id,title,icon,fields}], obj — редактируемый объект
// Экран не должен прыгать: после любой перерисовки возвращаем поле, которого касались, на то же место
let anchorKey = null;
document.addEventListener('pointerdown', (ev) => { const f = ev.target.closest && ev.target.closest('[data-k]'); anchorKey = f ? f.dataset.k : null; }, true);
function stable(scroller, fn) {
  if (!scroller) { fn(); return; }
  const sel = anchorKey ? '[data-k="' + anchorKey + '"]' : '.sec.open';
  const a = scroller.querySelector(sel);
  const top0 = scroller.scrollTop, t0 = a ? a.getBoundingClientRect().top : null;
  fn();
  const b = scroller.querySelector(sel);
  if (b && t0 != null) scroller.scrollTop = scroller.scrollTop + (b.getBoundingClientRect().top - t0);
  else scroller.scrollTop = top0;
}
function renderSpec(root, spec, obj, cfg) {
  stable(root.closest('.tab'), () => {
    const frag = document.createDocumentFragment();
    renderSpecInto(frag, root, spec, obj, cfg);
    root.innerHTML = ''; root.appendChild(frag);
  });
}
function renderSpecInto(rootFrag, root, spec, obj, cfg) {
  for (const sec of spec) {
    const open = cfg.open() === sec.id;
    const title = val(sec.title, obj);
    const secCost = cfg.secCost ? cfg.secCost(sec) : null;
    const box = h('div', { class: 'sec' + (open ? ' open' : ''), 'data-sec': sec.id });
    const summ = sec.fields.filter(f => !f.show || f.show(obj)).slice(0, 3).map(f => summaryOf(f, obj)).filter(Boolean).join(' · ');
    const head = h('button', { class: 'sec-h', onclick: () => { cfg.setOpen(open ? null : sec.id); renderSpec(root, spec, obj, cfg); if (!open) setTimeout(() => { const nb = root.querySelector('[data-sec="' + sec.id + '"]'); if (nb) scrollSecIntoView(nb); }, 30); } },
      h('div', { class: 'ic' }, icon(sec.icon)),
      h('div', { class: 't' }, h('b', null, title), h('small', null, summ)),
      h('div', { class: 'ch' }, '›'));
    box.append(head);
    if (open) {
      const body = h('div', { class: 'sec-b' });
      for (const f of sec.fields) {
        if (f.show && !f.show(obj)) continue;
        body.append(renderField(f, obj, cfg, () => renderSpec(root, spec, obj, cfg)));
      }
      box.append(body);
    }
    rootFrag.append(box);
  }
}
function scrollSecIntoView(box) {
  const sc = box.closest('.tab'); if (!sc) return;
  const sticky = sc.querySelector('.sticky-view');
  const off = sticky ? sticky.offsetHeight : 0;
  const top = box.getBoundingClientRect().top - sc.getBoundingClientRect().top + sc.scrollTop - off - 8;
  sc.scrollTo({ top, behavior: 'smooth' });
}
function summaryOf(f, o) {
  if (f.type === 'range') return f.fmt ? f.fmt(o) : (fmtVal(o[f.k], f) + (f.unit ? ' ' + f.unit : ''));
  if (f.type === 'toggle') return o[f.k] ? val(f.label, o) : null;
  if (f.type === 'color') return null;
  const opt = optList(f, o).find(x => x[0] === o[f.k]);
  return opt ? opt[1] : null;
}
function fmtVal(v, f) { const st = val(f.step, null) || 1; const d = st < 0.1 ? 2 : st < 1 ? 1 : 0; return num(v, d); }
function priceTxt(p) { return p > 0 ? money(p) : p < 0 ? '−' + money(-p).slice(1) : 'бесплатно'; }

function renderField(f, o, cfg, rerender) {
  const label = val(f.label, o);
  const wrap = h('div', { class: 'field', 'data-k': f.k });
  const isTune = f.tune || TUNE_KEYS.includes(f.k);
  const set = (v, structural) => {
    o[f.k] = v; cfg.onChange(f.k, structural);
    if (structural) rerender();
  };
  if (f.type === 'range') {
    const mn = val(f.min, o), mx = val(f.max, o), st = f.step;
    const valB = h('b', null, '');
    const info = h('div', { class: 'hint' });
    const inp = h('input', { type: 'range', min: mn, max: mx, step: st, value: o[f.k], id: 'f-' + f.k });
    const upd = () => {
      valB.textContent = f.fmt ? f.fmt(o) : fmtVal(o[f.k], f) + (f.unit ? ' ' + f.unit : '');
      inp.style.setProperty('--p', ((o[f.k] - mn) / (mx - mn) * 100) + '%');
      const t = fieldInfo(f.k, o); info.textContent = t || f.hint || ''; info.hidden = !info.textContent;
    };
    inp.addEventListener('input', () => { o[f.k] = parseFloat(inp.value); cfg.onChange(f.k, false); upd(); });
    inp.addEventListener('change', () => { cfg.onChange(f.k, true); rerender(); });
    const step = (d) => { o[f.k] = clamp(Math.round((o[f.k] + d * st) / st) * st, mn, mx); o[f.k] = parseFloat(o[f.k].toFixed(4)); inp.value = o[f.k]; cfg.onChange(f.k, true); upd(); };
    const rep = (d) => { let t, n = 0; const go = () => { step(d * (n > 8 ? 5 : 1)); n++; t = setTimeout(go, n === 1 ? 380 : 70); }; return { down: () => go(), up: () => clearTimeout(t) }; };
    const mk = (txt, d) => { const r = rep(d); const b = h('button', { 'aria-label': d < 0 ? 'Меньше' : 'Больше' }, txt); b.addEventListener('pointerdown', (ev) => { ev.preventDefault(); r.down(); }); ['pointerup', 'pointerleave', 'pointercancel'].forEach(x => b.addEventListener(x, () => r.up())); b.addEventListener('click', (ev) => ev.preventDefault()); return b; };
    wrap.append(h('div', { class: 'f-h' }, h('label', { for: 'f-' + f.k }, label, isTune ? h('span', { class: 'free' }, 'ЭБУ') : null), valB),
      h('div', { class: 'rng' }, mk('−', -1), inp, mk('+', 1)), info);
    upd();
  } else if (f.type === 'toggle') {
    const p = val(f.price || 0, o);
    const tg = h('button', { class: 'tg' + (o[f.k] ? ' on' : ''), 'aria-pressed': o[f.k] ? 'true' : 'false', onclick: () => set(!o[f.k], true) });
    wrap.append(h('div', { class: 'switch' }, h('div', null, h('label', null, label, p ? ' · ' + money(p) : ''), f.hint ? h('small', null, f.hint) : null), tg));
  } else if (f.type === 'color') {
    const sw = h('div', { class: 'swatches' });
    optList(f, o).forEach(([v, name]) => sw.append(h('button', { class: 'sw' + (o[f.k] === v ? ' on' : ''), style: 'background:' + (f.palette ? v : COVER_COLORS[v]), title: name, 'aria-label': name, onclick: () => set(v, true) })));
    wrap.append(h('div', { class: 'f-h' }, h('label', null, label)), sw);
  } else {
    const opts = optList(f, o);
    const nums = opts.every(x => typeof x[0] === 'number' && String(x[1]).length <= 3);
    const chips = h('div', { class: 'chips' + (nums ? ' nums' : '') });
    let curDesc = '';
    opts.forEach(([v, name, price, desc]) => {
      const p = val(price, o) || 0;
      if (o[f.k] === v) curDesc = desc || '';
      chips.append(h('button', { class: 'chip' + (o[f.k] === v ? ' on' : ''), onclick: () => { if (o[f.k] !== v) set(v, true); } },
        h('b', null, name), cfg.noPrices || isTune ? null : h('small', null, p ? money(p) : '—')));
    });
    wrap.append(h('div', { class: 'f-h' }, h('label', null, label)), chips);
    if (curDesc) wrap.append(h('div', { class: 'desc' }, curDesc));
    if (f.hint) wrap.append(h('div', { class: 'hint' }, f.hint));
  }
  return wrap;
}

// ================= ВКЛАДКА «ДВИГАТЕЛЬ» =================
let engDirty = true;
function renderEngineTab() {
  const e = curEngine();
  $('#engName').innerHTML = '';
  $('#engName').append(h('span', null, e.name), h('small', null, layoutCode(e) + ' · ' + num(displacement(e), 1) + ' л'));
  renderSpec($('#engSections'), ENGINE_SPEC, e, {
    open: () => S.openSec, setOpen: (id) => { S.openSec = id; save(); },
    onChange: (k, structural) => { fixEngine(e, k); engDirty = true; if (TUNE_KEYS.includes(k) && e.built) { /* настройка ЭБУ сразу действует */ } save(); if (structural) updateEngineStats(); else scheduleStats(); },
  });
  updateEngineStats();
}
let statsRaf = 0;
function scheduleStats() { if (statsRaf) return; statsRaf = requestAnimationFrame(() => { statsRaf = 0; updateEngineStats(); }); }
function updateEngineStats() { stable($('#tab-engine'), updateEngineStatsRaw); }
function updateEngineStatsRaw() {
  const e = curEngine();
  const c = engineCurve(e);
  const stats = $('#engStats');
  stats.innerHTML = '';
  stats.append(
    h('div', { class: 'hp' }, h('b', null, Math.round(c.peak.hp)), h('span', null, 'л.с. @' + c.peak.hpRpm)),
    h('div', { class: 'nm' }, h('b', null, Math.round(c.peak.nm)), h('span', null, 'Н·м @' + c.peak.nmRpm)),
    h('div', null, h('b', null, Math.round(engineMass(e))), h('span', null, 'кг · ' + balanceInfo(e).text)),
    h('div', null, h('b', null, money(engineCost(e)).replace('₽ ', '')), h('span', null, '₽ стоимость')));
  $('#engName').lastChild.textContent = layoutCode(e) + ' · ' + num(displacement(e), 1) + ' л';
  const { ctx, W, H } = fit($('#cvCalc'));
  const curves = [{ pts: c.pts, boost: isForced(e) }];
  if (e.lastDyno && e.lastDyno.pts) curves.unshift({ pts: e.lastDyno.pts, dash: true, alpha: 0.45, width: 1.5 });
  drawCurves(ctx, W, H, curves, { redline: Math.min(e.limiter, c.lim.mech) });
  const w = $('#warns'); w.innerHTML = '';
  if (c.warn.length) c.warn.forEach(k => WARN_TEXT[k] && w.append(h('div', { class: 'warn' }, WARN_TEXT[k])));
  else w.append(h('div', { class: 'ok' }, 'Расчёт без проблем. Порядок работы: ' + firingOrder(e).join('-') + ' · баланс: ' + balanceInfo(e).text.toLowerCase()));
  if (c.warn.length) w.append(h('div', { class: 'hint' }, 'Порядок работы: ' + firingOrder(e).join('-') + ' · баланс: ' + balanceInfo(e).text.toLowerCase()));
  renderBuildBar();
}
function renderBuildBar() {
  const e = curEngine(), bar = $('#buildBar');
  if (!hwChanged(e)) { bar.hidden = true; return; }
  bar.hidden = false; bar.innerHTML = '';
  const p = buildPrice(e);
  const label = !e.built ? 'Собрать двигатель' : 'Собрать изменения';
  const btn = h('button', { class: 'btn accent', onclick: () => {
    const price = buildPrice(e);
    if (!canPay(price)) { toast('Не хватает ' + money(price - S.money)); return; }
    pay(price); e.built = hwSnapshot(e); e.health = 100; e.blown = null; save(); vibrate(30);
    toast(price >= 0 ? 'Собрано за ' + money(price) : 'Собрано, возврат ' + money(-price)); renderBuildBar(); D.reset = true;
  } }, label + ' · ' + (S.sandbox ? 'бесплатно' : p >= 0 ? money(p) : 'возврат ' + money(-p)));
  bar.append(btn);
  if (e.built) bar.append(h('button', { class: 'btn', onclick: () => { Object.assign(e, e.built); fixEngine(e); renderEngineTab(); save(); } }, 'Отменить'));
}

// анимация двигателя в конструкторе
const engAnim = { crank: 0, t: 0, parts: new Particles() };
let eng3d = null, car3d = null;
function engKey(e) { const k = hwSnapshot(e); k.coverColor = e.coverColor; return k; }
function hide3D(host) { if (G3.canvas && G3.canvas.parentNode === host) G3.canvas.style.display = 'none'; }
function want3D(view) { return view === '3d' && init3D(); }
function tickEngineView(dt) {
  const e = curEngine();
  const host = $('#tab-engine .viewer');
  const is3d = want3D(S.view);
  $('#btnXray').hidden = !is3d; $('#hint3d').hidden = !is3d;
  if (is3d) {
    if (!eng3d) eng3d = new Engine3D();
    show3D(host, eng3d); G3.canvas.style.display = '';
    $('#cvEngine').style.visibility = 'hidden';
    eng3d.xray = S.xray !== false;
    eng3d.setEngine(engKey(e));
    engAnim.crank = (engAnim.crank + dt * 360 * 1.4) % 720; engAnim.t += dt;
    eng3d.update({ crank: engAnim.crank, rpm: 900, spool: 0.2, egt: 0.1, highlight: SEC_HL[S.openSec], t: engAnim.t }, dt);
    render3D(eng3d, dt);
    return;
  }
  hide3D(host); $('#cvEngine').style.visibility = '';
  const { ctx, W, H } = fit($('#cvEngine'));
  engAnim.crank = (engAnim.crank + dt * 360 * 1.4) % 720; engAnim.t += dt;
  engAnim.parts.step(dt);
  drawEngine(ctx, e, W, H, { crank: engAnim.crank, rpm: 900, throttle: 0.05, spool: 0.15, egt: 0.15, view: S.view, highlight: SEC_HL[S.openSec], t: engAnim.t, emit: true, load: 0 }, engAnim.parts);
}
$$('#tab-engine .view-btns .seg').forEach(b => b.addEventListener('click', () => { S.view = b.dataset.view; save(); syncViewBtns(); }));
function syncViewBtns() {
  if (S.view === '3d' && !init3D()) S.view = 'front';
  $$('#tab-engine .view-btns .seg').forEach(b => { b.classList.toggle('on', b.dataset.view === S.view); if (b.dataset.view === '3d' && !G3.ok) b.hidden = true; });
  $('#btnXray').classList.toggle('on', S.xray !== false);
}
$('#btnXray').addEventListener('click', () => { S.xray = S.xray === false; save(); syncViewBtns(); });

// ================= ВКЛАДКА «СТЕНД» =================
const snd = new EngineSound();
const D = {
  on: false, starting: 0, rpm: 0, spool: 0, coolT: 25, egtS: 0, thrPedal: 0, thrEff: 0, idleI: 0.05, cut: false,
  sweep: null, load: 0, nos: false, crank: 0, lastThr: 0, popT: 0, t: 0, parts: new Particles(), out: null, reset: true,
  cause: {}, view: '3d', alert: '', alertT: 0, curPts: null, dmgAcc: 0,
};
const DMG_TEXT = {
  rod: 'Оборвался шатун: обороты выше предела деталей',
  valves: 'Клапаны встретились с поршнями: зависание клапанов',
  knock: 'Детонация прожгла поршни',
  stress: (w) => 'Не выдержали ' + w + ': давление в цилиндрах выше их прочности',
  egt: 'Прогорели выпускные клапаны: перегрев выхлопа',
  overheat: 'Пробило прокладку головки: двигатель перегрелся',
  turbo: 'Разрушилась турбина: раскрутка сверх предела',
  bearings: 'Провернуло вкладыши: масляному насосу не хватило производительности',
};
function dynoEngine() { return builtEngine(curEngine()); }
function dynoPrepare() {
  const be = dynoEngine();
  if (!be) return null;
  D.be = be; D.lim = limits(be); D.vp = veParams(be);
  const c = engineCurve(be); D.calc = c;
  D.brakeK = Math.max(5, c.peak.nm) / Math.max(1500, c.peak.hpRpm) * 1.1;
  D.I = inertia(be) + 0.15;
  snd.configure(be);
  return be;
}
function renderDynoTab() { stable($('#tab-dyno'), renderDynoTabRaw); }
function renderDynoTabRaw() {
  const e = curEngine();
  const be = dynoPrepare();
  $('#btnNos').hidden = !(be && be.nitrous);
  if (D.view === '3d' && !init3D()) D.view = 'front';
  $('#dynoView').textContent = DYNO_VIEWS[D.view];
  // настройки ЭБУ на ходу
  const tune = $('#dynoTune'); tune.innerHTML = '';
  if (!be) {
    tune.append(h('div', { class: 'warn' }, 'Двигатель ещё не собран. Соберите его на вкладке «Двигатель».'));
  } else {
    const sec = ENGINE_SPEC.find(s => s.id === 'ecu');
    const fields = sec.fields.filter(f => f.tune && f.type === 'range' && (!f.show || f.show(e)) && f.k !== 'idle' && f.k !== 'launch');
    fields.push(sec.fields.find(f => f.k === 'knockSensor'));
    const cfg = { onChange: (k) => { fixEngine(e, k); dynoPrepare(); save(); engDirty = true; }, open: () => null, setOpen: () => { } };
    fields.forEach(f => tune.append(renderField(f, e, cfg, () => renderDynoTab())));
    if (hwChanged(e)) tune.prepend(h('div', { class: 'warn' }, 'На стенде стоит собранная версия. Изменения железа ещё не собраны.'));
  }
  drawDynoGraph();
  updateHealth();
  renderDynoRes();
}
function updateHealth() {
  const e = curEngine();
  const hp = clamp(e.health, 0, 100);
  $('#healthBar').style.width = hp + '%';
  $('#healthBar').style.background = hp > 60 ? 'var(--green)' : hp > 25 ? 'var(--amber)' : 'var(--red)';
  $('#healthTxt').textContent = Math.round(hp) + '%';
}
function renderDynoRes() {
  const e = curEngine(), d = e.lastDyno, res = $('#dynoRes');
  if (D.sweep) { res.innerHTML = 'Идёт замер… <b>' + Math.round(D.rpm) + '</b> об/мин'; return; }
  if (!d) return;
  res.innerHTML = '';
  const diff = e.prevDyno ? d.hp - e.prevDyno.hp : null;
  res.append(h('div', null, 'Пик мощности: ', h('b', null, Math.round(d.hp) + ' л.с.'), ' при ', h('b', null, d.hpRpm), ' об/мин',
    diff != null ? h('span', { class: 'tag ' + (diff >= 0 ? 'g' : 'r'), style: 'margin-left:6px' }, (diff >= 0 ? '+' : '−') + Math.abs(Math.round(diff)) + ' л.с.') : null));
  res.append(h('div', null, 'Пик момента: ', h('b', null, Math.round(d.nm) + ' Н·м'), ' при ', h('b', null, d.nmRpm), ' об/мин', d.boost > 0.05 ? ' · наддув до ' + num(d.boost, 2) + ' бар' : ''));
  res.append(h('div', { class: 'hint' }, num(d.hp / displacement(builtEngine(e) || e), 0) + ' л.с./л · ' + num(d.hp / engineMass(builtEngine(e) || e), 2) + ' л.с./кг'));
}
function drawDynoGraph() {
  const e = curEngine();
  const { ctx, W, H } = fit($('#cvDynoGraph'));
  const curves = [];
  if (e.prevDyno && e.prevDyno.pts) curves.push({ pts: e.prevDyno.pts, dash: true, alpha: 0.5, width: 1.5 });
  if (D.sweep && D.sweep.pts.length > 1) curves.push({ pts: D.sweep.pts, boost: true });
  else if (e.lastDyno && e.lastDyno.pts) curves.push({ pts: e.lastDyno.pts, boost: true });
  if (!curves.length) curves.push({ pts: [{ rpm: 0, nm: 0, hp: 0 }, { rpm: (D.be ? D.be.limiter : 7000), nm: 0, hp: 0 }] });
  drawCurves(ctx, W, H, curves, { maxRpm: D.be ? Math.min(D.be.limiter, D.lim.mech * 1.05) : 7000, marker: D.on ? D.rpm : null });
}

function startEngine() {
  const e = curEngine(), be = dynoPrepare();
  if (!be) { toast('Сначала соберите двигатель'); return; }
  if (e.blown) { showBlown(e); return; }
  snd.init(); snd.resume(); snd.on = true; snd.setRoom('garage'); snd.configure(be); snd.volume = S.settings.vol;
  const test = engineAt(be, 1000, { throttle: 0.3 });
  if (test.flags.includes('nostart')) { toast('Не заводится: дизелю нужна степень сжатия от 14'); return; }
  const crankDur = FUELS[be.fuel].diesel ? 1.5 : displacement(be) > 4.5 ? 1.25 : 1.05;
  D.on = true; D.starting = crankDur; D.rpm = 150; D.cut = false; D.cause = {};
  if (D.coolT < 25) D.coolT = 25;
  snd.starter(crankDur); vibrate(40);
  $('#btnStart').textContent = 'Заглушить'; $('#btnStart').classList.add('red');
}
function stopEngine() {
  D.on = false; D.sweep = null; D.starting = 0;
  $('#btnStart').textContent = 'Запуск'; $('#btnStart').classList.remove('red');
  $('#btnSweep').disabled = false;
}
$('#btnStart').addEventListener('click', () => { D.on ? stopEngine() : startEngine(); });
$('#btnSweep').addEventListener('click', () => {
  if (!D.on) { startEngine(); if (!D.on) return; setTimeout(beginSweep, 1300); return; }
  beginSweep();
});
function beginSweep() {
  if (!D.on || D.sweep) return;
  D.sweep = { pts: [], start: 1500, maxBoost: 0, hold: 0, prep: true };
  $('#btnSweep').disabled = true;
}
$('#btnAuto').addEventListener('click', () => {
  const e = curEngine();
  autoTune(e); fixEngine(e); save(); renderDynoTab(); engDirty = true;
  toast('ЭБУ настроен: угол ' + num(e.timing, 1) + '°, λ ' + num(e.lambda, 2) + (isForced(e) ? ', наддув ' + num(e.boost, 2) + ' бар' : ''));
});
const nosBtn = $('#btnNos');
['pointerdown'].forEach(ev => nosBtn.addEventListener(ev, (x) => { x.preventDefault(); D.nos = true; nosBtn.classList.add('on'); }));
['pointerup', 'pointerleave', 'pointercancel'].forEach(ev => nosBtn.addEventListener(ev, () => { D.nos = false; nosBtn.classList.remove('on'); }));
$('#loadRange').addEventListener('input', (ev) => { D.load = ev.target.value / 100; $('#loadVal').textContent = ev.target.value + '%'; ev.target.style.setProperty('--p', ev.target.value + '%'); });
$('#loadRange').style.setProperty('--p', '0%');
const DYNO_VIEWS = { '3d': 'Вид: 3D', front: 'Вид: разрез', side: 'Вид: сбоку' };
$('#dynoView').addEventListener('click', () => { const order = G3.ok ? ['3d', 'front', 'side'] : ['front', 'side']; D.view = order[(order.indexOf(D.view) + 1) % order.length]; $('#dynoView').textContent = DYNO_VIEWS[D.view]; });

// педаль газа
function bindPedal(el, onVal) {
  let id = null;
  const calc = (ev) => { const r = el.getBoundingClientRect(); return clamp((ev.clientY - r.top) / r.height * 1.25 - 0.05, 0.05, 1); };
  const plate = el.querySelector('.pedal-plate');
  const show = (v) => { plate.style.transform = 'rotateX(' + (v * 28) + 'deg)'; el.classList.toggle('down', v > 0.02); };
  el.addEventListener('pointerdown', (ev) => { ev.preventDefault(); id = ev.pointerId; try { el.setPointerCapture(id); } catch (_) { } const v = calc(ev); onVal(v); show(v); snd.init(); snd.resume(); });
  el.addEventListener('pointermove', (ev) => { if (ev.pointerId !== id) return; const v = calc(ev); onVal(v); show(v); });
  const up = (ev) => { if (ev.pointerId !== id) return; id = null; onVal(0); show(0); };
  el.addEventListener('pointerup', up); el.addEventListener('pointercancel', up);
  el.addEventListener('contextmenu', (ev) => ev.preventDefault());
  return show;
}
const pedalShow = bindPedal($('#pedal'), (v) => { D.thrPedal = v; });
// клавиатура (для проверки на ПК)
const keys = {};
window.addEventListener('keydown', (ev) => { if (ev.repeat) return; keys[ev.code] = true; if (ev.code === 'ArrowUp' || ev.code === 'KeyW') { D.thrPedal = 1; R.thr = 1; } if (ev.code === 'ShiftLeft' || ev.code === 'KeyE') raceShift(); });
window.addEventListener('keyup', (ev) => { keys[ev.code] = false; if (ev.code === 'ArrowUp' || ev.code === 'KeyW') { D.thrPedal = 0; R.thr = 0; } });

function tickDyno(dt) {
  const e = curEngine();
  const be = D.be || dynoPrepare();
  D.t += dt;
  if (be) {
    const steps = Math.ceil(dt / (1 / 240));
    const h2 = dt / steps;
    for (let i = 0; i < steps; i++) dynoStep(be, e, h2);
  }
  // отрисовка
  const vis = Math.min(D.rpm / 60, 2 + D.rpm / 1500);
  D.crank = (D.crank + vis * 360 * dt) % 720;
  D.parts.step(dt);
  const o = D.out;
  const { ctx, W, H } = fit($('#cvDyno'));
  const bal = be ? balanceInfo(be).score : 80;
  const shake = D.on ? (1 - bal / 100) * 3 * clamp(D.rpm / 4000, 0.3, 1.5) + (D.cut ? 1.2 : 0) + (o && o.knock > 0.2 ? 1.5 : 0) : 0;
  const egtN = o && D.on ? clamp((D.egtS - 400) / 700, 0, 1) : clamp(D.egtS / 1100, 0, 0.3);
  const dhost = $('.dyno-view');
  if (be && want3D(D.view)) {
    if (!eng3d) eng3d = new Engine3D();
    show3D(dhost, eng3d); G3.canvas.style.display = ''; $('#cvDyno').style.visibility = 'hidden';
    eng3d.xray = S.xray !== false;
    eng3d.setEngine(engKey(be));
    eng3d.orbit.auto = D.on ? 0 : 0.18;
    eng3d.update({ crank: D.crank, rpm: D.on ? D.rpm : 0, spool: D.spool, egt: egtN, off: !D.on, shake, pop: D.popNow, t: D.t }, dt);
    render3D(eng3d, dt);
  } else { hide3D(dhost); $('#cvDyno').style.visibility = ''; }
  if (be && !want3D(D.view)) drawEngine(ctx, be, W, H, {
    crank: D.crank, rpm: D.on ? D.rpm : 0, throttle: D.thrEff, spool: D.spool, egt: egtN, view: D.view, t: D.t, emit: D.on, load: D.thrEff,
    shake, blown: e.blown, nitrous: D.nos && be.nitrous, pop: D.popNow, off: !D.on, blackSmoke: FUELS[be.fuel].diesel && D.thrEff > 0.6,
  }, D.parts);
  else { ctx.fillStyle = COL.bg; ctx.fillRect(0, 0, W, H); ctx.fillStyle = COL.dim; ctx.font = '14px sans-serif'; ctx.textAlign = 'center'; ctx.fillText('Соберите двигатель, чтобы поставить его на стенд', W / 2, H / 2); }
  D.popNow = false;
  drawGauges(be, o);
  // тревога
  const al = $('#dynoAlert');
  al.textContent = D.alertT > 0 && Math.floor(D.t * 4) % 2 === 0 ? D.alert : '';
  D.alertT -= dt;
  $('#pedalLbl').textContent = D.sweep ? 'ЗАМЕР' : 'ГАЗ ' + Math.round(D.thrPedal * 100) + '%';
  if (D.sweep || (D.on && Math.floor(D.t * 10) % 3 === 0)) { drawDynoGraph(); if (D.sweep) renderDynoRes(); }
  snd.update({ rpm: D.rpm, throttle: D.thrEff, load: D.load, spool: D.spool, on: D.on || D.rpm > 200, limiter: D.cut });
}
function setAlert(t) { D.alert = t; D.alertT = 0.6; }

function dynoStep(be, e, dt) {
  const lim = D.lim;
  if (!D.on) {
    D.rpm = Math.max(0, D.rpm - (D.rpm * 1.5 + 300) * dt);
    D.spool = Math.max(0, D.spool - dt * 0.8);
    D.coolT += (25 - D.coolT) * dt / 120; D.egtS += (200 - D.egtS) * dt / 3;
    D.thrEff = 0;
    D.out = null;
    return;
  }
  if (D.starting > 0) { D.starting -= dt; D.rpm = 150 + Math.random() * 60; if (D.starting <= 0) D.rpm = be.idle * 1.3; return; }
  // газ
  if (D.sweep && D.sweep.prep) {
    // сначала сбрасываем обороты до начала замера
    if (D.rpm <= D.sweep.start + 30) { D.sweep.prep = false; D.rpm = D.sweep.start - 100; }
  }
  let thr = D.sweep ? (D.sweep.prep ? 0 : 1) : D.thrPedal;
  // холостой ход: регулятор
  if (thr < 0.06) { D.idleI += (be.idle - D.rpm) * 0.00004 * dt * 60; D.idleI = clamp(D.idleI, 0.0, 0.25); thr = Math.max(thr, D.idleI); }
  // отсечка
  const maxR = Math.min(be.limiter, 30000);
  if (D.rpm >= maxR) { if (!D.cut) { D.cut = true; } }
  if (D.cut && D.rpm < maxR - 180) D.cut = false;
  if (D.cut) { if (Math.random() < dt * 25) { D.popNow = true; if (Math.random() < 0.4) snd.pop(); } setAlert('ОТСЕЧКА'); }
  // BOV
  if (hasTurbo(be) && D.lastThr > 0.6 && thr < 0.25 && D.spool > 0.35) { if (be.bov) snd.bov(); else setAlert('ПОМПАЖ'); D.spool *= be.bov ? 0.55 : 0.75; }
  if (be.antilag && thr < 0.2 && D.spool > 0.3 && Math.random() < dt * 14) { D.popNow = true; snd.pop(); }
  D.lastThr = thr;
  D.thrEff += (thr - D.thrEff) * Math.min(1, dt * 25);
  // турбина
  if (hasTurbo(be)) {
    const tp = turboParams(be);
    const st = smooth(tp.onset, tp.full, D.rpm);
    let tgt = st * Math.pow(D.thrEff, 1.3) * (0.6 + 0.4 * Math.min(1, D.load + (D.sweep ? 1 : 0)));
    if (be.antilag && D.thrEff < 0.2) tgt = Math.max(tgt, st * 0.75);
    const tau = tgt > D.spool ? tp.tau : tp.tau * 0.6;
    D.spool += (tgt - D.spool) * Math.min(1, dt / tau);
  } else D.spool = isForced(be) ? clamp(D.thrEff, 0, 1) : 0;
  const nos = D.nos && be.nitrous > 0;
  const o = engineAt(be, Math.max(400, D.rpm), { throttle: D.cut ? 0 : D.thrEff, spool: hasTurbo(be) ? D.spool : null, lim, vp: D.vp, coolT: D.coolT, nitrous: nos });
  D.out = o;
  let tq = D.cut ? -o.tFric : o.torque;
  if (D.rpm < 350) { stopEngine(); toast('Двигатель заглох'); return; }
  // нагрузка
  const brake = D.load * D.brakeK * D.rpm;
  if (D.sweep && D.sweep.prep) {
    D.rpm = Math.max(D.sweep.start, D.rpm - 3000 * dt);
  } else if (D.sweep) {
    // стенд сам ведёт обороты с постоянной скоростью
    const rate = 600;
    D.rpm += rate * dt;
    D.sweep.hold += dt;
    const bucket = Math.round(D.rpm / 100) * 100;
    const last = D.sweep.pts[D.sweep.pts.length - 1];
    if (D.rpm >= D.sweep.start && (!last || bucket > last.rpm)) {
      D.sweep.pts.push({ rpm: bucket, nm: Math.max(0, o.torque), hp: Math.max(0, o.power), boost: o.boost });
      D.sweep.maxBoost = Math.max(D.sweep.maxBoost, o.boost);
    }
    if (D.rpm >= Math.min(maxR, lim.mech * 1.1) - 50 || D.cut) finishSweep(e);
  } else {
    const acc = (tq - brake) / D.I; // рад/с²
    D.rpm += acc * 60 / (2 * Math.PI) * dt;
    D.rpm -= freeRevDrag(be, D.rpm, D.thrEff) * dt;
  }
  // температуры
  const heat = Math.max(0, o.power) * 0.7355;
  const cap = ({ stock: 180, large: 330, race: 550 })[be.radiator] * (be.oilCooler ? 1.15 : 1) * ({ mech: 1, electric: 1.08, dual: 1.16 })[be.fan || 'mech'];
  const tEq = Math.max(be.thermostat === 'low' ? 76 : 88, 25 + 65 * (heat + 12) / cap);
  D.coolT += (tEq - D.coolT) * dt / (tEq > D.coolT ? 22 : 12);
  D.egtS += (o.egt - D.egtS) * Math.min(1, dt * 3);
  // повреждения
  const dmg = {};
  if (D.rpm > lim.mech) dmg.rod = (D.rpm / lim.mech - 1) * 320;
  if (D.rpm > lim.valveFloat * 1.04) { dmg.valves = (D.rpm / lim.valveFloat - 1.04) * 420; setAlert('ЗАВИСАНИЕ КЛАПАНОВ'); }
  if (o.knock > 0.05) { dmg.knock = o.knock * o.knock * 9 * o.map + o.knock * 1.5; setAlert('ДЕТОНАЦИЯ'); }
  if (o.stress > lim.pLim) { dmg.stress = (o.stress / lim.pLim - 1) * 70; setAlert('ПЕРЕГРУЗКА ДЕТАЛЕЙ'); }
  if (D.egtS > 1000) { dmg.egt = (D.egtS - 1000) * 0.09; setAlert('ПЕРЕГРЕВ ВЫХЛОПА'); }
  if (D.coolT > 115) { dmg.overheat = (D.coolT - 115) * 0.7; setAlert('ПЕРЕГРЕВ'); }
  if (o.flags.includes('turbo') && D.spool > 0.8) dmg.turbo = 3;
  if (D.rpm > 8500 && be.oilPump !== 'high' && be.oilSystem !== 'dry' && !isRotary(be)) { dmg.bearings = (D.rpm - 8500) / 1000 * 9; setAlert('ПАДАЕТ ДАВЛЕНИЕ МАСЛА'); }
  if (o.flags.includes('lean') && D.thrEff > 0.5) setAlert('БЕДНАЯ СМЕСЬ');
  if (o.ecuRetard > 0.5 && D.thrEff > 0.5 && !D.alertT) setAlert('ЭБУ УБИРАЕТ УГОЛ');
  let total = 0;
  for (const k in dmg) { total += dmg[k]; D.cause[k] = (D.cause[k] || 0) + dmg[k] * dt; }
  if (total > 0) {
    e.health = Math.max(0, e.health - total * dt);
    D.dmgAcc += total * dt;
    if (D.dmgAcc > 1) { D.dmgAcc = 0; updateHealth(); save(); vibrate(15); }
    if (e.health <= 0) blowUp(e);
  }
}
function finishSweep(e) {
  const sw = D.sweep; D.sweep = null;
  $('#btnSweep').disabled = false;
  if (!sw || sw.pts.length < 5) return;
  let pk = { hp: 0, hpRpm: 0, nm: 0, nmRpm: 0, boost: sw.maxBoost };
  sw.pts.forEach(p => { if (p.hp > pk.hp) { pk.hp = p.hp; pk.hpRpm = p.rpm; } if (p.nm > pk.nm) { pk.nm = p.nm; pk.nmRpm = p.rpm; } });
  e.prevDyno = e.lastDyno; e.lastDyno = Object.assign(pk, { pts: sw.pts.filter((p, i) => i % 2 === 0 || i === sw.pts.length - 1) });
  D.thrPedal = 0;
  save(); renderDynoRes(); drawDynoGraph(); engDirty = true;
  vibrate(40);
  if (!e.blown) checkContracts(e);
}
function checkContracts(e) {
  const be = builtEngine(e), d = e.lastDyno;
  if (!be || !d) return;
  const got = CONTRACTS.filter(c => !S.done[c.id] && c.check(be, d));
  got.forEach(c => { S.done[c.id] = true; earn(c.reward); });
  if (got.length) {
    save();
    modal((box, close) => {
      box.append(h('h3', null, got.length > 1 ? 'Заказы выполнены!' : 'Заказ выполнен!'));
      got.forEach(c => box.append(h('p', null, h('b', null, c.title), ' — ', c.text)));
      box.append(h('div', { class: 'big-num' }, '+' + money(got.reduce((a, c) => a + c.reward, 0))));
      box.append(h('button', { class: 'btn accent', onclick: close }, 'Отлично'));
    });
    renderGarage();
  }
}
function blowUp(e) {
  let worst = 'stress', mv = -1;
  for (const k in D.cause) if (D.cause[k] > mv) { mv = D.cause[k]; worst = k; }
  const lim = D.lim;
  e.blown = worst === 'stress' ? DMG_TEXT.stress(lim.weakest) : DMG_TEXT[worst];
  e.health = 0; save();
  snd.bang(); vibrate(400);
  for (let i = 0; i < 60; i++) {
    const r = $('#cvDyno').getBoundingClientRect();
    D.parts.add({ x: r.width / 2 + (Math.random() - 0.5) * 60, y: r.height * 0.4, vx: (Math.random() - 0.5) * 200, vy: -Math.random() * 160, r: 4 + Math.random() * 6, grow: 25, life: 2.5, max: 2.5, color: i % 5 ? '#555a61' : '#ff7a2a', alpha: 0.5, g: 40 });
  }
  D.sweep = null;
  stopEngine();
  updateHealth();
  setTimeout(() => showBlown(e), 700);
}
function showBlown(e) {
  const be = builtEngine(e) || e;
  const price = Math.round(engineCost(be) * 0.3 / 10) * 10;
  modal((box, close) => {
    box.append(h('h3', null, 'Двигатель разрушен'), h('p', null, e.blown || 'Двигатель сломан.'));
    box.append(h('p', { class: 'hint' }, 'Подсказка: смотрите предупреждения в расчёте на вкладке «Двигатель» и используйте «Автонастройку ЭБУ».'));
    box.append(h('button', { class: 'btn accent', onclick: () => {
      if (!canPay(price)) { toast('Не хватает денег на ремонт'); return; }
      pay(price); e.blown = null; e.health = 100; save(); updateHealth(); close(); toast('Двигатель отремонтирован');
    } }, 'Капремонт · ' + (S.sandbox ? 'бесплатно' : money(price))));
    box.append(h('button', { class: 'btn', onclick: close }, 'Позже'));
  }, 'bad');
}

function drawGauges(be, o) {
  const { ctx, W, H } = fit($('#cvGauges'));
  ctx.fillStyle = '#121418'; ctx.fillRect(0, 0, W, H);
  if (!be) return;
  const maxR = Math.ceil(Math.max(be.limiter, D.lim.mech) * 1.1 / 1000) * 1000;
  const R1 = Math.min(H * 0.45, W * 0.23);
  drawGauge(ctx, R1 + 10, H / 2, R1, D.rpm / 1000, 0, maxR / 1000, { red: Math.min(be.limiter, D.lim.mech) / 1000, major: maxR > 12000 ? 2 : 1, minor: maxR > 12000 ? 1 : 0.5, label: '×1000 об/мин', digital: String(Math.round(D.rpm)), lamp: D.cut ? '#ff4d3d' : (o && o.knock > 0.1 ? '#ffb020' : null) });
  const bx = R1 * 2 + 24, bw = W - bx - 10;
  let y = 8;
  // мгновенные значения
  ctx.textBaseline = 'top';
  ctx.font = '700 15px ui-monospace, monospace'; ctx.textAlign = 'left'; ctx.fillStyle = '#58c4ff';
  ctx.fillText((o && D.on ? Math.max(0, Math.round(o.power)) : 0) + ' л.с.', bx, y);
  ctx.fillStyle = COL.amber; ctx.textAlign = 'right';
  ctx.fillText((o && D.on ? Math.max(0, Math.round(o.torque)) : 0) + ' Н·м', bx + bw, y);
  y += 24;
  if (isForced(be)) {
    const R2 = Math.min(34, bw * 0.22);
    const mb = Math.max(1, Math.ceil(be.boost + 0.5));
    const vac = o && D.on ? (o.boost > 0.02 ? o.boost : -(1 - o.map) * 0.9) : 0;
    drawGauge(ctx, bx + R2, y + R2, R2, vac, -1, mb, { major: mb > 3 ? 1 : 0.5, minor: mb > 3 ? 0.5 : 0.25, needle: '#4fa3ff', fmt: () => '' });
    ctx.textAlign = 'left'; ctx.fillStyle = COL.dim; ctx.font = '600 11px sans-serif';
    ctx.fillText('Наддув', bx + R2 * 2 + 8, y + 4);
    ctx.fillStyle = '#7cc0ff'; ctx.font = '700 17px ui-monospace, monospace';
    ctx.fillText((vac >= 0 ? '+' : '') + num(vac, 2) + ' бар', bx + R2 * 2 + 8, y + 20);
    if (hasTurbo(be)) { ctx.fillStyle = COL.dim; ctx.font = '11px sans-serif'; ctx.fillText('турбина ' + Math.round(D.spool * 100) + '%', bx + R2 * 2 + 8, y + 42); }
    y += R2 * 2 + 10;
  }
  if (bw < 60) return;
  const rows = [];
  rows.push(['Охлаждающая жидкость', clamp((D.coolT - 40) / 90, 0, 1), Math.round(D.coolT) + '°C', D.coolT > 110 ? COL.red : D.coolT > 100 ? COL.amber : COL.green]);
  rows.push(['Выхлоп (EGT)', clamp(D.egtS / 1150, 0, 1), Math.round(D.egtS) + '°C', D.egtS > 1000 ? COL.red : D.egtS > 920 ? COL.amber : '#c98a5a']);
  const lam = o ? o.lambda : be.lambda;
  rows.push(['Смесь λ / AFR', clamp((lam - 0.6) / 0.8, 0, 1), num(lam, 2) + ' / ' + num(lam * FUELS[be.fuel].stoich, 1), lam > 1.05 && !FUELS[be.fuel].diesel ? COL.red : COL.boost]);
  rows.push(['Детонация', clamp((o ? o.knock : 0) / 2, 0, 1), o && o.knock > 0.05 ? num(o.knock, 2) : 'нет', COL.red]);
  if (be.fuelSystem !== 'carb') rows.push(['Форсунки', clamp(o && o.injDuty ? o.injDuty : 0, 0, 1.2), Math.round((o && o.injDuty && D.on ? o.injDuty : 0) * 100) + '%', o && o.injDuty > 0.95 ? COL.red : COL.amber]);
  rows.push(['Давление масла', clamp((D.on ? 1 + D.rpm / 1500 : 0) / 7, 0, 1), num(D.on ? Math.min(6.5, 1 + D.rpm / 1500) * (curEngine().health > 20 ? 1 : 0.5) : 0, 1) + ' бар', COL.amber]);
  const rh = Math.max(20, Math.min(30, (H - y - 4) / rows.length));
  rows.forEach((r, i) => drawBar(ctx, bx, y + 14 + i * rh, bw, 5, r[1], r[0], r[2], r[3]));
}

// ================= ВКЛАДКА «МАШИНА» =================
const PAINT = ['#c8312b', '#e8c33a', '#2e6bd9', '#1f8a52', '#f2f3f5', '#1b1c1f', '#8a8f98', '#e36414', '#7a3ad8', '#0fb5c9', '#b5174f', '#4a3424'];
const CAR_SPEC = [
  { id: 'body', title: 'Кузов', icon: 'body', fields: [
    { k: 'body', label: 'Тип кузова', opts: Object.entries(BODIES).map(([k, b]) => [k, b.name, b.price, b.mass + ' кг, Cx ' + b.cd]) },
    { k: 'color', label: 'Цвет', type: 'color', palette: true, tune: true, opts: PAINT.map(c => [c, c, 0]) },
    { k: 'finish', label: 'Покрытие', tune: true, opts: [['gloss', 'Глянец', 0, ''], ['metal', 'Металлик', 0, ''], ['pearl', 'Перламутр', 0, ''], ['candy', 'Кэнди', 0, 'Глубокий насыщенный блеск'], ['matte', 'Матовое', 0, '']] },
    { k: 'stripe', label: 'Гоночная полоса', type: 'toggle', price: 0 },
    { k: 'kit', label: 'Обвес', opts: [['stock', 'Штатный', 0, ''], ['sport', 'Спортивный', 1800, 'Пороги, меньше сопротивления'], ['wide', 'Широкий кузов', 6500, 'Расширители арок, шире колея, +3% сцепления']] },
    { k: 'hood', label: 'Капот', opts: [['stock', 'Штатный', 0, ''], ['scoop', 'С воздухозаборником', 900, ''], ['carbon', 'Карбоновый', 2800, '−9 кг']] },
    { k: 'roof', label: 'Крыша', show: (c) => !['roadster', 'dragster'].includes(c.body), opts: [['stock', 'Штатная', 0, ''], ['carbon', 'Карбоновая', 2200, '−6 кг, ниже центр тяжести']] },
    { k: 'lights', label: 'Фары', opts: [['stock', 'Галогеновые', 0, ''], ['led', 'Светодиодные', 1200, 'Холодный свет и светящаяся полоса сзади']] },
    { k: 'tint', label: 'Тонировка', tune: true, opts: [['none', 'Нет', 0, ''], ['light', 'Лёгкая', 0, ''], ['dark', 'Тёмная', 0, ''], ['limo', 'Лимузин', 0, '']] },
    { k: 'seats', label: 'Сиденья', opts: [['stock', 'Штатные', 0, ''], ['bucket', 'Ковши', 1400, '−25 кг']] },
    { k: 'cage', label: 'Каркас безопасности', type: 'toggle', price: 2500, hint: '+35 кг, но кузов жёстче: +2% сцепления' },
    { k: 'reduction', label: 'Облегчение', opts: [[0, 'Нет', 0, ''], [1, 'Ст. 1 (−60 кг)', 1500, 'Без лишнего салона'], [2, 'Ст. 2 (−140 кг)', 3500, 'Пластиковые стёкла, ковши'], [3, 'Карбон (−240 кг)', 9000, 'Карбоновые панели']] },
  ] },
  { id: 'trans', title: 'Трансмиссия', icon: 'gear', fields: [
    { k: 'drive', label: 'Привод', opts: [['fwd', 'Передний', 0, ''], ['rwd', 'Задний', 1500, 'Вес переносится на ведущие колёса'], ['awd', 'Полный', 5000, 'Лучший старт, +70 кг']] },
    { k: 'gearbox', label: 'Коробка передач', opts: Object.entries(GEARBOXES).map(([k, g]) => [k, g.name, g.price, 'Переключение ' + num(g.shift, 2) + ' с']) },
    { k: 'finalDrive', label: 'Главная пара', type: 'range', min: 2.0, max: 6.5, step: 0.05, unit: '', price: 0, hint: 'Больше — резвее разгон, меньше — выше максималка' },
    { k: 'gearSpread', label: 'Ряд передач', type: 'range', min: 0.7, max: 1.35, step: 0.01, unit: '', price: 0, fmt: (c) => num(c.gearSpread, 2) + (c.gearSpread < 0.95 ? ' (короткий)' : c.gearSpread > 1.05 ? ' (длинный)' : ''), hint: 'Растягивает или сжимает передаточные числа' },
    { k: 'clutch', label: 'Сцепление', opts: [['stock', 'Штатное', 0, 'Держит до 380 Н·м'], ['sport', 'Спортивное', 600, 'До 650 Н·м'], ['race', 'Металлокерамика', 1500, 'До 1100 Н·м'], ['triple', 'Трёхдисковое', 4000, 'До 2200 Н·м']] },
    { k: 'diff', label: 'Дифференциал', show: (c) => c.drive !== 'awd', opts: [['open', 'Свободный', 0, ''], ['lsd', 'Самоблок', 1200, ''], ['locked', 'Блокированный', 600, 'Для дрэга']] },
  ] },
  { id: 'wheels', title: 'Колёса и шины', icon: 'wheel', fields: [
    { k: 'tire', label: 'Шины', opts: Object.entries(TIRES).map(([k, t]) => [k, t.name, t.price, 'Сцепление ' + num(t.mu, 2)]) },
    { k: 'tireWidth', label: 'Ширина шины', type: 'range', min: 145, max: 355, step: 10, unit: 'мм', price: (c) => Math.max(0, c.tireWidth - 195) * 8 },
    { k: 'profile', label: 'Профиль', type: 'range', min: 25, max: 80, step: 5, unit: '%', price: 0 },
    { k: 'rim', label: 'Диаметр диска', type: 'range', min: 13, max: 22, step: 1, unit: '″', price: (c) => Math.max(0, c.rim - 15) * 150 },
    { k: 'rimStyle', label: 'Диски', opts: [['steel', 'Штампованные', 0, ''], ['alloy', 'Литые', 400, ''], ['mesh', 'Сетка', 900, ''], ['forged', 'Кованые', 1600, 'Лёгкие'], ['black', 'Чёрные', 500, ''], ['gold', 'Золотые', 700, '']] },
  ] },
  { id: 'susp', title: 'Подвеска и тормоза', icon: 'susp', fields: [
    { k: 'suspension', label: 'Подвеска', opts: [['stock', 'Штатная', 0, ''], ['sport', 'Спортивная', 900, ''], ['coilover', 'Винтовая', 2500, 'Регулировка клиренса'], ['drag', 'Дрэг', 1800, 'Мягкий зад для старта']] },
    { k: 'rideHeight', label: 'Клиренс', type: 'range', min: (c) => c.suspension === 'stock' ? -20 : c.suspension === 'sport' ? -40 : -80, max: 80, step: 5, unit: 'мм', price: 0 },
    { k: 'caliper', label: 'Цвет суппортов', tune: true, opts: [['black', 'Чёрный', 0, ''], ['red', 'Красный', 0, ''], ['yellow', 'Жёлтый', 0, ''], ['blue', 'Синий', 0, ''], ['green', 'Зелёный', 0, ''], ['orange', 'Оранжевый', 0, '']] },
    { k: 'brakes', label: 'Тормоза', opts: [['stock', 'Штатные', 0, ''], ['sport', 'Спортивные', 800, ''], ['race', 'Гоночные', 2000, ''], ['ceramic', 'Карбон-керамика', 6000, '−18 кг']] },
  ] },
  { id: 'aero', title: 'Аэродинамика', icon: 'aero', fields: [
    { k: 'spoiler', label: 'Антикрыло', opts: [['none', 'Нет', 0, ''], ['lip', 'Губа', 200, ''], ['wing', 'Крыло', 900, 'Прижим'], ['big', 'Большое крыло', 1800, 'Много прижима, но и сопротивления']] },
    { k: 'tips', label: 'Насадки выхлопа', opts: [['single', 'Одна', 0, ''], ['dual', 'Две по краям', 300, ''], ['center', 'Две по центру', 400, ''], ['quad', 'Четыре', 700, '']] },
    { k: 'splitter', label: 'Сплиттер', type: 'toggle', price: 400 },
    { k: 'diffuser', label: 'Диффузор', type: 'toggle', price: 700 },
  ] },
];
function carCost(c) {
  let sum = 0;
  for (const sec of CAR_SPEC) for (const f of sec.fields) {
    if (f.show && !f.show(c)) continue;
    if (f.type === 'range') sum += val(f.price || 0, c) || 0;
    else if (f.type === 'toggle') { if (c[f.k]) sum += val(f.price || 0, c) || 0; }
    else if (f.type !== 'color') { const o = optList(f, c).find(x => x[0] === c[f.k]); if (o) sum += val(o[2], c) || 0; }
  }
  return sum;
}
function carBuildPrice(c) { const nc = carCost(c); if (!c.built) return nc; const d = nc - carCost(builtCar(c)); return d >= 0 ? d : Math.round(d * 0.5); }
function carEngineOf(c) { const e = S.engines.find(x => x.id === c.engineId); return e ? builtEngine(e) : null; }
let perfT = 0;
// штатные колёса для каждого кузова: [диаметр, профиль, ширина]
const WHEEL_STOCK = { hatch: [15, 55, 195], sedan: [16, 55, 205], bizsedan: [18, 45, 245], coupe: [17, 45, 225], wagon: [16, 55, 205], roadster: [16, 50, 205], crossover: [17, 60, 225], suv: [18, 60, 255], pickup: [18, 65, 265], sport: [19, 35, 255], super: [20, 30, 285], dragster: [15, 55, 195] };
function renderCarTab() {
  const c = curCar();
  $('#carName').innerHTML = ''; $('#carName').append(h('span', null, c.name), h('small', null, BODIES[c.body].name));
  // выбор двигателя
  const box = $('#carEngine'); box.innerHTML = '';
  const sel = h('select', { id: 'selEngine', onchange: (ev) => { c.engineId = ev.target.value || null; save(); updateCarStats(); } }, h('option', { value: '' }, '— без двигателя —'));
  S.engines.forEach(e => sel.append(h('option', { value: e.id, selected: c.engineId === e.id ? 'selected' : false, disabled: !e.built ? 'disabled' : false }, e.name + ' · ' + layoutCode(e) + (e.built ? '' : ' (не собран)') + (e.blown ? ' (сломан)' : ''))));
  box.append(sel);
  renderSpec($('#carSections'), CAR_SPEC, c, {
    open: () => S.openCarSec, setOpen: (id) => { S.openCarSec = id; save(); },
    onChange: (k, structural) => {
      if (k === 'body') {
        // штатные колёса кузова, если игрок их ещё не менял
        const prev = WHEEL_STOCK[carView.lastBody || 'hatch'] || WHEEL_STOCK.hatch, nw = WHEEL_STOCK[c.body];
        if (nw && c.rim === prev[0] && c.profile === prev[1] && c.tireWidth === prev[2]) { c.rim = nw[0]; c.profile = nw[1]; c.tireWidth = nw[2]; }
        carView.lastBody = c.body;
        const sm = $('#carName small'); if (sm) sm.textContent = BODIES[c.body].name;
      }
      save(); if (structural) updateCarStats(); else scheduleCar();
    },
  });
  carView.lastBody = c.body;
  updateCarStats();
}
let carRaf = 0;
function scheduleCar() { if (carRaf) return; carRaf = requestAnimationFrame(() => { carRaf = 0; updateCarStats(true); }); }
function updateCarStats(fast) { stable($('#tab-car'), () => updateCarStatsRaw(fast)); }
function updateCarStatsRaw(fast) {
  const c = curCar(), e = carEngineOf(c);
  const st = carStats(c, e);
  const curve = e ? engineCurve(e) : null;
  const hp = e ? (curEngineById(c.engineId).lastDyno ? curEngineById(c.engineId).lastDyno.hp : curve.peak.hp) : 0;
  const sb = $('#carStats'); sb.innerHTML = '';
  sb.append(h('div', { class: 'hp' }, h('b', null, Math.round(hp)), h('span', null, 'л.с.')),
    h('div', null, h('b', null, Math.round(st.mass)), h('span', null, 'кг')),
    h('div', null, h('b', null, Math.round(hp / st.mass * 1000)), h('span', null, 'л.с./т')),
    h('div', null, h('b', null, money(carCost(c)).replace('₽ ', '')), h('span', null, '₽ детали')));
  carView.c = c; carView.st = st;
  clearTimeout(perfT);
  perfT = setTimeout(() => stable($('#tab-car'), () => {
    const p = $('#carPerf'); p.style.minHeight = p.offsetHeight + 'px'; p.innerHTML = '';
    p.append(h('div', { class: 'card-h' }, h('span', null, 'Расчётный разгон')));
    if (!e) { p.append(h('div', { class: 'warn' }, 'Поставьте собранный двигатель')); return; }
    const r = carPerformance(builtCar(c) || c, e);
    const cell = (l, v) => h('div', null, h('span', null, l), h('b', null, v));
    p.append(h('div', { class: 'perf' }, cell('0–100', r.t100 ? num(r.t100, 1) + ' с' : '—'), cell('0–200', r.t200 ? num(r.t200, 1) + ' с' : '—'),
      cell('402 м', r.quarter ? num(r.quarter, 2) + ' с' : '—'), cell('Скорость', Math.round(r.quarterV) + ' км/ч'), cell('Максимум', Math.round(r.vmax) + ' км/ч'), cell('Сцепление', num(st.mu, 2))));
    const w = [];
    if (curve.peak.nm > st.clutchCap) w.push('Сцепление не держит ' + Math.round(curve.peak.nm) + ' Н·м — будет буксовать');
    if (curEngineById(c.engineId).blown) w.push('Двигатель сломан: отремонтируйте его на стенде');
    if (carChanged(c)) w.push('Расчёт для собранной версии машины. Соберите изменения.');
    w.forEach(t => p.append(h('div', { class: 'warn' }, t)));
    p.style.minHeight = '';
  }), fast ? 250 : 30);
  renderCarBuildBar();
}
function curEngineById(id) { return S.engines.find(e => e.id === id) || {}; }
function renderCarBuildBar() {
  const c = curCar(), bar = $('#carBuildBar');
  if (!carChanged(c)) { bar.hidden = true; return; }
  bar.hidden = false; bar.innerHTML = '';
  const p = carBuildPrice(c);
  bar.append(h('button', { class: 'btn accent', onclick: () => {
    const price = carBuildPrice(c);
    if (!canPay(price)) { toast('Не хватает ' + money(price - S.money)); return; }
    pay(price); c.built = carHw(c); save(); toast('Машина собрана'); vibrate(30); updateCarStats();
  } }, (c.built ? 'Собрать изменения · ' : 'Собрать машину · ') + (S.sandbox ? 'бесплатно' : p >= 0 ? money(p) : 'возврат ' + money(-p))));
  if (c.built) bar.append(h('button', { class: 'btn', onclick: () => { Object.assign(c, c.built); renderCarTab(); save(); } }, 'Отменить'));
}
const carView = { rot: 0 };
function tickCarView(dt) {
  const host = $('#tab-car .viewer');
  if (carView.c && init3D()) {
    if (!car3d) car3d = new Car3D();
    show3D(host, car3d); G3.canvas.style.display = ''; $('#cvCar').style.visibility = 'hidden';
    car3d.setCar(carView.c, carView.st, carEngineOf(carView.c));
    render3D(car3d, dt);
    return;
  }
  hide3D(host); $('#cvCar').style.visibility = '';
  const { ctx, W, H } = fit($('#cvCar'));
  ctx.fillStyle = COL.bg; ctx.fillRect(0, 0, W, H);
  const g = ctx.createLinearGradient(0, 0, 0, H); g.addColorStop(0, '#1a1d22'); g.addColorStop(1, '#101114');
  ctx.fillStyle = g; ctx.fillRect(0, 0, W, H);
  ctx.fillStyle = '#23262c'; ctx.fillRect(0, H * 0.84, W, H * 0.16);
  ctx.strokeStyle = 'rgba(255,255,255,.06)'; for (let x = 0; x < W; x += 30) { ctx.beginPath(); ctx.moveTo(x, H * 0.84); ctx.lineTo(x - 30, H); ctx.stroke(); }
  if (carView.c) drawCar(ctx, carView.c, W, H, { stats: carView.st, wheelRot: carView.rot });
}

// ================= ЗАЕЗД =================
function renderRace() {
  const c = curCar(), e = carEngineOf(c);
  const rc = $('#raceCar'); rc.innerHTML = '';
  const engRec = curEngineById(c.engineId);
  rc.append(h('div', { class: 'item cur' }, h('div', { class: 'item-h' }, h('b', null, c.name), h('span', null, BODIES[c.body].name)),
    h('div', { class: 'meta' }, e ? engRec.name + ' · ' + layoutCode(e) + ' · ' + Math.round((engRec.lastDyno || engineCurve(e).peak).hp) + ' л.с. · ' + Math.round(carStats(builtCar(c) || c, e).mass) + ' кг' : 'Без двигателя')));
  const list = $('#rivals'); list.innerHTML = '';
  RIVALS.forEach((r, i) => {
    const locked = !S.sandbox && i > S.unlocked;
    const best = S.best[i];
    const tr = TRACKS[r.track] || TRACKS.city;
    list.append(h('div', { class: 'item' + (locked ? ' done' : '') },
      h('div', { class: 'item-h' }, h('b', null, r.name), h('span', null, '≈' + r.hp + ' л.с. · ' + r.mass + ' кг')),
      h('div', { class: 'track-tag t-' + tr.theme }, h('b', null, tr.name), ' · ' + tr.desc),
      h('div', { class: 'meta' }, 'Взнос ' + (r.fee ? money(r.fee) : 'нет') + ' · приз ' + money(r.prize) + (best ? ' · ваш лучший ' + num(best, 2) + ' с' : '') + (S.beaten && S.beaten[i] ? ' · ' : ''), S.beaten && S.beaten[i] ? h('span', { class: 'tag g' }, 'побеждён') : null),
      h('div', { class: 'acts' }, h('button', { class: 'btn accent', disabled: locked ? 'disabled' : false, onclick: () => startRace(i) }, locked ? 'Закрыто' : 'На старт'))));
  });
}
const R = { on: false, thr: 0 };
function rivalLook(r) {
  return Object.assign(defaultCar(), { body: r.body || (r.hp > 2000 ? 'dragster' : r.hp > 900 ? 'super' : r.hp > 400 ? 'coupe' : r.hp > 150 ? 'sedan' : 'hatch'), color: r.color, rim: r.hp > 300 ? 18 : 15, tireWidth: r.hp > 300 ? 245 : 195, profile: r.hp > 300 ? 40 : 55, rimStyle: r.hp > 600 ? 'forged' : r.hp > 150 ? 'alloy' : 'steel', tire: 'sport', spoiler: r.hp > 300 ? 'wing' : 'none', rideHeight: r.hp > 300 ? -25 : 0, kit: r.hp > 600 ? 'wide' : r.hp > 300 ? 'sport' : 'stock', lights: r.hp > 300 ? 'led' : 'stock', tips: r.hp > 300 ? 'quad' : 'single', caliper: r.hp > 300 ? 'red' : 'black', hood: r.hp > 450 ? 'carbon' : 'stock' });
}
function drawSprite(ctx, sp, frontX, groundY, ppm, dist) {
  const k = ppm / sp.ppm;
  ctx.save(); ctx.translate(frontX, groundY); ctx.scale(-k, k);
  ctx.drawImage(sp.body, -sp.ox, -sp.groundPx);
  sp.axles.forEach((ax, i) => {
    const img = (i === 0 && sp.wheelF) ? sp.wheelF : sp.wheel;
    const rr = (i === 0 ? sp.rF : sp.rW);
    ctx.save(); ctx.translate(ax * sp.ppm, -rr * sp.ppm); ctx.rotate(dist / rr);
    ctx.drawImage(img, -img.width / 2, -img.height / 2); ctx.restore();
  });
  ctx.restore();
}
function rivalCar(r) {
  // упрощённая машина соперника
  return { r, v: 0, x: 0, gear: 0, rpm: 1000, t: 0, shiftT: 0, gears: r.hp > 2000 ? [1.8, 1.0] : [3.3, 2.1, 1.55, 1.22, 1.0, 0.83], fd: r.hp > 2000 ? 3.2 : 3.7, rw: r.hp > 2000 ? 0.45 : 0.31, mu: r.hp > 2000 ? 2.4 : r.hp > 600 ? 1.35 : r.hp > 250 ? 1.15 : 1.0, launched: false };
}
function rivalTorque(r, rpm) { const pk = r.peak * 0.72; const k = (rpm - pk) / (r.peak * 0.9); return Math.max(r.nm * 0.35, r.nm * (1 - k * k * 0.55)) * (rpm > r.peak * 1.15 ? 0.5 : 1); }
function stepRival(s, dt) {
  const r = s.r;
  if (!s.launched) return;
  s.t += dt;
  const ratio = s.gears[s.gear] * s.fd;
  const wheelRpm = s.v / s.rw * 60 / (2 * Math.PI);
  s.rpm = Math.max(r.peak * 0.55, wheelRpm * ratio);
  if (s.shiftT > 0) { s.shiftT -= dt; }
  let F = s.shiftT > 0 ? 0 : rivalTorque(r, s.rpm) * ratio * 0.9 / s.rw;
  const grip = s.mu * r.mass * 9.81 * 0.62;
  F = Math.min(F, grip);
  const drag = 0.5 * 1.2 * 0.32 * 2.0 * s.v * s.v + 0.013 * r.mass * 9.81;
  s.v = Math.max(0, s.v + (F - drag) / r.mass * dt);
  s.x += s.v * dt;
  if (s.rpm > r.peak * 1.12 && s.gear < s.gears.length - 1 && s.shiftT <= 0) { s.gear++; s.shiftT = 0.25; }
}
// подбор лучших оборотов старта (быстрее всего проезжает первые 18 м)
function bestLaunch(c, e) {
  const maxR = Math.min(e.limiter, limits(e).mech);
  let best = null;
  for (let r = 2500; r <= maxR - 200; r += 250) {
    const s = newCarState(c, e); s.rpm = r; s.spool = 0.3; s.launched = true;
    let t = 0; while (s.x < 18 && t < 6) { stepCar(s, 1 / 120, 1, {}); t += 1 / 120; }
    if (!best || t < best.t - 0.005) best = { r, t };
  }
  return best ? best.r : 3000;
}
function startRace(i) {
  const c = curCar(), e = carEngineOf(c), engRec = curEngineById(c.engineId);
  if (!e) { toast('Поставьте собранный двигатель на вкладке «Машина»'); return; }
  if (engRec.blown) { toast('Двигатель сломан: отремонтируйте его на стенде'); return; }
  if (!c.built) { toast('Сначала соберите машину'); return; }
  const r = RIVALS[i];
  const bc = builtCar(c);
  const gbAuto = !!GEARBOXES[bc.gearbox].auto;
  let auto = gbAuto || !!S.settings.autoShift;
  const myHp = Math.round((engRec.lastDyno || engineCurve(e).peak).hp), myMass = Math.round(carStats(bc, e).mass);
  modal((box, close) => {
    const tr = TRACKS[r.track] || TRACKS.city;
    box.append(h('h3', null, tr.name + ' · ' + tr.dist + ' м'));
    if (tr.grip < 0.9) box.append(h('p', { class: 'hint' }, 'Скользко: сцепление шин ' + Math.round(tr.grip * 100) + '%. Газ в пол на старте — пробуксовка.'));
    box.append(h('div', { class: 'vs' },
      h('div', null, h('b', null, c.name), h('span', null, myHp + ' л.с. · ' + myMass + ' кг')),
      h('div', { class: 'x' }, 'VS'),
      h('div', null, h('b', null, r.name), h('span', null, '≈' + r.hp + ' л.с. · ' + r.mass + ' кг'))));
    const modeRow = h('div', { class: 'chips' });
    const drawMode = () => { modeRow.innerHTML = ''; [[true, 'Автомат', 'Передачи сами'], [false, 'Ручная', 'Кнопка «Передача +»']].forEach(([v, n, d]) => modeRow.append(h('button', { class: 'chip' + (auto === v ? ' on' : ''), disabled: gbAuto && !v ? 'disabled' : false, onclick: () => { auto = v; drawMode(); } }, h('b', null, n), h('small', null, d)))); };
    drawMode();
    box.append(h('div', { class: 'f-h' }, h('label', null, 'Переключение передач')), modeRow);
    box.append(h('ol', { class: 'howto' },
      h('li', null, 'До старта держите педаль: обороты сами встанут на лучшие для старта.'),
      h('li', null, 'Машина рвёт с места на зелёный. Отпустили газ — потеряете время на реакции.'),
      h('li', null, auto ? 'Передачи переключаются сами — просто держите газ.' : 'Переключайтесь, когда загорятся красные огни, или касайтесь экрана сверху.')));
    box.append(h('button', { class: 'btn accent big', onclick: () => {
      if (!canPay(r.fee)) { toast('Не хватает денег на взнос'); return; }
      S.settings.autoShift = auto; save(); close(); beginRace(i, auto);
    } }, 'Старт' + (r.fee ? ' · взнос ' + money(r.fee) : '')));
    box.append(h('button', { class: 'btn', onclick: close }, 'Отмена'));
  });
}
function beginRace(i, auto) {
  const c = curCar(), e = carEngineOf(c);
  const r = RIVALS[i];
  pay(r.fee);
  const bc = builtCar(c);
  const track = TRACKS[r.track] || TRACKS.city;
  Object.assign(R, { on: true, idx: i, rival: r, track, dist: track.dist, me: newCarState(bc, e), op: rivalCar(r), phase: 'stage', t: 0, green: null, rt: null, opRt: 0.12 + Math.random() * 0.25 * (1 - i / RIVALS.length), done: false, thr: 0, e, c: bc, parts: new Particles(), cam: 0, myTime: null, opTime: null, fault: false });
  R.me.rpm = e.idle;
  R.me.st.mu *= track.grip; R.op.mu *= track.grip;
  R.spMe = R.spOp = null; R.r3d = null;
  try {
    if (init3D()) {
      R.camMode = S.settings.raceCam || 0;
      R.r3d = new Race3D({ c: bc, st: R.me.st, e }, { c: rivalLook(r), st: { rWheel: 0.31 } }, track);
      show3D($('#raceView'), R.r3d); G3.canvas.style.display = '';
      $('#cvRace').style.visibility = 'hidden';
    }
  } catch (err) { console.warn(err); R.r3d = null; $('#cvRace').style.visibility = ''; }
  R.lightT = 1.6 + Math.random() * 0.8;
  R.auto = auto; R.launchRpm = bestLaunch(bc, e);
  const cv = engineCurve(e);
  R.shiftAt = Math.min(cv.maxRpm * 0.98, Math.max(cv.peak.hpRpm * 1.08, cv.peak.hpRpm + 300));
  R.msg = ''; R.msgT = 0; R.pressedAtGreen = false;
  $('#raceMode').textContent = auto ? 'Автомат' : 'Ручная';
  $('#raceScreen').hidden = false;
  $('#btnRaceNos').hidden = !e.nitrous;
  snd.init(); snd.resume(); snd.on = true; snd.setRoom('outdoor'); snd.configure(e); snd.volume = S.settings.vol;
  stopEngine();
}
function raceShift() {
  if (!R.on || !R.me || R.phase !== 'run' || R.auto) return;
  const me = R.me, rpm = me.rpm;
  if (me.gear >= me.st.gears.length - 1 || me.shiftT > 0) return;
  const k = rpm / R.shiftAt;
  if (shiftUp(me)) {
    vibrate(20); if (hasTurbo(R.e) && R.e.bov && me.spool > 0.4) snd.bov();
    if (k > 0.94 && k < 1.05) { flash('ИДЕАЛЬНО!', '#3dff7a'); me.shiftT *= 0.6; }
    else if (k <= 0.94) flash(k < 0.8 ? 'РАНО' : 'ЧУТЬ РАНО', '#ffb020');
    else flash('ПОЗДНО', '#ff6a4d');
  }
}
function flash(t, col) { R.msg = t; R.msgCol = col; R.msgT = 0.8; }
$('#btnShift').addEventListener('pointerdown', (ev) => { ev.preventDefault(); raceShift(); });
$('#raceView').addEventListener('pointerdown', () => raceShift());
$('#cvRace').addEventListener('pointerdown', () => raceShift());
$('#btnRaceExit').addEventListener('click', () => endRace(true));
$('#btnRaceCam').addEventListener('click', (ev) => { ev.stopPropagation(); const order = [0, 3, 1, 2], names = { 0: 'сзади', 3: 'сбоку', 1: 'проезд', 2: 'с капота' }; R.camMode = order[(order.indexOf(R.camMode || 0) + 1) % order.length]; S.settings.raceCam = R.camMode; save(); toast('Камера: ' + names[R.camMode]); });
['pointerdown', 'touchstart'].forEach(x => $('#btnRaceCam').addEventListener(x, (ev) => ev.stopPropagation()));
const rnBtn = $('#btnRaceNos');
rnBtn.addEventListener('pointerdown', (ev) => { ev.preventDefault(); if (R.me) R.me.nitrous = true; rnBtn.classList.add('on'); });
['pointerup', 'pointerleave', 'pointercancel'].forEach(x => rnBtn.addEventListener(x, () => { if (R.me) R.me.nitrous = false; rnBtn.classList.remove('on'); }));
bindPedal($('#racePedal'), (v) => { R.thr = v > 0 ? 1 : 0; });
function endRace(abort) {
  if (R.r3d) { disposeTree(R.r3d.scene); R.r3d = null; if (G3.canvas && G3.canvas.parentNode === $('#raceView')) G3.canvas.remove(); }
  $('#cvRace').style.visibility = '';
  R.on = false; $('#raceScreen').hidden = true; snd.fadeOut(0.6);
  if (abort) toast('Заезд прерван');
  renderRace();
}
function tickRace(dt) {
  const me = R.me, op = R.op, e = R.e;
  R.t += dt;
  const steps = Math.ceil(dt / (1 / 240)), h2 = dt / steps;
  for (let i = 0; i < steps; i++) {
    let thr = R.thr;
    if (R.phase === 'stage' || R.phase === 'tree') {
      // до старта педаль работает как лаунч-контроль: держит лучшие обороты
      if (thr > 0.3) thr = me.rpm < R.launchRpm ? 1 : 0.04;
      stepCar(me, h2, thr, {});
    } else if (R.phase === 'run') {
      if (R.rt == null) { if (R.thr > 0.3) R.rt = R.t - R.green; else thr = 0; }
      stepCar(me, h2, thr, { autoShift: R.auto, shiftRpm: R.shiftAt });
      stepRival(op, h2);
      if (R.myTime == null && me.x >= R.dist) { R.myTime = R.t - R.green; R.trap = me.v * 3.6; }
      if (R.opTime == null && op.x >= R.dist) { R.opTime = op.t + R.opRt; }
      if (R.myTime != null && R.opTime != null) { R.phase = 'done'; setTimeout(showRaceResult, 600); }
      if (me.t > 90 && R.myTime == null) { R.myTime = 99; R.trap = me.v * 3.6; }
    } else {
      // после финиша: сбросили газ и тормозим — мотор звучит на сбросе, потом на холостых
      stepCar(me, h2, 0, {}); stepRival(op, h2);
      me.v = Math.max(0, me.v - 6 * h2);
      if (me.v < 3) { me.rpm += (e.idle - me.rpm) * Math.min(1, h2 * 3); }
    }
  }
  // фазы старта
  if (R.phase === 'stage' && R.t > 1.0) { R.phase = 'tree'; R.treeT = 0; }
  if (R.phase === 'tree') {
    R.treeT += dt;
    if (R.treeT >= R.lightT && R.green == null) { R.green = R.t; }
    if (R.green != null) {
      // зелёный: машина стартует сама; если газ уже нажат — реакция почти идеальная
      me.launched = true; R.phase = 'run'; vibrate(30);
      if (R.thr > 0.3) { R.rt = 0.03 + Math.random() * 0.04; R.green -= R.rt; flash('СТАРТ!', '#3dff7a'); }
      else R.rt = null;
    }
  }
  if (R.phase === 'run' && !op.launched && R.t - R.green > R.opRt) op.launched = true;
  if (R.phase === 'run' && R.rt == null && R.t - R.green > 1.5) { R.rt = R.t - R.green; }
  // подсказки на экране
  const hint = $('#raceHint');
  let ht = '';
  if (R.phase === 'stage' || R.phase === 'tree') ht = R.thr > 0.3 ? 'Обороты стоят на старте · ждите зелёный' : 'Нажмите и держите педаль газа';
  else if (R.phase === 'run' && R.rt == null) ht = 'ГАЗ!';
  hint.hidden = !ht; if (ht) hint.textContent = ht;
  R.msgT -= dt;
  const fl = $('#raceFlash'); fl.textContent = R.msgT > 0 ? R.msg : ''; fl.style.color = R.msgCol || '#fff';
  // звук
  snd.update({ rpm: me.rpm, throttle: R.phase === 'done' ? 0 : R.thr, load: me.launched ? 1 : 0, spool: me.spool, on: true, limiter: me.rpm >= Math.min(e.limiter, me.lim.mech) - 100 });
  drawRace(dt);
}
function showRaceResult() {
  if (!R.on) return;
  const win = R.myTime < R.opTime;
  const i = R.idx, r = R.rival;
  if (!S.best[i] || R.myTime < S.best[i]) S.best[i] = R.myTime;
  S.beaten = S.beaten || {};
  if (win) { earn(r.prize); S.beaten[i] = true; S.unlocked = Math.max(S.unlocked, i + 1); }
  save();
  modal((box, close) => {
    box.append(h('h3', null, win ? 'Победа!' : 'Поражение'));
    box.append(h('div', { class: 'big-num' }, num(R.myTime, 2) + ' с'));
    box.append(h('p', null, 'Реакция ' + num(R.rt || 0, 2) + ' с · скорость на финише ' + Math.round(R.trap) + ' км/ч'));
    box.append(h('p', null, r.name + ': ' + num(R.opTime, 2) + ' с · ' + (win ? 'вы быстрее на ' : 'вы медленнее на ') + num(Math.abs(R.opTime - R.myTime), 2) + ' с'));
    if (win) box.append(h('p', null, 'Приз: ', h('b', null, money(r.prize))));
    box.append(h('button', { class: 'btn accent', onclick: () => { close(); endRace(); } }, 'В гараж'));
    box.append(h('button', { class: 'btn', onclick: () => { close(); endRace(); startRace(i); } }, 'Ещё раз' + (r.fee ? ' · ' + money(r.fee) : '')));
  }, win ? '' : 'bad');
}
// фон города (детерминированный)
const city = (() => { const a = []; let s = 7; const rnd = () => (s = (s * 9301 + 49297) % 233280) / 233280; for (let i = 0; i < 60; i++) a.push({ w: 20 + rnd() * 50, h: 40 + rnd() * 120, win: rnd() }); return a; })();
function drawRace(dt) {
  if (R.r3d) { R.r3d.update(R, dt); render3D(R.r3d, dt); drawRaceHud(); return; }
  const { ctx, W, H } = fit($('#cvRace'));
  const me = R.me, op = R.op;
  const ppm = Math.min(W / 16, H / 6);
  R.cam += ((me.x - 4) - R.cam) * Math.min(1, dt * 6);
  const camX = R.cam - 2;
  // небо
  const g = ctx.createLinearGradient(0, 0, 0, H); g.addColorStop(0, '#0d1424'); g.addColorStop(0.6, '#1b2236'); g.addColorStop(1, '#2a2430');
  ctx.fillStyle = g; ctx.fillRect(0, 0, W, H);
  // город
  let x = -((camX * ppm * 0.2) % 2200);
  for (let k = 0; k < 3; k++) for (const b of city) {
    const bh = b.h * H / 400;
    ctx.fillStyle = '#161b28'; ctx.fillRect(x, H * 0.5 - bh, b.w, bh);
    ctx.fillStyle = 'rgba(255,210,120,0.35)';
    for (let yy = H * 0.5 - bh + 6; yy < H * 0.5 - 6; yy += 10) for (let xx = x + 4; xx < x + b.w - 4; xx += 8) if (((xx * 7 + yy * 13) | 0) % 5 < b.win * 3) ctx.fillRect(xx, yy, 3, 4);
    x += b.w + 6; if (x > W + 100) break;
  }
  // трибуны и отбойник
  const roadTop = H * 0.5;
  ctx.fillStyle = '#25272c'; ctx.fillRect(0, roadTop, W, H - roadTop);
  ctx.fillStyle = '#3a3d44'; ctx.fillRect(0, roadTop, W, 6);
  const laneOp = roadTop + (H - roadTop) * 0.38, laneMe = roadTop + (H - roadTop) * 0.86;
  ctx.strokeStyle = 'rgba(255,255,255,0.5)'; ctx.setLineDash([ppm * 2, ppm * 2]); ctx.lineDashOffset = camX * ppm; ctx.lineWidth = 2;
  ctx.beginPath(); ctx.moveTo(0, (laneOp + laneMe) / 2 + 2); ctx.lineTo(W, (laneOp + laneMe) / 2 + 2); ctx.stroke(); ctx.setLineDash([]);
  const sx = (wx) => (wx - camX) * ppm;
  // столбы освещения и отметки дистанции
  for (let m = Math.floor(camX / 20) * 20; m < camX + W / ppm + 20; m += 20) {
    const px = sx(m);
    ctx.fillStyle = '#4a4e56'; ctx.fillRect(px, roadTop - H * 0.18, 3, H * 0.18);
    ctx.fillStyle = 'rgba(255,220,150,0.8)'; ctx.fillRect(px - 6, roadTop - H * 0.18, 14, 3);
    if (m % 100 === 0 && m > 0 && m < R.dist) { ctx.fillStyle = '#e7e9ec'; ctx.font = '700 ' + Math.round(H * 0.06) + 'px sans-serif'; ctx.textAlign = 'center'; ctx.fillText(m + ' м', px, roadTop - H * 0.2); }
  }
  // старт и финиш
  [[0, '#e7e9ec'], [R.dist, null]].forEach(([m, c]) => {
    const px = sx(m);
    if (px < -40 || px > W + 40) return;
    if (c) { ctx.fillStyle = c; ctx.fillRect(px - 2, roadTop + 6, 4, H - roadTop); }
    else { for (let yy = roadTop + 6, k = 0; yy < H; yy += 8, k++) { ctx.fillStyle = k % 2 ? '#111' : '#eee'; ctx.fillRect(px - 4, yy, 8, 8); ctx.fillStyle = k % 2 ? '#eee' : '#111'; ctx.fillRect(px + 4, yy, 8, 8); } }
  });
  // «ёлка»
  const tx = sx(-1.2);
  if (tx > -30) {
    ctx.fillStyle = '#1a1b1e'; ctx.fillRect(tx - 8, roadTop - H * 0.32, 16, H * 0.32);
    const lit = R.phase === 'tree' ? Math.floor(R.treeT / (R.lightT / 3.2)) : (R.phase === 'run' || R.phase === 'done' ? 9 : -1);
    for (let k = 0; k < 3; k++) { ctx.fillStyle = lit > k && lit < 9 && R.green == null ? '#ffb020' : '#3a2d12'; ctx.beginPath(); ctx.arc(tx, roadTop - H * 0.28 + k * H * 0.07, 5, 0, Math.PI * 2); ctx.fill(); }
    ctx.fillStyle = R.green != null ? '#3dff7a' : '#123a20'; ctx.beginPath(); ctx.arc(tx, roadTop - H * 0.28 + 3 * H * 0.07, 6, 0, Math.PI * 2); ctx.fill();
  }
  // машины
  const opCar = { body: R.rival.body || (R.rival.hp > 2000 ? 'dragster' : R.rival.hp > 900 ? 'super' : R.rival.hp > 400 ? 'coupe' : 'sedan'), color: R.rival.color, rim: 17, tireWidth: 235, profile: 45, rimStyle: 'alloy', tire: 'sport', spoiler: R.rival.hp > 300 ? 'wing' : 'none', rideHeight: -20 };
  const opB = BODY_SHAPE[opCar.body] || BODY_SHAPE.sedan, meB = BODY_SHAPE[R.c.body] || BODY_SHAPE.sedan;
  if (R.spOp && R.spMe) {
    drawSprite(ctx, R.spOp, sx(op.x), laneOp, ppm, op.x);
    drawSprite(ctx, R.spMe, sx(me.x), laneMe, ppm, me.x);
  } else {
    drawCar(ctx, opCar, W, H, { scale: ppm, x: sx(op.x - opB.L), ground: laneOp, flip: true, stats: { rWheel: 0.31 }, wheelRot: op.x / 0.31 });
    drawCar(ctx, R.c, W, H, { scale: ppm, x: sx(me.x - meB.L), ground: laneMe, flip: true, stats: me.st, wheelRot: me.x / me.st.rWheel });
  }
  // дым от пробуксовки
  if (me.wheelspin > 0.05) for (let k = 0; k < 2; k++) R.parts.add({ x: sx(me.x - meB.L + meB.L - meB.wb[1] * 1) , y: laneMe - 4, vx: -60 - Math.random() * 60, vy: -20 - Math.random() * 30, r: 4, grow: 30, life: 1.2, max: 1.2, color: '#c9ccd2', alpha: 0.35 });
  if (me.launched && R.thr > 0.5 && me.rpm > me.lim.mech * 0.9 && Math.random() < 0.2) R.parts.add({ x: sx(me.x - meB.L), y: laneMe - 0.4 * ppm, vx: -120, vy: 0, r: 3, grow: 12, life: 0.15, max: 0.15, color: '#ff8a2a' });
  R.parts.step(dt); R.parts.draw(ctx);
  // HUD
  drawRaceHud();
}
function drawRaceHud() {
  const { ctx, W, H } = fit($('#cvRaceHud'));
  const me = R.me, e = R.e;
  ctx.fillStyle = '#0b0c0f'; ctx.fillRect(0, 0, W, H);
  const maxR = Math.ceil(Math.max(e.limiter, me.lim.mech) * 1.1 / 1000) * 1000;
  const red = Math.min(e.limiter, me.lim.mech);
  const Rg = Math.min(H * 0.3, W * 0.22);
  const zone = R.phase === 'run' || R.phase === 'done' ? [R.shiftAt * 0.95 / 1000, Math.min(red, R.shiftAt * 1.04) / 1000, 'rgba(61,255,122,0.85)'] : [(R.launchRpm - 300) / 1000, (R.launchRpm + 300) / 1000, 'rgba(61,255,122,0.85)'];
  drawGauge(ctx, W / 2, Rg + 26, Rg, me.rpm / 1000, 0, maxR / 1000, { red: red / 1000, zones: [zone], major: maxR > 12000 ? 2 : 1, minor: maxR > 12000 ? 1 : 0.5, label: '×1000 об/мин', digital: String(Math.round(me.rpm)) });
  // светодиоды переключения
  const leds = 10, opt = R.shiftAt;
  const lw = Math.min(22, (W * 0.8) / leds);
  for (let i = 0; i < leds; i++) {
    const th = opt * (0.7 + 0.3 * i / (leds - 1));
    const on = me.rpm >= th;
    const c = i < 4 ? '#3dff7a' : i < 8 ? '#ffb020' : '#ff3b30';
    ctx.fillStyle = on ? c : '#1e2024';
    ctx.beginPath(); ctx.arc(W / 2 - (leds - 1) / 2 * lw + i * lw, 12, lw * 0.32, 0, Math.PI * 2); ctx.fill();
  }
  const blink = me.rpm >= opt * 0.95 && R.phase === 'run' && !R.auto;
  $('#btnShift').classList.toggle('flash', blink && Math.floor(R.t * 10) % 2 === 0);
  $('#btnShift').hidden = !!R.auto;
  // скорость и передача
  ctx.textAlign = 'left'; ctx.textBaseline = 'top';
  ctx.fillStyle = COL.text; ctx.font = '800 ' + Math.round(Rg * 0.5) + 'px ui-monospace, monospace';
  ctx.fillText(String(Math.round(me.v * 3.6)), 14, 30);
  ctx.fillStyle = COL.dim; ctx.font = '600 12px sans-serif'; ctx.fillText('км/ч', 16, 30 + Rg * 0.52);
  ctx.textAlign = 'right'; ctx.fillStyle = COL.amber; ctx.font = '800 ' + Math.round(Rg * 0.6) + 'px ui-monospace, monospace';
  ctx.fillText(R.phase === 'run' || R.phase === 'done' ? String(me.gear + 1) : 'N', W - 14, 26);
  ctx.fillStyle = COL.dim; ctx.font = '600 12px sans-serif'; ctx.fillText('передача', W - 14, 26 + Rg * 0.62);
  if (hasTurbo(e) || isForced(e)) { ctx.fillStyle = '#7cc0ff'; ctx.font = '700 14px ui-monospace, monospace'; ctx.fillText('+' + num(Math.max(0, me.out ? me.out.boost : 0), 2) + ' бар', W - 14, 26 + Rg * 0.62 + 18); }
  // подсказка фазы
  ctx.textAlign = 'left'; ctx.font = '700 15px sans-serif';
  let msg = '';
  if (R.phase === 'stage' || R.phase === 'tree') msg = 'Старт на ' + Math.round(R.launchRpm / 100) * 100 + ' об/мин';
  else if (R.phase === 'run') msg = me.wheelspin > 0.1 ? 'Пробуксовка!' : (me.x < R.dist ? Math.round(R.dist - me.x) + ' м · ' + num(R.t - R.green, 1) + ' с' : 'Финиш!');
  ctx.fillStyle = msg.startsWith('ЗЕЛ') ? '#3dff7a' : me.wheelspin > 0.1 ? COL.amber : COL.text;
  ctx.fillText(msg, 14, Rg * 2 + 36);
  // прогресс двух машин
  const py = Rg * 2 + 60, px = 14, pw = Math.max(80, W - 150 - px);
  ctx.fillStyle = '#1e2024'; ctx.fillRect(px, py, pw, 4); ctx.fillRect(px, py + 10, pw, 4);
  ctx.fillStyle = R.rival.color; ctx.fillRect(px, py, pw * clamp(R.op.x / R.dist, 0, 1), 4);
  ctx.fillStyle = R.c.color; ctx.fillRect(px, py + 10, pw * clamp(me.x / R.dist, 0, 1), 4);
}

// ================= ГАРАЖ =================
function renderGarage() {
  const el = $('#engList'); el.innerHTML = '';
  S.engines.forEach(e => {
    const be = builtEngine(e) || e, c = engineCurve(be);
    const hp = e.lastDyno ? e.lastDyno.hp : c.peak.hp;
    const nameIn = h('input', { type: 'text', value: e.name, maxlength: 40, 'aria-label': 'Название', onchange: (ev) => { e.name = ev.target.value.trim() || 'Двигатель'; save(); } });
    el.append(h('div', { class: 'item' + (e.id === S.curE ? ' cur' : '') },
      h('div', { class: 'item-h' }, h('b', null, e.name), h('span', null, layoutCode(be) + ' · ' + num(displacement(be), 1) + ' л')),
      h('div', { class: 'meta' }, Math.round(hp) + ' л.с. ' + (e.lastDyno ? '(замер)' : '(расчёт)') + ' · ' + Math.round(engineMass(be)) + ' кг · ресурс ' + Math.round(e.health) + '% ',
        e.blown ? h('span', { class: 'tag r' }, 'сломан') : !e.built ? h('span', { class: 'tag d' }, 'не собран') : hwChanged(e) ? h('span', { class: 'tag a' }, 'есть изменения') : h('span', { class: 'tag g' }, 'собран')),
      e.id === S.curE ? h('label', { class: 'hint' }, 'Название', nameIn) : null,
      h('div', { class: 'acts' },
        e.id !== S.curE ? h('button', { class: 'btn accent', onclick: () => { S.curE = e.id; save(); stopEngine(); D.be = null; renderGarage(); toast('Выбран: ' + e.name); } }, 'Выбрать') : null,
        h('button', { class: 'btn', onclick: () => { const n = JSON.parse(JSON.stringify(e)); n.id = newId(); n.name = e.name + ' (копия)'; n.built = null; n.lastDyno = null; n.prevDyno = null; n.health = 100; n.blown = null; S.engines.push(n); save(); renderGarage(); } }, 'Копия'),
        S.engines.length > 1 ? h('button', { class: 'btn red', onclick: () => confirmDel('двигатель «' + e.name + '»', () => { S.engines = S.engines.filter(x => x !== e); if (S.curE === e.id) S.curE = S.engines[0].id; S.cars.forEach(c => { if (c.engineId === e.id) c.engineId = null; }); save(); renderGarage(); }) }, 'Удалить') : null)));
  });
  const cl = $('#carList'); cl.innerHTML = '';
  S.cars.forEach(c => {
    const nameIn = h('input', { type: 'text', value: c.name, maxlength: 40, 'aria-label': 'Название', onchange: (ev) => { c.name = ev.target.value.trim() || 'Машина'; save(); } });
    const eng = curEngineById(c.engineId);
    cl.append(h('div', { class: 'item' + (c.id === S.curC ? ' cur' : '') },
      h('div', { class: 'item-h' }, h('b', null, c.name), h('span', null, BODIES[c.body].name)),
      h('div', { class: 'meta' }, eng.name ? 'Двигатель: ' + eng.name : 'Без двигателя', ' ', c.built ? (carChanged(c) ? h('span', { class: 'tag a' }, 'есть изменения') : h('span', { class: 'tag g' }, 'собрана')) : h('span', { class: 'tag d' }, 'не собрана')),
      c.id === S.curC ? h('label', { class: 'hint' }, 'Название', nameIn) : null,
      h('div', { class: 'acts' },
        c.id !== S.curC ? h('button', { class: 'btn accent', onclick: () => { S.curC = c.id; save(); renderGarage(); toast('Выбрана: ' + c.name); } }, 'Выбрать') : null,
        S.cars.length > 1 ? h('button', { class: 'btn red', onclick: () => confirmDel('машину «' + c.name + '»', () => { S.cars = S.cars.filter(x => x !== c); if (S.curC === c.id) S.curC = S.cars[0].id; save(); renderGarage(); }) }, 'Удалить') : null)));
  });
  const ct = $('#contracts'); ct.innerHTML = '';
  CONTRACTS.forEach(c => ct.append(h('div', { class: 'item' + (S.done[c.id] ? ' done' : '') },
    h('div', { class: 'item-h' }, h('b', null, c.title), h('span', null, money(c.reward))),
    h('div', { class: 'meta' }, c.text, ' ', S.done[c.id] ? h('span', { class: 'tag g' }, 'выполнен') : null))));
  renderSettings();
}
function confirmDel(what, fn) {
  modal((box, close) => {
    box.append(h('h3', null, 'Удалить?'), h('p', null, 'Удалить ' + what + '? Это нельзя отменить.'));
    box.append(h('button', { class: 'btn red', onclick: () => { fn(); close(); } }, 'Удалить'), h('button', { class: 'btn', onclick: close }, 'Отмена'));
  });
}
$('#btnNewEngine').addEventListener('click', () => {
  modal((box, close) => {
    box.append(h('h3', null, 'Новый двигатель'), h('p', null, 'Выберите основу. Создать можно бесплатно — платите при сборке.'));
    PRESETS.forEach((p, i) => {
      const e = makePreset(i), c = engineCurve(e);
      box.append(h('button', { class: 'chip', style: 'min-height:56px', onclick: () => { e.id = newId(); S.engines.push(e); S.curE = e.id; S.openSec = 'layout'; save(); close(); stopEngine(); D.be = null; switchTab('engine'); } },
        h('b', null, p.name), h('small', null, layoutCode(e) + ' · ' + Math.round(c.peak.hp) + ' л.с. · сборка ' + money(engineCost(e)))));
    });
    box.append(h('button', { class: 'btn', onclick: close }, 'Отмена'));
  });
});
$('#btnNewCar').addEventListener('click', () => {
  const c = defaultCar(); c.id = newId(); c.name = 'Машина ' + (S.cars.length + 1); c.engineId = S.curE;
  S.cars.push(c); S.curC = c.id; save(); switchTab('car');
});
function renderSettings() {
  const st = $('#settings'); st.innerHTML = '';
  const vol = h('input', { type: 'range', min: 0, max: 100, step: 5, value: Math.round(S.settings.vol * 100), id: 'volRange' });
  vol.style.setProperty('--p', S.settings.vol * 100 + '%');
  vol.addEventListener('input', () => { S.settings.vol = vol.value / 100; snd.volume = S.settings.vol; vol.style.setProperty('--p', vol.value + '%'); save(); });
  st.append(h('div', { class: 'field' }, h('div', { class: 'f-h' }, h('label', { for: 'volRange' }, 'Громкость')), vol));
  const tgl = (label, hint, get, setv) => { const b = h('button', { class: 'tg' + (get() ? ' on' : ''), onclick: () => { setv(!get()); save(); renderSettings(); updateMoney(); } }); return h('div', { class: 'switch' }, h('div', null, h('label', null, label), h('small', null, hint)), b); };
  st.append(tgl('Песочница', 'Бесконечные деньги и все соперники открыты', () => S.sandbox, (v) => { S.sandbox = v; renderRace(); }));
  st.append(tgl('Вибрация', 'Отдача при переключениях и поломках', () => S.settings.vibro, (v) => S.settings.vibro = v));
  const gq = h('div', { class: 'chips' });
  [['auto', 'Авто', 'Снижается сама'], ['high', 'Высокая', ''], ['mid', 'Средняя', ''], ['low', 'Низкая', 'Для слабых телефонов'], ['2d', 'Без 3D', 'Только 2D']].forEach(([v, n, d]) => gq.append(h('button', { class: 'chip' + ((S.settings.gfx || 'auto') === v ? ' on' : ''), onclick: () => { S.settings.gfx = v; save(); applyGfx(); renderSettings(); } }, h('b', null, n), d ? h('small', null, d) : null)));
  st.append(h('div', { class: 'field', 'data-k': 'gfx' }, h('div', { class: 'f-h' }, h('label', null, 'Качество графики')), gq));
  st.append(h('div', { class: 'row btnrow' },
    h('button', { class: 'btn', onclick: exportSave }, 'Код сохранения'),
    h('button', { class: 'btn', onclick: importSave }, 'Загрузить код')));
  if (lastErrorText()) st.append(h('button', { class: 'btn', onclick: showLastError }, 'Последняя ошибка'));
  st.append(h('button', { class: 'btn red', onclick: () => confirmDel('весь прогресс (деньги, двигатели, машины)', () => { S = freshState(); save(); location.reload(); }) }, 'Сбросить прогресс'));
  if (canUpdate()) st.append(h('button', { class: 'btn', onclick: () => checkUpdate(true) }, 'Проверить обновления'));
  st.append(h('div', { class: 'hint' }, 'МоторСтудия ' + APP_VERSION));
}

// ================= ВКЛАДКИ И ЦИКЛ =================
let tab = 'engine';
function switchTab(t) {
  if (t !== 'dyno' && D.on) stopEngine();
  tab = t;
  $$('#tabs button').forEach(b => b.classList.toggle('on', b.dataset.tab === t));
  $$('.tab').forEach(s => s.hidden = s.id !== 'tab-' + t);
  $('#pedalWrap').hidden = t !== 'dyno';
  if (t === 'engine') { renderEngineTab(); syncViewBtns(); }
  if (t === 'dyno') { D.be = null; renderDynoTab(); }
  if (t === 'car') renderCarTab();
  if (t === 'race') renderRace();
  if (t === 'garage') renderGarage();
  if (t !== 'dyno') snd.silence();
}
$$('#tabs button').forEach(b => b.addEventListener('click', () => switchTab(b.dataset.tab)));
window.onBack = () => {
  if (!$('#modal').hidden) { closeModal(); return true; }
  if (R.on) { endRace(true); return true; }
  if (tab !== 'engine') { switchTab('engine'); return true; }
  return false;
};
window.onAppPause = () => { if (D.on) stopEngine(); snd.silence(); };
document.addEventListener('visibilitychange', () => { if (document.hidden) window.onAppPause(); });

let lastT = 0;
function loop(t) {
  const dt = Math.min(0.05, lastT ? (t - lastT) / 1000 : 0.016); lastT = t;
  try {
    if (G3.ok && G3.canvas && G3.canvas.isConnected && G3.canvas.style.display !== 'none') watchFps(dt);
    if (R.on) tickRace(dt);
    else if (tab === 'engine') tickEngineView(dt);
    else if (tab === 'dyno') tickDyno(dt);
    else if (tab === 'car') tickCarView(dt);
  } catch (err) { reportError(err, 'кадр'); }
  requestAnimationFrame(loop);
}
window.addEventListener('resize', () => { if (tab === 'engine') updateEngineStats(); if (tab === 'dyno') drawDynoGraph(); });

load();
applyGfx();
snd.volume = S.settings.vol;
updateMoney();
switchTab('engine');
requestAnimationFrame(loop);
setTimeout(() => { const sp = $('#splash'); if (sp) { sp.classList.add('out'); setTimeout(() => sp.remove(), 400); } }, 150);
setTimeout(() => {
  if (loadProblem) modal((box, close) => { box.append(h('h3', null, 'Проблема с сохранением'), h('p', null, loadProblem), h('button', { class: 'btn accent', onclick: close }, 'Понятно')); });
  const crash = getNativeCrash() || (function () { try { const t = localStorage.getItem('motorGarage.crash'); localStorage.removeItem('motorGarage.crash'); return t; } catch (_) { return null; } })();
  if (crash) modal((box, close) => { box.append(h('h3', null, 'В прошлый раз игра закрылась с ошибкой'), h('p', { class: 'hint' }, 'Прогресс сохранён. Если ошибка повторяется, пришлите этот текст:'), h('textarea', { class: 'code', readonly: 'readonly' }, crash), h('button', { class: 'btn accent', onclick: close }, 'Продолжить')); });
  if (canUpdate()) setTimeout(() => checkUpdate(false), 2500);
}, 600);

// ================= ОБНОВЛЕНИЕ ВНУТРИ ИГРЫ =================
// Игра спрашивает у телефона (Java) последнюю версию в релизе GitHub,
// показывает, что нового, скачивает APK и открывает установку поверх текущей версии.
var updManual = false, updBox = null;
function canUpdate() { try { return !!(window.Android && Android.checkUpdate); } catch (_) { return false; } }
function checkUpdate(manual) {
  updManual = manual;
  if (manual) toast('Проверяю обновления…');
  try { Android.checkUpdate(); } catch (e) { if (manual) toast('Не удалось проверить обновления'); }
}
window.onUpdateInfo = function (info) {
  if (!info) { if (updManual) toast('Нет связи с сервером обновлений'); return; }
  let mine = 0; try { mine = Android.appVersion(); } catch (_) { }
  if (!(info.code > mine)) { if (updManual) toast('У вас последняя версия ' + APP_VERSION); return; }
  if (!updManual && S.settings.skipUpd === info.code) return;
  modal((box, close) => {
    updBox = box;
    box.append(h('h3', null, 'Доступно обновление ' + (info.name || '')),
      h('p', null, info.notes || 'Новая версия игры.'),
      h('p', { class: 'hint' }, 'Прогресс сохранится: новая версия ставится поверх текущей.'),
      h('div', { class: 'upd-bar' }, h('i', { id: 'updFill' })),
      h('p', { class: 'hint', id: 'updState' }, ''),
      h('button', { class: 'btn accent', id: 'btnUpd', onclick: () => { save(); $('#btnUpd').disabled = true; $('#updState').textContent = 'Скачиваю…'; try { Android.downloadUpdate(); } catch (e) { $('#updState').textContent = 'Не удалось начать загрузку'; } } }, 'Обновить'),
      h('button', { class: 'btn', onclick: () => { S.settings.skipUpd = info.code; save(); close(); } }, 'Позже'));
  });
};
window.onUpdateProgress = function (p) {
  const f = $('#updFill'), st = $('#updState'); if (!f || !st) return;
  if (p < 0) { st.textContent = 'Ошибка загрузки. Проверьте интернет и попробуйте ещё раз.'; const b = $('#btnUpd'); if (b) b.disabled = false; return; }
  f.style.width = Math.round(p * 100) + '%';
  st.textContent = p >= 1 ? 'Готово. Подтвердите установку в окне Android.' : 'Скачиваю… ' + Math.round(p * 100) + '%';
  const b = $('#btnUpd'); if (p >= 1 && b) { b.disabled = false; b.textContent = 'Установить'; b.onclick = () => { try { Android.installUpdate(); } catch (_) { } }; }
};
window.onUpdatePermission = function () { const st = $('#updState'); if (st) st.textContent = 'Разрешите установку из этого приложения и вернитесь в игру — установка продолжится.'; };


// ================= НАДЁЖНОСТЬ =================
var errShown = 0;
function reportError(err, where) {
  const text = (where ? '[' + where + '] ' : '') + (err && (err.stack || err.message) || String(err));
  try { localStorage.setItem('motorGarage.lastError', new Date().toISOString() + '\n' + text.slice(0, 1500)); } catch (_) { }
  console.error(text);
  const now = Date.now();
  if (now - errShown > 8000) { errShown = now; toast('Ошибка исправлена на лету, игра продолжает работу'); }
}
function lastErrorText() { try { return localStorage.getItem('motorGarage.lastError'); } catch (_) { return null; } }
function showLastError() { modal((box, close) => { box.append(h('h3', null, 'Последняя ошибка'), h('textarea', { class: 'code', readonly: 'readonly' }, lastErrorText() || ''), h('button', { class: 'btn', onclick: () => { try { localStorage.removeItem('motorGarage.lastError'); } catch (_) { } close(); renderSettings(); } }, 'Очистить'), h('button', { class: 'btn accent', onclick: close }, 'Закрыть')); }); }
window.addEventListener('error', (ev) => reportError(ev.error || ev.message, 'скрипт'));
window.addEventListener('unhandledrejection', (ev) => reportError(ev.reason, 'промис'));
function getNativeCrash() { try { return window.Android && Android.takeCrash ? Android.takeCrash() : null; } catch (_) { return null; } }

// код сохранения: перенос на другой телефон
function exportSave() {
  let code = '';
  try { code = btoa(unescape(encodeURIComponent(JSON.stringify(S)))); } catch (err) { toast('Не удалось создать код'); return; }
  modal((box, close) => {
    const ta = h('textarea', { class: 'code', readonly: 'readonly' }, code);
    box.append(h('h3', null, 'Код сохранения'), h('p', { class: 'hint' }, 'Скопируйте код и вставьте его на другом телефоне в «Загрузить код».'), ta);
    box.append(h('button', { class: 'btn accent', onclick: () => {
      const ok = () => toast('Код скопирован');
      if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(code).then(ok).catch(() => { ta.select(); document.execCommand('copy'); ok(); });
      else { ta.select(); document.execCommand('copy'); ok(); }
    } }, 'Скопировать'), h('button', { class: 'btn', onclick: close }, 'Закрыть'));
  });
}
function importSave() {
  modal((box, close) => {
    const ta = h('textarea', { class: 'code', placeholder: 'Вставьте код сохранения' });
    box.append(h('h3', null, 'Загрузить сохранение'), h('p', { class: 'hint' }, 'Текущий прогресс будет заменён.'), ta);
    box.append(h('button', { class: 'btn accent', onclick: () => {
      try {
        const d = JSON.parse(decodeURIComponent(escape(atob(ta.value.trim()))));
        if (!d || !d.engines || !d.engines.length) throw new Error('в коде нет двигателей');
        localStorage.setItem(SAVE_KEY, JSON.stringify(d));
        location.reload();
      } catch (err) { toast('Код не подошёл: ' + err.message); }
    } }, 'Загрузить'), h('button', { class: 'btn', onclick: close }, 'Отмена'));
  });
}

// качество графики и автоснижение на слабых телефонах
var GFX_PR = { high: 1.75, mid: 1.25, low: 0.9 };
var gfxLevel = 'high';
function applyGfx() {
  const g = S.settings.gfx || 'auto';
  if (g === '2d') { G3.disabled = true; return; }
  G3.disabled = false;
  gfxLevel = g === 'auto' ? (S.settings.gfxAuto || 'high') : g;
  G3.quality = gfxLevel;
  if (G3.r) { G3.r.setPixelRatio(Math.min(window.devicePixelRatio || 1, GFX_PR[gfxLevel])); G3.w = 0; }
}
var fpsMon = { t: 0, n: 0 };
function watchFps(dt) {
  if ((S.settings.gfx || 'auto') !== 'auto' || !G3.ok || G3.disabled) return;
  fpsMon.t += dt; fpsMon.n++;
  if (fpsMon.t < 4) return;
  const fps = fpsMon.n / fpsMon.t; fpsMon.t = 0; fpsMon.n = 0;
  const cur = S.settings.gfxAuto || 'high';
  if (fps < 28 && cur !== 'low') {
    S.settings.gfxAuto = cur === 'high' ? 'mid' : 'low'; save(); applyGfx();
    toast('Графика снижена для плавности (' + Math.round(fps) + ' кадров/с)');
  }
}
