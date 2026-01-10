# 🎰 Lotería Probabilidad

Aplicación Android para analizar históricos de loterías españolas y generar combinaciones basadas en diferentes métodos de cálculo de probabilidad.

## ✨ Características

### 🎯 Loterías soportadas
| Lotería | Formato | Días de sorteo |
|---------|---------|----------------|
| La Primitiva | 6 números (1-49) + Complementario + Reintegro | Lunes, Jueves, Sábado |
| Bonoloto | 6 números (1-49) + Complementario + Reintegro | Lunes a Sábado |
| Euromillones | 5 números (1-50) + 2 Estrellas (1-12) | Martes, Viernes |
| El Gordo de la Primitiva | 5 números (1-54) + Número Clave (0-9) | Domingo |
| Lotería Nacional | Números de 5 cifras | Jueves, Sábado |
| El Gordo de Navidad | Números de 5 cifras | 22 de Diciembre |
| El Niño | Números de 5 cifras | 6 de Enero |

### 🧮 Métodos de cálculo

1. **Regla de Laplace** - Probabilidad teórica pura: `P(A) = casos favorables / casos posibles`
2. **Análisis de Frecuencias** - Basado en histórico de apariciones
3. **Números Calientes** - Los más frecuentes en sorteos recientes
4. **Números Fríos** - Los menos frecuentes (teoría del equilibrio)
5. **Equilibrio Estadístico** - Mezcla de calientes y fríos
6. **Probabilidad Condicional** - Números que suelen salir juntos
7. **Desviación de la Media** - Números alejados de su frecuencia esperada
8. **Aleatorio Puro** - Selección completamente al azar

### 📅 Filtro por rango de fechas
- Todo el histórico
- Últimos 5/10/20 años
- Años específicos
- Rangos personalizados

### 📊 Datos históricos incluidos
- **Primitiva**: ~6,300 sorteos (desde 1985)
- **Bonoloto**: ~11,850 sorteos (desde 1988)
- **Euromillones**: ~2,285 sorteos (desde 2004)
- **Gordo Primitiva**: ~1,057 sorteos (desde 2005)
- **Lotería Nacional**: ~4,280 sorteos
- **Navidad/Niño**: ~41 sorteos cada uno

## 🔄 Actualización automática de datos (GitHub Actions)

Los datos históricos se actualizan **automáticamente cada día** a las 00:00 hora española mediante GitHub Actions.

### Configuración
El workflow `.github/workflows/actualizar-historicos.yml` ejecuta el script Python diariamente.

### Ejecución manual
También puedes ejecutar la actualización manualmente desde GitHub:
1. Ve a **Actions** → **Actualizar Históricos de Loterías**
2. Click en **Run workflow**
3. Opcionalmente especifica el año desde el que regenerar

## 🛠️ Instalación y uso

### Requisitos
- Android Studio Hedgehog (2023.1.1) o superior
- JDK 17+
- Android SDK 34

### Compilar el proyecto
1. Abre el proyecto en Android Studio
2. Sincroniza Gradle
3. **Build → Rebuild Project**
4. Ejecuta en emulador o dispositivo

### Actualizar datos manualmente
```bash
cd scripts
python3 descargar_historicos.py --all --desde 1985
```

## 📁 Estructura del proyecto

```
LoteriaProbabilidad/
├── .github/
│   └── workflows/
│       └── actualizar-historicos.yml   # GitHub Actions
├── app/
│   └── src/main/
│       ├── java/com/loteria/probabilidad/
│       │   ├── data/
│       │   │   ├── datasource/         # Lectura de CSVs
│       │   │   ├── model/              # Modelos de datos
│       │   │   └── repository/         # Repositorio
│       │   ├── domain/
│       │   │   ├── calculator/         # Calculador de probabilidades
│       │   │   └── usecase/            # Casos de uso
│       │   └── ui/
│       │       ├── components/         # Componentes UI
│       │       ├── screens/            # Pantallas
│       │       └── theme/              # Tema Material 3
│       └── res/
│           └── raw/                    # Datos CSV históricos
└── scripts/
    └── descargar_historicos.py         # Script de actualización
```

## 📐 Fórmulas implementadas

### Regla de Laplace
```
P(A) = casos favorables / casos posibles

Primitiva: P = 1 / C(49,6) = 1 / 13,983,816 ≈ 0.00000715%
Euromillones: P = 1 / (C(50,5) × C(12,2)) = 1 / 139,838,160 ≈ 0.000000715%
```

### Análisis de frecuencias
```
Frecuencia relativa = apariciones del número / total de sorteos
Puntuación = Σ frecuencias de números seleccionados
```

### Probabilidad condicional
```
P(B|A) = P(A∩B) / P(A)
Analiza pares de números que salen juntos frecuentemente
```

## ⚠️ Disclaimer

**IMPORTANTE**: Los sorteos de lotería son eventos aleatorios independientes. 
Las frecuencias históricas NO predicen resultados futuros. 
Esta aplicación es solo para entretenimiento y análisis estadístico.

**Juega con responsabilidad.**

## 📄 Licencia

MIT License - Uso libre para fines educativos y personales.

## 🔗 Fuentes de datos

- [Loterías y Apuestas del Estado](https://www.loteriasyapuestas.es/)
- [Lotoideas.com](https://www.lotoideas.com/) - Históricos completos

---

Desarrollado con ❤️ y Kotlin
