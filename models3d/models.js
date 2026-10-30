// Jetstrike 3D models, built procedurally with Three.js.
// Guns are traced rect-by-rect from the 2D sprites in app/src/main/res/drawable-nodpi (pixel coords, y down).
// Characters follow the Hero body numbers in Game.kt (tw, th, hip, hr, limb, pack, scale, squareHead) and the
// per-fighter gear from GameView.kt (bodyGear / headGear / accessory). All models face +X.
import * as THREE from 'three';
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js';

const S = 0.01; // gun sprite px -> scene units
const U = 0.1;  // hero sprite units -> scene units

const cache = new Map();
export function mat(col, o = {}) {
  const key = col + '|' + (o.rough ?? '') + '|' + (o.metal ?? '') + '|' + (o.em ?? '') + '|' + (o.ei ?? '');
  if (!o.uniq && cache.has(key)) return cache.get(key);
  const m = new THREE.MeshStandardMaterial({
    color: col, roughness: o.rough ?? 0.55, metalness: o.metal ?? 0.12,
    emissive: o.em ?? 0x000000, emissiveIntensity: o.ei ?? 1,
    transparent: o.alpha !== undefined, opacity: o.alpha ?? 1, side: o.side ?? THREE.FrontSide,
  });
  if (!o.uniq) cache.set(key, m);
  return m;
}

const K = {
  grey: 0x9097a0, greyL: 0xb4bac1, dark: 0x25282c, black: 0x15171a, charcoal: 0x3a3c42,
  olive: 0x6c7152, oliveL: 0x80875f, oliveD: 0x4a5139, wood: 0x8a4f26, woodL: 0xa8672f,
  yellow: 0xe9e24c, glass: 0x5fd0e8, red: 0xe0443a, cream: 0xf2e3c4,
};

// ---------------------------------------------------------------- guns
function tools(H) {
  const g = new THREE.Group();
  const add = (geo, col, x, y, o) => {
    const m = new THREE.Mesh(geo, mat(col, o));
    m.position.set(x * S, (H - y) * S, o.dz || 0);
    if (o.rot) m.rotation.z = -o.rot * Math.PI / 180;
    g.add(m);
    return m;
  };
  // rectangle in image px, z = thickness in scene units
  const R = (x0, y0, x1, y1, z, col, o = {}) => {
    const w = (x1 - x0) * S, h = (y1 - y0) * S;
    return add(new RoundedBoxGeometry(w, h, z, 2, Math.min(w, h, z) * (o.r ?? 0.28)), col, (x0 + x1) / 2, (y0 + y1) / 2, o);
  };
  // cylinder along x from x0 to x1 at y centre yc, back radius r, front radius o.rf
  const T = (x0, x1, yc, r, col, o = {}) => {
    const geo = new THREE.CylinderGeometry((o.rf ?? r) * S, r * S, (x1 - x0) * S, o.seg || 20);
    geo.rotateZ(-Math.PI / 2);
    const m = add(geo, col, (x0 + x1) / 2, yc, o);
    if (o.sz) m.scale.z = o.sz;
    return m;
  };
  const Sp = (cx, cy, r, col, o = {}) => {
    const m = add(new THREE.SphereGeometry(r * S, 24, 16), col, cx, cy, o);
    if (o.sc) m.scale.set(...o.sc);
    return m;
  };
  const Tor = (cx, cy, r, tube, col, o = {}) => add(new THREE.TorusGeometry(r * S, tube * S, 8, 24), col, cx, cy, o);
  return { g, R, T, Sp, Tor };
}

function fin(g, H, grip, muzzle, fore, kind) {
  const w = new THREE.Group();
  g.position.set(-grip[0] * S, -(H - grip[1]) * S, 0);
  w.add(g);
  w.userData = {
    kind,
    muzzle: new THREE.Vector3((muzzle[0] - grip[0]) * S, (grip[1] - muzzle[1]) * S, 0),
    fore: new THREE.Vector3((fore[0] - grip[0]) * S, (grip[1] - fore[1]) * S, 0),
  };
  return w;
}

