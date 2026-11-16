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
    this.on = false; this.limiter = false; this.vol = 0; this.volS = 0;
    this.crank = 0; this.catchT = 0; this.catchAge = 9; this.voices = [];
    this.cfg = null; this.seed = 12345; this.w = 0;
    this.hdrF = []; this.hdrB = []; this.hdrLp = [];
    this.pF = [new Float32Array(8192), new Float32Array(8192)]; this.pB = [new Float32Array(8192), new Float32Array(8192)];
    this.pLp = [0, 0]; this.prevTail = [0, 0];
    this.m1 = 0; this.m2 = 0; this.hp = 0; this.hpx = 0; this.body = 0; this.body2 = 0;
    this.inEnv = 0; this.ib1 = 0; this.ib2 = 0; this.clEnv = 0; this.cb1 = 0; this.cb2 = 0;
    this.stPh = 0; this.stT = 0; this.relT = 0; this.armRel = 0; this.strain = 0; this.clickEnv = 0; this.clickT = 0;
    this.gn = 0; this.lk = 0; this.air1 = 0; this.air2 = 0;
    this.sb1 = 0; this.sb2 = 0; this.kb1 = 0; this.kb2 = 0; this.tick = 0; this.tickEnv = 0; this.tb1 = 0; this.tb2 = 0;
  }
  rnd() { this.seed = (this.seed * 1664525 + 1013904223) >>> 0; return this.seed / 4294967296; }
  setConfig(c) {
    this.cfg = c;
    const N = c.phases.length;
    this.hdrF = []; this.hdrB = []; this.hdrLp = new Float32Array(N); this.exc = new Float32Array(N);
    for (let i = 0; i < N; i++) { this.hdrF.push(new Float32Array(1024)); this.hdrB.push(new Float32Array(1024)); }
  }
  setParams(p) {
    this.rpmT = p.rpm; this.thr = p.throttle; this.load = p.load || 0; this.on = !!p.on; this.limiter = !!p.limiter; this.vol = p.vol;
    if (p.crank) { this.crank = p.crank; this.stT = 0; this.relT = 0; this.catchAge = 9; }
  }
  trigger(i, combust) {
    const c = this.cfg; if (!c) return;
    let amp;
    const thr = this.thrS, idleish = this.rpm < c.idle * 1.4;
    if (!combust) amp = this.crank > 0 ? 0.16 * c.compDip / 0.3 : 0.08;     // только сжатие: «пых» воздуха (прокрутка, глушение)
    else {
      amp = (0.5 + 0.5 * Math.max(thr, this.load * 0.8)) * c.amp[i];
      amp *= 1 + (this.rnd() - 0.5) * (idleish ? c.jitterIdle : c.jitter);   // неровность сгорания
      if (this.limiter && this.rnd() < 0.55) amp = 0.04;                  // отсечка топлива
      if (thr < 0.06 && this.rpm > c.idle * 1.7 && this.catchAge > 2.5) {   // торможение двигателем
        amp = 0.28 + 0.12 * this.rnd();
        if (c.crackle > 0 && this.rnd() < c.crackle) amp = 1.3 + this.rnd();
      }
      if (this.catchAge < 2.5) amp = Math.max(amp, 0.75 * c.amp[i]);
      if (this.catchAge < 0.35) { if (this.rnd() < 0.35) amp *= 0.1; else amp *= 1.3; }   // первые вспышки — рвано и громко
    }
    if (this.voices.length > 48) this.voices.shift();
    const gapS = (c.cycle / c.phases.length) / Math.max(60, this.rpm * 6);
    const tau = Math.min(c.tau, gapS * 0.6);
    this.voices.push({ t: -c.delay[i], a: amp * (0.65 + 0.35 * Math.sqrt(c.tau / tau)), i, comb: combust, tau });
    if (combust && c.diesel) this.clEnv = Math.min(1.5, this.clEnv + 0.6 + 0.6 * (1 - this.thrS));
    this.inEnv = Math.min(1.5, this.inEnv + 0.5);
  }
  process(out, n) {
    const c = this.cfg, sr = this.sr, dt = 1 / sr;
    if (!c) { for (let k = 0; k < n; k++) out[k] = 0; return; }
    const cyc = c.cycle, ph = c.phases, N = ph.length;
    const two = 2 * Math.PI;
    const a1 = 1 - Math.exp(-two * c.mufFc / sr), a2 = 1 - Math.exp(-two * c.mufFc * 1.6 / sr);
    const hLp = 1 - Math.exp(-two * 2600 / sr), pLpK = 1 - Math.exp(-two * c.tailFc / sr);
    const bodyK = 1 - Math.exp(-two * 140 / sr), gnK = 1 - Math.exp(-two * 1800 / sr);
    const airK = 1 - Math.exp(-two * c.airFc / sr), leakK = 1 - Math.exp(-two * c.mufFc * 3.5 / sr);
    const exc = this.exc;
    for (let k = 0; k < n; k++) {
      // ---------- обороты ----------
      let target = this.rpmT, dip = 0;
      if (this.crank > 0) {
        if (this.stT === 0) { this.clickEnv = 1; this.clickT = 0; }
        this.stT += dt; this.crank -= dt;
        const spin = Math.min(1, this.stT / 0.18);
        // на такте сжатия вал «упирается»: обороты проседают, после ВМТ воздух толкает поршень обратно
        const p1 = (this.theta / cyc * N) % 1;
        const d = Math.cos(two * (p1 - 0.06));
        dip = d > 0 ? d * d * d : -0.25 * d * d;
        target = c.crankRpm * (0.2 + 0.8 * spin) * (1 - c.compDip * dip);
        this.rpm = target;
        if (this.crank <= 0) { this.catchAge = 0; this.relT = 0.25; this.armRel = target * 13 / 60; this.stT = 0; this.clickEnv = 0.5; this.clickT = 0; }
      }
      this.strain += (Math.max(0, dip) - this.strain) * 0.01;
      // «схватил»: обороты взлетают выше холостых и за 1–2 с опускаются
      if (this.catchAge < 2.5) {
        this.catchAge += dt;
        const a = this.catchAge, sh = a < 0.28 ? a / 0.28 : Math.exp(-(a - 0.28) / 0.6);
        target = this.rpmT + c.idle * c.flare * sh;
      }
      if (this.crank <= 0) {
        const kR = this.catchAge < 0.6 ? 0.0005 : (target > this.rpm ? 0.0016 : 0.0009);
        this.rpm += (target - this.rpm) * kR;
      }
      if (!this.on && this.crank <= 0) this.rpm += (0 - this.rpm) * 0.00025;
      this.thrS += (this.thr - this.thrS) * 0.003;
      this.volS += (this.vol - this.volS) * 0.002;
      // ---------- угол коленвала и вспышки ----------
      const prev = this.theta;
      this.theta += this.rpm / 60 * 360 * dt;
      const wrap = this.theta >= cyc;
      if (wrap) this.theta -= cyc;
      const combust = this.on && this.crank <= 0 && this.rpm > 120;
      if (this.rpm > 30) for (let i = 0; i < N; i++) {
        const p = ph[i];
        if (wrap ? (p >= prev || p < this.theta) : (p >= prev && p < this.theta)) this.trigger(i, combust);
      }
      // ---------- импульсы давления у выпускных клапанов ----------
      for (let i = 0; i < N; i++) exc[i] = 0;
      this.gn += ((this.rnd() * 2 - 1) - this.gn) * gnK;   // шум газа — только до ~1.8 кГц
      let blowSum = 0;
      for (let v = this.voices.length - 1; v >= 0; v--) {
        const o = this.voices[v];
        o.t += dt;
        if (o.t < 0) continue;
        const env = Math.exp(-o.t / o.tau);
        if (env < 0.002) { this.voices.splice(v, 1); continue; }
        const att = 1 - Math.exp(-o.t / 0.00025);
        const blow = o.a * env * att;
        exc[o.i] += blow * (1 + c.rasp * this.gn * 2.2 * (o.comb ? 1 : 0.35));
        blowSum += blow;
      }
      // ---------- выпускной коллектор: у каждого цилиндра свой патрубок (волновод) ----------
      const w = this.w;
      let col0 = 0, col1 = 0;
      for (let i = 0; i < N; i++) {
        const d = c.hdr[i], F = this.hdrF[i], B = this.hdrB[i];
        const fo = F[(w - d) & 1023], bo = B[(w - d) & 1023];
        F[w & 1023] = exc[i] + c.rValve * bo;                     // у клапана волна отражается обратно
        this.hdrLp[i] += (fo - this.hdrLp[i]) * hLp;
        B[w & 1023] = c.rCol * this.hdrLp[i];                     // у коллектора — с переворотом (расширение)
        const tr = fo * (1 + c.rCol);
        if (c.bank[i]) col1 += tr; else col0 += tr;
      }
      // ---------- труба: волна туда и обратно, отражение у открытого среза ----------
      let tail = 0;
      const banks = c.dual ? 2 : 1;
      if (!c.dual) { col0 += col1; col1 = 0; }
      for (let b = 0; b < banks; b++) {
        const d = c.pipe[b], F = this.pF[b], B = this.pB[b];
        const fo = F[(w - d) & 8191], bo = B[(w - d) & 8191];
        F[w & 8191] = (b ? col1 : col0) + c.rJoin * bo;
        this.pLp[b] += (fo - this.pLp[b]) * pLpK;
        B[w & 8191] = -c.rTail * this.pLp[b];
        // излучение из среза трубы ~ производная потока
        const rad = fo - 0.82 * this.prevTail[b]; this.prevTail[b] = fo;
        tail += rad * 0.4 + fo * 0.6;
      }
      this.w = (w + 1) & 8191;
      // ---------- катализатор и глушитель ----------
      this.m1 += (tail - this.m1) * a1; this.m2 += (this.m1 - this.m2) * a2;
      this.lk += ((tail - this.m2) - this.lk) * leakK;
      const ex = this.m2 * c.mufGain + this.lk * c.leak * 2;
      // ---------- «тело» мотора: низкий гул блока и кузова ----------
      this.body += (blowSum - this.body) * bodyK; this.body2 += (this.body - this.body2) * bodyK;
      const body = this.body2 * c.bodyGain;
      // ---------- впуск ----------
      this.inEnv *= 0.9985;
      const nz = this.rnd() * 2 - 1;
      const fi = Math.min(0.45, two * (c.inF + this.rpm * c.inK) / sr);
      this.ib1 += fi * (nz - this.ib1 - 0.7 * this.ib2); this.ib2 += fi * this.ib1;
      const intake = this.ib1 * this.inEnv * (0.12 + this.thrS) * c.inGain * (this.on ? 1 : 0.3);
      // ---------- дизельный стук ----------
      let clat = 0;
      if (c.diesel) {
        this.clEnv *= 0.9955;
        const fc2 = Math.min(0.45, two * 2600 / sr);
        this.cb1 += fc2 * (nz - this.cb1 - 0.35 * this.cb2); this.cb2 += fc2 * this.cb1;
        clat = this.cb1 * this.clEnv * 0.22;
      }
      // ---------- механика: стук клапанов на каждом обороте распредвала ----------
      this.tick += this.rpm / 60 / 2 * dt * N;
      if (this.tick >= 1) { this.tick -= 1; this.tickEnv = 0.5 + 0.5 * this.rnd(); }
      this.tickEnv *= 0.993;
      const ft = Math.min(0.45, two * 3800 / sr);
      this.tb1 += ft * (nz - this.tb1 - 0.3 * this.tb2); this.tb2 += ft * this.tb1;
      const mech = (this.tb1 * this.tickEnv * (0.03 + 0.05 * (1 - this.thrS))) * Math.min(1.4, 0.35 + this.rpm / 6000) * c.mechGain * (this.rpm > 100 ? 1 : 0);
      // ---------- стартер ----------
      let st = 0;
      if (this.crank > 0 || this.relT > 0) {
        let arm, fade = 1;
        if (this.crank > 0) arm = Math.max(4, this.rpm * 13 / 60);
        else { this.relT -= dt; this.armRel += (110 - this.armRel) * 0.0015; arm = this.armRel; fade = Math.max(0, this.relT / 0.25); fade *= fade; }
        this.stPh = (this.stPh + arm / sr) % 1000;
        // шестерня бендикса (10 зубьев) и коллектор якоря: «пилообразный» вой с гармониками
        const meshP = this.stPh * 10, commP = this.stPh * 12;
        let gear = 0; for (let h = 1; h <= 6; h++) gear += Math.sin(two * meshP * h) / h;
        const hum = Math.sin(two * this.stPh) * 0.5 + Math.sin(two * this.stPh * 2) * 0.35 + Math.sin(two * this.stPh * 3) * 0.15;
        const fb2 = Math.min(0.45, two * 2100 / sr);
        this.sb1 += fb2 * (nz - this.sb1 - 0.45 * this.sb2); this.sb2 += fb2 * this.sb1;
        const brush = this.sb1 * (0.5 + 0.5 * Math.sin(two * commP));
        const strain = 0.65 + 0.7 * this.strain, spin = Math.min(1, (this.stT || 1) / 0.08);
        st = (gear * 0.05 * strain + hum * 0.07 * (0.8 + this.strain) + brush * 0.06 * strain) * fade * spin * c.starterGain;
      }
      if (this.clickEnv > 0.001) {
        this.clickT += dt; this.clickEnv *= 0.9965;
        const fc3 = Math.min(0.45, two * 1700 / sr);
        this.kb1 += fc3 * (nz - this.kb1 - 0.25 * this.kb2); this.kb2 += fc3 * this.kb1;
        st += (this.kb1 * 0.6 + Math.sin(two * 190 * this.clickT) * 0.35 * Math.exp(-this.clickT / 0.02)) * this.clickEnv * c.starterGain;
      }
      const s = ex * c.loud + body + intake + clat + mech;
      const hpOut = s - this.hpx + 0.995 * this.hp; this.hpx = s; this.hp = hpOut;
      // поглощение в воздухе: срезаем «шипящий» верх
      const y = hpOut + st; this.air1 += (y - this.air1) * airK; this.air2 += (this.air1 - this.air2) * airK;
      out[k] = this.air2 * this.volS;
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
    // мягкое насыщение даёт гармоники низких частот — их слышно даже на маленьком динамике телефона;
    // подъём середины — «присутствие» мотора
    this.engIn = c.createGain(); this.engIn.gain.value = 1.6;
    const sat = c.createWaveShaper(); const cv = new Float32Array(1024); for (let i = 0; i < 1024; i++) { const x = i / 512 - 1; cv[i] = Math.tanh(x * 2.2) / Math.tanh(2.2); } sat.curve = cv; sat.oversample = '2x';
    const pres = c.createBiquadFilter(); pres.type = 'peaking'; pres.frequency.value = 1000; pres.Q.value = 0.7; pres.gain.value = 5;
    const outG = c.createGain(); outG.gain.value = 0.62;
    this.engIn.connect(sat); sat.connect(pres); pres.connect(outG); outG.connect(this.master);
    // реверберация: гараж со стендом или открытая трасса
    this.verb = c.createConvolver(); this.verbG = c.createGain(); this.verbG.gain.value = 0.22;
    outG.connect(this.verb); this.verb.connect(this.verbG); this.verbG.connect(this.master);
    this.setRoom('garage');
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
  setRoom(kind) {
    if (!this.ctx || !this.verb || this.room === kind) return;
    this.room = kind;
    const c = this.ctx, sr = c.sampleRate, len = Math.round(sr * (kind === 'garage' ? 0.9 : 0.45));
    const ir = c.createBuffer(2, len, sr);
    for (let ch = 0; ch < 2; ch++) {
      const d = ir.getChannelData(ch); let lp = 0;
      // ранние отражения (стены гаража / отбойник и трибуны) + затухающий «хвост»
      const early = kind === 'garage' ? [0.007, 0.013, 0.019, 0.027, 0.041] : [0.018, 0.045, 0.09];
      early.forEach((t, i) => { const k = Math.round(t * sr * (1 + ch * 0.07)); if (k < len) d[k] += (ch ? -1 : 1) * 0.55 / (i + 1); });
      const decay = kind === 'garage' ? 0.28 : 0.12, damp = kind === 'garage' ? 0.35 : 0.2;
      for (let i = 0; i < len; i++) { const n = Math.random() * 2 - 1; lp += (n - lp) * damp; d[i] += lp * Math.exp(-i / sr / decay) * 0.35; }
    }
    this.verb.buffer = ir;
    if (this.verbG) this.verbG.gain.value = kind === 'garage' ? 0.24 : 0.12;
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
    const pipe0 = Math.round(sr * L / cGas), pipe1 = Math.round(sr * (L * (e.exhManifold === 'equal' ? 1.0 : 1.07)) / cGas);
    let mufFc = ({ stock: 800, sport: 1150, straight: 2300, none: 5200 })[muf];
    let mufGain = ({ stock: 0.42, sport: 0.75, straight: 1.0, none: 1.2 })[muf];
    let leak = ({ stock: 0.03, sport: 0.08, straight: 0.2, none: 0.45 })[muf];
    if (e.cat) { mufFc *= 0.82; mufGain *= 0.9; leak *= 0.7; }
    if (e.exhWrap) leak *= 0.85;
    const fb = ({ stock: 0.3, sport: 0.42, straight: 0.52, none: 0.18 })[muf] + (two && e.tunedPipe ? 0.25 : 0);
    const loud = 2.3 * (0.55 + Math.min(0.35, (dia - 40) / 200)) * (e.exhLayout === 'dual' ? 1.1 : 1) * (two ? 0.7 : 1);
    const crackle = muf === 'stock' ? 0.004 : muf === 'sport' ? 0.02 : 0.05;
    const itb = e.manifold === 'itb';
    // прокрутка: большой объём и дизель крутятся медленнее и тяжелее, ротор — быстро и ровно
    const disp = displacement(e);
    let crankRpm = clamp(290 - disp * 22, 150, 280), compDip = clamp(0.18 + e.cr / 60, 0.25, 0.55);
    if (diesel) { crankRpm *= 0.85; compDip = 0.55; }
    if (rot) { crankRpm = 260; compDip = 0.15; }
    if (n >= 10) compDip *= 0.6;
    const starterGain = (diesel || disp > 4.5 ? 1.25 : 1) * 0.42;
    // длины патрубков коллектора (волноводы): литой — короткие и разные, «паук» — длиннее, равнодлинный — одинаковые
    const hdrL = ({ cast: 0.38, tubular: 0.75, equal: 0.85 })[e.exhManifold] || 0.5;
    const hdrVar = ({ cast: 0.45, tubular: 0.25, equal: 0.02 })[e.exhManifold] || 0.3;
    const hdr = phases.map(() => Math.max(8, Math.min(1000, Math.round(sr * hdrL * (1 + (r() - 0.5) * hdrVar) / cGas))));
    const rTail = ({ stock: 0.25, sport: 0.45, straight: 0.6, none: 0.72 })[muf];
    const flare = diesel ? 0.35 : rot ? 0.9 : 0.75;
    const cfg = {
      hdr, airFc: ({ stock: 4200, sport: 5500, straight: 7000, none: 8500 })[muf], rValve: 0.72, rCol: -0.45, rJoin: 0.3 + (two && e.tunedPipe ? 0.35 : 0), rTail, tailFc: 1400, bodyGain: 0.2 * (diesel ? 1.4 : 1), flare,
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
  fadeOut(sec) { if (this.ctx) { this.master.gain.setTargetAtTime(0, this.ctx.currentTime, sec / 3); setTimeout(() => this.send({ p: { rpm: 0, throttle: 0, on: false, vol: 0 } }), sec * 1000); } }
  silence() { if (this.ctx) { this.master.gain.setTargetAtTime(0, this.ctx.currentTime, 0.08); this.send({ p: { rpm: 0, throttle: 0, on: false, vol: 0 } }); } }
}
