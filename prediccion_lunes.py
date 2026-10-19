#!/usr/bin/env python3
"""
prediccion_lunes.py -- Prediccion para La Primitiva del lunes 02/03/2026.

Usa las mismas 20 estrategias de sim_magia.py + candidatos Abuelo
para generar la combinacion optima.
"""

import csv
import math
import random
from collections import defaultdict
from pathlib import Path
from datetime import datetime

CSV_PATH = Path(__file__).parent / "app/src/main/res/raw/historico_primitiva.csv"
NUMS = 49
PICK = 6
WINDOWS = [5, 12, 30, 60]

# --- Estructuras ---
class Draw:
    def __init__(self, fecha, nums, weekday, reintegro):
        self.fecha = fecha
        self.nums = nums
        self.weekday = weekday
        self.reintegro = reintegro

class DrawState:
    def __init__(self):
        self.n_draws = 0
        self.freq = [0] * (NUMS + 1)
        self.last_seen = [0] * (NUMS + 1)
        self.ema = [0.0] * (NUMS + 1)
        self.pair_count = defaultdict(int)
        self.appearances = [[] for _ in range(NUMS + 1)]
        self.recent_draws = []
        self.max_window = max(WINDOWS)
        self.last_sets = []
        self.reintegro_freq = [0] * 10
        self.reintegro_recent = []
        self.weekday_freq = [[0] * (NUMS + 1) for _ in range(7)]
        self.reintegro_weekday_freq = [[0] * 10 for _ in range(7)]

    def update(self, draw, alpha_ema=0.15):
        idx = self.n_draws
        nums_set = set(draw.nums)
        for n in draw.nums:
            self.freq[n] += 1
            self.last_seen[n] = idx
            self.appearances[n].append(idx)
            self.weekday_freq[draw.weekday][n] += 1
        for n in range(1, NUMS + 1):
            val = 1.0 if n in nums_set else 0.0
            self.ema[n] = alpha_ema * val + (1 - alpha_ema) * self.ema[n]
        for i in range(len(draw.nums)):
            for j in range(i + 1, len(draw.nums)):
                self.pair_count[(draw.nums[i], draw.nums[j])] += 1
        self.recent_draws.append(draw.nums[:])
        if len(self.recent_draws) > self.max_window:
            self.recent_draws.pop(0)
        self.last_sets.append(nums_set)
        if len(self.last_sets) > 10:
            self.last_sets.pop(0)
        self.reintegro_freq[draw.reintegro] += 1
        self.reintegro_recent.append(draw.reintegro)
        if len(self.reintegro_recent) > 60:
            self.reintegro_recent.pop(0)
        self.reintegro_weekday_freq[draw.weekday][draw.reintegro] += 1
        self.n_draws += 1

    def window_freq(self, w):
        f = [0] * (NUMS + 1)
        start = max(0, len(self.recent_draws) - w)
        for draw_nums in self.recent_draws[start:]:
            for n in draw_nums:
                f[n] += 1
        return f

# --- Carga ---
def load_draws(path):
    draws = []
    with open(path, "r", encoding="utf-8") as f:
        reader = csv.reader(f)
        next(reader)
        for row in reader:
            if len(row) < 9:
                continue
            fecha = row[0].strip()
            nums = sorted(int(row[i]) for i in range(1, 7))
            dt = datetime.strptime(fecha, "%Y-%m-%d")
            weekday = dt.weekday()
            reintegro = int(row[8].strip())
            draws.append(Draw(fecha, nums, weekday, reintegro))
    draws.reverse()
    return draws

# --- Helpers ---
def top_n(scores, n=PICK):
    ranked = sorted(range(1, NUMS + 1), key=lambda x: scores[x], reverse=True)
    return sorted(ranked[:n])

