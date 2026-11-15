'use strict';
// ================= КАТАЛОГ ДЕТАЛЕЙ, ШАБЛОНЫ, ЗАКАЗЫ =================
// opt: [значение, название, цена (число или функция от двигателя), описание]

const pc = (x) => (e) => x * e.cyl;               // цена за цилиндр
const perHead = (x) => (e) => x * BANKS_HEADS(e);
const perL = (x) => (e) => Math.round(x * displacement(e));
const perT = (x) => (e) => x * Math.max(1, turboCount(e));

const notRot = (e) => !isRotary(e);
const fourStroke = (e) => !isRotary(e) && e.cycle === 4;
const isDiesel = (e) => !!FUELS[e.fuel].diesel;
const standalone = (e) => e.ecu === 'standalone';

const ENGINE_SPEC = [
  { id: 'layout', title: 'Компоновка', icon: 'layout', fields: [
    { k: 'type', label: 'Тип двигателя', opts: [
      ['inline', 'Рядный', 0, 'Все цилиндры в один ряд. Просто, дёшево, легко обслуживать'],
      ['v', 'V-образный', 1500, 'Два ряда под углом. Короткий и мощный'],
      ['boxer', 'Оппозитный', 2000, 'Ряды лежат друг против друга (180°). Низкий центр тяжести'],
      ['vr', 'VR (узкий V)', 2500, 'Два ряда под 15° с общей головкой. Компактный'],
      ['w', 'W-образный', 6000, 'Два VR-блока на одном коленвале. Очень много цилиндров в малом объёме'],
      ['radial', 'Звездообразный', 4000, 'Цилиндры звездой вокруг вала, как у самолётов'],
      ['rotary', 'Роторный (Ванкель)', 3000, 'Без поршней: треугольный ротор в корпусе. Высокие обороты'],
    ] },
    { k: 'cyl', label: (e) => cylLabel(e), opts: (e) => CYL_OPTIONS[e.type].map(n => [n, String(n), isRotary(e) ? n * 1200 : n * 300, '']) },
    { k: 'vAngle', label: 'Угол развала', type: 'range', min: 15, max: 150, step: 1, unit: '°', show: (e) => e.type === 'v' || e.type === 'w', price: 0,
      hint: 'V6 лучше всего уравновешен при 60°, V8 — при 90°' },
    { k: 'crankPlane', label: 'Коленвал V8', show: (e) => e.type === 'v' && e.cyl === 8, opts: [
      ['cross', 'Крестообразный', 0, 'Мягкий, «булькающий» звук маслкара'],
      ['flat', 'Плоский', 1500, 'Резкий визг, быстрее раскручивается, больше вибраций'],
    ] },
    { k: 'cycle', label: 'Рабочий цикл', show: notRot, opts: [
      [4, '4-тактный', 0, 'Впуск, сжатие, рабочий ход, выпуск — за 2 оборота'],
      [2, '2-тактный', -500, 'Рабочий ход каждый оборот: лёгкий и злой, но прожорливый'],
    ] },
  ] },
  { id: 'block', title: 'Блок цилиндров', icon: 'block', fields: [
    { k: 'block', label: 'Материал блока', opts: [
      ['iron', 'Чугун', 0, 'Тяжёлый, но очень прочный'],
      ['cgi', 'Вермикулярный чугун', perL(500), 'Прочнее и легче обычного чугуна'],
      ['alu', 'Алюминий с гильзами', perL(700), 'Почти вдвое легче'],
      ['alusil', 'Алюсил (без гильз)', perL(1000), 'Самый лёгкий литой блок'],
      ['billet', 'Фрезерованный из заготовки', perL(4500), 'Для 2000+ сил'],
    ] },
    { k: 'deck', label: 'Рубашка блока', show: notRot, opts: [
      ['open', 'Открытая', 0, 'Лучше охлаждение, слабее под наддувом'],
      ['closed', 'Закрытая', 800, '+25% к прочности блока'],
    ] },
    { k: 'bore', label: 'Диаметр цилиндра', type: 'range', min: 50, max: 135, step: 0.5, unit: 'мм', show: notRot, price: (e) => Math.max(0, e.bore - 80) * 4 * e.cyl,
      hint: 'Больше диаметр — больше объём и места для клапанов, но выше риск детонации' },
    { k: 'stroke', label: 'Ход поршня', type: 'range', min: 40, max: 135, step: 0.5, unit: 'мм', show: notRot, price: (e) => Math.max(0, e.stroke - 80) * 4 * e.cyl,
      hint: 'Длинный ход — больше момента внизу, короткий — выше обороты' },
    { k: 'rotorVol', label: 'Объём секции', type: 'range', min: 300, max: 1000, step: 5, unit: 'см³', show: isRotary, price: (e) => Math.max(0, e.rotorVol - 600) * 3 * e.cyl },
  ] },
  { id: 'crank', title: 'Кривошипно-шатунный механизм', icon: 'crank', fields: [
    { k: 'crank', label: (e) => isRotary(e) ? 'Эксцентриковый вал' : 'Коленчатый вал', opts: [
      ['cast', 'Литой', 0, 'Штатный'], ['forged', 'Кованый', (e) => 300 * e.cyl, 'Выдерживает обороты и наддув'],
      ['billet', 'Из заготовки', (e) => 900 * e.cyl, 'Максимальная прочность'],
    ] },
    { k: 'rods', label: 'Шатуны', show: notRot, opts: [
      ['cast', 'Литые', 0, ''], ['forged', 'Кованые H-профиль', pc(150), ''],
      ['titanium', 'Титановые', pc(500), 'Лёгкие — для высоких оборотов'], ['billet', 'Стальные из заготовки', pc(380), 'Для огромного давления'],
    ] },
    { k: 'rodLen', label: 'Длина шатуна', type: 'range', min: (e) => Math.ceil(e.stroke * 1.3), max: (e) => Math.max(Math.ceil(e.stroke * 1.3) + 10, Math.round(e.stroke * 2.3)), step: 1, unit: 'мм', show: notRot, price: 0,
      hint: 'Отношение шатун/ход выше 1,6 снижает нагрузки на высоких оборотах' },
    { k: 'pistons', label: (e) => isRotary(e) ? 'Апекс-уплотнения' : 'Поршни', opts: [
      ['cast', 'Литые', 0, ''], ['hyper', 'Гиперэвтектика', pc(60), 'Чуть прочнее литых'],
      ['forged', 'Кованые', pc(220), 'Держат детонацию и наддув'], ['billet', 'Из заготовки', pc(420), 'Для экстремального наддува'],
    ] },
    { k: 'cr', label: 'Степень сжатия', type: 'range', min: 6, max: 23, step: 0.1, unit: ':1', price: 0,
      hint: 'Выше — больше КПД, но выше риск детонации. Дизелю нужно 14–22' },
    { k: 'flywheel', label: 'Маховик', opts: [
      ['heavy', 'Тяжёлый', -100, 'Ровный холостой ход, медленно крутится'], ['stock', 'Штатный', 0, ''],
      ['light', 'Облегчённый', 300, 'Быстрее набирает обороты'], ['carbon', 'Карбоновый', 900, 'Мгновенный отклик'],
    ] },
    { k: 'bearings', label: 'Вкладыши', show: notRot, opts: [['stock', 'Штатные', 0, ''], ['race', 'Гоночные (триметалл)', pc(60), '+4% к пределу оборотов']] },
    { k: 'damper', label: 'Демпфер коленвала', opts: [['stock', 'Резиновый', 0, ''], ['fluid', 'Жидкостный', 700, 'Гасит крутильные колебания, +4% к оборотам']] },
    { k: 'balance', label: 'Балансирные валы', type: 'toggle', show: (e) => e.type === 'inline' && e.cyl <= 4, price: 500, hint: 'Гасят вибрации, но немного отбирают мощность' },
  ] },
  { id: 'head', title: (e) => isRotary(e) ? 'Окна и фазы' : 'Головка блока и ГРМ', icon: 'head', fields: [
    { k: 'valvetrain', label: 'Газораспределение', show: fourStroke, opts: [
      ['ohv', 'OHV (штанги)', 0, 'Распредвал в блоке. Компактно, низкие обороты'],
      ['sohc', 'SOHC (1 вал)', perHead(400), ''],
      ['dohc', 'DOHC (2 вала)', perHead(1000), 'Высокие обороты, 4–5 клапанов'],
    ] },
    { k: 'valves', label: 'Клапанов на цилиндр', show: fourStroke, opts: (e) => (e.valvetrain === 'ohv' ? [2] : e.valvetrain === 'sohc' ? [2, 3, 4] : [2, 4, 5]).map(v => [v, String(v), ({ 2: 0, 3: 40, 4: 80, 5: 150 })[v] * e.cyl, '']) },
    { k: 'camDur', label: (e) => isRotary(e) ? 'Фазы окон' : 'Фаза распредвала', type: 'range', min: 200, max: 320, step: 1, unit: '°', show: (e) => e.cycle === 4 || isRotary(e),
      price: (e) => 200 + Math.max(0, e.camDur - 240) * 12 * BANKS_HEADS(e), hint: 'Широкая фаза сдвигает мощность на высокие обороты, но «убивает» низы' },
    { k: 'camLift', label: 'Подъём клапана', type: 'range', min: 7, max: 16, step: 0.1, unit: 'мм', show: fourStroke, price: (e) => Math.max(0, e.camLift - 9.5) * 150 * BANKS_HEADS(e) },
    { k: 'lifters', label: 'Толкатели', show: fourStroke, opts: [['hydraulic', 'Гидравлические', 0, 'Тихие, без регулировки'], ['solid', 'Механические', pc(40), '+5% к оборотам зависания'], ['roller', 'Роликовые', pc(110), '+8% и меньше трения']] },
    { k: 'vvt', label: 'Изменение фаз', show: (e) => fourStroke(e) && e.valvetrain !== 'ohv', opts: [
      ['none', 'Нет', 0, ''], ['intake', 'На впуске', 600, 'Шире полка момента'], ['dual', 'На впуске и выпуске', 1200, ''],
      ['vtec', 'Ступенчатое (два профиля)', 1800, 'Мягкий кулачок внизу, злой — наверху'],
    ] },
    { k: 'springs', label: 'Пружины клапанов', show: fourStroke, opts: (e) => [
      ['stock', 'Штатные', 0, ''], ['sport', 'Усиленные', 300, '+12% к оборотам зависания'], ['race', 'Гоночные', 900, '+25%'],
      ...(e.valvetrain === 'dohc' ? [['pneumatic', 'Пневматические (F1)', 6000, 'Почти без предела оборотов']] : []),
    ] },
    { k: 'headGasket', label: 'Прокладка головки', show: notRot, opts: [['stock', 'Штатная', 0, 'Держит ~30 бар'], ['mls', 'Многослойная стальная', 300, '~48 бар'], ['copper', 'Медная (дрэг)', 900, '~75 бар']] },
    { k: 'headStuds', label: 'Крепёж головки', show: notRot, opts: [['bolts', 'Штатные болты', 0, ''], ['arp', 'Шпильки ARP', 450, '+25% к прочности стыка']] },
    { k: 'head', label: 'Материал головки', show: notRot, opts: [['iron', 'Чугун', 0, 'Тяжелее'], ['alu', 'Алюминий', perHead(400), '']] },
    { k: 'porting', label: (e) => isRotary(e) ? 'Тип окон' : e.cycle === 2 ? 'Продувочные окна' : 'Доработка каналов', opts: (e) => isRotary(e) ? [
      ['stock', 'Боковые', 0, ''], ['stage1', 'Расширенные', 900, ''], ['stage2', 'Мостовые (bridge port)', 2200, 'Злой холостой и бешеные верха']] : [
      ['stock', 'Штатные', 0, ''], ['stage1', 'Шлифовка (ст. 1)', 600, ''], ['stage2', 'Гоночная (ст. 2)', 1600, '']] },
    { k: 'tunedPipe', label: 'Резонатор выпуска', type: 'toggle', show: (e) => e.cycle === 2 && notRot(e), price: pc(400), hint: 'Расширительная камера: +16% наполнения в узком диапазоне' },
  ] },
  { id: 'intake', title: 'Впуск', icon: 'intake', fields: [
    { k: 'airFilter', label: 'Воздушный фильтр', opts: [['stock', 'Штатный короб', 0, ''], ['cone', 'Нулевик', 150, ''], ['ram', 'Холодный впуск', 500, '']] },
    { k: 'manifold', label: 'Впускной коллектор', opts: (e) => [
      ['stock', 'Штатный', 0, ''], ['long', 'Длинные каналы', 500, 'Момент внизу'], ['short', 'Короткие каналы', 500, 'Мощность наверху'],
      ['variable', 'С изменяемой длиной', 1400, 'Широкая полка'], ['itb', 'Отдельные дроссели', 250 * e.cyl, 'Острый отклик и звук'],
    ] },
    { k: 'throttle', label: (e) => e.manifold === 'itb' ? 'Дроссель на цилиндр' : 'Диаметр дросселя', type: 'range', min: 28, max: 130, step: 1, unit: 'мм', price: (e) => Math.max(0, e.throttle - 50) * 15 },
  ] },
  { id: 'boost', title: 'Наддув и закись азота', icon: 'turbo', fields: [
    { k: 'induction', label: 'Наддув', opts: [
      ['na', 'Атмосферный', 0, 'Без наддува'], ['turbo', 'Турбина', 0, 'Энергия выхлопа. Есть турбояма'],
      ['eturbo', 'Электротурбина', 3000, 'Турбина с электромотором: почти без ямы'],
      ['sc_roots', 'Компрессор Roots', 4500, 'Наддув с холостых, сильно греет воздух'],
      ['sc_screw', 'Винтовой компрессор', 6000, 'Как Roots, но эффективнее'],
      ['sc_cent', 'Центробежный компрессор', 4000, 'Наддув растёт с оборотами'],
      ['twincharge', 'Компрессор + турбина', 5000, 'Компрессор внизу, турбина наверху'],
    ] },
    { k: 'turboLayout', label: 'Схема турбин', show: hasTurbo, opts: [
      ['single', 'Одна', 0, ''], ['twin', 'Две параллельно', 800, 'Каждая на свой ряд'], ['sequential', 'Последовательные', 2000, 'Маленькая внизу, большая наверху'],
      ['quad', 'Четыре', 3000, 'Как у гиперкаров'],
    ] },
    { k: 'turboSize', label: 'Размер компрессора', type: 'range', min: 36, max: 110, step: 1, unit: 'мм', show: hasTurbo, price: perT(0), cost: (e) => (800 + (e.turboSize - 36) * 45) * turboCount(e),
      hint: 'Маленькая — раньше выходит на наддув, большая — даёт больше воздуха на верхах' },
    { k: 'bearing', label: 'Подшипник турбины', show: hasTurbo, opts: [['journal', 'Скольжения', 0, ''], ['ball', 'Шариковый', perT(900), 'Раскручивается на 15% раньше']] },
    { k: 'wastegate', label: 'Вестгейт', show: hasTurbo, opts: [['internal', 'Встроенный', 0, ''], ['external', 'Внешний', 700, 'Точнее держит наддув']] },
    { k: 'intercooler', label: 'Интеркулер', show: isForced, opts: [
      ['none', 'Нет', 0, 'Горячий воздух — меньше мощности, больше детонации'], ['air', 'Воздух-воздух', 900, ''],
      ['big', 'Большой фронтальный', 1600, ''], ['water', 'Водяной', 2200, 'Лучшее охлаждение'],
    ] },
    { k: 'turboAR', label: 'A/R горячей части', show: hasTurbo, opts: [[0.63, '0.63', 0, 'Быстрый выход на наддув, душит верха'], [0.82, '0.82', 0, 'Универсально'], [1.06, '1.06', perT(300), 'Поздний наддув, но свободный выдох']] },
    { k: 'exhWrap', label: 'Термообмотка коллектора', type: 'toggle', show: hasTurbo, price: 250, hint: 'Сохраняет тепло выхлопа: турбина раскручивается чуть раньше' },
    { k: 'waterMeth', label: 'Впрыск воды и метанола', type: 'toggle', show: isForced, price: 1800, hint: 'Охлаждает заряд и подавляет детонацию под наддувом' },
    { k: 'bov', label: 'Перепускной клапан (BOV)', type: 'toggle', show: hasTurbo, price: 250, hint: 'Тот самый «пшик» при сбросе газа' },
    { k: 'antilag', label: 'Антилаг', type: 'toggle', show: (e) => hasTurbo(e) && standalone(e), price: 2000, hint: 'Держит турбину раскрученной: стрельба из выхлопа' },
    { k: 'nitrous', label: 'Закись азота', opts: [[0, 'Нет', 0, ''], [50, '+50 л.с.', 800, ''], [100, '+100 л.с.', 1200, ''], [150, '+150 л.с.', 1600, ''], [250, '+250 л.с.', 2500, ''], [400, '+400 л.с.', 4000, ''], [800, '+800 л.с.', 7000, 'Для дрэга']] },
  ] },
  { id: 'fuel', title: 'Топливная система', icon: 'fuel', fields: [
    { k: 'fuel', label: 'Топливо', opts: Object.entries(FUELS).map(([k, f]) => [k, f.name, f.price, f.diesel ? 'Самовоспламенение от сжатия' : 'Октановое число ' + f.oct]) },
    { k: 'fuelSystem', label: 'Подача топлива', opts: (e) => isDiesel(e) ? [['di', 'Непосредственный впрыск', 2500, 'Common Rail']] : [
      ['carb', 'Карбюратор', 0, 'Просто, но смесь неточная'], ['tbi', 'Моновпрыск', 300, ''], ['mpi', 'Распределённый впрыск', 700, ''],
      ['di', 'Непосредственный впрыск', 2500, 'Меньше детонации, +3%'],
    ] },
    { k: 'injectors', label: 'Производительность форсунок', type: 'range', min: 120, max: 6000, step: 10, unit: 'см³/мин', show: (e) => e.fuelSystem !== 'carb',
      price: (e) => Math.round(Math.max(0, e.injectors - 200) * 0.6 * e.cyl * (isRotary(e) ? 2 : 1)) },
    { k: 'pump', label: 'Топливный насос', opts: [['stock', 'Штатный', 0, 'Примерно до 350 л.с.'], ['sport', 'Спортивный', 300, '~600 л.с.'], ['race', 'Гоночный', 800, '~1000 л.с.'], ['dual', 'Два гоночных', 1600, '~1800 л.с.'], ['mech', 'Механический (дрэг)', 5000, 'Для тысяч сил на метаноле']] },
  ] },
  { id: 'ecu', title: 'Зажигание и ЭБУ', icon: 'ecu', tune: true, fields: [
    { k: 'ecu', label: 'Блок управления', opts: [
      ['stock', 'Штатный', 0, 'Отсечка до 7500, наддув до 1 бар'], ['piggy', 'Доп. модуль', 800, 'Отсечка до 9500, наддув до 2 бар'],
      ['standalone', 'Программируемый', 2500, 'Без ограничений, антилаг, лаунч'],
    ] },
    { k: 'plugs', label: 'Свечи', show: (e) => !isDiesel(e), opts: [['stock', 'Штатные', 0, ''], ['iridium', 'Иридиевые «холодные»', pc(25), 'Меньше детонации и пропусков']] },
    { k: 'coils', label: (e) => isDiesel(e) ? 'Свечи накала' : 'Катушки зажигания', opts: [['stock', 'Штатные', 0, 'Пропуски при наддуве > 1,4 бар'], ['race', 'Усиленные', pc(80), '']] },
    { k: 'timing', label: (e) => isDiesel(e) ? 'Опережение впрыска' : 'Угол опережения', type: 'range', tune: true, min: 0, max: 45, step: 0.5, unit: '°',
      hint: 'Ближе к оптимуму — больше мощность. Слишком рано — детонация' },
    { k: 'lambda', label: 'Состав смеси (λ)', type: 'range', tune: true, min: (e) => isDiesel(e) ? 1.05 : e.ecu === 'stock' ? 0.8 : 0.68, max: (e) => isDiesel(e) ? 2.0 : e.ecu === 'stock' ? 1.0 : 1.3, step: 0.01, unit: '',
      fmt: (e) => 'λ ' + e.lambda.toFixed(2) + '  (AFR ' + (e.lambda * FUELS[e.fuel].stoich).toFixed(1) + ')',
      hint: 'Меньше 1 — богатая (прохладнее, безопаснее), больше 1 — бедная (горячо)' },
    { k: 'boost', label: 'Давление наддува', type: 'range', tune: true, show: isForced, min: 0.1, max: (e) => e.ecu === 'stock' ? 1.0 : e.ecu === 'piggy' ? 2.0 : 5.0, step: 0.05, unit: 'бар' },
    { k: 'limiter', label: 'Отсечка', type: 'range', tune: true, min: 3000, max: (e) => e.ecu === 'stock' ? 7500 : e.ecu === 'piggy' ? 9500 : 22000, step: 100, unit: 'об/мин' },
    { k: 'idle', label: 'Холостой ход', type: 'range', tune: true, min: 500, max: 1800, step: 25, unit: 'об/мин' },
    { k: 'launch', label: 'Лаунч-контроль', type: 'range', tune: true, show: standalone, min: 2000, max: 9000, step: 100, unit: 'об/мин', hint: 'Обороты удержания на старте' },
    { k: 'knockSensor', label: 'Защита от детонации', type: 'toggle', tune: true, price: 0, hint: 'ЭБУ сам убирает угол при детонации: безопасно, но теряется мощность' },
  ] },
  { id: 'exhaust', title: 'Выпуск', icon: 'exhaust', fields: [
    { k: 'exhManifold', label: 'Выпускной коллектор', opts: [['cast', 'Литой', 0, ''], ['tubular', 'Трубчатый «паук»', 700, ''], ['equal', 'Равнодлинный', 1500, '']] },
    { k: 'exhDia', label: 'Диаметр трубы', type: 'range', min: 35, max: 120, step: 1, unit: 'мм', price: (e) => Math.max(0, e.exhDia - 45) * 20 },
    { k: 'exhLayout', label: 'Схема выпуска', opts: [['single', 'Одна труба', 0, ''], ['dual', 'Две трубы', 900, '+35% пропускной способности у рядных']] },
    { k: 'cat', label: 'Катализатор', type: 'toggle', price: 0, hint: 'Без него громче и свободнее, но не для города' },
    { k: 'muffler', label: 'Глушитель', opts: [['stock', 'Штатный', 0, 'Тихо'], ['sport', 'Спортивный', 400, ''], ['straight', 'Прямоток', 600, 'Громко'], ['none', 'Прямая труба', 0, 'Очень громко']] },
  ] },
  { id: 'cooling', title: 'Охлаждение и смазка', icon: 'cool', fields: [
    { k: 'radiator', label: 'Радиатор', opts: [['stock', 'Штатный', 0, 'Хватит примерно на 180 кВт'], ['large', 'Увеличенный', 500, '~330 кВт'], ['race', 'Гоночный', 1300, '~550 кВт']] },
    { k: 'fan', label: 'Вентилятор', opts: [['mech', 'С вискомуфтой', 0, ''], ['electric', 'Электрический', 350, '+8% к охлаждению'], ['dual', 'Два электрических', 700, '+16%']] },
    { k: 'thermostat', label: 'Термостат', opts: [['stock', 'Штатный 88°C', 0, ''], ['low', 'Низкотемпературный 75°C', 150, 'Холоднее мотор, меньше детонации']] },
    { k: 'oilCooler', label: 'Масляный радиатор', type: 'toggle', price: 450, hint: '+15% к охлаждению' },
    { k: 'oilSystem', label: 'Система смазки', opts: [['wet', 'Мокрый картер', 0, ''], ['dry', 'Сухой картер', 2800, 'Меньше трения, ниже посадка']] },
    { k: 'oilPump', label: 'Масляный насос', opts: [['stock', 'Штатный', 0, 'Выше 8500 об/мин может не хватить давления'], ['high', 'Производительный', 600, 'Для высоких оборотов']] },
    { k: 'oil', label: 'Масло', opts: [['std', '10W-40', 0, ''], ['race', 'Гоночное 0W-20', 200, '-4% трения']] },
  ] },
  { id: 'look', title: 'Внешний вид', icon: 'paint', fields: [
    { k: 'coverColor', label: 'Цвет крышки клапанов', type: 'color', tune: true, opts: [
      ['black', 'Чёрный', 0], ['red', 'Красный', 0], ['silver', 'Серебро', 0], ['blue', 'Синий', 0], ['gold', 'Золото', 0], ['green', 'Зелёный', 0], ['orange', 'Оранжевый', 0], ['purple', 'Фиолетовый', 0],
    ] },
  ] },
];

