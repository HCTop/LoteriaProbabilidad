"""
Backtest para medir el poder predictivo de cada característica por separado.

Para cada sorteo, cada característica rankea los 49 números.
Medimos la posición media de los números ganadores en ese ranking.
Un ranking perfecto = posición media 3 (top 6 de 49).
Un ranking aleatorio = posición media 25 (promedio de 1..49).

Resultado: qué características son señal real y cuáles son ruido puro.
"""
import sys, csv
from collections import defaultdict
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

def cargar(path):
    s = []
    with open(path, 'r') as f:
        for row in csv.DictReader(f):
            s.append({
                'numeros': sorted([int(row['n%d' % i]) for i in range(1, 7)])
            })
    s.reverse()
    return s

def rank_frecuencia(hist, maxN=49):
    """Frecuencia histórica total."""
    frec = defaultdict(int)
    for s in hist:
        for x in s['numeros']: frec[x] += 1
    return sorted(range(1, maxN+1), key=lambda x: -frec[x])

def rank_tendencia(hist, ventana=12, maxN=49):
    """Frecuencia reciente (últimos [ventana] sorteos)."""
    recientes = hist[-ventana:]
    frec = defaultdict(int)
    for s in recientes:
        for x in s['numeros']: frec[x] += 1
    return sorted(range(1, maxN+1), key=lambda x: -frec[x])

def rank_tendencia_corta(hist, ventana=5, maxN=49):
    """Frecuencia muy reciente (últimos 5 sorteos)."""
    return rank_tendencia(hist, ventana=ventana, maxN=maxN)

def rank_gap(hist, maxN=49):
    """Números más 'debidos' = más sorteos sin salir (gap más largo)."""
    ultima = {}
    for i, s in enumerate(hist):
        for x in s['numeros']: ultima[x] = i
    n = len(hist)
    # Gap = sorteos desde última aparición (mayor gap = más "debido")
    gap = {x: (n - 1 - ultima.get(x, -1)) for x in range(1, maxN+1)}
    return sorted(range(1, maxN+1), key=lambda x: -gap[x])

def rank_frios(hist, maxN=49):
    """Opuesto a frecuencia: números menos frecuentes."""
    ranking = rank_frecuencia(hist, maxN)
    return list(reversed(ranking))

def rank_paridad_impar(hist, maxN=49):
    """Favorece números impares."""
    return sorted(range(1, maxN+1), key=lambda x: (x % 2 == 0, x))

