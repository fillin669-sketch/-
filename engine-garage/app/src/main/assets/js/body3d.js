'use strict';
// ================= РЕАЛИСТИЧНЫЕ КУЗОВА =================
// Каждый класс машины — своя оригинальная модель: силуэт сбоку, сечение (плечевая линия, завал стёкол,
// расширения над колёсами), форма в плане, оптика, решётка, двери, стойки и отделка.
// Кузов строится «лофтом» из сечений. Фары, фонари, решётки, зазоры панелей, молдинги и накладки
// проецируются на поверхность кузова, поэтому повторяют её изгибы.

// u — доля половины ширины (|z| / (W/2)), v — высота над землёй, м (на штатной подвеске)
const DESIGNS = {
  hatch: {
    L: 4.02, W: 1.78, wb: [0.83, 3.39], sill: 0.18, doors: 4, rear: 'hatch',
    top: [[0, 0.34], [0.012, 0.49], [0.05, 0.635], [0.14, 0.725], [0.38, 0.785], [0.9, 0.85], [1.42, 0.92], [2.4, 0.97], [3.3, 0.99], [3.84, 1.0], [3.94, 0.96], [3.99, 0.78], [4.02, 0.45]],
    cab: [[1.42, 0.92], [1.75, 1.15], [2.05, 1.38], [2.32, 1.46], [3.1, 1.47], [3.55, 1.43], [3.82, 1.3], [3.9, 1.0]],
    ws: 2.3, bl: 3.55, gh: 0.74, gb: 0.9, sh: 0.3, crown: 0.025, noseR: 0.5, noseK: 0.18, tailR: 0.32, tailK: 0.12, taper: 0.04, flare: 0.022,
    pillars: [{ x: 2.92, w: 0.045, k: 2 }], dlo: [1.98, 3.6], blW: 0.86, hoodX: 0.24, bumpY: 0.52, plateF: 0.44, plateR: 0.62,
    head: { kind: 'swept', poly: [[0.4, 0.62], [0.86, 0.625], [0.955, 0.665], [0.93, 0.725], [0.66, 0.715], [0.41, 0.68]], eyes: [[0.62, 0.675], [0.8, 0.68]] },
    grille: { kind: 'slot', poly: [[0, 0.62], [0.38, 0.63], [0.37, 0.675], [0, 0.68]] },
    intake: { poly: [[0, 0.33], [0.56, 0.34], [0.63, 0.47], [0, 0.48]] }, fog: [0.64, 0.42, 0.032],
    tail: { kind: 'corner', poly: [[0.6, 0.79], [0.95, 0.79], [0.94, 0.95], [0.84, 0.96], [0.62, 0.86]] },
  },
  sedan: {
    L: 4.62, W: 1.79, wb: [0.9, 3.56], sill: 0.19, doors: 4, rear: 'trunk',
    top: [[0, 0.35], [0.012, 0.5], [0.05, 0.645], [0.15, 0.745], [0.45, 0.8], [1.1, 0.875], [1.66, 0.93], [2.8, 0.97], [3.8, 1.0], [4.42, 1.02], [4.54, 0.99], [4.6, 0.82], [4.62, 0.45]],
    cab: [[1.66, 0.93], [2.0, 1.15], [2.3, 1.37], [2.56, 1.44], [3.25, 1.45], [3.6, 1.38], [3.95, 1.17], [4.18, 1.02]],
    ws: 2.52, bl: 3.4, gh: 0.72, gb: 0.9, sh: 0.26, crown: 0.025, noseR: 0.55, noseK: 0.2, tailR: 0.4, tailK: 0.14, taper: 0.04, flare: 0.022,
    pillars: [{ x: 3.08, w: 0.045, k: 2 }], dlo: [2.12, 3.82], blW: 0.8, hoodX: 0.26, bumpY: 0.55, plateF: 0.44, plateR: 0.78,
    head: { kind: 'swept', poly: [[0.36, 0.625], [0.86, 0.625], [0.955, 0.67], [0.93, 0.737], [0.6, 0.723], [0.37, 0.68]], eyes: [[0.58, 0.68], [0.77, 0.685]] },
    grille: { kind: 'wide', poly: [[0, 0.52], [0.34, 0.52], [0.4, 0.62], [0.36, 0.675], [0, 0.68]] },
    intake: { poly: [[0, 0.33], [0.42, 0.34], [0.46, 0.43], [0, 0.44]] }, fog: [0.66, 0.44, 0.03],
    tail: { kind: 'wrap', poly: [[0.52, 0.86], [0.96, 0.86], [0.97, 0.95], [0.86, 0.985], [0.55, 0.96]] },
  },
  bizsedan: {
    L: 5.02, W: 1.9, wb: [0.96, 3.98], sill: 0.19, doors: 4, rear: 'trunk', chrome: true, sunroof: true,
    top: [[0, 0.37], [0.01, 0.55], [0.04, 0.69], [0.12, 0.78], [0.45, 0.84], [1.25, 0.9], [2.0, 0.97], [3.0, 1.0], [4.2, 1.02], [4.84, 1.04], [4.96, 1.0], [5.01, 0.84], [5.02, 0.46]],
    cab: [[2.0, 0.97], [2.38, 1.2], [2.68, 1.4], [2.98, 1.47], [3.7, 1.475], [4.02, 1.41], [4.32, 1.21], [4.56, 1.05]],
    ws: 2.94, bl: 3.86, gh: 0.73, gb: 0.9, sh: 0.22, crown: 0.022, noseR: 0.5, noseK: 0.16, tailR: 0.42, tailK: 0.12, taper: 0.03, flare: 0.018,
    pillars: [{ x: 3.48, w: 0.05, k: 2 }], dlo: [2.5, 4.26], blW: 0.8, hoodX: 0.2, bumpY: 0.57, plateF: 0.46, plateR: 0.8,
    head: { kind: 'block', poly: [[0.5, 0.64], [0.92, 0.64], [0.96, 0.67], [0.95, 0.735], [0.52, 0.73]], eyes: [[0.62, 0.685], [0.74, 0.685], [0.86, 0.685]] },
    grille: { kind: 'tall', poly: [[0, 0.47], [0.4, 0.47], [0.43, 0.6], [0.42, 0.73], [0, 0.745]] },
    intake: { poly: [[0, 0.32], [0.36, 0.33], [0.38, 0.4], [0, 0.41]] }, fog: null,
    tail: { kind: 'bar', poly: [[0.5, 0.88], [0.95, 0.88], [0.96, 0.96], [0.9, 0.99], [0.52, 0.98]] },
  },
  coupe: {
    L: 4.64, W: 1.86, wb: [0.93, 3.63], sill: 0.16, doors: 2, rear: 'trunk',
    top: [[0, 0.31], [0.012, 0.45], [0.05, 0.57], [0.17, 0.65], [0.6, 0.72], [1.25, 0.8], [1.82, 0.88], [2.7, 0.93], [3.6, 0.97], [4.47, 0.98], [4.57, 0.94], [4.62, 0.76], [4.64, 0.43]],
    cab: [[1.82, 0.88], [2.2, 1.1], [2.52, 1.29], [2.76, 1.35], [3.15, 1.35], [3.56, 1.24], [3.96, 1.06], [4.3, 0.98]],
    ws: 2.72, bl: 3.2, gh: 0.7, gb: 0.88, sh: 0.3, crown: 0.02, noseR: 0.6, noseK: 0.2, tailR: 0.42, tailK: 0.15, taper: 0.05, flare: 0.04,
    pillars: [], dlo: [2.32, 3.86], blW: 0.78, hoodX: 0.22, bumpY: 0.5, plateF: 0.4, plateR: 0.56, doorEnd: 3.24,
    head: { kind: 'blade', poly: [[0.4, 0.58], [0.86, 0.565], [0.96, 0.6], [0.92, 0.648], [0.56, 0.645], [0.41, 0.615]], eyes: [[0.7, 0.618]] },
    grille: { kind: 'mesh', poly: [[0, 0.42], [0.4, 0.42], [0.48, 0.56], [0.36, 0.6], [0, 0.6]] },
    intake: null, fog: null, sideVent: true,
    tail: { kind: 'twin', poly: [[0.56, 0.84], [0.95, 0.84], [0.96, 0.92], [0.6, 0.93]] },
  },
  wagon: {
    L: 4.82, W: 1.82, wb: [0.92, 3.76], sill: 0.19, doors: 4, rear: 'hatch', rails: true,
    top: [[0, 0.35], [0.012, 0.5], [0.05, 0.645], [0.15, 0.745], [0.45, 0.8], [1.1, 0.875], [1.66, 0.93], [2.8, 0.97], [4.0, 1.0], [4.66, 1.01], [4.77, 0.97], [4.81, 0.8], [4.82, 0.45]],
    cab: [[1.66, 0.93], [2.0, 1.15], [2.3, 1.37], [2.56, 1.45], [4.2, 1.45], [4.5, 1.42], [4.68, 1.3], [4.76, 1.02]],
    ws: 2.52, bl: 4.45, gh: 0.73, gb: 0.9, sh: 0.26, crown: 0.025, noseR: 0.55, noseK: 0.2, tailR: 0.34, tailK: 0.12, taper: 0.04, flare: 0.02,
    pillars: [{ x: 3.12, w: 0.045, k: 2 }, { x: 3.98, w: 0.05, k: 2 }], dlo: [2.12, 4.52], blW: 0.86, hoodX: 0.26, bumpY: 0.55, plateF: 0.44, plateR: 0.66,
    head: { kind: 'swept', poly: [[0.38, 0.625], [0.86, 0.625], [0.955, 0.67], [0.93, 0.733], [0.62, 0.722], [0.39, 0.68]], eyes: [[0.6, 0.68], [0.79, 0.683]] },
    grille: { kind: 'wide', poly: [[0, 0.52], [0.36, 0.52], [0.39, 0.62], [0.37, 0.675], [0, 0.68]] },
    intake: { poly: [[0, 0.33], [0.4, 0.34], [0.44, 0.43], [0, 0.44]] }, fog: [0.66, 0.44, 0.03],
    tail: { kind: 'vertical', poly: [[0.66, 0.78], [0.95, 0.78], [0.95, 1.0], [0.88, 1.02], [0.7, 0.9]] },
  },
  suv: {
    L: 4.94, W: 1.97, wb: [0.97, 3.94], sill: 0.32, doors: 4, rear: 'hatch', rails: true, clad: true, sunroof: true,
    top: [[0, 0.46], [0.01, 0.66], [0.04, 0.84], [0.13, 0.96], [0.5, 1.02], [1.2, 1.08], [1.76, 1.13], [3.0, 1.17], [4.5, 1.2], [4.84, 1.19], [4.91, 1.12], [4.94, 0.9], [4.95, 0.56]],
    cab: [[1.76, 1.13], [2.1, 1.38], [2.38, 1.63], [2.62, 1.77], [4.25, 1.78], [4.62, 1.73], [4.82, 1.55], [4.88, 1.2]],
    ws: 2.6, bl: 4.5, gh: 0.78, gb: 0.91, sh: 0.24, crown: 0.03, noseR: 0.45, noseK: 0.14, tailR: 0.3, tailK: 0.1, taper: 0.03, flare: 0.03,
    pillars: [{ x: 3.28, w: 0.05, k: 2 }, { x: 4.1, w: 0.06, k: 2 }], dlo: [2.22, 4.6], blW: 0.88, hoodX: 0.18, bumpY: 0.74, plateF: 0.58, plateR: 0.86,
    head: { kind: 'block', poly: [[0.52, 0.86], [0.9, 0.86], [0.95, 0.9], [0.94, 0.96], [0.54, 0.95]], eyes: [[0.65, 0.905], [0.8, 0.905]] },
    grille: { kind: 'truck', poly: [[0, 0.62], [0.46, 0.62], [0.5, 0.78], [0.5, 0.95], [0, 0.96]] },
    intake: { poly: [[0, 0.44], [0.52, 0.45], [0.56, 0.57], [0, 0.58]] }, fog: [0.8, 0.52, 0.04], skid: true,
    tail: { kind: 'vertical', poly: [[0.7, 0.96], [0.95, 0.96], [0.95, 1.2], [0.86, 1.22], [0.72, 1.1]] },
  },
  crossover: {
    L: 4.5, W: 1.86, wb: [0.92, 3.6], sill: 0.27, doors: 4, rear: 'hatch', rails: true, clad: true, sunroof: true,
    top: [[0, 0.42], [0.01, 0.6], [0.045, 0.76], [0.14, 0.86], [0.5, 0.92], [1.1, 0.99], [1.62, 1.04], [2.8, 1.09], [3.9, 1.13], [4.38, 1.14], [4.46, 1.08], [4.49, 0.86], [4.5, 0.52]],
    cab: [[1.62, 1.04], [1.96, 1.28], [2.22, 1.5], [2.46, 1.6], [3.5, 1.61], [3.95, 1.54], [4.28, 1.36], [4.42, 1.16]],
    ws: 2.42, bl: 3.75, gh: 0.75, gb: 0.9, sh: 0.25, crown: 0.028, noseR: 0.55, noseK: 0.18, tailR: 0.36, tailK: 0.12, taper: 0.035, flare: 0.03,
    pillars: [{ x: 3.08, w: 0.05, k: 2 }], dlo: [2.06, 4.08], blW: 0.84, hoodX: 0.2, bumpY: 0.68, plateF: 0.52, plateR: 0.8,
    head: { kind: 'split', poly: [[0.46, 0.84], [0.9, 0.82], [0.95, 0.845], [0.9, 0.865], [0.48, 0.865]], eyes: [], low: [[0.7, 0.58], [0.88, 0.58], [0.9, 0.68], [0.72, 0.68]] },
    grille: { kind: 'wide', poly: [[0, 0.6], [0.4, 0.6], [0.44, 0.72], [0.42, 0.83], [0, 0.84]] },
    intake: { poly: [[0, 0.42], [0.46, 0.43], [0.5, 0.54], [0, 0.55]] }, fog: null, skid: true,
    tail: { kind: 'bar', poly: [[0.56, 0.98], [0.95, 0.98], [0.95, 1.06], [0.88, 1.09], [0.58, 1.06]] },
  },
  roadster: {
    L: 4.06, W: 1.77, wb: [0.86, 3.22], sill: 0.15, doors: 2, rear: 'trunk', open: true, tub: [2.0, 3.0],
    top: [[0, 0.3], [0.012, 0.43], [0.05, 0.54], [0.18, 0.62], [0.6, 0.69], [1.3, 0.78], [1.72, 0.84], [2.6, 0.87], [3.5, 0.9], [3.93, 0.9], [4.01, 0.85], [4.05, 0.68], [4.06, 0.42]],
    cab: [[1.72, 0.84], [1.9, 1.02], [2.0, 1.12], [2.06, 1.1], [2.08, 0.9]],
    ws: 2.02, bl: 2.06, gh: 0.7, gb: 0.88, sh: 0.32, crown: 0.02, noseR: 0.6, noseK: 0.22, tailR: 0.42, tailK: 0.16, taper: 0.05, flare: 0.04,
    pillars: [], dlo: [9, 9], blW: 0.6, hoodX: 0.22, bumpY: 0.46, plateF: 0.38, plateR: 0.54, doorEnd: 2.9,
    head: { kind: 'oval', poly: [[0.46, 0.57], [0.8, 0.565], [0.92, 0.6], [0.88, 0.635], [0.6, 0.63], [0.47, 0.6]], eyes: [[0.68, 0.6]] },
    grille: { kind: 'mouth', poly: [[0, 0.36], [0.36, 0.37], [0.42, 0.44], [0.36, 0.51], [0, 0.52]] },
    intake: null, fog: null,
    tail: { kind: 'twin', poly: [[0.58, 0.76], [0.94, 0.76], [0.95, 0.83], [0.62, 0.84]] },
  },
  pickup: {
    L: 5.38, W: 1.99, wb: [1.0, 4.27], sill: 0.38, doors: 4, rear: 'bed', bed: [3.68, 5.32], clad: false,
    top: [[0, 0.5], [0.01, 0.72], [0.04, 0.92], [0.13, 1.04], [0.5, 1.09], [1.5, 1.14], [2.02, 1.18], [3.2, 1.21], [5.26, 1.21], [5.33, 1.17], [5.36, 1.0], [5.38, 0.6]],
    cab: [[2.02, 1.18], [2.36, 1.52], [2.6, 1.79], [2.82, 1.87], [3.42, 1.88], [3.54, 1.82], [3.6, 1.6], [3.62, 1.21]],
    ws: 2.8, bl: 3.5, gh: 0.8, gb: 0.92, sh: 0.22, crown: 0.025, noseR: 0.35, noseK: 0.12, tailR: 0.15, tailK: 0.06, taper: 0.02, flare: 0.03,
    pillars: [{ x: 3.02, w: 0.05, k: 2 }], dlo: [2.4, 3.55], blW: 0.62, hoodX: 0.16, bumpY: 0.78, plateF: 0.6, plateR: 0.62,
    head: { kind: 'block', poly: [[0.56, 0.9], [0.92, 0.9], [0.95, 0.94], [0.94, 1.02], [0.58, 1.01]], eyes: [[0.68, 0.955], [0.82, 0.955]] },
    grille: { kind: 'truck', poly: [[0, 0.66], [0.52, 0.66], [0.56, 0.8], [0.56, 1.01], [0, 1.02]] },
    intake: { poly: [[0, 0.46], [0.6, 0.47], [0.64, 0.58], [0, 0.59]] }, fog: [0.82, 0.54, 0.04], skid: true,
    tail: { kind: 'vertical', poly: [[0.82, 0.86], [0.97, 0.86], [0.97, 1.16], [0.84, 1.16]] },
  },
  sport: {
    L: 4.5, W: 1.92, wb: [0.96, 3.56], sill: 0.14, doors: 2, rear: 'trunk',
    top: [[0, 0.29], [0.015, 0.42], [0.06, 0.53], [0.2, 0.6], [0.7, 0.67], [1.4, 0.77], [1.96, 0.86], [2.9, 0.92], [3.6, 0.96], [4.36, 0.97], [4.45, 0.92], [4.49, 0.74], [4.5, 0.42]],
    cab: [[1.96, 0.86], [2.3, 1.06], [2.56, 1.21], [2.8, 1.26], [3.1, 1.255], [3.55, 1.12], [4.0, 0.99], [4.3, 0.97]],
    ws: 2.78, bl: 3.1, gh: 0.66, gb: 0.86, sh: 0.34, crown: -0.012, noseR: 0.7, noseK: 0.24, tailR: 0.45, tailK: 0.16, taper: 0.06, flare: 0.05,
    pillars: [], dlo: [2.42, 3.72], blW: 0.74, hoodX: 0.2, bumpY: 0.48, plateF: 0.36, plateR: 0.52, doorEnd: 3.28,
    head: { kind: 'almond', poly: [[0.44, 0.555], [0.8, 0.55], [0.95, 0.585], [0.9, 0.615], [0.6, 0.615], [0.45, 0.58]], eyes: [[0.62, 0.585], [0.78, 0.585]] },
    grille: { kind: 'mesh', poly: [[0, 0.33], [0.46, 0.34], [0.55, 0.46], [0.42, 0.53], [0, 0.535]] },
    intake: null, fog: null, sideVent: true,
    tail: { kind: 'twin', poly: [[0.58, 0.82], [0.95, 0.82], [0.96, 0.9], [0.62, 0.9]] },
  },
  super: {
    L: 4.62, W: 2.03, wb: [1.06, 3.73], sill: 0.11, doors: 2, rear: 'engine', mid: true,
    top: [[0, 0.25], [0.02, 0.35], [0.1, 0.45], [0.4, 0.53], [1.0, 0.62], [1.55, 0.71], [2.6, 0.86], [3.5, 0.95], [4.46, 0.98], [4.56, 0.92], [4.61, 0.7], [4.62, 0.38]],
    cab: [[1.55, 0.71], [1.95, 0.94], [2.25, 1.09], [2.5, 1.15], [2.9, 1.14], [3.3, 1.06], [3.75, 0.99], [3.95, 0.96]],
    ws: 2.45, bl: 2.92, gh: 0.62, gb: 0.82, sh: 0.38, crown: -0.02, noseR: 0.9, noseK: 0.3, tailR: 0.4, tailK: 0.12, taper: 0.08, flare: 0.065,
    pillars: [], dlo: [2.05, 3.2], blW: 0.7, hoodX: 0.3, bumpY: 0.42, plateF: 0, plateR: 0.48, doorEnd: 3.1,
    head: { kind: 'blade', poly: [[0.5, 0.45], [0.86, 0.47], [0.95, 0.5], [0.88, 0.52], [0.54, 0.485]], eyes: [[0.74, 0.49]] },
    grille: { kind: 'none', poly: [[0, 0.28], [0.5, 0.29], [0.62, 0.38], [0.4, 0.4], [0, 0.4]] },
    intake: null, fog: null, sideIntake: true,
    tail: { kind: 'bar', poly: [[0.42, 0.84], [0.94, 0.84], [0.95, 0.88], [0.44, 0.88]] },
  },
};

