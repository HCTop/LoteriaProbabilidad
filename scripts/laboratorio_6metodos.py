#!/usr/bin/env python3
"""
Prueba de los 6 métodos propuestos para la app, sobre TODO el historial
(La Primitiva y Bonoloto, ambos 6/49), en walk-forward (solo pasado).

Muestra:
  * Totales: media de aciertos, premios (3+), 4 aciertos, 5+, veces que "ganó".
  * Líder por tramos de 500 sorteos: si el mejor cambia de un tramo a otro,
    es que no hay un método mejor de verdad, solo suerte.

Uso: python3 scripts/laboratorio_6metodos.py
"""
import csv
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import laboratorio_primitiva as lab  # noqa: E402

RAIZ = Path(__file__).resolve().parent.parent / "app/src/main/res/raw"
TRAMO = 500


def ensemble(E):
    """Votación: cada método (menos el aleatorio) vota sus 6 números."""
    votos = Counter()
    for f in (METODOS["📊 Los que más salen"], METODOS["🔥 Calientes 50"],
              METODOS["❄️ Debidos"], METODOS["🔭 Multi-Horizon"]):
        votos.update(f(E))
    return lab.top(lambda n: votos[n] + E.freq[n] * 1e-6)


METODOS = {
    "🎲 Aleatorio":          lambda E: set(lab.rng.sample(lab.NUMS, lab.K)),
    "📊 Los que más salen":  lambda E: lab.top(lambda n: E.freq[n]),
    "🔥 Calientes 50":       lambda E: lab.top(lambda n: E.ventana[50][1][n]),
    "❄️ Debidos":            lambda E: lab.top(E.gap),
    "🔭 Multi-Horizon":      lab.consenso,
}
METODOS["🗳️ Ensemble"] = ensemble


def evaluar(nombre_csv, desde=100):
    filas = list(csv.DictReader(open(RAIZ / nombre_csv, encoding="utf-8")))
    sorteos = [[int(r[f"n{i}"]) for i in range(1, 7)] for r in filas][::-1]
    fechas = [r["fecha"] for r in filas][::-1]
    E = lab.Estado()
    for s in sorteos[:desde]:
        E.anadir(s)
    aciertos = {m: [] for m in METODOS}
    for i in range(desde, len(sorteos)):
        real = set(sorteos[i])
        res = {m: len(f(E) & real) for m, f in METODOS.items()}
        for m, a in res.items():
            aciertos[m].append(a)
        E.anadir(sorteos[i])
    return aciertos, fechas[desde:]


def informe(titulo, aciertos, fechas):
    total = len(fechas)
    print(f"\n{'═' * 78}\n{titulo}: {total} sorteos ({fechas[0]} → {fechas[-1]})\n{'═' * 78}")
    t3 = sum(lab.P_ACIERTOS[k] for k in range(3, 7)) * total
    t4 = lab.P_ACIERTOS[4] * total
    print(f"{'Método':22} {'media':>6} {'premios(3+)':>12} {'4 ac.':>6} {'5+':>4} {'ganó':>6}")
    print(f"{'· azar (teoría)':22} {lab.MEDIA_TEO:6.3f} {t3:12.0f} {t4:6.1f} {'':>4} {'':>6}")
    gano = Counter()
    for i in range(total):
        mx = max(aciertos[m][i] for m in METODOS)
        if mx > 0:
            for m in METODOS:
                if aciertos[m][i] == mx:
                    gano[m] += 1
    filas = []
    for m, a in aciertos.items():
        c = Counter(a)
        filas.append((m, sum(a) / total, sum(c[k] for k in range(3, 7)), c[4], c[5] + c[6], gano[m]))
    for m, media, pr, c4, c5, g in sorted(filas, key=lambda x: (-x[2], -x[1])):
        print(f"{m:22} {media:6.3f} {pr:12d} {c4:6d} {c5:4d} {g:6d}")

    print(f"\nLíder (más premios) en cada tramo de {TRAMO} sorteos:")
    for ini in range(0, total, TRAMO):
        fin = min(ini + TRAMO, total)
        premios = {m: sum(1 for x in aciertos[m][ini:fin] if x >= 3) for m in METODOS}
        orden = sorted(premios.items(), key=lambda kv: -kv[1])
        lider, pl = orden[0]
        ultimo, pu = orden[-1]
        print(f"  {fechas[ini]} → {fechas[fin - 1]}:  1º {lider} ({pl})   ·   último {ultimo} ({pu})")


if __name__ == "__main__":
    for archivo, nombre in (("historico_primitiva.csv", "LA PRIMITIVA"),
                            ("historico_bonoloto.csv", "BONOLOTO")):
        a, f = evaluar(archivo)
        informe(nombre, a, f)