const GUN_BUILD = {
  pistol() { // revolver, 264x124
    const H = 124, { g, R, T, Tor } = tools(H);
    T(130, 258, 32, 10, K.grey, { metal: 0.5, rough: 0.35 });
    R(130, 17, 255, 26, 0.12, K.greyL, { metal: 0.5 });
    R(238, 10, 251, 24, 0.1, K.dark);
    R(55, 24, 150, 80, 0.3, K.grey, { r: 0.35, metal: 0.5, rough: 0.35 });
    T(82, 132, 52, 21, K.greyL, { metal: 0.55, rough: 0.3 });
    for (let i = 0; i < 5; i++) R(90 + i * 9, 36, 94 + i * 9, 68, 0.46, K.charcoal, { r: 0.1 });
    R(50, 18, 66, 36, 0.08, K.dark, { rot: -15 });
    R(30, 66, 60, 118, 0.26, K.black, { rot: 25, r: 0.4 });
    for (let i = 0; i < 4; i++) R(14 + i * 3, 96 + i * 5, 32 + i * 3, 99 + i * 5, 0.28, K.charcoal, { rot: 25, r: 0.1 });
    Tor(88, 88, 13, 2.2, K.grey, { metal: 0.5 });
    R(86, 72, 91, 88, 0.05, K.dark);
    return fin(g, H, [48, 80], [258, 32], [60, 96], 'bullet');
  },
  rifle() { // XM8-style, 284x133
    const H = 133, { g, R, T, Tor } = tools(H);
    R(0, 38, 54, 90, 0.2, K.olive, { r: 0.3 });
    R(0, 36, 8, 94, 0.24, K.black);
    R(45, 28, 190, 78, 0.28, K.olive, { r: 0.35 });
    R(180, 42, 256, 78, 0.3, K.oliveL, { r: 0.3 });
    R(165, 64, 256, 82, 0.28, K.charcoal, { rot: 1.5 });
    for (let i = 0; i < 8; i++) R(172 + i * 10, 66, 176 + i * 10, 80, 0.3, K.dark, { r: 0.1 });
    R(92, 6, 152, 28, 0.26, K.dark);
    R(98, 10, 106, 24, 0.27, K.red, { em: 0x661111 });
    R(70, 18, 190, 28, 0.2, K.dark);
    T(250, 284, 51, 4.5, K.dark, { metal: 0.5 });
    T(268, 286, 51, 7, K.black, { metal: 0.4 });
    R(112, 76, 150, 130, 0.2, K.dark, { rot: 9 });
    R(84, 76, 114, 114, 0.22, K.dark, { rot: 15, r: 0.4 });
    Tor(124, 80, 12, 2, K.dark);
    return fin(g, H, [100, 88], [286, 51], [210, 60], 'bullet');
  },
  shotgun() { // pump shotgun, 404x149
    const H = 149, { g, R, T, Tor } = tools(H);
    R(55, 26, 158, 70, 0.26, 0x3b3d42, { r: 0.3 });
    R(95, 38, 128, 46, 0.27, K.grey, { metal: 0.4 });
    R(5, 28, 66, 70, 0.26, K.dark, { r: 0.4 });
    T(156, 304, 48, 25, 0x232428, { sz: 0.78, rough: 0.5 });
    for (let i = 0; i < 9; i++) T(168 + i * 15, 173 + i * 15, 48, 26, K.black, { sz: 0.8 });
    T(300, 404, 38, 9, 0x3a3c40, { metal: 0.5 });
    T(300, 392, 60, 7, 0x303236, { metal: 0.5 });
    T(392, 404, 38, 11, K.black);
    R(372, 10, 388, 30, 0.1, K.dark);
    R(28, 12, 60, 30, 0.12, K.dark);
    R(18, 72, 50, 146, 0.28, K.black, { rot: 22, r: 0.4 });
    Tor(100, 92, 17, 2.4, K.charcoal);
    R(96, 72, 101, 90, 0.05, K.dark);
    return fin(g, H, [40, 90], [404, 38], [235, 48], 'pellet');
  },
  sniper() { // scoped sniper with bipod, 284x100
    const H = 100, { g, R, T } = tools(H);
    R(0, 36, 64, 80, 0.2, 0x6a6f4e, { r: 0.3 });
    R(0, 30, 50, 40, 0.2, K.dark);
    R(0, 36, 7, 80, 0.24, K.black);
    R(58, 34, 140, 72, 0.26, 0x6f7378, { r: 0.3, metal: 0.4 });
    R(100, 40, 176, 68, 0.28, K.olive, { r: 0.35 });
    T(72, 148, 16, 8, 0x26282c, { metal: 0.4 });
    T(66, 82, 16, 11, K.dark);
    T(136, 152, 16, 11, K.dark);
    T(150, 153, 16, 9.5, K.glass, { em: 0x1b6f80, rough: 0.1 });
    R(88, 22, 96, 38, 0.12, K.dark);
    R(125, 22, 133, 38, 0.12, K.dark);
    T(140, 250, 46, 3.2, 0x303236, { metal: 0.5 });
    T(236, 284, 41, 9, 0x8a8f94, { rf: 9, metal: 0.45 });
    R(102, 66, 128, 94, 0.2, K.dark);
    R(60, 66, 86, 96, 0.22, K.black, { rot: 20, r: 0.4 });
    for (const dz of [-0.2, 0.2]) {
      R(140, 54, 146, 98, 0.05, K.dark, { rot: -24, dz });
      R(158, 54, 164, 98, 0.05, K.dark, { rot: 18, dz });
    }
    return fin(g, H, [72, 66], [284, 41], [140, 56], 'bullet');
  },
  smg() { // AK-style, 284x122
    const H = 122, { g, R, T, Tor } = tools(H);
    R(0, 34, 80, 92, 0.22, K.wood, { rot: -8, r: 0.3 });
    R(75, 14, 166, 60, 0.26, 0x1c1d20, { r: 0.3 });
    R(82, 10, 160, 22, 0.25, 0x2a2b2f);
    R(155, 38, 238, 62, 0.3, K.wood, { r: 0.3 });
    R(150, 24, 240, 36, 0.26, K.woodL);
    T(160, 246, 22, 3.5, K.dark);
    T(235, 282, 45, 3.5, K.dark, { metal: 0.5 });
    R(262, 14, 272, 42, 0.08, K.dark);
    T(272, 286, 45, 5, K.black);
    R(122, 54, 158, 86, 0.18, K.dark, { rot: -10 });
    R(131, 80, 167, 108, 0.18, K.dark, { rot: -18 });
    R(142, 102, 178, 124, 0.18, K.dark, { rot: -27 });
    R(62, 72, 94, 116, 0.22, K.woodL, { rot: 15, r: 0.4 });
    Tor(112, 76, 12, 2, K.dark);
    return fin(g, H, [82, 80], [286, 45], [200, 52], 'bullet');
  },
  rocket() { // launcher, 284x115
    const H = 115, { g, R, T } = tools(H);
    T(20, 250, 56, 18.5, 0x7a6b3f, { rough: 0.7 });
    T(0, 32, 55, 27, K.black, { rf: 24 });
    T(232, 284, 56, 22, K.black, { rf: 22 });
    T(88, 99, 56, 19.5, 0xf2d21a);
    T(158, 214, 17, 9, K.black, { metal: 0.4 });
    T(212, 216, 17, 7.5, K.glass, { em: 0x1b6f80, rough: 0.1 });
    R(170, 22, 200, 40, 0.18, K.black);
    R(140, 60, 218, 80, 0.28, K.black, { r: 0.3 });
    R(172, 76, 214, 112, 0.26, K.black, { r: 0.35 });
    R(128, 64, 135, 112, 0.05, K.black, { rot: -12 });
    return fin(g, H, [192, 92], [284, 56], [100, 56], 'blast');
  },
  flame() { // flamethrower, 284x108
    const H = 108, { g, R, T, Sp, Tor } = tools(H);
    T(55, 160, 36, 24, K.yellow, { em: 0x4a4800, rough: 0.3 });
    T(61, 70, 36, 25.5, K.dark);
    T(141, 150, 36, 25.5, K.dark);
    R(38, 50, 172, 74, 0.24, 0x2e2f33, { r: 0.35 });
    R(20, 60, 62, 108, 0.24, K.charcoal, { rot: 20, r: 0.4 });
    T(160, 228, 52, 8, K.dark);
    R(160, 24, 178, 100, 0.2, K.charcoal);
    Sp(252, 30, 26, 0x3a3c42, { sc: [1, 1, 0.95] });
    T(266, 286, 30, 10, 0x8a8d92, { rf: 14, metal: 0.4 });
    Tor(22, 40, 14, 2.2, K.dark);
    Tor(262, 40, 18, 2.2, K.dark);
    return fin(g, H, [40, 72], [288, 30], [172, 62], 'flame');
  },
  laser() { // sci-fi laser rifle, 284x109
    const H = 109, { g, R, T } = tools(H);
    R(40, 34, 250, 84, 0.34, 0x5f6b45, { r: 0.4 });
    R(105, 18, 282, 50, 0.3, 0x73805a, { r: 0.3 });
    for (let i = 0; i < 9; i++) R(122 + i * 17, 29, 133 + i * 17, 39, 0.32, 0x1a1d16, { r: 0.1 });
    R(55, 5, 115, 34, 0.28, K.olive, { rot: -10 });
    R(78, 0, 102, 20, 0.2, K.oliveD, { rot: -28 });
    R(120, 56, 262, 90, 0.3, K.oliveD, { r: 0.35 });
    R(62, 68, 98, 108, 0.24, K.black, { rot: 12, r: 0.4 });
    T(262, 286, 34, 8, 0x00e5ff, { em: 0x00b8d4, ei: 1.2, rough: 0.2 });
    return fin(g, H, [80, 74], [286, 34], [180, 60], 'laser');
  },
  grenade() { // 73x99
    const H = 99, { g, R, T, Sp, Tor } = tools(H);
    Sp(36, 62, 30, 0x5a6a3c, { sc: [1, 1.22, 1], rough: 0.6 });
    for (const y of [48, 62, 76]) {
      const t = Tor(36, y, y === 62 ? 30 : 26, 1.6, 0x2c3620);
      t.rotation.x = Math.PI / 2;
    }
    const v = Tor(36, 62, 31, 1.6, 0x2c3620); v.rotation.y = Math.PI / 2; v.scale.y = 1.22;
    T(30, 44, 22, 8, K.grey, { rf: 8, rot: 90 });
    R(28, 3, 46, 12, 0.2, K.grey, { metal: 0.5 });
    R(40, 2, 70, 10, 0.1, K.greyL, { rot: 14, metal: 0.5 });
    Tor(68, 22, 6, 1.4, K.greyL);
    return fin(g, H, [36, 52], [36, 52], [36, 52], 'none');
  },
};