// ---------- общая математика ----------
function mirrorPoly(h) { const out = h.slice(); for (let i = h.length - 1; i >= 0; i--) if (h[i][0] > 1e-6) out.push([-h[i][0], h[i][1]]); return out; }
function smoothstep(a, b, x) { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }
function lookupFn(list, n) {
  const pts = profilePts(list, n || 200);
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

// Форма кузова: функции профиля, ширины и сечения + проекции на поверхность
function bodyForm(D, c, rW) {
  const lift = (c.rideHeight || 0) / 1000 + (rW - 0.3) * 0.7;
  const wide = c.kit === 'wide';
  const L = D.L, W = D.W + (wide ? 0.14 : 0);
  const drag = !!D.dragster;
  const topF = lookupFn(D.top);
  const cabF = lookupFn(D.cab);
  const cab0 = D.cab[0][0], cab1 = D.cab[D.cab.length - 1][0];
  const archR = rW + 0.03;
  const top = (x) => topF(x) + lift;
  const roof = (x) => (x > cab0 && x < cab1) ? Math.max(top(x), cabF(x) + lift) : top(x);
  const cabin = (x) => x > cab0 + 0.005 && x < cab1 - 0.005 && roof(x) > top(x) + 0.03;
  const sill = D.sill + lift;
  const bot = (x) => {
    let b = sill;
    const df = x, dr = L - x;
    if (df < 0.5) b += 0.07 * Math.pow(1 - df / 0.5, 2);
    if (dr < 0.45) b += 0.13 * Math.pow(1 - dr / 0.45, 2);
    if (!drag) for (const ax of D.wb) { const dx = x - ax; if (Math.abs(dx) < archR) b = Math.max(b, rW + Math.sqrt(archR * archR - dx * dx)); }
    return Math.min(b, top(x) - 0.035);
  };
  const halfW = (x) => {
    if (drag) { const d = Math.min(x, L - x); return W / 2 * (d < 2.2 ? 0.35 + 0.65 * Math.sqrt(1 - Math.pow(1 - d / 2.2, 2)) : 1); }
    let p = 1;
    if (x < D.noseR) p -= D.noseK * (1 - Math.sqrt(1 - Math.pow(1 - x / D.noseR, 2)));
    const dr = L - x;
    if (dr < D.tailR) p -= D.tailK * (1 - Math.sqrt(1 - Math.pow(1 - dr / D.tailR, 2)));
    p *= 1 - D.taper * Math.pow(Math.max(0, 1 - x / (L * 0.4)), 2);
    return W / 2 * p;
  };
  const flare = (D.flare || 0.02) + (wide ? 0.05 : 0);
  const bulge = (x) => { let b = 0; if (!drag) for (const ax of D.wb) b += Math.exp(-Math.pow((x - ax) / 0.55, 2)); return b * flare; };
  const inTub = (x) => D.open && x > D.tub[0] && x < D.tub[1];
  const inBed = (x) => D.bed && x > D.bed[0] && x < D.bed[1];
  // половина сечения [z, y] от низа по центру до верха по центру
  const halfRaw = (x) => {
    const t = top(x), b = bot(x), hw = halfW(x), r = roof(x);
    const span = Math.max(0.035, t - b);
    const bz = hw * (1 + bulge(x));
    const sh = D.sh * span;
    const crown = (D.crown || 0) * Math.min(1, span / 0.3);
    const pts = [[0, b], [hw * 0.84, b], [hw * 0.95, b + Math.min(0.014, span * 0.1)], [hw * 0.972, b + Math.min(0.06, span * 0.22)],
      [bz * 0.992, b + (span - sh) * 0.5], [bz, t - sh], [bz, t - sh], [bz * 0.988, t - sh * 0.45], [hw * 0.958, t - Math.min(0.012, span * 0.06)], [hw * 0.93, t]];
    if (cabin(x)) {
      const gw = hw * D.gb, rw = hw * D.gh, rh = r - t;
      pts.push([gw, t + 0.006], [gw - (gw - rw) * 0.42, t + rh * 0.5], [rw + (gw - rw) * 0.05, r - Math.min(0.05, rh * 0.25)], [rw * 0.9, r - Math.min(0.01, rh * 0.08)], [rw * 0.5, r + 0.01], [0, r + 0.014]);
    } else if (inTub(x) || inBed(x)) {
      const dep = inBed(x) ? 0.42 : 0.3, ew = inBed(x) ? 0.9 : 0.86;
      pts.push([hw * ew, t + 0.004], [hw * (ew - 0.04), t + 0.006], [hw * (ew - 0.06), t - 0.03], [hw * (ew - 0.07), t - dep * 0.85], [hw * (ew - 0.1), t - dep], [0, t - dep]);
    } else {
      pts.push([hw * 0.86, t + 0.004], [hw * 0.74, t + crown * 0.45], [hw * 0.6, t + crown * 0.7], [hw * 0.42, t + crown * 0.86], [hw * 0.2, t + crown * 0.97], [0, t + crown]);
    }
    return chaikin(pts, G3.quality === 'low' ? 1 : 2);
  };
  const cache = new Map();
  const half = (x) => { const k = Math.round(x * 2000); let h = cache.get(k); if (!h) { h = halfRaw(k / 2000); cache.set(k, h); if (cache.size > 6000) cache.clear(); } return h; };
  const inside = (x, z, y) => {
    const h = half(x); let ins = false;
    for (let i = 0, j = h.length - 1; i < h.length; j = i++) {
      const a = h[i], bb = h[j];
      if ((a[1] > y) !== (bb[1] > y) && z < (bb[0] - a[0]) * (y - a[1]) / (bb[1] - a[1]) + a[0]) ins = !ins;
    }
    return ins;
  };
  const fMax = Math.min(1.6, L * 0.4);
  // первое попадание внутрь сечения при движении от носа (или от кормы) + уточнение делением пополам
  const projF = (z, y) => {
    let prev = 0;
    if (inside(0.0005, z, y)) return 0.0005;
    for (let x = 0.02; x <= fMax + 1e-9; x += 0.02) {
      if (inside(x, z, y)) { let lo = prev, hi = x; for (let i = 0; i < 12; i++) { const m = (lo + hi) / 2; if (inside(m, z, y)) hi = m; else lo = m; } return hi; }
      prev = x;
    }
    return null;
  };
  const projR = (z, y) => {
    let prev = L;
    if (inside(L - 0.0005, z, y)) return L - 0.0005;
    for (let x = L - 0.02; x >= L - fMax - 1e-9; x -= 0.02) {
      if (inside(x, z, y)) { let lo = x, hi = prev; for (let i = 0; i < 12; i++) { const m = (lo + hi) / 2; if (inside(m, z, y)) lo = m; else hi = m; } return lo; }
      prev = x;
    }
    return null;
  };
  const sideZ = (x, y) => {
    const h = half(x); let best = null;
    for (let i = 0; i < h.length - 1; i++) { const a = h[i], b = h[i + 1]; if ((a[1] - y) * (b[1] - y) <= 0 && a[1] !== b[1]) { const z = a[0] + (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]); if (best === null || z > best) best = z; } }
    return best;
  };
  const topY = (x, z) => {
    const h = half(x); let best = null;
    for (let i = 0; i < h.length - 1; i++) { const a = h[i], b = h[i + 1]; if ((a[0] - z) * (b[0] - z) <= 0 && a[0] !== b[0]) { const y = a[1] + (b[1] - a[1]) * (z - a[0]) / (b[0] - a[0]); if (best === null || y > best) best = y; } }
    return best;
  };
  return { D, L, W, lift, top, roof, cabin, bot, halfW, bulge, cab0, cab1, sill, archR, rW, half, halfRaw, projF, projR, sideZ, topY, inTub, inBed,
    ws: (D.ws || cab0 + 0.6) , bl: (D.bl || cab1 - 0.6), roofF: D.ws || cab0 + 0.6, roofR: D.bl || cab1 - 0.6 };
}
function sectionHalf(F, x) { return F.half(x); }

