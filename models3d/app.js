import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { GLTFExporter } from 'three/addons/exporters/GLTFExporter.js';
import { GUN_ORDER, WEAPON_STATS, SKINS, buildGun, buildHero } from './models.js';

const $ = (s) => document.querySelector(s);
const stage = $('#stage');

const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, preserveDrawingBuffer: false });
renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.05;
renderer.outputColorSpace = THREE.SRGBColorSpace;
stage.appendChild(renderer.domElement);

const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(36, 1, 0.1, 200);
camera.position.set(4, 2.2, 7);
const controls = new OrbitControls(camera, renderer.domElement);
controls.enableDamping = true;
controls.enablePan = false;
controls.autoRotate = true;
controls.autoRotateSpeed = 2.2;

scene.add(new THREE.HemisphereLight(0xcfeaff, 0x1b2a2f, 1.1));
const key = new THREE.DirectionalLight(0xffffff, 2.2);
key.position.set(5, 8, 6);
scene.add(key);
const rim = new THREE.PointLight(0x19e3c8, 40, 30);
rim.position.set(-5, 3, -4);
scene.add(rim);
const fill = new THREE.PointLight(0xff9b3d, 18, 30);
fill.position.set(5, -1, 4);
scene.add(fill);

const state = { tab: 'guns', gun: 'rifle', hero: 0, heroGun: 'rifle', spin: true, jet: true, walk: false };
let current = null, ground = null, flash = null, flashT = 0;

function resize() {
  const w = stage.clientWidth, h = stage.clientHeight;
  renderer.setSize(w, h);
  camera.aspect = w / h;
  camera.updateProjectionMatrix();
}
addEventListener('resize', resize);

function setAccent(hex) {
  document.documentElement.style.setProperty('--accent', hex);
  rim.color.set(hex);
}

function makeFlash(kind) {
  const g = new THREE.Group();
  const col = kind === 'laser' ? 0x00e5ff : kind === 'flame' ? 0xff7a1a : 0xffc24a;
  const len = kind === 'flame' ? 2.6 : kind === 'laser' ? 6 : kind === 'blast' ? 1.4 : 0.9;
  const rad = kind === 'laser' ? 0.05 : kind === 'flame' ? 0.5 : 0.28;
  const cone = new THREE.Mesh(new THREE.ConeGeometry(rad, len, 12, 1, true), new THREE.MeshBasicMaterial({ color: col, transparent: true, opacity: 0.9, side: THREE.DoubleSide }));
  cone.rotation.z = -Math.PI / 2;
  cone.position.x = len / 2;
  const core = new THREE.Mesh(new THREE.SphereGeometry(rad * 0.9, 12, 10), new THREE.MeshBasicMaterial({ color: 0xfff3b0, transparent: true, opacity: 0.95 }));
  g.add(cone, core);
  g.visible = false;
  return g;
}

function show() {
  if (current) scene.remove(current);
  if (ground) scene.remove(ground);
  let obj, gunObj, accent;
  if (state.tab === 'guns') {
    obj = buildGun(state.gun);
    gunObj = obj;
    accent = '#ffb347';
  } else {
    const sk = SKINS[state.hero];
    obj = buildHero(sk, state.heroGun);
    gunObj = obj.userData.gun;
    accent = sk.c;
  }
  scene.add(obj);
  current = obj;
  if (gunObj.userData.kind !== 'none') {
    flash = makeFlash(gunObj.userData.kind);
    flash.position.copy(gunObj.userData.muzzle);
    gunObj.add(flash);
  } else flash = null;
  setAccent(accent);

  const box = new THREE.Box3().setFromObject(obj);
  const size = box.getSize(new THREE.Vector3()), c = box.getCenter(new THREE.Vector3());
  const R = Math.max(size.x, size.y, size.z);
  controls.target.copy(c);
  const dist = R * (state.tab === 'guns' ? 1.55 : 1.9) / Math.tan(THREE.MathUtils.degToRad(camera.fov / 2)) * 0.5;
  const dir = camera.position.clone().sub(controls.target).normalize();
  camera.position.copy(controls.target).addScaledVector(dir, Math.max(dist, 3));
  controls.minDistance = R * 0.7;
  controls.maxDistance = R * 5;

  ground = new THREE.Group();
  const ringMat = new THREE.MeshBasicMaterial({ color: new THREE.Color(accent), transparent: true, opacity: 0.55, side: THREE.DoubleSide });
  const r1 = new THREE.Mesh(new THREE.RingGeometry(R * 0.62, R * 0.65, 64), ringMat);
  const r2 = new THREE.Mesh(new THREE.RingGeometry(R * 0.78, R * 0.79, 64), ringMat.clone());
  r2.material.opacity = 0.25;
  const disc = new THREE.Mesh(new THREE.CircleGeometry(R * 0.62, 64), new THREE.MeshBasicMaterial({ color: new THREE.Color(accent), transparent: true, opacity: 0.08, side: THREE.DoubleSide }));
  for (const m of [r1, r2, disc]) { m.rotation.x = -Math.PI / 2; ground.add(m); }
  ground.position.set(c.x, box.min.y - 0.25 - (state.tab === 'guns' ? 0.3 : 0), c.z);
  scene.add(ground);
  current.userData.baseY = current.position.y;
  renderInfo();
  renderChips();
}