const COVER_COLORS = { black: '#24262b', red: '#b8262a', silver: '#a9b0b9', blue: '#2554b8', gold: '#c99a2e', green: '#2f7d47', orange: '#d8661f', purple: '#6b3aa8' };

function optList(f, e) { return typeof f.opts === 'function' ? f.opts(e) : f.opts; }
function val(x, e) { return typeof x === 'function' ? x(e) : x; }

// Стоимость «железа» двигателя
function engineCost(e) {
  let sum = 1500 + e.cyl * 300 * (isRotary(e) ? 0 : 1);
  for (const sec of ENGINE_SPEC) for (const f of sec.fields) {
    if (f.show && !f.show(e)) continue;
    if (f.k === 'cyl') { const o = optList(f, e).find(o => o[0] === e.cyl); sum += o ? val(o[2], e) - (isRotary(e) ? 0 : e.cyl * 300) : 0; continue; }
    if (f.opts && f.type !== 'color') {
      const o = optList(f, e).find(o => o[0] === e[f.k]);
      if (o) sum += val(o[2], e) || 0;
    } else if (f.type === 'range') {
      sum += val(f.cost || f.price || 0, e) || 0;
    } else if (f.type === 'toggle') {
      if (e[f.k]) sum += val(f.price || 0, e) || 0;
    }
  }
  return Math.max(500, Math.round(sum / 10) * 10);
}

