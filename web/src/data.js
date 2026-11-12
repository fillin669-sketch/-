// Корабли игрока. look — как корабль выглядит.
export const PLAYER_SHIPS = [
  {
    id: 'sloop', name: 'Чайка', cls: 'Шлюп',
    desc: 'Лёгкий и послушный кораблик для первых вылазок.',
    hp: 120, speed: 15, turn: 1.9, cannons: 2, dmg: 10, reload: 1.8, range: 42, mass: 1.0,
    price: 0, gems: 0,
    look: { len: 12, width: 4.2, masts: 1, hull: 0x7a4a26, trim: 0x2f6db5, sail: 0xf6f0e0, flag: 0x2f6db5 },
  },
  {
    id: 'brigantine', name: 'Ласточка', cls: 'Бригантина',
    desc: 'Быстрая двухмачтовая. Отлично таранит.',
    hp: 200, speed: 16.5, turn: 1.75, cannons: 3, dmg: 12, reload: 1.7, range: 46, mass: 1.4,
    price: 1200, gems: 0,
    look: { len: 15, width: 5, masts: 2, hull: 0x6e3f22, trim: 0x2a9d8f, sail: 0xf6f0e0, flag: 0x2a9d8f },
  },
  {
    id: 'corvette', name: 'Буревестник', cls: 'Корвет',
    desc: 'Самый быстрый корабль. Догонит кого угодно.',
    hp: 280, speed: 19, turn: 1.8, cannons: 4, dmg: 14, reload: 1.5, range: 50, mass: 1.6,
    price: 3500, gems: 0,
    look: { len: 17, width: 5.2, masts: 2, hull: 0x5b3a29, trim: 0xe9c46a, sail: 0xfff8e7, flag: 0xe9c46a },
  },
  {
    id: 'frigate', name: 'Гроза', cls: 'Фрегат',
    desc: 'Военный корабль: крепкий корпус и шесть пушек на борт.',
    hp: 420, speed: 17, turn: 1.5, cannons: 6, dmg: 16, reload: 1.5, range: 55, mass: 2.4,
    price: 8000, gems: 10,
    look: { len: 21, width: 6.4, masts: 3, hull: 0x4a2f1f, trim: 0xb23a48, sail: 0xf3ead3, flag: 0xb23a48 },
  },
  {
    id: 'galleon', name: 'Левиафан', cls: 'Галеон',
    desc: 'Плавучая крепость. Медленный, но выдерживает бурю ядер.',
    hp: 700, speed: 14.5, turn: 1.15, cannons: 9, dmg: 20, reload: 1.6, range: 58, mass: 3.6,
    price: 18000, gems: 40,
    look: { len: 25, width: 8, masts: 3, hull: 0x3d2817, trim: 0xd4a017, sail: 0xf1e4c3, flag: 0xd4a017 },
  },
  {
    id: 'dragon', name: 'Морской дракон', cls: 'Линкор',
    desc: 'Легенда морей. Двенадцать пушек на борт и таран, как у кита.',
    hp: 1000, speed: 16, turn: 1.05, cannons: 12, dmg: 24, reload: 1.4, range: 62, mass: 5.0,
    price: 40000, gems: 120,
    look: { len: 29, width: 8.8, masts: 4, hull: 0x2b1d14, trim: 0x7b2cbf, sail: 0xe9e3f5, flag: 0x7b2cbf },
  },
];

export const UPGRADES = [
  { id: 'hull', name: 'Корпус', icon: '🛡️', per: 0.15, text: 'прочность' },
  { id: 'sails', name: 'Паруса', icon: '⛵', per: 0.06, text: 'скорость' },
  { id: 'guns', name: 'Пушки', icon: '💥', per: 0.15, text: 'урон' },
  { id: 'reload', name: 'Заряжание', icon: '⏱️', per: 0.08, text: 'скорость стрельбы' },
];
export const MAX_LEVEL = 5;

export function upgradeCost(ship, level) {
  const base = Math.max(150, ship.price / 5);
  return Math.round((base * Math.pow(level + 1, 1.6)) / 10) * 10;
}

export function shipStats(ship, up) {
  up = up || { hull: 0, sails: 0, guns: 0, reload: 0 };
  return {
    hp: Math.round(ship.hp * (1 + 0.15 * up.hull)),
    speed: ship.speed * (1 + 0.06 * up.sails),
    turn: ship.turn,
    cannons: ship.cannons,
    dmg: ship.dmg * (1 + 0.15 * up.guns),
    reload: ship.reload * (1 - 0.08 * up.reload),
    range: ship.range,
    mass: ship.mass,
  };
}