// Сетка кузова: группы 0 краска, 1 стекло, 2 тёмная отделка
function buildBodyGeometry(F, c, paintRGB) {
  const D = F.D, L = F.L;
  const lowQ = G3.quality === 'low', N = lowQ ? 96 : G3.quality === 'mid' ? 120 : 140, a = 0.72;
  const xs = [];
  for (let i = 0; i <= N; i++) { const t = i / N; xs.push(L * (t - a * Math.sin(2 * Math.PI * t) / (2 * Math.PI))); }
  const K = F.halfRaw(L / 2).length, R = K * 2 - 2;
  const pos = [], col = [], cls = [];
  const blW = D.blW || 0.8;
  xs.forEach((x) => {
    const half = F.halfRaw(x);
    const ring = [];
    for (let k = K - 1; k >= 1; k--) ring.push([-half[k][0], half[k][1]]);
    for (let k = 0; k < K - 1; k++) ring.push([half[k][0], half[k][1]]);
    const hw = F.halfW(x), gw = hw * D.gb, t = F.top(x), r = F.roof(x), b = F.bot(x);
    const cab = F.cabin(x), front = x < F.ws - 0.015, back = x > F.bl + 0.015;
    const nearArch = !D.dragster && D.wb.some(ax => Math.abs(x - ax) < F.archR + 0.01);
    ring.forEach(([z, y]) => {
      pos.push(x - L / 2, y, z);
      const az = Math.abs(z);
      let k = 0;
      if (cab && y > t + 0.004) {
        const pf = gw > 0 ? D.gh / D.gb : 0.8;
        const inDlo = x > D.dlo[0] && x < D.dlo[1];
        const rw = hw * D.gh;
        let zp = rw;                                                // линия стойки (A или C/D) на этом сечении
        if (front) zp = gw * 0.94 + (rw * 0.97 - gw * 0.94) * Math.min(1, (x - F.cab0) / Math.max(0.05, F.ws - F.cab0));
        if (back) zp = rw * 0.95 + (gw * blW - rw * 0.95) * Math.min(1, (x - F.bl) / Math.max(0.05, F.cab1 - F.bl));
        if (az < zp + 0.025) {                                     // верх: лобовое, крыша, заднее
          if (front || back) k = az < zp - 0.04 ? 1 : az < zp - 0.012 ? 2 : 0;
        } else if (inDlo && y < r - 0.03) {                       // боковые стёкла
          k = (y < t + 0.022 || y > r - 0.055) ? (D.chrome && y < t + 0.022 ? 0 : 2) : 1;
          for (const p of (D.pillars || [])) if (Math.abs(x - p.x) < p.w) k = p.k;
          if (x - D.dlo[0] < 0.025 || D.dlo[1] - x < 0.025) k = 2;
        } else if (front) k = 2;
      }
      if ((F.inTub(x) || F.inBed(x)) && y < t - 0.012 && az < hw * 0.84) k = 2;
      if (y < b + 0.006 && az < hw * 0.84) k = 2;
      if (nearArch && y < b + 0.02) k = 2;
      cls.push(k);
      // мягкое затенение к низу кузова и в арках (имитация «окклюзии»)
      let sh = 1;
      if (k === 0 && !D.dragster) sh = 0.72 + 0.28 * smoothstep(b, b + 0.28, y);
      col.push(paintRGB[0] * sh, paintRGB[1] * sh, paintRGB[2] * sh);
    });
  });
  const idx = [[], [], []];
  for (let i = 0; i < N; i++) for (let j = 0; j < R; j++) {
    const jn = (j + 1) % R;
    const p0 = i * R + j, p1 = i * R + jn, p2 = p0 + R, p3 = (i + 1) * R + jn;
    const tri = (p, q, s) => { const ks = [cls[p], cls[q], cls[s]]; const g = ks.includes(2) ? 2 : (ks[0] === 1 && ks[1] === 1 && ks[2] === 1) ? 1 : 0; idx[g].push(p, q, s); };
    tri(p0, p2, p1); tri(p1, p2, p3);
  }
  const capS = pos.length / 3; pos.push(-L / 2, (F.bot(0) + F.top(0)) / 2, 0); col.push(...paintRGB); cls.push(0);
  const capE = pos.length / 3; pos.push(L / 2, (F.bot(L) + F.top(L)) / 2, 0); col.push(...paintRGB); cls.push(0);
  for (let j = 0; j < R; j++) { const jn = (j + 1) % R; idx[0].push(capS, jn, j); const o = N * R; idx[0].push(capE, o + j, o + jn); }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute('color', new THREE.Float32BufferAttribute(col, 3));
  g.setIndex(idx[0].concat(idx[1], idx[2]));
  g.addGroup(0, idx[0].length, 0); g.addGroup(idx[0].length, idx[1].length, 1); g.addGroup(idx[0].length + idx[1].length, idx[2].length, 2);
  g.computeVertexNormals();
  return g;
}

// «Наклейка» по треугольникам кузова (для карбона и полос)
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
  const uv = []; for (let i = 0; i < out.length; i += 3) uv.push(out[i] * 2, out[i + 2] * 2 + out[i + 1]);
  g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
  return new THREE.Mesh(g, mat);
}
let CARBON_TEX = null;
function carbonShared() { if (!CARBON_TEX) { CARBON_TEX = carbonTex(); CARBON_TEX.repeat.set(3, 3); } return CARBON_TEX; }