export const GUN_ORDER = ['pistol', 'smg', 'rifle', 'shotgun', 'sniper', 'rocket', 'flame', 'laser', 'grenade'];
export function buildGun(key) { return GUN_BUILD[key](); }

// Stats from the Weapon table in Game.kt (rate = seconds between shots).
export const WEAPON_STATS = {
  pistol:  { n: 'Revolver',        dmg: '16',      rate: 0.26,  mag: 12,  rel: 1.0, d: 'Reliable sidearm. Chrome frame, six-round drum.' },
  smg:     { n: 'AK SMG',          dmg: '6',       rate: 0.06,  mag: 40,  rel: 1.4, d: 'Wood furniture, curved mag. Wide spray.' },
  rifle:   { n: 'Assault Rifle',   dmg: '9',       rate: 0.095, mag: 30,  rel: 1.5, d: 'Olive polymer body with red-dot carry sight.' },
  shotgun: { n: 'Shotgun',         dmg: '14 x 7',  rate: 0.75,  mag: 6,   rel: 1.7, d: 'Pump forend, tube mag, seven pellets per shot.' },
  sniper:  { n: 'Sniper',          dmg: '62',      rate: 1.05,  mag: 5,   rel: 2.0, d: 'Scope, bipod and suppressor. Long range.' },
  rocket:  { n: 'Rocket Launcher', dmg: 'Blast',   rate: 1.2,   mag: 3,   rel: 2.4, d: 'Shoulder tube with yellow band and optic.' },
  flame:   { n: 'Flamethrower',    dmg: '5 / tick', rate: 0.06, mag: 100, rel: 2.2, d: 'Fuel tank on a pistol grip, bulb nozzle.' },
  laser:   { n: 'Laser Rifle',     dmg: '24',      rate: 0.42,  mag: 12,  rel: 1.8, d: 'Vented emitter, finned sci-fi body.' },
  grenade: { n: 'Grenade',         dmg: 'Area',    rate: 0,     mag: 0,   rel: 0,   d: 'Thrown by players. Pineapple body and pin ring.' },
};