def rank_decenas(hist, maxN=49):
    """Favorece decenas con más apariciones recientes."""
    recientes = hist[-20:]
    decena_frec = defaultdict(int)
    for s in recientes:
        for x in s['numeros']: decena_frec[x // 10] += 1
    return sorted(range(1, maxN+1), key=lambda x: -decena_frec[x // 10])

def rank_bajos(hist, maxN=49):
    """Favorece números bajos (1-24)."""
    return sorted(range(1, maxN+1), key=lambda x: x)

def rank_ema(hist, alpha=0.15, maxN=49):
    """EMA (media exponencial): más peso a sorteos recientes."""
    ema = {x: 0.0 for x in range(1, maxN+1)}
    for s in hist:
        presentes = set(s['numeros'])
        for x in range(1, maxN+1):
            val = 1.0 if x in presentes else 0.0
            ema[x] = alpha * val + (1 - alpha) * ema[x]
    return sorted(range(1, maxN+1), key=lambda x: -ema[x])

def rank_aleatorio_fijo(hist, maxN=49):
    """Control: ranking fijo (siempre 1,2,3,...) = puro ruido estructurado."""
    return list(range(1, maxN+1))

def evaluar_ranking(ranking, ganadores):
    """
    Devuelve la posición media de los ganadores en el ranking.
    Posición 1 = el número más predicho.
    Menor = mejor predicción.
    """
    pos = {num: i+1 for i, num in enumerate(ranking)}
    return sum(pos[g] for g in ganadores) / len(ganadores)

def backtest(sorteos, rank_fn, min_hist=30, n=300):
    """
    Walk-forward: para cada sorteo, rankea con solo el historial previo.
    Devuelve la posición media de los ganadores.
    """
    inicio = max(min_hist, len(sorteos) - n)
    posiciones = []
    for i in range(inicio, len(sorteos)):
        hist = sorteos[:i]
        if len(hist) < min_hist:
            continue
        try:
            ranking = rank_fn(hist)
            pos_media = evaluar_ranking(ranking, sorteos[i]['numeros'])
            posiciones.append(pos_media)
        except Exception:
            pass
    return sum(posiciones) / len(posiciones) if posiciones else 25.0, len(posiciones)

# ─────────────────────────────────────────────────────────────
sorteos = cargar('app/src/main/res/raw/historico_primitiva.csv')

print(f'Sorteos totales: {len(sorteos)}, backtest con últimos 300')
print()
print(f'{"Característica":<30} {"Pos. media":>10}  {"Señal":>8}  Interpretación')
print('─' * 75)

# Referencia teórica: aleatorio = posición media 25.0
ALEATORIO = 25.0
# "Señal" = cuánto mejor que aleatorio (en %)
# Negativo = contraproducente

caracteristicas = [
    ('Frecuencia histórica',        lambda h: rank_frecuencia(h)),
    ('Tendencia reciente (12s)',     lambda h: rank_tendencia(h, 12)),
    ('Tendencia corta (5s)',         lambda h: rank_tendencia_corta(h, 5)),
    ('EMA (alpha=0.15)',             lambda h: rank_ema(h, 0.15)),
    ('Gap / Debidos',               lambda h: rank_gap(h)),
    ('Fríos (anti-frecuencia)',     lambda h: rank_frios(h)),
    ('Paridad (impares)',           lambda h: rank_paridad_impar(h)),
    ('Decenas frecuentes',          lambda h: rank_decenas(h)),
    ('Números bajos (1-24)',        lambda h: rank_bajos(h)),
    ('Aleatorio fijo (control)',    lambda h: rank_aleatorio_fijo(h)),
]

resultados = []
for nombre, fn in caracteristicas:
    media, n_sorteos = backtest(sorteos, fn)
    señal = (ALEATORIO - media) / ALEATORIO * 100
    if señal > 3:
        interp = '✓ SEÑAL'
    elif señal > 0:
        interp = '~ marginal'
    elif señal > -3:
        interp = '≈ ruido'
    else:
        interp = '✗ CONTRAPRODUCENTE'
    resultados.append((nombre, media, señal, interp))
    print(f'{nombre:<30} {media:>10.2f}  {señal:>+7.1f}%  {interp}')

print()
print(f'Referencia teórica (aleatorio): {ALEATORIO:.1f}')
print(f'Referencia óptima (6 números top): ~3.5')
print()

# ─────────────────────────────────────────────────────────────
# Calcular pesos sugeridos para MemoriaIA basados en la señal real
print('=' * 75)
print('PESOS SUGERIDOS para características (normalizados, suma = 1.0):')
print()

# Las 10 características de MemoriaIA.CARACTERISTICAS:
# frecuencia, gap, tendencia, patrones, balance, ciclos, consecutivos, suma, paridad, decenas

# Mapeamos los resultados del backtest a los nombres de CARACTERISTICAS
# Para las que no tenemos medida directa (patrones, balance, suma, consecutivos, ciclos)
# usamos inferencia desde los resultados de método.

mapping = {
    'frecuencia':   next(s for n,s,_,_ in resultados if 'Frecuencia histórica' in n),
    'tendencia':    next(s for n,s,_,_ in resultados if 'Tendencia reciente' in n),
    'gap':          next(s for n,s,_,_ in resultados if 'Gap' in n),
    'paridad':      next(s for n,s,_,_ in resultados if 'Paridad' in n),
    'decenas':      next(s for n,s,_,_ in resultados if 'Decenas' in n),
    # Sin medida directa — inferidos de backtest de métodos:
    # 'patrones': correlaciones entre pares → ruido según backtest → ~0
    # 'balance': equilibrio → ruido → ~0
    # 'ciclos': ciclos → ruido → ~0
    # 'consecutivos': no testado → neutral → ~0
    # 'suma': no testado → neutral → ~0
    'patrones':     -2.0,   # correlaciones = ruido
    'balance':      -1.0,   # equilibrio = ruido
    'ciclos':       -2.0,   # ciclos = ruido
    'consecutivos':  0.0,   # sin datos → neutral
    'suma':          0.0,   # sin datos → neutral
}

print(f'{"Característica":<15} {"Señal backtest":>15}')
for car, señal in mapping.items():
    print(f'  {car:<15} {señal:>+.1f}%')

# Convertir señales a pesos: desplazar para que todo sea positivo, normalizar
MIN_PESO_SUGERIDO = 0.04
señales = mapping.copy()
min_s = min(señales.values())
desplazado = {k: v - min_s + 0.5 for k, v in señales.items()}  # +0.5 de base
total = sum(desplazado.values())
pesos = {k: max(MIN_PESO_SUGERIDO, v / total) for k, v in desplazado.items()}
# Renormalizar después del clip
total2 = sum(pesos.values())
pesos = {k: v / total2 for k, v in pesos.items()}

print()
print(f'{"Característica":<15} {"Peso sugerido":>15}  {"vs actual (0.10)":>18}')
for car in ['frecuencia','tendencia','gap','patrones','balance','ciclos','consecutivos','suma','paridad','decenas']:
    p = pesos[car]
    diff = p - 0.10
    print(f'  {car:<15} {p:>14.3f}  {diff:>+17.3f}')
print()
print(f'Suma de pesos: {sum(pesos.values()):.3f}')