// Пираты
export const ENEMIES = {
  sloop: {
    name: 'Пиратский шлюп', hp: 60, speed: 11, turn: 1.6, cannons: 2, dmg: 6, reload: 2.4, range: 38, mass: 0.9,
    coins: [40, 80], gems: 0,
    look: { len: 11, width: 4, masts: 1, hull: 0x3b2a1e, trim: 0x8b1a1a, sail: 0x3a3a3a, flag: 0x111111 },
  },
  brig: {
    name: 'Пиратская бригантина', hp: 130, speed: 12.5, turn: 1.45, cannons: 3, dmg: 9, reload: 2.2, range: 44, mass: 1.4,
    coins: [90, 150], gems: 0.08,
    look: { len: 15, width: 5, masts: 2, hull: 0x33241a, trim: 0x8b1a1a, sail: 0x2e2e2e, flag: 0x111111 },
  },
  frigate: {
    name: 'Корсарский фрегат', hp: 280, speed: 13.5, turn: 1.3, cannons: 5, dmg: 13, reload: 2.0, range: 52, mass: 2.4,
    coins: [200, 320], gems: 0.35,
    look: { len: 21, width: 6.4, masts: 3, hull: 0x2a1d15, trim: 0x6a040f, sail: 0x262626, flag: 0x111111 },
  },
  galleon: {
    name: 'Чёрный галеон', hp: 650, speed: 11.5, turn: 1.0, cannons: 8, dmg: 18, reload: 1.9, range: 58, mass: 3.8,
    coins: [700, 1000], gems: 6, boss: true,
    look: { len: 26, width: 8.2, masts: 3, hull: 0x1a1210, trim: 0xc9a227, sail: 0x1c1c1c, flag: 0x111111 },
  },
};

export const WORLD = 1000; // половина размера мира

export const PORTS = [
  { name: 'Гавань Ветров', x: 0, z: 0, r: 52 },
  { name: 'Коралловая бухта', x: 620, z: -430, r: 48 },
  { name: 'Форт Чайки', x: -640, z: 300, r: 55 },
  { name: 'Туманный порт', x: 430, z: 640, r: 50 },
  { name: 'Скала Ворона', x: -420, z: -650, r: 50 },
];

// Задания. После списка генерируются бесконечные задания с ростом сложности.
const QUESTS = [
  { type: 'kill', enemy: 'sloop', n: 1, text: 'Потопи пиратский шлюп', reward: { coins: 100 } },
  { type: 'ram', enemy: 'sloop', n: 1, text: 'Протарань пиратский шлюп', reward: { coins: 150 } },
  { type: 'collect', n: 300, text: 'Собери 300 монет', reward: { gems: 3 } },
  { type: 'reach', port: 2, text: 'Доплыви до порта «Форт Чайки»', reward: { coins: 200 } },
  { type: 'upgrade', n: 1, text: 'Улучши корабль в порту', reward: { coins: 150 } },
  { type: 'kill', enemy: 'brig', n: 3, text: 'Потопи 3 пиратские бригантины', reward: { coins: 300, gems: 3 } },
  { type: 'buy', n: 1, text: 'Купи новый корабль на верфи', reward: { gems: 10 } },
  { type: 'ram', enemy: 'brig', n: 1, text: 'Протарань пиратскую бригантину', reward: { coins: 400 } },
  { type: 'reach', port: 1, text: 'Доплыви до «Коралловой бухты»', reward: { coins: 300 } },
  { type: 'kill', enemy: 'frigate', n: 1, text: 'Потопи корсарский фрегат', reward: { coins: 600, gems: 5 } },
  { type: 'collect', n: 1500, text: 'Собери 1 500 монет', reward: { gems: 8 } },
  { type: 'kill', enemy: 'galleon', n: 1, text: 'Потопи Чёрный галеон', reward: { coins: 1500, gems: 20 } },
];

export function questAt(i) {
  if (i < QUESTS.length) return QUESTS[i];
  const k = i - QUESTS.length;
  const lvl = Math.floor(k / 4) + 1;
  switch (k % 4) {
    case 0: return { type: 'kill', enemy: 'brig', n: 3 + lvl, text: `Потопи ${3 + lvl} пиратских бригантин`, reward: { coins: 400 * lvl, gems: 3 } };
    case 1: return { type: 'ram', enemy: 'frigate', n: 1, text: 'Протарань корсарский фрегат', reward: { coins: 700 * lvl, gems: 4 } };
    case 2: return { type: 'kill', enemy: 'frigate', n: 1 + lvl, text: `Потопи ${1 + lvl} корсарских фрегата`, reward: { coins: 800 * lvl, gems: 6 } };
    default: return { type: 'kill', enemy: 'galleon', n: 1, text: 'Потопи Чёрный галеон', reward: { coins: 2000 * lvl, gems: 20 } };
  }
}