// ---------------------------------------------------------------- heroes
const hero = (o) => Object.assign({ tw: 9.5, th: 21, hip: 17, hr: 12.5, limb: 1, rad: 6, pack: 1, scale: 1, sq: false }, o);
export const SKINS = [
  { name: 'Nova', c: '#19e3c8', d: '#0b8f86', l: '#a6fff1', acc: 0, role: 'SCOUT', l1: 'Efficient jetpack, quick reload', l2: 'Balanced all-rounder', hp: 100, dmg: 1, spd: 1, armor: 0, unlock: 'Starter', h: hero({}) },
  { name: 'Blaze', c: '#ff8a3d', d: '#b34a12', l: '#ffd3ae', acc: 1, role: 'BRUISER', l1: '+30% damage, slower reload', l2: 'Broad and tough to miss', hp: 90, dmg: 1.3, spd: 1, armor: 0, unlock: 'Get 20 kills', h: hero({ tw: 12.5, th: 22, hip: 14, hr: 11, limb: 1.25, rad: 4, sq: true }) },
  { name: 'Frost', c: '#7cc8ff', d: '#2a6fae', l: '#dff2ff', acc: 2, role: 'TANK', l1: '150 HP, takes 25% less damage', l2: 'Slow and heavy, big target', hp: 150, dmg: 1, spd: 0.88, armor: 25, unlock: 'Win a match', h: hero({ scale: 1.1, tw: 13.5, th: 24, hip: 15, hr: 12, limb: 1.4, rad: 3, sq: true }) },
  { name: 'Viper', c: '#7ee05c', d: '#2f8a22', l: '#d6ffc2', acc: 3, role: 'ASSASSIN', l1: 'Fast, heals 25% of damage dealt', l2: 'Fragile, hard to hit', hp: 80, dmg: 1.1, spd: 1.18, armor: 0, unlock: 'Get 75 kills', h: hero({ tw: 6.8, th: 24, hip: 23, hr: 10, limb: 0.75, rad: 5 }) },
  { name: 'Sol', c: '#ffd23f', d: '#a67c00', l: '#fff2ae', acc: 4, role: 'MEDIC', l1: 'Regenerates 5 HP/s out of combat', l2: 'Slightly weaker shots', hp: 110, dmg: 0.9, spd: 1, armor: 0, unlock: 'Play 10 matches', h: hero({ tw: 11.5, th: 19, hip: 14, hr: 14, limb: 1.1, rad: 9 }) },
  { name: 'Bloom', c: '#ff6b9a', d: '#a3305a', l: '#ffc7da', acc: 5, role: 'PILOT', l1: 'Jetpack uses half the fuel, refills fast', l2: 'Tiny target, fast mover', hp: 85, dmg: 1, spd: 1.08, armor: 0, unlock: 'Clear 2 story waves', h: hero({ scale: 0.85, tw: 9, th: 17, hip: 13, hr: 13.5, limb: 0.9, rad: 7, pack: 1.5 }) },
  { name: 'Onyx', c: '#6f8296', d: '#2f3d4b', l: '#c4d3e0', acc: 6, role: 'MARKSMAN', l1: '+55% damage with Sniper and Laser', l2: 'Light armor, lean frame', hp: 90, dmg: 1.1, spd: 1, armor: 10, unlock: 'Win 5 matches', h: hero({ tw: 8.5, th: 25, hip: 20, hr: 11, limb: 0.85, rad: 1.5, sq: true }) },
  { name: 'Rust', c: '#c97b4a', d: '#6a3a1a', l: '#f0c9a6', acc: 7, role: 'BERSERKER', l1: 'Hits up to 80% harder as health drops', l2: 'Sturdy, a little quicker', hp: 105, dmg: 1, spd: 1.05, armor: 0, unlock: 'Defeat the Overlord', h: hero({ tw: 11, th: 20, hip: 18, hr: 13, limb: 1.2, rad: 5 }) },
  { name: 'Overlord', c: '#8a3040', d: '#43151f', l: '#eaa3ad', acc: 8, boss: true, role: 'BOSS', l1: 'Rocket volleys, flamethrower sweep', l2: 'Slam, charge, calls reinforcements', hp: 0, dmg: 0, spd: 0, armor: 0, unlock: 'Story mode final tier', h: hero({}) },
];

