#!/usr/bin/env python3
"""
LABORATORIO DE MÉTODOS — LA PRIMITIVA
=====================================

Prueba muchos métodos de selección de números sobre TODO el histórico, de forma honesta:

  * Walk-forward: para predecir el sorteo i, cada método solo ve los sorteos 0..i-1.
  * Se compara contra la teoría del azar puro (distribución hipergeométrica):
      aciertos esperados = 6·6/49 = 0,7347 por combinación.
  * z-score y p-valor de cada método, con corrección de Bonferroni
    (al probar muchos métodos, alguno "gana" por suerte).
  * Validación fuera de muestra: se eligen los 3 mejores en la PRIMERA mitad del
    histórico y se comprueba si siguen siendo mejores en la SEGUNDA mitad.
  * Test chi-cuadrado de sesgo del bombo: ¿alguna bola sale más de lo que debería?

Uso:
    python3 scripts/laboratorio_primitiva.py [ruta_csv] [--desde N]

Para añadir un método nuevo: escribe una función que reciba el estado `E`
(solo información del pasado) y devuelva un set de 6 números, y añádela a METODOS.
"""
import csv
import math
import random
import sys
from collections import Counter, deque
from math import comb
from pathlib import Path

N, K = 49, 6
NUMS = range(1, N + 1)

# ─── Teoría ──────────────────────────────────────────────────────────────────
P_ACIERTOS = {k: comb(K, k) * comb(N - K, K - k) / comb(N, K) for k in range(K + 1)}
MEDIA_TEO = sum(k * p for k, p in P_ACIERTOS.items())                  # 0,7347
SD_TEO = math.sqrt(sum((k - MEDIA_TEO) ** 2 * p for k, p in P_ACIERTOS.items()))  # 0,7600

# Premios aproximados de La Primitiva (€ por apuesta de 1 €). 3 aciertos es fijo (8 €);
# el resto son medias orientativas porque dependen de la recaudación y los acertantes.
PREMIO = {3: 8, 4: 70, 5: 2_500, 6: 1_000_000}


# ─── Estado incremental (SOLO pasado) ────────────────────────────────────────
class Estado:
    """Todo lo que un método puede saber antes del sorteo i."""

    VENTANAS = (6, 12, 25, 50, 100)

    def __init__(self):
        self.h = []                                   # sorteos pasados (cronológico)
        self.freq = Counter()                         # apariciones totales
        self.ultimo = {}                              # índice de la última aparición
        self.suma_gaps = Counter()                    # para el ciclo medio
        self.n_gaps = Counter()
        self.ventana = {w: (deque(), Counter()) for w in self.VENTANAS}
        self.pares = Counter()                        # co-apariciones (a, b)
        self.trans = Counter()                        # (j en sorteo t-1, n en sorteo t)
        self.trans_base = Counter()                   # veces que j estuvo en t-1
        self.ema = {n: K / N for n in NUMS}           # media exponencial (alpha 0.02)
        self.ema_r = {n: K / N for n in NUMS}         # media exponencial (alpha 0.10)

    def anadir(self, sorteo):
        i = len(self.h)
        s = set(sorteo)
        if self.h:
            for j in self.h[-1]:
                self.trans_base[j] += 1
                for n in s:
                    self.trans[(j, n)] += 1
        for n in s:
            if n in self.ultimo:
                self.suma_gaps[n] += i - self.ultimo[n]
                self.n_gaps[n] += 1
            self.ultimo[n] = i
            self.freq[n] += 1
        ss = sorted(s)
        for a in range(len(ss)):
            for b in range(a + 1, len(ss)):
                self.pares[(ss[a], ss[b])] += 1
        for w, (dq, c) in self.ventana.items():
            dq.append(s)
            c.update(s)
            if len(dq) > w:
                c.subtract(dq.popleft())
        for n in NUMS:
            x = 1.0 if n in s else 0.0
            self.ema[n] += 0.02 * (x - self.ema[n])
            self.ema_r[n] += 0.10 * (x - self.ema_r[n])
        self.h.append(s)

    def gap(self, n):
        return len(self.h) - self.ultimo.get(n, -1)

    def ciclo(self, n):
        return self.suma_gaps[n] / self.n_gaps[n] if self.n_gaps[n] else len(self.h)


# ─── Utilidades ──────────────────────────────────────────────────────────────
rng = random.Random(2026)


def top(puntuacion, k=K, mayor=True):
    """Los k números con mejor puntuación; empates rotos al azar (sin sesgo)."""
    nums = list(NUMS)
    rng.shuffle(nums)
    nums.sort(key=puntuacion, reverse=mayor)
    return set(nums[:k])


def aleatoria_filtrada(cond, intentos=5000):
    for _ in range(intentos):
        c = sorted(rng.sample(NUMS, K))
        if cond(c):
            return set(c)
    return set(rng.sample(NUMS, K))