// Применение зависимостей при смене детали
function fixEngine(e, changed) {
  const opts = CYL_OPTIONS[e.type];
  if (!opts.includes(e.cyl)) e.cyl = opts.reduce((a, b) => Math.abs(b - e.cyl) < Math.abs(a - e.cyl) ? b : a);
  if (changed === 'type') {
    if (e.type === 'rotary') { e.cyl = 2; e.limiter = Math.max(e.limiter, 8500); e.cr = Math.min(e.cr, 10); e.camDur = 240; }
    if (e.type === 'vr') e.vAngle = 15;
    if (e.type === 'w') e.vAngle = 72;
    if (e.type === 'v' && e.vAngle < 45) e.vAngle = 90;
    if (e.type === 'radial') { e.valvetrain = 'ohv'; e.valves = 2; }
  }
  if (isRotary(e)) e.cycle = 4;
  if (e.valvetrain === 'ohv') e.valves = 2;
  if (e.valvetrain === 'sohc' && e.valves === 5) e.valves = 4;
  if (e.valvetrain === 'dohc' && e.valves === 3) e.valves = 4;
  if (e.valvetrain !== 'dohc' && e.springs === 'pneumatic') e.springs = 'race';
  if (changed === 'fuel') {
    if (isDiesel(e)) { e.cr = Math.max(e.cr, 17); e.lambda = 1.3; e.timing = 14; e.fuelSystem = 'di'; e.limiter = Math.min(e.limiter, 5000); }
    else if (e.cr > 14) { e.cr = 10; e.lambda = 0.88; e.timing = 28; e.limiter = Math.max(e.limiter, 6500); }
    else if (e.lambda > 1.3) { e.lambda = 0.88; e.timing = 28; }
  }
  if (isDiesel(e)) e.fuelSystem = 'di';
  if (!hasTurbo(e)) { e.antilag = false; }
  if (!standalone(e)) e.antilag = false;
  const rl = Math.ceil(e.stroke * 1.3); if (e.rodLen < rl) e.rodLen = rl;
  // ограничения настроек
  for (const sec of ENGINE_SPEC) for (const f of sec.fields) if (f.type === 'range') {
    const mn = val(f.min, e), mx = val(f.max, e);
    e[f.k] = clamp(e[f.k], mn, mx);
  }
  return e;
}

