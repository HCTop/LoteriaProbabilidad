#!/usr/bin/env python3
"""
"🧩 Perfil típico" para loterías de 5 números (Euromillones 5/50, El Gordo 5/54),
mismo algoritmo que CalculadorProbabilidad.generarPerfilTipico + FormaTipica:
  5 de los 15 más calientes (últimos 50 sorteos), sin números del último sorteo,
  forma típica: suma media ± 1 desv., 2-3 impares, 2-3 bajos, sin seguidos, 3-5 decenas;
  puntos = calor + 1,5 por número > 31.
Walk-forward: cada sorteo solo con los anteriores. Uso: python3 scripts/laboratorio_perfil_5.py
"""
import csv
import random
from collections import Counter
from itertools import combinations
from math import comb, sqrt
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent / "app/src/main/res/raw"


def rango_suma(N, k):
    media = k * (N + 1) / 2
    sd = sqrt(k * (N * N - 1) / 12 * (N - k) / (N - 1))
    a5 = lambda x: int(round(x / 5)) * 5
    return a5(media - sd), a5(media + sd)


def forma_ok(c, N):
    s = sorted(c); lo, hi = rango_suma(N, 5)
    return (lo <= sum(s) <= hi and 2 <= sum(x % 2 for x in s) <= 3
            and 2 <= sum(x <= N // 2 for x in s) <= 3
            and sum(1 for a, b in zip(s, s[1:]) if b == a + 1) == 0
            and 3 <= len({x // 10 for x in s}) <= 5)


def perfil(pasado, N):
    rec = Counter(n for s in pasado[:50] for n in s)
    tot = Counter(n for s in pasado for n in s)
    pool = sorted((n for n in range(1, N + 1) if n not in pasado[0]),
                  key=lambda n: (rec[n], tot[n], -n), reverse=True)[:15]
    mejor, mp = None, -1e9
    for c in combinations(pool, 5):
        if forma_ok(c, N):
            p = sum(rec[n] for n in c) + 1.5 * sum(n > 31 for n in c)
            if p > mp:
                mejor, mp = set(c), p
    return mejor or set(pool[:5])


if __name__ == "__main__":
    for archivo, N, nombre in (("historico_euromillones.csv", 50, "EUROMILLONES"),
                               ("historico_gordo_primitiva.csv", 54, "EL GORDO")):
        filas = list(csv.DictReader(open(RAIZ / archivo, encoding="utf-8")))
        sorteos = [{int(r[f"n{i}"]) for i in range(1, 6)} for r in filas]
        rng = random.Random(0)
        res = {"Perfil típico": [], "Aleatorio": []}
        desde = 100
        for i in range(len(sorteos) - desde - 1, -1, -1):
            real, pasado = sorteos[i], sorteos[i + 1:]
            res["Perfil típico"].append(len(perfil(pasado, N) & real))
            res["Aleatorio"].append(len(set(rng.sample(range(1, N + 1), 5)) & real))
        n = len(res["Aleatorio"])
        P = {k: comb(5, k) * comb(N - 5, 5 - k) / comb(N, 5) for k in range(6)}
        print(f"\n{nombre}: {n} sorteos · azar: media {25 / N:.3f}, 2+ aciertos {n * sum(P[k] for k in range(2, 6)):.1f}, "
              f"3+ {n * sum(P[k] for k in range(3, 6)):.1f}")
        for m, a in res.items():
            c = Counter(a)
            print(f"  {m:14} media {sum(a) / n:.3f} · 2+ {sum(c[k] for k in range(2, 6))} · 3+ {sum(c[k] for k in range(3, 6))} · 4+ {c[4] + c[5]}")
