'use strict';
// ================= ЗВУК ДВИГАТЕЛЯ (синтез Web Audio) =================
// Частота вспышек = обороты/60 × цилиндры/2 (4-такт). Тембр зависит от числа цилиндров,
// типа выпуска, неравномерности работы, наддува.
class EngineSound {
  constructor() { this.ctx = null; this.on = false; this.volume = 0.8; }
  init() {
    if (this.ctx) return true;
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return false;
    const c = this.ctx = new AC();
    this.master = c.createGain(); this.master.gain.value = 0;
    const comp = c.createDynamicsCompressor(); comp.threshold.value = -14; comp.ratio.value = 4;
    this.master.connect(comp); comp.connect(c.destination);
    // основной тон: импульсы выхлопа
    this.osc = c.createOscillator(); this.osc.setPeriodicWave(this.pulseWave(0.55));
    this.sub = c.createOscillator(); this.sub.type = 'triangle';
    this.oscG = c.createGain(); this.subG = c.createGain();
    this.shaper = c.createWaveShaper(); this.shaper.curve = this.distCurve(18); this.shaper.oversample = '2x';
    this.lp = c.createBiquadFilter(); this.lp.type = 'lowpass'; this.lp.Q.value = 2.5;
    this.peak = c.createBiquadFilter(); this.peak.type = 'peaking'; this.peak.gain.value = 6; this.peak.Q.value = 1.2;
    this.osc.connect(this.oscG); this.sub.connect(this.subG);
    this.oscG.connect(this.shaper); this.subG.connect(this.shaper);
    this.shaper.connect(this.peak); this.peak.connect(this.lp);
    this.engG = c.createGain(); this.lp.connect(this.engG); this.engG.connect(this.master);
    // шум: впуск/механика
    const nb = c.createBuffer(1, c.sampleRate * 2, c.sampleRate), d = nb.getChannelData(0);
    for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    this.noiseBuf = nb;
    this.noise = c.createBufferSource(); this.noise.buffer = nb; this.noise.loop = true;
    this.nbp = c.createBiquadFilter(); this.nbp.type = 'bandpass'; this.nbp.Q.value = 0.9;
    this.noiseG = c.createGain(); this.noiseG.gain.value = 0;
    this.noise.connect(this.nbp); this.nbp.connect(this.noiseG); this.noiseG.connect(this.master);
    // свист турбины и вой компрессора
    this.whistle = c.createOscillator(); this.whistle.type = 'sine';
    this.whG = c.createGain(); this.whG.gain.value = 0; this.whistle.connect(this.whG); this.whG.connect(this.master);
    this.whine = c.createOscillator(); this.whine.type = 'sawtooth';
    this.wnF = c.createBiquadFilter(); this.wnF.type = 'bandpass'; this.wnF.Q.value = 6;
    this.wnG = c.createGain(); this.wnG.gain.value = 0; this.whine.connect(this.wnF); this.wnF.connect(this.wnG); this.wnG.connect(this.master);
    [this.osc, this.sub, this.noise, this.whistle, this.whine].forEach(n => n.start());
    return true;
  }
  pulseWave(duty) {
    const n = 48, re = new Float32Array(n), im = new Float32Array(n);
    for (let k = 1; k < n; k++) { im[k] = Math.sin(Math.PI * k * duty) / (k * 0.9) * (k % 2 ? 1 : 0.8); re[k] = 0; }
    return this.ctx.createPeriodicWave(re, im);
  }
  distCurve(k) {
    const n = 1024, c = new Float32Array(n);
    for (let i = 0; i < n; i++) { const x = i / n * 2 - 1; c[i] = (1 + k) * x / (1 + k * Math.abs(x)); }
    return c;
  }
  resume() { if (this.ctx && this.ctx.state === 'suspended') this.ctx.resume(); }
  configure(e) {
    if (!this.ctx) return;
    this.e = e;
    // тембр по числу цилиндров: мало цилиндров — «тарахтит», много — «поёт»
    const n = e.cyl, duty = n <= 2 ? 0.18 : n <= 4 ? 0.3 : n <= 6 ? 0.42 : 0.5;
    this.osc.setPeriodicWave(this.pulseWave(isRotary(e) ? 0.35 : duty));
    this.loud = ({ stock: 0.55, sport: 0.75, straight: 0.95, none: 1.1 })[e.muffler] * (e.cat ? 0.9 : 1);
    this.cut = ({ stock: 900, sport: 1600, straight: 2800, none: 4200 })[e.muffler];
    // неравномерность: крестообразный V8, оппозитник с неравнодлинным выпуском, V2 и т.п.
    this.burble = (e.type === 'v' && e.cyl === 8 && e.crankPlane === 'cross') ? 0.55 : (e.type === 'boxer' && e.exhManifold !== 'equal') ? 0.45 : (e.cyl <= 2 ? 0.5 : e.cyl === 5 ? 0.3 : 0.12);
    if (e.exhManifold === 'equal') this.burble *= 0.6;
  }
  // p: { rpm, throttle, load, spool, boost, on, limiter }
  update(p) {
    if (!this.ctx || !this.e) return;
    const e = this.e, t = this.ctx.currentTime;
    const strokes = (e.cycle === 2 || isRotary(e)) ? 1 : 0.5;
    const fire = Math.max(5, p.rpm / 60 * e.cyl * strokes);
    const on = p.on ? 1 : 0;
    const vol = this.volume * on;
    const set = (param, v, tc) => param.setTargetAtTime(v, t, tc || 0.03);
    set(this.osc.frequency, fire, 0.015);
    set(this.sub.frequency, fire / (e.cyl >= 4 ? e.cyl / 2 : 1) / (isRotary(e) ? 1 : 1), 0.015);
    const thr = p.throttle;
    set(this.oscG.gain, 0.35 + thr * 0.5, 0.03);
    set(this.subG.gain, this.burble * (0.4 + thr * 0.6), 0.03);
    set(this.lp.frequency, Math.min(12000, this.cut + fire * 2.2 + thr * 1500), 0.03);
    set(this.peak.frequency, fire * 2, 0.03);
    set(this.engG.gain, (0.18 + thr * 0.22 + (p.load || 0) * 0.1) * this.loud * (p.limiter ? 0.55 : 1), 0.02);
    set(this.nbp.frequency, 300 + p.rpm * 0.35, 0.05);
    set(this.noiseG.gain, (0.012 + thr * 0.05 * (e.manifold === 'itb' ? 2.2 : 1)) * on, 0.05);
    // турбина
    if (hasTurbo(e)) {
      const s = p.spool || 0;
      set(this.whistle.frequency, 1800 + s * 6500 * (50 / e.turboSize), 0.08);
      set(this.whG.gain, s * s * 0.03 * on * (e.muffler === 'stock' ? 0.6 : 1), 0.08);
    } else set(this.whG.gain, 0, 0.05);
    if (hasSC(e)) {
      const f = p.rpm * (e.induction === 'sc_cent' ? 2.2 : 0.55);
      set(this.whine.frequency, Math.max(30, f), 0.03); set(this.wnF.frequency, Math.max(60, f), 0.03);
      set(this.wnG.gain, (0.01 + thr * 0.05) * clamp(p.rpm / 6000, 0, 1) * on, 0.05);
    } else set(this.wnG.gain, 0, 0.05);
    set(this.master.gain, vol, 0.05);
  }
  burst(freq, q, dur, gain) {
    if (!this.ctx || !this.on) return;
    const c = this.ctx, t = c.currentTime;
    const src = c.createBufferSource(); src.buffer = this.noiseBuf;
    const f = c.createBiquadFilter(); f.type = 'bandpass'; f.frequency.value = freq; f.Q.value = q;
    const g = c.createGain(); g.gain.setValueAtTime(gain * this.volume, t); g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    src.connect(f); f.connect(g); g.connect(this.master);
    src.start(t, Math.random()); src.stop(t + dur + 0.05);
  }
  pop() { this.burst(180 + Math.random() * 200, 0.8, 0.12, 1.4); }
  bov() { this.burst(2600, 0.6, 0.45, 0.7); }
  bang() { this.burst(90, 0.5, 1.5, 3.5); this.burst(900, 0.4, 0.8, 1.5); }
  starter(dur) {
    if (!this.ctx) return;
    const c = this.ctx, t = c.currentTime;
    const o = c.createOscillator(); o.type = 'sawtooth'; o.frequency.setValueAtTime(14, t); o.frequency.linearRampToValueAtTime(22, t + dur);
    const f = c.createBiquadFilter(); f.type = 'lowpass'; f.frequency.value = 600;
    const g = c.createGain(); g.gain.setValueAtTime(0.25 * this.volume, t); g.gain.setTargetAtTime(0, t + dur, 0.05);
    const lfo = c.createOscillator(); lfo.frequency.value = 9; const lg = c.createGain(); lg.gain.value = 0.15 * this.volume; lfo.connect(lg); lg.connect(g.gain);
    o.connect(f); f.connect(g); g.connect(c.destination); o.start(t); lfo.start(t); o.stop(t + dur + 0.3); lfo.stop(t + dur + 0.3);
  }
  silence() { if (this.ctx) this.master.gain.setTargetAtTime(0, this.ctx.currentTime, 0.05); }
}