def combinacion_tipica(c):
    """Perfil 'típico' de una combinación ganadora: suma 115-185, 2-4 impares,
    2-4 bajos (≤24) y sin 3 consecutivos."""
    impares = sum(x % 2 for x in c)
    bajos = sum(x <= 24 for x in c)
    consec = any(c[i] + 1 == c[i + 1] and c[i + 1] + 1 == c[i + 2] for i in range(K - 2))
    return 115 <= sum(c) <= 185 and 2 <= impares <= 4 and 2 <= bajos <= 4 and not consec


def parejas_greedy(E):
    """Empieza por la pareja más repetida y añade el número con más co-apariciones."""
    if not E.pares:
        return set(rng.sample(NUMS, K))
    (a, b), _ = E.pares.most_common(1)[0]
    elegidos = [a, b]
    while len(elegidos) < K:
        cand = max((n for n in NUMS if n not in elegidos),
                   key=lambda n: sum(E.pares[tuple(sorted((n, e)))] for e in elegidos) + rng.random() * 1e-6)
        elegidos.append(cand)
    return set(elegidos)


def markov(E):
    """P(n sale | los números del último sorteo), con suavizado de Laplace."""
    if not E.h:
        return set(rng.sample(NUMS, K))
    previos = E.h[-1]
    return top(lambda n: sum((E.trans[(j, n)] + 1) / (E.trans_base[j] + N / K) for j in previos))


def consenso(E):
    tops = [top(lambda n, w=w: E.ventana[w][1][n], 12) for w in (6, 12, 25)]
    return top(lambda n: sum(n in t for t in tops) + E.freq[n] * 1e-6)


def equilibrio(E):
    bajos = sorted(range(1, 25), key=lambda n: -E.freq[n])[:3]
    altos = sorted(range(25, N + 1), key=lambda n: -E.freq[n])[:3]
    return set(bajos + altos)


def bayes_sesgo(E):
    """Beta-Binomial: estima la probabilidad real de cada bola y elige las que, con un
    90 % de credibilidad, más probablemente estén por ENCIMA de 6/49."""
    t = len(E.h)
    def score(n):
        a, b = 1 + E.freq[n], 1 + t - E.freq[n]
        m = a / (a + b)
        sd = math.sqrt(a * b / ((a + b) ** 2 * (a + b + 1)))
        return m - 1.28 * sd
    return top(score)


METODOS = {
    "Aleatorio (control)":           lambda E: set(rng.sample(NUMS, K)),
    "Frecuencia total (más salen)":  lambda E: top(lambda n: E.freq[n]),
    "Frecuencia total (menos salen)": lambda E: top(lambda n: E.freq[n], mayor=False),
    "Calientes 6 sorteos":           lambda E: top(lambda n: E.ventana[6][1][n]),
    "Calientes 12 sorteos":          lambda E: top(lambda n: E.ventana[12][1][n]),
    "Calientes 25 sorteos":          lambda E: top(lambda n: E.ventana[25][1][n]),
    "Calientes 50 sorteos":          lambda E: top(lambda n: E.ventana[50][1][n]),
    "Calientes 100 sorteos":         lambda E: top(lambda n: E.ventana[100][1][n]),
    "Fríos 50 sorteos":              lambda E: top(lambda n: E.ventana[50][1][n], mayor=False),
    "Debidos (más tiempo sin salir)": lambda E: top(E.gap),
    "Ciclo (gap / ciclo medio)":     lambda E: top(lambda n: E.gap(n) / max(E.ciclo(n), 1)),
    "Recién salidos (gap mínimo)":   lambda E: top(E.gap, mayor=False),
    "Repetir último sorteo":         lambda E: set(E.h[-1]) if E.h else set(rng.sample(NUMS, K)),
    "Media exponencial lenta":       lambda E: top(lambda n: E.ema[n]),
    "Media exponencial rápida":      lambda E: top(lambda n: E.ema_r[n]),
    "Markov (tras el último sorteo)": markov,
    "Parejas más repetidas":         parejas_greedy,
    "Consenso 6/12/25":              consenso,
    "Equilibrio bajos/altos":        equilibrio,
    "Bayes (sesgo de bola)":         bayes_sesgo,
    "Aleatoria con perfil típico":   lambda E: aleatoria_filtrada(combinacion_tipica),
    "Anti-popular (sin fechas)":     lambda E: aleatoria_filtrada(lambda c: sum(x > 31 for x in c) >= 3),
}


# ─── Estadística ─────────────────────────────────────────────────────────────
def p_valor_dos_colas(z):
    return math.erfc(abs(z) / math.sqrt(2))


def resumen(cuentas, total):
    media = sum(k * v for k, v in cuentas.items()) / total
    z = (media - MEDIA_TEO) / (SD_TEO / math.sqrt(total))
    retorno = sum(PREMIO.get(k, 0) * v for k, v in cuentas.items()) / total
    return media, z, retorno


def ejecutar(sorteos, desde):
    E = Estado()
    for s in sorteos[:desde]:
        E.anadir(s)
    aciertos = {m: [] for m in METODOS}               # aciertos por sorteo evaluado
    for i in range(desde, len(sorteos)):
        real = set(sorteos[i])
        for m, fn in METODOS.items():
            aciertos[m].append(len(fn(E) & real))
        E.anadir(sorteos[i])                          # ahora sí pasa a ser "pasado"
    return aciertos


