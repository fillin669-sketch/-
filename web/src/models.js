import * as THREE from 'three';
import { seeded } from './utils.js';
import { mergeGeometries } from 'three/examples/jsm/utils/BufferGeometryUtils.js';

function subGeometry(geo, start, count) {
  const out = new THREE.BufferGeometry();
  for (const name of ['position', 'normal']) {
    const a = geo.attributes[name];
    out.setAttribute(name, new THREE.BufferAttribute(a.array.slice(start * 3, (start + count) * 3), 3));
  }
  return out;
}

/** Склеивает все меши группы по материалам: меньше вызовов отрисовки. */
export function bake(root) {
  root.updateMatrixWorld(true);
  const inv = new THREE.Matrix4().copy(root.matrixWorld).invert();
  const byMat = new Map();
  const remove = [];
  root.traverse((o) => {
    if (!o.isMesh || o.userData.keep) return;
    remove.push(o);
    let geo = o.geometry.index ? o.geometry.toNonIndexed() : o.geometry.clone();
    if (!geo.attributes.normal) geo.computeVertexNormals();
    geo.applyMatrix4(new THREE.Matrix4().multiplyMatrices(inv, o.matrixWorld));
    const parts = [];
    if (Array.isArray(o.material)) {
      for (const g of geo.groups) parts.push([o.material[g.materialIndex], subGeometry(geo, g.start, g.count)]);
    } else {
      parts.push([o.material, subGeometry(geo, 0, geo.attributes.position.count)]);
    }
    for (const [m, g] of parts) {
      if (!byMat.has(m)) byMat.set(m, []);
      byMat.get(m).push(g);
    }
  });
  for (const o of remove) o.parent.remove(o);
  for (const [m, list] of byMat) {
    const merged = mergeGeometries(list, false);
    root.add(new THREE.Mesh(merged, m));
  }
  return root;
}

const matCache = new Map();
export function mat(color, opts = {}) {
  const key = color + JSON.stringify(opts);
  if (!matCache.has(key)) matCache.set(key, new THREE.MeshLambertMaterial({ color, flatShading: true, ...opts }));
  return matCache.get(key);
}

// ---------------- Корабль ----------------

function hullShape(L, W, k = 1) {
  const s = new THREE.Shape();
  const l = L * k, w = W * k;
  s.moveTo(0, -l / 2);
  s.quadraticCurveTo(w * 0.5, -l * 0.28, w * 0.5, l * 0.08);
  s.lineTo(w * 0.42, l / 2);
  s.lineTo(-w * 0.42, l / 2);
  s.lineTo(-w * 0.5, l * 0.08);
  s.quadraticCurveTo(-w * 0.5, -l * 0.28, 0, -l / 2);
  return s;
}

function extrude(shape, depth, top, materials) {
  const g = new THREE.ExtrudeGeometry(shape, { depth, bevelEnabled: false, curveSegments: 5 });
  g.rotateX(Math.PI / 2); // форма лежит в плоскости XZ, нос смотрит в -Z
  g.translate(0, top, 0);
  return new THREE.Mesh(g, materials);
}

function sailGeometry(w, h, bulge) {
  const g = new THREE.PlaneGeometry(w, h, 6, 3);
  const p = g.attributes.position;
  for (let i = 0; i < p.count; i++) {
    const x = p.getX(i) / w + 0.5;
    const y = p.getY(i) / h + 0.5;
    p.setZ(i, -Math.sin(x * Math.PI) * bulge * (0.6 + 0.4 * Math.sin(y * Math.PI)));
  }
  g.computeVertexNormals();
  return g;
}