// ---------- Шаблоны ----------
const PRESETS = [
  { name: 'Городской 1.6 16V', p: {} },
  { name: 'Старый «копейка» 1.3', p: { bore: 79, stroke: 66, valvetrain: 'sohc', valves: 2, cr: 8.5, fuel: 'ai92', fuelSystem: 'carb', limiter: 6000, camDur: 232, camLift: 9, head: 'alu', coverColor: 'silver' } },
  { name: 'Турбо R4 2.0 (спорт)', p: { bore: 86, stroke: 86, block: 'alu', deck: 'closed', crank: 'forged', rods: 'forged', pistons: 'forged', cr: 9.0, induction: 'turbo', turboSize: 58, bearing: 'ball', intercooler: 'big', boost: 1.4, ecu: 'piggy', fuel: 'ai98', injectors: 750, pump: 'sport', timing: 18, lambda: 0.8, limiter: 7200, exhManifold: 'tubular', exhDia: 76, muffler: 'sport', camDur: 252, camLift: 10.5, vvt: 'dual', springs: 'sport', radiator: 'large', coverColor: 'red' } },
  { name: 'Высокооборотный R4 2.0', p: { lifters: 'solid', oilPump: 'high', bearings: 'race', damper: 'fluid', bore: 87, stroke: 84, block: 'alu', crank: 'forged', rods: 'titanium', pistons: 'forged', cr: 12.5, fuel: 'race', camDur: 290, camLift: 13, vvt: 'vtec', springs: 'race', manifold: 'itb', throttle: 48, porting: 'stage2', ecu: 'standalone', limiter: 9800, timing: 34, exhManifold: 'equal', exhDia: 63, muffler: 'straight', cat: false, injectors: 440, flywheel: 'light', coverColor: 'red' } },
  { name: 'Рядная шестёрка 3.0 твин-турбо', p: { headGasket: 'mls', plugs: 'iridium', fan: 'electric', exhLayout: 'dual', cyl: 6, bore: 86, stroke: 86, block: 'iron', deck: 'closed', crank: 'forged', rods: 'forged', pistons: 'forged', cr: 8.5, induction: 'turbo', turboLayout: 'sequential', turboSize: 58, intercooler: 'big', boost: 1.6, ecu: 'standalone', fuel: 'ai98', injectors: 1000, pump: 'race', timing: 16, lambda: 0.78, limiter: 7200, exhManifold: 'tubular', exhDia: 80, muffler: 'sport', camDur: 256, camLift: 10.8, vvt: 'dual', springs: 'sport', radiator: 'large', coils: 'race', coverColor: 'silver' } },
  { name: 'V8 5.7 OHV (маслкар)', p: { type: 'v', cyl: 8, vAngle: 90, bore: 101.6, stroke: 88.4, rodLen: 157, valvetrain: 'ohv', valves: 2, camDur: 262, camLift: 12, block: 'iron', cr: 10.2, fuelSystem: 'carb', fuel: 'ai98', limiter: 6300, exhManifold: 'tubular', exhDia: 64, muffler: 'sport', cat: false, timing: 32, coverColor: 'orange', flywheel: 'heavy' } },
  { name: 'Плоский V8 4.5', p: { oilPump: 'high', lifters: 'roller', damper: 'fluid', type: 'v', cyl: 8, vAngle: 90, crankPlane: 'flat', bore: 94, stroke: 81, block: 'alu', crank: 'forged', rods: 'titanium', pistons: 'forged', cr: 12.5, camDur: 270, camLift: 12, vvt: 'dual', springs: 'race', fuelSystem: 'di', fuel: 'ai98', injectors: 400, pump: 'sport', ecu: 'standalone', limiter: 9000, timing: 30, exhManifold: 'equal', exhDia: 70, muffler: 'sport', oilSystem: 'dry', flywheel: 'light', coverColor: 'red', porting: 'stage1', manifold: 'short' } },
  { name: 'Оппозитник 2.5 турбо', p: { type: 'boxer', cyl: 4, bore: 99.5, stroke: 79, valvetrain: 'dohc', block: 'alu', crank: 'forged', rods: 'forged', pistons: 'forged', cr: 8.2, induction: 'turbo', turboSize: 60, intercooler: 'air', boost: 1.3, ecu: 'piggy', fuel: 'ai98', injectors: 850, pump: 'sport', timing: 16, lambda: 0.8, limiter: 7000, exhManifold: 'equal', exhDia: 76, muffler: 'sport', coverColor: 'blue' } },
  { name: 'Роторный 13B турбо', p: { oilPump: 'high', fan: 'electric', type: 'rotary', cyl: 2, rotorVol: 654, crank: 'forged', pistons: 'forged', cr: 9.0, induction: 'turbo', turboLayout: 'sequential', turboSize: 54, intercooler: 'big', boost: 1.0, ecu: 'standalone', fuel: 'ai98', injectors: 850, pump: 'race', timing: 18, lambda: 0.8, limiter: 9000, camDur: 260, porting: 'stage1', exhDia: 76, muffler: 'straight', cat: false, coverColor: 'silver' } },
  { name: 'V12 6.5 атмо', p: { oilPump: 'high', lifters: 'roller', fan: 'dual', exhLayout: 'dual', type: 'v', cyl: 12, vAngle: 65, bore: 95, stroke: 76.4, block: 'alu', crank: 'forged', rods: 'titanium', pistons: 'forged', cr: 13.5, camDur: 268, camLift: 12, vvt: 'dual', springs: 'race', fuelSystem: 'mpi', fuel: 'race', injectors: 420, pump: 'race', ecu: 'standalone', limiter: 9200, timing: 30, exhManifold: 'equal', exhDia: 76, muffler: 'sport', oilSystem: 'dry', flywheel: 'light', radiator: 'race', coverColor: 'red', porting: 'stage1', manifold: 'variable' } },
  { name: 'W16 8.0 квад-турбо', p: { headGasket: 'mls', headStuds: 'arp', plugs: 'iridium', fan: 'dual', exhLayout: 'dual', type: 'w', cyl: 16, vAngle: 90, bore: 86, stroke: 86, block: 'alu', deck: 'closed', crank: 'billet', rods: 'titanium', pistons: 'forged', cr: 9.0, induction: 'turbo', turboLayout: 'quad', turboSize: 56, bearing: 'ball', intercooler: 'water', boost: 1.5, ecu: 'standalone', fuel: 'ai98', fuelSystem: 'mpi', injectors: 1000, pump: 'dual', timing: 15, lambda: 0.78, limiter: 7100, exhManifold: 'tubular', exhDia: 100, muffler: 'sport', radiator: 'race', oilCooler: true, oilSystem: 'dry', coils: 'race', coverColor: 'black', camDur: 250 } },
  { name: 'Дизель R6 3.0 турбо', p: { headGasket: 'mls', headStuds: 'arp', cyl: 6, bore: 84, stroke: 90, rodLen: 135, block: 'cgi', deck: 'closed', crank: 'forged', rods: 'forged', pistons: 'forged', cr: 16.5, induction: 'turbo', turboLayout: 'sequential', turboSize: 56, intercooler: 'big', boost: 1.8, ecu: 'piggy', fuel: 'diesel', fuelSystem: 'di', injectors: 600, pump: 'sport', timing: 14, lambda: 1.25, limiter: 4800, idle: 750, exhDia: 70, camDur: 228, coverColor: 'black' } },
  { name: 'Звезда 9 цил. (авиа)', p: { type: 'radial', cyl: 9, bore: 146 > 135 ? 135 : 146, stroke: 135, rodLen: 230, valvetrain: 'ohv', valves: 2, cr: 7.0, fuel: 'race', fuelSystem: 'carb', induction: 'sc_cent', boost: 0.6, intercooler: 'none', limiter: 2700, idle: 600, camDur: 236, block: 'alu', head: 'alu', exhDia: 90, muffler: 'none', cat: false, timing: 26, flywheel: 'heavy', coverColor: 'silver', throttle: 110 } },
  { name: 'Двухтактный R3 1.0', p: { cyl: 3, cycle: 2, bore: 70, stroke: 64, rodLen: 120, cr: 7.5, fuel: 'ai98', fuelSystem: 'carb', tunedPipe: true, porting: 'stage1', limiter: 8500, ecu: 'piggy', exhDia: 45, muffler: 'none', cat: false, timing: 18, coverColor: 'blue' } },
  { name: 'Дрэг V8 8.2 компрессор', p: { headGasket: 'copper', headStuds: 'arp', bearings: 'race', damper: 'fluid', lifters: 'solid', waterMeth: false, plugs: 'iridium', type: 'v', cyl: 8, vAngle: 90, bore: 110, stroke: 108, rodLen: 165, valvetrain: 'ohv', valves: 2, camDur: 290, camLift: 15, springs: 'race', block: 'billet', deck: 'closed', crank: 'billet', rods: 'billet', pistons: 'billet', cr: 7.5, induction: 'sc_roots', boost: 2.4, intercooler: 'none', fuel: 'meth', fuelSystem: 'mpi', injectors: 5500, pump: 'mech', ecu: 'standalone', timing: 30, lambda: 0.75, limiter: 7800, exhManifold: 'tubular', exhDia: 110, muffler: 'none', cat: false, nitrous: 0, oilSystem: 'dry', coils: 'race', radiator: 'race', coverColor: 'gold', porting: 'stage2', throttle: 130, manifold: 'short', flywheel: 'light' } },
];

