import sys, csv, random
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
from itertools import combinations
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

def candidatos(hist, v=17, pf=0.15, pc=0.70, pd=0.15, vc=12):
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

def greedy_combos(cands, t=3, m=3, max_t=15):
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

# Reintegros frecuentes en el historico (para prediccion)
def top_reintegros(hist, n=3):
    frec=defaultdict(int)
    for s in hist: frec[s['reintegro']]+=1
    return [r for r,_ in sorted(frec.items(),key=lambda x:-x[1])][:n]

def premio(ac_nums, ac_rein):
    """Premios Primitiva reales (fijos)"""
    if ac_nums==6 and ac_rein: return 'BOTE',   0  # jackpot variable
    if ac_nums==6:              return '2a',      0  # 2a cat variable
    if ac_nums==5 and ac_rein: return '3a',  20000  # ~20000e aprox
    if ac_nums==5:              return '4a',   1500  # ~1500e aprox
    if ac_nums==4:              return '5a',     48
    if ac_nums==3:              return '6a',      8
    if ac_rein:                 return 'R',       1
    return None, 0

def simular_con_reintegros(sorteos, estrategia_fn, n_sims=200):
    inicio=len(sorteos)-n_sims
    gastado=ganado=0
    conteo=defaultdict(int)
    for i in range(inicio,len(sorteos)):
        s=sorteos[i]; h=sorteos[:i]
        boletos = estrategia_fn(h)  # lista de (6nums, reintegro)
        gan=set(s['numeros']); rein_real=s['reintegro']
        gastado+=len(boletos)
        mejor_cat=None; mejor_val=0
        for (nums,rein) in boletos:
            ac_n=len(set(nums)&gan)
            ac_r=(rein==rein_real)
            cat,val=premio(ac_n,ac_r)
            if cat:
                conteo[cat]+=1
                if val>mejor_val: mejor_val=val; mejor_cat=cat
        ganado+=mejor_val
    return gastado, ganado, ganado-gastado, dict(conteo)

# ============================================================
# ESTRATEGIAS
# ============================================================

def estrategia_actual(hist):
    """15 combos distintos, 3 reintegros top (5+5+5)"""
    cands=candidatos(hist); combos=greedy_combos(cands)
    reints=top_reintegros(hist,3)
    if not reints: reints=[7,3,1]
    total=len(combos)
    result=[]
    for idx,c in enumerate(combos):
        r=reints[(idx*len(reints))//total]
        result.append((c,r))
    return result

def estrategia_cubre5reintegros(hist):
    """15 combos distintos, cubriendo 5 reintegros distintos (3+3+3+3+3)"""
    cands=candidatos(hist); combos=greedy_combos(cands)
    reints=top_reintegros(hist,5)
    if len(reints)<5: reints=(reints+[0,1,2,3,4])[:5]
    total=len(combos)
    result=[]
    for idx,c in enumerate(combos):
        r=reints[(idx*len(reints))//total]
        result.append((c,r))
    return result

def estrategia_cubre10reintegros(hist):
    """15 combos distintos, cubriendo los 10 reintegros posibles"""
    cands=candidatos(hist); combos=greedy_combos(cands)
    all10=list(range(10))  # 0-9
    result=[]
    for idx,c in enumerate(combos):
        r=all10[idx % 10]  # ciclo por todos los reintegros
        result.append((c,r))
    return result

def estrategia_mejor_combo_10R(hist):
    """Mejor combo jugado con los 10 reintegros + 5 combos distintos"""
    cands=candidatos(hist); combos=greedy_combos(cands,max_t=15)
    mejor=combos[0]  # el mejor del greedy
    resto=combos[1:6] if len(combos)>1 else []
    reints_resto=top_reintegros(hist,len(resto)) if resto else []
    result=[]
    for r in range(10):  # 10 boletos: mismo combo, 10 reintegros
        result.append((mejor,r))
    for idx,c in enumerate(resto):  # 5 boletos adicionales
        rein=reints_resto[idx] if idx<len(reints_resto) else idx%10
        result.append((c,rein))
    return result

def estrategia_2combos_10R(hist):
    """2 mejores combos x 5 reintegros c/u + 5 combos distintos = 15 total"""
    cands=candidatos(hist); combos=greedy_combos(cands,max_t=15)
    top2=combos[:2]; resto=combos[2:7] if len(combos)>2 else []
    reints_top=[0,2,4,6,8]  # 5 reintegros pares para combo 1
    reints_top2=[1,3,5,7,9]  # 5 reintegros impares para combo 2
    result=[]
    for r in reints_top: result.append((top2[0],r))
    if len(top2)>1:
        for r in reints_top2: result.append((top2[1],r))
    reints_resto=top_reintegros(hist,5)
    for idx,c in enumerate(resto):
        r=reints_resto[idx%len(reints_resto)] if reints_resto else idx%10
        result.append((c,r))
    return result[:15]

def estrategia_frecuencia_interleaved(hist):
    """15 combos, 10 reintegros: top5 frecuentes x2 boletos, bottom5 x1 boleto"""
    cands=candidatos(hist); combos=greedy_combos(cands)
    # Calcular frecuencia de reintegros en el histórico
    frec=defaultdict(int)
    for s in hist: frec[s['reintegro']]+=1
    sorted_r=sorted(range(10), key=lambda r:-frec[r])
    top5=sorted_r[:5]; bottom5=sorted_r[5:]
    # Intercalar: posiciones pares=top5 (2 boletos), impares=bottom5 (1 boleto)
    orden=[x for par in zip(top5,bottom5) for x in par]  # [t0,b0,t1,b1,...]
    result=[]
    for idx,c in enumerate(combos):
        r=orden[(idx*10)//len(combos)]
        result.append((c,r))
    return result

sorteos=cargar(r'app/src/main/res/raw/historico_primitiva.csv')
N=200

estrategias=[
    ('A: 15 combos, 3 reintegros (5+5+5)  [ACTUAL]', estrategia_actual),
    ('C: 15 combos, 10 reintegros ciclo 0-9',         estrategia_cubre10reintegros),
    ('F: 15 combos, top5x2 + bot5x1 por frecuencia',  estrategia_frecuencia_interleaved),
]

print('Simulacion %d sorteos con reintegro real del historico' % N)
print()
print('%-42s %5s %5s %6s  %4s %4s %4s %4s %4s %4s %4s' % (
    'ESTRATEGIA','GAST','GAN','BAL','6+R','6','5+R','5','4','3','R'))
print('-'*105)

for nom,fn in estrategias:
    g,w,b,c=simular_con_reintegros(sorteos,fn,N)
    print('%-42s %5de %4de %+5de  %4s %4s %4d %4d %4d %4d %4d' % (
        nom, g, w, b,
        str(c.get('BOTE',0)) if c.get('BOTE',0) else '-',
        str(c.get('2a',0))   if c.get('2a',0)   else '-',
        c.get('3a',0), c.get('4a',0), c.get('5a',0), c.get('6a',0), c.get('R',0)
    ))

print()
print('Leyenda premios fijos:')
print('  5+R (3a cat) ~20000e  |  5nums (4a) ~1500e  |  4nums (5a) 48e  |  3nums (6a) 8e  |  R=1e')
print()
print('Analisis reintegros por sorteo:')
for nom,fn in estrategias:
    g,w,b,c=simular_con_reintegros(sorteos,fn,N)
    pct_r=c.get('R',0)/N*100
    print('  %-42s -> R en %d/%d sorteos (%.0f%%)  = %.1fe ganados' % (
        nom, c.get('R',0), N, pct_r, c.get('R',0)))