function limbMesh(a, b, r, col) {
  const d = new THREE.Vector3().subVectors(b, a);
  const len = Math.max(d.length(), 0.05);
  const m = new THREE.Mesh(new THREE.CapsuleGeometry(r, Math.max(len - 2 * r, 0.02), 6, 12), mat(col));
  m.position.copy(a).addScaledVector(d, 0.5);
  m.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), d.normalize());
  return m;
}

// hanging cloth (cape, scarf, headband tails); the update fn is called each frame
function cloth(w, hgt, col, back, amp = 0.28, speed = 4) {
  const geo = new THREE.PlaneGeometry(w, hgt, 4, 8);
  geo.rotateY(Math.PI / 2);
  geo.translate(0, -hgt / 2, 0);
  const orig = geo.attributes.position.array.slice();
  const m = new THREE.Mesh(geo, mat(col, { side: THREE.DoubleSide, uniq: true }));
  const pos = geo.attributes.position;
  m.userData.update = (t) => {
    for (let i = 0; i < pos.count; i++) {
      const yn = -orig[i * 3 + 1] / hgt;
      pos.setX(i, orig[i * 3] - back * yn + Math.sin(t * speed + yn * 5) * amp * yn);
    }
    pos.needsUpdate = true;
    geo.computeVertexNormals();
  };
  m.userData.update(0);
  return m;
}