function makePreset(i) {
  const e = Object.assign(defaultEngine(), JSON.parse(JSON.stringify(PRESETS[i].p)));
  e.name = PRESETS[i].name;
  if (e.rodLen < e.stroke * 1.3 || !PRESETS[i].p.rodLen) e.rodLen = Math.round(e.stroke * 1.65);
  if (e.type === 'radial') e.rodLen = Math.round(e.stroke * 1.7);
  if (!PRESETS[i].p.throttle) {
    const disp = displacement(e) * (isRotary(e) ? 2 : 1) * (e.cycle === 2 ? 1.6 : 1);
    const n = e.manifold === 'itb' ? e.cyl : 1;
    e.throttle = clamp(Math.round(Math.sqrt(disp * e.limiter / 2 * (isForced(e) ? 1.6 : 1) / (3.4 * 0.85 * n))), 28, 130);
  }
  fixEngine(e);
  autoTune(e);
  return e;
}

// ---------- Заказы ----------
// d — последний замер на стенде (peak hp, nm, rpm), e — двигатель
const CONTRACTS = [
  { id: 'taxi', title: 'Мотор для такси', text: 'От 110 л.с., масса до 140 кг, на АИ-92', reward: 3000, check: (e, d) => d.hp >= 110 && engineMass(e) <= 140 && e.fuel === 'ai92' },
  { id: 'eco', title: 'Экономичная малолитражка', text: 'Объём до 1,2 л, от 90 л.с., с катализатором', reward: 4000, check: (e, d) => displacement(e) <= 1.2 && d.hp >= 90 && e.cat },
  { id: 'pickup', title: 'Тяговитый пикап', text: 'От 400 Н·м, пик момента не выше 3500 об/мин', reward: 5000, check: (e, d) => d.nm >= 400 && d.nmRpm <= 3500 },
  { id: 'moto', title: 'Мотор для мотоцикла', text: 'Объём до 1,0 л, от 140 л.с.', reward: 7000, check: (e, d) => displacement(e) <= 1.0 && d.hp >= 140 },
  { id: 'truck', title: 'Дизель для тягача', text: 'Дизель, от 900 Н·м', reward: 8000, check: (e, d) => isDiesel(e) && d.nm >= 900 },
  { id: 'v8', title: 'Классический маслкар', text: 'V8 со штангами (OHV), от 400 л.с.', reward: 8000, check: (e, d) => e.type === 'v' && e.cyl === 8 && e.valvetrain === 'ohv' && d.hp >= 400 },
  { id: 'na', title: 'Атмосферный рекорд', text: 'Без наддува, от 120 л.с. с литра', reward: 9000, check: (e, d) => !isForced(e) && !e.nitrous && d.hp / displacement(e) >= 120 },
  { id: 'twostroke', title: 'Двухтактный зверь', text: '2-тактный, от 250 л.с.', reward: 9000, check: (e, d) => e.cycle === 2 && !isRotary(e) && d.hp >= 250 },
  { id: 'rotary', title: 'Ротор жужжит', text: 'Роторный, от 350 л.с.', reward: 10000, check: (e, d) => isRotary(e) && d.hp >= 350 },
  { id: 'rally', title: 'Оппозитник для ралли', text: 'Оппозитный с турбиной, от 350 л.с.', reward: 10000, check: (e, d) => e.type === 'boxer' && hasTurbo(e) && d.hp >= 350 },
  { id: 'plane', title: 'Мотор для самолёта', text: 'Звездообразный, от 600 л.с.', reward: 12000, check: (e, d) => e.type === 'radial' && d.hp >= 600 },
  { id: 'light', title: 'Лёгкий и мощный', text: 'От 350 л.с., масса до 115 кг', reward: 15000, check: (e, d) => d.hp >= 350 && engineMass(e) <= 115 },
  { id: 'v12', title: 'Гран-турер V12', text: 'V12 без наддува, от 700 л.с.', reward: 18000, check: (e, d) => e.type === 'v' && e.cyl === 12 && !isForced(e) && !e.nitrous && d.hp >= 700 },
  { id: 'thousand', title: 'Тысячник', text: 'От 1000 л.с. без закиси', reward: 25000, check: (e, d) => d.hp >= 1000 && !e.nitrous },
  { id: 'w16', title: 'Гиперкар', text: 'W16, от 1600 л.с.', reward: 40000, check: (e, d) => e.type === 'w' && e.cyl === 16 && d.hp >= 1600 },
  { id: 'drag', title: 'Дрэг-монстр', text: 'От 3000 л.с.', reward: 60000, check: (e, d) => d.hp >= 3000 },
];

