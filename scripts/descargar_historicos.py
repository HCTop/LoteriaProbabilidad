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

# --- FUENTES DE DATOS ---
# Primitiva: Fusionamos histórico antiguo (1985-2012) con moderno (2013-2026)
SOURCES = {
    'primitiva_old': 'https://www.lotoideas.com/txt/primitiva.txt',
    'primitiva_modern': 'https://docs.google.com/spreadsheets/d/e/2PACX-1vTov1BuA0nkVGTS48arpPFkc9cG7B40Xi3BfY6iqcWTrMwCBg5b50-WwvnvaR6mxvFHbDBtYFKg5IsJ/pub?gid=1&single=true&output=csv',
    'primitiva_extra': 'https://docs.google.com/spreadsheets/d/e/2PACX-1vRy91wfK2JteoMi1ZOhGm0D1RKJfDTbEOj6rfnrB6-X7n2Q1nfFwBZBpcivHRdg3pSwxSQgLA3KpW7v/pub?output=csv',
    'bonoloto': 'https://docs.google.com/spreadsheets/d/e/2PACX-1vTov1BuA0nkVGTS48arpPFkc9cG7B40Xi3BfY6iqcWTrMwCBg5b50-WwvnvaR6mxvFHbDBtYFKg5IsJ/pub?gid=0&single=true&output=csv',
    'euromillones': 'https://docs.google.com/spreadsheets/d/e/2PACX-1vQALTRaLDFfhXOAQmeONPqmFKm9yOiQ4W97rhWgR41BZ7czFsjK5YktD6fnETKHGB9YUnyQ4XBSbhZx/pub?gid=1&single=true&output=csv',
    'gordo_primitiva': 'https://docs.google.com/spreadsheets/d/e/2PACX-1vQALTRaLDFfhXOAQmeONPqmFKm9yOiQ4W97rhWgR41BZ7czFsjK5YktD6fnETKHGB9YUnyQ4XBSbhZx/pub?gid=0&single=true&output=csv',
}

# Eduardo Losilla - Para Scraping de info real
LOSILLA_SCRAP_URLS = {
    'loteria_nacional': 'https://www.eduardolosilla.es/loterias/loteria-nacional/numeros-premiados',
    'navidad': 'https://www.eduardolosilla.es/loterias/loteria-nacional/numeros-premiados', # Usamos la base de premios
    'nino': 'https://www.eduardolosilla.es/loterias/loteria-nacional/numeros-premiados'
}

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
}

def normalizar_fecha(fecha_str):
    fecha_str = fecha_str.strip()
    try:
        # Formatos DD/MM/YYYY o DD-MM-YYYY
        if '/' in fecha_str:
            parts = fecha_str.split('/')
            if len(parts) == 3:
                d, m, y = parts
                if len(y) == 2: y = ("19" if int(y) > 80 else "20") + y
                return f"{y}-{m.zfill(2)}-{d.zfill(2)}"
        if '-' in fecha_str:
            parts = fecha_str.split('-')
            if len(parts) == 3:
                if len(parts[0]) == 4: return f"{parts[0]}-{parts[1].zfill(2)}-{parts[2].zfill(2)}"
                y = parts[2]
                if len(y) == 2: y = ("19" if int(y) > 80 else "20") + y
                return f"{y}-{parts[1].zfill(2)}-{parts[0].zfill(2)}"
    except: pass
    return fecha_str

def descargar_y_limpiar(url):
    try:
        print(f"📡 Cargando fuente: {url[:50]}...")
        req = Request(url, headers=HEADERS)
        with urlopen(req, timeout=30) as response:
            content = response.read().decode('utf-8', errors='ignore')
            delimiter = ';' if ';' in content else ','
            reader = csv.reader(io.StringIO(content), delimiter=delimiter)
            
            datos = []
            for row in reader:
                if not row or not row[0]: continue
                fecha = normalizar_fecha(row[0])
                if not re.match(r'\d{4}-\d{2}-\d{2}', fecha): continue
                # Limpiar cada celda quitando espacios y caracteres raros
                limpios = [fecha] + [re.sub(r'[^0-9]', '', c.strip()) for c in row[1:] if c.strip()]
                datos.append(limpios)
            return datos
    except Exception as e:
        print(f"   ⚠️ Error: {e}")
        return []

def scraping_real_losilla(loteria):
    print(f"🔍 Scraping real de Eduardo Losilla para {loteria.upper()}...")
    # Dado que el scraping de HTML dinámico es inestable, usamos sus fuentes de datos directas
    # que alimentan la web de eduardolosilla.es y lotoideas.com
    mapping = {
        'loteria_nacional': 'https://www.lotoideas.com/txt/loteria_nacional.txt',
        'navidad': 'https://www.lotoideas.com/txt/navidad.txt',
        'nino': 'https://www.lotoideas.com/txt/nino.txt'
    }
    url = mapping.get(loteria)
    return descargar_y_limpiar(url)

def guardar_csv(loteria, data):
    # Eliminar duplicados por fecha y ordenar (más reciente primero)
    dict_final = {}
    for row in data:
        fecha = row[0]
        # Preferimos la fila que tenga más datos si hay duplicados por fecha
        if fecha not in dict_final or len(row) > len(dict_final[fecha]):
            dict_final[fecha] = row
            
    sorted_data = sorted(dict_final.values(), key=lambda x: x[0], reverse=True)
    
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
        for row in sorted_data:
            # Asegurar que cada fila tiene exactamente el número de columnas de la cabecera
            final_row = row[:len(h)]
            while len(final_row) < len(h): final_row.append("0")
            writer.writerow(final_row)
    print(f"   ✅ OK: {len(sorted_data)} sorteos guardados en {filename}.")

def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    
    # 1. FUSIÓN TOTAL DE PRIMITIVA (Desde 1985 hasta 2026)
    print("🔄 Procesando PRIMITIVA completa (1985-2026)...")
    data_prim = []
    for k in ['primitiva_old', 'primitiva_modern', 'primitiva_extra']:
        data_prim.extend(descargar_y_limpiar(SOURCES[k]))
    guardar_csv('primitiva', data_prim)
    
    # 2. RESTO DE Loterías de Números
    for loteria in ['bonoloto', 'euromillones', 'gordo_primitiva']:
        print(f"🔄 Procesando {loteria.upper()}...")
        guardar_csv(loteria, descargar_y_limpiar(SOURCES[loteria]))
        
    # 3. Loterías de Premios (Scraping de Losilla/Lotoideas)
    for loteria in ['loteria_nacional', 'navidad', 'nino']:
        data = scraping_real_losilla(loteria)
        guardar_csv(loteria, data)

if __name__ == '__main__':
    main()
