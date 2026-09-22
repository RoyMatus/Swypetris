"""Проверка полноты плейлиста, декодирования Vorbis, клиппинга и завершений."""
import json, pathlib, subprocess, hashlib
from render_playlist import ROOT, REPORT, OUT, FFMPEG, ORDER, np
report=json.loads((REPORT/'playlist-report.json').read_text(encoding='utf-8'))
assert [r['id'] for r in report]==ORDER
checks=[]
rms_values=[]
for row in report:
 name=row['id']; path=OUT/(name+'.ogg')
 assert hashlib.sha256(path.read_bytes()).hexdigest()==row['oggSha256']
 result=subprocess.run([str(FFMPEG),'-v','error','-i',str(path),'-f','f32le','-ac','2','-ar','32000','pipe:1'],capture_output=True,check=True)
 data=np.frombuffer(result.stdout,dtype='<f4').reshape(-1,2)
 seconds=len(data)/32000
 peak=float(np.abs(data).max())
 rms=float(np.sqrt(np.mean(data*data)))
 rms_values.append(rms)
 mono=float(np.sqrt(np.mean(data.mean(axis=1)**2)))
 assert mono/rms>.7,(name,'mono cancellation')
 assert len(row['instruments'])>=12,(name,'missing instruments')
 ending=float(np.abs(data[-640:]).max())
 assert abs(seconds-row['seconds'])<.12,(name,seconds)
 assert peak<.99,(name,peak)
 assert ending<.015,(name,ending)
 if name not in ('trepak','sugar_plum'): assert 160<seconds<200
 if name=='trepak': assert row['sections'][0]['sourceMeasures']==[69]*4 and row['sections'][0]['beats']==168
 if name=='sugar_plum': assert row['sections'][0]['sourceMeasures']==[84]*22
 checks.append(dict(id=name,decodedSeconds=seconds,peak=peak,rms=rms,monoRms=mono,last20msPeak=ending))
 print(name,round(seconds,2),'s; peak',round(peak,3),'end',round(ending,5))
assert 20*np.log10(max(rms_values)/min(rms_values))<3,'Track loudness spread exceeds 3dB'
(REPORT/'decoded-validation.json').write_text(json.dumps(checks,indent=2),encoding='utf-8')