export function buildHero(skin, gunKey = 'rifle') {
  const h = skin.h, boss = !!skin.boss;
  const cC = new THREE.Color(skin.c), cD = new THREE.Color(skin.d), cL = new THREE.Color(skin.l);
  const root = new THREE.Group();
  const B = new THREE.Group();
  root.add(B);
  B.scale.setScalar(h.scale * (boss ? 2.1 : 1));
  const updaters = [];

  const tw = h.tw * U, th = h.th * U, hr = h.hr * U, limb = h.limb;
  const tBot = (h.hip - 3) * U, tTop = tBot + th, shY = tTop - 0.8;
  const hcy = tTop + hr * 0.56, hTop = hcy + hr;
  const tx = tw * 0.85, tz = tw * 1.15;
  const bodyM = (col, o) => mat(col, o);
  const mesh = (geo, col, x, y, z, o = {}) => {
    const m = new THREE.Mesh(geo, bodyM(col, o));
    m.position.set(x, y, z);
    B.add(m);
    return m;
  };
  const box = (w, hh, d, col, x, y, z, o = {}) => mesh(new RoundedBoxGeometry(w, hh, d, 2, Math.min(w, hh, d) * 0.3), col, x, y, z, o);

  // torso + belt
  mesh(new RoundedBoxGeometry(2 * tx, th, 2 * tz, 4, Math.min(h.rad * U, tx * 0.9)), cC, 0, tBot + th / 2, 0);
  box(2 * tx + 0.05, 0.4, 2 * tz + 0.05, cD, 0, tBot + 0.3, 0);
  box(0.08, th * 0.4, tz * 0.9, cL, tx - 0.01, tTop - th * 0.28, 0, { alpha: 0.55, uniq: true });

  // legs (pivot at the hip so they can swing)
  const legs = [];
  const legTotal = tBot + 0.15 - 0.3, lr = 0.38 * limb;
  for (const s of [1, -1]) {
    const piv = new THREE.Group();
    piv.position.set(0, tBot + 0.15, s * tz * 0.5);
    const leg = new THREE.Mesh(new THREE.CapsuleGeometry(lr, Math.max(legTotal - 2 * lr, 0.05), 6, 12), bodyM(s > 0 ? cC : cD));
    leg.position.y = -legTotal / 2;
    piv.add(leg);
    const boot = new THREE.Mesh(new RoundedBoxGeometry(1.2 * limb + 0.2, 0.5, 0.85 * limb, 2, 0.12), bodyM(0x1b2c34));
    boot.position.set(0.28, -legTotal + 0.02, 0);
    piv.add(boot);
    B.add(piv);
    legs.push(piv);
  }

  // head, visor
  const hx = 0.1;
  if (h.sq) mesh(new RoundedBoxGeometry(hr * 1.9, hr * 1.84, hr * 1.9, 4, hr * 0.42), cC, hx, hcy, 0);
  else mesh(new THREE.SphereGeometry(hr, 32, 24), cC, hx, hcy, 0);
  const rim = mesh(new THREE.SphereGeometry(hr * 0.62, 24, 16), 0x2b8ca3, hx + hr * 0.62, hcy, 0);
  rim.scale.set(0.42, 0.62, 0.95);
  const vis = mesh(new THREE.SphereGeometry(hr * 0.56, 24, 16), 0x8fe3f5, hx + hr * 0.7, hcy + hr * 0.03, 0, { em: 0x2b8ca3, ei: 0.7, rough: 0.12, metal: 0.3 });
  vis.scale.set(0.4, 0.52, 0.86);

  // jetpack: block + twin thrusters with flames
  const pk = h.pack, flames = [];
  box(0.9 * pk, th * 0.92, tz * 1.5, 0x3f525c, -(tx + 0.4 * pk), tBot + th * 0.5, 0);
  const strip = box(0.12, 0.28, tz * 1.3, 0xff6f59, -(tx + 0.4 * pk) + 0.5 * pk, tTop - 0.55, 0, { uniq: true, em: 0x551a10 });
  for (const s of [1, -1]) {
    const zz = s * tz * 0.55 * Math.min(pk, 1.3);
    mesh(new THREE.CylinderGeometry(0.34 * pk, 0.42 * pk, 0.5, 14), 0x1b2830, -(tx + 0.4 * pk), tBot - 0.1, zz);
    const f = new THREE.Group();
    f.position.set(-(tx + 0.4 * pk), tBot - 0.35, zz);
    const o1 = new THREE.Mesh(new THREE.ConeGeometry(0.34 * pk, 1.5 * pk, 12, 1, true), mat(0xff9b3d, { em: 0xff7a1a, ei: 1.4, alpha: 0.85, uniq: true, side: THREE.DoubleSide }));
    o1.rotation.x = Math.PI; o1.position.y = -0.75 * pk;
    const o2 = new THREE.Mesh(new THREE.ConeGeometry(0.18 * pk, 0.9 * pk, 10, 1, true), mat(0xfff0aa, { em: 0xffe070, ei: 1.5, alpha: 0.95, uniq: true, side: THREE.DoubleSide }));
    o2.rotation.x = Math.PI; o2.position.y = -0.45 * pk;
    f.add(o1, o2);
    B.add(f);
    flames.push(f);
  }

  // gear (acc index follows GameView.kt)
  const chestY = tBot + th * 0.55, headTopY = hTop;
  const shZ = tz + 0.3;
  const spike = (x, y, z, r, len, col, rx = 0, rz = 0) => {
    const m = mesh(new THREE.ConeGeometry(r, len, 8), col, x, y, z);
    m.rotation.set(rx, 0, rz);
    return m;
  };
  const ring = (r, tube, col, y, o = {}) => {
    const m = mesh(new THREE.TorusGeometry(r, tube, 8, 28), col, hx, y, 0, o);
    m.rotation.x = Math.PI / 2;
    return m;
  };
  switch (skin.acc) {
    case 0: { // Nova: antenna + chest light
      const a = limbMesh(new THREE.Vector3(-0.2, headTopY - 0.2, 0), new THREE.Vector3(-0.75, headTopY + 0.9, 0), 0.07, cL);
      B.add(a);
      const tip = mesh(new THREE.SphereGeometry(0.26, 14, 10), cL, -0.78, headTopY + 1.0, 0, { uniq: true, em: skin.l, ei: 1 });
      const cl = mesh(new THREE.SphereGeometry(0.24, 14, 10), cL, tx + 0.02, chestY, 0.2, { uniq: true, em: skin.l, ei: 1 });
      updaters.push((t) => { const on = (t * 1.5) % 2 < 1; tip.material.emissiveIntensity = cl.material.emissiveIntensity = on ? 1.6 : 0.3; });
      break;
    }
    case 1: { // Blaze: mohawk fin, brow band, spiked pauldrons
      const f = mesh(new THREE.ConeGeometry(0.5, 1.7, 4), cD, -0.35, headTopY + 0.2, 0);
      f.rotation.z = 0.35; f.scale.z = 0.35;
      ring(hr * 0.86, 0.14, cD, hcy + hr * 0.45);
      for (const s of [1, -1]) {
        spike(0.05, tTop + 0.55, s * shZ, 0.34, 1.0, cD);
        spike(-0.4, tTop + 0.25, s * (shZ + 0.1), 0.24, 0.7, cD, 0, 0.7);
        ring2(s * shZ);
      }
      function ring2(z) {
        const m = mesh(new THREE.TorusGeometry(0.46 * limb, 0.1, 8, 18), cL, 0.15, shY - 2.4, z);
        m.rotation.y = Math.PI / 2;
      }
      break;
    }
    case 2: { // Frost: crest fins, helmet brow, chest plate, big pauldron
      for (let k = 0; k < 3; k++) {
        const f = mesh(new THREE.ConeGeometry(0.3, 1.0 + (k === 1 ? 0.35 : 0), 4), cL, -0.4 + k * 0.45, headTopY + 0.15, 0);
        f.scale.z = 0.4;
      }
      box(0.5, 0.45, hr * 1.5, cD, hx + hr * 0.78, hcy + hr * 0.42, 0);
      box(0.12, th * 0.55, tz * 1.5, cD, tx + 0.02, tTop - th * 0.38, 0);
      box(0.14, 0.24, tz * 1.2, cL, tx + 0.08, tTop - 1.0, 0);
      for (const s of [1, -1]) mesh(new THREE.SphereGeometry(0.16, 10, 8), cL, tx + 0.1, tBot + th * 0.35, s * tz * 0.7);
      const pa = mesh(new THREE.SphereGeometry(0.95, 18, 12), cD, 0.1, tTop + 0.05, tz + 0.55);
      pa.scale.set(1, 0.8, 0.8);
      break;
    }
    case 3: { // Viper: headband tails, scarf, cross strap
      ring(hr * 0.9, 0.12, cD, hcy - hr * 0.25 + 0.5);
      for (const [dz, i] of [[0.25, 0], [-0.25, 1]]) {
        const c = cloth(0.2, 0.95, cL, 0.8, 0.2, 9 + i);
        c.position.set(-hr * 0.9 + hx, hcy - hr * 0.2, dz);
        c.rotation.z = 1.35;
        B.add(c); updaters.push(c.userData.update);
      }
      const sc = cloth(0.7, 2.2, cL, 2.0, 0.35, 7);
      sc.position.set(-0.2, tTop - 0.1, 0);
      sc.rotation.z = 1.25;
      B.add(sc); updaters.push(sc.userData.update);
      const st = box(0.12, th * 1.05, 0.28, cD, tx + 0.02, tBot + th * 0.5, 0);
      st.rotation.x = 0.75;
      break;
    }
    case 4: { // Sol: halo + cross emblem
      const hal = ring(hr * 0.62, 0.11, cL, headTopY + 0.7, { uniq: true, em: skin.l, ei: 0.8 });
      updaters.push((t) => { hal.position.y = headTopY + 0.7 + Math.sin(t * 2.2) * 0.1; hal.rotation.z = Math.sin(t * 1.4) * 0.12; });
      box(0.1, 0.34, 1.5, cL, tx + 0.02, chestY + 0.3, 0);
      box(0.1, 1.5, 0.34, cL, tx + 0.02, chestY + 0.3, 0);
      box(2 * tx + 0.04, 0.2, 2 * tz + 0.04, cL, 0, tBot + 0.85, 0);
      break;
    }
    case 5: { // Bloom: ear fins, harness, buckle
      for (const s of [1, -1]) {
        const f = mesh(new THREE.ConeGeometry(0.4, 1.2, 4), cD, -0.15, headTopY + 0.05, s * hr * 0.5);
        f.rotation.x = s * -0.3; f.scale.x = 0.4;
        box(0.12, th * 0.9, 0.24, cD, tx + 0.02, tBot + th * 0.5, s * tz * 0.7);
      }
      mesh(new THREE.SphereGeometry(0.26, 12, 10), cL, tx + 0.1, tBot + th * 0.5, 0);
      break;
    }
    case 6: { // Onyx: hood cap, antennae, blade shoulders, chest diamond
      const cap = mesh(new THREE.SphereGeometry(hr * 1.02, 24, 12, 0, Math.PI * 2, 0, Math.PI * 0.42), cD, hx, hcy + hr * 0.04, 0);
      cap.scale.set(1.0, 1.0, 1.0);
      for (const s of [1, -1]) {
        B.add(limbMesh(new THREE.Vector3(-0.3, headTopY - 0.2, s * 0.3), new THREE.Vector3(-0.9, headTopY + 0.95, s * 0.8), 0.06, cL));
        mesh(new THREE.SphereGeometry(0.2, 10, 8), cL, -0.92, headTopY + 1.0, s * 0.82);
        spike(-0.1, tTop + 0.7, s * (tz + 0.15), 0.4, 1.5, cD, 0, s * 0.25).scale.x = 0.5;
      }
      const dia = mesh(new THREE.OctahedronGeometry(0.55), cL, tx + 0.05, chestY, 0, { em: skin.l, ei: 0.3, uniq: true });
      dia.scale.set(0.3, 1, 0.8);
      break;
    }
    case 7: { // Rust: headband, cape, bandolier, big pauldron
      ring(hr * 0.9, 0.11, cL, hcy + hr * 0.3);
      const cp = cloth(tz * 1.7, 3.6, cD, 1.4, 0.3, 4);
      cp.position.set(-tx - 0.95 * pk - 0.1, tTop - 0.2, 0);
      B.add(cp); updaters.push(cp.userData.update);
      const bd = box(0.14, th * 1.15, 0.38, cD, tx + 0.02, tBot + th * 0.5, 0);
      bd.rotation.x = -0.7;
      for (let k = 0; k < 3; k++) {
        const b = mesh(new THREE.CylinderGeometry(0.13, 0.13, 0.4, 8), cL, tx + 0.15, tBot + th * (0.7 - k * 0.2), (k - 1) * 0.55);
        b.rotation.x = 0.0;
      }
      const pa = mesh(new THREE.SphereGeometry(0.9, 18, 12), cD, 0.1, tTop + 0.1, tz + 0.55);
      pa.scale.set(1, 0.85, 0.85);
      break;
    }
    case 8: { // Overlord: horns, spiked shoulders, cape
      for (const s of [1, -1]) {
        const hn = mesh(new THREE.ConeGeometry(0.42, 2.0, 10), K.cream, -0.1, headTopY + 0.3, s * hr * 0.62);
        hn.rotation.x = s * -0.5; hn.rotation.z = 0.2;
        spike(0.05, tTop + 0.6, s * shZ, 0.4, 1.3, cD);
        spike(-0.5, tTop + 0.3, s * (shZ + 0.1), 0.3, 1.0, cD, 0, 0.7);
      }
      const cp = cloth(tz * 1.7, 4.0, cD, 1.2, 0.3, 3);
      cp.position.set(-tx - 0.95 * pk - 0.1, tTop - 0.2, 0);
      B.add(cp); updaters.push(cp.userData.update);
      box(0.12, 0.3, tz * 1.3, 0xf2e3c4, tx + 0.03, chestY + 0.6, 0);
      break;
    }
  }

  // gun + arms
  const gun = buildGun(gunKey);
  const G = new THREE.Vector3(tx + 0.75, shY - 0.55, 0);
  gun.position.copy(G);
  B.add(gun);
  const aR = limb * 0.3;
  const fore = gun.userData.fore.clone().add(G);
  for (const [s, target, col] of [[1, G.clone().add(new THREE.Vector3(0, 0, 0.12)), cC], [-1, fore.clone().add(new THREE.Vector3(0, 0, -0.12)), cD]]) {
    const sh = new THREE.Vector3(0.1, shY, s * (tz + 0.15));
    B.add(limbMesh(sh, target, aR, col));
    const hand = mesh(new THREE.SphereGeometry(aR * 1.25, 12, 10), cL, target.x, target.y, target.z);
    hand.name = 'hand';
    const jt = mesh(new THREE.SphereGeometry(aR * 1.15, 12, 10), col, sh.x, sh.y, sh.z);
    jt.name = 'shoulder';
  }
  box(0.5, 0.5, 0.2, 0x1b2830, G.x, G.y, 0).visible = false;

  root.userData.gun = gun;
  root.userData.update = (t, flags = {}) => {
    const jet = flags.jet !== false;
    const bob = Math.sin(t * 2.4) * 0.05;
    B.position.y = bob + (jet ? 0.25 + Math.sin(t * 1.7) * 0.08 : 0);
    flames.forEach((f, i) => {
      f.visible = jet;
      const k = 0.75 + Math.abs(Math.sin(t * 22 + i * 1.7)) * 0.5 + Math.sin(t * 37 + i) * 0.12;
      f.scale.set(1, k, 1);
    });
    strip.material.emissive.setHex(jet ? 0xffb030 : 0x551a10);
    legs.forEach((l, i) => {
      const w = flags.walk ? Math.sin(t * 7 + i * Math.PI) * 0.55 : (jet ? -0.12 + Math.sin(t * 2 + i) * 0.04 : 0);
      l.rotation.z = w;
    });
    updaters.forEach((u) => u(t));
  };
  root.userData.update(0);
  return root;
}
