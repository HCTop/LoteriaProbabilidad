#!/usr/bin/env python3
"""
Simulacion de la Formula del Abuelo - test de multiples configuraciones.
"""

import csv
from itertools import combinations
from collections import defaultdict
import sys

# Fix encoding on Windows
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

def cargar_historico(path):
    sorteos = []
    with open(path, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            sorteos.append({
                'fecha': row['fecha'],
                'numeros': sorted([int(row[f'n{i}']) for i in range(1, 7)]),
                'complementario': int(row['complementario']),
                'reintegro': int(row['reintegro'])
            })
    sorteos.reverse()  # mas antiguo primero
    return sorteos

def seleccionar_candidatos(historico, num_candidatos, max_num=49, nums_por_sorteo=6,
                            peso_frec=0.40, peso_cal=0.35, peso_deb=0.25, ventana_cal=30):
    total = len(historico)
    if total < 30:
        return list(range(1, num_candidatos + 1))

    # FRECUENCIA
    frecuencia = defaultdict(int)
    for sorteo in historico:
        for num in sorteo['numeros']:
            frecuencia[num] += 1

    frec_esperada = total * nums_por_sorteo / max_num
    score_frec = {}
    for num in range(1, max_num + 1):
        diff = frecuencia[num] - frec_esperada
        score_frec[num] = diff / max(frec_esperada, 1.0)

    # CALIENTES
    ventana = min(ventana_cal, total // 3)
    ventana = max(ventana, 10)
    recientes = historico[-ventana:]
    frec_reciente = defaultdict(int)
    for sorteo in recientes:
        for num in sorteo['numeros']:
            frec_reciente[num] += 1

    frec_esp_rec = ventana * nums_por_sorteo / max_num
    score_caliente = {}
    for num in range(1, max_num + 1):
        diff = frec_reciente[num] - frec_esp_rec
        score_caliente[num] = diff / max(frec_esp_rec, 1.0)

    # DEBIDOS
    ultima_aparicion = {}
    for idx, sorteo in enumerate(historico):
        for num in sorteo['numeros']:
            ultima_aparicion[num] = idx

    score_debido = {}
    for num in range(1, max_num + 1):
        ua = ultima_aparicion.get(num, -1)
        gap_actual = total - 1 - ua
        gap_promedio = total / max(frecuencia[num], 1)
        score_debido[num] = (gap_actual - gap_promedio) / max(gap_promedio, 1.0)

    # NORMALIZAR
    def normalizar(scores):
        vals = list(scores.values())
        min_v, max_v = min(vals), max(vals)
        rango = max(max_v - min_v, 0.001)
        return {k: (v - min_v) / rango for k, v in scores.items()}

    norm_frec = normalizar(score_frec)
    norm_cal = normalizar(score_caliente)
    norm_deb = normalizar(score_debido)

    score_final = {}
    for num in range(1, max_num + 1):
        score_final[num] = (peso_frec * norm_frec[num] +
                           peso_cal * norm_cal[num] +
                           peso_deb * norm_deb[num])

    # Equilibrar alto/bajo
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

    # Para muchos candidatos, limitar boletos candidatos con muestreo
    if len(candidatos) <= 20:
        boleto_candidatos = list(combinations(candidatos, k))
    else:
        import random
        rng = random.Random(sum(candidatos))
        boleto_candidatos_set = set()
        for _ in range(3000):
            b = tuple(sorted(rng.sample(candidatos, k)))
            boleto_candidatos_set.add(b)
        boleto_candidatos = list(boleto_candidatos_set)

    t_subs = set(combinations(candidatos, t))
    total_tsubs = len(t_subs)

    cobertura_por_boleto = {}
    for boleto in boleto_candidatos:
        boleto_set = set(boleto)
        cubiertos = set()
        for tsub in t_subs:
            if len(set(tsub) & boleto_set) >= m:
                cubiertos.add(tsub)
        cobertura_por_boleto[boleto] = cubiertos

    tickets = []
    sin_cubrir = set(t_subs)

    while sin_cubrir and len(tickets) < max_tickets:
        mejor_boleto = None
        mejor_cob = 0
        for boleto in boleto_candidatos:
            if boleto in tickets:
                continue
            cob = len(cobertura_por_boleto[boleto] & sin_cubrir)
            if cob > mejor_cob:
                mejor_cob = cob
                mejor_boleto = boleto
        if mejor_boleto is None or mejor_cob == 0:
            break
        tickets.append(mejor_boleto)
        sin_cubrir -= cobertura_por_boleto[mejor_boleto]

    pct = (total_tsubs - len(sin_cubrir)) / total_tsubs * 100 if total_tsubs > 0 else 100
    return [list(t) for t in tickets], pct


def calcular_premio(aciertos, con_complementario):
    if aciertos == 6: return 1_500_000
    elif aciertos == 5 and con_complementario: return 40_000
    elif aciertos == 5: return 2_500
    elif aciertos == 4: return 60
    elif aciertos == 3: return 8
    return 0


def simular(sorteos, config, num_sims=20, verbose=False):
    total_sorteos = len(sorteos)
    inicio = total_sorteos - num_sims

    total_gastado = 0
    total_ganado = 0
    dist_aciertos = defaultdict(int)
    total_cands_acertados = 0
    premios_detalle = []

    for i in range(inicio, total_sorteos):
        sorteo_actual = sorteos[i]
        historico_previo = sorteos[:i]

        candidatos = seleccionar_candidatos(
            historico_previo,
            num_candidatos=config['v'],
            peso_frec=config.get('peso_frec', 0.40),
            peso_cal=config.get('peso_cal', 0.35),
            peso_deb=config.get('peso_deb', 0.25),
            ventana_cal=config.get('ventana', 30)
        )

        tickets, cob_pct = generar_cobertura(
            candidatos,
            k=config['k'], t=config['t'], m=config['m'],
            max_tickets=config.get('max_tickets', 20)
        )
        num_tickets = len(tickets)
        coste = num_tickets
        total_gastado += coste

        numeros_ganadores = set(sorteo_actual['numeros'])
        complementario = sorteo_actual['complementario']

        cands_acertados = len(set(candidatos) & numeros_ganadores)
        total_cands_acertados += cands_acertados

        mejor_ac = 0
        premio_total = 0
        for ticket in tickets:
            ticket_set = set(ticket)
            ac = len(ticket_set & numeros_ganadores)
            con_c = complementario in ticket_set and ac == 5
            p = calcular_premio(ac, con_c)
            premio_total += p
            mejor_ac = max(mejor_ac, ac)

        total_ganado += premio_total
        dist_aciertos[mejor_ac] += 1
        premios_detalle.append(premio_total)

        if verbose:
            acum = total_ganado - total_gastado
            print(f"  {sorteo_actual['fecha']}  {str(sorteo_actual['numeros']):<28} "
                  f"cand={cands_acertados:>2} bol={num_tickets:>2} "
                  f"mejor={mejor_ac} premio={premio_total:>6} acum={acum:>+7}")

    balance = total_ganado - total_gastado
    roi = balance / total_gastado * 100 if total_gastado > 0 else 0
    media_cands = total_cands_acertados / num_sims

    return {
        'gastado': total_gastado,
        'ganado': total_ganado,
        'balance': balance,
        'roi': roi,
        'dist': dict(dist_aciertos),
        'media_cands': media_cands,
        'tickets_por_sorteo': total_gastado / num_sims,
        'premios': premios_detalle
    }


def main():
    path = r'C:\Users\parra\AndroidStudioProjects\LoteriaProbabilidad\app\src\main\res\raw\historico_primitiva.csv'
    sorteos = cargar_historico(path)
    print(f"Historico: {len(sorteos)} sorteos ({sorteos[0]['fecha']} a {sorteos[-1]['fecha']})")
    print()

    NUM_SIMS = 20

    configs = [
        # Config original
        {
            'nombre': 'A) v=18 t=2 m=2 (original)',
            'v': 18, 'k': 6, 't': 2, 'm': 2,
            'peso_frec': 0.40, 'peso_cal': 0.35, 'peso_deb': 0.25,
            'ventana': 30, 'max_tickets': 20
        },
        # Mas candidatos
        {
            'nombre': 'B) v=24 t=2 m=2 (mas candidatos)',
            'v': 24, 'k': 6, 't': 2, 'm': 2,
            'peso_frec': 0.40, 'peso_cal': 0.35, 'peso_deb': 0.25,
            'ventana': 30, 'max_tickets': 20
        },
        # Mas peso a calientes
        {
            'nombre': 'C) v=18 calientes=60%',
            'v': 18, 'k': 6, 't': 2, 'm': 2,
            'peso_frec': 0.20, 'peso_cal': 0.60, 'peso_deb': 0.20,
            'ventana': 20, 'max_tickets': 20
        },
        # Mas peso a debidos
        {
            'nombre': 'D) v=18 debidos=60%',
            'v': 18, 'k': 6, 't': 2, 'm': 2,
            'peso_frec': 0.20, 'peso_cal': 0.20, 'peso_deb': 0.60,
            'ventana': 30, 'max_tickets': 20
        },
        # Solo calientes puros
        {
            'nombre': 'E) v=18 solo calientes ventana=15',
            'v': 18, 'k': 6, 't': 2, 'm': 2,
            'peso_frec': 0.0, 'peso_cal': 1.0, 'peso_deb': 0.0,
            'ventana': 15, 'max_tickets': 20
        },
        # Solo frecuencia pura
        {
            'nombre': 'F) v=18 solo frecuencia',
            'v': 18, 'k': 6, 't': 2, 'm': 2,
            'peso_frec': 1.0, 'peso_cal': 0.0, 'peso_deb': 0.0,
            'ventana': 30, 'max_tickets': 20
        },
        # Pocos candidatos, muchos tickets por garantia 3
        {
            'nombre': 'G) v=12 t=3 m=3 (garantia 3 aciertos)',
            'v': 12, 'k': 6, 't': 3, 'm': 3,
            'peso_frec': 0.40, 'peso_cal': 0.35, 'peso_deb': 0.25,
            'ventana': 30, 'max_tickets': 20
        },
        # v=15 con t=3 m=3
        {
            'nombre': 'H) v=15 t=3 m=3',
            'v': 15, 'k': 6, 't': 3, 'm': 3,
            'peso_frec': 0.40, 'peso_cal': 0.35, 'peso_deb': 0.25,
            'ventana': 30, 'max_tickets': 20
        },
        # Muchos candidatos, pocos boletos
        {
            'nombre': 'I) v=28 t=2 m=2 max=10 boletos',
            'v': 28, 'k': 6, 't': 2, 'm': 2,
            'peso_frec': 0.40, 'peso_cal': 0.35, 'peso_deb': 0.25,
            'ventana': 30, 'max_tickets': 10
        },
        # Calientes + debidos, sin frecuencia historica
        {
            'nombre': 'J) v=20 calientes+debidos ventana=10',
            'v': 20, 'k': 6, 't': 2, 'm': 2,
            'peso_frec': 0.0, 'peso_cal': 0.50, 'peso_deb': 0.50,
            'ventana': 10, 'max_tickets': 20
        },
        # Control: 14 boletos completamente al azar (sin estadistica)
        {
            'nombre': 'Z) CONTROL: 14 boletos al azar',
            'v': 49, 'k': 6, 't': 2, 'm': 2,
            'peso_frec': 0.0, 'peso_cal': 0.0, 'peso_deb': 0.0,
            'ventana': 30, 'max_tickets': 14,
            'es_azar': True
        },
    ]

    # Para el control (azar): usar todos los numeros como candidatos
    # La seleccion sera efectivamente random ya que todos tienen score ~igual

    print("=" * 100)
    print(f"{'CONFIG':<42} {'GAST':>5} {'GAN':>6} {'BAL':>7} {'ROI':>7} "
          f"{'BOL':>4} {'CAND':>5} {'0ac':>3} {'1ac':>3} {'2ac':>3} {'3ac':>3} {'4ac':>3} {'5ac':>3}")
    print("=" * 100)

    for config in configs:
        r = simular(sorteos, config, NUM_SIMS, verbose=False)

        d = r['dist']
        print(f"{config['nombre']:<42} "
              f"{r['gastado']:>4}e {r['ganado']:>5}e {r['balance']:>+6}e {r['roi']:>+6.1f}% "
              f"{r['tickets_por_sorteo']:>4.0f} {r['media_cands']:>5.1f} "
              f"{d.get(0,0):>3} {d.get(1,0):>3} {d.get(2,0):>3} {d.get(3,0):>3} {d.get(4,0):>3} {d.get(5,0):>3}")

    # Detalle de las 2 mejores configs
    print()
    print("=" * 100)
    print("DETALLE DE LAS MEJORES CONFIGURACIONES:")
    print("=" * 100)

    # Encontrar las 2 con mejor ROI
    resultados = []
    for config in configs:
        r = simular(sorteos, config, NUM_SIMS)
        resultados.append((config, r))
    resultados.sort(key=lambda x: x[1]['roi'], reverse=True)

    for config, _ in resultados[:3]:
        print(f"\n--- {config['nombre']} ---")
        simular(sorteos, config, NUM_SIMS, verbose=True)

    # Simulacion mas larga (100 sorteos) de la mejor config
    print()
    print("=" * 100)
    best_config = resultados[0][0]
    print(f"SIMULACION LARGA (100 sorteos) de: {best_config['nombre']}")
    print("=" * 100)
    r100 = simular(sorteos, best_config, 100)
    d = r100['dist']
    print(f"  Gastado: {r100['gastado']}e | Ganado: {r100['ganado']}e | Balance: {r100['balance']:+}e | ROI: {r100['roi']:+.1f}%")
    print(f"  Boletos/sorteo: {r100['tickets_por_sorteo']:.0f} | Media candidatos acertados: {r100['media_cands']:.1f}")
    print(f"  Distribucion: 0ac={d.get(0,0)} 1ac={d.get(1,0)} 2ac={d.get(2,0)} 3ac={d.get(3,0)} 4ac={d.get(4,0)} 5ac={d.get(5,0)} 6ac={d.get(6,0)}")

    # Comparativa coste 1 boleto al azar
    print()
    print("=" * 100)
    print("REFERENCIA: Si hubieras jugado 1 boleto al azar por sorteo (20 sorteos = 20e)")
    print("  EV teorico: 20 * 0.55 = 11e ganados, balance -9e, ROI -45%")

if __name__ == '__main__':
    main()