// ---------- проекция деталей на поверхность ----------
// kind: front (u=z, v=y), rear (u=z, v=y), side (u=x, v=y, s — сторона), top (u=x, v=z)
function surfMapper(F, kind, s) {
  const L = F.L;
  const base = kind === 'front' ? (u, v) => { const x = F.projF(Math.abs(u), v); return x === null ? null : [x, v, u]; }
    : kind === 'rear' ? (u, v) => { const x = F.projR(Math.abs(u), v); return x === null ? null : [x, v, u]; }
    : kind === 'side' ? (u, v) => { const z = F.sideZ(u, v); return z === null ? null : [u, v, s * z]; }
    : (u, v) => { const y = F.topY(u, Math.abs(v)); return y === null ? null : [u, y, v]; };
  const hint = kind === 'front' ? [-1, 0, 0] : kind === 'rear' ? [1, 0, 0] : kind === 'side' ? [0, 0, s] : [0, 1, 0];
  const fn = (u, v, off) => {
    const p = base(u, v); if (!p) return null;
    const e = 0.004;
    const a = base(u + e, v) || base(u - e, v), b = base(u, v + e) || base(u, v - e);
    let n = hint.slice();
    if (a && b) {
      const ax = a[0] - p[0], ay = a[1] - p[1], az = a[2] - p[2], bx = b[0] - p[0], by = b[1] - p[1], bz = b[2] - p[2];
      const cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
      const l = Math.hypot(cx, cy, cz);
      if (l > 1e-9) { n = [cx / l, cy / l, cz / l]; if (n[0] * hint[0] + n[1] * hint[1] + n[2] * hint[2] < 0) n = [-n[0], -n[1], -n[2]]; }
    }
    return [p[0] - L / 2 + n[0] * off, p[1] + n[1] * off, p[2] + n[2] * off];
  };
  fn.hint = hint;
  return fn;
}
// развернуть треугольники наружу (по направлению проекции)
function orientOut(pos, idx, hint) {
  for (let i = 0; i < idx.length; i += 3) {
    const a = idx[i] * 3, b = idx[i + 1] * 3, c = idx[i + 2] * 3;
    const ux = pos[b] - pos[a], uy = pos[b + 1] - pos[a + 1], uz = pos[b + 2] - pos[a + 2];
    const vx = pos[c] - pos[a], vy = pos[c + 1] - pos[a + 1], vz = pos[c + 2] - pos[a + 2];
    const nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
    if (nx * hint[0] + ny * hint[1] + nz * hint[2] < 0) { const t = idx[i + 1]; idx[i + 1] = idx[i + 2]; idx[i + 2] = t; }
  }
}
function densify(poly, step, closed) {
  const out = [];
  const n = poly.length, m = closed ? n : n - 1;
  for (let i = 0; i < m; i++) {
    const a = poly[i], b = poly[(i + 1) % n];
    const k = Math.max(1, Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1]) / step));
    for (let j = 0; j < k; j++) out.push([a[0] + (b[0] - a[0]) * j / k, a[1] + (b[1] - a[1]) * j / k]);
  }
  if (!closed) out.push(poly[n - 1]);
  return out;
}
// Заполненная фигура (звёздчатая относительно центра), с выпуклостью к центру — как стекло фары
function shapeMesh(map, poly, opt) {
  opt = opt || {};
  const pts = densify(poly, opt.step || 0.018, true);
  let cx = 0, cy = 0; poly.forEach(p => { cx += p[0]; cy += p[1]; }); cx /= poly.length; cy /= poly.length;
  if (opt.center) { cx = opt.center[0]; cy = opt.center[1]; }
  const RN = opt.rings || 4, off = opt.off || 0.003, bul = opt.bulge || 0;
  const pos = [], idx = [], ok = [];
  const push = (u, v, s) => { const p = map(u, v, off + bul * (1 - s * s)); if (p) { pos.push(p[0], p[1], p[2]); ok.push(1); } else { pos.push(0, 0, 0); ok.push(0); } };
  push(cx, cy, 0);
  for (let r = 1; r <= RN; r++) { const s = r / RN; pts.forEach(p => push(cx + (p[0] - cx) * s, cy + (p[1] - cy) * s, s)); }
  const M = pts.length;
  const id = (r, i) => r === 0 ? 0 : 1 + (r - 1) * M + (i % M);
  const tri = (a, b, c2) => { if (ok[a] && ok[b] && ok[c2]) idx.push(a, b, c2); };
  for (let i = 0; i < M; i++) tri(0, id(1, i), id(1, i + 1));
  for (let r = 1; r < RN; r++) for (let i = 0; i < M; i++) { tri(id(r, i), id(r + 1, i), id(r + 1, i + 1)); tri(id(r, i), id(r + 1, i + 1), id(r, i + 1)); }
  if (!idx.length) return null;
  if (map.hint) orientOut(pos, idx, map.hint);
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  g.setIndex(idx);
  g.computeVertexNormals();
  return g;
}
// Полоса вдоль линии (зазоры панелей, молдинги, накладки)
function stripMesh(map, path, width, opt) {
  opt = opt || {};
  const pts = densify(path, opt.step || 0.02, !!opt.closed);
  const n = pts.length, off = opt.off || 0.0025, w2 = width / 2, w0 = opt.inner != null ? opt.inner : -w2;
  const pos = [], idx = [], ok = [];
  for (let i = 0; i < n; i++) {
    const pa = pts[opt.closed ? (i - 1 + n) % n : Math.max(0, i - 1)], pb = pts[opt.closed ? (i + 1) % n : Math.min(n - 1, i + 1)];
    let tx = pb[0] - pa[0], ty = pb[1] - pa[1]; const l = Math.hypot(tx, ty) || 1; tx /= l; ty /= l;
    const nx = -ty, ny = tx;
    const p = pts[i];
    [w0, w2].forEach(w => { const q = map(p[0] + nx * w, p[1] + ny * w, off); if (q) { pos.push(q[0], q[1], q[2]); ok.push(1); } else { pos.push(0, 0, 0); ok.push(0); } });
  }
  const m = opt.closed ? n : n - 1;
  for (let i = 0; i < m; i++) {
    const a = i * 2, b = a + 1, c2 = ((i + 1) % n) * 2, d = c2 + 1;
    if (ok[a] && ok[b] && ok[c2]) idx.push(a, c2, b);
    if (ok[b] && ok[c2] && ok[d]) idx.push(b, c2, d);
  }
  if (!idx.length) return null;
  if (map.hint) orientOut(pos, idx, map.hint);
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  g.setIndex(idx); g.computeVertexNormals();
  return g;
}
function arcPath(cx, cy, r, a0, a1, n) { const out = []; for (let i = 0; i <= n; i++) { const a = a0 + (a1 - a0) * i / n; out.push([cx + Math.cos(a) * r, cy + Math.sin(a) * r]); } return out; }
function ellipsePoly(cx, cy, rx, ry, n) { const out = []; for (let i = 0; i < n; i++) { const a = i / n * Math.PI * 2; out.push([cx + Math.cos(a) * rx, cy + Math.sin(a) * ry]); } return out; }
function rrectPoly(x0, y0, x1, y1, r, n) {
  const out = []; n = n || 4;
  const cs = [[x1 - r, y1 - r, 0], [x0 + r, y1 - r, Math.PI / 2], [x0 + r, y0 + r, Math.PI], [x1 - r, y0 + r, Math.PI * 1.5]];
  cs.forEach(([cx, cy, a0]) => { for (let i = 0; i <= n; i++) { const a = a0 + i / n * Math.PI / 2; out.push([cx + Math.cos(a) * r, cy + Math.sin(a) * r]); } });
  return out;
}
function plateTex() {
  if (plateTex.t) return plateTex.t;
  const cv = document.createElement('canvas'); cv.width = 256; cv.height = 56; const g = cv.getContext('2d');
  g.fillStyle = '#f1f2f0'; g.fillRect(0, 0, 256, 56); g.strokeStyle = '#111'; g.lineWidth = 4; g.strokeRect(3, 3, 250, 50);
  g.fillStyle = '#16181b'; g.font = 'bold 36px sans-serif'; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText('M 042 GR', 128, 30);
  const t = new THREE.CanvasTexture(cv); t.encoding = THREE.sRGBEncoding; plateTex.t = t; return t;
}
function meshTex(kind) {
  const key = 'k' + kind; if (meshTex[key]) return meshTex[key];
  const cv = document.createElement('canvas'); cv.width = cv.height = 64; const g = cv.getContext('2d');
  g.fillStyle = '#050506'; g.fillRect(0, 0, 64, 64);
  if (kind === 'hex') { g.strokeStyle = '#3a3d43'; g.lineWidth = 2; for (let y = 0; y < 5; y++) for (let x = 0; x < 5; x++) { const cx = x * 16 + (y % 2) * 8, cy = y * 14; g.beginPath(); for (let k = 0; k < 6; k++) { const a = k / 6 * Math.PI * 2 + Math.PI / 6; g.lineTo(cx + Math.cos(a) * 7, cy + Math.sin(a) * 7); } g.closePath(); g.stroke(); } }
  else if (kind === 'h') { g.fillStyle = '#4a4e55'; for (let y = 0; y < 64; y += 10) g.fillRect(0, y, 64, 3); g.fillStyle = '#1c1e22'; for (let y = 3; y < 64; y += 10) g.fillRect(0, y, 64, 1); }
  else if (kind === 'v') { g.fillStyle = '#34373c'; for (let x = 0; x < 64; x += 8) g.fillRect(x, 0, 2.5, 64); }
  else { g.fillStyle = '#26282c'; for (let y = 0; y < 64; y += 8) for (let x = (y / 8 % 2) * 4; x < 64; x += 8) g.fillRect(x, y, 4, 4); }
  const t = new THREE.CanvasTexture(cv); t.wrapS = t.wrapT = THREE.RepeatWrapping; t.encoding = THREE.sRGBEncoding; meshTex[key] = t; return t;
}
function planarUV(g, axes, scale) {
  const p = g.attributes.position, uv = [];
  for (let i = 0; i < p.count; i++) { const v = [p.getX(i), p.getY(i), p.getZ(i)]; uv.push(v[axes[0]] * scale, v[axes[1]] * scale); }
  g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
  return g;
}

