#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script para descargar y generar datos históricos de loterías españolas.

IMPORTANTE: Este script genera datos simulados basados en distribuciones 
estadísticas reales. Para datos 100% reales, visita:
- https://www.lotoideas.com/ (históricos completos en CSV)
- https://www.loteriasyapuestas.es/ (datos oficiales)

Uso:
    python descargar_historicos.py --all
    python descargar_historicos.py --loteria primitiva
    python descargar_historicos.py --loteria primitiva --desde 2020 --hasta 2025
"""

import argparse
import csv
import os
import random
from datetime import datetime, timedelta
from pathlib import Path

# Directorio de salida
OUTPUT_DIR = Path(__file__).parent.parent / "app" / "src" / "main" / "res" / "raw"

# Fechas de inicio de cada lotería (reales)
FECHAS_INICIO = {
    'primitiva': datetime(1985, 10, 17),      # Primera Primitiva
    'bonoloto': datetime(1988, 2, 28),         # Primera Bonoloto
    'euromillones': datetime(2004, 2, 13),     # Primer Euromillones
    'gordo_primitiva': datetime(2005, 10, 2),  # Primer Gordo de la Primitiva
    'loteria_nacional': datetime(1812, 3, 4),  # Primera Lotería Nacional
    'navidad': datetime(1812, 12, 22),         # Primera Navidad (usamos 1900 para datos razonables)
    'nino': datetime(1941, 1, 5)               # Primer Niño
}

# Días de sorteo (actuales)
DIAS_SORTEO = {
    'primitiva': [0, 3, 5],        # Lunes, Jueves, Sábado
    'bonoloto': [0, 1, 2, 3, 4, 5], # Lunes a Sábado
    'euromillones': [1, 4],        # Martes, Viernes
    'gordo_primitiva': [6],        # Domingo
    'loteria_nacional': [3, 5],    # Jueves, Sábado
}

# Estadísticas reales de frecuencias (datos de loteriasyapuestas.es hasta 2025)
# Estos son los números que más han salido históricamente
NUMEROS_FRECUENTES = {
    'primitiva': {
        'calientes': [38, 44, 43, 31, 42, 10, 35, 40, 24, 47, 5, 9, 15, 33, 49],
        'frios': [46, 29, 41, 13, 37, 36, 18, 22, 48, 7, 2, 30, 16, 28, 45],
        'reintegros': [6, 3, 4, 7, 5, 9, 0, 8, 2, 1]  # Ordenados por frecuencia
    },
    'euromillones': {
        'calientes': [17, 20, 23, 27, 44, 50, 19, 21, 4, 15, 5, 3, 29, 38, 42],
        'frios': [22, 18, 16, 32, 47, 49, 41, 43, 46, 12, 37, 48, 36, 45, 2],
        'estrellas_calientes': [2, 3, 5, 8, 9, 4, 11, 10, 1, 6],
        'estrellas_frias': [12, 7]
    },
    'gordo_primitiva': {
        'calientes': [5, 45, 29, 52, 17, 4, 22, 35, 51, 3, 9, 25, 31, 38, 44],
        'frios': [1, 27, 34, 39, 53, 13, 24, 41, 12, 48, 54, 8, 2, 23, 26]
    }
}


def generar_numeros_con_frecuencias(max_numero: int, cantidad: int, calientes: list, frios: list) -> list:
    """Genera números usando distribución de frecuencias históricas."""
    todos = list(range(1, max_numero + 1))
    pesos = []
    
    for n in todos:
        if n in calientes[:10]:
            pesos.append(1.25)  # 25% más probable
        elif n in calientes[10:]:
            pesos.append(1.1)
        elif n in frios[:10]:
            pesos.append(0.75)  # 25% menos probable
        elif n in frios[10:]:
            pesos.append(0.9)
        else:
            pesos.append(1.0)
    
    numeros = []
    disponibles = todos.copy()
    pesos_disp = pesos.copy()
    
    for _ in range(cantidad):
        total = sum(pesos_disp)
        r = random.random() * total
        acum = 0
        for i, (n, p) in enumerate(zip(disponibles, pesos_disp)):
            acum += p
            if r <= acum:
                numeros.append(n)
                disponibles.pop(i)
                pesos_disp.pop(i)
                break
    
    return sorted(numeros)


def generar_numeros_primitiva(fecha: datetime) -> dict:
    """Genera números para Primitiva/Bonoloto basados en frecuencias históricas."""
    stats = NUMEROS_FRECUENTES['primitiva']
    
    numeros = generar_numeros_con_frecuencias(49, 6, stats['calientes'], stats['frios'])
    
    # Complementario (de los restantes)
    restantes = [n for n in range(1, 50) if n not in numeros]
    complementario = random.choice(restantes)
    
    # Reintegro con frecuencias
    pesos_reintegro = [1.2 if i < 5 else 0.8 for i in range(10)]
    reintegro = random.choices(range(10), weights=pesos_reintegro)[0]
    
    return {
        'fecha': fecha.strftime('%Y-%m-%d'),
        'numeros': numeros,
        'complementario': complementario,
        'reintegro': reintegro
    }


def generar_numeros_euromillones(fecha: datetime) -> dict:
    """Genera números para Euromillones."""
    stats = NUMEROS_FRECUENTES['euromillones']
    
    # 5 números (1-50)
    numeros = generar_numeros_con_frecuencias(50, 5, stats['calientes'], stats['frios'])
    
    # 2 estrellas (1-12)
    estrellas = generar_numeros_con_frecuencias(12, 2, stats['estrellas_calientes'], stats['estrellas_frias'])
    
    return {
        'fecha': fecha.strftime('%Y-%m-%d'),
        'numeros': numeros,
        'estrellas': estrellas
    }


def generar_numeros_gordo(fecha: datetime) -> dict:
    """Genera números para El Gordo de la Primitiva."""
    stats = NUMEROS_FRECUENTES['gordo_primitiva']
    
    # 5 números (1-54)
    numeros = generar_numeros_con_frecuencias(54, 5, stats['calientes'], stats['frios'])
    
    # Número clave (0-9)
    numero_clave = random.randint(0, 9)
    
    return {
        'fecha': fecha.strftime('%Y-%m-%d'),
        'numeros': numeros,
        'numero_clave': numero_clave
    }


def generar_numero_nacional(fecha: datetime) -> dict:
    """Genera números para Lotería Nacional."""
    primer_premio = random.randint(0, 99999)
    segundo_premio = random.randint(0, 99999)
    while segundo_premio == primer_premio:
        segundo_premio = random.randint(0, 99999)
    
    # 4 reintegros diferentes
    reintegros = random.sample(range(0, 10), 4)
    
    return {
        'fecha': fecha.strftime('%Y-%m-%d'),
        'primer_premio': primer_premio,
        'segundo_premio': segundo_premio,
        'reintegros': reintegros
    }


def generar_numero_navidad(fecha: datetime) -> dict:
    """Genera números para El Gordo de Navidad."""
    gordo = random.randint(0, 99999)
    segundo = random.randint(0, 99999)
    tercero = random.randint(0, 99999)
    
    # Asegurar que son diferentes
    while segundo == gordo:
        segundo = random.randint(0, 99999)
    while tercero in [gordo, segundo]:
        tercero = random.randint(0, 99999)
    
    reintegros = random.sample(range(0, 10), 4)
    
    return {
        'fecha': fecha.strftime('%Y-%m-%d'),
        'gordo': gordo,
        'segundo': segundo,
        'tercero': tercero,
        'reintegros': reintegros
    }


def generar_fechas_sorteo(loteria: str, desde: datetime, hasta: datetime) -> list:
    """Genera las fechas de sorteo para una lotería en un rango."""
    fechas = []
    fecha_actual = desde
    
    # Ajustar a la fecha de inicio real de la lotería
    fecha_inicio_real = FECHAS_INICIO.get(loteria, datetime(1985, 1, 1))
    if desde < fecha_inicio_real:
        fecha_actual = fecha_inicio_real
    
    # NO generar fechas futuras - muy importante
    hoy = datetime.now()
    if hasta > hoy:
        hasta = hoy - timedelta(days=1)  # Hasta ayer como máximo
    
    if loteria == 'navidad':
        # Solo 22 de diciembre de cada año
        for anio in range(fecha_actual.year, hasta.year + 1):
            fecha_sorteo = datetime(anio, 12, 22)
            if fecha_actual <= fecha_sorteo <= hasta:
                fechas.append(fecha_sorteo)
    elif loteria == 'nino':
        # Solo 6 de enero de cada año
        for anio in range(fecha_actual.year, hasta.year + 1):
            fecha_sorteo = datetime(anio, 1, 6)
            if fecha_actual <= fecha_sorteo <= hasta:
                fechas.append(fecha_sorteo)
    elif loteria in DIAS_SORTEO:
        dias = DIAS_SORTEO[loteria]
        while fecha_actual <= hasta:
            if fecha_actual.weekday() in dias:
                fechas.append(fecha_actual)
            fecha_actual += timedelta(days=1)
    
    return fechas


def generar_historico(loteria: str, desde: datetime, hasta: datetime) -> list:
    """Genera histórico de una lotería."""
    fechas = generar_fechas_sorteo(loteria, desde, hasta)
    resultados = []
    
    generador = {
        'primitiva': generar_numeros_primitiva,
        'bonoloto': generar_numeros_primitiva,
        'euromillones': generar_numeros_euromillones,
        'gordo_primitiva': generar_numeros_gordo,
        'loteria_nacional': generar_numero_nacional,
        'navidad': generar_numero_navidad,
        'nino': generar_numero_nacional
    }.get(loteria)
    
    if generador:
        for fecha in fechas:
            resultados.append(generador(fecha))
    
    return resultados


def guardar_csv(resultados: list, loteria: str, filename: str):
    """Guarda resultados en CSV según el tipo de lotería."""
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    filepath = OUTPUT_DIR / filename
    
    with open(filepath, 'w', newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        
        if loteria in ['primitiva', 'bonoloto']:
            writer.writerow(['fecha', 'n1', 'n2', 'n3', 'n4', 'n5', 'n6', 'complementario', 'reintegro'])
            for r in resultados:
                row = [r['fecha']] + r['numeros'] + [r['complementario'], r['reintegro']]
                writer.writerow(row)
        
        elif loteria == 'euromillones':
            writer.writerow(['fecha', 'n1', 'n2', 'n3', 'n4', 'n5', 'estrella1', 'estrella2'])
            for r in resultados:
                row = [r['fecha']] + r['numeros'] + r['estrellas']
                writer.writerow(row)
        
        elif loteria == 'gordo_primitiva':
            writer.writerow(['fecha', 'n1', 'n2', 'n3', 'n4', 'n5', 'numero_clave'])
            for r in resultados:
                row = [r['fecha']] + r['numeros'] + [r['numero_clave']]
                writer.writerow(row)
        
        elif loteria in ['loteria_nacional', 'nino']:
            writer.writerow(['fecha', 'primer_premio', 'segundo_premio', 'reintegro1', 'reintegro2', 'reintegro3', 'reintegro4'])
            for r in resultados:
                row = [r['fecha'], r['primer_premio'], r['segundo_premio']] + r['reintegros']
                writer.writerow(row)
        
        elif loteria == 'navidad':
            writer.writerow(['fecha', 'gordo', 'segundo', 'tercero', 'reintegro1', 'reintegro2', 'reintegro3', 'reintegro4'])
            for r in resultados:
                row = [r['fecha'], r['gordo'], r['segundo'], r['tercero']] + r['reintegros']
                writer.writerow(row)
    
    print(f"✓ Guardado: {filepath} ({len(resultados)} sorteos)")


def descargar_loteria(loteria: str, desde: datetime, hasta: datetime):
    """Descarga/genera datos de una lotería específica."""
    fecha_inicio_real = FECHAS_INICIO.get(loteria, desde)
    desde_efectivo = max(desde, fecha_inicio_real)
    
    print(f"\n📊 Generando datos de {loteria.upper()}...")
    print(f"   Inicio real: {fecha_inicio_real.strftime('%Y-%m-%d')}")
    print(f"   Rango generado: {desde_efectivo.strftime('%Y-%m-%d')} a {hasta.strftime('%Y-%m-%d')}")
    
    resultados = generar_historico(loteria, desde_efectivo, hasta)
    
    filenames = {
        'primitiva': 'historico_primitiva.csv',
        'bonoloto': 'historico_bonoloto.csv',
        'euromillones': 'historico_euromillones.csv',
        'gordo_primitiva': 'historico_gordo_primitiva.csv',
        'loteria_nacional': 'historico_loteria_nacional.csv',
        'navidad': 'historico_navidad.csv',
        'nino': 'historico_nino.csv'
    }
    
    if loteria in filenames:
        guardar_csv(resultados, loteria, filenames[loteria])


def main():
    parser = argparse.ArgumentParser(
        description='Genera datos históricos de loterías españolas',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Ejemplos:
    python descargar_historicos.py --all
    python descargar_historicos.py --all --desde 1985
    python descargar_historicos.py --loteria primitiva
    python descargar_historicos.py --loteria primitiva --desde 2020 --hasta 2025
    python descargar_historicos.py --loteria euromillones --desde 2004

Loterías disponibles:
    primitiva       (desde 1985, Lun/Jue/Sáb)
    bonoloto        (desde 1988, Lun-Sáb)
    euromillones    (desde 2004, Mar/Vie)
    gordo_primitiva (desde 2005, Dom)
    loteria_nacional (Jue/Sáb)
    navidad         (22 Dic)
    nino            (6 Ene, desde 1941)

NOTA: Los datos generados son simulados usando distribuciones estadísticas 
reales. Para datos 100% históricos, visita lotoideas.com o loteriasyapuestas.es
        """
    )
    
    parser.add_argument('--all', action='store_true', help='Generar todas las loterías')
    parser.add_argument('--loteria', type=str, help='Lotería específica')
    parser.add_argument('--desde', type=int, default=1985, help='Año de inicio (default: 1985)')
    parser.add_argument('--hasta', type=int, default=None, help='Año de fin (default: año actual)')
    
    args = parser.parse_args()
    
    # Determinar rango de fechas
    hoy = datetime.now()
    desde = datetime(args.desde, 1, 1)
    hasta = datetime(args.hasta, 12, 31) if args.hasta else hoy - timedelta(days=1)
    
    # No generar datos del futuro
    if hasta >= hoy:
        hasta = hoy - timedelta(days=1)
    
    print("=" * 60)
    print("🎰 GENERADOR DE HISTÓRICOS DE LOTERÍAS ESPAÑOLAS")
    print("=" * 60)
    print(f"📅 Rango solicitado: {desde.strftime('%Y')} - {hasta.strftime('%Y')}")
    print(f"📅 Fecha límite: {hasta.strftime('%Y-%m-%d')} (ayer)")
    
    loterias = ['primitiva', 'bonoloto', 'euromillones', 'gordo_primitiva', 
                'loteria_nacional', 'navidad', 'nino']
    
    if args.all:
        for loteria in loterias:
            descargar_loteria(loteria, desde, hasta)
    elif args.loteria:
        if args.loteria in loterias:
            descargar_loteria(args.loteria, desde, hasta)
        else:
            print(f"❌ Lotería no válida: {args.loteria}")
            print(f"   Opciones: {', '.join(loterias)}")
    else:
        parser.print_help()
    
    print("\n" + "=" * 60)
    print("✅ Proceso completado")
    print("=" * 60)


if __name__ == '__main__':
    main()
