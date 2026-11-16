'use strict';
// ================= ЗВУК ДВИГАТЕЛЯ =================
// Звук собирается «по физике» на каждый отсчёт:
//  • коленвал вращается с текущими оборотами; в момент вспышки каждого цилиндра (по порядку работы
//    и реальным фазам) в выпуск уходит импульс давления — удар + «звон» коллектора + шум газа;
//  • импульсы идут по своим патрубкам (разная длина у литого коллектора, у оппозитника, по рядам у V8),
//    затем в трубу с отражениями (гребенчатый резонатор), катализатор и глушитель (фильтры);
//  • отдельно — впуск (шипение на каждом такте впуска), механика, дизельный «стук», стартер.
// Работает в AudioWorklet (отдельный поток); на старых WebView — через ScriptProcessor.

const SYNTH_SRC = `
class EngineSynth {
  constructor(sr) {
    this.sr = sr; this.theta = 0; this.rpm = 0; this.rpmT = 0; this.thr = 0; this.thrS = 0; this.load = 0;
    this.on = false; this.limiter = false; this.vol = 0;  this.volS = 0;
    this.crank = 0; this.catchT = 0; this.voices = [];
    this.cfg = null; this.seed = 12345;
    this.dl = [new Float32Array(8192), new Float32Array(8192)]; this.dw = 0;
    this.lpz = [0, 0]; this.m1 = 0; this.m2 = 0; this.m3 = 0; this.hp = 0; this.hpx = 0;
    this.inEnv = 0; this.ib1 = 0; this.ib2 = 0; this.clEnv = 0; this.cb1 = 0; this.cb2 = 0;
    this.stPh = 0; this.stPh2 = 0; this.mech = 0; this.tick = 0;
    this.stT = 0; this.relT = 0; this.armRel = 0; this.strain = 0; this.clickEnv = 0; this.clickT = 0; this.sb1 = 0; this.sb2 = 0; this.kb1 = 0; this.kb2 = 0;
  }
  rnd() { this.seed = (this.seed * 1664525 + 1013904223) >>> 0; return this.seed / 4294967296; }
  setConfig(c) { this.cfg = c; }
  setParams(p) {
    this.rpmT = p.rpm; this.thr = p.throttle; this.load = p.load || 0; this.on = !!p.on; this.limiter = !!p.limiter; this.vol = p.vol;
    if (p.crank) { this.crank = p.crank; this.stT = 0; this.relT = 0; }
  }
  trigger(i, combust) {
    const c = this.cfg; if (!c) return;
    let amp;
    const thr = this.thrS, idleish = this.rpm < c.idle * 1.4;
    if (!combust) amp = this.crank > 0 ? 0.13 * c.compDip / 0.3 : 0.07;                                    // только сжатие (прокрутка стартером, глушение)
    else {
      amp = (0.4 + 0.6 * Math.max(thr, this.load * 0.8)) * c.amp[i];
      // нестабильность: у ротора и большого перекрытия на холостых — «брап»
      amp *= 1 + (this.rnd() - 0.5) * (idleish ? c.jitterIdle : c.jitter);
      if (this.limiter && this.rnd() < 0.55) { amp = 0.04; }    // отсечка топлива
      if (thr < 0.06 && this.rpm > c.idle * 1.7) {               // торможение двигателем
        amp = 0.12;
        if (c.crackle > 0 && this.rnd() < c.crackle) amp = 1.3 + this.rnd();   // хлопки в выпуске
      }
      if (this.catchT > 0 && this.rnd() < 0.3) amp *= 0.15;     // первые вспышки «схватывает» неровно
    }
    if (this.voices.length > 40) this.voices.shift();
    // импульс не длиннее промежутка между вспышками: на высоких оборотах звук «поёт» на частоте вспышек
    const gapS = (c.cycle / c.phases.length) / Math.max(60, this.rpm * 6);
    const tau = Math.min(c.tau, gapS * 0.55);
    const fr = Math.max(c.fr, 1.3 / gapS);
    this.voices.push({ t: -c.delay[i], a: amp * (0.6 + 0.4 * Math.sqrt(c.tau / tau)), b: c.bank[i], comb: combust, tau, fr });
    if (combust && c.diesel) this.clEnv = Math.min(1.5, this.clEnv + 0.6 + 0.6 * (1 - this.thrS));
    this.inEnv = Math.min(1.5, this.inEnv + 0.5);
  }
  process(out, n) {
    const c = this.cfg, sr = this.sr, dt = 1 / sr;
    if (!c) { for (let k = 0; k < n; k++) out[k] = 0; return; }
    const cyc = c.cycle, ph = c.phases, N = ph.length;
    const two = 2 * Math.PI;
    const fc = c.mufFc, a1 = 1 - Math.exp(-two * fc / sr), a2 = 1 - Math.exp(-two * fc * 1.6 / sr);
    const lpLoop = 1 - Math.exp(-two * c.loopFc / sr);
    for (let k = 0; k < n; k++) {
      // --- обороты: обычные, прокрутка стартером, глушение ---
      let target = this.rpmT;
      let dip = 0;
      if (this.crank > 0) {
        // прокрутка: втягивающее реле щёлкает, стартер раскручивает вал; на каждом такте сжатия
        // вал «упирается» — обороты проседают, стартер натужно гудит ниже и громче
        if (this.stT === 0) { this.clickEnv = 1; this.clickT = 0; }
        this.stT += dt; this.crank -= dt;
        const spin = Math.min(1, this.stT / 0.14);
        const ph = (this.theta / cyc * N) % 1;
        const d = Math.cos(two * (ph - 0.08));
        dip = d > 0 ? d * d * d : 0;
        target = c.crankRpm * (0.25 + 0.75 * spin) * (1 - c.compDip * dip);
        this.rpm = target;
        if (this.crank <= 0) { this.catchT = 0.35; this.relT = 0.2; this.armRel = target * 13 / 60; this.stT = 0; this.clickEnv = 0.55; this.clickT = 0; }
      }
      this.strain += (dip - this.strain) * 0.01;
      if (this.catchT > 0) this.catchT -= dt;
      const kR = target > this.rpm ? 0.0016 : 0.0009;
      this.rpm += (target - this.rpm) * (this.crank > 0 ? 0.02 : kR);
      if (!this.on && this.crank <= 0) this.rpm += (0 - this.rpm) * 0.00025;
      this.thrS += (this.thr - this.thrS) * 0.003;
      this.volS += (this.vol - this.volS) * 0.002;
      // --- угол коленвала и вспышки ---
      const prev = this.theta;
      this.theta += this.rpm / 60 * 360 * dt;
      const wrap = this.theta >= cyc;
      if (wrap) this.theta -= cyc;
      const combust = this.on && this.crank <= 0 && this.rpm > 120;
      if (this.rpm > 30) for (let i = 0; i < N; i++) {
        const p = ph[i];
        const hit = wrap ? (p >= prev || p < this.theta) : (p >= prev && p < this.theta);
        if (hit) this.trigger(i, combust);
      }
      // --- импульсы в выпуске ---
      let b0 = 0, b1 = 0;
      for (let v = this.voices.length - 1; v >= 0; v--) {
        const o = this.voices[v];
        o.t += dt;
        if (o.t < 0) continue;
        const env = Math.exp(-o.t / o.tau);
        if (env < 0.002) { this.voices.splice(v, 1); continue; }
        const att = 1 - Math.exp(-o.t / 0.00035);
        const blow = o.a * env * att;
        const s = blow * (0.55 + 0.75 * Math.sin(two * o.fr * o.t) + c.rasp * (this.rnd() * 2 - 1) * (o.comb ? 1 : 0.3));
        if (o.b) b1 += s; else b0 += s;
      }
      // --- труба: отражения (гребенчатый резонатор с затуханием в петле) ---
      const w = this.dw;
      let y0, y1;
      const d0 = c.pipe[0], d1 = c.pipe[1];
      const r0 = this.dl[0][(w - d0 + 8192) & 8191], r1 = this.dl[1][(w - d1 + 8192) & 8191];
      this.lpz[0] += (r0 - this.lpz[0]) * lpLoop; this.lpz[1] += (r1 - this.lpz[1]) * lpLoop;
      if (c.dual) { y0 = b0 + c.fb * this.lpz[0]; y1 = b1 + c.fb * this.lpz[1]; }
      else { y0 = b0 + b1 + c.fb * this.lpz[0]; y1 = 0; }
      this.dl[0][w] = y0; this.dl[1][w] = y1; this.dw = (w + 1) & 8191;
      let x = y0 + y1;
      // --- катализатор и глушитель ---
      this.m1 += (x - this.m1) * a1; this.m2 += (this.m1 - this.m2) * a2;
      let ex = this.m2 * c.mufGain + (x - this.m2) * c.leak;
      // --- впуск: шипение на тактах впуска ---
      this.inEnv *= 0.9985;
      const nz = this.rnd() * 2 - 1;
      const fi = Math.min(0.45, two * (c.inF + this.rpm * c.inK) / sr);
      this.ib1 += fi * (nz - this.ib1 - 0.7 * this.ib2); this.ib2 += fi * this.ib1;
      const intake = this.ib1 * this.inEnv * (0.15 + this.thrS) * c.inGain * (this.on ? 1 : 0.3);
      // --- дизельный стук ---
      let clat = 0;
      if (c.diesel) {
        this.clEnv *= 0.9955;
        const fc2 = Math.min(0.45, two * 2600 / sr);
        this.cb1 += fc2 * (nz - this.cb1 - 0.35 * this.cb2); this.cb2 += fc2 * this.cb1;
        clat = this.cb1 * this.clEnv * 0.22;
      }
      // --- механика: шум ГРМ, растёт с оборотами ---
      const mech = (this.rnd() * 2 - 1) * 0.004 * Math.min(1.5, this.rpm / 5000) * c.mechGain;
      // --- стартер: мотор и шестерня бендикса ---
      let st = 0;
      if (this.crank > 0 || this.relT > 0) {
        // частота якоря стартера: обороты вала × передаточное число венца (~13)
        let arm, fade = 1;
        if (this.crank > 0) arm = Math.max(5, this.rpm * 13 / 60);
        else { this.relT -= dt; this.armRel += (95 - this.armRel) * 0.0012; arm = this.armRel; fade = Math.max(0, this.relT / 0.2); fade *= fade; }
        this.stPh = (this.stPh + arm / sr) % 1000;
        const commP = this.stPh * 12, meshP = this.stPh * 10;
        const hum = ((this.stPh % 1) * 2 - 1) * 0.05 + Math.sin(two * this.stPh * 2) * 0.04;
        const whine = Math.sin(two * commP) * 0.06 + Math.sin(two * meshP) * 0.045 + Math.sin(two * meshP * 2) * 0.02 + Math.sin(two * meshP * 3) * 0.008;
        const fb2 = Math.min(0.45, two * 2300 / sr);
        this.sb1 += fb2 * (nz - this.sb1 - 0.5 * this.sb2); this.sb2 += fb2 * this.sb1;
        const brush = this.sb1 * (0.55 + 0.45 * Math.sin(two * commP)) * 0.07;
        const strain = 0.7 + 0.6 * this.strain;
        st = (hum * (1 + this.strain) + whine * strain + brush * strain) * fade * c.starterGain;
      }
      // щелчок втягивающего реле (включение) и выход бендикса (отключение)
      if (this.clickEnv > 0.001) {
        this.clickT += dt; this.clickEnv *= 0.9965;
        const fc3 = Math.min(0.45, two * 1700 / sr);
        this.kb1 += fc3 * (nz - this.kb1 - 0.25 * this.kb2); this.kb2 += fc3 * this.kb1;
        st += (this.kb1 * 0.6 + Math.sin(two * 190 * this.clickT) * 0.35 * Math.exp(-this.clickT / 0.02)) * this.clickEnv * c.starterGain;
      }
      let s = (ex * c.loud + intake + clat + mech) ;
      // убрать постоянную составляющую
      const hpOut = s - this.hpx + 0.995 * this.hp; this.hpx = s; this.hp = hpOut;
      out[k] = (hpOut + st) * this.volS;
    }
  }
}
`;