/** Корабль из простых форм. Нос смотрит в -Z. */
export function makeShip(look) {
  const g = new THREE.Group();
  const L = look.len, W = look.width, H = W * 0.62;
  const top = H * 0.6;
  const wood = mat(look.hull);
  const deck = mat(0xc89a62);
  const trim = mat(look.trim);
  const dark = mat(0x222222);
  const sailMat = mat(look.sail, { side: THREE.DoubleSide });

  // Корпус: крышка — палуба, борта — тёмное дерево
  g.add(extrude(hullShape(L, W), H, top, [deck, wood]));
  // Цветная полоса по борту
  g.add(extrude(hullShape(L, W, 1.03), 0.45, top - 0.25, [trim, trim]));
  // Фальшборт (бортик)
  const rail = extrude(hullShape(L, W, 1.0), 0.6, top + 0.6, [wood, wood]);
  rail.scale.set(1, 1, 1);
  g.add(rail);
  const deckInner = extrude(hullShape(L * 0.92, W * 0.86), 0.62, top + 0.62, [deck, deck]);
  g.add(deckInner);

  // Кормовая надстройка
  const cabin = new THREE.Mesh(new THREE.BoxGeometry(W * 0.72, H * 0.7, L * 0.2), wood);
  cabin.position.set(0, top + H * 0.35, L * 0.36);
  g.add(cabin);
  const cabinTop = new THREE.Mesh(new THREE.BoxGeometry(W * 0.78, 0.25, L * 0.22), trim);
  cabinTop.position.set(0, top + H * 0.72, L * 0.36);
  g.add(cabinTop);
  for (const sx of [-1, 1]) {
    const win = new THREE.Mesh(new THREE.BoxGeometry(0.05, H * 0.22, L * 0.05), mat(0xffd27a, { emissive: 0x553300 }));
    win.position.set(sx * W * 0.365, top + H * 0.38, L * 0.36);
    g.add(win);
  }

  // Бушприт
  const bow = new THREE.Mesh(new THREE.CylinderGeometry(0.12, 0.2, L * 0.3, 5), wood);
  bow.rotation.x = Math.PI / 2 - 0.25;
  bow.position.set(0, top + 0.9, -L * 0.55);
  g.add(bow);

  // Мачты и паруса
  const n = look.masts;
  const mastH = L * 0.62 + 3;
  for (let i = 0; i < n; i++) {
    const z = n === 1 ? -L * 0.05 : -L * 0.28 + (L * 0.5 * i) / (n - 1);
    const h = mastH * (i === 0 && n > 2 ? 0.88 : 1);
    const mast = new THREE.Mesh(new THREE.CylinderGeometry(0.16, 0.26, h, 6), wood);
    mast.position.set(0, top + h / 2, z);
    g.add(mast);
    const sw = W * (n === 1 ? 1.9 : 1.6);
    for (let k = 0; k < 2; k++) {
      const sh = h * (k === 0 ? 0.36 : 0.26);
      const sy = top + h * (k === 0 ? 0.36 : 0.72);
      const sail = new THREE.Mesh(sailGeometry(sw * (k === 0 ? 1 : 0.8), sh, W * 0.28), sailMat);
      sail.position.set(0, sy, z - 0.25);
      g.add(sail);
      const yard = new THREE.Mesh(new THREE.CylinderGeometry(0.1, 0.1, sw * (k === 0 ? 1.05 : 0.85), 5), wood);
      yard.rotation.z = Math.PI / 2;
      yard.position.set(0, sy + sh / 2, z - 0.1);
      g.add(yard);
    }
    // Марс (площадка)
    const nest = new THREE.Mesh(new THREE.CylinderGeometry(0.7, 0.55, 0.5, 6), wood);
    nest.position.set(0, top + h * 0.86, z);
    g.add(nest);
    if (i === n - 1 || n === 1) {
      const flag = new THREE.Mesh(new THREE.PlaneGeometry(2.2, 1.3), mat(look.flag, { side: THREE.DoubleSide }));
      flag.position.set(0, top + h + 0.5, z + 1.1);
      flag.rotation.y = Math.PI / 2;
      flag.userData.flag = true;
      g.add(flag);
    }
  }
  // Косой парус у носа
  const jib = new THREE.Mesh(
    new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(0, top + 1, -L * 0.62),
      new THREE.Vector3(0, top + mastH * 0.75, -L * 0.2),
      new THREE.Vector3(0, top + 1.2, -L * 0.26),
    ]),
    sailMat
  );
  jib.geometry.computeVertexNormals();
  g.add(jib);

  // Пушки по бортам
  const guns = Math.min(6, Math.max(2, Math.round(L / 4)));
  for (const sx of [-1, 1]) {
    for (let i = 0; i < guns; i++) {
      const c = new THREE.Mesh(new THREE.CylinderGeometry(0.28, 0.36, 1.6, 6), dark);
      c.rotation.z = Math.PI / 2;
      c.position.set(sx * (W * 0.5 + 0.2), top + 0.15, -L * 0.25 + (L * 0.5 * i) / Math.max(1, guns - 1));
      g.add(c);
    }
  }
  g.userData.len = L;
  g.userData.width = W;
  return bake(g);
}

// ---------------- Острова ----------------

