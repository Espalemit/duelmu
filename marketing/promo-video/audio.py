import math, random, wave, struct, array, sys, os
SR=44100; DUR=30.0; N=int(SR*DUR)
random.seed(7)
mus=array.array('f',[0.0])*N; sfx=array.array('f',[0.0])*N
def add(buf,t0,samples,g=1.0):
    i0=int(t0*SR)
    for k,v in enumerate(samples):
        i=i0+k
        if 0<=i<N: buf[i]+=v*g
def midi(m): return 440*2**((m-69)/12)
def env(n,a,d):  # attack/decay in samples-based exponential
    return [min(1,k/max(1,a))*math.exp(-k/d) for k in range(n)]
def tone(f,dur,kind='sq',dec=0.15,att=0.004,vol=1.0):
    n=int(dur*SR); out=[]; ph=0.0; A=att*SR; D=dec*SR
    for k in range(n):
        ph+=f/SR; x=ph%1.0
        if kind=='sq': v=(1 if x<.5 else -1)*.5 + math.sin(2*math.pi*ph)*.5
        elif kind=='tri': v=4*abs(x-.5)-1
        elif kind=='saw': v=(2*x-1)*.6+math.sin(2*math.pi*ph)*.4
        else: v=math.sin(2*math.pi*ph)
        e=min(1,k/A)*math.exp(-k/D)
        if k>n-200: e*= (n-k)/200
        out.append(v*e*vol)
    return out
def kick():
    n=int(.22*SR); out=[]; ph=0
    for k in range(n):
        t=k/SR; f=50+110*math.exp(-t*30); ph+=f/SR
        out.append(math.sin(2*math.pi*ph)*math.exp(-t*14))
    return out
def noise(dur,dec,hp=True,vol=1.0):
    n=int(dur*SR); out=[]; prev=0
    for k in range(n):
        r=random.uniform(-1,1); v=(r-prev) if hp else r; prev=r
        out.append(v*math.exp(-k/(dec*SR))*vol)
    return out
KICK=kick(); HAT=noise(.04,.012,True,.35); CLAP=[v*.8 for v in noise(.16,.045,False)]
BPM=120; BEAT=60/BPM; S16=BEAT/4
prog=[(48,[60,64,67,72]),(43,[59,62,67,71]),(45,[60,64,69,72]),(41,[60,65,69,72])]  # C G Am F
arp_cache={}
def arpnote(m):
    if m not in arp_cache: arp_cache[m]=tone(midi(m),S16*1.6,'tri',dec=.09,vol=.28)
    return arp_cache[m]
bass_cache={}
def bassnote(m):
    if m not in bass_cache: bass_cache[m]=tone(midi(m),BEAT*.48,'saw',dec=.16,vol=.5)
    return bass_cache[m]
nbeats=int(DUR/BEAT)
for b in range(nbeats):
    t=b*BEAT
    if t>=29.0: break
    bar=b//4; root,ch=prog[bar%4]
    tension = 5.0<=t<8.0          # countdown: drop drums
    outro = t>=25.5
    if not tension:
        add(mus,t,KICK,.9)
        if b%2==1: add(mus,t,CLAP,.45)
    for h in range(2):
        add(mus,t+h*BEAT/2+BEAT/4,HAT,.6 if not tension else .9)
    # bass 8ths
    if not tension:
        for e in range(2): add(mus,t+e*BEAT/2,bassnote(root+(12 if e==1 else 0)),.55)
    # arp 16ths
    pat=[0,1,2,3,2,1,3,2] if not outro else [0,2,3,1]
    for s16 in range(4):
        idx=(b*4+s16)%len(pat)
        add(mus,t+s16*S16,arpnote(ch[pat[idx]]+12),.6 if not tension else .35)
# final chord sustain from 25.5
for m in [60,64,67,72,76]:
    add(mus,25.5,tone(midi(m),4.0,'saw',dec=1.6,att=.02,vol=.12))