// ================= СБОРКА МАШИНЫ =================
Car3D.prototype.build = function (c, stats, eng) {
  const D = DESIGNS[c.body] || CAR_PROFILES[c.body] || DESIGNS.hatch;
  const isDrag = !!D.dragster;
  if (isDrag && !D.sh) Object.assign(D, { gh: 0.64, gb: 0.84, sh: 0.2, crown: 0.02, ws: 4.55, bl: 4.7, dlo: [9, 9], blW: 0.6, pillars: [] });
  const root = new THREE.Group();
  const rW = stats ? stats.rWheel : 0.3;
  const F = bodyForm(D, c, rW);
  const L = F.L, W = F.W, X = (x) => x - L / 2, hw0 = W / 2;
  const lift = F.lift;
  const col = new THREE.Color(c.color || '#c8312b'); col.convertSRGBToLinear();
  const fin = PAINT_FIN[c.finish || 'gloss'] || PAINT_FIN.gloss;
  const paint = new THREE.MeshPhysicalMaterial({ color: 0xffffff, vertexColors: true, metalness: fin[0], roughness: fin[1], clearcoat: fin[2], clearcoatRoughness: 0.03, envMapIntensity: 1.3 });
  if (c.finish === 'pearl') { paint.sheen = 1; paint.sheenColor = new THREE.Color(0xffffff); paint.sheenRoughness = 0.35; }
  const paintSolid = new THREE.MeshPhysicalMaterial({ color: col.clone(), metalness: fin[0], roughness: fin[1], clearcoat: fin[2], clearcoatRoughness: 0.03, envMapIntensity: 1.3 });
  const tintK = { none: 0.45, light: 0.72, dark: 0.88, limo: 0.96 }[c.tint || 'light'];
  const glass = new THREE.MeshPhysicalMaterial({ color: 0x03060a, metalness: 0.4, roughness: 0.02, clearcoat: 1, transparent: true, opacity: tintK, envMapIntensity: 1.6, side: THREE.DoubleSide });
  const trim = std(0x0c0d0f, 0.25, 0.6);
  const plastic = std(0x141518, 0.05, 0.78);
  const black = std(0x101114, 0.3, 0.45), chrome = std(0xf4f6f8, 1, 0.06), satin = std(0xb9bec4, 0.9, 0.28);
  const gapM = new THREE.MeshBasicMaterial({ color: 0x050505, polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -2 });
  const carbon = std(0x2a2c30, 0.5, 0.18, { map: carbonShared() });
  const add = (geo, mat, pos, rot, parent) => { const m = new THREE.Mesh(geo, mat); if (pos) m.position.copy(pos); if (rot) m.rotation.set(rot[0] || 0, rot[1] || 0, rot[2] || 0); (parent || root).add(m); return m; };
  const addG = (g, mat) => { if (g) { const m = new THREE.Mesh(g, mat); root.add(m); return m; } return null; };
  const decal = (pred, mat, off) => { const m = surfaceDecal(bodyGeo, F, pred, mat, off); if (m) root.add(m); return m; };
  const mFront = surfMapper(F, 'front'), mRear = surfMapper(F, 'rear'), mTop = surfMapper(F, 'top');
  const mSide = { 1: surfMapper(F, 'side', 1), '-1': surfMapper(F, 'side', -1) };
  const U = (poly) => poly.map(([u, v]) => [u * hw0, v + lift]);          // доля ширины -> метры
  const both = (poly) => [poly, poly.map(([z, y]) => [-z, y]).reverse()];  // левая и правая копии

  // ---- кузов ----
  const bodyGeo = buildBodyGeometry(F, c, [col.r, col.g, col.b]);
  root.add(new THREE.Mesh(bodyGeo, [paint, glass, trim]));
  const top = F.top, hw = F.halfW, red = c.reduction || 0;
  const sideX = (x) => hw(x) * (1 + F.bulge(x));

  if (!isDrag) {
    // ---- зазоры панелей ----
    const gw = 0.0055;
    const gap = (map, path, closed) => addG(stripMesh(map, path, gw, { closed, off: 0.0018 }), gapM);
    const doorBot = F.sill + 0.075;
    const dF = D.wb[0] + F.archR + 0.11;
    const belt = (x) => top(x) - 0.012;
    const bx = (D.pillars && D.pillars[0]) ? D.pillars[0].x : (D.doorEnd || F.cab1 - 0.6);
    const rearArch = (x0) => {                       // путь по краю задней арки
      const r = F.archR + 0.07, ax = D.wb[1];
      const pts = [];
      const a0 = Math.PI - Math.acos(Math.max(-1, Math.min(1, (ax - x0) / r)));
      for (let i = 0; i <= 10; i++) { const a = a0 + (Math.PI - a0) * i / 10; const y = rW + Math.sin(a) * r; if (y < doorBot) break; pts.push([ax + Math.cos(a) * r, y]); }
      return pts;
    };
    [1, -1].forEach(s => {
      const m = mSide[s];
      const doorsX = [dF];
      gap(m, [[dF, belt(dF)], [dF + 0.01, doorBot + 0.04], [dF + 0.04, doorBot]]);       // передний край двери
      if (D.doors === 4) {
        gap(m, [[bx + 0.02, belt(bx)], [bx + 0.02, doorBot]]);                                // стык дверей
        const xr = D.wb[1] - F.archR - 0.02;
        gap(m, [[xr + 0.06, belt(xr + 0.06)], [xr + 0.06, rW + F.archR + 0.09]].concat(rearArch(xr + 0.06)));
        gap(m, [[dF + 0.04, doorBot], [Math.max(bx + 0.3, xr - 0.1), doorBot]]);
        doorsX.push(bx + 0.02);
        this.handles = [bx - 0.2, xr - 0.08];
      } else {
        const de = D.doorEnd || bx;
        gap(m, [[de, belt(de)], [de + 0.02, doorBot + 0.04], [de - 0.03, doorBot]]);
        gap(m, [[dF + 0.04, doorBot], [de - 0.03, doorBot]]);
        this.handles = [de - 0.2];
      }
      // ручки дверей
      this.handles.forEach(hx => {
        const hy = top(hx) - 0.085 - (D.sh > 0.3 ? 0.02 : 0);
        const hp = rrectPoly(hx - 0.075, hy - 0.012, hx + 0.075, hy + 0.012, 0.011, 3);
        addG(shapeMesh(m, hp, { off: 0.004, bulge: 0.008, rings: 3 }), D.chrome ? chrome : paintSolid);
        addG(shapeMesh(m, rrectPoly(hx - 0.06, hy - 0.022, hx + 0.06, hy - 0.008, 0.006, 2), { off: 0.0015, rings: 2 }), trim);
      });
      // стыки бамперов с крыльями
      const fb = D.wb[0] - F.archR * 0.72;
      gap(m, [[fb + 0.06, top(fb) - 0.04], [fb, rW + Math.sqrt(Math.max(0, F.archR * F.archR - (fb - D.wb[0]) ** 2)) + 0.012]]);
      const rb = D.wb[1] + F.archR * 0.72;
      if (D.rear !== 'bed') gap(m, [[rb, (D.bumpY || 0.55) + lift], [rb, rW + Math.sqrt(Math.max(0, F.archR * F.archR - (rb - D.wb[1]) ** 2)) + 0.012]]);
      // повторитель поворота на крыле
      const rx = D.wb[0] + F.archR + 0.05, ry = top(rx) - 0.13;
      if (!D.mid) addG(shapeMesh(m, rrectPoly(rx - 0.03, ry - 0.008, rx + 0.03, ry + 0.008, 0.007, 2), { off: 0.003, bulge: 0.003 }), new THREE.MeshStandardMaterial({ color: 0x8a4a00, emissive: 0xff8a10, emissiveIntensity: 0.5, roughness: 0.2 }));
      // молдинги окон (хром для бизнес-класса)
      if (D.chrome && !D.open) addG(stripMesh(m, [[D.dlo[0] + 0.05, top(D.dlo[0] + 0.05) + 0.012], [D.dlo[1] - 0.04, top(D.dlo[1] - 0.04) + 0.012]], 0.014, { off: 0.003 }), chrome);
      // пластиковые накладки (кроссовер, внедорожник)
      if (D.clad) {
        D.wb.forEach(ax => {
          const r0 = F.archR + 0.005, r1 = F.archR + 0.075, pa = [], pb = [];
          for (let i = 0; i <= 20; i++) { const a = Math.PI * i / 20; const y0 = rW + Math.sin(a) * r0; if (y0 < F.sill - 0.02) continue; pa.push([ax + Math.cos(a) * ((r0 + r1) / 2), rW + Math.sin(a) * ((r0 + r1) / 2)]); }
          addG(stripMesh(m, pa, r1 - r0, { off: 0.006 }), plastic);
        });
        addG(stripMesh(m, [[D.wb[0] + F.archR + 0.04, F.sill + 0.06], [D.wb[1] - F.archR - 0.04, F.sill + 0.06]], 0.11, { off: 0.006 }), plastic);
      }
      // боковые воздухозаборники (суперкар) и жабры (купе, спорткар)
      if (D.sideIntake) {
        const x1 = D.wb[1] - F.archR - 0.06, x0 = x1 - 0.42, y0 = F.sill + 0.2, y1 = Math.min(top(x1) - 0.1, y0 + 0.24);
        const ig = shapeMesh(m, [[x0 + 0.12, y0 + 0.04], [x1, y0], [x1, y1], [x0, y1 - 0.02]], { off: 0.002, rings: 3 });
        if (ig) addG(planarUV(ig, [0, 1], 8), std(0x050506, 0.2, 0.7, { map: meshTex('h') }));
      }
      if (D.sideVent) { const vx = D.wb[0] + F.archR + 0.12, vy = (top(vx) + F.sill) / 2 + 0.03; addG(shapeMesh(m, [[vx, vy - 0.04], [vx + 0.22, vy - 0.02], [vx + 0.22, vy + 0.0], [vx, vy + 0.02]], { off: 0.002 }), trim); }
      // лючок бензобака (справа)
      if (s === 1 && D.rear !== 'engine') { const fx = D.wb[1] + (D.rear === 'bed' ? -F.archR - 0.25 : 0.12), fy = top(fx) - 0.13; gap(m, rrectPoly(fx - 0.08, fy - 0.07, fx + 0.08, fy + 0.07, 0.03, 3), true); }
    });
    // капот
    const hx0 = D.hoodX || 0.25, hx1 = F.cab0 - 0.03;
    const hzF = (x) => hw(x) * 0.8;
    const hoodPts = [];
    for (let i = 0; i <= 12; i++) { const x = hx0 + (hx1 - hx0) * i / 12; hoodPts.push([x, hzF(x)]); }
    [1, -1].forEach(s => gap(mTop, hoodPts.map(([x, z]) => [x, s * z])));
    const crossTop = (x, frac) => { const out = []; for (let i = 0; i <= 14; i++) out.push([x, -hw(x) * frac + 2 * hw(x) * frac * i / 14]); return out; };
    gap(mTop, crossTop(hx0, 0.8));
    // багажник / дверь задка
    if (D.rear === 'trunk') {
      const tx0 = F.cab1 + 0.03, tx1 = L - 0.04;
      const tz = (x) => hw(x) * 0.8;
      gap(mTop, crossTop(tx0, 0.8));
      [1, -1].forEach(s => { const pts = []; for (let i = 0; i <= 8; i++) { const x = tx0 + (tx1 - tx0 - 0.1) * i / 8; pts.push([x, s * tz(x)]); } gap(mTop, pts); });
      const by = (D.bumpY || 0.55) + lift + 0.05, ty = top(L - 0.12) - 0.03, zz = hw(L - 0.1) * 0.8;
      gap(mRear, [[zz, ty], [zz, by], [-zz, by], [-zz, ty]]);
    } else if (D.rear === 'hatch') {
      const by = (D.bumpY || 0.55) + lift + 0.05, zz = hw(L - 0.1) * 0.74, ty = F.top(L - 0.2) + 0.05;
      gap(mRear, [[zz, ty], [zz, by], [-zz, by], [-zz, ty]]);
    } else if (D.rear === 'bed') {
      const by = F.sill + 0.12 + 0.18, zz = hw(L - 0.1) * 0.9, ty = top(L - 0.05) - 0.02;
      gap(mRear, [[zz, ty], [zz, by], [-zz, by], [-zz, ty]]);
      addG(stripMesh(mRear, [[-0.15, ty - 0.12], [0.15, ty - 0.12]], 0.035, { off: 0.004 }), trim);
    } else if (D.rear === 'engine') {
      // жалюзи над мотором
      const x0 = F.cab1 + 0.08, x1 = L - 0.35;
      for (let k = 0; k < 7; k++) { const x = x0 + (x1 - x0) * k / 7; addG(stripMesh(mTop, [[x, -hw(x) * 0.42], [x, hw(x) * 0.42]], 0.022, { off: 0.002 }), trim); }
    }
    // линия заднего бампера
    if (D.rear !== 'bed') gap(mRear, [[-hw(L - 0.02) * 0.95, (D.bumpY || 0.55) + lift], [hw(L - 0.02) * 0.95, (D.bumpY || 0.55) + lift]]);

    // ---- фары ----
    const led = c.lights === 'led';
    const H = D.head;
    const housing = std(0x15171b, 0.85, 0.22), reflector = std(0xd9dde2, 1, 0.12);
    const lensM = new THREE.MeshPhysicalMaterial({ color: 0xffffff, metalness: 0, roughness: 0.02, clearcoat: 1, transparent: true, opacity: 0.16, envMapIntensity: 2.2, depthWrite: false });
    const drlM = new THREE.MeshStandardMaterial({ color: 0xffffff, emissive: led ? 0xdbe8ff : 0xfff4dc, emissiveIntensity: led ? 2.6 : 1.6, roughness: 0.1 });
    const bulbM = new THREE.MeshStandardMaterial({ color: 0xffffff, emissive: led ? 0xe2ecff : 0xfff0c8, emissiveIntensity: led ? 2.2 : 1.2, roughness: 0.05, metalness: 0.2 });
    const headPoly = U(H.poly);
    both(headPoly).forEach((poly, si) => {
      addG(shapeMesh(mFront, poly, { off: 0.0025, rings: 4 }), housing);
      // внутренняя рамка-отражатель
      const shrink = poly.map(([z, y]) => { const cz = poly.reduce((a, p) => a + p[0], 0) / poly.length, cy = poly.reduce((a, p) => a + p[1], 0) / poly.length; return [cz + (z - cz) * 0.86, cy + (y - cy) * 0.78]; });
      addG(stripMesh(mFront, shrink, 0.006, { closed: true, off: 0.0045 }), satin);
      // «глаза»: проекторы или LED-модули
      (H.eyes || []).forEach(([u, v]) => {
        const z = (si === 0 ? 1 : -1) * u * hw0, y = v + lift;
        const r = H.kind === 'block' || H.kind === 'oval' ? 0.03 : 0.024;
        if (led) addG(shapeMesh(mFront, rrectPoly(z - r, y - r * 0.55, z + r, y + r * 0.55, r * 0.4, 2), { off: 0.006, bulge: 0.004 }), bulbM);
        else { addG(shapeMesh(mFront, ellipsePoly(z, y, r, r, 14), { off: 0.005, bulge: 0.008 }), reflector); addG(shapeMesh(mFront, ellipsePoly(z, y, r * 0.45, r * 0.45, 10), { off: 0.012 }), bulbM); }
      });
      // ходовые огни: полоса по верхнему краю фары
      const edge = poly.slice(Math.floor(poly.length / 2) - 1).concat([poly[0]]);
      const drlPath = (H.kind === 'block' || H.kind === 'truck') ? [[poly[0][0], poly[0][1] + 0.012], [poly[1][0], poly[1][1] + 0.012], [poly[2][0], poly[2][1] + 0.01]] : edge.map(([z, y]) => [z, y - 0.012]);
      addG(stripMesh(mFront, (si === 0 ? drlPath : drlPath), led ? 0.01 : 0.006, { off: 0.0065 }), drlM);
      // стекло
      addG(shapeMesh(mFront, poly, { off: 0.004, bulge: 0.012, rings: 5 }), lensM);
    });
    if (H.low) both(U(H.low)).forEach(poly => {
      addG(shapeMesh(mFront, poly, { off: 0.003 }), housing);
      const zc = poly.reduce((a, p) => a + p[0], 0) / poly.length, yc = poly.reduce((a, p) => a + p[1], 0) / poly.length;
      [-0.045, 0.045].forEach(dz => { addG(shapeMesh(mFront, ellipsePoly(zc + dz, yc, 0.03, 0.03, 14), { off: 0.005, bulge: 0.008 }), reflector); addG(shapeMesh(mFront, ellipsePoly(zc + dz, yc, 0.012, 0.012, 10), { off: 0.012 }), bulbM); });
      addG(shapeMesh(mFront, poly, { off: 0.006, bulge: 0.01 }), lensM);
    });
    // ---- решётка и воздухозаборники ----
    const G = D.grille;
    if (G) {
      const gp = mirrorPoly(U(G.poly));
      if (G.kind === 'none') addG(planarUV(shapeMesh(mFront, gp, { off: 0.002 }), [2, 1], 6), std(0x060607, 0.3, 0.6, { map: meshTex('h') }));
      else {
        const tex = { wide: 'h', slot: 'h', tall: 'v', mesh: 'hex', truck: 'h', mouth: 'hex' }[G.kind] || 'h';
        const ins = shapeMesh(mFront, gp, { off: 0.0015, rings: 4 });
        if (ins) addG(planarUV(ins, [2, 1], G.kind === 'truck' ? 4 : 7), std(0x08090a, 0.5, 0.4, { map: meshTex(tex) }));
        const surround = G.kind === 'tall' || G.kind === 'truck' || D.chrome ? chrome : G.kind === 'mesh' || G.kind === 'mouth' ? black : satin;
        addG(stripMesh(mFront, gp, G.kind === 'truck' ? 0.03 : 0.014, { closed: true, off: 0.004 }), surround);
        if (G.kind === 'truck' || G.kind === 'tall') {
          const ys = G.poly.map(p => p[1]), y0 = Math.min(...ys) + lift, y1 = Math.max(...ys) + lift, zw = Math.max(...G.poly.map(p => p[0])) * hw0;
          if (G.kind === 'truck') for (let k = 1; k < 3; k++) { const y = y0 + (y1 - y0) * k / 3; addG(stripMesh(mFront, [[-zw * 0.97, y], [zw * 0.97, y]], 0.03, { off: 0.006 }), satin); }
          else for (let k = -3; k <= 3; k++) { const z = k / 3.6 * zw; addG(stripMesh(mFront, [[z, y0 + 0.01], [z, y1 - 0.01]], 0.008, { off: 0.005 }), chrome); }
        }
      }
    }
    if (D.intake) {
      const ip = mirrorPoly(U(D.intake.poly));
      const g = shapeMesh(mFront, ip, { off: 0.0015 }); if (g) addG(planarUV(g, [2, 1], 7), std(0x060607, 0.2, 0.7, { map: meshTex('hex') }));
      addG(stripMesh(mFront, ip, 0.01, { closed: true, off: 0.003 }), D.chrome ? satin : black);
    }
    if (D.fog) both([[D.fog[0] * hw0, D.fog[1] + lift]]).forEach(([[z, y]]) => {
      addG(shapeMesh(mFront, ellipsePoly(z, y, D.fog[2] * 1.5, D.fog[2], 14), { off: 0.002 }), trim);
      addG(shapeMesh(mFront, ellipsePoly(z, y, D.fog[2] * 0.8, D.fog[2] * 0.8, 12), { off: 0.004, bulge: 0.006 }), bulbM);
    });
    if (D.skid) addG(shapeMesh(mFront, mirrorPoly([[0, F.sill + 0.11 + lift * 0], [0.42 * hw0, F.sill + 0.11], [0.45 * hw0, F.sill + 0.16], [0, F.sill + 0.165]]), { off: 0.004 }), satin);
    if (D.clad) addG(shapeMesh(mRear, mirrorPoly([[0, F.sill + 0.14], [0.8 * hw0, F.sill + 0.14], [0.86 * hw0, F.sill + 0.3], [0, F.sill + 0.3]]), { off: 0.003 }), plastic);
    // ---- задние фонари ----
    const T = D.tail;
    const tailHouse = new THREE.MeshPhysicalMaterial({ color: 0x2a0303, metalness: 0.3, roughness: 0.15, clearcoat: 1, emissive: 0x3a0000, emissiveIntensity: 0.6 });
    const tailGlow = new THREE.MeshStandardMaterial({ color: 0x500606, emissive: 0xff1a12, emissiveIntensity: led ? 2.2 : 1.6, roughness: 0.2 });
    const revM = new THREE.MeshStandardMaterial({ color: 0xffffff, emissive: 0xffffff, emissiveIntensity: 0.35, roughness: 0.15 });
    const tlensM = new THREE.MeshPhysicalMaterial({ color: 0xff3a30, metalness: 0, roughness: 0.03, clearcoat: 1, transparent: true, opacity: 0.22, envMapIntensity: 2, depthWrite: false });
    const tp = U(T.poly);
    both(tp).forEach((poly, si) => {
      addG(shapeMesh(mRear, poly, { off: 0.0025, rings: 4 }), tailHouse);
      const ys = poly.map(p => p[1]), zs = poly.map(p => Math.abs(p[0]));
      const y0 = Math.min(...ys), y1 = Math.max(...ys), z0 = Math.min(...zs), z1 = Math.max(...zs), sg = si === 0 ? 1 : -1;
      // светящаяся графика: контур «C» или две полосы
      if (T.kind === 'twin') { [0.32, 0.72].forEach(f => { const zc = sg * (z0 + (z1 - z0) * f); addG(shapeMesh(mRear, ellipsePoly(zc, (y0 + y1) / 2, (z1 - z0) * 0.15, (y1 - y0) * 0.33, 14), { off: 0.005, bulge: 0.004 }), tailGlow); }); }
      else {
        const inner = poly.map(([z, y]) => { const cz = poly.reduce((a, p) => a + p[0], 0) / poly.length, cy = poly.reduce((a, p) => a + p[1], 0) / poly.length; return [cz + (z - cz) * 0.8, cy + (y - cy) * 0.6]; });
        addG(stripMesh(mRear, inner, led ? 0.014 : 0.01, { closed: true, off: 0.005 }), tailGlow);
        addG(shapeMesh(mRear, [[sg * (z0 + (z1 - z0) * 0.25), y0 + (y1 - y0) * 0.38], [sg * (z0 + (z1 - z0) * 0.5), y0 + (y1 - y0) * 0.38], [sg * (z0 + (z1 - z0) * 0.5), y0 + (y1 - y0) * 0.6], [sg * (z0 + (z1 - z0) * 0.25), y0 + (y1 - y0) * 0.6]], { off: 0.0045 }), revM);
      }
      addG(shapeMesh(mRear, poly, { off: 0.004, bulge: 0.008, rings: 4 }), tlensM);
    });
    if (T.kind === 'bar') { const yb = (Math.min(...tp.map(p => p[1])) + Math.max(...tp.map(p => p[1]))) / 2, zb = Math.min(...tp.map(p => p[0])); addG(stripMesh(mRear, [[-zb, yb], [zb, yb]], 0.012, { off: 0.004 }), tailGlow); }
    // стоп-сигнал над задним стеклом
    if (!D.open && D.rear !== 'engine') { const x = F.bl + 0.04; addG(stripMesh(mTop, [[x, -0.2], [x, 0.2]], 0.014, { off: 0.004 }), tailGlow); }
    // ---- номера ----
    const plateM = std(0xffffff, 0, 0.45, { map: plateTex() });
    const plate = (map, y, rot) => { const p = map(0, y + lift, 0.012); if (!p) return; const m = add(new THREE.PlaneGeometry(0.52, 0.115), plateM, V3(p[0], p[1], p[2]), [0, rot, 0]); };
    if (D.plateF) plate(mFront, D.plateF, -Math.PI / 2);
    plate(mRear, D.plateR || 0.6, Math.PI / 2);
    // ---- рейлинги, антенна, люк ----
    if (D.rails && !D.open) [1, -1].forEach(s => {
      const pts = [];
      for (let i = 0; i <= 12; i++) { const x = F.ws + 0.12 + (F.bl - F.ws - 0.18) * i / 12; const z = s * hw(x) * D.gh * 0.82; const y = F.topY(x, Math.abs(z)); if (y !== null) pts.push(V3(X(x), y + 0.045, z)); }
      if (pts.length > 3) { add(new THREE.TubeGeometry(new THREE.CatmullRomCurve3(pts), 30, 0.014, 6), satin); [pts[0], pts[pts.length - 1]].forEach(p => add(new THREE.BoxGeometry(0.08, 0.05, 0.03), black, V3(p.x, p.y - 0.025, p.z))); }
    });
    if (!D.open && D.rear !== 'engine') {
      const fx = F.bl - 0.12, fy = F.roof(fx) + 0.012;
      const fs = new THREE.Shape(); fs.moveTo(0, 0); fs.lineTo(0.14, 0); fs.quadraticCurveTo(0.03, 0.02, 0.0, 0.055); fs.lineTo(-0.01, 0.05); fs.lineTo(0, 0);
      const fg = new THREE.ExtrudeGeometry(fs, { depth: 0.035, bevelEnabled: true, bevelSize: 0.008, bevelThickness: 0.008, bevelSegments: 2 }); fg.translate(0, 0, -0.0175);
      add(fg, paintSolid, V3(X(fx), fy, 0));
    }
    if (D.sunroof && !D.open && c.roof !== 'carbon') {
      const x0 = F.ws + 0.12, x1 = D.rear === 'hatch' && c.body !== 'hatch' ? F.bl - 0.3 : F.ws + 0.75;
      const poly = rrectPoly(x0, -0.0, x1, 0, 0.04, 2).map(([x, z], i) => [x, 0]); // заглушка для формы
      const pts = []; const zf = (x) => hw(x) * D.gh * 0.62;
      for (let i = 0; i <= 8; i++) { const x = x0 + (x1 - x0) * i / 8; pts.push([x, zf(x)]); }
      for (let i = 8; i >= 0; i--) { const x = x0 + (x1 - x0) * i / 8; pts.push([x, -zf(x)]); }
      addG(shapeMesh(mTop, pts, { off: 0.003 }), glass);
      addG(stripMesh(mTop, pts, 0.012, { closed: true, off: 0.0035 }), trim);
    }
    // ---- рамки стёкол: стойки и уплотнители (скрывают «лесенку» сетки) ----
    if (!D.open) {
      const frameM = std(0x0b0c0e, 0.4, 0.3), pillarM = D.chrome ? frameM : frameM;
      const pathA = [], pathC = [];
      for (let i = 0; i <= 14; i++) { const x = F.cab0 + 0.02 + (F.ws - F.cab0 - 0.02) * i / 14, hwx = hw(x), gwx = hwx * D.gb, rwx = hwx * D.gh; const tt = Math.min(1, (x - F.cab0) / Math.max(0.05, F.ws - F.cab0)); pathA.push([x, gwx * 0.94 + (rwx * 0.97 - gwx * 0.94) * tt - 0.026]); }
      for (let i = 0; i <= 14; i++) { const x = F.bl + (F.cab1 - 0.02 - F.bl) * i / 14, hwx = hw(x), gwx = hwx * D.gb, rwx = hwx * D.gh; const tt = Math.min(1, (x - F.bl) / Math.max(0.05, F.cab1 - F.bl)); pathC.push([x, rwx * 0.95 + (gwx * (D.blW || 0.8) - rwx * 0.95) * tt - 0.026]); }
      [1, -1].forEach(s => {
        addG(stripMesh(mTop, pathA.map(([x, z]) => [x, s * z]), 0.045, { off: 0.003 }), pillarM);
        addG(stripMesh(mTop, pathC.map(([x, z]) => [x, s * z]), 0.045, { off: 0.003 }), pillarM);
        const m = mSide[s], d0 = Math.max(D.dlo[0], F.cab0 + 0.02), d1 = Math.min(D.dlo[1], F.cab1 - 0.02);
        if (d1 > d0) {
          const topP = [], botP = [];
          for (let i = 0; i <= 24; i++) { const x = d0 + (d1 - d0) * i / 24; topP.push([x, F.roof(x) - 0.042]); botP.push([x, top(x) + 0.014]); }
          addG(stripMesh(m, topP.filter(p => p[1] > top(p[0]) + 0.05), 0.026, { off: 0.003 }), frameM);
          addG(stripMesh(m, botP, 0.02, { off: 0.003 }), D.chrome ? chrome : frameM);
          (D.pillars || []).forEach(pl => addG(stripMesh(m, [[pl.x, top(pl.x) + 0.01], [pl.x, F.roof(pl.x) - 0.03]], pl.w * 2, { off: 0.003 }), frameM));
          [d0 + 0.012, d1 - 0.012].forEach(x => { if (F.roof(x) - 0.04 > top(x) + 0.03) addG(stripMesh(m, [[x, top(x) + 0.01], [x, F.roof(x) - 0.03]], 0.026, { off: 0.003 }), frameM); });
        }
      });
    }
    // ---- зеркала ----
    [1, -1].forEach(s => {
      const mx = Math.max(F.cab0 + 0.12, D.dlo[0] + 0.02), t0 = top(mx), my = t0 + 0.085, gz = hw(mx) * D.gb;
      const shellM = c.kit === 'wide' || red >= 3 ? carbon : paintSolid;
      add(new THREE.BoxGeometry(0.12, 0.05, 0.05), black, V3(X(mx) + 0.02, t0 + 0.03, s * (gz + 0.01)));
      const arm = add(new THREE.BoxGeometry(0.05, 0.022, 0.08), black, V3(X(mx) + 0.04, my - 0.025, s * (gz + 0.05)));
      const shell = add(new THREE.SphereGeometry(1, 22, 14), shellM, V3(X(mx) + 0.03, my, s * (gz + 0.13))); shell.scale.set(0.065, 0.062, 0.105);
      const mg = add(new THREE.CircleGeometry(1, 24), std(0x8fa2b4, 1, 0.03), V3(X(mx) + 0.083, my, s * (gz + 0.13)), [0, Math.PI / 2, 0]); mg.scale.set(0.095, 0.05, 1);
      if (D.chrome || D.clad) add(new THREE.BoxGeometry(0.012, 0.01, 0.12), new THREE.MeshStandardMaterial({ color: 0x8a4a00, emissive: 0xff8a10, emissiveIntensity: 0.5 }), V3(X(mx) - 0.025, my - 0.045, s * (gz + 0.15)));
    });
    // ---- дворники ----
    if (!D.open) {
      const wx = F.cab0 + 0.05, wy = top(wx) + 0.02;
      [-1, 1].forEach(s => add(new THREE.BoxGeometry(0.018, 0.012, W * 0.36), black, V3(X(wx) + 0.03, wy, s * W * 0.15 - 0.1), [0, s * 0.12, 0.3]));
      if (D.rear === 'hatch') { const rx = F.bl + (F.cab1 - F.bl) * 0.6; add(new THREE.BoxGeometry(0.016, 0.012, 0.36), black, V3(X(rx) + 0.02, F.roof(rx) + 0.01, 0.12), [0, 0.3, -1.0]); }
    }
  }

  // ---- гоночные полосы, карбон ----
  if (c.stripe) decal((x, y, z, nx, ny) => ny > 0.2 && Math.abs(Math.abs(z) - 0.16) < 0.07 && y > F.sill + 0.25, new THREE.MeshPhysicalMaterial({ color: 0xf2f3f5, roughness: 0.3, clearcoat: 1 }), 0.002);
  const hoodEnd = F.cab0 - 0.03;
  const carbonHood = c.hood === 'carbon' || red >= 3;
  if (carbonHood && !isDrag) decal((x, y, z, nx, ny) => x > (D.hoodX || 0.25) + 0.01 && x < hoodEnd && ny > 0.5 && Math.abs(z) < hw(x) * 0.79, carbon, 0.003);
  if ((c.roof === 'carbon' || red >= 3) && !D.open && !isDrag) decal((x, y, z, nx, ny) => x > F.ws && x < F.bl && y > F.roof(x) - 0.03 && ny > 0.6, carbon, 0.003);
  if (red >= 2 && !isDrag && D.rear === 'trunk') decal((x, y, z, nx, ny) => x > F.cab1 + 0.05 && x < L - 0.1 && ny > 0.5 && Math.abs(z) < hw(x) * 0.78, carbon, 0.003);

  // ---- обвес ----
  if ((c.kit === 'sport' || c.kit === 'wide') && !isDrag) {
    [1, -1].forEach(s => addG(stripMesh(mSide[s], [[D.wb[0] + F.archR + 0.03, F.sill + 0.03], [D.wb[1] - F.archR - 0.03, F.sill + 0.03]], 0.07, { off: 0.012 }), carbon));
    add(new THREE.BoxGeometry(0.3, 0.02, W * 0.85), carbon, V3(X(0.12), F.bot(0.12) - 0.01, 0));
  }
  if (c.kit === 'wide' && !isDrag) [1, -1].forEach(s => D.wb.forEach(ax => {
    const pa = []; for (let i = 0; i <= 20; i++) { const a = Math.PI * i / 20; const y = rW + Math.sin(a) * (F.archR + 0.04); if (y > F.sill - 0.03) pa.push([ax + Math.cos(a) * (F.archR + 0.04), y]); }
    addG(stripMesh(mSide[s], pa, 0.06, { off: 0.01 }), black);
  }));
  // ---- капот: горб, воздухозаборник, компрессор ----
  const hx = ((D.hoodX || 0.3) + 0.15 + hoodEnd) / 2;
  const bigEngine = eng && (eng.cyl >= 8 || displacement(eng) > 4.2);
  const roots = eng && (eng.induction === 'sc_roots' || eng.induction === 'sc_screw' || eng.induction === 'twincharge');
  if (!isDrag && !D.mid && D.wb[0] < hoodEnd) {
    const hy = top(hx);
    const slope = Math.atan2(top(hoodEnd) - top(0.5), hoodEnd - 0.5);
    if (bigEngine && !roots) { const b = add(new THREE.SphereGeometry(1, 28, 12, 0, Math.PI * 2, 0, Math.PI / 2), carbonHood ? carbon : paintSolid, V3(X(hx), hy - 0.004, 0), [0, 0, slope]); b.scale.set((hoodEnd - 0.5) * 0.42, 0.035, hw(hx) * 0.3); }
    if (c.hood === 'scoop' || (eng && hasTurbo(eng) && c.hood !== 'stock')) {
      add(new THREE.BoxGeometry(0.42, 0.07, W * 0.26), carbonHood ? carbon : paintSolid, V3(X(hx + 0.15), hy + 0.03, 0), [0, 0, slope]);
      add(new THREE.BoxGeometry(0.02, 0.05, W * 0.22), trim, V3(X(hx - 0.06), hy + 0.035, 0), [0, 0, slope]);
    }
    if (eng && hasTurbo(eng)) decal((x, y, z, nx, ny) => x > hx + 0.05 && x < hx + 0.35 && ny > 0.6 && Math.abs(Math.abs(z) - hw(x) * 0.5) < 0.07 && Math.floor(x * 40) % 2 === 0, trim, 0.004);
    if (roots) {
      const by = hy + 0.06;
      add(new THREE.BoxGeometry(0.42, 0.13, 0.32), std(0xc9ced5, 1, 0.2), V3(X(hx - 0.05), by, 0), [0, 0, slope]);
      for (let k = -3; k <= 3; k++) add(new THREE.BoxGeometry(0.4, 0.008, 0.005), std(0x9aa1aa, 1, 0.3), V3(X(hx - 0.05), by, 0.16 + 0.002), [0, 0, slope]);
      add(new THREE.BoxGeometry(0.3, 0.12, 0.26), std(0x16171a, 0.5, 0.3), V3(X(hx - 0.05), by + 0.12, 0), [0, 0, slope]);
      add(new THREE.BoxGeometry(0.02, 0.09, 0.22), std(0x050506, 0, 0.9), V3(X(hx - 0.2), by + 0.12, 0), [0, 0, slope]);
    }
    if (eng && isForced(eng) && eng.intercooler !== 'none' && D.intake) {
      const ip = mirrorPoly(U(D.intake.poly)).map(([z, y]) => [z * 0.85, y]);
      const g = shapeMesh(mFront, ip, { off: 0.003 }); if (g) addG(planarUV(g, [2, 1], 9), std(0x8f979f, 0.8, 0.35, { map: stripeTex('#a8afb7', '#2c2f34', 18, true) }));
    }
  }
  // ---- салон ----
  if (!isDrag && !D.open) {
    // тёмная обивка салона: уменьшенная копия «теплицы», видна изнутри сквозь стёкла
    const NS = 28, lp = [], li = [];
    const xa = F.cab0 + 0.03, xb = F.cab1 - 0.03;
    let RR = 0;
    for (let i = 0; i <= NS; i++) {
      const x = xa + (xb - xa) * i / NS, h = F.halfRaw(x), b0 = F.sill + 0.15, r0 = F.roof(x), cy = (b0 + r0) / 2;
      const ring = [];
      for (let k2 = h.length - 1; k2 >= 1; k2--) ring.push([-h[k2][0], h[k2][1]]);
      for (let k2 = 0; k2 < h.length - 1; k2++) ring.push([h[k2][0], h[k2][1]]);
      RR = ring.length;
      ring.forEach(([z, y]) => lp.push(X(x), cy + (Math.max(y, b0) - cy) * 0.9, z * 0.9));
    }
    for (let i = 0; i < NS; i++) for (let j = 0; j < RR; j++) { const jn = (j + 1) % RR, a0 = i * RR + j, a1 = i * RR + jn; li.push(a0, a1, a0 + RR, a1, a1 + RR, a0 + RR); }
    const lg = new THREE.BufferGeometry(); lg.setAttribute('position', new THREE.Float32BufferAttribute(lp, 3)); lg.setIndex(li); lg.computeVertexNormals();
    root.add(new THREE.Mesh(lg, new THREE.MeshBasicMaterial({ color: 0x050506, side: THREE.DoubleSide })));
  }
  const seatX = D.open ? F.cab1 + 0.35 : F.cab0 + 1.0;
  const belt = top(seatX);
  const rows = isDrag ? [0] : (red >= 2 || D.doors === 2 || D.open ? [0] : [0, 0.9]);
  const seatCol = c.seats === 'bucket' ? 0x121316 : (D.chrome ? 0x2a211a : 0x17181b);
  rows.forEach((dx, ri) => [1, -1].forEach(s => {
    if (isDrag && s < 0) return;
    const sz = isDrag ? 0 : s * W * 0.21, sx = seatX + dx;
    const bucket = c.seats === 'bucket' && ri === 0;
    const sm = std(seatCol, 0.05, 0.9);
    const cy = belt - 0.42, roofHere = (D.open || isDrag) ? belt + 0.5 : F.roof(sx + 0.3);
    const bh = Math.max(0.3, roofHere - 0.14 - cy);
    add(new THREE.BoxGeometry(0.48, 0.1, 0.46), sm, V3(X(sx), cy, sz));
    add(new THREE.BoxGeometry(0.11, bh, 0.46), sm, V3(X(sx + 0.29), cy + bh / 2, sz), [0, 0, 0.18]);
    if (bucket) [1, -1].forEach(q => add(new THREE.BoxGeometry(0.12, bh * 0.8, 0.05), std(0xc8241f, 0.1, 0.7), V3(X(sx + 0.28), cy + bh * 0.42, sz + q * 0.22), [0, 0, 0.18]));
    else add(new THREE.BoxGeometry(0.1, 0.14, 0.26), sm, V3(X(sx + 0.33), cy + bh + 0.02, sz), [0, 0, 0.18]);
  }));
  add(new THREE.BoxGeometry(0.42, 0.16, W * 0.82), std(0x141519, 0.1, 0.8), V3(X(F.cab0 + 0.25), top(F.cab0 + 0.25) - 0.06, 0));
  add(new THREE.TorusGeometry(0.18, 0.022, 8, 28), std(0x111214, 0.2, 0.6), V3(X(seatX - 0.5), belt - 0.03, isDrag ? 0 : W * 0.21), [0, Math.PI / 2 + 0.45, 0]);
  if (c.cage || isDrag || D.open) {
    const cm = std(c.cage ? 0xd23b2a : 0x2a2b30, 0.6, 0.35);
    const hx2 = X(seatX + 0.42), hz = isDrag ? 0.3 : hw(seatX) * 0.62, hy2 = D.open || isDrag ? belt + 0.45 : F.roof(seatX + 0.42) - 0.07;
    add(new THREE.TubeGeometry(new THREE.CatmullRomCurve3([V3(hx2, belt - 0.22, -hz), V3(hx2, hy2, -hz * 0.85), V3(hx2, hy2, hz * 0.85), V3(hx2, belt - 0.22, hz)]), 20, 0.025, 6), cm);
    if (c.cage && !D.open && !isDrag) [1, -1].forEach(s => add(new THREE.TubeGeometry(new THREE.CatmullRomCurve3([V3(hx2, hy2, s * hz * 0.85), V3(X(F.ws + 0.1), F.roof(F.ws + 0.1) - 0.08, s * hz * 0.82), V3(X(F.cab0 + 0.12), top(F.cab0 + 0.12) + 0.02, s * hz)]), 16, 0.022, 6), cm));
  }
  // ---- аэродинамика ----
  const rear = X(L), front = X(0);
  if (c.splitter) add(new THREE.BoxGeometry(0.24, 0.018, W * 0.88), carbon, V3(front + 0.08, F.bot(0.1) - 0.012, 0));
  if (c.diffuser) { add(new THREE.BoxGeometry(0.5, 0.02, W * 0.72), black, V3(rear - 0.2, F.sill + 0.04, 0), [0, 0, 0.16]); for (let k = -2; k <= 2; k++) add(new THREE.BoxGeometry(0.42, 0.09, 0.012), black, V3(rear - 0.22, F.sill + 0.06, k * W * 0.13), [0, 0, 0.16]); }
  const tailX = D.bed ? L - 0.15 : L - 0.18;
  const tailTop = top(tailX);
  if (c.spoiler === 'lip') add(new THREE.BoxGeometry(0.1, 0.025, W * 0.78), carbonHood ? carbon : paintSolid, V3(rear - 0.08, top(L - 0.08) + 0.015, 0), [0, 0, -0.3]);
  if (c.spoiler === 'wing' || c.spoiler === 'big' || isDrag) {
    const big = c.spoiler === 'big' || isDrag, hgt = isDrag ? 0.95 : big ? 0.32 : 0.17, chord = big ? 0.36 : 0.26;
    const af = new THREE.Shape(); af.moveTo(0, 0); af.quadraticCurveTo(chord * 0.3, 0.05, chord, 0.012); af.lineTo(chord, -0.004); af.quadraticCurveTo(chord * 0.3, -0.012, 0, 0);
    const wd = isDrag ? 1.3 : W * 0.94;
    const wg = new THREE.ExtrudeGeometry(af, { depth: wd, bevelEnabled: false, curveSegments: 10 }); wg.translate(0, 0, -wd / 2);
    add(wg, isDrag ? paintSolid : carbon, V3(rear - chord - 0.06, tailTop + hgt, 0), [0, 0, -0.1]);
    [1, -1].forEach(s => { add(new THREE.BoxGeometry(0.08, hgt, 0.018), black, V3(rear - chord * 0.5 - 0.06, tailTop + hgt / 2, s * wd * 0.28)); add(new THREE.BoxGeometry(chord * 1.15, 0.15, 0.012), black, V3(rear - chord * 0.5 - 0.06, tailTop + hgt + 0.02, s * wd / 2)); });
  }
  if (D.bed) {
    const bx0 = D.bed[0], bx1 = D.bed[1], by = top((bx0 + bx1) / 2);
    add(new THREE.BoxGeometry(bx1 - bx0 - 0.04, 0.012, W * 0.74), std(0x15161a, 0.2, 0.8, { map: stripeTex('#1d1e22', '#0f1012', 10, false) }), V3(X((bx0 + bx1) / 2), by - 0.41, 0));
    [1, -1].forEach(s => add(new THREE.BoxGeometry(bx1 - bx0, 0.02, 0.06), black, V3(X((bx0 + bx1) / 2), by + 0.01, s * hw((bx0 + bx1) / 2) * 0.88)));
  }
  // ---- выхлоп ----
  const tipR = clamp(((eng && eng.exhDia) || 50) / 1000 * 0.75, 0.03, 0.07);
  const tips = c.tips === 'quad' ? [-0.38, -0.27, 0.27, 0.38] : c.tips === 'dual' ? [-0.33, 0.33] : c.tips === 'center' ? [-0.06, 0.06] : [0.3];
  this.tipPos = [];
  const tipY = F.bot(L - 0.1) + 0.05;
  tips.forEach(z => { const p = V3(rear - 0.02, tipY, z * W * 0.9); add(new THREE.CylinderGeometry(tipR, tipR * 0.92, 0.18, 24, 1, true), chrome, p, [0, 0, Math.PI / 2]); add(new THREE.CircleGeometry(tipR * 0.9, 20), std(0x050505, 0, 1), p.clone().add(V3(0.08, 0, 0)), [0, Math.PI / 2, 0]); this.tipPos.push(p.clone().add(V3(0.11, 0, 0))); });
  // ---- колёса ----
  this.wheels = [];
  const tw = (stats ? c.tireWidth : 205) / 1000;
  const rimR = c.rim * 0.0254 / 2;
  D.wb.forEach((ax, i) => [1, -1].forEach(s => {
    let rr = rW, ww = tw;
    if (isDrag) { if (i === 0) { rr = 0.24; ww = 0.09; } else { rr = 0.46; ww = 0.43; } }
    const outer = isDrag ? (i === 0 ? W / 2 + 0.25 : W / 2 + ww / 2 + 0.12) : sideX(ax) - ww / 2 - 0.012;
    const wg = this.wheel(c, rr, ww, isDrag ? (i === 0 ? 0.17 : 0.29) : rimR, s, isDrag && i === 0);
    wg.position.set(X(ax), rr, s * outer);
    if (i === 0 && !isDrag) wg.rotation.y = s * 0; // колёса прямо
    root.add(wg); this.wheels.push(wg);
  }));
  if (isDrag) [1, -1].forEach(s => add(new THREE.CylinderGeometry(0.02, 0.02, W / 2 + 0.25, 8), black, V3(X(D.wb[0]), 0.24, s * (W / 4 + 0.12)), [Math.PI / 2, 0, 0]));
  if (c.suspension === 'drag') root.rotation.z = -0.012;
  return root;
};

