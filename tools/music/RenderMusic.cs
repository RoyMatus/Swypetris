using System;
using System.IO;
using System.Collections.Generic;
using System.Globalization;

/// <summary>Ретро-аранжировка с мягкими импульсами Dendy, FM-тембром Sega, басом и электронной перкуссией.</summary>
public sealed class RenderMusic {
    const int Rate = 44100;
    const double Beat = 60.0 / 150;
    readonly float[] left, right;
    readonly bool loop;

    /// <summary>Создаёт стереобуфер; в петле хвосты последних нот переходят в начало.</summary>
    RenderMusic(double seconds, bool loop) {
        left = new float[(int)Math.Round(seconds * Rate)];
        right = new float[left.Length];
        this.loop = loop;
    }

    /// <summary>Синтезирует ретро-волну без резкого фронта и гармоник выше 6 кГц.</summary>
    static double ChipTone(double phase, double frequency, int voice) {
        double sample = 0;
        if (voice == 2) {
            // Треугольный бас с быстро убывающими нечётными гармониками.
            for (int n=1; n<=7 && n*frequency<6000; n+=2)
                sample += ((n%4==1)?1:-1)*Math.Sin(n*phase)/(n*n);
        } else {
            double duty = voice == 4 ? .25 : .5;
            for (int n=1; n<=9 && n*frequency<5000; n++)
                sample += Math.Sin(Math.PI*n*duty)*Math.Cos(n*phase)*Math.Exp(-.10*(n-1))/n;
        }
        return sample;
    }

    /// <summary>Смешивает одну ноту с независимыми фазой, огибающей и панорамой.</summary>
    void Note(double start, double duration, int midi, double volume, double pan, int voice = 0) {
        double frequency = 440 * Math.Pow(2, (midi - 69) / 12.0);
        double release = voice == 1 ? 1.8 : voice == 2 ? .15 : voice == 3 ? 1.1 : voice == 4 ? .12 : .18;
        if (!loop) release = voice == 1 ? 1.8 : voice == 2 ? .45 : 1.1;
        int first = (int)Math.Round(start * Rate);
        int length = (int)((duration + release * 5) * Rate);
        double lg = Math.Sqrt((1-pan)/2), rg = Math.Sqrt((1+pan)/2);
        for (int i = 0; i < length; i++) {
            int index = first + i;
            if (!loop && index >= left.Length) break;
            index %= left.Length;
            double t = i / (double)Rate;
            double phase = 2*Math.PI*frequency*t;
            double attack = voice == 1 ? .35 : voice == 3 ? .075 : .012;
            if (!loop) attack = voice == 1 ? .35 : voice == 3 ? .075 : .012;
            double envelope = (1-Math.Exp(-t/attack));
            if (t > duration) envelope *= Math.Exp(-(t-duration)/release);
            double sample;
            if (voice == 1) {
                sample = .55*Math.Sin(phase) + .25*Math.Sin(phase*1.0018) + .12*Math.Sin(phase*2);
            } else if (voice == 2) {
                envelope *= Math.Exp(-t/2.5);
                sample = loop ? ChipTone(phase, frequency, 2) : Math.Sin(phase)+.13*Math.Sin(phase*2);
            } else if (voice == 3) {
                sample = Math.Sin(phase)+.26*Math.Sin(phase*2)+.09*Math.Sin(phase*3);
            } else {
                // Устойчивый ретро-тембр с мягкой атакой и затуханием, без биткрашинга.
                envelope *= .78 + .22*Math.Exp(-t/.18);
                sample = Math.Sin(phase) + .22*Math.Sin(phase*2)
                    + .09*Math.Exp(-t/.22)*Math.Sin(phase*3) + .035*Math.Sin(phase*4);
                sample *= .985 + .015*Math.Sin(2*Math.PI*4.2*t);
                if (loop) sample = voice == 5
                    ? .8*Math.Sin(phase + (.35+.75*Math.Exp(-t/.16))*Math.Sin(2*phase))
                        + .15*ChipTone(phase, frequency, 0)
                    : ChipTone(phase, frequency, voice);
            }
            double tail = loop ? Math.Min(1, (length - 1 - i) / (Rate * .02)) : 1;
            double value = sample*envelope*volume*tail;
            left[index] += (float)(value*lg);
            right[index] += (float)(value*rg);
        }
    }

