#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import csv
import os
import time
import re
from datetime import datetime
from pathlib import Path
from urllib.request import urlopen, Request

# Directorio de salida
OUTPUT_DIR = Path(__file__).parent.parent / "app" / "src" / "main" / "res" / "raw"

# Fuentes alternativas estables (CSV directo de portales de datos abiertos)
SOURCES = {
    'primitiva': 'https://www.combinacionganadora.com/exportar/primitiva/',
    'bonoloto': 'https://www.combinacionganadora.com/exportar/bonoloto/',
    'euromillones': 'https://www.combinacionganadora.com/exportar/euromillones/',
    'gordo_primitiva': 'https://www.combinacionganadora.com/exportar/el-gordo-de-la-primitiva/',
}

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
}

def parse_date(date_str):
    """Limpia y formatea la fecha del CSV."""
    date_str = date_str.strip().replace('"', '')
    for fmt in ('%d/%m/%Y', '%Y-%m-%d', '%d-%m-%Y'):
        try:
            return datetime.strptime(date_str, fmt).strftime('%Y-%m-%d')
        except ValueError:
            continue
    return date_str

def procesar_csv_externo(loteria, url):
    print(f"📊 Descargando {loteria.upper()} desde fuente estable...")
    try:
        req = Request(url, headers=HEADERS)
        with urlopen(req, timeout=20) as response:
            contenido = response.read().decode('utf-8', errors='ignore')
        
        lineas = contenido.splitlines()
        resultados = []
        
        # El formato de exportación suele ser CSV separado por comas o punto y coma
        delimitador = ';' if ';' in lineas[0] else ','
        
        for line in lineas:
            parts = [p.strip().replace('"', '') for p in line.split(delimitador)]
            if not parts or len(parts) < 5: continue
            
            # Intentar detectar si es una línea de datos (empieza por fecha)
            if not re.match(r'\d', parts[0]): continue
            
            fecha = parse_date(parts[0])
            
            try:
                if loteria in ['primitiva', 'bonoloto'] and len(parts) >= 9:
                    nums = [int(parts[i]) for i in range(1, 7)]
                    comp = int(parts[7])
                    reint = int(parts[8])
                    resultados.append([fecha] + nums + [comp, reint])
                
                elif loteria == 'euromillones' and len(parts) >= 8:
                    nums = [int(parts[i]) for i in range(1, 6)]
                    estrellas = [int(parts[6]), int(parts[7])]
                    resultados.append([fecha] + nums + estrellas)

                elif loteria == 'gordo_primitiva' and len(parts) >= 7:
                    nums = [int(parts[i]) for i in range(1, 6)]
                    clave = int(parts[6])
                    resultados.append([fecha] + nums + [clave])
            except:
                continue

        if resultados:
            filename = f"historico_{loteria}.csv"
            filepath = OUTPUT_DIR / filename
            # Ordenar: más reciente primero
            resultados.sort(key=lambda x: x[0], reverse=True)
            
            with open(filepath, 'w', newline='', encoding='utf-8') as f:
                writer = csv.writer(f)
                if loteria in ['primitiva', 'bonoloto']:
                    writer.writerow(['fecha', 'n1', 'n2', 'n3', 'n4', 'n5', 'n6', 'complementario', 'reintegro'])
                elif loteria == 'euromillones':
                    writer.writerow(['fecha', 'n1', 'n2', 'n3', 'n4', 'n5', 'estrella1', 'estrella2'])
                elif loteria == 'gordo_primitiva':
                    writer.writerow(['fecha', 'n1', 'n2', 'n3', 'n4', 'n5', 'numero_clave'])
                writer.writerows(resultados)
            print(f"   ✅ {loteria} actualizado: {len(resultados)} sorteos.")
            return True
    except Exception as e:
        print(f"   ❌ Error con {loteria}: {e}")
    return False

def main():
    print("🎰 ACTUALIZADOR DE HISTÓRICOS (DATOS REALES)")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for loteria, url in SOURCES.items():
        exito = procesar_csv_externo(loteria, url)
        if not exito:
            print(f"   ⚠️ Reintentando {loteria} con fuente secundaria...")
            # Aquí podrías añadir una segunda URL si la primera falla
        time.sleep(1)
    print("🚀 Proceso terminado.")

if __name__ == '__main__':
    main()
