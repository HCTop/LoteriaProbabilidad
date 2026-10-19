#!/usr/bin/env python3
"""
sim_magia.py -- Backtester de 20 estrategias de prediccion para La Primitiva.

Walk-forward validation sin lookahead. Actualizacion incremental O(1) por sorteo.
Incluye reintegro (0-9) y distribucion detallada de aciertos.
"""

import csv
import math
import random
import time
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

# --- Configuracion -----------------------------------------------------------

CSV_PATH = Path(__file__).parent / "app/src/main/res/raw/historico_primitiva.csv"
NUMS = 49          # 1..49
PICK = 6           # 6 numeros por sorteo
WARMUP = 200       # sorteos iniciales para calentar estado
BACKTEST = 2000    # ultimos N sorteos a evaluar
RANDOM_REPS = 50   # repeticiones del baseline aleatorio por sorteo
PROGRESS_EVERY = 200

WINDOWS = [5, 12, 30, 60]


# --- Estructuras -------------------------------------------------------------

@dataclass
class Draw:
    fecha: str
    nums: list  # 6 ints ordenados
    weekday: int  # 0=lun .. 6=dom
    reintegro: int  # 0..9


class DrawState:
    """Estado incremental actualizado con cada sorteo."""

    def __init__(self):
        self.n_draws = 0
        self.freq = [0] * (NUMS + 1)          # freq[1..49]
        self.last_seen = [0] * (NUMS + 1)     # ultimo indice donde aparecio
        self.ema = [0.0] * (NUMS + 1)         # media movil exponencial
        self.pair_count = defaultdict(int)     # (a,b) -> count, a<b
        self.appearances = [[] for _ in range(NUMS + 1)]  # indices de aparicion
        self.recent_draws = []                 # ultimos max(WINDOWS) sorteos (listas de nums)
        self.weekday_freq = [[0] * (NUMS + 1) for _ in range(7)]  # por dia semana
        self.max_window = max(WINDOWS)

        # Para Markov: appeared[k] = set de nums que aparecieron hace k sorteos
        self.last_sets = []  # ultimos 10 sets

        # Reintegro
        self.reintegro_freq = [0] * 10         # freq[0..9]
        self.reintegro_recent = []             # ultimos 60 reintegros
        self.reintegro_weekday_freq = [[0] * 10 for _ in range(7)]

    def update(self, draw: Draw, alpha_ema=0.15):
        idx = self.n_draws
        nums_set = set(draw.nums)

        for n in draw.nums:
            self.freq[n] += 1
            self.last_seen[n] = idx
            self.appearances[n].append(idx)
            self.weekday_freq[draw.weekday][n] += 1

        # EMA
        for n in range(1, NUMS + 1):
            val = 1.0 if n in nums_set else 0.0
            self.ema[n] = alpha_ema * val + (1 - alpha_ema) * self.ema[n]

        # Pares
        sorted_nums = draw.nums
        for i in range(len(sorted_nums)):
            for j in range(i + 1, len(sorted_nums)):
                self.pair_count[(sorted_nums[i], sorted_nums[j])] += 1

        # Ventanas recientes
        self.recent_draws.append(draw.nums[:])
        if len(self.recent_draws) > self.max_window:
            self.recent_draws.pop(0)

        # Markov
        self.last_sets.append(nums_set)
        if len(self.last_sets) > 10:
            self.last_sets.pop(0)

        # Reintegro
        self.reintegro_freq[draw.reintegro] += 1
        self.reintegro_recent.append(draw.reintegro)
        if len(self.reintegro_recent) > 60:
            self.reintegro_recent.pop(0)
        self.reintegro_weekday_freq[draw.weekday][draw.reintegro] += 1

        self.n_draws += 1

    def window_freq(self, w):
        """Frecuencia de cada numero en los ultimos w sorteos."""
        f = [0] * (NUMS + 1)
        start = max(0, len(self.recent_draws) - w)
        for draw_nums in self.recent_draws[start:]:
            for n in draw_nums:
                f[n] += 1
        return f


# --- Reintegro helper -------------------------------------------------------

def best_reintegro(state, mode="global", weekday=0, window=30):
    """Devuelve el reintegro mas probable segun el modo."""
    if mode == "random":
        return random.randint(0, 9)
    elif mode == "recent":
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
    else:  # global
        return max(range(10), key=lambda x: (state.reintegro_freq[x], -x))


