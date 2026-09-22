"""Нотные источники → открытые события: четверти, MIDI-высота, длительность, голос.
MIDI используется только как нотная запись; готовые звукозаписи не используются.
"""
import json, math, sys, pathlib, xml.etree.ElementTree as ET
ROOT = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / 'bin/python_libs'))
import mido


def midi_notes(name):
    """Извлекает только мелодический канал ABC, без чужого автоаккомпанемента."""
    midi = mido.MidiFile(ROOT / 'sources' / (name + '.mid'))
    notes = []
    for track in midi.tracks:
        tick = 0
        active = {}
        for event in track:
            tick += event.time
            if getattr(event, 'channel', -1) != 0:
                continue
            if event.type == 'note_on' and event.velocity:
                active[event.note] = tick
            elif event.type == 'note_off' or (event.type == 'note_on' and not event.velocity):
                if event.note in active:
                    start = active.pop(event.note)
                    notes.append([start/midi.ticks_per_beat, event.note, (tick-start)/midi.ticks_per_beat, 0])
    notes.sort()
    offset = notes[0][0]
    for n in notes:
        n[0] = round(n[0] - offset, 6)
        n[2] = round(n[2], 6)
    length = round(max(n[0]+n[2] for n in notes) + .06)
    return notes, length


def xml_notes(name):
    """Читает все такты, транспозиции, аккорды, связки и обе вольты Трепака."""
    root = ET.parse(ROOT / 'sources' / (name + '.xml')).getroot()
    events = []
    measure_counts = []
    steps = {'C':0,'D':2,'E':4,'F':5,'G':7,'A':9,'B':11}
    for part_index, part in enumerate(root.findall('part')):
        measures = part.findall('measure')
        measure_counts.append(len(measures))
        # Вольта 1: такт 16, вольта 2: 17. Повторяется весь первый раздел.
        order = list(range(16)) + list(range(15)) + list(range(16,69)) if name == 'trepak' else list(range(len(measures)))
        divisions = 1
        transpose = 0
        bar_beats = 2
        base = 0
        ties = {}
        for mi in order:
            measure = measures[mi]
            attrs = measure.find('attributes')
            if attrs is not None:
                if attrs.find('divisions') is not None: divisions = float(attrs.findtext('divisions'))
                if attrs.find('time') is not None:
                    bar_beats = float(attrs.findtext('time/beats'))*4/float(attrs.findtext('time/beat-type'))
                if attrs.find('transpose') is not None:
                    transpose = int(attrs.findtext('transpose/chromatic','0')) + 12*int(attrs.findtext('transpose/octave-change','0'))
            cursor = 0
            previous = 0
            maximum = 0
            for item in measure:
                if item.tag in ('backup','forward'):
                    cursor += float(item.findtext('duration'))/divisions * (-1 if item.tag=='backup' else 1)
                elif item.tag == 'note':
                    duration = float(item.findtext('duration','0'))/divisions
                    start = previous if item.find('chord') is not None else cursor
                    pitch = item.find('pitch')
                    if pitch is not None:
                        midi = 12*(int(pitch.findtext('octave'))+1)+steps[pitch.findtext('step')]+int(pitch.findtext('alter','0'))+transpose
                        # Баритоновая запись Трепака возвращается в G и в игровой верхний регистр.
                        if name == 'trepak': midi += 11 if part_index < 2 else -1
                        flags = [t.attrib['type'] for t in item.findall('tie')]
                        key = (item.findtext('voice','1'),midi)
                        if 'stop' in flags and key in ties:
                            events[ties[key]][2] += duration
                        else:
                            event = [round(base+start,6),midi,round(max(duration,.08),6),part_index]
                            events.append(event)
                            if 'start' in flags: ties[key] = len(events)-1
                        if 'stop' in flags and 'start' not in flags: ties.pop(key,None)
                    if item.find('chord') is None:
                        previous = start
                        cursor += duration
                    maximum = max(maximum,cursor)
            base += max(maximum,bar_beats)
        assert len(measures) == (69 if name=='trepak' else 84)
    return sorted(events),base,measure_counts


def build():
    """Сохраняет полные нотные данные и проверяемые сведения о форме каждой композиции."""
    out = ROOT / 'scores'
    out.mkdir(exist_ok=True)
    specs = [('kalinka','Калинка',150,2,'minor','pulse',62),
             ('kamarinskaya','Камаринская',116,7,'major','fm',67),
             ('barynya','Барыня',144,7,'major','pulse25',67),
             ('vo_sadu','Во саду ли, в огороде',132,4,'minor','pluck',64)]
    for name,title,bpm,tonic,mode,voice,ending in specs:
        notes,beats = midi_notes(name)
        data = dict(id=name,title=title,bpm=bpm,meter='2/4',tonic=tonic,mode=mode,voice=voice,ending=ending,
                    beats=beats,notes=notes,form='Полная тема с повторами из нотного источника; игровые вариации без сокращений')
        (out/(name+'.json')).write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
    # Обе полные фразы Коробейников из прежнего проверенного нотного файла.
    notes=[]; at=0
    for line in (ROOT/'melody.txt').read_text(encoding='utf-8-sig').splitlines():
        if line.startswith('#') or not line.strip(): continue
        for token in line.split():
            pitch,duration=token.split(':'); pitch=int(pitch); duration=float(duration)
            if pitch: notes.append([at,pitch,duration,0])
            at+=duration
    data=dict(id='korobeiniki',title='Коробейники',bpm=150,meter='4/4',tonic=9,mode='minor',voice='brass',ending=69,
              beats=at,notes=notes,form='Полная двухчастная народная мелодия из melody.txt')
    (out/'korobeiniki.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
    # Народная тема (раздел 1, 16 тактов) по нотам и табулатуре balalaika.org.ru.
    # Современные вариации разделов 2+ не заимствуются: вариации игры создаёт собственный аранжировщик.
    melody = '''74:1.5 76:.5 |78:1 76:.5 74:.5 |73:.5 69:.5 73:.5 74:.5 |76:1 69:1
    74:1.5 76:.5 |78:1 76:.5 74:.5 |73:.5 69:.5 73:.5 74:.5 |76:1 76:.5 0:.5
    71:1.5 73:.5 |74:1 73:.5 71:.5 |69:1 73:1 |76:1 74:.5 73:.5
    71:1.5 73:.5 |76:.5 74:.5 73:.5 71:.5 |69:1 73:1 |69:1.5 0:.5'''
    notes=[]; at=0
    for token in melody.replace('|',' ').split():
        pitch,duration=token.split(':'); duration=float(duration)
        if int(pitch): notes.append([at,int(pitch),duration*.94,0])
        at+=duration
    assert at == 32
    data=dict(id='svetit_mesyat',title='Светит месяц',bpm=124,meter='2/4',tonic=2,mode='major',voice='soft',ending=74,
              beats=32,notes=notes,form='Полная народная тема, 16 тактов; собственные вариации')
    (out/'svetit_mesyat.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
    for name,title,bpm,voice in [('trepak','Трепак',144,'fm'),('sugar_plum','Танец Феи Драже',90,'bell')]:
        notes,beats,counts=xml_notes(name)
        data=dict(id=name,title=title,bpm=bpm,meter='2/4',voice=voice,beats=beats,notes=notes,
                  sourceMeasures=counts,form='Полный номер; все партии и такты, Трепак с повтором и обеими вольтами')
        (out/(name+'.json')).write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
    for p in out.glob('*.json'):
        d=json.loads(p.read_text(encoding='utf-8'))
        print(p.name,'beats',d['beats'],'notes',len(d['notes']))

if __name__=='__main__': build()
