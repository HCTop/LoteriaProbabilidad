#!/usr/bin/env python3
"""
Analisis de la Primitiva por año.
Comprueba si los numeros calientes cambian por año/epoca
y si usar ventanas temporales mejora la prediccion.
"""

import csv
from collections import defaultdict
from datetime import datetime
import sys
from itertools import combinations

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

def cargar_historico(path):
    sorteos = []
    with open(path, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            fecha = row['fecha']
            dt = datetime.strptime(fecha, '%Y-%m-%d')
            sorteos.append({
                'fecha': fecha,
                'dt': dt,
                'anio': dt.year,
                'numeros': sorted([int(row[f'n{i}']) for i in range(1, 7)]),
                'complementario': int(row['complementario']),
                'reintegro': int(row['reintegro'])
            })
    sorteos.reverse()
    return sorteos

def analizar_por_anio(sorteos):
    """Muestra los top numeros por año y cómo cambian."""
    anios = defaultdict(list)
    for s in sorteos:
        anios[s['anio']].append(s)

    print("=" * 100)
    print("TOP 10 NUMEROS MAS FRECUENTES POR AÑO")
    print("=" * 100)

    tops_por_anio = {}
    for anio in sorted(anios.keys()):
        if len(anios[anio]) < 20:
            continue
        freq = defaultdict(int)
        total = len(anios[anio])
        for s in anios[anio]:
            for n in s['numeros']:
                freq[n] += 1
        esperada = total * 6 / 49
        top10 = sorted(range(1, 50), key=lambda n: freq[n], reverse=True)[:10]
        tops_por_anio[anio] = set(top10)
        top_str = ", ".join(f"{n:>2}({freq[n]})" for n in top10)
        print(f"  {anio} ({total:>3} sorteos, esp={esperada:.0f}): {top_str}")

    # Persistencia: cuantos del top10 se repiten al año siguiente
    print()
    print("=" * 100)
    print("PERSISTENCIA: cuantos del top 10 de un año repiten en el siguiente")
    print("=" * 100)
    anios_ord = sorted(tops_por_anio.keys())
    for i in range(1, len(anios_ord)):
        a_prev = anios_ord[i-1]
        a_curr = anios_ord[i]
        comunes = tops_por_anio[a_prev] & tops_por_anio[a_curr]
        print(f"  {a_prev}->{a_curr}: {len(comunes)}/10 repiten ({sorted(comunes)})")

    # Persistencia en ventanas de 6 meses
    print()
    print("=" * 100)
    print("TOP 10 POR SEMESTRE (ultimos 10 años)")
    print("=" * 100)

    semestres = defaultdict(list)
    for s in sorteos:
        if s['anio'] >= 2015:
            sem = f"{s['anio']}-{'H1' if s['dt'].month <= 6 else 'H2'}"
            semestres[sem].append(s)

    tops_sem = {}
    for sem in sorted(semestres.keys()):
        freq = defaultdict(int)
        total = len(semestres[sem])
        if total < 10:
            continue
        for s in semestres[sem]:
            for n in s['numeros']:
                freq[n] += 1
        top10 = sorted(range(1, 50), key=lambda n: freq[n], reverse=True)[:10]
        tops_sem[sem] = set(top10)
        top_str = ", ".join(f"{n:>2}({freq[n]})" for n in top10)
        print(f"  {sem} ({total:>3} sorteos): {top_str}")

    sems_ord = sorted(tops_sem.keys())
    print()
    for i in range(1, len(sems_ord)):
        comunes = tops_sem[sems_ord[i-1]] & tops_sem[sems_ord[i]]
        print(f"  {sems_ord[i-1]}->{sems_ord[i]}: {len(comunes)}/10 repiten")


def seleccionar_candidatos_ventana(historico_previo, ventana_sorteos, num_candidatos=18):
    """Selecciona candidatos usando solo los ultimos N sorteos."""
    max_num = 49
    nums_por_sorteo = 6

    recientes = historico_previo[-ventana_sorteos:] if len(historico_previo) > ventana_sorteos else historico_previo
    total = len(recientes)
    if total < 10:
        return list(range(1, num_candidatos + 1))

    # Frecuencia en ventana
    freq = defaultdict(int)
    for s in recientes:
        for n in s['numeros']:
            freq[n] += 1
    frec_esp = total * nums_por_sorteo / max_num

    score_frec = {}
    for num in range(1, max_num + 1):
        score_frec[num] = (freq[num] - frec_esp) / max(frec_esp, 1.0)

    # Calientes recientes (ultimos 15 dentro de la ventana)
    cal_ventana = min(15, total // 2)
    cal_ventana = max(cal_ventana, 5)
    cal_recientes = recientes[-cal_ventana:]
    freq_cal = defaultdict(int)
    for s in cal_recientes:
        for n in s['numeros']:
            freq_cal[n] += 1
    frec_esp_cal = cal_ventana * nums_por_sorteo / max_num

    score_cal = {}
    for num in range(1, max_num + 1):
        score_cal[num] = (freq_cal[num] - frec_esp_cal) / max(frec_esp_cal, 1.0)

    # Debidos dentro de la ventana
    ultima_ap = {}
    for idx, s in enumerate(recientes):
        for n in s['numeros']:
            ultima_ap[n] = idx
    score_deb = {}
    for num in range(1, max_num + 1):
        ua = ultima_ap.get(num, -1)
        gap = total - 1 - ua
        gap_prom = total / max(freq[num], 1)
        score_deb[num] = (gap - gap_prom) / max(gap_prom, 1.0)

    def normalizar(scores):
        vals = list(scores.values())
        min_v, max_v = min(vals), max(vals)
        rango = max(max_v - min_v, 0.001)
        return {k: (v - min_v) / rango for k, v in scores.items()}

    nf = normalizar(score_frec)
    nc = normalizar(score_cal)
    nd = normalizar(score_deb)

    score_final = {}
    for num in range(1, max_num + 1):
        score_final[num] = 0.40 * nf[num] + 0.35 * nc[num] + 0.25 * nd[num]

    ordenados = sorted(range(1, max_num + 1), key=lambda n: score_final[n], reverse=True)
    return ordenados[:num_candidatos]


def seleccionar_todo_historico(historico_previo, num_candidatos=18):
    """Selecciona usando TODO el historico (como referencia)."""
    return seleccionar_candidatos_ventana(historico_previo, len(historico_previo), num_candidatos)


def generar_cobertura(candidatos, k=6, max_tickets=14):
    """Genera boletos simplificado: toma los primeros max_tickets combos greedy."""
    import random
    candidatos = sorted(candidatos)
    rng = random.Random(sum(candidatos))
    # Greedy: cada boleto maximiza numeros nuevos cubiertos
    usados = set()
    tickets = []
    # Generar pool de combinaciones
    pool = []
    for _ in range(2000):
        pool.append(tuple(sorted(rng.sample(candidatos, k))))
    pool = list(set(pool))

    for _ in range(max_tickets):
        mejor, mejor_new = None, -1
        for combo in pool:
            if combo in tickets:
                continue
            new = len(set(combo) - usados)
            if new > mejor_new:
                mejor_new = new
                mejor = combo
        if mejor is None:
            break
        tickets.append(mejor)
        usados.update(mejor)

    return [list(t) for t in tickets]


def calcular_premio(ac, con_c):
    if ac == 6: return 1_500_000
    if ac == 5 and con_c: return 40_000
    if ac == 5: return 2_500
    if ac == 4: return 60
    if ac == 3: return 8
    return 0


def simular_ventanas(sorteos, num_sims=100):
    """Compara distintas ventanas temporales."""
    total = len(sorteos)
    inicio = total - num_sims

    ventanas = [
        ("TODO", None),       # todo el historico
        ("5 años", 5*156),    # ~156 sorteos/año (3/semana)
        ("3 años", 3*156),
        ("2 años", 2*156),
        ("1 año", 156),
        ("6 meses", 78),
        ("3 meses", 39),
        ("50 sorteos", 50),
    ]

    resultados = {}
    for vi, (label, vent) in enumerate(ventanas):
        print(f"  Simulando {label}... ({vi+1}/{len(ventanas)})", flush=True)
        stats = {'gasto': 0, 'ganado': 0, 'cands': 0, 'dist': defaultdict(int)}
        for i in range(inicio, total):
            s = sorteos[i]
            hist = sorteos[:i]
            nums = set(s['numeros'])
            compl = s['complementario']

            if vent is None:
                cands = seleccionar_todo_historico(hist)
            else:
                cands = seleccionar_candidatos_ventana(hist, vent)

            tickets = generar_cobertura(cands)
            n_tick = len(tickets)

            mejor, prem = 0, 0
            for t in tickets:
                ac = len(set(t) & nums)
                cc = compl in set(t) and ac == 5
                p = calcular_premio(ac, cc)
                prem += p
                mejor = max(mejor, ac)

            cands_ac = len(set(cands) & nums)
            stats['gasto'] += n_tick
            stats['ganado'] += prem
            stats['cands'] += cands_ac
            stats['dist'][mejor] += 1

        resultados[label] = stats

    # Mostrar tabla resumen
    print()
    print("=" * 110)
    print(f"COMPARATIVA DE VENTANAS TEMPORALES ({num_sims} sorteos)")
    print("=" * 110)
    print(f"{'VENTANA':<15} {'GAST':>6} {'GAN':>6} {'BAL':>7} {'ROI':>7} "
          f"{'BOL':>4} {'CAND':>5} {'0ac':>4} {'1ac':>4} {'2ac':>4} {'3ac':>4} {'4ac':>4} {'5ac':>4}")
    print("-" * 110)

    for label, vent in ventanas:
        st = resultados[label]
        bal = st['ganado'] - st['gasto']
        roi = bal / st['gasto'] * 100 if st['gasto'] > 0 else 0
        media_c = st['cands'] / num_sims
        d = st['dist']
        print(f"{label:<15} {st['gasto']:>5}e {st['ganado']:>5}e {bal:>+6}e {roi:>+6.1f}% "
              f"{st['gasto']//num_sims:>4} {media_c:>5.1f} "
              f"{d.get(0,0):>4} {d.get(1,0):>4} {d.get(2,0):>4} {d.get(3,0):>4} {d.get(4,0):>4} {d.get(5,0):>4}")

    # Control: azar
    import random
    rng = random.Random(42)
    stats_azar = {'gasto': 0, 'ganado': 0, 'cands': 0, 'dist': defaultdict(int)}
    for i in range(inicio, total):
        s = sorteos[i]
        nums = set(s['numeros'])
        compl = s['complementario']
        cands = rng.sample(range(1, 50), 18)
        tickets = generar_cobertura(cands)
        n_tick = len(tickets)
        mejor, prem = 0, 0
        for t in tickets:
            ac = len(set(t) & nums)
            cc = compl in set(t) and ac == 5
            p = calcular_premio(ac, cc)
            prem += p
            mejor = max(mejor, ac)
        stats_azar['gasto'] += n_tick
        stats_azar['ganado'] += prem
        stats_azar['cands'] += len(set(cands) & nums)
        stats_azar['dist'][mejor] += 1

    st = stats_azar
    bal = st['ganado'] - st['gasto']
    roi = bal / st['gasto'] * 100 if st['gasto'] > 0 else 0
    media_c = st['cands'] / num_sims
    d = st['dist']
    print(f"{'AZAR':<15} {st['gasto']:>5}e {st['ganado']:>5}e {bal:>+6}e {roi:>+6.1f}% "
          f"{st['gasto']//num_sims:>4} {media_c:>5.1f} "
          f"{d.get(0,0):>4} {d.get(1,0):>4} {d.get(2,0):>4} {d.get(3,0):>4} {d.get(4,0):>4} {d.get(5,0):>4}")


def analizar_cambio_numeros(sorteos):
    """Mide cuanto cambian los top candidatos entre sorteos consecutivos."""
    total = len(sorteos)
    inicio = total - 50  # ultimos 50

    print()
    print("=" * 100)
    print("ESTABILIDAD DE CANDIDATOS: cuantos de 18 cambian entre sorteo y sorteo")
    print("=" * 100)

    for vent_label, vent in [("TODO", None), ("1 año", 156), ("6 meses", 78), ("50 sorteos", 50)]:
        cambios = []
        prev_cands = None
        for i in range(inicio, total):
            hist = sorteos[:i]
            if vent is None:
                cands = set(seleccionar_todo_historico(hist))
            else:
                cands = set(seleccionar_candidatos_ventana(hist, vent))
            if prev_cands is not None:
                cambios.append(len(cands - prev_cands))
            prev_cands = cands

        media = sum(cambios) / len(cambios) if cambios else 0
        maximo = max(cambios) if cambios else 0
        print(f"  {vent_label:<12}: media {media:.1f} cambios/sorteo, max {maximo}")


def main():
    path = r'C:\Users\parra\AndroidStudioProjects\LoteriaProbabilidad\app\src\main\res\raw\historico_primitiva.csv'
    sorteos = cargar_historico(path)
    print(f"Historico: {len(sorteos)} sorteos ({sorteos[0]['fecha']} a {sorteos[-1]['fecha']})\n")

    # 1. Top numeros por año
    analizar_por_anio(sorteos)

    # 2. Estabilidad de candidatos
    analizar_cambio_numeros(sorteos)

    # 3. Simulacion comparativa de ventanas (20 sorteos rapido)
    print("\n\n--- SIMULACION RAPIDA (20 sorteos) ---")
    simular_ventanas(sorteos, 20)

    # 4. Simulacion larga (100 sorteos)
    print("\n\n--- SIMULACION LARGA (100 sorteos) ---")
    simular_ventanas(sorteos, 100)

if __name__ == '__main__':
    main()