# --- Estrategias -------------------------------------------------------------

def top_n(scores, n=PICK):
    """Devuelve los n numeros (1..49) con mayor score, ordenados."""
    ranked = sorted(range(1, NUMS + 1), key=lambda x: scores[x], reverse=True)
    return sorted(ranked[:n])


# 1. Random
def strat_random(state, draw_weekday):
    nums = sorted(random.sample(range(1, NUMS + 1), PICK))
    return (nums, random.randint(0, 9))


# 2. Pure Frequency
def strat_pure_freq(state, draw_weekday):
    return (top_n(state.freq), best_reintegro(state, "global"))


# 3. Hot Numbers (ventana 12)
def strat_hot(state, draw_weekday):
    wf = state.window_freq(12)
    scores = [0.0] * (NUMS + 1)
    for n in range(1, NUMS + 1):
        scores[n] = wf[n] * 1000 + state.freq[n]
    return (top_n(scores), best_reintegro(state, "recent", window=12))


# 4. Cold/Due
def strat_cold(state, draw_weekday):
    idx = state.n_draws
    scores = [0.0] * (NUMS + 1)
    for n in range(1, NUMS + 1):
        gap = idx - state.last_seen[n] if state.last_seen[n] > 0 else idx
        scores[n] = gap * 1000 + state.freq[n]
    return (top_n(scores), best_reintegro(state, "global"))


# 5. Weighted Blend 15/70/15
def strat_blend(state, draw_weekday):
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
    return (top_n(scores), best_reintegro(state, "global"))


# 6. EMA
def strat_ema(state, draw_weekday):
    return (top_n(state.ema), best_reintegro(state, "recent", window=30))


# 7. Pair Co-occurrence
def strat_pairs(state, draw_weekday):
    wf = state.window_freq(12)
    hot12 = sorted(range(1, NUMS + 1), key=lambda x: wf[x], reverse=True)[:12]
    scores = [0.0] * (NUMS + 1)
    for n in range(1, NUMS + 1):
        for h in hot12:
            if n != h:
                key = (min(n, h), max(n, h))
                scores[n] += state.pair_count[key]
    return (top_n(scores), best_reintegro(state, "global"))


# 8. Anti-repetition
def strat_anti_rep(state, draw_weekday):
    if not state.recent_draws:
        return strat_pure_freq(state, draw_weekday)
    last_set = set(state.recent_draws[-1])
    scores = [0.0] * (NUMS + 1)
    for n in range(1, NUMS + 1):
        scores[n] = state.freq[n] if n not in last_set else -1
    return (top_n(scores), best_reintegro(state, "global"))


# 9. Adaptive Ensemble (meta-learner)
class AdaptiveEnsemble:
    def __init__(self, base_strats):
        self.base_strats = base_strats  # lista de funciones
        self.weights = [1.0] * len(base_strats)
        self.window = 30  # ventana de evaluacion rolling

    def __call__(self, state, draw_weekday):
        scores = [0.0] * (NUMS + 1)
        total_w = sum(self.weights)
        for func, w in zip(self.base_strats, self.weights):
            pred_nums, _ = func(state, draw_weekday)
            for n in pred_nums:
                scores[n] += w / total_w
        return (top_n(scores), best_reintegro(state, "global"))

    def update_weights(self, predictions_by_strat, actual_set, lr=0.1):
        for i, pred in enumerate(predictions_by_strat):
            hits = len(set(pred) & actual_set)
            self.weights[i] = self.weights[i] * (1 + lr * (hits - PICK * PICK / NUMS))
            self.weights[i] = max(self.weights[i], 0.01)


# 10. Bayesian Posterior (Beta-binomial)
def strat_bayesian(state, draw_weekday):
    total = max(state.n_draws, 1)
    scores = [0.0] * (NUMS + 1)
    for n in range(1, NUMS + 1):
        alpha = 1 + state.freq[n]
        beta = 1 + total - state.freq[n]
        scores[n] = alpha / (alpha + beta)
    return (top_n(scores), best_reintegro(state, "global"))


# 11. Gap Timing
def strat_gap_timing(state, draw_weekday):
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
        if mean_gap > 0:
            scores[n] = current_gap / mean_gap
        else:
            scores[n] = 0
    return (top_n(scores), best_reintegro(state, "global"))