// ---------- Соперники в заездах на 402 м ----------
const RIVALS = [
  { name: 'Сосед на седане', hp: 95, nm: 140, mass: 1150, peak: 5500, fee: 0, prize: 800, color: '#7d8590' },
  { name: 'Студент на хэтче', hp: 140, nm: 180, mass: 1050, peak: 6500, fee: 300, prize: 1800, color: '#3a7bd5' },
  { name: 'Таксист Гоша', hp: 200, nm: 300, mass: 1300, peak: 5800, fee: 800, prize: 3500, color: '#e8c33a' },
  { name: 'Клуб «Турбо»', hp: 330, nm: 450, mass: 1300, peak: 6200, fee: 1500, prize: 6500, color: '#2f9e5a' },
  { name: 'Маслкар Палыча', hp: 480, nm: 620, mass: 1550, peak: 5800, fee: 3000, prize: 11000, color: '#d8661f' },
  { name: 'Уличный король', hp: 700, nm: 800, mass: 1400, peak: 6800, fee: 6000, prize: 20000, color: '#7a3ad8' },
  { name: 'Гиперкар', hp: 1200, nm: 1400, mass: 1600, peak: 6800, fee: 12000, prize: 40000, color: '#e0e4ea' },
  { name: 'Топ-фьюел дрэгстер', hp: 3500, nm: 3500, mass: 1000, peak: 7500, fee: 25000, prize: 90000, color: '#e33b3b' },
];