function renderChips() {
  const row = $('#chips');
  row.innerHTML = '';
  const items = state.tab === 'guns'
    ? GUN_ORDER.map((k) => ({ id: k, name: WEAPON_STATS[k].n, dot: '#ffb347', on: state.gun === k }))
    : SKINS.map((s, i) => ({ id: i, name: s.name, dot: s.c, on: state.hero === i }));
  items.forEach((it, i) => {
    const b = document.createElement('button');
    b.className = 'chip' + (it.on ? ' on' : '');
    b.style.setProperty('--dot', it.dot);
    b.style.animationDelay = i * 35 + 'ms';
    b.innerHTML = '<i></i>' + it.name;
    b.onclick = () => {
      if (state.tab === 'guns') state.gun = it.id; else state.hero = it.id;
      show();
      b.scrollIntoView({ inline: 'center', block: 'nearest', behavior: 'smooth' });
    };
    row.appendChild(b);
  });
  const wrow = $('#weapons');
  wrow.hidden = state.tab !== 'heroes';
  if (state.tab === 'heroes') {
    wrow.innerHTML = '<span>Weapon</span>';
    GUN_ORDER.forEach((k) => {
      const b = document.createElement('button');
      b.className = 'chip sm' + (state.heroGun === k ? ' on' : '');
      b.textContent = WEAPON_STATS[k].n;
      b.onclick = () => { state.heroGun = k; show(); };
      wrow.appendChild(b);
    });
  }
}

function tile(label, val) { return `<div class="tile"><b>${val}</b><span>${label}</span></div>`; }

function renderInfo() {
  const card = $('#info');
  let html;
  if (state.tab === 'guns') {
    const w = WEAPON_STATS[state.gun];
    html = `<h2>${w.n}</h2><p class="tag">WEAPON</p><p>${w.d}</p><div class="tiles">`
      + tile('damage', w.dmg)
      + (w.rate ? tile('shots/s', (1 / w.rate).toFixed(w.rate < 0.1 ? 0 : 1)) + tile('magazine', w.mag) + tile('reload', w.rel.toFixed(1) + 's') : tile('thrown', 'by hand'))
      + '</div>';
  } else {
    const s = SKINS[state.hero];
    html = `<h2>${s.name}</h2><p class="tag">${s.role}</p><p>${s.l1}.<br>${s.l2}.</p><div class="tiles">`
      + (s.boss ? tile('type', 'Boss') : tile('HP', s.hp) + tile('damage', 'x' + s.dmg) + tile('speed', 'x' + s.spd) + tile('armor', s.armor + '%'))
      + `</div><p class="unlock">Unlock: ${s.unlock}</p>`;
  }
  card.innerHTML = html;
  card.classList.remove('pop'); void card.offsetWidth; card.classList.add('pop');
}

// tabs + toggles
document.querySelectorAll('.tab').forEach((t) => {
  t.onclick = () => {
    state.tab = t.dataset.tab;
    document.querySelectorAll('.tab').forEach((x) => x.classList.toggle('on', x === t));
    document.querySelector('.tabs').dataset.on = state.tab;
    $('#walk').hidden = state.tab !== 'heroes';
    $('#jet').hidden = state.tab !== 'heroes';
    show();
  };
});
function toggle(id, key, cb) {
  const b = $(id);
  b.onclick = () => { state[key] = !state[key]; b.classList.toggle('on', state[key]); cb && cb(); };
  b.classList.toggle('on', state[key]);
}
toggle('#spin', 'spin', () => { controls.autoRotate = state.spin; });
toggle('#jet', 'jet');
toggle('#walk', 'walk');
$('#fire').onclick = () => { flashT = 0.14; if (flash) flash.visible = true; };
$('#export').onclick = () => {
  const name = state.tab === 'guns' ? state.gun : SKINS[state.hero].name.toLowerCase() + '-' + state.heroGun;
  if (flash) flash.visible = false;
  new GLTFExporter().parse(current, (buf) => {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([buf], { type: 'model/gltf-binary' }));
    a.download = `jetstrike-${name}.glb`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 4000);
  }, (e) => console.error(e), { binary: true });
};

const clock = new THREE.Clock();
function loop() {
  const t = clock.getElapsedTime(), dt = clock.getDelta();
  controls.update();
  if (current) {
    if (current.userData.update) current.userData.update(t, { jet: state.jet, walk: state.walk });
    else current.position.y = Math.sin(t * 1.6) * 0.08;
    if (state.tab === 'guns') current.rotation.z = Math.sin(t * 1.1) * 0.04;
  }
  if (ground) { ground.children[0].rotation.z = t * 0.3; ground.children[1].rotation.z = -t * 0.2; ground.children[1].scale.setScalar(1 + Math.sin(t * 2) * 0.02); }
  if (flashT > 0 && flash) {
    flashT -= 1 / 60;
    const k = Math.max(flashT / 0.14, 0);
    flash.scale.setScalar(0.6 + (1 - k) * 0.9);
    flash.children.forEach((m) => { m.material.opacity = k; });
    if (flashT <= 0) flash.visible = false;
  }
  renderer.render(scene, camera);
  requestAnimationFrame(loop);
}

resize();
show();
loop();
