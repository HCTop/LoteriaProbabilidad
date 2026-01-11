#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import csv
import os
import time
from datetime import datetime
from pathlib import Path
from urllib.request import urlopen, Request

# Directorio de salida
OUTPUT_DIR = Path(__file__).parent.parent / "app" / "src" / "main" / "res" / "raw"

# URLs directas de Lotoideas (Archivos completos actualizados)
SOURCES = {
    'primitiva': 'https://www.lotoideas.com/txt/primitiva.txt',
    'bonoloto': 'https://www.lotoideas.com/txt/bonoloto.txt',
    'euromillones': 'https://www.lotoideas.com/txt/euromillones.txt',
    'gordo_primitiva': 'https://www.lotoideas.com/txt/el_gordo.txt',
}

HEADERS = {'User-Agent': 'Mozilla/5.0'}

def parse_date(date_str):
    """Convierte DD/MM/YYYY a YYYY-MM-DD."""
    try:
        return datetime.strptime(date_str.strip(), '%d/%m/%Y').strftime('%Y-%m-%d')
    except:
        return date_str

def actualizar_loteria(loteria, url):
    print(f"📊 Actualizando {loteria.upper()} desde Lotoideas...")
    try:
        req = Request(url, headers=HEADERS)
        with urlopen(req, timeout=15) as response:
            lineas = response.read().decode('utf-8', errors='ignore').splitlines()
        
        resultados = []
        for line in lineas:
            if not line or ';' not in line: continue
            parts = line.split(';')
            
            # Formato Lotoideas suele ser: Fecha;N1;N2;N3;N4;N5;N6;C;R
            if loteria in ['primitiva', 'bonoloto'] and len(parts) >= 9:
                fecha = parse_date(parts[0])
                numeros = [parts[i].strip() for i in range(1, 7)]
                comp = parts[7].strip()
                reint = parts[8].strip()
                resultados.append([fecha] + numeros + [comp, reint])
            
            # Formato Euromillones: Fecha;N1;N2;N3;N4;N5;E1;E2
            elif loteria == 'euromillones' and len(parts) >= 8:
                fecha = parse_date(parts[0])
                numeros = [parts[i].strip() for i in range(1, 6)]
                estrellas = [parts[6].strip(), parts[7].strip()]
                resultados.append([fecha] + numeros + estrellas)

            # Formato El Gordo: Fecha;N1;N2;N3;N4;N5;Clave
            elif loteria == 'gordo_primitiva' and len(parts) >= 7:
                fecha = parse_date(parts[0])
                numeros = [parts[i].strip() for i in range(1, 6)]
                clave = parts[6].strip()
                resultados.append([fecha] + numeros + [clave])

        if resultados:
            filename = f"historico_{loteria}.csv"
            filepath = OUTPUT_DIR / filename
            # Ordenar por fecha (más reciente primero para la app)
            resultados.sort(key=lambda x: x[0], reverse=True)
            
            with open(filepath, 'w', newline='', encoding='utf-8') as f:
                writer = csv.writer(f)
                # Escribir cabecera según tipo
                if loteria in ['primitiva', 'bonoloto']:
                    writer.writerow(['fecha', 'n1', 'n2', 'n3', 'n4', 'n5', 'n6', 'complementario', 'reintegro'])
                elif loteria == 'euromillones':
                    writer.writerow(['fecha', 'n1', 'n2', 'n3', 'n4', 'n5', 'estrella1', 'estrella2'])
                elif loteria == 'gordo_primitiva':
                    writer.writerow(['fecha', 'n1', 'n2', 'n3', 'n4', 'n5', 'numero_clave'])
                
                writer.writerows(resultados)
            print(f"   ✅ {loteria} actualizado: {len(resultados)} sorteos reales.")
        else:
            print(f"   ⚠️ No se pudieron procesar datos para {loteria}")

    except Exception as e:
        print(f"   ❌ Error con {loteria}: {e}")

def main():
    print("🎰 INICIANDO ACTUALIZACIÓN DE DATOS REALES (LOTOIDEAS)")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for loteria, url in SOURCES.items():
        actualizar_loteria(loteria, url)
    print("🚀 Proceso terminado.")

if __name__ == '__main__':
    main()