def best_reintegro(state, mode="global", weekday=0, window=30):
    if mode == "recent":
        w = min(window, len(state.reintegro_recent))
        if w == 0:
            return random.randint(0, 9)
        freq = [0] * 10
        for r in state.reintegro_recent[-w:]:
            freq[r] += 1
        return max(range(10), key=lambda x: (freq[x], -x))
    elif mode == "calendar":
        wf = state.reintegro_weekday_freq[weekday]
        if sum(wf) == 0:
            return random.randint(0, 9)
        return max(range(10), key=lambda x: (wf[x], -x))
    else:
        return max(range(10), key=lambda x: (state.reintegro_freq[x], -x))

# --- Estrategias ---
def strat_pure_freq(state, wd):
    return top_n(state.freq)

def strat_hot(state, wd):
    wf = state.window_freq(12)
    scores = [0.0] * (NUMS + 1)
    for n in range(1, NUMS + 1):
        scores[n] = wf[n] * 1000 + state.freq[n]
    return top_n(scores)

def strat_cold(state, wd):
    idx = state.n_draws
    scores = [0.0] * (NUMS + 1)
    for n in range(1, NUMS + 1):
        gap = idx - state.last_seen[n] if state.last_seen[n] > 0 else idx
        scores[n] = gap * 1000 + state.freq[n]
    return top_n(scores)

def strat_blend(state, wd):
    idx = state.n_draws
    wf = state.window_freq(12)
    total_draws = max(state.n_draws, 1)
    scores = [0.0] * (NUMS + 1)
    for n in range(1, NUMS + 1):
        freq_norm = state.freq[n] / total_draws
        recent_norm = wf[n] / min(12, total_draws)
        gap = idx - state.last_seen[n] if state.last_seen[n] > 0 else idx
        gap_norm = gap / total_draws
        scores[n] = 0.15 * freq_norm + 0.70 * recent_norm + 0.15 * gap_norm
    return top_n(scores)

def strat_ema(state, wd):
    return top_n(state.ema)

def strat_bayesian(state, wd):
    total = max(state.n_draws, 1)
    scores = [0.0] * (NUMS + 1)
    for n in range(1, NUMS + 1):
        alpha = 1 + state.freq[n]
        beta = 1 + total - state.freq[n]
        scores[n] = alpha / (alpha + beta)
    return top_n(scores)

def strat_gap_timing(state, wd):
    idx = state.n_draws
    scores = [0.0] * (NUMS + 1)
    for n in range(1, NUMS + 1):
        apps = state.appearances[n]
        if len(apps) < 3:
            scores[n] = 0
            continue
        gaps = [apps[i] - apps[i - 1] for i in range(1, len(apps))]
        mean_gap = sum(gaps) / len(gaps)
        current_gap = idx - apps[-1]
        scores[n] = current_gap / mean_gap if mean_gap > 0 else 0
    return top_n(scores)

def strat_streak(state, wd):
    wf = state.window_freq(12)
    wf5 = state.window_freq(5)
    scores = [0.0] * (NUMS + 1)
    for n in range(1, NUMS + 1):
        scores[n] = wf[n] * 2 + wf5[n] * 3
    return top_n(scores)

def strat_neighbor(state, wd):
    scores = [0.0] * (NUMS + 1)
    total = max(state.n_draws, 1)
    for n in range(1, NUMS + 1):
        scores[n] = state.freq[n] / total
    for draw_nums in state.recent_draws[-3:]:
        for num in draw_nums:
            for delta in range(-2, 3):
                nb = num + delta
                if 1 <= nb <= NUMS:
                    scores[nb] += 0.05 * (3 - abs(delta)) / 3
    return top_n(scores)

def strat_low_vol(state, wd):
    idx = state.n_draws
    scores = [0.0] * (NUMS + 1)
    for n in range(1, NUMS + 1):
        apps = state.appearances[n]
        if len(apps) < 5:
            scores[n] = -1
            continue
        gaps = [apps[i] - apps[i - 1] for i in range(1, len(apps))]
        mean_g = sum(gaps) / len(gaps)
        if mean_g == 0:
            scores[n] = -1
            continue
        var_g = sum((g - mean_g) ** 2 for g in gaps) / len(gaps)
        cv = math.sqrt(var_g) / mean_g
        current_gap = idx - apps[-1]
        timing = current_gap / mean_g
        scores[n] = timing / (1 + cv)
    return top_n(scores)