# 12. Streak Momentum
def strat_streak(state, draw_weekday):
    wf = state.window_freq(12)
    wf5 = state.window_freq(5)
    scores = [0.0] * (NUMS + 1)
    for n in range(1, NUMS + 1):
        scores[n] = wf[n] * 2 + wf5[n] * 3
    return (top_n(scores), best_reintegro(state, "recent", window=12))


# 13. Position-aware
def strat_position(state, draw_weekday):
    pos_freq = [[0] * (NUMS + 1) for _ in range(PICK)]
    for draw_nums in state.recent_draws:
        for pos, n in enumerate(draw_nums):
            pos_freq[pos][n] += 1

    chosen = set()
    result = []
    for pos in range(PICK):
        best_n = -1
        best_score = -1
        for n in range(1, NUMS + 1):
            if n not in chosen and pos_freq[pos][n] > best_score:
                best_score = pos_freq[pos][n]
                best_n = n
        if best_n > 0:
            chosen.add(best_n)
            result.append(best_n)

    while len(result) < PICK:
        for n in range(1, NUMS + 1):
            if n not in chosen:
                chosen.add(n)
                result.append(n)
                break

    return (sorted(result), best_reintegro(state, "global"))


# 14. Neighbor Clustering
def strat_neighbor(state, draw_weekday):
    if not state.recent_draws:
        return strat_pure_freq(state, draw_weekday)
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
    return (top_n(scores), best_reintegro(state, "global"))


# 15. Low Volatility (bajo CV de gaps)
def strat_low_vol(state, draw_weekday):
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
        timing = current_gap / mean_g if mean_g > 0 else 0
        scores[n] = timing / (1 + cv)
    return (top_n(scores), best_reintegro(state, "global"))


# 16. Anti-pattern (penaliza patrones humanos)
def strat_anti_pattern(state, draw_weekday):
    scores = [0.0] * (NUMS + 1)
    total = max(state.n_draws, 1)
    for n in range(1, NUMS + 1):
        scores[n] = state.freq[n] / total
    for n in range(1, 32):
        scores[n] *= 0.9
    for n in range(1, NUMS + 1):
        if n % 5 == 0 or n % 7 == 0 or n % 10 == 0:
            scores[n] *= 0.9
    return (top_n(scores), best_reintegro(state, "global"))


# 17. Calendar
def strat_calendar(state, draw_weekday):
    wf = state.weekday_freq[draw_weekday]
    total_wd = max(sum(wf), 1)
    scores = [0.0] * (NUMS + 1)
    for n in range(1, NUMS + 1):
        scores[n] = wf[n] / total_wd * 1000 + state.freq[n]
    return (top_n(scores), best_reintegro(state, "calendar", weekday=draw_weekday))


# 18. Regression to Mean
def strat_regression(state, draw_weekday):
    wf30 = state.window_freq(30)
    expected = PICK * 30 / NUMS
    scores = [0.0] * (NUMS + 1)
    for n in range(1, NUMS + 1):
        deficit = expected - wf30[n]
        scores[n] = deficit * 1000 + state.freq[n]
    return (top_n(scores), best_reintegro(state, "global"))


# 19. Markov Transition
def strat_markov_fast(state, draw_weekday):
    """Markov simplificado: score = sum de freq[n] en ventana condicionada."""
    scores = [0.0] * (NUMS + 1)
    if len(state.last_sets) < 2:
        return strat_pure_freq(state, draw_weekday)

    for n in range(1, NUMS + 1):
        apps = state.appearances[n]
        if len(apps) < 3:
            scores[n] = state.freq[n]
            continue
        app_set = set(apps)
        hits = 0
        checks = 0
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
    return (top_n(scores), best_reintegro(state, "global"))


# 20. Multi-horizon Consensus
def strat_multi_horizon(state, draw_weekday):
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
    return (top_n(scores), best_reintegro(state, "recent", window=30))


# --- Mann-Whitney U test (sin scipy) ----------------------------------------