    /// <summary>Синтезирует округлый электронный удар или приглушённый шумовой малый барабан.</summary>
    void Percussion(double start, bool kick, double volume) {
        int first = (int)Math.Round(start*Rate);
        uint noise = (uint)(first+7919);
        double filtered = 0, phase = 0;
        int length = (int)(Rate*.3);
        for (int i=0; i<length; i++) {
            double t = i/(double)Rate;
            noise = unchecked(noise*1664525u+1013904223u);
            filtered += .12*((noise/(double)uint.MaxValue*2-1)-filtered);
            phase += 2*Math.PI*(kick ? 52+100*Math.Exp(-t/.025) : 165)/Rate;
            double sound = kick ? Math.Sin(phase) : .75*filtered+.15*Math.Sin(phase)*Math.Exp(-t/.035);
            double envelope = (1-Math.Exp(-t/.004))*Math.Exp(-t/(kick ? .065 : .045))
                *Math.Min(1,(length-1-i)/(Rate*.02));
            int index = (first+i)%left.Length;
            left[index] += (float)(sound*envelope*volume*.707);
            right[index] += (float)(sound*envelope*volume*.707);
        }
    }

    /// <summary>Добавляет пульсирующий бас, арпеджио и мягкий электронный ритм в куплетах.</summary>
    void Accompany(double atBeat, double beats, int variant) {
        int[][] chords = { new[]{57,60,64}, new[]{56,59,64}, new[]{57,60,64}, new[]{57,60,64},
            new[]{57,62,65}, new[]{57,60,64}, new[]{56,59,64}, new[]{57,60,64} };
        int[] bass = {45,40,45,45,38,45,40,45};
        for (int bar=0; bar < beats/4; bar++) {
            var chord = chords[bar%8];
            double start = atBeat + bar*4;
            for (int tone=0; tone<3; tone++) {
                Note(start*Beat, 3.7*Beat, chord[tone], .012, (tone-1)*.65, 1);
            }
            Note(start*Beat, (variant < 0 ? 2.9 : .8)*Beat, bass[bar%8], .14, -.08, 2);
            if (variant < 0) {
                for (int tone=0; tone<3; tone++) Note((start+tone*.5)*Beat, 1.8*Beat, chord[tone]+12, .095, (tone-1)*.35, 4);
            } else {
                for (int beat=0; beat<4; beat++) {
                    Percussion((start+beat)*Beat, beat%2==0, beat%2==0 ? .15 : .12);
                    if (beat>0) Note((start+beat)*Beat, .75*Beat, bass[bar%8]+(beat%2==1?12:0), .1, -.08, 2);
                }
                double step = variant % 3 == 2 ? .25 : .5;
                for (int n=0; n<4/step; n++) {
                    int tone = (n + variant) % 3;
                    Note((start+n*step+.035)*Beat, step*.85*Beat, chord[tone], .075, (tone-1)*.45, 4);
                }
                if (variant >= 3) Note((start+2)*Beat, 1.2*Beat, bass[bar%8]+12, .045, .12, 2);
            }
        }
    }

    /// <summary>Исполняет полную тему, чередуя импульсный и мягкий FM-тембр, динамику и регистр.</summary>
    void Theme(List<Tuple<int,double>> melody, double startBeat, int variant) {
        double cursor = startBeat;
        int index = 0;
        foreach (var note in melody) {
            if (note.Item1 != 0) {
                double velocity = .24 + .025*Math.Cos(index*.73);
                Note(cursor*Beat, note.Item2*.96*Beat, note.Item1, velocity, -.13, variant%3==2 ? 5 : 0);
                if (variant == 3 || variant == 6)
                    Note(cursor*Beat+.018, note.Item2*.9*Beat, note.Item1-12, .045, .23);
                if (variant == 7 && note.Item2 >= 1)
                    Note(cursor*Beat+.028, note.Item2*.8*Beat, note.Item1+12, .025, .3);
            }
            cursor += note.Item2;
            index++;
        }
        Accompany(startBeat, 32, variant);
    }

    /// <summary>Создаёт мягкое стереопространство; циклические задержки не обрезаются на стыке петли.</summary>
    void Space() {
        var dryL = (float[])left.Clone(); var dryR = (float[])right.Clone();
        double[] times = {.073,.113,.181,.277,.419,.631};
        double[] gains = {.13,.11,.09,.065,.045,.027};
        for (int tap=0; tap<times.Length; tap++) {
            int delay = (int)(times[tap]*Rate);
            for (int i=0; i<left.Length; i++) {
                int source = i-delay;
                if (source<0) { if (!loop) continue; source += left.Length; }
                left[i] += (float)(dryR[source]*gains[tap]*(loop ? .65 : 1));
                right[i] += (float)(dryL[source]*gains[tap]*(loop ? .65 : 1));
            }
        }
        // Однополюсный фильтр убирает резкость, прогрев обеспечивает непрерывность петли.
        double l=0,r=0;
        if (loop) for (int i=left.Length-1000; i<left.Length; i++) { l += .32*(left[i]-l); r += .32*(right[i]-r); }
        for (int i=0; i<left.Length; i++) { l += .32*(left[i]-l); r += .32*(right[i]-r); left[i]=(float)l; right[i]=(float)r; }
    }