def strat_calendar(state, wd):
    wf = state.weekday_freq[wd]
    total_wd = max(sum(wf), 1)
    scores = [0.0] * (NUMS + 1)
    for n in range(1, NUMS + 1):
        scores[n] = wf[n] / total_wd * 1000 + state.freq[n]
    return top_n(scores)

def strat_regression(state, wd):
    wf30 = state.window_freq(30)
    expected = PICK * 30 / NUMS
    scores = [0.0] * (NUMS + 1)
    for n in range(1, NUMS + 1):
        deficit = expected - wf30[n]
        scores[n] = deficit * 1000 + state.freq[n]
    return top_n(scores)

def strat_markov(state, wd):
    scores = [0.0] * (NUMS + 1)
    for n in range(1, NUMS + 1):
        apps = state.appearances[n]
        if len(apps) < 3:
            scores[n] = state.freq[n]
            continue
        app_set = set(apps)
        hits = checks = 0
        for k in range(1, 4):
            for a in apps:
                if a + k < state.n_draws:
                    checks += 1
                    if (a + k) in app_set:
                        hits += 1
        current_gap = state.n_draws - apps[-1] if apps else 999
        if checks > 0 and current_gap <= 3:
            scores[n] = hits / checks * 1000 + state.freq[n]
        else:
            scores[n] = state.freq[n] * 0.5
    return top_n(scores)

def strat_multi_horizon(state, wd):
    horizons = [5, 12, 30, 60]
    scores = [0.0] * (NUMS + 1)
    for w in horizons:
        wf = state.window_freq(w)
        ranked = sorted(range(1, NUMS + 1), key=lambda x: wf[x], reverse=True)
        for n in ranked[:15]:
            scores[n] += 1
    total = max(state.n_draws, 1)
    for n in range(1, NUMS + 1):
        scores[n] += state.freq[n] / total * 0.1
    return top_n(scores)

def strat_anti_pattern(state, wd):
    scores = [0.0] * (NUMS + 1)
    total = max(state.n_draws, 1)
    for n in range(1, NUMS + 1):
        scores[n] = state.freq[n] / total
    for n in range(1, 32):
        scores[n] *= 0.9
    for n in range(1, NUMS + 1):
        if n % 5 == 0 or n % 7 == 0 or n % 10 == 0:
            scores[n] *= 0.9
    return top_n(scores)

# --- Candidatos Abuelo (15/70/15, ventana=12) ---
def candidatos_abuelo(state, v=17):
    idx = state.n_draws
    total = max(idx, 1)
    expected = PICK / NUMS
    vc = 12
    wf = state.window_freq(vc)
    recent_expected = PICK * min(vc, total) / NUMS

    raw_frec = []
    raw_cal = []
    raw_deb = []
    for n in range(1, NUMS + 1):
        f = (state.freq[n] / total - expected) / max(expected, 1e-9)
        raw_frec.append(f)
        c = (wf[n] - recent_expected) / max(recent_expected, 1e-9)
        raw_cal.append(c)
        gap = idx - state.last_seen[n] if state.last_seen[n] > 0 else idx
        mean_gap = total / max(state.freq[n], 1)
        d = (gap - mean_gap) / max(mean_gap, 1e-9)
        raw_deb.append(d)

    def normalize(arr):
        mn, mx = min(arr), max(arr)
        rng = mx - mn if mx > mn else 1
        return [(x - mn) / rng for x in arr]

    nf = normalize(raw_frec)
    nc = normalize(raw_cal)
    nd = normalize(raw_deb)

    scored = []
    for i in range(NUMS):
        n = i + 1
        s = 0.15 * nf[i] + 0.70 * nc[i] + 0.15 * nd[i]
        scored.append((s, n))
    scored.sort(reverse=True)

    top_v = [n for _, n in scored[:v]]
    low = sorted([n for n in top_v if n <= 24])
    high = sorted([n for n in top_v if n > 24])
    result = []
    while low or high:
        if low:
            result.append(low.pop(0))
        if high:
            result.append(high.pop(0))
    return result

