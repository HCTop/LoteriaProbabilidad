#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import csv
import os
import re
import io
from datetime import datetime
from pathlib import Path
from urllib.request import urlopen, Request

# Directorio de salida
OUTPUT_DIR = Path(__file__).parent.parent / "app" / "src" / "main" / "res" / "raw"

# Mapeo de Google Sheets (Proporcionados por el usuario)
GOOGLE_SHEETS_SOURCES = {
    'primitiva': 'https://docs.google.com/spreadsheets/d/e/2PACX-1vTov1BuA0nkVGTS48arpPFkc9cG7B40Xi3BfY6iqcWTrMwCBg5b50-WwvnvaR6mxvFHbDBtYFKg5IsJ/pub?gid=1&single=true&output=csv',
    'bonoloto': 'https://docs.google.com/spreadsheets/d/e/2PACX-1vTov1BuA0nkVGTS48arpPFkc9cG7B40Xi3BfY6iqcWTrMwCBg5b50-WwvnvaR6mxvFHbDBtYFKg5IsJ/pub?gid=0&single=true&output=csv',
    'euromillones': 'https://docs.google.com/spreadsheets/d/e/2PACX-1vQALTRaLDFfhXOAQmeONPqmFKm9yOiQ4W97rhWgR41BZ7czFsjK5YktD6fnETKHGB9YUnyQ4XBSbhZx/pub?gid=1&single=true&output=csv',
    'gordo_primitiva': 'https://docs.google.com/spreadsheets/d/e/2PACX-1vQALTRaLDFfhXOAQmeONPqmFKm9yOiQ4W97rhWgR41BZ7czFsjK5YktD6fnETKHGB9YUnyQ4XBSbhZx/pub?gid=0&single=true&output=csv',
}

# Páginas para buscar los de Lotería Nacional, Navidad y Niño
LOTOIDEAS_LANDING = {
    'loteria_nacional': 'https://www.lotoideas.com/loteria-nacional-resultados-historicos-de-todos-los-sorteos/',
    'navidad': 'https://www.lotoideas.com/loteria-de-navidad-resultados-historicos-de-todos-los-sorteos/',
    'nino': 'https://www.lotoideas.com/loteria-del-nino-resultados-historicos-de-todos-los-sorteos/'
}

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
}

def parse_date(date_str):
    date_str = date_str.strip()
    try:
        # Intentar varios formatos comunes
        for fmt in ('%Y-%m-%d', '%d/%m/%Y', '%d-%m-%Y'):
            try:
                dt = datetime.strptime(date_str, fmt)
                return dt.strftime('%Y-%m-%d')
            except: continue
    except: pass
    return date_str

def procesar_csv_google(loteria, url):
    print(f"📡 Descargando {loteria.upper()} desde Google Sheets...")
    try:
        req = Request(url, headers=HEADERS)
        with urlopen(req, timeout=20) as response:
            content = response.read().decode('utf-8')
            reader = csv.reader(io.StringIO(content))
            
            rows = list(reader)
            if not rows: return False
            
            # Detectar si hay cabecera y saltarla si es necesario
            data_start = 0
            if not re.match(r'\d', rows[0][0]): data_start = 1
            
            final_data = []
            for row in rows[data_start:]:
                if not row or not row[0]: continue
                fecha = parse_date(row[0])
                # Limpiar números (quitar espacios, etc)
                nums = [n.strip() for n in row[1:] if n.strip()]
                final_data.append([fecha] + nums)
            
            if final_data:
                guardar_csv(loteria, final_data)
                return True
    except Exception as e:
        print(f"   ❌ Error en Google Sheets {loteria}: {e}")
    return False

def buscar_y_descargar_lotoideas(loteria, landing_url):
    print(f"🔍 Buscando datos reales de {loteria.upper()} en Lotoideas...")
    try:
        req = Request(landing_url, headers=HEADERS)
        with urlopen(req, timeout=15) as response:
            html = response.read().decode('utf-8')
            # Buscar enlaces .txt o .csv
            links = re.findall(r'href="([^"]+\.(?:txt|csv))"', html)
            if not links: return False
            
            download_url = links[0]
            if not download_url.startswith('http'):
                download_url = 'https://www.lotoideas.com' + (download_url if download_url.startswith('/') else '/' + download_url)
            
            print(f"   📡 Descargando desde: {download_url}")
            req_dl = Request(download_url, headers=HEADERS)
            with urlopen(req_dl, timeout=20) as resp_dl:
                content = resp_dl.read().decode('utf-8', errors='ignore')
                lineas = content.splitlines()
            
            final_data = []
            for line in lineas:
                if ';' not in line: continue
                parts = line.split(';')
                if len(parts) < 3: continue
                fecha = parse_date(parts[0])
                if not re.match(r'\d{4}-\d{2}-\d{2}', fecha): continue
                final_data.append([fecha] + [p.strip() for p in parts[1:]])
            
            if final_data:
                guardar_csv(loteria, final_data)
                return True
    except Exception as e:
        print(f"   ❌ Error en Lotoideas {loteria}: {e}")
    return False

def guardar_csv(loteria, data):
    # Ordenar por fecha descendente
    data.sort(key=lambda x: x[0], reverse=True)
    
    filename = f"historico_{loteria}.csv"
    filepath = OUTPUT_DIR / filename
    
    headers = {
        'primitiva': ['fecha','n1','n2','n3','n4','n5','n6','complementario','reintegro'],
        'bonoloto': ['fecha','n1','n2','n3','n4','n5','n6','complementario','reintegro'],
        'euromillones': ['fecha','n1','n2','n3','n4','n5','estrella1','estrella2'],
        'gordo_primitiva': ['fecha','n1','n2','n3','n4','n5','numero_clave'],
        'loteria_nacional': ['fecha','primer_premio','segundo_premio','reintegro1','reintegro2','reintegro3','reintegro4'],
        'navidad': ['fecha','gordo','segundo','tercero','reintegro1','reintegro2','reintegro3','reintegro4'],
        'nino': ['fecha','primer_premio','segundo_premio','reintegro1','reintegro2','reintegro3','reintegro4']
    }
    
    with open(filepath, 'w', newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        h = headers.get(loteria, ['fecha'])
        writer.writerow(h)
        # Asegurar que solo escribimos el número de columnas de la cabecera
        for row in data:
            writer.writerow(row[:len(h)])
    print(f"   ✅ OK: {len(data)} sorteos guardados en {filename}.")

def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    
    # 1. Procesar Google Sheets
    for loteria, url in GOOGLE_SHEETS_SOURCES.items():
        procesar_csv_google(loteria, url)
        
    # 2. Procesar Lotoideas (para Nacional, Navidad, Niño)
    for loteria, landing_url in LOTOIDEAS_LANDING.items():
        buscar_y_descargar_lotoideas(loteria, landing_url)

if __name__ == '__main__':
    main()
