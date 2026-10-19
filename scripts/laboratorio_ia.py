#!/usr/bin/env python3
"""
LABORATORIO IA — métodos "inventados" para La Primitiva
=======================================================

Métodos nuevos, todos evaluados igual que en laboratorio_primitiva.py
(walk-forward: para el sorteo i solo se usa información de los sorteos anteriores):

 1. IA Gradient Boosting: un modelo aprende, con TODAS las señales a la vez
    (frecuencias en 5 ventanas, huecos, ciclos, medias exponenciales, Markov,
    afinidad de parejas con el último sorteo, frecuencia en el mismo día de la semana,
    paridad/rango...), qué probabilidad tiene cada número de salir en el siguiente sorteo.
    Se reentrena cada 250 sorteos solo con el pasado.
 2. IA Regresión logística: lo mismo con un modelo lineal (menos propenso a ver fantasmas).
 3. Mezcla de expertos (algoritmo Hedge): sigue a los 21 métodos clásicos a la vez y da más
    peso a los que mejor van últimamente. Si alguno tuviera "rachas" de acierto, esto lo explota.
 4. Día de la semana: números que más salen en el mismo día de la semana que el sorteo.

Requiere numpy y scikit-learn:  pip install numpy scikit-learn
Uso: python3 scripts/laboratorio_ia.py
"""
import csv
import math
import sys
from collections import Counter
from datetime import date
from pathlib import Path

import numpy as np
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler

sys.path.insert(0, str(Path(__file__).resolve().parent))
import laboratorio_primitiva as lab  # noqa: E402

N, K = lab.N, lab.K
DESDE = 300          # sorteos iniciales solo para calentar
BLOQUE = 250         # cada cuántos sorteos se reentrena la IA

NOMBRES_FEATURES = [
    "frec_total", "v6", "v12", "v25", "v50", "v100", "gap", "gap/ciclo",
    "ema_lenta", "ema_rapida", "en_ultimo", "markov", "afinidad_parejas",
    "frec_mismo_dia", "numero", "impar",
]


def features(E, dia_semana, por_dia, n_por_dia):
    t = max(len(E.h), 1)
    ultimo = E.h[-1] if E.h else set()
    filas = []
    for n in lab.NUMS:
        markov = sum((E.trans[(j, n)] + 1) / (E.trans_base[j] + N / K) for j in ultimo)
        afinidad = sum(E.pares[tuple(sorted((n, j)))] for j in ultimo if j != n) / t
        filas.append([
            E.freq[n] / t,
            E.ventana[6][1][n], E.ventana[12][1][n], E.ventana[25][1][n],
            E.ventana[50][1][n], E.ventana[100][1][n],
            E.gap(n), E.gap(n) / max(E.ciclo(n), 1),
            E.ema[n], E.ema_r[n],
            1.0 if n in ultimo else 0.0,
            markov, afinidad,
            por_dia[dia_semana][n] / max(n_por_dia[dia_semana], 1),
            n, n % 2,
        ])
    return np.array(filas, dtype=float)