// ---------- колесо: шина с боковиной, литой диск со спицами, тормоз ----------
Car3D.prototype.wheel = function (c, R, w, rimR, side, wire) {
  const g = new THREE.Group();
  const ri = Math.min(rimR + 0.012, R - 0.035), sidewall = R - ri;
  const pts = [];
  const bul = Math.min(0.012, w * 0.06);
  pts.push(new THREE.Vector2(ri - 0.004, -w / 2 + 0.012));
  pts.push(new THREE.Vector2(ri + 0.01, -w / 2 - 0.002));
  for (let k = 1; k <= 6; k++) { const t = k / 7; pts.push(new THREE.Vector2(ri + sidewall * t, -w / 2 - bul * Math.sin(t * Math.PI))); }
  const rnd = Math.min(sidewall * 0.35, 0.03);
  for (let k = 0; k <= 5; k++) { const a = -Math.PI / 2 + k / 5 * Math.PI / 2; pts.push(new THREE.Vector2(R - rnd + Math.cos(a) * rnd, -w / 2 + rnd + Math.sin(a) * rnd)); }
  for (let k = 0; k <= 5; k++) { const a = k / 5 * Math.PI / 2; pts.push(new THREE.Vector2(R - rnd + Math.cos(a) * rnd, w / 2 - rnd + Math.sin(a) * rnd)); }
  for (let k = 6; k >= 1; k--) { const t = k / 7; pts.push(new THREE.Vector2(ri + sidewall * t, w / 2 + bul * Math.sin(t * Math.PI))); }
  pts.push(new THREE.Vector2(ri + 0.01, w / 2 + 0.002)); pts.push(new THREE.Vector2(ri - 0.004, w / 2 - 0.012));
  const lq = G3.quality === 'low';
  const tg = new THREE.LatheGeometry(pts, lq ? 28 : 40); tg.rotateX(Math.PI / 2);
  const slick = c.tire === 'slick' || c.tire === 'drag';
  g.add(new THREE.Mesh(tg, std(0x151618, 0, slick ? 0.6 : 0.88, slick ? {} : { map: stripeTex('#1b1c1e', '#0e0e0f', 28, true) })));
  const rimM = std(RIM_COL[c.rimStyle] || 0xcfd5dc, c.rimStyle === 'steel' ? 0.5 : 1, c.rimStyle === 'forged' ? 0.1 : c.rimStyle === 'black' ? 0.32 : 0.2);
  const barrel = new THREE.Mesh(new THREE.CylinderGeometry(ri, ri, w * 0.9, 40, 1, true), std(0x6c7178, 0.9, 0.35, { side: THREE.DoubleSide })); barrel.rotation.x = Math.PI / 2; g.add(barrel);
  // внешний обод
  const lipG = new THREE.TorusGeometry(ri - 0.006, 0.011, 8, 48); const lip = new THREE.Mesh(lipG, rimM); lip.position.z = side * w * 0.43; g.add(lip);
  // тёмная внутренность диска (за спицами)
  const back = new THREE.Mesh(new THREE.CircleGeometry(ri, 32), std(0x0a0a0b, 0.3, 0.8)); back.position.z = side * w * 0.05; if (side < 0) back.rotation.y = Math.PI; g.add(back);
  // тормоз
  const lvl = { stock: 0, sport: 1, race: 2, ceramic: 3 }[c.brakes || 'stock'];
  const discR = ri * (0.74 + lvl * 0.05);
  const disc = new THREE.Mesh(new THREE.CylinderGeometry(discR, discR, 0.026, 36), lvl === 3 ? std(0x6b6255, 0.6, 0.45) : std(0x8d939a, 1, 0.38)); disc.rotation.x = Math.PI / 2; disc.position.z = side * w * 0.14; g.add(disc);
  const hat = new THREE.Mesh(new THREE.CylinderGeometry(discR * 0.45, discR * 0.45, 0.03, 20), std(0x3a3d42, 0.8, 0.4)); hat.rotation.x = Math.PI / 2; hat.position.z = side * w * 0.16; g.add(hat);
  if (lvl >= 1) for (let k = 0; k < 16; k++) { const a = k / 16 * Math.PI * 2; const h = new THREE.Mesh(new THREE.CircleGeometry(0.006, 6), std(0x111111, 0, 1)); h.position.set(Math.cos(a) * discR * 0.78, Math.sin(a) * discR * 0.78, disc.position.z + side * 0.0135); h.rotation.y = side > 0 ? 0 : Math.PI; g.add(h); }
  const calM = std(lvl === 0 && c.caliper === 'black' ? 0x3a3c40 : (CALIPER[c.caliper || 'black'] || 0x1a1b1e), 0.3, 0.35);
  const calG = new THREE.CylinderGeometry(discR + 0.012, discR + 0.012, 0.06 + lvl * 0.01, 16, 1, false, 0, 0.7 + lvl * 0.15); calG.rotateX(Math.PI / 2);
  const cal = new THREE.Mesh(calG, calM); cal.position.z = side * w * 0.18; cal.rotation.z = Math.PI * 0.62;
  // спицы
  const face = new THREE.Group(); face.position.z = side * w * 0.36; g.add(face);
  const style = wire ? 'wire' : c.rimStyle;
  let nSp = { steel: 0, alloy: 5, mesh: 10, forged: 10, black: 6, gold: 7, wire: 24 }[style]; if (nSp === undefined) nSp = 5;
  if (nSp === 0) {
    const d = new THREE.Mesh(new THREE.CylinderGeometry(ri * 0.96, ri * 0.9, 0.03, 36), rimM); d.rotation.x = Math.PI / 2; face.add(d);
    for (let k = 0; k < 8; k++) { const h = new THREE.Mesh(new THREE.CircleGeometry(ri * 0.1, 12), std(0x0b0b0c, 0, 0.9)); h.position.set(Math.cos(k / 8 * Math.PI * 2) * ri * 0.66, Math.sin(k / 8 * Math.PI * 2) * ri * 0.66, side * 0.016); if (side < 0) h.rotation.y = Math.PI; face.add(h); }
    const cover = new THREE.Mesh(new THREE.SphereGeometry(ri * 0.38, 20, 8, 0, Math.PI * 2, 0, Math.PI / 2), std(0xb7bcc2, 0.9, 0.3)); cover.rotation.x = side * Math.PI / 2; cover.scale.y = 0.25; face.add(cover);
  } else {
    // спица — трапеция, расширяется к ободу, с фаской
    for (let k = 0; k < nSp; k++) {
      const a = k / nSp * Math.PI * 2;
      const twin = style === 'mesh' || style === 'forged';
      const wIn = style === 'wire' ? 0.004 : twin ? 0.016 : 0.03, wOut = style === 'wire' ? 0.004 : twin ? 0.022 : 0.06;
      const sh = new THREE.Shape(); sh.moveTo(-wIn / 2, ri * 0.2); sh.lineTo(wIn / 2, ri * 0.2); sh.lineTo(wOut / 2, ri * 0.93); sh.lineTo(-wOut / 2, ri * 0.93); sh.lineTo(-wIn / 2, ri * 0.2);
      const sg = new THREE.ExtrudeGeometry(sh, { depth: 0.022, bevelEnabled: style !== 'wire' && !lq, bevelThickness: 0.006, bevelSize: 0.004, bevelSegments: 1 });
      sg.translate(0, 0, -0.011);
      const sp = new THREE.Mesh(sg, rimM);
      sp.rotation.z = a + (twin ? (k % 2 ? 0.07 : -0.07) : 0);
      sp.rotation.x = side * 0.06; // лёгкая «вогнутость»
      face.add(sp);
    }
  }
  const hub = new THREE.Mesh(new THREE.CylinderGeometry(ri * 0.24, ri * 0.27, 0.04, 24), rimM); hub.rotation.x = Math.PI / 2; face.add(hub);
  for (let k = 0; k < 5; k++) { const a = k / 5 * Math.PI * 2; const nut = new THREE.Mesh(new THREE.CylinderGeometry(0.009, 0.009, 0.05, 6), std(0xc8ccd0, 1, 0.25)); nut.rotation.x = Math.PI / 2; nut.position.set(Math.cos(a) * ri * 0.17, Math.sin(a) * ri * 0.17, side * 0.006); face.add(nut); }
  const cap = new THREE.Mesh(new THREE.CylinderGeometry(ri * 0.09, ri * 0.09, 0.052, 20), std(0x1b1d21, 0.6, 0.3)); cap.rotation.x = Math.PI / 2; face.add(cap);
  if (c.tire === 'sport' || c.tire === 'semi' || c.tire === 'drag') {
    const ring = new THREE.Mesh(new THREE.RingGeometry(R * 0.86, R * 0.895, 48, 1, 0, Math.PI * 0.3), new THREE.MeshBasicMaterial({ color: c.tire === 'drag' ? 0xf2a93b : 0xcfd1d4 }));
    ring.position.z = side * (w / 2 + bul + 0.003); if (side < 0) ring.rotation.y = Math.PI; g.add(ring);
    const ring2 = ring.clone(); ring2.rotation.z = Math.PI; g.add(ring2);
  }
  g.userData.spin = [face, g.children[0]];
  this.calipers = (this.calipers || []).concat(cal);
  const outer = new THREE.Group(); outer.add(g); outer.add(cal); outer.userData.inner = g;
  return outer;
};

