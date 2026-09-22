"""Собственный детерминированный ретро-синтез полного плейлиста Swypetris.
Зависимость: numpy==2.3.5. FFmpeg 8.1.2 (закреплённый архив) кодирует Ogg Vorbis.
"""
import pathlib, json, math, wave, subprocess, hashlib, argparse, sys
ROOT = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / 'bin/python_libs'))
import numpy as np
PROJECT = ROOT.parent.parent
OUT = PROJECT / 'app/src/main/res/raw'
REPORT = PROJECT / 'app/build/reports/audio'
RATE = 32000
FFMPEG = ROOT / 'bin/ffmpeg-8.1.2-essentials_build/bin/ffmpeg.exe'
ORDER = ['korobeiniki','kalinka','kamarinskaya','barynya','svetit_mesyat','vo_sadu','trepak','sugar_plum']


class Synth:
    """Полосоограниченные импульсы, FM, треугольный бас и мягкие колокольчики."""
    def __init__(self, seconds):
        self.buffer = np.zeros((int(seconds*RATE)+1,2), np.float32)
        self.cache = {}
        self.instruments = set()

    def note(self, start, duration, pitch, gain=.16, voice='pulse', pan=0):
        """Добавляет ноту с плавной атакой и затухающим хвостом без циклического переноса."""
        if pitch <= 0: return
        self.instruments.add(voice)
        duration = round(max(.025,duration),4)
        key = (pitch,duration,voice)
        sample = self.cache.get(key)
        if sample is None:
            release = .11 if voice not in ('bell','soft') else .36
            t = np.arange(int((duration+release*5)*RATE),dtype=np.float64)/RATE
            frequency = 440*2**((pitch-69)/12)
            phase = 2*np.pi*frequency*t
            if voice in ('brass','reed','epiano','glass','guitar','organ','strings'):
                vibrato = .004*np.sin(2*np.pi*5.3*t)*(1-np.exp(-t/.18))
                p = phase + phase*vibrato/np.maximum(1,t*frequency)
                if voice == 'brass':
                    sample = np.sin(p + (1.7*np.exp(-t/.16)+.35)*np.sin(p)) + .16*np.sin(2*p)
                    sample *= .65+.35*np.exp(-t/.18)
                elif voice == 'reed':
                    sample = np.sin(p)+.36*np.sin(3*p)*np.exp(-t/.45)+.10*np.sin(5*p)
                elif voice == 'epiano':
                    sample = np.sin(p+1.9*np.exp(-t/.14)*np.sin(3*p))*np.exp(-t/.65)
                elif voice == 'glass':
                    sample = (np.sin(p)+.34*np.sin(2*p)*np.exp(-t/.12)+.15*np.sin(4*p))*np.exp(-t/.35)
                elif voice == 'guitar':
                    sample = np.sin(p+2.5*np.exp(-t/.055)*np.sin(2*p))*np.exp(-t/.20)
                elif voice == 'organ':
                    sample = (np.sin(p)+.25*np.sin(2*p)+.16*np.sin(3*p))*np.exp(-t/.55)
                else:
                    sample = (np.sin(p)+.24*np.sin(p*1.003)+.20*np.sin(2*p))*np.exp(-t/.7)
            elif voice == 'fm':
                sample = np.sin(phase+(.3+.9*np.exp(-t/.15))*np.sin(2*phase))*.85
            elif voice == 'bell':
                sample = (np.sin(phase)+.25*np.sin(phase*2)+.13*np.sin(phase*3)) * np.exp(-t/.65)
            elif voice == 'soft':
                sample = np.sin(phase)+.18*np.sin(phase*2)+.06*np.sin(phase*3)
            elif voice == 'bass':
                sample = sum(((-1)**((n-1)//2))*np.sin(n*phase)/(n*n) for n in (1,3,5,7))
            else:
                duty = .25 if voice in ('pulse25','pluck') else .5
                sample = sum(np.sin(np.pi*n*duty)*np.cos(n*phase)*np.exp(-.16*(n-1))/n
                             for n in range(1,10) if n*frequency < 5000)
                if voice == 'pluck': sample *= np.exp(-t/.19)
            attack = .012 if voice != 'soft' else .035
            envelope = (1-np.exp(-t/attack))*np.exp(-np.maximum(0,t-duration)/release)
            envelope *= np.minimum(1,(len(t)-1-np.arange(len(t)))/(RATE*.025))
            sample = (sample*envelope).astype(np.float32)
            if len(self.cache) < 2500: self.cache[key] = sample
        start = round(start*RATE)
        count = min(len(sample),len(self.buffer)-start)
        if count <= 0: return
        gains = np.array([math.sqrt((1-pan)/2),math.sqrt((1+pan)/2)],np.float32)*gain
        self.buffer[start:start+count] += sample[:count,None]*gains

    def drum(self,start,kind,gain):
        """Раздельные FM-барабаны, хэты, томы и короткие тарелки с фиксированным шумом."""
        self.instruments.add(kind)
        duration = .7 if kind=='crash' else .35 if kind in ('openhat','tomlow','tomhigh') else .22
        t=np.arange(int(RATE*duration))/RATE
        rng=np.random.default_rng(713+int(start*1000))
        noise=rng.uniform(-1,1,len(t))
        high=noise-np.convolve(noise,np.ones(9)/9,mode='same')
        if kind=='kick':
            phase=2*np.pi*(48*t+2.6*(1-np.exp(-t/.018)))
            s=(np.sin(phase)*np.exp(-t/.065)+.15*high*np.exp(-t/.007))
        elif kind=='snare':
            s=(high*.55+np.sin(2*np.pi*180*t)*.35)*np.exp(-t/.055)
        elif kind in ('tomlow','tomhigh'):
            f=95 if kind=='tomlow' else 145
            s=np.sin(2*np.pi*(f*t+1.3*(1-np.exp(-t/.025))))*np.exp(-t/.095)
        elif kind=='clap':
            s=high*sum(np.exp(-np.maximum(0,t-d)/.025)*(t>=d) for d in (0,.009,.018))*.30
        else:
            decay={'hat':.017,'openhat':.065,'crash':.17}[kind]
            metallic=sum(np.sin(2*np.pi*f*t) for f in (3701,4871,6173))/3
            s=(high*.60+metallic*.20)*np.exp(-t/decay)
        s*=(1-np.exp(-t/.001))*gain
        at=round(start*RATE); count=min(len(s),len(self.buffer)-at)
        if count>0: self.buffer[at:at+count] += s[:count,None]*.707

    def finish(self):
        """Добавляет короткие отражения, нормализацию с запасом и плавное завершение."""
        dry=self.buffer.copy()
        for seconds,gain in [(0.073,.10),(.113,.08),(.181,.06),(.277,.04)]:
            delay=int(seconds*RATE)
            self.buffer[delay:] += dry[:-delay,::-1]*gain
        peak=float(np.max(np.abs(self.buffer)))
        rms=float(np.sqrt(np.mean(self.buffer*self.buffer)))
        self.buffer *= min(.76/max(peak,.001), .13/max(rms,.001))
        n=int(RATE*.04); self.buffer[:n] *= np.linspace(0,1,n)[:,None]
        n=int(RATE*.8); self.buffer[-n:] *= np.linspace(1,0,n)[:,None]
        return self.buffer


# Собственные гармонические карты по фразам, а не выбор аккорда по наиболее частой ноте.
PROFILES = {
 'korobeiniki': dict(chords='Am E Am Am Dm Am E Am', span=4, leads=['brass','reed','fm','epiano'], comp='guitar', groove=0),
 'kalinka': dict(chords='Dm A7 Dm A7 Dm A7 Dm A7 Dm Dm F F F Gm C7 F F Gm A7 Dm', span=4, leads=['reed','brass','fm','organ'], comp='epiano', groove=1),
 'kamarinskaya': dict(chords='G D7 G G C D7 G C D7 G D7 G G D7 G Em D7 G', span=4, leads=['guitar','reed','brass','fm'], comp='organ', groove=2),
 'barynya': dict(chords='D7 G D7 G D7 G D7 G C G D7 G C G D7 G', span=2, leads=['brass','fm','reed','guitar'], comp='organ', groove=3),
 'svetit_mesyat': dict(chords='D D A7 A7 D D A7 A7 Bm Bm A A Bm Bm A A', span=2, leads=['epiano','reed','glass','brass'], comp='strings', groove=4),
 'vo_sadu': dict(chords='Am G Am B7 Em D Em B7', span=2, leads=['glass','guitar','epiano','reed'], comp='epiano', groove=5)
}
ROOTS={'C':0,'D':2,'E':4,'F':5,'G':7,'A':9,'B':11}


def chord_at(profile,at):
    """Возвращает корень и гармонические ступени явно записанной последовательности."""
    labels=profile['chords'].split(); label=labels[int(at/profile['span'])%len(labels)]
    root=ROOTS[label[0]]
    intervals=[0,3 if 'm' in label else 4,7]+([10] if '7' in label else [])
    return root,intervals


def rhythm(synth,offset,beats,beat,energy,groove):
    """Четыре рисунка грува, восьмые хэты и переходы через 4/8 двухдольных тактов."""
    for at in np.arange(0,beats,.5):
        bar=int(at//4); pos=at%4
        if pos in (0,2) or (energy>1 and pos==3.5 and (bar+groove)%2):
            synth.drum((offset+at)*beat,'kick',.20 if energy else .12)
        if energy and pos in (1,3):
            synth.drum((offset+at)*beat,'snare',.115)
            if groove in (1,3) and bar%2: synth.drum((offset+at+.025)*beat,'clap',.06)
        if energy:
            kind='openhat' if pos==3.5 and bar%2 else 'hat'
            synth.drum((offset+at)*beat,kind,.047 if pos%1 else .028)
        if energy>1 and bar%2==1 and pos>=3:
            for sub in (0,.25):
                if at+sub<beats: synth.drum((offset+at+sub)*beat,'tomhigh' if pos==3 else 'tomlow',.095)
    if energy>1: synth.drum(offset*beat,'crash',.075)


def accompaniment(synth,data,beats,offset,beat,variant):
    """Индивидуальная гармония, синкопированный бас, короткие аккорды и ответные фразы."""
    profile=PROFILES[data['id']]; notes=data['notes']
    for at in np.arange(0,beats,2):
        phrase=int(at//8); energy=0 if variant<0 else 1+(phrase+variant)%3
        root,tones=chord_at(profile,at); bass=36+root
        if bass>43: bass-=12
        next_root,_=chord_at(profile,at+2)
        pattern=[(0,0,.7),(1,7,.45)] if energy==0 else (
            [(0,0,.42),(.75,12,.20),(1,7,.40),(1.75,next_root-root,.18)] if (phrase+profile['groove'])%2
            else [(0,0,.5),(.5,7,.25),(1,12,.32),(1.5,7,.28)])
        for pos,interval,dur in pattern:
            if at+pos<beats: synth.note((offset+at+pos)*beat,dur*beat,bass+interval,.18,'bass',0)
        # Короткие аккордовые удары освобождают пространство между нотами мелодии.
        for pos in ([0] if energy==0 else [.5,1.5]):
            if at+pos>=beats: continue
            for i,tone in enumerate(tones):
                synth.note((offset+at+pos)*beat,(.8 if energy==0 else .27)*beat,48+root+tone,
                    .052,profile['comp'],(i-1)*.22)
        if energy>=2 or variant<0:
            step=.25 if energy==3 else .5
            order=[0,2,1,2] if (phrase+variant)%2 else [2,1,0,1]
            for j,pos in enumerate(np.arange(0,min(2,beats-at),step)):
                synth.note((offset+at+pos)*beat,step*.66*beat,60+root+tones[order[j%4]],
                    .047,'glass' if phrase%2 else 'pluck',.42 if j%2 else -.42)
        # Ответ в свободном месте либо под длинной нотой, а не постоянное дублирование.
        if energy and (phrase+variant)%2:
            for pos in (0.5,1.0,1.5):
                local=at+pos
                occupied=any(n[0]<=local<n[0]+n[2] and n[2]<1 for n in notes)
                if not occupied and local<beats:
                    tone=tones[(int(pos*2)+phrase)%len(tones)]
                    synth.note((offset+local)*beat,.28*beat,60+root+tone,.075,'epiano',.30)
    rhythm(synth,offset,beats,beat,0 if variant<0 else 2,profile['groove'])


def folk(data):
    """Полные темы с развивающейся инструментовкой и отдельной разреженной серединой."""
    beat=60/data['bpm']; length=data['beats']; notes=data['notes']; profile=PROFILES[data['id']]
    rounds=max(2,round((178/beat-32)/length)); total=rounds*length+32
    synth=Synth(total*beat+1.4); sections=[]; at=0
    accompaniment(synth,data,8,at,beat,-1)
    sections.append(dict(name='Вступление',startBeat=at,beats=8)); at+=8
    for variant in range(rounds):
        if variant==rounds//2:
            accompaniment(synth,data,16,at,beat,-1)
            # Инструментальный диалог по гармонии, с паузами вместо тянущейся подложки.
            for pos in range(0,16,2):
                root,tones=chord_at(profile,pos)
                for j in range(3):
                    synth.note((at+pos+j*.5)*beat,.35*beat,60+root+tones[j],.12,
                        'epiano' if pos%4==0 else 'guitar',-.2 if pos%4==0 else .2)
            sections.append(dict(name='Инструментальный диалог',startBeat=at,beats=16)); at+=16
        used=[]
        for start,pitch,duration,_ in notes:
            voice=profile['leads'][(variant+int(start//8))%len(profile['leads'])]
            used.append(voice)
            gain=.22 if voice in ('epiano','glass','guitar') else .19
            synth.note((at+start)*beat,duration*.90*beat,pitch,gain,voice,-.13)
            if variant%3==2 and int(start//8)%2 and duration>=.5:
                synth.note((at+start)*beat,duration*.8*beat,pitch-12,.045,'strings',.22)
        accompaniment(synth,data,length,at,beat,variant)
        sections.append(dict(name=f'Полная тема, вариация {variant+1}',startBeat=at,beats=length,
            voices=sorted(set(used)),harmonies=profile['chords'])); at+=length
    accompaniment(synth,data,6,at,beat,-1)
    synth.note((at+6)*beat,1.3*beat,data['ending'],.20,'brass')
    tonic=data['ending']%12
    for pitch in [36+tonic,48+tonic,55+tonic]: synth.note((at+6)*beat,1.3*beat,pitch,.07,'epiano')
    synth.drum((at+6)*beat,'crash',.07)
    sections.append(dict(name='Каденция',startBeat=at,beats=8))
    assert at+8==total
    return synth.finish(),sections,sorted(synth.instruments)


def classical(data):
    """Полная партитура с раздельными FM-группами, передачей голосов и динамическим ритмом."""
    beat=60/data['bpm']; synth=Synth(data['beats']*beat+1.8); sugar=data['id']=='sugar_plum'
    for start,pitch,duration,part in data['notes']:
        phrase=int(start//16)
        if sugar:
            voice=('bell' if phrase%3!=1 else 'glass') if part==11 else (
                'bass' if part in (15,16,20,21) else 'guitar' if part>=12 else ('reed','epiano','strings','brass')[part%4])
            gain=.16 if part==11 else .07 if part in (15,16,20,21) else .045 if part>=12 else .034
        else:
            voices=[('brass','fm','reed')[phrase%3], 'guitar' if phrase%2 else 'epiano','strings','bass']
            voice=voices[part]; gain=(.21,.085,.065,.16)[part]
        dynamic=(.85,1.0,.92,1.08)[phrase%4]
        synth.note(start*beat,duration*.90*beat,pitch,gain*dynamic,voice,
            0 if voice=='bass' else -.14 if part in (0,11) else (part%3-1)*.38)
    for at in range(0,int(data['beats']),8):
        energy=(1,2,0,2)[(at//8)%4] if sugar else (2,2,1,3)[(at//8)%4]
        rhythm(synth,at,min(8,data['beats']-at),beat,energy,4 if sugar else 2)
    return synth.finish(),[dict(name='Полный номер',startBeat=0,beats=data['beats'],sourceMeasures=data['sourceMeasures'])],sorted(synth.instruments)


def write_wav(path,samples,rate=RATE):
    """Записывает стерео PCM16, оставляя запас до цифрового клиппинга."""
    with wave.open(str(path),'wb') as w:
        w.setnchannels(2);w.setsampwidth(2);w.setframerate(rate)
        w.writeframes(np.round(np.clip(samples,-1,1)*32767).astype('<i2').tobytes())


def encode(source,target):
    """Закреплённый Vorbis с постоянным serial и без изменяемых метаданных."""
    subprocess.run([str(FFMPEG),'-hide_banner','-loglevel','error','-y','-i',str(source),
        '-map_metadata','-1','-fflags','+bitexact','-flags:a','+bitexact','-c:a','libvorbis','-q:a','4',
        '-serial_offset','42',str(target)],check=True)


def generate():
    """Генерирует WAV, игровые Ogg, образцы и отчёт о форме/пиках/контрольных суммах."""
    REPORT.mkdir(parents=True,exist_ok=True)
    measures=[]
    for name in ORDER:
        data=json.loads((ROOT/'scores'/(name+'.json')).read_text(encoding='utf-8'))
        samples,form,instruments=classical(data) if 'sourceMeasures' in data else folk(data)
        rate=RATE
        write_wav(REPORT/(name+'.wav'),samples)
        target=OUT/(name+'.ogg')
        encode(REPORT/(name+'.wav'),target)
        preview=samples[min(rate*6,len(samples)-rate*30):][:rate*30].copy()
        preview[:int(rate*.1)]*=np.linspace(0,1,int(rate*.1))[:,None]
        preview[-int(rate*.6):]*=np.linspace(1,0,int(rate*.6))[:,None]
        write_wav(REPORT/(name+'-preview.wav'),preview,rate)
        peak=float(np.max(np.abs(samples)))
        assert peak < .99
        assert float(np.max(np.abs(samples[-int(rate*.02):]))) < .01
        row=dict(id=name,instruments=instruments,rms=float(np.sqrt(np.mean(samples*samples))),seconds=round(len(samples)/rate,3),peak=peak,sections=form,
                 oggSha256=hashlib.sha256(target.read_bytes()).hexdigest())
        measures.append(row)
        print(name,row['seconds'],'seconds, peak',round(peak,4),'ogg',target.stat().st_size,flush=True)
    (REPORT/'playlist-report.json').write_text(json.dumps(measures,ensure_ascii=False,indent=2),encoding='utf-8')

if __name__=='__main__': generate()