def tabla(titulo, aciertos, filas=None):
    total = len(next(iter(aciertos.values())))
    print(f"\n{titulo} — {total} sorteos")
    print(f"{'Método':34} {'media':>6} {'z':>6} {'p':>6} {'3':>5} {'4':>4} {'5':>3} {'6':>3} {'€/apuesta':>9}")
    t = {k: P_ACIERTOS[k] * total for k in range(7)}
    ret_teo = sum(PREMIO.get(k, 0) * p for k, p in P_ACIERTOS.items())
    print(f"{'· Teoría (azar puro)':34} {MEDIA_TEO:6.3f} {'':>6} {'':>6} {t[3]:5.0f} {t[4]:4.1f} "
          f"{t[5]:3.1f} {t[6]:3.1f} {ret_teo:9.3f}")
    filas = filas or list(aciertos)
    datos = []
    for m in filas:
        c = Counter(aciertos[m])
        media, z, ret = resumen(c, total)
        datos.append((m, media, z, c, ret))
    for m, media, z, c, ret in sorted(datos, key=lambda d: -d[1]):
        print(f"{m:34} {media:6.3f} {z:6.2f} {p_valor_dos_colas(z):6.3f} {c[3]:5d} {c[4]:4d} "
              f"{c[5]:3d} {c[6]:3d} {ret:9.3f}")
    return datos


def test_sesgo_bombo(sorteos):
    c = Counter(n for s in sorteos for n in s)
    esperado = len(sorteos) * K / N
    chi2 = sum((c[n] - esperado) ** 2 / esperado for n in NUMS)
    gl = N - 1
    # Aproximación de Wilson-Hilferty para el p-valor de chi-cuadrado
    zc = ((chi2 / gl) ** (1 / 3) - (1 - 2 / (9 * gl))) / math.sqrt(2 / (9 * gl))
    p = 0.5 * math.erfc(zc / math.sqrt(2))
    print("\nTEST DE SESGO DEL BOMBO (chi-cuadrado, todo el histórico)")
    print(f"  chi² = {chi2:.1f} con {gl} g.l.  →  p = {p:.3f}")
    print("  " + ("⚠️ Hay indicios de sesgo: merece investigarse." if p < 0.01
                  else "Sin evidencia de sesgo: las frecuencias encajan con un bombo justo."))
    mas = c.most_common(3)
    menos = c.most_common()[-3:]
    print(f"  Más salidas: {', '.join(f'{n} ({v})' for n, v in mas)}   "
          f"Menos: {', '.join(f'{n} ({v})' for n, v in menos)}   Esperado ≈ {esperado:.0f}")


def main():
    raiz = Path(__file__).resolve().parent.parent
    ruta = Path(sys.argv[1]) if len(sys.argv) > 1 and not sys.argv[1].startswith("--") \
        else raiz / "app/src/main/res/raw/historico_primitiva.csv"
    desde = 100
    if "--desde" in sys.argv:
        desde = int(sys.argv[sys.argv.index("--desde") + 1])

    filas = list(csv.DictReader(open(ruta, encoding="utf-8")))
    sorteos = [[int(r[f"n{i}"]) for i in range(1, 7)] for r in filas][::-1]  # cronológico
    print(f"Histórico: {len(sorteos)} sorteos ({filas[-1]['fecha']} → {filas[0]['fecha']})")
    print(f"Teoría: media {MEDIA_TEO:.4f} aciertos/combinación, desviación {SD_TEO:.4f}")

    test_sesgo_bombo(sorteos)

    aciertos = ejecutar(sorteos, desde)
    datos = tabla("TODOS LOS MÉTODOS, TODO EL HISTÓRICO (walk-forward)", aciertos)

    n_metodos = len(METODOS)
    umbral = 0.05 / n_metodos
    significativos = [m for m, _, z, _, _ in datos if p_valor_dos_colas(z) < umbral]
    print(f"\nCorrección de Bonferroni: con {n_metodos} métodos, un resultado es significativo "
          f"solo si p < {umbral:.4f}.")
    print("  Métodos significativamente distintos del azar: "
          + (", ".join(significativos) if significativos else "NINGUNO"))

    # Validación fuera de muestra
    mitad = len(next(iter(aciertos.values()))) // 2
    primera = {m: v[:mitad] for m, v in aciertos.items()}
    segunda = {m: v[mitad:] for m, v in aciertos.items()}
    rank1 = sorted(primera, key=lambda m: -sum(primera[m]) / mitad)
    mejores = [m for m in rank1 if m != "Aleatorio (control)"][:3]
    print(f"\nVALIDACIÓN FUERA DE MUESTRA: los 3 mejores de la 1ª mitad → {', '.join(mejores)}")
    tabla("Cómo les va en la 2ª mitad (datos que no vieron al ser elegidos)",
          segunda, mejores + ["Aleatorio (control)"])
    print("\nSi un método tuviera ventaja real, seguiría por encima de 0,735 en la 2ª mitad.")
    print("'€/apuesta' es orientativo: premios de 4, 5 y 6 aciertos varían en cada sorteo.")


if __name__ == "__main__":
    main()