    /// <summary>Нормализует без ограничения пиков; короткий фрагмент получает плавные края.</summary>
    void Write(string path, double from = 0, double seconds = -1, bool fade = false) {
        double peak = .001;
        for(int i=0;i<left.Length;i++) peak=Math.Max(peak,Math.Max(Math.Abs(left[i]),Math.Abs(right[i])));
        double gain=.78/peak;
        int start=(int)(from*Rate), count=seconds<0?left.Length:(int)(seconds*Rate);
        using (var writer=new BinaryWriter(File.Create(path))) {
            writer.Write(System.Text.Encoding.ASCII.GetBytes("RIFF")); writer.Write(36+count*4);
            writer.Write(System.Text.Encoding.ASCII.GetBytes("WAVEfmt ")); writer.Write(16);
            writer.Write((short)1); writer.Write((short)2); writer.Write(Rate); writer.Write(Rate*4);
            writer.Write((short)4); writer.Write((short)16); writer.Write(System.Text.Encoding.ASCII.GetBytes("data")); writer.Write(count*4);
            for(int i=0;i<count;i++) {
                double envelope=fade?Math.Min(1,Math.Min(i/(Rate*.3),(count-1-i)/(Rate*.8))):1;
                writer.Write((short)(left[(start+i)%left.Length]*gain*envelope*32767));
                writer.Write((short)(right[(start+i)%right.Length]*gain*envelope*32767));
            }
        }
    }

    /// <summary>Читает проверяемый нотный текст, создаёт трёхминутную петлю, образец и фанфары.</summary>
    public static void Generate(string notesPath, string output, string preview) {
        var melody = new List<Tuple<int,double>>();
        double length=0;
        foreach(string line in File.ReadAllLines(notesPath)) {
            if (line.TrimStart().StartsWith("#") || String.IsNullOrWhiteSpace(line)) continue;
            foreach(string token in line.Split(new[]{' '},StringSplitOptions.RemoveEmptyEntries)) {
                var pair=token.Split(':'); double duration=double.Parse(pair[1],CultureInfo.InvariantCulture);
                melody.Add(Tuple.Create(int.Parse(pair[0]),duration)); length+=duration;
            }
        }
        if(Math.Abs(length-32)>.001) throw new Exception("Тема должна содержать 32 четверти, получено "+length);
        var music=new RenderMusic(448*Beat,true);
        music.Accompany(0,16,-1);
        double cursor=16;
        for(int verse=0;verse<12;verse++) {
            if(verse==6) { music.Accompany(cursor,32,-1); cursor+=32; }
            music.Theme(melody,cursor,verse % 8); cursor+=32;
        }
        music.Accompany(cursor,16,-1);
        if (cursor + 16 != 448) throw new Exception("Аранжировка должна содержать 112 тактов");
        music.Space(); music.Write(Path.Combine(output,"korobeiniki.wav"));
        music.Write(preview,16*Beat,30,true);
        GenerateFanfare(output);
    }

    /// <summary>Создаёт только однократные фанфары; плейлист генерируется общим Python-синтезатором.</summary>
    public static void GenerateFanfare(string output) {
        var fanfare=new RenderMusic(5,false);
        int[][] chords={new[]{60,64,67},new[]{65,69,72},new[]{67,71,74},new[]{60,64,67,72,76}};
        double[] starts={0,.65,1.3,2.2};
        for(int c=0;c<4;c++) {
            for(int n=0;n<chords[c].Length;n++) fanfare.Note(starts[c]+n*.018,c==3?1.1:.45,chords[c][n],.12,(n-1)*.18,3);
            fanfare.Note(starts[c],.8,chords[c][0]-24,.14,0,2);
            fanfare.Note(starts[c]+.12,.65,chords[c][chords[c].Length-1]+12,.05,.3);
        }
        fanfare.Space(); fanfare.Write(Path.Combine(output,"record_fanfare.wav"),0,5,true);
    }
}

