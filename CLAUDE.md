# LoteriaProbabilidad — notas para Claude

App Android (Kotlin + Jetpack Compose) para analizar históricos de loterías españolas
y generar combinaciones. Repo: github.com/HCTop/LoteriaProbabilidad (rama `master`).
El dueño es Hector, aprendiz de programador (sobre todo Java): explicar en español y con claridad.

## Reglas que NO se pueden romper

1. **Los CSV van de MÁS RECIENTE a MÁS ANTIGUO** (`historico[0]` = último sorteo).
   - Sorteos recientes → `take(n)`, nunca `takeLast(n)`.
   - El "pasado" del sorteo `i` es `historico.drop(i + 1)` / `subList(i + 1, size)`.
2. **Nada de look-ahead**: en backtests y evaluaciones, cada predicción solo puede usar
   sorteos ANTERIORES al que se evalúa (frecuencias incluidas). Calcular dentro del bucle
   con `historicoHastaMomento`, no con todo el histórico.
3. **Predicciones: siempre a través de `domain/usecase/GestorPredicciones.kt`**.
   - `obtenerOGenerar()` devuelve lo guardado para el próximo sorteo o lo genera y lo guarda.
   - `evaluarPendientes()` compara lo guardado con el resultado real y actualiza el aprendizaje.
   - Tiene candado por lotería: no guardar/evaluar predicciones por otro camino.
   - La pantalla de inicio (`SeleccionViewModel`) y Resultados (`ResultadosViewModel`) lo usan.
4. **Context**: ViewModels, factories y clases de larga vida usan `applicationContext`.
5. **No inventar datos**: si un CSV/scraper no trae un valor, descartar la fila, no rellenar.

## Arquitectura rápida

- `data/datasource/LoteriaLocalDataSource` — lee CSV (prioriza `filesDir` sobre `res/raw`),
  con caché en memoria que se invalida sola cuando cambia el archivo descargado.
- `data/datasource/ActualizadorHistorico` — descarga CSV desde GitHub cada 3 h
  (validación de cabecera `fecha,` + escritura atómica). Lo llama `SeleccionViewModel`
  ANTES de evaluar aciertos.
- `domain/ml/HistorialPredicciones` — persistencia (SharedPreferences) de predicciones
  y evaluaciones; historial de 60 sorteos.
- `domain/ml/MemoriaIA` — pesos aprendidos. `registrarRendimientoEstrategias` acepta
  aciertos Int o medias Double.
- `service/AprendizajeContinuo` — mini walk-forward mientras la app está visible.
  OJO: evalúa versiones simplificadas de las estrategias y escribe en el mismo historial
  que los sorteos reales (pendiente de separar).
- `service/SincronizacionDiariaWorker` — WorkManager, 1 vez al día (~9:00, con red y batería):
  descarga CSV, `evaluarPendientes()` y guarda predicciones del próximo sorteo de las 4 loterías
  de bolas. Se programa en `MainActivity.onCreate` con KEEP (no se duplica).
- Reintegro (Primitiva/Bonoloto): cada método lo calcula con SU criterio en
  `CalculadorProbabilidad.reintegrosSegunMetodo` (fríos → el menos salido, calientes → últimos 50,
  debidos → el más atrasado…) usando solo sorteos desde `INICIO_REINTEGRO` (1991-06-20; antes el CSV
  trae 0 inventado). `ObtenerCombinacionesUseCase` ya NO lo baraja. Solo se puede elegir comprando online.
- Historial: entradas `simulado = true` vienen del botón "Simular los últimos 10 sorteos"
  (walk-forward, sin entrenar la IA); una evaluación real sustituye a la simulada.
- `scripts/laboratorio_primitiva.py` — laboratorio honesto: ~22 métodos en walk-forward sobre
  todo el histórico, z-score frente al azar, Bonferroni, validación fuera de muestra y test
  chi-cuadrado de sesgo del bombo. Para probar un método nuevo, añadirlo a `METODOS` y ejecutar.
  Resultado actual (4.101 sorteos): ningún método se distingue del azar; bombo sin sesgo (p=0,96).
- `scripts/laboratorio_ia.py` — métodos "inventados": Gradient Boosting y regresión logística con
  16 señales (reentreno cada 250 sorteos), mezcla de expertos (Hedge) y día de la semana.
  Resultado: todos ≈ 0,735; la IA da la misma probabilidad (0,1222 vs 0,1223) a las bolas que salen
  y a las que no → no hay señal aprovechable. Necesita numpy y scikit-learn.
- `scripts/actualizar_datos.py` + `.github/workflows/actualizar-historicos.yml` —
  regeneran los CSV cada noche desde lotoideas (Google Sheets).

## Build

- AGP 8.5.2 (subido desde 8.2.0, ver abajo), Kotlin 1.9.20, compose compiler 1.5.5, compileSdk 34.
- **Compose BOM 2024.09.00 (Compose UI 1.7)**: necesario para el crash
  `IllegalStateException: The ACTION_HOVER_EXIT event was not cleared` al hacer scroll
  con ratón en el emulador (arreglado en Compose UI 1.7.0-alpha03). No bajar de esa versión.
- Wrapper apunta a `gradle-9.0-milestone-1` (arriesgado con AGP; revisar si falla el build).
- **JDK fijado en `gradle.properties`** (`org.gradle.java.home`, ruta absoluta de la máquina
  de Hector): el JBR 25 que trae Android Studio por defecto no compila este proyecto, hace
  falta JDK 21. Si Android Studio no compila, confirmar primero que esa ruta sigue existiendo.
- **AGP 8.2.0 → 8.5.2**: necesario para que las librerías nativas del APK (p. ej.
  `libandroidx.graphics.path.so` de Compose) queden alineadas a 16 KB, no solo a 4 KB
  ("page size" de 16 KB que exigen dispositivos/Play Console recientes). Verificar con
  el offset de cada entrada `.so` dentro del APK (debe ser múltiplo de 16384), no basta
  con que el build pase.
- Al subir AGP, el lint "vital" de `assembleRelease` se puso más estricto: `backup_rules.xml`
  y `data_extraction_rules.xml` no pueden usar `domain="no_backup"` (no es válido ahí; ese
  directorio ya se excluye solo del backup). Si vuelve a aparecer ese error al tocar esos
  XML, quitar la línea en vez de "arreglar" el domain.

## Pendientes conocidos

- Euromillones: la 2ª estrella era siempre 1 por un fallo del scraper (columna vacía).
  Script corregido; el CSV se regenera con la Action nocturna (o lanzarla a mano).
- `AprendizajeContinuo`: separar su historial del de sorteos reales o eliminarlo.
- "K-Fold temporal" en `MotorInteligencia` entrena con datos futuros; solo walk-forward es válido.
- Pantalla Backtest crea otro `ResultadosViewModel` que regenera predicciones solo para
  obtener los históricos (pesado).
- `PantallaLogin`: contraseña en texto plano en un repo público.
- Datos de Lotería Nacional muy cortos (22 sorteos, hasta 2024); faltan Navidad 2025 y Niño 2026.
- `MotorInteligencia.kt` tiene ~5.500 líneas: partir por estrategias.

## Expectativas honestas

Los sorteos son aleatorios e independientes. Tras quitar el look-ahead, los aciertos de
backtest rondan el azar (~0,73 aciertos por combinación en Primitiva). Si un cambio
"mejora mucho" los aciertos, sospechar primero de una fuga de datos futuros.
