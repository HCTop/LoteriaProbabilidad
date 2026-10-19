#!/usr/bin/env python3
"""
Verificación: replica EXACTAMENTE el algoritmo Kotlin de obtenerCandidatosDelHistorico()
del UseCase y muestra los 18 candidatos que la app debería generar.

El algoritmo Kotlin (ObtenerCombinacionesUseCase.kt líneas 250-366):
1. FRECUENCIA (40%): (freq[n] - esperada) / esperada  (todo el histórico)
2. CALIENTES (35%): igual pero con ventana = min(30, total/3).coerceAtLeast(10)
3. DEBIDOS (25%): (gapActual - gapPromedio) / gapPromedio
4. Normalizar cada score 0-1
5. Combinar: 0.40*frec + 0.35*cal + 0.25*deb
6. Ordenar desc, luego alternar bajo/alto para equilibrar
"""

import csv
from collections import defaultdict
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

def cargar_historico(path):
    sorteos = []
    with open(path, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            sorteos.append({
                'fecha': row['fecha'],
                'numeros': sorted([int(row[f'n{i}']) for i in range(1, 7)]),
            })
    # La app ahora invierte: sorteos[0]=más antiguo, sorteos[-1]=más reciente
    # (orden cronológico, para que takeLast tome los recientes)
    sorteos.reverse()
    return sorteos


def obtener_candidatos_kotlin(sorteos, max_num=49, num_por_sorteo=6, num_candidatos=18):
    """
    Replica EXACTA del algoritmo Kotlin en ObtenerCombinacionesUseCase.kt
    """
    total = len(sorteos)
    if total == 0:
        return list(range(1, num_candidatos + 1))

    # ═══ CRITERIO 1: FRECUENCIA ═══
    frecuencia = [0] * (max_num + 1)
    for s in sorteos:
        for n in s['numeros']:
            if 1 <= n <= max_num:
                frecuencia[n] += 1

    frec_esperada = total * num_por_sorteo / max_num
    score_frecuencia = [0.0] * (max_num + 1)
    for num in range(1, max_num + 1):
        diff = frecuencia[num] - frec_esperada
        score_frecuencia[num] = diff / max(frec_esperada, 1.0)

    # ═══ CRITERIO 2: CALIENTES ═══
    # Kotlin: minOf(30, totalSorteos / 3).coerceAtLeast(10)
    ventana_caliente = min(30, total // 3)
    ventana_caliente = max(ventana_caliente, 10)
    sorteos_recientes = sorteos[-ventana_caliente:]

    frec_reciente = [0] * (max_num + 1)
    for s in sorteos_recientes:
        for n in s['numeros']:
            if 1 <= n <= max_num:
                frec_reciente[n] += 1

    frec_esperada_reciente = ventana_caliente * num_por_sorteo / max_num
    score_caliente = [0.0] * (max_num + 1)
    for num in range(1, max_num + 1):
        diff = frec_reciente[num] - frec_esperada_reciente
        score_caliente[num] = diff / max(frec_esperada_reciente, 1.0)

    # ═══ CRITERIO 3: DEBIDOS ═══
    ultima_aparicion = [-1] * (max_num + 1)
    for idx, s in enumerate(sorteos):
        for n in s['numeros']:
            if 1 <= n <= max_num:
                ultima_aparicion[n] = idx

    gap_promedio = [0.0] * (max_num + 1)
    for num in range(1, max_num + 1):
        if frecuencia[num] > 1:
            gap_promedio[num] = total / frecuencia[num]
        else:
            gap_promedio[num] = float(total)

    score_debido = [0.0] * (max_num + 1)
    for num in range(1, max_num + 1):
        gap_actual = total - 1 - ultima_aparicion[num]
        if gap_promedio[num] > 0:
            score_debido[num] = (gap_actual - gap_promedio[num]) / gap_promedio[num]
        else:
            score_debido[num] = 0.0

    # ═══ NORMALIZAR 0-1 ═══
    def normalizar(scores):
        vals = [scores[n] for n in range(1, max_num + 1)]
        min_v = min(vals)
        max_v = max(vals)
        rango = max(max_v - min_v, 0.001)
        result = [0.0] * (max_num + 1)
        for num in range(1, max_num + 1):
            result[num] = (scores[num] - min_v) / rango
        return result

    norm_frec = normalizar(score_frecuencia)
    norm_cal = normalizar(score_caliente)
    norm_deb = normalizar(score_debido)

    # ═══ COMBINAR ═══
    score_final = [0.0] * (max_num + 1)
    for num in range(1, max_num + 1):
        score_final[num] = 0.40 * norm_frec[num] + 0.35 * norm_cal[num] + 0.25 * norm_deb[num]

    # ═══ SELECCIONAR TOP V CON EQUILIBRIO ALTO/BAJO ═══
    # Kotlin: sortedByDescending { scoreFinal[it] }
    candidatos_ordenados = sorted(range(1, max_num + 1), key=lambda n: score_final[n], reverse=True)

    mitad = max_num // 2  # 24 para Primitiva (49//2=24)
    candidatos_bajos = [n for n in candidatos_ordenados if n <= mitad]
    candidatos_altos = [n for n in candidatos_ordenados if n > mitad]

    # Alternar bajo-alto
    resultado = []
    i_bajo = 0
    i_alto = 0
    while len(resultado) < num_candidatos:
        if i_bajo < len(candidatos_bajos) and (i_alto >= len(candidatos_altos) or i_bajo <= i_alto):
            resultado.append(candidatos_bajos[i_bajo])
            i_bajo += 1
        elif i_alto < len(candidatos_altos):
            resultado.append(candidatos_altos[i_alto])
            i_alto += 1
        else:
            break

    return resultado[:num_candidatos]


def main():
    path = r'C:\Users\parra\AndroidStudioProjects\LoteriaProbabilidad\app\src\main\res\raw\historico_primitiva.csv'
    sorteos = cargar_historico(path)

    total = len(sorteos)
    max_num = 49
    num_por_sorteo = 6

    ventana_caliente = max(min(30, total // 3), 10)
    sorteos_recientes = sorteos[-ventana_caliente:]
    frec_esperada = total * num_por_sorteo / max_num
    frec_esperada_reciente = ventana_caliente * num_por_sorteo / max_num

    # Imprimir en FORMATO IDENTICO al logcat de Kotlin para comparacion directa
    print("=== VERIFICACION CANDIDATOS ===")
    print(f"totalSorteos={total} maxNum={max_num} ventanaCaliente={ventana_caliente}")
    print(f"frecEsperada={frec_esperada} frecEspReciente={frec_esperada_reciente}")
    print(f"Primer sorteo: {sorteos[0]['numeros']}")
    print(f"Ultimo sorteo: {sorteos[-1]['numeros']}")

    # Calcular todas las frecuencias y scores
    frecuencia = [0] * (max_num + 1)
    for s in sorteos:
        for n in s['numeros']:
            frecuencia[n] += 1

    frec_reciente = [0] * (max_num + 1)
    for s in sorteos_recientes:
        for n in s['numeros']:
            frec_reciente[n] += 1

    ultima_aparicion = [-1] * (max_num + 1)
    for idx, s in enumerate(sorteos):
        for n in s['numeros']:
            ultima_aparicion[n] = idx

    gap_promedio = [0.0] * (max_num + 1)
    for num in range(1, max_num + 1):
        gap_promedio[num] = total / frecuencia[num] if frecuencia[num] > 1 else float(total)

    score_frecuencia = [0.0] * (max_num + 1)
    for num in range(1, max_num + 1):
        score_frecuencia[num] = (frecuencia[num] - frec_esperada) / max(frec_esperada, 1.0)

    score_caliente = [0.0] * (max_num + 1)
    for num in range(1, max_num + 1):
        score_caliente[num] = (frec_reciente[num] - frec_esperada_reciente) / max(frec_esperada_reciente, 1.0)

    score_debido = [0.0] * (max_num + 1)
    for num in range(1, max_num + 1):
        gap_actual = total - 1 - ultima_aparicion[num]
        if gap_promedio[num] > 0:
            score_debido[num] = (gap_actual - gap_promedio[num]) / gap_promedio[num]

    def normalizar(scores):
        vals = [scores[n] for n in range(1, max_num + 1)]
        min_v, max_v = min(vals), max(vals)
        rango = max(max_v - min_v, 0.001)
        result = [0.0] * (max_num + 1)
        for num in range(1, max_num + 1):
            result[num] = (scores[num] - min_v) / rango
        return result

    nf = normalizar(score_frecuencia)
    nc = normalizar(score_caliente)
    nd = normalizar(score_debido)

    score_final = [0.0] * (max_num + 1)
    for num in range(1, max_num + 1):
        score_final[num] = 0.40 * nf[num] + 0.35 * nc[num] + 0.25 * nd[num]

    # Top-25 en formato IDENTICO al logcat de Kotlin
    ranking = sorted(range(1, max_num + 1), key=lambda n: score_final[n], reverse=True)
    for num in ranking[:25]:
        gap_actual = total - 1 - ultima_aparicion[num]
        print(f"#{num} frec={frecuencia[num]} frecRec={frec_reciente[num]} gap={gap_actual} "
              f"gapProm={gap_promedio[num]:.1f} nF={nf[num]:.4f} nC={nc[num]:.4f} "
              f"nD={nd[num]:.4f} TOTAL={score_final[num]:.4f}")

    # Candidatos finales
    candidatos = obtener_candidatos_kotlin(sorteos)
    print(f"Resultado final: {sorted(candidatos)}")
    print()

    # Calcular candidatos con TODO el historico
    print("=" * 80)
    print("CANDIDATOS QUE LA APP DEBERIA GENERAR (algoritmo Kotlin exacto)")
    print("=" * 80)
    print(f"  Candidatos (ordenados): {sorted(candidatos)}")
    print()

    # Mostrar scores detallados para verificar
    frecuencia = [0] * (max_num + 1)
    for s in sorteos:
        for n in s['numeros']:
            frecuencia[n] += 1
    frec_esperada = total * num_por_sorteo / max_num

    ventana_caliente = min(30, total // 3)
    ventana_caliente = max(ventana_caliente, 10)
    sorteos_recientes = sorteos[-ventana_caliente:]
    frec_reciente = [0] * (max_num + 1)
    for s in sorteos_recientes:
        for n in s['numeros']:
            frec_reciente[n] += 1
    frec_esperada_reciente = ventana_caliente * num_por_sorteo / max_num

    ultima_aparicion = [-1] * (max_num + 1)
    for idx, s in enumerate(sorteos):
        for n in s['numeros']:
            ultima_aparicion[n] = idx

    gap_promedio = [0.0] * (max_num + 1)
    for num in range(1, max_num + 1):
        if frecuencia[num] > 1:
            gap_promedio[num] = total / frecuencia[num]
        else:
            gap_promedio[num] = float(total)

    # Scores detallados para los 18 candidatos
    print("DETALLE DE SCORES DE LOS 18 CANDIDATOS:")
    print(f"  {'NUM':>3} {'FREC':>5} {'FrecRec':>7} {'Gap':>5} {'GapProm':>7} {'sFrec':>7} {'sCal':>7} {'sDeb':>7} {'FINAL':>7}")
    print("-" * 80)

    # Recalcular scores normalizados
    score_frecuencia = [0.0] * (max_num + 1)
    for num in range(1, max_num + 1):
        score_frecuencia[num] = (frecuencia[num] - frec_esperada) / max(frec_esperada, 1.0)

    score_caliente = [0.0] * (max_num + 1)
    for num in range(1, max_num + 1):
        score_caliente[num] = (frec_reciente[num] - frec_esperada_reciente) / max(frec_esperada_reciente, 1.0)

    score_debido = [0.0] * (max_num + 1)
    for num in range(1, max_num + 1):
        gap_actual = total - 1 - ultima_aparicion[num]
        if gap_promedio[num] > 0:
            score_debido[num] = (gap_actual - gap_promedio[num]) / gap_promedio[num]

    def normalizar(scores):
        vals = [scores[n] for n in range(1, max_num + 1)]
        min_v = min(vals)
        max_v = max(vals)
        rango = max(max_v - min_v, 0.001)
        result = [0.0] * (max_num + 1)
        for num in range(1, max_num + 1):
            result[num] = (scores[num] - min_v) / rango
        return result

    nf = normalizar(score_frecuencia)
    nc = normalizar(score_caliente)
    nd = normalizar(score_debido)

    score_final = [0.0] * (max_num + 1)
    for num in range(1, max_num + 1):
        score_final[num] = 0.40 * nf[num] + 0.35 * nc[num] + 0.25 * nd[num]

    for num in candidatos:
        gap_actual = total - 1 - ultima_aparicion[num]
        print(f"  {num:>3} {frecuencia[num]:>5} {frec_reciente[num]:>7} {gap_actual:>5} {gap_promedio[num]:>7.1f} "
              f"{nf[num]:>7.3f} {nc[num]:>7.3f} {nd[num]:>7.3f} {score_final[num]:>7.4f}")

    # Mostrar los 49 numeros ordenados por score para referencia completa
    print()
    print("=" * 80)
    print("RANKING COMPLETO (49 numeros ordenados por score final)")
    print("=" * 80)
    ranking = sorted(range(1, max_num + 1), key=lambda n: score_final[n], reverse=True)
    for i, num in enumerate(ranking):
        gap_actual = total - 1 - ultima_aparicion[num]
        marca = " <-- CANDIDATO" if num in candidatos else ""
        print(f"  {i+1:>2}. #{num:>2} frec={frecuencia[num]:>3} frecRec={frec_reciente[num]:>2} "
              f"gap={gap_actual:>3} sF={nf[num]:.3f} sC={nc[num]:.3f} sD={nd[num]:.3f} "
              f"TOTAL={score_final[num]:.4f}{marca}")

    # Instrucciones para verificar en la app
    print()
    print("=" * 80)
    print("COMO VERIFICAR EN LA APP:")
    print("=" * 80)
    print("1. Abre la app en Primitiva")
    print("2. Selecciona 'Metodo del Abuelo' con rango 'Todo el historico'")
    print("3. Los candidatos deben ser EXACTAMENTE:")
    print(f"   {sorted(candidatos)}")
    print()
    print("Si los candidatos de la app son diferentes, hay un bug.")
    print("Si son iguales, el algoritmo Python y Kotlin son identicos.")


if __name__ == '__main__':
    main()