class EngineSound {
  constructor() { this.ctx = null; this.on = false; this.volume = 0.8; this.mode = ''; }
  init() {
    if (this.ctx) return true;
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return false;
    const c = this.ctx = new AC();
    this.master = c.createGain(); this.master.gain.value = 0;
    const comp = c.createDynamicsCompressor(); comp.threshold.value = -12; comp.ratio.value = 5; comp.attack.value = 0.003; comp.release.value = 0.15;
    // «кузов»: лёгкий подъём низа, чтобы мотор звучал плотно на маленьком динамике
    const low = c.createBiquadFilter(); low.type = 'peaking'; low.frequency.value = 140; low.Q.value = 0.8; low.gain.value = 4;
    this.master.connect(low); low.connect(comp); comp.connect(c.destination);
    this.engIn = c.createGain(); this.engIn.gain.value = 1; this.engIn.connect(this.master);
    // шум для хлопков и сброса
    const nb = c.createBuffer(1, c.sampleRate * 2, c.sampleRate), d = nb.getChannelData(0);
    for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    this.noiseBuf = nb;
    // свист турбины и вой компрессора
    this.whistle = c.createOscillator(); this.whistle.type = 'sine';
    this.whG = c.createGain(); this.whG.gain.value = 0; this.whistle.connect(this.whG); this.whG.connect(this.master);
    this.whine = c.createOscillator(); this.whine.type = 'sawtooth';
    this.wnF = c.createBiquadFilter(); this.wnF.type = 'bandpass'; this.wnF.Q.value = 6;
    this.wnG = c.createGain(); this.wnG.gain.value = 0; this.whine.connect(this.wnF); this.wnF.connect(this.wnG); this.wnG.connect(this.master);
    // шипение турбины на впуске
    this.tnoise = c.createBufferSource(); this.tnoise.buffer = nb; this.tnoise.loop = true;
    this.tnF = c.createBiquadFilter(); this.tnF.type = 'bandpass'; this.tnF.Q.value = 1.4; this.tnF.frequency.value = 3500;
    this.tnG = c.createGain(); this.tnG.gain.value = 0; this.tnoise.connect(this.tnF); this.tnF.connect(this.tnG); this.tnG.connect(this.master);
    [this.whistle, this.whine, this.tnoise].forEach(n => n.start());
    this.startSynth();
    return true;
  }
  startSynth() {
    const c = this.ctx;
    const useSP = () => {
      if (this.mode) return;
      try {
        const Synth = new Function(SYNTH_SRC + '; return EngineSynth;')();
        this.synth = new Synth(c.sampleRate);
        const sp = c.createScriptProcessor(2048, 0, 1);
        sp.onaudioprocess = (ev) => this.synth.process(ev.outputBuffer.getChannelData(0), ev.outputBuffer.length);
        sp.connect(this.engIn); this.sp = sp; this.mode = 'sp';
        this.flush();
      } catch (e) { console.warn('звук недоступен', e); }
    };
    try {
      if (c.audioWorklet) {
        const code = SYNTH_SRC + `
class EngineProc extends AudioWorkletProcessor {
  constructor() { super(); this.s = new EngineSynth(sampleRate); this.port.onmessage = (ev) => { const m = ev.data; if (m.cfg) this.s.setConfig(m.cfg); if (m.p) this.s.setParams(m.p); }; }
  process(inputs, outputs) { const o = outputs[0][0]; if (o) this.s.process(o, o.length); return true; }
}
registerProcessor('engine-proc', EngineProc);`;
        // из file:// работает только data:-адрес (blob и файлы блокируются)
        const url = 'data:application/javascript;charset=utf-8,' + encodeURIComponent(code);
        const t = setTimeout(useSP, 2500);
        c.audioWorklet.addModule(url).then(() => {
          clearTimeout(t); if (this.mode) return;
          this.node = new AudioWorkletNode(c, 'engine-proc', { numberOfInputs: 0, outputChannelCount: [1] });
          this.node.connect(this.engIn); this.mode = 'worklet';
          this.flush();
        }).catch(() => { clearTimeout(t); useSP(); });
      } else useSP();
    } catch (e) { useSP(); }
  }
  send(m) {
    if (this.mode === 'worklet') this.node.port.postMessage(m);
    else if (this.mode === 'sp') { if (m.cfg) this.synth.setConfig(m.cfg); if (m.p) this.synth.setParams(m.p); }
    else { if (m.cfg) this.pendingCfg = m.cfg; if (m.p) this.pendingP = Object.assign(this.pendingP || {}, m.p); }
  }
  flush() { if (this.pendingCfg) this.send({ cfg: this.pendingCfg }); if (this.pendingP) this.send({ p: this.pendingP }); this.pendingCfg = this.pendingP = null; }
  resume() { if (this.ctx && this.ctx.state === 'suspended') this.ctx.resume(); }