function jitterCylinder(rTop, rBot, h, seg, rnd, amount) {
  const g = new THREE.CylinderGeometry(rTop, rBot, h, seg, 1);
  const p = g.attributes.position;
  const offsets = [];
  for (let i = 0; i <= seg; i++) offsets.push(1 + (rnd() - 0.5) * amount);
  offsets[seg] = offsets[0];
  for (let i = 0; i < p.count; i++) {
    const x = p.getX(i), z = p.getZ(i);
    if (Math.abs(x) < 1e-4 && Math.abs(z) < 1e-4) continue;
    const a = Math.atan2(z, x);
    const idx = Math.round(((a + Math.PI) / (Math.PI * 2)) * seg) % seg;
    p.setX(i, x * offsets[idx]);
    p.setZ(i, z * offsets[idx]);
  }
  g.computeVertexNormals();
  return g;
}

function tree(rnd) {
  const t = new THREE.Group();
  const trunk = new THREE.Mesh(new THREE.CylinderGeometry(0.35, 0.5, 3, 5), mat(0x6b4423));
  trunk.position.y = 1.5;
  t.add(trunk);
  const green = [0x2f7d32, 0x388e3c, 0x2e6b30][Math.floor(rnd() * 3)];
  for (let k = 0; k < 2; k++) {
    const cone = new THREE.Mesh(new THREE.ConeGeometry(2.4 - k * 0.7, 3.6, 6), mat(green));
    cone.position.y = 4 + k * 1.8;
    t.add(cone);
  }
  t.scale.setScalar(0.8 + rnd() * 0.6);
  return t;
}

function palm(rnd) {
  const t = new THREE.Group();
  const trunk = new THREE.Mesh(new THREE.CylinderGeometry(0.25, 0.4, 6, 5), mat(0x8d6e4a));
  trunk.position.y = 3;
  trunk.rotation.z = (rnd() - 0.5) * 0.4;
  t.add(trunk);
  for (let i = 0; i < 5; i++) {
    const leaf = new THREE.Mesh(new THREE.ConeGeometry(0.7, 4, 4), mat(0x43a047));
    leaf.position.y = 6;
    leaf.rotation.z = Math.PI / 2.4;
    const holder = new THREE.Group();
    holder.add(leaf);
    holder.rotation.y = (i / 5) * Math.PI * 2 + rnd();
    leaf.position.x = 1.6;
    t.add(holder);
  }
  t.scale.setScalar(0.8 + rnd() * 0.4);
  return t;
}

function rock(rnd, s) {
  const r = new THREE.Mesh(new THREE.DodecahedronGeometry(s, 0), mat([0x6d6d6d, 0x5a5a5a, 0x7b7b7b][Math.floor(rnd() * 3)]));
  r.scale.set(1, 0.7 + rnd() * 0.5, 1);
  r.rotation.set(rnd() * 3, rnd() * 3, rnd() * 3);
  return r;
}

function house(rnd) {
  const h = new THREE.Group();
  const w = 6 + rnd() * 4, d = 5 + rnd() * 3, hh = 4 + rnd() * 2;
  const walls = new THREE.Mesh(new THREE.BoxGeometry(w, hh, d), mat([0x8d5b3a, 0x9c6b45, 0xd7c4a3][Math.floor(rnd() * 3)]));
  walls.position.y = hh / 2;
  h.add(walls);
  const roof = new THREE.Mesh(new THREE.ConeGeometry(Math.max(w, d) * 0.78, 4, 4), mat([0x9b2226, 0xae2012, 0x7f1d1d][Math.floor(rnd() * 3)]));
  roof.rotation.y = Math.PI / 4;
  roof.scale.set(w / Math.max(w, d), 1, d / Math.max(w, d));
  roof.position.y = hh + 2;
  h.add(roof);
  const door = new THREE.Mesh(new THREE.BoxGeometry(1.4, 2.4, 0.2), mat(0x3e2412));
  door.position.set(0, 1.2, d / 2 + 0.05);
  h.add(door);
  return h;
}

function dock(len) {
  const d = new THREE.Group();
  const plank = new THREE.Mesh(new THREE.BoxGeometry(7, 0.6, len), mat(0xa57744));
  plank.position.set(0, 2.2, len / 2);
  d.add(plank);
  for (let i = 0; i <= 3; i++) {
    for (const sx of [-1, 1]) {
      const post = new THREE.Mesh(new THREE.CylinderGeometry(0.35, 0.35, 5, 5), mat(0x5c3b1e));
      post.position.set(sx * 3.4, 0.8, (len * i) / 3);
      d.add(post);
    }
  }
  const cross = new THREE.Mesh(new THREE.BoxGeometry(14, 0.6, 4), mat(0xa57744));
  cross.position.set(0, 2.2, len);
  d.add(cross);
  return d;
}