def main():
    raiz = Path(__file__).resolve().parent.parent
    ruta = raiz / "app/src/main/res/raw/historico_primitiva.csv"
    filas = list(csv.DictReader(open(ruta, encoding="utf-8")))[::-1]  # cronológico
    sorteos = [[int(r[f"n{i}"]) for i in range(1, 7)] for r in filas]
    dias = [date.fromisoformat(r["fecha"]).weekday() for r in filas]

    E = lab.Estado()
    por_dia = {d: Counter() for d in range(7)}
    n_por_dia = Counter()

    X_hist, y_hist = [], []
    gb = lr = scaler = None
    aciertos = {"IA Gradient Boosting": [], "IA Regresión logística": [],
                "Mezcla de expertos (Hedge)": [], "Día de la semana": [],
                "Aleatorio (control)": []}
    prob_salen, prob_no_salen = [], []

    # Hedge sobre los métodos clásicos
    expertos = {m: f for m, f in lab.METODOS.items() if m != "Aleatorio (control)"}
    pesos = {m: 1.0 for m in expertos}
    eta = 0.05

    for i, sorteo in enumerate(sorteos):
        real = set(sorteo)
        X = features(E, dias[i], por_dia, n_por_dia)

        if i >= DESDE:
            # Reentrenar cada BLOQUE sorteos solo con el pasado
            if gb is None or (i - DESDE) % BLOQUE == 0:
                Xh, yh = np.vstack(X_hist), np.concatenate(y_hist)
                gb = HistGradientBoostingClassifier(max_iter=150, learning_rate=0.05,
                                                    max_leaf_nodes=15, random_state=0).fit(Xh, yh)
                scaler = StandardScaler().fit(Xh)
                lr = LogisticRegression(max_iter=500).fit(scaler.transform(Xh), yh)

            p_gb = gb.predict_proba(X)[:, 1]
            p_lr = lr.predict_proba(scaler.transform(X))[:, 1]
            top_gb = set(int(k) + 1 for k in np.argsort(-p_gb)[:K])
            top_lr = set(int(k) + 1 for k in np.argsort(-p_lr)[:K])
            aciertos["IA Gradient Boosting"].append(len(top_gb & real))
            aciertos["IA Regresión logística"].append(len(top_lr & real))
            for n in lab.NUMS:
                (prob_salen if n in real else prob_no_salen).append(p_gb[n - 1])

            # Hedge: votos ponderados de los expertos
            votos = Counter()
            predicciones = {m: f(E) for m, f in expertos.items()}
            for m, comb in predicciones.items():
                for n in comb:
                    votos[n] += pesos[m]
            hedge = lab.top(lambda n: votos[n])
            aciertos["Mezcla de expertos (Hedge)"].append(len(hedge & real))
            for m, comb in predicciones.items():   # actualizar pesos tras conocer el resultado
                pesos[m] *= math.exp(eta * len(comb & real))
            s = sum(pesos.values())
            pesos = {m: w / s for m, w in pesos.items()}

            dia = lab.top(lambda n: por_dia[dias[i]][n])
            aciertos["Día de la semana"].append(len(dia & real))
            aciertos["Aleatorio (control)"].append(len(set(lab.rng.sample(lab.NUMS, K)) & real))

        # El sorteo i pasa a ser "pasado"
        X_hist.append(X)
        y_hist.append(np.array([1 if n in real else 0 for n in lab.NUMS]))
        E.anadir(sorteo)
        por_dia[dias[i]].update(real)
        n_por_dia[dias[i]] += 1

    lab.tabla("MÉTODOS INVENTADOS (walk-forward, IA reentrenada cada "
              f"{BLOQUE} sorteos)", aciertos)

    # ¿La IA distingue algo? Si no, la probabilidad que da a las bolas que salen
    # y a las que no salen es la misma.
    ps, pn = np.mean(prob_salen), np.mean(prob_no_salen)
    print(f"\nProbabilidad media que la IA dio a las bolas que SÍ salieron: {ps:.4f}")
    print(f"Probabilidad media que la IA dio a las bolas que NO salieron: {pn:.4f}")
    print(f"(Azar puro: {K / N:.4f} para todas. Si la IA 'viera' algo, la primera sería mayor.)")

    # Qué señales usó más el modelo final (importancia por permutación simplificada)
    Xh, yh = np.vstack(X_hist), np.concatenate(y_hist)
    base = gb.score(Xh, yh)
    rng = np.random.default_rng(0)
    imp = []
    for j, nombre in enumerate(NOMBRES_FEATURES):
        Xp = Xh.copy()
        rng.shuffle(Xp[:, j])
        imp.append((nombre, base - gb.score(Xp, yh)))
    print("\nSeñales que más usa la IA (pérdida de precisión al desordenarlas):")
    for nombre, v in sorted(imp, key=lambda x: -x[1])[:5]:
        print(f"  {nombre:18} {v:+.5f}")
    print(f"  (precisión base {base:.4f}; predecir siempre 'no sale' ya da {1 - K / N:.4f})")


if __name__ == "__main__":
    main()
