import sys, csv, random
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
from collections import defaultdict

def cargar(path):
    s=[]
    with open(path,'r') as f:
        for row in csv.DictReader(f):
            s.append({
                'fecha': row['fecha'],
                'numeros': sorted([int(row['n%d'%i]) for i in range(1,7)]),
                'reintegro': int(row['reintegro'])
            })
    s.reverse()
    return s

# ─── MÉTODOS ───────────────────────────────────────────────────────

def metodo_aleatorio(hist):
    return sorted(random.sample(range(1,50),6))

def metodo_frecuencias(hist, n=6):
    """Top 6 números por frecuencia histórica total"""
    frec=defaultdict(int)
    for s in hist:
        for x in s['numeros']: frec[x]+=1
    return sorted(sorted(range(1,50), key=lambda x:-frec[x])[:n])

def metodo_frios(hist, n=6):
    """Los 6 números que menos han salido"""
    frec=defaultdict(int)
    for s in hist:
        for x in s['numeros']: frec[x]+=1
    return sorted(sorted(range(1,50), key=lambda x:frec[x])[:n])

def metodo_debidos(hist, n=6):
    """Los 6 números que más tiempo llevan sin salir"""
    ua={}
    for i,s in enumerate(hist):
        for x in s['numeros']: ua[x]=i
    total=len(hist)
    orden=sorted(range(1,50), key=lambda x:-(total-1-ua.get(x,-1)))
    return sorted(orden[:n])

def metodo_calientes(hist, ventana=12, n=6):
    """Top 6 en los últimos 12 sorteos (calientes recientes)"""
    recent=hist[-ventana:] if len(hist)>=ventana else hist
    frec=defaultdict(int)
    for s in recent:
        for x in s['numeros']: frec[x]+=1
    return sorted(sorted(range(1,50), key=lambda x:-frec[x])[:n])

def metodo_mixto_15_70_15(hist, ventana=12, n=6):
    """Pesos 15% frecuencia + 70% calientes + 15% debidos (= config Abuelo)"""
    total=len(hist)
    if total<10: return metodo_aleatorio(hist)
    # Frecuencia
    frec=defaultdict(int)
    for s in hist:
        for x in s['numeros']: frec[x]+=1
    fe=total*6/49
    sf={x:(frec[x]-fe)/max(fe,1) for x in range(1,50)}
    # Calientes
    vc=min(ventana,total//3); vc=max(vc,5)
    fc=defaultdict(int)
    for s in hist[-vc:]:
        for x in s['numeros']: fc[x]+=1
    fer=vc*6/49
    sc={x:(fc[x]-fer)/max(fer,1) for x in range(1,50)}
    # Debidos
    ua={}
    for i,s in enumerate(hist):
        for x in s['numeros']: ua[x]=i
    sd={x:((total-1-ua.get(x,-1))-(total/max(frec[x],1)))/max(total/max(frec[x],1),1) for x in range(1,50)}
    def norm(d):
        vals=list(d.values()); mn,mx=min(vals),max(vals); r=max(mx-mn,0.001)
        return {k:(v-mn)/r for k,v in d.items()}
    nf,nc,nd=norm(sf),norm(sc),norm(sd)
    fin={x:0.15*nf[x]+0.70*nc[x]+0.15*nd[x] for x in range(1,50)}
    return sorted(sorted(range(1,50),key=lambda x:-fin[x])[:n])

def metodo_pares_frecuentes(hist, n=6):
    """Selecciona números que aparecen en los pares más frecuentes"""
    pares=defaultdict(int)
    for s in hist:
        nums=s['numeros']
        for i in range(len(nums)):
            for j in range(i+1,len(nums)):
                pares[(nums[i],nums[j])]+=1
    # Conteo de aparición en top pares
    score=defaultdict(int)
    for (a,b),cnt in sorted(pares.items(),key=lambda x:-x[1])[:50]:
        score[a]+=cnt; score[b]+=cnt
    return sorted(sorted(range(1,50),key=lambda x:-score[x])[:n])

METODOS=[
    ('Aleatorio Puro',         metodo_aleatorio),
    ('Frecuencias',            metodo_frecuencias),
    ('Numeros Frios',          metodo_frios),
    ('Numeros Debidos',        metodo_debidos),
    ('Calientes (ult.12)',     metodo_calientes),
    ('Mix 15/70/15 [Abuelo]',  metodo_mixto_15_70_15),
    ('Pares Frecuentes',       metodo_pares_frecuentes),
]

# ─── BACKTEST ──────────────────────────────────────────────────────

def backtest(sorteos, metodo_fn, n=200, reps=1):
    inicio=len(sorteos)-n
    total_ac=[0]*7  # aciertos 0..6
    for i in range(inicio,len(sorteos)):
        s=sorteos[i]; h=sorteos[:i]
        if not h: continue
        # Para aleatorio repetimos para estabilizar
        mejor=0
        for _ in range(reps):
            pred=metodo_fn(h)
            ac=len(set(pred)&set(s['numeros']))
            mejor=max(mejor,ac)
        total_ac[mejor]+=1
    total=sum(total_ac)
    media=sum(k*v for k,v in enumerate(total_ac))/max(total,1)
    pct3=sum(total_ac[3:])/max(total,1)*100
    pct4=sum(total_ac[4:])/max(total,1)*100
    return total_ac, media, pct3, pct4

# ─── MAIN ──────────────────────────────────────────────────────────

sorteos=cargar(r'app/src/main/res/raw/historico_primitiva.csv')
N=300  # últimos 300 sorteos

print('Backtest walk-forward: %d sorteos (Primitiva)' % N)
print()
print('%-26s %6s %6s %6s %6s %6s %6s  %6s %5s %5s' % (
    'METODO','0ac','1ac','2ac','3ac','4ac','5ac','MEDIA','3ac+','4ac+'))
print('-'*97)

resultados=[]
for nombre, fn in METODOS:
    ac, media, pct3, pct4 = backtest(sorteos, fn, N, reps=1)
    resultados.append((nombre, ac, media, pct3, pct4))
    print('%-26s %6d %6d %6d %6d %6d %6d  %6.3f %4.1f%% %4.1f%%' % (
        nombre,
        ac[0],ac[1],ac[2],ac[3],ac[4],ac[5] if len(ac)>5 else 0,
        media, pct3, pct4
    ))

print()
print('Ranking por media de aciertos:')
for i,(nom,_,media,pct3,_) in enumerate(sorted(resultados,key=lambda x:-x[2])):
    marca=''
    if i==0: marca=' <- MEJOR'
    if nom=='Aleatorio Puro': marca=' <- BASELINE'
    print('  %d. %-26s  %.4f aciertos/sorteo  3ac+: %.1f%%%s' % (i+1,nom,media,pct3,marca))

print()
print('Nota: valor esperado teorico por combo = 6x6/49 = 0.735 aciertos.')
print('Metodos que no superen ese valor son ruido puro.')
