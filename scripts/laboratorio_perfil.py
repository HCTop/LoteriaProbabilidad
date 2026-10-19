#!/usr/bin/env python3
"""
Método "🧩 Perfil típico" (el mismo algoritmo que CalculadorProbabilidad.generarPerfilTipico),
probado en walk-forward sobre todo el historial de La Primitiva y de Bonoloto.

Cada sorteo se predice solo con los anteriores:
  * 1 número del último sorteo (el más caliente de los 6) + 5 de los 15 más calientes
    de los últimos 50 sorteos (sin otros números del último sorteo).
  * La combinación debe tener la forma típica: suma 120-180, 2-4 impares, 2-4 bajos (1-24),
    exactamente una pareja de números seguidos y números en 3-5 decenas distintas.
  * Entre las que cumplen, gana la de más "calor" + 1,5 puntos por cada número > 31
    (menos jugado por la gente, que apuesta fechas).

Uso: python3 scripts/laboratorio_perfil.py
"""
import csv
import random
from collections import Counter
from itertools import combinations
from math import comb
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent / "app/src/main/res/raw"
VENTANA, POOL, BONUS_ALTO = 50, 15, 1.5


def forma_ok(c):
    s = sorted(c)
    seguidos = sum(1 for a, b in zip(s, s[1:]) if b == a + 1)
    return (120 <= sum(s) <= 180 and 2 <= sum(x % 2 for x in s) <= 4
            and 2 <= sum(x <= 24 for x in s) <= 4 and seguidos == 1
            and 3 <= len({x // 10 for x in s}) <= 5)


def perfil_tipico(pasado):
    """pasado: lista de sorteos (sets) del MÁS RECIENTE al más antiguo."""
    rec = Counter(n for s in pasado[:VENTANA] for n in s)
    tot = Counter(n for s in pasado for n in s)
    calor = lambda n: (rec[n], tot[n], -n)
    ultimo = pasado[0]
    pool = sorted((n for n in range(1, 50) if n not in ultimo), key=calor, reverse=True)[:POOL]
    mejor, mejor_p = None, -1e9
    for r in ultimo:
        for resto in combinations(pool, 5):
            c = (r,) + resto
            if not forma_ok(c):
                continue
            p = sum(rec[n] for n in c) + BONUS_ALTO * sum(n > 31 for n in c)
            if p > mejor_p:
                mejor, mejor_p = set(c), p
    if mejor is None:  # no debería pasar; respaldo: los 6 más calientes
        mejor = set(sorted(range(1, 50), key=calor, reverse=True)[:6])
    return mejor


def evaluar(archivo, desde=100):
    filas = list(csv.DictReader(open(RAIZ / archivo, encoding="utf-8")))  # reciente → antiguo
    sorteos = [{int(r[f"n{i}"]) for i in range(1, 7)} for r in filas]
    rng = random.Random(0)
    res = {"🧩 Perfil típico": [], "🎲 Aleatorio": []}
    for i in range(len(sorteos) - desde - 1, -1, -1):  # del más antiguo al más reciente
        real, pasado = sorteos[i], sorteos[i + 1:]
        res["🧩 Perfil típico"].append(len(perfil_tipico(pasado) & real))
        res["🎲 Aleatorio"].append(len(set(rng.sample(range(1, 50), 6)) & real))
    return res, filas[len(sorteos) - desde - 1]["fecha"], filas[0]["fecha"]


P = {k: comb(6, k) * comb(43, 6 - k) / comb(49, 6) for k in range(7)}

if __name__ == "__main__":
    for archivo, nombre in (("historico_primitiva.csv", "LA PRIMITIVA"), ("historico_bonoloto.csv", "BONOLOTO")):
        res, d, h = evaluar(archivo)
        n = len(res["🎲 Aleatorio"])
        print(f"\n{nombre}: {n} sorteos ({d} → {h})")
        print(f"{'':20} {'media':>6} {'premios(3+)':>12} {'4 ac.':>6} {'5+':>4}")
        print(f"{'· azar (teoría)':20} {6 * 6 / 49:6.3f} {n * sum(P[k] for k in range(3, 7)):12.1f} {n * P[4]:6.1f}")
        for m, a in res.items():
            c = Counter(a)
            print(f"{m:20} {sum(a) / n:6.3f} {sum(c[k] for k in range(3, 7)):12d} {c[4]:6d} {c[5] + c[6]:4d}")