# master fade out of music
for i in range(int(28.6*SR),N):
    mus[i]*=max(0,1-(i/SR-28.6)/1.3)

def loadwav(fn):
    w=wave.open(fn); sr=w.getframerate(); raw=w.readframes(w.getnframes()); w.close()
    a=array.array('h',raw); x=[v/32768 for v in a]
    if sr!=SR:
        r=SR/sr; out=[]
        for k in range(int(len(x)*r)):
            p=k/r; i=int(p); f=p-i
            out.append(x[i]*(1-f)+(x[min(i+1,len(x)-1)])*f)
        x=out
    return x
TAP=loadwav('sound_tap.wav'); OK=loadwav('sound_correct.wav'); START=loadwav('sound_match_start.wav')
def whoosh(dur=.55):
    n=int(dur*SR); out=[]; lp=0
    for k in range(n):
        p=k/n; a=.02+.35*math.sin(math.pi*p)**2
        lp+= a*(random.uniform(-1,1)-lp)
        out.append(lp*math.sin(math.pi*p)**1.5*2.2)
    return out
WH=whoosh()
def pop():
    n=int(.09*SR); out=[]; ph=0
    for k in range(n):
        t=k/SR; f=500+900*math.exp(-t*40); ph+=f/SR
        out.append(math.sin(2*math.pi*ph)*math.exp(-t*35)*.7)
    return out
POP=pop()
TICK=[a+b for a,b in zip(tone(1320,.12,'sine',dec=.05,vol=.5),tone(660,.12,'sine',dec=.05,vol=.3))]
SPARK=[]
for j,m in enumerate([84,88,91,96]):
    nt=tone(midi(m),.35,'sine',dec=.12,vol=.25); SPARK+= [0]*0; add(sfx,0,[])
cuts=[3.2,10.0,15.0,21.0,25.5]
for c in cuts: add(sfx,c-.3,WH,.55)
add(sfx,0.0,START,.6)
for t in [0.1,0.4,0.7,1.5]: add(sfx,t,POP,.8)
add(sfx,3.5,POP,.6)
for i in range(4): add(sfx,3.2+.8+i*.15,TAP,.9)
for t in [5.0,6.0,7.0]: add(sfx,t,TICK,.9)
add(sfx,8.0,OK,.95)
for j,m in enumerate([84,88,91,96]): add(sfx,8.08+j*.06,tone(midi(m),.35,'sine',dec=.12,vol=.22))
QFS=[10.15,11.75,13.35]
for q in QFS:
    add(sfx,q,WH[:int(.3*SR)],.35); add(sfx,q+.95,OK,.8)
add(sfx,15.05,START,.6); add(sfx,15.6,POP,.7)
for i in range(4): add(sfx,15.9+i*.2,POP,.8)
add(sfx,17.0,WH[:int(.35*SR)],.4)
for k in range(10): add(sfx,17.5+k*.18,TAP,.5)
add(sfx,21.05,POP,.7)
for i in range(3): add(sfx,21.7+i*.55,WH[:int(.3*SR)],.35); add(sfx,21.85+i*.55,POP,.7)
add(sfx,25.55,START,.7)
for j,m in enumerate([72,76,79,84,88]): add(sfx,25.6+j*.07,tone(midi(m),.6,'sine',dec=.25,vol=.22))
add(sfx,26.3,POP,.6); add(sfx,26.6,POP,.6); add(sfx,27.1,OK,.6)

out=array.array('h')
mix=[mus[i]*.42+sfx[i]*.85 for i in range(N)]
pk=max(abs(v) for v in mix) or 1
g=.95/pk if pk>.95 else 1.0
for i in range(N):
    v=math.tanh(mix[i]*g*1.1)/math.tanh(1.1)
    s=int(max(-1,min(1,v))*32000); out.append(s); out.append(s)
w=wave.open('audio.wav','wb'); w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(out.tobytes()); w.close()
print('peak',pk)