export function makeIsland(x, z, r, seed, port) {
  const rnd = seeded(seed);
  const g = new THREE.Group();
  g.position.set(x, 0, z);
  const seg = 14;
  const sand = new THREE.Mesh(jitterCylinder(r * 0.98, r * 1.08, 5, seg, rnd, 0.22), mat(0xe6d3a3));
  sand.position.y = -1.2;
  g.add(sand);
  const grass = new THREE.Mesh(jitterCylinder(r * 0.82, r * 0.9, 3, seg, rnd, 0.2), mat(0x67a83d));
  grass.position.y = 2.2;
  g.add(grass);
  if (!port || rnd() < 0.5) {
    const hill = new THREE.Mesh(jitterCylinder(r * 0.35, r * 0.55, r * 0.25, 9, rnd, 0.3), mat(0x5b9634));
    hill.position.set((rnd() - 0.5) * r * 0.3, 3.7 + r * 0.12, port ? -r * 0.4 : (rnd() - 0.5) * r * 0.3);
    g.add(hill);
  }

  const occupied = [];
  const free = (px, pz, rad) => occupied.every((o) => Math.hypot(o[0] - px, o[1] - pz) > o[2] + rad);

  if (port) {
    // Причал на южном берегу (к камере)
    const dk = dock(r * 0.45);
    dk.position.set(0, 0, r * 0.72);
    g.add(dk);
    occupied.push([0, r * 0.6, 8]);
    const houses = 3 + Math.floor(rnd() * 3);
    for (let i = 0; i < houses * 4 && occupied.length < houses + 1; i++) {
      const a = rnd() * Math.PI * 2;
      const d = r * (0.25 + rnd() * 0.42);
      const px = Math.cos(a) * d, pz = Math.sin(a) * d;
      if (!free(px, pz, 6)) continue;
      const h = house(rnd);
      h.position.set(px, 3.7, pz);
      h.rotation.y = Math.atan2(px, pz) + (rnd() - 0.5) * 0.6;
      g.add(h);
      occupied.push([px, pz, 6]);
    }
    if (port.lighthouse) {
      const lh = new THREE.Group();
      const tower = new THREE.Mesh(new THREE.CylinderGeometry(2, 2.8, 16, 8), mat(0xf1f1f1));
      tower.position.y = 8;
      lh.add(tower);
      for (let k = 0; k < 3; k++) {
        const band = new THREE.Mesh(new THREE.CylinderGeometry(2.2 - k * 0.2, 2.4 - k * 0.2, 1.5, 8), mat(0xc62828));
        band.position.y = 3 + k * 5;
        lh.add(band);
      }
      const light = new THREE.Mesh(new THREE.CylinderGeometry(1.6, 1.6, 2, 8), mat(0xfff3b0, { emissive: 0xffcc33 }));
      light.position.y = 17;
      lh.add(light);
      const cap = new THREE.Mesh(new THREE.ConeGeometry(2.2, 2.4, 8), mat(0xc62828));
      cap.position.y = 19.2;
      lh.add(cap);
      lh.position.set(-r * 0.5, 3.7, r * 0.35);
      g.add(lh);
      occupied.push([-r * 0.5, r * 0.35, 4]);
    }
  }

  const trees = Math.floor(r / 4) + 3;
  for (let i = 0; i < trees * 3 && trees > 0; i++) {
    const a = rnd() * Math.PI * 2;
    const d = r * (0.15 + rnd() * 0.62);
    const px = Math.cos(a) * d, pz = Math.sin(a) * d;
    if (!free(px, pz, 2.5)) continue;
    const t = rnd() < 0.4 ? palm(rnd) : tree(rnd);
    t.position.set(px, 3.7, pz);
    g.add(t);
    occupied.push([px, pz, 2.5]);
  }
  const rocks = 4 + Math.floor(rnd() * 5);
  for (let i = 0; i < rocks; i++) {
    const a = rnd() * Math.PI * 2;
    if (port && Math.abs(Math.sin(a) - 1) < 0.25) continue; // не загораживаем причал
    const d = r * (0.95 + rnd() * 0.15);
    const rk = rock(rnd, 1.5 + rnd() * 3);
    rk.position.set(Math.cos(a) * d, 0.5, Math.sin(a) * d);
    g.add(rk);
  }

  // Пена у берега
  const foamGeo = new THREE.RingGeometry(r * 1.02, r * 1.2, 28);
  foamGeo.rotateX(-Math.PI / 2);
  const foam = new THREE.Mesh(foamGeo, new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.35, depthWrite: false }));
  foam.position.y = 0.6;
  bake(g);
  foam.userData.keep = true;
  g.add(foam);
  g.userData.foam = foam;
  return g;
}

