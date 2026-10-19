#!/usr/bin/env python3
"""
Analisis de la Primitiva por dia de la semana.
Comprueba si hay numeros que salen mas en ciertos dias.
"""

import csv
from collections import defaultdict
from datetime import datetime
import sys
from itertools import combinations
import math

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

def cargar_historico(path):
    sorteos = []
    with open(path, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            fecha = row['fecha']
            dt = datetime.strptime(fecha, '%Y-%m-%d')
            dia_semana = dt.weekday()  # 0=lunes, 3=jueves, 5=sabado
            sorteos.append({
                'fecha': fecha,
                'dia': dia_semana,
                'dia_nombre': ['LUN','MAR','MIE','JUE','VIE','SAB','DOM'][dia_semana],
                'numeros': sorted([int(row[f'n{i}']) for i in range(1, 7)]),
                'complementario': int(row['complementario']),
                'reintegro': int(row['reintegro'])
            })
    sorteos.reverse()
    return sorteos

def analizar_por_dia(sorteos):
    """Analiza frecuencias por dia de la semana."""
    dias = defaultdict(list)
    for s in sorteos:
        dias[s['dia_nombre']].append(s)

    print("=" * 80)
    print("DISTRIBUCION DE SORTEOS POR DIA")
    print("=" * 80)
    for dia in ['LUN','JUE','SAB']:
        print(f"  {dia}: {len(dias[dia])} sorteos")
    print()

    # Para cada dia, calcular los 10 numeros mas frecuentes y los 10 menos
    print("=" * 80)
    print("TOP 10 NUMEROS MAS FRECUENTES POR DIA (vs frecuencia esperada)")
    print("=" * 80)

    frecuencias_por_dia = {}
    for dia in ['LUN','JUE','SAB']:
        if not dias[dia]:
            continue
        freq = defaultdict(int)
        total = len(dias[dia])
        for s in dias[dia]:
            for n in s['numeros']:
                freq[n] += 1

        esperada = total * 6 / 49
        # Chi-cuadrado por numero
        scores = {}
        for num in range(1, 50):
            obs = freq[num]
            chi2 = (obs - esperada)**2 / esperada
            exceso_pct = (obs - esperada) / esperada * 100
            scores[num] = {'obs': obs, 'esp': esperada, 'chi2': chi2, 'exceso': exceso_pct}

        frecuencias_por_dia[dia] = scores

        top = sorted(scores.items(), key=lambda x: x[1]['obs'], reverse=True)[:10]
        print(f"\n  {dia} ({total} sorteos, esperado {esperada:.1f} por numero):")
        for num, s in top:
            print(f"    #{num:>2}: {s['obs']:>3} veces ({s['exceso']:>+5.1f}% vs esperado)")

    # Numeros que son top en un dia pero NO en otros
    print()
    print("=" * 80)
    print("NUMEROS EXCLUSIVOS DE CADA DIA (top 15 de un dia, fuera del top 15 en otros)")
    print("=" * 80)

    for dia in ['LUN','JUE','SAB']:
        if dia not in frecuencias_por_dia:
            continue
        scores = frecuencias_por_dia[dia]
        top15_este = set(num for num, _ in sorted(scores.items(), key=lambda x: x[1]['obs'], reverse=True)[:15])

        otros_dias = [d for d in ['LUN','JUE','SAB'] if d != dia and d in frecuencias_por_dia]
        top15_otros = set()
        for od in otros_dias:
            top15_otros.update(num for num, _ in sorted(frecuencias_por_dia[od].items(),
                              key=lambda x: x[1]['obs'], reverse=True)[:15])

        exclusivos = top15_este - top15_otros
        if exclusivos:
            print(f"\n  {dia} exclusivos: {sorted(exclusivos)}")
            for num in sorted(exclusivos):
                s = scores[num]
                print(f"    #{num:>2}: {s['obs']:>3} veces ({s['exceso']:>+5.1f}%)")
        else:
            print(f"\n  {dia}: ninguno exclusivo (los top se repiten entre dias)")

    return frecuencias_por_dia


def seleccionar_candidatos_por_dia(historico_previo, dia_objetivo, num_candidatos=18,
                                    peso_frec_dia=0.40, peso_cal=0.35, peso_deb_dia=0.25,
                                    ventana=20):
    """Selecciona candidatos usando SOLO los sorteos del mismo dia de la semana."""
    max_num = 49
    nums_por_sorteo = 6

    # Filtrar solo sorteos del mismo dia
    sorteos_dia = [s for s in historico_previo if s['dia'] == dia_objetivo]
    # Todos los sorteos (para calientes recientes, que son de cualquier dia)
    todos = historico_previo

    if len(sorteos_dia) < 20:
        # Fallback: usar todos
        sorteos_dia = todos

    total_dia = len(sorteos_dia)
    total_todos = len(todos)

    # FRECUENCIA del mismo dia
    freq_dia = defaultdict(int)
    for s in sorteos_dia:
        for n in s['numeros']:
            freq_dia[n] += 1
    frec_esp_dia = total_dia * nums_por_sorteo / max_num

    score_frec = {}
    for num in range(1, max_num + 1):
        diff = freq_dia[num] - frec_esp_dia
        score_frec[num] = diff / max(frec_esp_dia, 1.0)

    # CALIENTES recientes (ultimos N sorteos de CUALQUIER dia)
    ventana_real = min(ventana, total_todos // 3)
    ventana_real = max(ventana_real, 10)
    recientes = todos[-ventana_real:]
    freq_rec = defaultdict(int)
    for s in recientes:
        for n in s['numeros']:
            freq_rec[n] += 1
    frec_esp_rec = ventana_real * nums_por_sorteo / max_num

    score_cal = {}
    for num in range(1, max_num + 1):
        diff = freq_rec[num] - frec_esp_rec
        score_cal[num] = diff / max(frec_esp_rec, 1.0)

    # DEBIDOS del mismo dia
    ultima_ap_dia = {}
    for idx, s in enumerate(sorteos_dia):
        for n in s['numeros']:
            ultima_ap_dia[n] = idx

    score_deb = {}
    for num in range(1, max_num + 1):
        ua = ultima_ap_dia.get(num, -1)
        gap = total_dia - 1 - ua
        gap_prom = total_dia / max(freq_dia[num], 1)
        score_deb[num] = (gap - gap_prom) / max(gap_prom, 1.0)

    # Normalizar
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
        score_final[num] = peso_frec_dia * nf[num] + peso_cal * nc[num] + peso_deb_dia * nd[num]

    # Equilibrar
    ordenados = sorted(range(1, max_num + 1), key=lambda n: score_final[n], reverse=True)
    mitad = max_num // 2
    bajos = [n for n in ordenados if n <= mitad]
    altos = [n for n in ordenados if n > mitad]
    resultado = []
    ib, ia = 0, 0
    while len(resultado) < num_candidatos:
        if ib < len(bajos) and (ia >= len(altos) or ib <= ia):
            resultado.append(bajos[ib]); ib += 1
        elif ia < len(altos):
            resultado.append(altos[ia]); ia += 1
        else:
            break
    return resultado[:num_candidatos]


def seleccionar_candidatos_sin_dia(historico_previo, num_candidatos=18, ventana=20):
    """Seleccion normal sin filtrar por dia (para comparar)."""
    max_num = 49
    nums_por_sorteo = 6
    total = len(historico_previo)
    if total < 20:
        return list(range(1, num_candidatos + 1))

    freq = defaultdict(int)
    for s in historico_previo:
        for n in s['numeros']:
            freq[n] += 1
    frec_esp = total * nums_por_sorteo / max_num

    score_frec = {}
    for num in range(1, max_num + 1):
        score_frec[num] = (freq[num] - frec_esp) / max(frec_esp, 1.0)

    ventana_real = min(ventana, total // 3)
    ventana_real = max(ventana_real, 10)
    recientes = historico_previo[-ventana_real:]
    freq_rec = defaultdict(int)
    for s in recientes:
        for n in s['numeros']:
            freq_rec[n] += 1
    frec_esp_rec = ventana_real * nums_por_sorteo / max_num

    score_cal = {}
    for num in range(1, max_num + 1):
        score_cal[num] = (freq_rec[num] - frec_esp_rec) / max(frec_esp_rec, 1.0)

    ultima_ap = {}
    for idx, s in enumerate(historico_previo):
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
    mitad = max_num // 2
    bajos = [n for n in ordenados if n <= mitad]
    altos = [n for n in ordenados if n > mitad]
    resultado = []
    ib, ia = 0, 0
    while len(resultado) < num_candidatos:
        if ib < len(bajos) and (ia >= len(altos) or ib <= ia):
            resultado.append(bajos[ib]); ib += 1
        elif ia < len(altos):
            resultado.append(altos[ia]); ia += 1
        else:
            break
    return resultado[:num_candidatos]


def generar_cobertura(candidatos, k=6, t=2, m=2, max_tickets=20):
    candidatos = sorted(candidatos)
    if len(candidatos) <= 20:
        boletos = list(combinations(candidatos, k))
    else:
        import random
        rng = random.Random(sum(candidatos))
        s = set()
        for _ in range(3000):
            s.add(tuple(sorted(rng.sample(candidatos, k))))
        boletos = list(s)

    t_subs = set(combinations(candidatos, t))
    total = len(t_subs)
    cob = {}
    for b in boletos:
        bs = set(b)
        cob[b] = {ts for ts in t_subs if len(set(ts) & bs) >= m}

    tickets = []
    sin = set(t_subs)
    while sin and len(tickets) < max_tickets:
        mejor, mc = None, 0
        for b in boletos:
            if b in tickets: continue
            c = len(cob[b] & sin)
            if c > mc: mc, mejor = c, b
        if not mejor or mc == 0: break
        tickets.append(mejor)
        sin -= cob[mejor]

    pct = (total - len(sin)) / total * 100 if total > 0 else 100
    return [list(t) for t in tickets], pct


def calcular_premio(ac, con_c):
    if ac == 6: return 1_500_000
    if ac == 5 and con_c: return 40_000
    if ac == 5: return 2_500
    if ac == 4: return 60
    if ac == 3: return 8
    return 0


def simular_comparativa(sorteos, num_sims=20):
    total = len(sorteos)
    inicio = total - num_sims

    print()
    print("=" * 110)
    print(f"{'FECHA':<12} {'DIA':>3} {'RESULTADO':<28} "
          f"{'--- CON DIA ---':^25} {'--- SIN DIA ---':^25} {'DIFF':>5}")
    print(f"{'':>43} {'cand':>4} {'mejor':>5} {'prem':>7} "
          f"{'cand':>4} {'mejor':>5} {'prem':>7} {'':>5}")
    print("=" * 110)

    totales = {'con': {'gasto': 0, 'ganado': 0, 'cands': 0},
               'sin': {'gasto': 0, 'ganado': 0, 'cands': 0}}
    dist_con = defaultdict(int)
    dist_sin = defaultdict(int)

    for i in range(inicio, total):
        s = sorteos[i]
        hist = sorteos[:i]
        nums = set(s['numeros'])
        compl = s['complementario']

        # CON filtro por dia
        cands_dia = seleccionar_candidatos_por_dia(hist, s['dia'])
        tickets_dia, _ = generar_cobertura(cands_dia)
        n_tick_dia = len(tickets_dia)

        # SIN filtro por dia
        cands_gen = seleccionar_candidatos_sin_dia(hist)
        tickets_gen, _ = generar_cobertura(cands_gen)
        n_tick_gen = len(tickets_gen)

        # Evaluar CON dia
        mejor_dia, prem_dia = 0, 0
        for t in tickets_dia:
            ac = len(set(t) & nums)
            cc = compl in set(t) and ac == 5
            p = calcular_premio(ac, cc)
            prem_dia += p
            mejor_dia = max(mejor_dia, ac)

        # Evaluar SIN dia
        mejor_gen, prem_gen = 0, 0
        for t in tickets_gen:
            ac = len(set(t) & nums)
            cc = compl in set(t) and ac == 5
            p = calcular_premio(ac, cc)
            prem_gen += p
            mejor_gen = max(mejor_gen, ac)

        cands_dia_ac = len(set(cands_dia) & nums)
        cands_gen_ac = len(set(cands_gen) & nums)

        totales['con']['gasto'] += n_tick_dia
        totales['con']['ganado'] += prem_dia
        totales['con']['cands'] += cands_dia_ac
        totales['sin']['gasto'] += n_tick_gen
        totales['sin']['ganado'] += prem_gen
        totales['sin']['cands'] += cands_gen_ac
        dist_con[mejor_dia] += 1
        dist_sin[mejor_gen] += 1

        diff = prem_dia - prem_gen
        diff_str = f"{diff:>+5}" if diff != 0 else "    ="

        print(f"{s['fecha']:<12} {s['dia_nombre']:>3} {str(s['numeros']):<28} "
              f"{cands_dia_ac:>4} {mejor_dia:>5} {prem_dia:>6}e "
              f"{cands_gen_ac:>4} {mejor_gen:>5} {prem_gen:>6}e "
              f"{diff_str}")

    print()
    print("=" * 110)
    print("RESUMEN COMPARATIVO")
    print("=" * 110)

    for tipo, label in [('con', 'CON filtro por dia'), ('sin', 'SIN filtro (general)')]:
        t = totales[tipo]
        bal = t['ganado'] - t['gasto']
        roi = bal / t['gasto'] * 100 if t['gasto'] > 0 else 0
        media_c = t['cands'] / num_sims
        d = dist_con if tipo == 'con' else dist_sin
        print(f"\n  {label}:")
        print(f"    Gastado: {t['gasto']}e | Ganado: {t['ganado']}e | Balance: {bal:+}e | ROI: {roi:+.1f}%")
        print(f"    Media candidatos acertados: {media_c:.1f}/sorteo")
        print(f"    Distribucion: ", end="")
        for ac in range(7):
            if d.get(ac, 0) > 0:
                print(f"{ac}ac={d[ac]} ", end="")
        print()


def main():
    path = r'C:\Users\parra\AndroidStudioProjects\LoteriaProbabilidad\app\src\main\res\raw\historico_primitiva.csv'
    sorteos = cargar_historico(path)
    print(f"Historico: {len(sorteos)} sorteos\n")

    # 1. Analisis de frecuencias por dia
    freq_dia = analizar_por_dia(sorteos)

    # 2. Simulacion comparativa: con dia vs sin dia (20 sorteos)
    print("\n\nSIMULACION: 20 ultimos sorteos")
    simular_comparativa(sorteos, 20)

    # 3. Simulacion larga (100 sorteos)
    print("\n\nSIMULACION LARGA: 100 ultimos sorteos")
    simular_comparativa(sorteos, 100)

if __name__ == '__main__':
    main()