def mann_whitney_p(sample_a, sample_b):
    """Aproximacion normal del test Mann-Whitney U. Devuelve p-value (two-tailed)."""
    na, nb = len(sample_a), len(sample_b)
    if na < 20 or nb < 20:
        return 1.0

    combined = [(v, 0) for v in sample_a] + [(v, 1) for v in sample_b]
    combined.sort(key=lambda x: x[0])

    ranks = [0.0] * len(combined)
    i = 0
    while i < len(combined):
        j = i
        while j < len(combined) and combined[j][0] == combined[i][0]:
            j += 1
        avg_rank = (i + j + 1) / 2
        for k in range(i, j):
            ranks[k] = avg_rank
        i = j

    r_a = sum(ranks[k] for k in range(len(combined)) if combined[k][1] == 0)
    u_a = r_a - na * (na + 1) / 2

    mu = na * nb / 2
    sigma = math.sqrt(na * nb * (na + nb + 1) / 12)
    if sigma == 0:
        return 1.0

    z = (u_a - mu) / sigma
    p = 2 * (1 - _norm_cdf(abs(z)))
    return p


def _norm_cdf(x):
    """Aproximacion de la CDF normal estandar (Abramowitz & Stegun)."""
    if x < 0:
        return 1 - _norm_cdf(-x)
    t = 1 / (1 + 0.2316419 * x)
    d = 0.3989422804014327  # 1/sqrt(2*pi)
    poly = t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))))
    return 1 - d * math.exp(-x * x / 2) * poly


# --- Carga de datos ---------------------------------------------------------

def load_draws(path):
    draws = []
    with open(path, "r", encoding="utf-8") as f:
        reader = csv.reader(f)
        header = next(reader)
        for row in reader:
            if len(row) < 9:
                continue
            fecha = row[0].strip()
            try:
                nums = sorted(int(row[i]) for i in range(1, 7))
                reintegro = int(row[8])
            except ValueError:
                continue
            y, m, d = int(fecha[:4]), int(fecha[5:7]), int(fecha[8:10])
            weekday = _weekday(y, m, d)
            draws.append(Draw(fecha=fecha, nums=nums, weekday=weekday, reintegro=reintegro))

    draws.reverse()  # orden cronologico (mas antiguo primero)
    return draws


