// Простые синтезированные звуки (без файлов).
let ctx = null;
let noiseBuf = null;
export const audio = { enabled: true };

export function unlockAudio() {
  if (ctx) {
    if (ctx.state === 'suspended') ctx.resume();
    return;
  }
  try {
    ctx = new (window.AudioContext || window.webkitAudioContext)();
    noiseBuf = ctx.createBuffer(1, ctx.sampleRate, ctx.sampleRate);
    const d = noiseBuf.getChannelData(0);
    for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
  } catch (e) {
    ctx = null;
  }
}

function noise(dur, freq, type, vol, when = 0) {
  if (!ctx || !audio.enabled) return;
  const t = ctx.currentTime + when;
  const src = ctx.createBufferSource();
  src.buffer = noiseBuf;
  const f = ctx.createBiquadFilter();
  f.type = type;
  f.frequency.value = freq;
  const g = ctx.createGain();
  g.gain.setValueAtTime(vol, t);
  g.gain.exponentialRampToValueAtTime(0.001, t + dur);
  src.connect(f).connect(g).connect(ctx.destination);
  src.start(t, Math.random() * 0.5);
  src.stop(t + dur);
}

function tone(freq, dur, vol, type = 'sine', when = 0, slide = 0) {
  if (!ctx || !audio.enabled) return;
  const t = ctx.currentTime + when;
  const o = ctx.createOscillator();
  o.type = type;
  o.frequency.setValueAtTime(freq, t);
  if (slide) o.frequency.exponentialRampToValueAtTime(freq * slide, t + dur);
  const g = ctx.createGain();
  g.gain.setValueAtTime(vol, t);
  g.gain.exponentialRampToValueAtTime(0.001, t + dur);
  o.connect(g).connect(ctx.destination);
  o.start(t);
  o.stop(t + dur);
}

export const sfx = {
  cannon(vol = 1) {
    noise(0.5, 380, 'lowpass', 0.5 * vol);
    tone(90, 0.35, 0.35 * vol, 'sine', 0, 0.4);
  },
  splash(vol = 1) { noise(0.35, 1400, 'highpass', 0.15 * vol); },
  hit(vol = 1) {
    noise(0.25, 900, 'bandpass', 0.5 * vol);
    tone(140, 0.2, 0.25 * vol, 'square', 0, 0.5);
  },
  coin() {
    tone(988, 0.08, 0.12, 'square');
    tone(1319, 0.18, 0.12, 'square', 0.07);
  },
  gem() {
    tone(1047, 0.1, 0.12, 'triangle');
    tone(1568, 0.1, 0.12, 'triangle', 0.08);
    tone(2093, 0.2, 0.12, 'triangle', 0.16);
  },
  sink() { noise(1.2, 300, 'lowpass', 0.4); tone(70, 1.0, 0.3, 'sine', 0, 0.5); },
  boost() { noise(0.6, 600, 'bandpass', 0.25); },
  quest() {
    [523, 659, 784, 1047].forEach((f, i) => tone(f, 0.18, 0.13, 'triangle', i * 0.1));
  },
  click() { tone(660, 0.05, 0.08, 'square'); },
};
