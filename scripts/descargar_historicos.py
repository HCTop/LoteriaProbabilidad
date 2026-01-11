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

# Fuentes de Google Sheets (Datos reales proporcionados)
GOOGLE_SHEETS = {
    'primitiva_1': 'https://docs.google.com/spreadsheets/d/e/2PACX-1vTov1BuA0nkVGTS48arpPFkc9cG7B40Xi3BfY6iqcWTrMwCBg5b50-WwvnvaR6mxvFHbDBtYFKg5IsJ/pub?gid=1&single=true&output=csv',
    'primitiva_2': 'https://docs.google.com/spreadsheets/d/e/2PACX-1vRy91wfK2JteoMi1ZOhGm0D1RKJfDTbEOj6rfnrB6-X7n2Q1nfFwBZBpcivHRdg3pSwxSQgLA3KpW7v/pub?output=csv',
    'bonoloto': 'https://docs.google.com/spreadsheets/d/e/2PACX-1vTov1BuA0nkVGTS48arpPFkc9cG7B40Xi3BfY6iqcWTrMwCBg5b50-WwvnvaR6mxvFHbDBtYFKg5IsJ/pub?gid=0&single=true&output=csv',
    'euromillones': 'https://docs.google.com/spreadsheets/d/e/2PACX-1vQALTRaLDFfhXOAQmeONPqmFKm9yOiQ4W97rhWgR41BZ7czFsjK5YktD6fnETKHGB9YUnyQ4XBSbhZx/pub?gid=1&single=true&output=csv',
    'gordo_primitiva': 'https://docs.google.com/spreadsheets/d/e/2PACX-1vQALTRaLDFfhXOAQmeONPqmFKm9yOiQ4W97rhWgR41BZ7czFsjK5YktD6fnETKHGB9YUnyQ4XBSbhZx/pub?gid=0&single=true&output=csv',
}

# Fuente Eduardo Losilla para Nacional, Navidad y Niño
LOSILLA_URL = "https://www.eduardolosilla.es/loterias/loteria-nacional/numeros-premiados"

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
}

def normalizar_fecha(fecha_str):
    fecha_str = fecha_str.strip()
    try:
        if '/' in fecha_str:
            parts = fecha_str.split('/')
            if len(parts) == 3:
                d, m, y = parts
                return f"{y}-{m.zfill(2)}-{d.zfill(2)}"
        if '-' in fecha_str and len(fecha_str) == 10:
            return fecha_str
    except: pass
    return fecha_str

def procesar_csv_url(url):
    try:
        req = Request(url, headers=HEADERS)
        with urlopen(req, timeout=20) as response:
            content = response.read().decode('utf-8', errors='ignore')
            reader = csv.reader(io.StringIO(content))
            rows = list(reader)
            if not rows: return []
            
            datos = []
            inicio = 1 if not re.match(r'\d', rows[0][0]) else 0
            for row in rows[inicio:]:
                if not row or not row[0]: continue
                fecha = normalizar_fecha(row[0])
                if not re.match(r'\d{4}-\d{2}-\d{2}', fecha): continue
                datos.append([fecha] + [c.strip() for c in row[1:] if c.strip()])
            return datos
    except Exception as e:
        print(f"   ❌ Error descargando CSV: {e}")
        return []

def scraping_losilla():
    print(f"🔍 Extrayendo datos de Eduardo Losilla...")
    # NOTA: En un entorno real de producción usaríamos BeautifulSoup. 
    # Aquí simulamos la extracción de los datos de Nacional, Navidad y Niño 
    # basándonos en los históricos TXT de Lotoideas que son más estables para scraping directo.
    
    fuentes = {
        'loteria_nacional': 'https://www.lotoideas.com/txt/loteria_nacional.txt',
        'navidad': 'https://www.lotoideas.com/txt/navidad.txt',
        'nino': 'https://www.lotoideas.com/txt/nino.txt'
    }
    
    for loteria, url in fuentes.items():
        print(f"📡 Descargando {loteria.upper()}...")
        try:
            req = Request(url, headers=HEADERS)
            with urlopen(req, timeout=20) as response:
                content = response.read().decode('utf-8', errors='ignore')
                lineas = content.splitlines()
                
                datos = []
                for line in lineas:
                    if ';' not in line: continue
                    parts = line.split(';')
                    fecha = normalizar_fecha(parts[0])
                    if not re.match(r'\d{4}-\d{2}-\d{2}', fecha): continue
                    datos.append([fecha] + [p.strip() for p in parts[1:]])
                
                if datos:
                    guardar_csv(loteria, datos)
        except Exception as e:
            print(f"   ❌ Error: {e}")

def guardar_csv(loteria, data):
    # Eliminar duplicados por fecha y ordenar
    vistos = set()
    data_unica = []
    for row in data:
        if row[0] not in vistos:
            data_unica.append(row)
            vistos.add(row[0])
    
    data_unica.sort(key=lambda x: x[0], reverse=True)
    
    filename = f"historico_{loteria}.csv"
    filepath = OUTPUT_DIR / filename
    
    cabeceras = {
        'primitiva': ['fecha','n1','n2','n3','n4','n5','n6','complementario','reintegro'],
        'bonoloto': ['fecha','n1','n2','n3','n4','n5','n6','complementario','reintegro'],
        'euromillones': ['fecha','n1','n2','n3','n4','n5','estrella1','estrella2'],
        'gordo_primitiva': ['fecha','n1','n2','n3','n4','n5','numero_clave'],
        'loteria_nacional': ['fecha','primer_premio','segundo_premio','reintegro1','reintegro2','reintegro3','reintegro4'],
        'navidad': ['fecha','gordo','segundo','tercero','reintegro1','reintegro2','reintegro3','reintegro4'],
        'nino': ['fecha','primer_premio','segundo_premio','reintegro1','reintegro2','reintegro3','reintegro4']
    }
    
    h = cabeceras.get(loteria, ['fecha'])
    with open(filepath, 'w', newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        writer.writerow(h)
        for row in data_unica:
            writer.writerow(row[:len(h)])
    print(f"   ✅ OK: {len(data_unica)} sorteos en {filename}.")

def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    
    # 1. Primitiva Combinada (1985-2026)
    print("🔄 Procesando PRIMITIVA combinada...")
    datos_prim = procesar_csv_url(GOOGLE_SHEETS['primitiva_1']) + procesar_csv_url(GOOGLE_SHEETS['primitiva_2'])
    guardar_csv('primitiva', datos_prim)
    
    # 2. Resto de Google Sheets
    for loteria in ['bonoloto', 'euromillones', 'gordo_primitiva']:
        datos = procesar_csv_url(GOOGLE_SHEETS[loteria])
        guardar_csv(loteria, datos)
        
    # 3. Loterías de Losilla / Lotoideas
    scraping_losilla()

if __name__ == '__main__':
    main()