def _weekday(y, m, d):
    """Zeller's -- devuelve 0=lun..6=dom."""
    if m < 3:
        m += 12
        y -= 1
    k = y % 100
    j = y // 100
    h = (d + (13 * (m + 1)) // 5 + k + k // 4 + j // 4 - 2 * j) % 7
    return (h + 5) % 7


# --- Main --------------------------------------------------------------------

def main():
    print("=" * 78)
    print("  sim_magia.py -- Backtester de 20 estrategias de prediccion")
    print("  La Primitiva (6/49 + Reintegro 0-9)")
    print("=" * 78)

    draws = load_draws(CSV_PATH)
    total = len(draws)
    print(f"\nSorteos cargados: {total}")

    backtest_count = min(BACKTEST, total - WARMUP)
    start_idx = total - backtest_count
    print(f"Warmup: {start_idx} sorteos | Backtest: {backtest_count} sorteos")
    print(f"Periodo backtest: {draws[start_idx].fecha} -> {draws[-1].fecha}")

    # Definir estrategias
    base_for_ensemble = [strat_pure_freq, strat_hot, strat_cold, strat_blend,
                         strat_ema, strat_bayesian, strat_gap_timing, strat_streak]
    ensemble = AdaptiveEnsemble(base_for_ensemble)

    strategies = [
        ("Random Baseline", None),  # tratamiento especial
        ("Pure Frequency", strat_pure_freq),
        ("Hot Numbers (w12)", strat_hot),
        ("Cold/Due", strat_cold),
        ("Weighted Blend", strat_blend),
        ("EMA (a=0.15)", strat_ema),
        ("Pair Co-occurrence", strat_pairs),
        ("Anti-repetition", strat_anti_rep),
        ("Adaptive Ensemble", ensemble),
        ("Bayesian Posterior", strat_bayesian),
        ("Gap Timing", strat_gap_timing),
        ("Streak Momentum", strat_streak),
        ("Position-aware", strat_position),
        ("Neighbor Clustering", strat_neighbor),
        ("Low Volatility", strat_low_vol),
        ("Anti-pattern", strat_anti_pattern),
        ("Calendar", strat_calendar),
        ("Regression to Mean", strat_regression),
        ("Markov Transition", strat_markov_fast),
        ("Multi-horizon", strat_multi_horizon),
    ]

    n_strats = len(strategies)

    # Tracking por estrategia
    hits_all = [[] for _ in range(n_strats)]          # hits numeros por sorteo
    hit_dist = [[0] * 7 for _ in range(n_strats)]     # distribucion 0..6 aciertos
    reintegro_hits = [0 for _ in range(n_strats)]      # aciertos de reintegro
    # Premios reales: [strat][hits][0=sin reint, 1=con reint] -> count
    prizes = [[[0, 0] for _ in range(7)] for _ in range(n_strats)]

    # Estado incremental
    state = DrawState()

    # Warmup
    print(f"\nCalentando estado con {start_idx} sorteos...")
    for i in range(start_idx):
        state.update(draws[i])

    # Backtest
    print(f"Ejecutando backtest de {backtest_count} sorteos...\n")
    t0 = time.time()

    for bt_i in range(backtest_count):
        draw_idx = start_idx + bt_i
        actual_draw = draws[draw_idx]
        actual_set = set(actual_draw.nums)
        actual_reintegro = actual_draw.reintegro
        weekday = actual_draw.weekday

        base_preds = []
        for s_idx, (name, func) in enumerate(strategies):
            if func is None:
                # Random: promedio de RANDOM_REPS
                total_hits = 0
                total_reint = 0
                reps_dist = [0] * 7
                reps_prizes = [[0, 0] for _ in range(7)]
                for _ in range(RANDOM_REPS):
                    pred_nums, pred_reint = strat_random(state, weekday)
                    h = len(set(pred_nums) & actual_set)
                    r_ok = 1 if pred_reint == actual_reintegro else 0
                    total_hits += h
                    reps_dist[h] += 1
                    total_reint += r_ok
                    reps_prizes[h][r_ok] += 1
                avg_hits = total_hits / RANDOM_REPS
                hits_all[s_idx].append(avg_hits)
                for h in range(7):
                    hit_dist[s_idx][h] += reps_dist[h] / RANDOM_REPS
                    prizes[s_idx][h][0] += reps_prizes[h][0] / RANDOM_REPS
                    prizes[s_idx][h][1] += reps_prizes[h][1] / RANDOM_REPS
                reintegro_hits[s_idx] += total_reint / RANDOM_REPS
            else:
                pred_nums, pred_reint = func(state, weekday)
                hits = len(set(pred_nums) & actual_set)
                r_ok = 1 if pred_reint == actual_reintegro else 0
                hits_all[s_idx].append(hits)
                hit_dist[s_idx][hits] += 1
                prizes[s_idx][hits][r_ok] += 1
                if r_ok:
                    reintegro_hits[s_idx] += 1

                # Guardar predicciones de estrategias base para ensemble
                if func in base_for_ensemble:
                    base_preds.append(pred_nums)

        # Actualizar ensemble weights
        if base_preds:
            ensemble.update_weights(base_preds, actual_set)

        # Revelar sorteo: actualizar estado
        state.update(actual_draw)

        # Progreso
        if (bt_i + 1) % PROGRESS_EVERY == 0 or bt_i == backtest_count - 1:
            elapsed = time.time() - t0
            pct = (bt_i + 1) / backtest_count * 100
            remaining = elapsed / (bt_i + 1) * (backtest_count - bt_i - 1)
            print(f"  [{pct:5.1f}%] Sorteo {bt_i + 1}/{backtest_count}"
                  f"  ({elapsed:.1f}s elapsed, ~{remaining:.0f}s left)")

    elapsed_total = time.time() - t0
    print(f"\nBacktest completado en {elapsed_total:.1f}s")

    # --- Resultados ----------------------------------------------------------

    random_hits = hits_all[0]
    results = []
    for s_idx, (name, func) in enumerate(strategies):
        h = hits_all[s_idx]
        avg = sum(h) / len(h)
        if s_idx == 0:
            p_val = None
        else:
            p_val = mann_whitney_p(h, random_hits)
        results.append((name, avg, p_val, s_idx))

    results.sort(key=lambda x: x[1], reverse=True)

    expected = PICK * PICK / NUMS

    # --- Distribucion de aciertos (conteos reales) ---
    print(f"\n{'=' * 95}")
    print(f"  ACIERTOS POR METODO ({backtest_count} sorteos, teorico random: {expected:.4f} hits/sorteo)")
    print(f"{'=' * 95}")
    print(f"{'#':>3}  {'Metodo':<22} {'Avg':>5} | {'0ac':>5} {'1ac':>5} {'2ac':>5} {'3ac':>5} {'4ac':>4} {'5ac':>3} {'6ac':>3} | {'R ok':>5}/{backtest_count}")
    print("-" * 95)

    best_name = None
    best_func = None
    for rank, (name, avg, p_val, s_idx) in enumerate(results, 1):
        d = hit_dist[s_idx]
        r_total = reintegro_hits[s_idx]

        marker = ""
        if s_idx != 0 and best_name is None:
            best_name = name
            best_func = strategies[s_idx][1]
            marker = " <-"

        if p_val is None:
            sig = ""
        elif p_val < 0.01:
            sig = " **"
        elif p_val < 0.05:
            sig = " *"
        else:
            sig = ""

        print(f"{rank:>3}. {name:<22} {avg:>5.3f} | {d[0]:>5.0f} {d[1]:>5.0f} {d[2]:>5.0f} {d[3]:>5.0f} {d[4]:>4.0f} {d[5]:>3.0f} {d[6]:>3.0f} | {r_total:>5.0f}{sig}{marker}")

    # --- Premios ganados (conteos reales) ---
    # prizes[s_idx][hits][0=sin reint, 1=con reint]
    # Primitiva: R solo, 3ac, 3+R, 4ac, 4+R, 5ac, 5+R, 6ac, 6+R
    print(f"\n{'=' * 95}")
    print(f"  PREMIOS GANADOS ({backtest_count} sorteos) -- conteos reales")
    print(f"  R=solo reintegro | 3,4,5,6=aciertos numeros | +R=con reintegro acertado")
    print(f"{'=' * 95}")
    print(f"{'#':>3}  {'Metodo':<22} {'R':>4} | {'3ac':>4} {'3+R':>4} | {'4ac':>4} {'4+R':>4} | {'5ac':>3} {'5+R':>3} | {'6ac':>3} {'6+R':>3} | {'Total':>5}")
    print("-" * 95)

    for rank, (name, avg, p_val, s_idx) in enumerate(results, 1):
        p = prizes[s_idx]
        # R solo = 0 hits + reintegro ok
        r_solo = p[0][1] + p[1][1] + p[2][1]  # reintegro sin premio de numeros (0,1,2 hits)
        n3    = p[3][0]  # 3 aciertos sin reintegro
        n3_r  = p[3][1]  # 3 aciertos con reintegro
        n4    = p[4][0]
        n4_r  = p[4][1]
        n5    = p[5][0]
        n5_r  = p[5][1]
        n6    = p[6][0]
        n6_r  = p[6][1]
        total_premios = r_solo + n3 + n3_r + n4 + n4_r + n5 + n5_r + n6 + n6_r

        print(f"{rank:>3}. {name:<22} {r_solo:>4.0f} | {n3:>4.0f} {n3_r:>4.0f} | {n4:>4.0f} {n4_r:>4.0f} | {n5:>3.0f} {n5_r:>3.0f} | {n6:>3.0f} {n6_r:>3.0f} | {total_premios:>5.0f}")

    # --- Combinacion magica --------------------------------------------------

    print(f"\n{'=' * 78}")
    next_weekday = draws[-1].weekday
    if best_func:
        combo_nums, combo_reint = best_func(state, next_weekday)
        print(f"  COMBINACION MAGICA para el proximo sorteo")
        print(f"  Mejor metodo: {best_name}")
        print(f"\n  >> {' '.join(f'{n:2d}' for n in combo_nums)}  R:{combo_reint} <<")
    else:
        print("  No se encontro metodo mejor que random.")
    print(f"{'=' * 78}")

    # --- Top 5 combinaciones -------------------------------------------------

    print(f"\n  Top 5 metodos -- combinaciones para proximo sorteo:")
    for rank, (name, avg, p_val, s_idx) in enumerate(results[:6], 1):
        func = strategies[s_idx][1]
        if func is None:
            pred_nums, pred_reint = strat_random(state, next_weekday)
            name += " (muestra)"
        else:
            pred_nums, pred_reint = func(state, next_weekday)
        print(f"  {rank}. {name:<25} -> {' '.join(f'{n:2d}' for n in pred_nums)}  R:{pred_reint}")

    print()


if __name__ == "__main__":
    main()