Car3D.prototype.setCar = function (c, stats, eng) {
  const ek = eng ? [eng.cyl, eng.induction, eng.intercooler, eng.exhDia, eng.type, Math.round(displacement(eng) * 10)].join(',') : '';
  const key = JSON.stringify(c) + (stats ? stats.rWheel : '') + ek;
  if (key === this.key) return;
  this.key = key;
  if (this.root) { this.scene.remove(this.root); disposeTree(this.root); }
  this.root = this.build(c, stats, eng);
  this.scene.add(this.root);
  const P = CAR_PROFILES[c.body] || CAR_PROFILES.hatch;
  this.shadow.scale.set(P.L * 1.2, P.W * 1.45, 1);
  this.orbit.target.set(0, 0.62, 0);
  this.orbit.base = Math.max(7.5, P.L * 2.1); if (!this.framed) { this.orbit.dist = this.orbit.base; this.framed = true; }
};

function buildCarModel(c, stats, eng) {
  const b = Object.create(Car3D.prototype);
  b.wheels = []; b.calipers = [];
  const g = b.build(c, stats, eng);
  return { group: g, wheels: b.wheels, tips: b.tipPos || [] };
}

// Новые модели заменяют старые профили (дрэгстер остаётся прежним)
Object.keys(DESIGNS).forEach(k => { CAR_PROFILES[k] = DESIGNS[k]; });