  // Параметры звука из устройства двигателя
  configure(e) {
    if (!this.ctx) return;
    this.e = e;
    const rot = isRotary(e), two = e.cycle === 2 && !rot, n = e.cyl;
    const cycle = (two || rot) ? 360 : 720;
    let phases = cylPhases(e).slice();
    // V-образная «двойка»: неравномерные интервалы вспышек (как у мотоциклов)
    if (e.type === 'v' && n === 2 && !two) phases = [0, 360 + (e.vAngle || 90)];
    if (e.type === 'inline' && n === 2 && !two) phases = [0, 360];
    // какому ряду (трубе) принадлежит цилиндр
    const bank = phases.map((_, i) => {
      if (n < 4 || e.type === 'inline' || e.type === 'radial' || rot) return i % 2 && e.exhLayout === 'dual' ? 1 : 0;
      if ((e.type === 'v' && n === 8 && e.crankPlane !== 'flat') || e.type === 'boxer') return (i + 1) % 2;
      return i < n / 2 ? 0 : 1;
    });
    // разная длина патрубков: литой — сильно, «паук» — меньше, равнодлинный — одинаково
    const spread = ({ cast: 0.0026, tubular: 0.0012, equal: 0.0001 })[e.exhManifold] || 0.002;
    let seed = n * 7 + (e.type || '').length * 13;
    const r = () => { seed = (seed * 9301 + 49297) % 233280; return seed / 233280; };
    const delay = phases.map((_, i) => spread * r() + (e.type === 'boxer' && e.exhManifold !== 'equal' ? (bank[i] ? 0.0022 : 0) : 0));
    const amp = phases.map(() => 1 + (r() - 0.5) * (e.exhManifold === 'equal' ? 0.05 : e.exhManifold === 'tubular' ? 0.15 : 0.28));
    // крестообразный V8 с одной трубой — фирменное «бульканье»: ряды приходят в трубу неравномерно
    const ccCyl = displacement(e) * 1000 / Math.max(1, n);
    const dia = e.exhDia || 50;
    let fr = 3300 / Math.sqrt(Math.max(50, ccCyl)) * Math.pow(50 / dia, 0.25);
    let tau = 0.0048, rasp = 0.35, jitter = 0.08, jitterIdle = 0.18, mechGain = 1;
    if (rot) { fr = 420; tau = 0.0026; rasp = 0.75; jitter = 0.14; jitterIdle = 0.75; mechGain = 0.3; }   // ротор: резкий «жужжащий» звук и «брап» на холостых
    if (two) { fr = 650; tau = 0.0032; rasp = 0.6; jitterIdle = 0.4; }                                   // двухтактник: звонкий «ринг-динг»
    const diesel = !!FUELS[e.fuel].diesel;
    if (diesel) { tau = 0.006; rasp = 0.2; fr *= 0.8; mechGain = 2; }
    if (e.type === 'radial') { jitterIdle = 0.3; mechGain = 2.5; }
    if (e.valvetrain === 'ohv') mechGain *= 1.4;
    if (e.lifters === 'solid') mechGain *= 1.6;
    if (e.camDur > 262) jitterIdle += Math.min(0.6, (e.camDur - 262) / 60);   // «злой» распредвал — неровный холостой
    // выпускная система
    const muf = e.muffler || 'stock';
    const L = ({ stock: 3.8, sport: 3.3, straight: 2.7, none: 1.1 })[muf];
    const sr = this.ctx.sampleRate, cGas = 470;
    const pipe0 = Math.round(sr * 2 * L / cGas), pipe1 = Math.round(sr * 2 * (L * (e.exhManifold === 'equal' ? 1.0 : 1.07)) / cGas);
    let mufFc = ({ stock: 420, sport: 900, straight: 2100, none: 5200 })[muf];
    let mufGain = ({ stock: 0.55, sport: 0.8, straight: 1.0, none: 1.15 })[muf];
    let leak = ({ stock: 0.03, sport: 0.08, straight: 0.2, none: 0.45 })[muf];
    if (e.cat) { mufFc *= 0.82; mufGain *= 0.9; leak *= 0.7; }
    if (e.exhWrap) leak *= 0.85;
    const fb = ({ stock: 0.3, sport: 0.42, straight: 0.52, none: 0.18 })[muf] + (two && e.tunedPipe ? 0.25 : 0);
    const loud = (0.55 + Math.min(0.35, (dia - 40) / 200)) * (e.exhLayout === 'dual' ? 1.1 : 1) * (two ? 0.7 : 1);
    const crackle = muf === 'stock' ? 0.004 : muf === 'sport' ? 0.02 : 0.05;
    const itb = e.manifold === 'itb';
    // прокрутка: большой объём и дизель крутятся медленнее и тяжелее, ротор — быстро и ровно
    const disp = displacement(e);
    let crankRpm = clamp(290 - disp * 22, 150, 280), compDip = clamp(0.18 + e.cr / 60, 0.25, 0.55);
    if (diesel) { crankRpm *= 0.85; compDip = 0.55; }
    if (rot) { crankRpm = 260; compDip = 0.15; }
    if (n >= 10) compDip *= 0.6;
    const starterGain = diesel || disp > 4.5 ? 1.25 : 1;
    const cfg = {
      crankRpm, compDip, starterGain,
      cycle, phases, bank, delay, amp, fr, tau, rasp, jitter, jitterIdle, mechGain, diesel,
      pipe: [Math.min(8000, pipe0), Math.min(8000, pipe1)], dual: e.exhLayout === 'dual' && n > 1, fb, loopFc: 1800,
      mufFc, mufGain, leak, loud, crackle, idle: e.idle || 900,
      inF: itb ? 900 : 350, inK: itb ? 0.35 : 0.15, inGain: (itb ? 0.5 : 0.12) * (isForced(e) ? 0.5 : 1),
    };
    this.send({ cfg });
  }
  // p: { rpm, throttle, load, spool, on, limiter }
  update(p) {
    if (!this.ctx || !this.e) return;
    const e = this.e, t = this.ctx.currentTime;
    const on = p.on ? 1 : 0;
    this.send({ p: { rpm: p.rpm, throttle: p.throttle, load: p.load || 0, on: !!p.on, limiter: !!p.limiter, vol: this.volume * 0.9 } });
    const set = (param, v, tc) => param.setTargetAtTime(v, t, tc || 0.03);
    set(this.master.gain, this.on ? 1 : 0, 0.05);
    if (hasTurbo(e)) {
      const s = p.spool || 0;
      set(this.whistle.frequency, 1800 + s * 6500 * (50 / e.turboSize), 0.08);
      set(this.whG.gain, s * s * 0.025 * on * (e.muffler === 'stock' ? 0.6 : 1) * this.volume, 0.08);
      set(this.tnG.gain, s * p.throttle * 0.05 * on * this.volume, 0.08);
      set(this.tnF.frequency, 2500 + s * 3000, 0.1);
    } else { set(this.whG.gain, 0, 0.05); set(this.tnG.gain, 0, 0.05); }
    if (hasSC(e)) {
      const f = p.rpm * (e.induction === 'sc_cent' ? 2.2 : 0.55);
      set(this.whine.frequency, Math.max(30, f), 0.03); set(this.wnF.frequency, Math.max(60, f), 0.03);
      set(this.wnG.gain, (0.01 + p.throttle * 0.04) * clamp(p.rpm / 6000, 0, 1) * on * this.volume, 0.05);
    } else set(this.wnG.gain, 0, 0.05);
  }
  burst(freq, q, dur, gain) {
    if (!this.ctx || !this.on) return;
    const c = this.ctx, t = c.currentTime;
    const src = c.createBufferSource(); src.buffer = this.noiseBuf;
    const f = c.createBiquadFilter(); f.type = 'bandpass'; f.frequency.value = freq; f.Q.value = q;
    const g = c.createGain(); g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(gain * this.volume, t + 0.004); g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    src.connect(f); f.connect(g); g.connect(this.master);
    src.start(t, Math.random()); src.stop(t + dur + 0.05);
  }
  // хлопок в выпуске: удар + короткий раскат
  pop() { this.burst(140 + Math.random() * 160, 0.9, 0.09, 1.2); this.burst(900 + Math.random() * 600, 0.7, 0.05, 0.5); }
  bov() { this.burst(2600, 0.6, 0.45, 0.6); this.burst(5200, 0.8, 0.25, 0.25); }
  bang() { this.burst(90, 0.5, 1.5, 3.5); this.burst(900, 0.4, 0.8, 1.5); }
  // прокрутка стартером: синтезатор сам крутит вал ~200 об/мин с проседаниями на сжатии, потом «схватывает»
  starter(dur) {
    if (!this.ctx) return;
    this.on = true;
    this.send({ p: { crank: dur, rpm: 200, throttle: 0, on: true, vol: this.volume * 0.9 } });
    this.master.gain.setTargetAtTime(1, this.ctx.currentTime, 0.02);
  }
  silence() { if (this.ctx) { this.master.gain.setTargetAtTime(0, this.ctx.currentTime, 0.08); this.send({ p: { rpm: 0, throttle: 0, on: false, vol: 0 } }); } }
}