# --- Reintegro inteligente ---
def reintegro_inteligente(state, weekday):
    """Combina frecuencia global, reciente y por dia de la semana."""
    scores = [0.0] * 10
    # Global
    total_r = sum(state.reintegro_freq)
    if total_r > 0:
        for r in range(10):
            scores[r] += 0.3 * state.reintegro_freq[r] / total_r

    # Reciente (30)
    w = min(30, len(state.reintegro_recent))
    if w > 0:
        freq_r = [0] * 10
        for r in state.reintegro_recent[-w:]:
            freq_r[r] += 1
        for r in range(10):
            scores[r] += 0.4 * freq_r[r] / w

    # Calendario
    wf = state.reintegro_weekday_freq[weekday]
    total_wd = sum(wf)
    if total_wd > 0:
        for r in range(10):
            scores[r] += 0.3 * wf[r] / total_wd

    ranked = sorted(range(10), key=lambda x: scores[x], reverse=True)
    return ranked[0], ranked[:3], scores

# --- Main ---
def main():
    draws = load_draws(CSV_PATH)
    state = DrawState()

    for d in draws:
        state.update(d)

    print(f"Total sorteos cargados: {state.n_draws}")
    print(f"Ultimo sorteo: {draws[-1].fecha} -> {draws[-1].nums} R={draws[-1].reintegro}")
    print()

    # El lunes es weekday=0
    TARGET_WD = 0  # Lunes
    print("=" * 70)
    print("  PREDICCION PARA PRIMITIVA - LUNES 02/03/2026")
    print("=" * 70)
    print()

    # --- Ejecutar todas las estrategias ---
    strategies = {
        "Frecuencia Pura": strat_pure_freq,
        "Calientes (v12)": strat_hot,
        "Frios/Debidos": strat_cold,
        "Blend 15/70/15": strat_blend,
        "EMA (a=0.15)": strat_ema,
        "Bayesiano": strat_bayesian,
        "Gap Timing": strat_gap_timing,
        "Streak Momentum": strat_streak,
        "Vecinos": strat_neighbor,
        "Baja Volatilidad": strat_low_vol,
        "Calendario (Lunes)": strat_calendar,
        "Regresion Media": strat_regression,
        "Markov": strat_markov,
        "Multi-Horizon": strat_multi_horizon,
        "Anti-Patron": strat_anti_pattern,
    }

    all_predictions = {}
    print("--- Predicciones por estrategia ---")
    for name, func in strategies.items():
        pred = func(state, TARGET_WD)
        all_predictions[name] = pred
        print(f"  {name:25s}: {pred}")

    print()

    # --- Candidatos Abuelo ---
    cands = candidatos_abuelo(state, v=17)
    print(f"--- Candidatos Abuelo (top 17, pesos 15/70/15, ventana=12) ---")
    print(f"  {cands}")
    print()

    # --- Consenso: numeros que mas aparecen en TODAS las estrategias ---
    vote_count = [0] * (NUMS + 1)
    for name, pred in all_predictions.items():
        for n in pred:
            vote_count[n] += 1

    print("--- Consenso (votos de 15 estrategias) ---")
    ranked_votes = sorted(range(1, NUMS + 1), key=lambda x: vote_count[x], reverse=True)
    for n in ranked_votes[:20]:
        bar = "#" * vote_count[n]
        print(f"  {n:2d}: {vote_count[n]:2d} votos  {bar}")

    print()

    # --- Combinacion final: top 6 por consenso ---
    consensus_6 = sorted(ranked_votes[:6])
    print(f"*** COMBINACION POR CONSENSO (top 6 votos): {consensus_6}")

    # --- Multi-Horizon (mejor metodo en backtest) ---
    mh = all_predictions["Multi-Horizon"]
    print(f"*** COMBINACION MULTI-HORIZON:              {mh}")

    # --- Blend Abuelo ---
    blend = all_predictions["Blend 15/70/15"]
    print(f"*** COMBINACION BLEND ABUELO:               {blend}")

    # --- Combinacion hibrida: consenso + multi-horizon + candidatos abuelo ---
    hybrid_scores = [0.0] * (NUMS + 1)

    # Votos de consenso (peso 0.4)
    for n in range(1, NUMS + 1):
        hybrid_scores[n] += 0.40 * (vote_count[n] / 15)

    # Multi-horizon top (peso 0.35)
    mh_scores = [0.0] * (NUMS + 1)
    for w in [5, 12, 30, 60]:
        wf = state.window_freq(w)
        ranked = sorted(range(1, NUMS + 1), key=lambda x: wf[x], reverse=True)
        for i, n in enumerate(ranked[:15]):
            mh_scores[n] += (15 - i) / 15
    mx_mh = max(mh_scores[1:]) if max(mh_scores[1:]) > 0 else 1
    for n in range(1, NUMS + 1):
        hybrid_scores[n] += 0.35 * (mh_scores[n] / mx_mh)

    # Candidatos Abuelo (peso 0.25)
    for i, n in enumerate(cands):
        hybrid_scores[n] += 0.25 * (len(cands) - i) / len(cands)

    hybrid_ranked = sorted(range(1, NUMS + 1), key=lambda x: hybrid_scores[x], reverse=True)
    hybrid_6 = sorted(hybrid_ranked[:6])

    print(f"*** COMBINACION HIBRIDA (consenso+MH+abuelo): {hybrid_6}")

    print()

    # --- Reintegro ---
    best_r, top3_r, r_scores = reintegro_inteligente(state, TARGET_WD)
    print("--- Reintegro ---")
    print(f"  Frecuencias globales: {state.reintegro_freq}")
    r_recent = [0] * 10
    for r in state.reintegro_recent[-30:]:
        r_recent[r] += 1
    print(f"  Frecuencias recientes (30): {r_recent}")
    print(f"  Frecuencias lunes: {state.reintegro_weekday_freq[0]}")
    print(f"  Scores combinados: {['%.3f'%s for s in r_scores]}")
    print(f"  -> MEJOR REINTEGRO: {best_r}  (top 3: {top3_r})")

    print()

    # --- Ultimos sorteos para contexto ---
    print("--- Ultimos 10 sorteos ---")
    for d in draws[-10:]:
        dia = ["Lun","Mar","Mie","Jue","Vie","Sab","Dom"][d.weekday]
        print(f"  {d.fecha} ({dia}): {d.nums}  R={d.reintegro}")

    print()

    # --- Analisis de los numeros que dimos el sabado ---
    print("--- Analisis retroactivo: prediccion sabado (4-12-13-15-27-47) ---")
    pred_sab = {4, 12, 13, 15, 27, 47}
    # Buscar ultimo sorteo del sabado
    for d in reversed(draws):
        if d.weekday == 5:  # Sabado
            actual = set(d.nums)
            hits = pred_sab & actual
            print(f"  Sorteo {d.fecha}: {d.nums} R={d.reintegro}")
            print(f"  Aciertos: {len(hits)} -> {sorted(hits) if hits else 'ninguno'}")
            break

    print()
    print("=" * 70)
    print("  RECOMENDACION FINAL PARA LUNES 02/03/2026")
    print("=" * 70)
    print(f"  Combinacion:  {hybrid_6}")
    print(f"  Reintegro:    {best_r}")
    print("=" * 70)

if __name__ == "__main__":
    main()
