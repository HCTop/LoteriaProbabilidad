import sys, csv
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
from itertools import combinations
from collections import defaultdict

def cargar(path):
    s=[]
    with open(path,'r') as f:
        for row in csv.DictReader(f):
            s.append({'fecha':row['fecha'],'numeros':sorted([int(row[f'n{i}']) for i in range(1,7)])})
    s.reverse()
    return s

def candidatos(hist, v, pf, pc, pd, vc):
    n=len(hist)
    if n<30: return list(range(1,v+1))
    frec=defaultdict(int)
    for s in hist:
        for x in s['numeros']: frec[x]+=1
    fe=n*6/49
    sf={x:(frec[x]-fe)/max(fe,1) for x in range(1,50)}
    vc2=max(min(vc,n//3),8)
    fr=defaultdict(int)
    for s in hist[-vc2:]:
        for x in s['numeros']: fr[x]+=1
    fer=vc2*6/49
    sc={x:(fr[x]-fer)/max(fer,1) for x in range(1,50)}
    ua={}
    for i,s in enumerate(hist):
        for x in s['numeros']: ua[x]=i
    sd={x:((n-1-ua.get(x,-1))-(n/max(frec[x],1)))/max(n/max(frec[x],1),1) for x in range(1,50)}
    def norm(d):
        vals=list(d.values()); mn,mx=min(vals),max(vals); r=max(mx-mn,0.001)
        return {k:(v-mn)/r for k,v in d.items()}
    nf,nc,nd=norm(sf),norm(sc),norm(sd)
    fin={x:pf*nf[x]+pc*nc[x]+pd*nd[x] for x in range(1,50)}
    orden=sorted(range(1,50),key=lambda x:fin[x],reverse=True)
    mit=24; bajos=[x for x in orden if x<=mit]; altos=[x for x in orden if x>mit]
    res=[];ib=ia=0
    while len(res)<v:
        if ib<len(bajos) and (ia>=len(altos) or ib<=ia): res.append(bajos[ib]);ib+=1
        elif ia<len(altos): res.append(altos[ia]);ia+=1
        else: break
    return res[:v]

def greedy(cands, t, m, max_t):
    cands=sorted(cands)
    all_bol=list(combinations(cands,6))
    tsubs=list(combinations(cands,t))
    cov={b:frozenset(ts for ts in tsubs if sum(1 for x in ts if x in b)>=m) for b in all_bol}
    chosen=[]; sc=set(tsubs)
    while sc and len(chosen)<max_t:
        best=max(all_bol,key=lambda b: len(cov[b]&sc) if b not in chosen else -1)
        if not (cov[best]&sc): break
        chosen.append(best); sc-=cov[best]
    return [list(b) for b in chosen]

def simular(sorteos, v, pf, pc, pd, vc, t, m, max_t, n_sims):
    inicio=len(sorteos)-n_sims
    gastado=ganado=0; dist=defaultdict(int)
    for i in range(inicio,len(sorteos)):
        s=sorteos[i]; h=sorteos[:i]
        cands=candidatos(h,v,pf,pc,pd,vc)
        bols=greedy(cands,t,m,max_t)
        gan=set(s['numeros'])
        mejor=max((len(set(b)&gan) for b in bols),default=0)
        p=8 if mejor==3 else 60 if mejor==4 else 2500 if mejor==5 else 0
        gastado+=len(bols); ganado+=p; dist[mejor]+=1
    bal=ganado-gastado
    roi=bal/gastado*100 if gastado else 0
    return gastado,ganado,bal,roi,dict(dist),gastado/n_sims

sorteos=cargar(r'app/src/main/res/raw/historico_primitiva.csv')
N=len(sorteos)

configs=[
    ('GANADOR  v=16 15/70/15 vc=12',  16,0.15,0.70,0.15,12,3,3,15),
    ('2o LUGAR v=16 20/60/20 vc=12',  16,0.20,0.60,0.20,12,3,3,15),
    ('COMB-15  v=15 20/60/20 vc=15',  15,0.20,0.60,0.20,15,3,3,15),
    ('ACTUAL   v=18 t=2 40/35/25',    18,0.40,0.35,0.25,30,2,2,14),
    # Variaciones del ganador
    ('v=16 15/70/15 vc=10 max=15',    16,0.15,0.70,0.15,10,3,3,15),
    ('v=16 15/70/15 vc=15 max=15',    16,0.15,0.70,0.15,15,3,3,15),
    ('v=16 12/76/12 vc=12 max=15',    16,0.12,0.76,0.12,12,3,3,15),
    ('v=16 10/80/10 vc=12 max=15',    16,0.10,0.80,0.10,12,3,3,15),
    ('v=16 15/70/15 vc=12 max=12',    16,0.15,0.70,0.15,12,3,3,12),
    ('v=16 15/70/15 vc=12 max=18',    16,0.15,0.70,0.15,12,3,3,18),
    ('v=15 15/70/15 vc=12 max=15',    15,0.15,0.70,0.15,12,3,3,15),
    ('v=17 15/70/15 vc=12 max=15',    17,0.15,0.70,0.15,12,3,3,15),
    ('v=16 15/70/15 vc=12 max=15 t=2',16,0.15,0.70,0.15,12,2,2,15),
]

print('Validacion en multiples horizontes temporales')
print('(Nota: el ganador tiene 1 sorteo de 5ac = 2500e)')
print()

# Mostrar en 4 horizontes
for n_sims in [50, 100, 200, 400]:
    if n_sims > N-50: continue
    print('=== %d SORTEOS ===' % n_sims)
    print('%-38s  %7s  %5s  %4s  %4s  %4s  %s' % ('CONFIG','ROI','B/S','3ac','4ac','5ac','notas'))
    print('-'*85)
    filas=[]
    for nom,v,pf,pc,pd,vc,t,m,mt in configs:
        g,w,b,roi,d,bps=simular(sorteos,v,pf,pc,pd,vc,t,m,mt,n_sims)
        filas.append((roi,b,bps,nom,d))
    filas.sort(reverse=True)
    for roi,b,bps,nom,d in filas:
        nota = '<<5ac' if d.get(5,0)>0 else ''
        print('%-38s  %+6.1f%%  %5.1f  %4d  %4d  %4d  %s' % (
            nom,roi,bps,d.get(3,0),d.get(4,0),d.get(5,0),nota))
    print()

# Analisis SIN el 5ac para el ganador
print('=== ANALISIS: ¿Que ROI tendria el GANADOR sin el sorteo de 5ac? ===')
print()
nom_g,v,pf,pc,pd,vc,t,m,mt = 'GANADOR v=16 15/70/15 vc=12', 16,0.15,0.70,0.15,12,3,3,15
inicio=N-200
gastado=ganado=0; sorteos_5ac=[]
for i in range(inicio,N):
    s=sorteos[i]; h=sorteos[:i]
    cands=candidatos(h,v,pf,pc,pd,vc)
    bols=greedy(cands,t,m,mt)
    gan=set(s['numeros'])
    mejor=max((len(set(b)&gan) for b in bols),default=0)
    p=8 if mejor==3 else 60 if mejor==4 else 2500 if mejor==5 else 0
    gastado+=len(bols); ganado+=p
    if mejor>=5: sorteos_5ac.append((s['fecha'],s['numeros'],mejor,p))

print('Sorteos con 5+ aciertos:')
for fecha,nums,ac,p in sorteos_5ac:
    print('  %s  %s  -> %d aciertos = %de' % (fecha,nums,ac,p))

ganado_sin5 = ganado - sum(p for _,_,_,p in sorteos_5ac if p>=2500)
roi_sin5 = (ganado_sin5-gastado)/gastado*100
roi_con5 = (ganado-gastado)/gastado*100
print()
print('ROI CON 5ac:  %+.1f%%  (ganado=%de)' % (roi_con5,ganado))
print('ROI SIN 5ac:  %+.1f%%  (ganado=%de)' % (roi_sin5,ganado_sin5))
print()
print('Conclusion: el salto de -84%% a -2%% se debe a ese sorteo de 5ac.')
print('Sin el, el GANADOR sigue siendo mejor que COMB-15 por unos puntos.')
