#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import csv
import os
import re
from datetime import datetime
from pathlib import Path
from urllib.request import urlopen, Request

# Directorio de salida
OUTPUT_DIR = Path(__file__).parent.parent / "app" / "src" / "main" / "res" / "raw"

# Fuentes TXT de Lotoideas (Son las más fiables)
SOURCES = {
    'primitiva': 'https://www.lotoideas.com/txt/primitiva.txt',
    'bonoloto': 'https://www.lotoideas.com/txt/bonoloto.txt',
    'euromillones': 'https://www.lotoideas.com/txt/euromillones.txt',
    'gordo_primitiva': 'https://www.lotoideas.com/txt/el_gordo.txt',
}

# Cabeceras para evitar el error 406
HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Accept': '*/*',
    'Accept-Language': 'es-ES,es;q=0.9',
}

def parse_date(date_str):
    try:
        # Lotoideas usa DD/MM/YYYY
        d, m, y = date_str.strip().split('/')
        return f"{y}-{m.zfill(2)}-{d.zfill(2)}"
    except:
        return date_str

def descargar_real(loteria, url):
    print(f"📡 Descargando datos REALES de {loteria.upper()}...")
    try:
        req = Request(url, headers=HEADERS)
        with urlopen(req, timeout=30) as response:
            lineas = response.read().decode('utf-8', errors='ignore').splitlines()
        
        resultados = []
        for line in lineas:
            if not line or ';' not in line: continue
            parts = line.split(';')
            if len(parts) < 7: continue
            
            fecha = parse_date(parts[0])
            
            if loteria in ['primitiva', 'bonoloto'] and len(parts) >= 9:
                # Formato: Fecha;N1;N2;N3;N4;N5;N6;C;R
                nums = [parts[i].strip() for i in range(1, 7)]
                resultados.append([fecha] + nums + [parts[7].strip(), parts[8].strip()])
            
            elif loteria == 'euromillones' and len(parts) >= 8:
                # Formato: Fecha;N1;N2;N3;N4;N5;E1;E2
                nums = [parts[i].strip() for i in range(1, 6)]
                resultados.append([fecha] + nums + [parts[6].strip(), parts[7].strip()])

            elif loteria == 'gordo_primitiva' and len(parts) >= 7:
                # Formato: Fecha;N1;N2;N3;N4;N5;Clave
                nums = [parts[i].strip() for i in range(1, 6)]
                resultados.append([fecha] + nums + [parts[6].strip()])

        if resultados:
            # IMPORTANTE: Ordenar por fecha para que la app lea bien
            resultados.sort(key=lambda x: x[0], reverse=True)
            
            filename = f"historico_{loteria}.csv"
            filepath = OUTPUT_DIR / filename
            
            with open(filepath, 'w', newline='', encoding='utf-8') as f:
                writer = csv.writer(f)
                if loteria in ['primitiva', 'bonoloto']:
                    writer.writerow(['fecha', 'n1', 'n2', 'n3', 'n4', 'n5', 'n6', 'complementario', 'reintegro'])
                elif loteria == 'euromillones':
                    writer.writerow(['fecha', 'n1', 'n2', 'n3', 'n4', 'n5', 'estrella1', 'estrella2'])
                elif loteria == 'gordo_primitiva':
                    writer.writerow(['fecha', 'n1', 'n2', 'n3', 'n4', 'n5', 'numero_clave'])
                writer.writerows(resultados)
            print(f"   ✅ OK: {len(resultados)} sorteos guardados.")
            return True
    except Exception as e:
        print(f"   ❌ Error: {e}")
    return False

def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for loteria, url in SOURCES.items():
        descargar_real(loteria, url)

if __name__ == '__main__':
    main()