// ---------------- Вода ----------------

export function makeWater() {
  const geo = new THREE.PlaneGeometry(1200, 1200, 110, 110);
  geo.rotateX(-Math.PI / 2);
  const material = new THREE.ShaderMaterial({
    uniforms: {
      uTime: { value: 0 },
      uDeep: { value: new THREE.Color(0x1f74b8) },
      uShallow: { value: new THREE.Color(0x3194d6) },
      uFog: { value: new THREE.Color(0x8fc5e8) },
      uLight: { value: new THREE.Vector3(0.4, 1, 0.3).normalize() },
      uCam: { value: new THREE.Vector3() },
      uFogNear: { value: 300 },
      uFogFar: { value: 560 },
    },
    vertexShader: /* glsl */ `
      uniform float uTime;
      varying vec3 vWorld;
      float wave(vec2 p) {
        return sin(p.x * 0.08 + uTime * 1.1) * 0.35 + sin(p.y * 0.11 + uTime * 0.9) * 0.3
             + sin((p.x + p.y) * 0.05 + uTime * 0.7) * 0.4;
      }
      void main() {
        vec4 wp = modelMatrix * vec4(position, 1.0);
        wp.y += wave(wp.xz);
        vWorld = wp.xyz;
        gl_Position = projectionMatrix * viewMatrix * wp;
      }`,
    fragmentShader: /* glsl */ `
      uniform float uTime;
      uniform vec3 uDeep, uShallow, uFog, uLight, uCam;
      uniform float uFogNear, uFogFar;
      varying vec3 vWorld;
      float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
      float noise(vec2 p) {
        vec2 i = floor(p), f = fract(p);
        vec2 u = f * f * (3.0 - 2.0 * f);
        return mix(mix(hash(i), hash(i + vec2(1, 0)), u.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), u.x), u.y);
      }
      void main() {
        vec3 n = normalize(cross(dFdx(vWorld), dFdy(vWorld)));
        if (n.y < 0.0) n = -n;
        float l = clamp(dot(n, uLight), 0.0, 1.0);
        float patches = noise(vWorld.xz * 0.025 + vec2(uTime * 0.02, 0.0)) * 0.6 + noise(vWorld.xz * 0.09) * 0.4;
        vec3 col = mix(uDeep, uShallow, smoothstep(0.35, 0.8, patches));
        col *= 0.86 + 0.24 * l;
        vec3 v = normalize(uCam - vWorld);
        float spec = pow(max(dot(reflect(-uLight, n), v), 0.0), 48.0);
        col += vec3(spec * 0.25);
        float sparkle = smoothstep(0.95, 0.995, noise(vWorld.xz * 0.35 + vec2(uTime * 0.3, -uTime * 0.2))) * 0.22;
        col += sparkle;
        float d = distance(uCam, vWorld);
        col = mix(col, uFog, smoothstep(uFogNear, uFogFar, d));
        gl_FragColor = vec4(col, 1.0);
      }`,
  });
  const mesh = new THREE.Mesh(geo, material);
  mesh.frustumCulled = false;
  return { mesh, material };
}

// ---------------- Добыча ----------------

export function makeChest() {
  const g = new THREE.Group();
  const box = new THREE.Mesh(new THREE.BoxGeometry(2.4, 1.4, 1.6), mat(0x8b5a2b));
  g.add(box);
  const lid = new THREE.Mesh(new THREE.CylinderGeometry(0.8, 0.8, 2.4, 6, 1, false, 0, Math.PI), mat(0x9c6b30));
  lid.rotation.z = Math.PI / 2;
  lid.position.y = 0.7;
  g.add(lid);
  const band = new THREE.Mesh(new THREE.BoxGeometry(2.5, 0.3, 1.7), mat(0xffc93c, { emissive: 0x664400 }));
  band.position.y = 0.2;
  g.add(band);
  const coins = new THREE.Mesh(new THREE.CylinderGeometry(0.7, 0.7, 0.25, 8), mat(0xffd54f, { emissive: 0x806000 }));
  coins.position.y = 1.4;
  g.add(coins);
  return g;
}

export function makeGem() {
  return new THREE.Mesh(new THREE.OctahedronGeometry(1.3, 0), mat(0xe040fb, { emissive: 0x7b1fa2 }));
}
