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

configs=[]

# Variar V (candidatos): 12..17
for v in [12,13,14,15,16,17]:
    configs.append(('v=%d t=3 20/60/20 vc=15 max=15' % v, v,0.20,0.60,0.20,15,3,3,15))

# Variar t con v=15
for t in [2,3,4]:
    configs.append(('v=15 t=%d 20/60/20 vc=15 max=15' % t, 15,0.20,0.60,0.20,15,t,t,15))

# Variar pesos (v=15 t=3 vc=15 max=15)
for nom,pf,pc,pd in [
    ('10/80/10',0.10,0.80,0.10),
    ('15/70/15',0.15,0.70,0.15),
    ('20/60/20',0.20,0.60,0.20),
    ('25/50/25',0.25,0.50,0.25),
    ('30/40/30',0.30,0.40,0.30),
    ('40/35/25',0.40,0.35,0.25),
    ('50/30/20',0.50,0.30,0.20),
    ('33/33/33',0.33,0.33,0.33),
    ('0/100/0', 0.00,1.00,0.00),
    ('20/70/10',0.20,0.70,0.10),
    ('10/70/20',0.10,0.70,0.20),
]:
    configs.append(('v=15 t=3 %s vc=15 max=15' % nom, 15,pf,pc,pd,15,3,3,15))

# Variar ventana caliente
for vc in [8,10,12,15,20,25]:
    configs.append(('v=15 t=3 20/60/20 vc=%d max=15' % vc, 15,0.20,0.60,0.20,vc,3,3,15))

# Variar max tickets
for mt in [8,10,12,15,18,20]:
    configs.append(('v=15 t=3 20/60/20 vc=15 max=%d' % mt, 15,0.20,0.60,0.20,15,3,3,mt))

# Especiales
configs+=[
    ('v=13 t=3 15/70/15 vc=12 max=12', 13,0.15,0.70,0.15,12,3,3,12),
    ('v=14 t=3 15/70/15 vc=12 max=12', 14,0.15,0.70,0.15,12,3,3,12),
    ('v=14 t=3 20/60/20 vc=12 max=12', 14,0.20,0.60,0.20,12,3,3,12),
    ('v=16 t=3 15/70/15 vc=12 max=15', 16,0.15,0.70,0.15,12,3,3,15),
    ('v=15 t=3 10/80/10 vc=10 max=15', 15,0.10,0.80,0.10,10,3,3,15),
    ('v=15 t=4 20/60/20 vc=15 max=20', 15,0.20,0.60,0.20,15,4,4,20),
    ('v=16 t=3 20/60/20 vc=12 max=15', 16,0.20,0.60,0.20,12,3,3,15),
    ('v=14 t=3 20/60/20 vc=15 max=15', 14,0.20,0.60,0.20,15,3,3,15),
    ('v=15 t=3 20/60/20 vc=20 max=12', 15,0.20,0.60,0.20,20,3,3,12),
    ('v=13 t=3 20/60/20 vc=15 max=10', 13,0.20,0.60,0.20,15,3,3,10),
    ('v=12 t=3 20/60/20 vc=10 max=10', 12,0.20,0.60,0.20,10,3,3,10),
    ('v=15 t=2 20/60/20 vc=15 max=10', 15,0.20,0.60,0.20,15,2,2,10),
    ('v=18 t=3 20/60/20 vc=15 max=20', 18,0.20,0.60,0.20,15,3,3,20),
    ('ACTUAL v=18 t=2 40/35/25 max=14', 18,0.40,0.35,0.25,30,2,2,14),
    ('COMB-15 [ACTUAL]', 15,0.20,0.60,0.20,15,3,3,15),
]

# Eliminar duplicados por params
seen=set(); configs2=[]
for c in configs:
    k=c[1:]
    if k not in seen: seen.add(k); configs2.append(c)
configs=configs2

print('Probando %d configuraciones en 200 sorteos...' % len(configs))
print()

resultados=[]
for idx,(nom,v,pf,pc,pd,vc,t,m,mt) in enumerate(configs):
    g,w,b,roi,d,bps=simular(sorteos,v,pf,pc,pd,vc,t,m,mt,200)
    resultados.append((roi,b,bps,nom,g,w,d))
    if (idx+1)%10==0:
        print('  [%d/%d completadas...]' % (idx+1,len(configs)))

resultados.sort(reverse=True)

print()
header='%-45s %7s %7s %5s  %4s %4s %4s' % ('CONFIG','ROI','BAL','B/S','3ac','4ac','5ac')
print(header)
print('-'*85)
for roi,b,bps,nom,g,w,d in resultados[:30]:
    print('%-45s %+6.1f%% %+6de %5.1f  %4d %4d %4d' % (
        nom,roi,b,bps,d.get(3,0),d.get(4,0),d.get(5,0)))

print()
print('=== TOP 5 MAS ECONOMICOS (<=12 bol/s) ===')
eco=[(roi,b,bps,nom,g,w,d) for roi,b,bps,nom,g,w,d in resultados if bps<=12]
for roi,b,bps,nom,g,w,d in eco[:5]:
    print('%-45s %+6.1f%% %+6de %5.1f  %4d %4d' % (nom,roi,b,bps,d.get(3,0),d.get(4,0)))

print()
print('=== GANADOR ABSOLUTO ===')
best=resultados[0]
print('%-45s  ROI=%+.1f%%  Balance=%+de  %.1f bol/sorteo' % (best[3],best[0],best[1],best[2]))
