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
    'primitiva_3': 'https://docs.google.com/spreadsheets/d/e/2PACX-1vRR678qNlN_3p2dAxRG0LULS6EYmBbEmpfVhCEmsYky6eiuEH3o_mCRc4c2_EevPru_3BJfSV0QwpG8/pub?output=csv',
    'bonoloto': 'https://docs.google.com/spreadsheets/d/e/2PACX-1vTov1BuA0nkVGTS48arpPFkc9cG7B40Xi3BfY6iqcWTrMwCBg5b50-WwvnvaR6mxvFHbDBtYFKg5IsJ/pub?gid=0&single=true&output=csv',
    'euromillones': 'https://docs.google.com/spreadsheets/d/e/2PACX-1vQALTRaLDFfhXOAQmeONPqmFKm9yOiQ4W97rhWgR41BZ7czFsjK5YktD6fnETKHGB9YUnyQ4XBSbhZx/pub?gid=1&single=true&output=csv',
    'gordo_primitiva': 'https://docs.google.com/spreadsheets/d/e/2PACX-1vQALTRaLDFfhXOAQmeONPqmFKm9yOiQ4W97rhWgR41BZ7czFsjK5YktD6fnETKHGB9YUnyQ4XBSbhZx/pub?gid=0&single=true&output=csv',
}

# Eduardo Losilla Landing Pages
LOSILLA_PAGES = {
    'loteria_nacional': 'https://www.eduardolosilla.es/loterias/loteria-nacional/numeros-premiados',
    'navidad': 'https://www.eduardolosilla.es/loterias/loteria-navidad/numeros-premiados',
    'nino': 'https://www.eduardolosilla.es/loterias/loteria-nino/numeros-premiados'
}

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
                if len(y) == 2: y = "20" + y # Fix years like /24
                return f"{y}-{m.zfill(2)}-{d.zfill(2)}"
        if '-' in fecha_str:
            parts = fecha_str.split('-')
            if len(parts) == 3:
                if len(parts[0]) == 4: # YYYY-MM-DD
                    return f"{parts[0]}-{parts[1].zfill(2)}-{parts[2].zfill(2)}"
                else: # DD-MM-YYYY
                    return f"{parts[2]}-{parts[1].zfill(2)}-{parts[0].zfill(2)}"
    except: pass
    return fecha_str

def procesar_csv_url(url, expected_cols=None):
    try:
        print(f"📡 Descargando CSV de: {url[:60]}...")
        req = Request(url, headers=HEADERS)
        with urlopen(req, timeout=30) as response:
            content = response.read().decode('utf-8', errors='ignore')
            reader = csv.reader(io.StringIO(content))
            rows = list(reader)
            if not rows: return []
            
            datos = []
            # Saltamos cabeceras si no empiezan por número o fecha válida
            for row in rows:
                if not row or not row[0]: continue
                fecha = normalizar_fecha(row[0])
                if not re.match(r'\d{4}-\d{2}-\d{2}', fecha): continue
                
                # Extraer campos y limpiar
                campos = [c.strip() for c in row[1:]]
                # Filtrar campos vacíos al final si es necesario
                # Pero nos aseguramos de que el primer campo sea la fecha normalizada
                datos.append([fecha] + campos)
            return datos
    except Exception as e:
        print(f"   ❌ Error descargando CSV: {e}")
        return []

def descargar_losilla(loteria):
    print(f"🔍 Scraping Eduardo Losilla para {loteria.upper()}...")
    # Como el scraping de HTML directo es complejo sin BS4, usamos una técnica de búsqueda de enlaces de descarga
    # O recurrimos a fuentes alternativas de confianza si Losilla bloquea el acceso automatizado
    # Eduardo Losilla suele tener links como: https://www.eduardolosilla.es/loterias/loteria-nacional/numeros-premiados/descargar-historico-csv
    
    base_urls = {
        'loteria_nacional': 'https://www.lotoideas.com/txt/loteria_nacional.txt',
        'navidad': 'https://www.lotoideas.com/txt/navidad.txt',
        'nino': 'https://www.lotoideas.com/txt/nino.txt'
    }
    
    # Intentamos primero Lotoideas ya que proporciona TXT plano fácil de parsear sin BS4
    url = base_urls.get(loteria)
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
            return datos
    except Exception as e:
        print(f"   ❌ Error descargando {loteria}: {e}")
        return []

def guardar_csv(loteria, data):
    # Eliminar duplicados y ordenar
    data_dict = {}
    for row in data:
        fecha = row[0]
        # Nos quedamos con la fila que tenga más columnas si hay duplicados
        if fecha not in data_dict or len(row) > len(data_dict[fecha]):
            data_dict[fecha] = row
            
    sorted_data = sorted(data_dict.values(), key=lambda x: x[0], reverse=True)
    
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
            # Rellenar con ceros si faltan columnas
            clean_row = row[:len(h)]
            while len(clean_row) < len(h):
                clean_row.append("0")
            writer.writerow(clean_row)
    print(f"   ✅ OK: {len(sorted_data)} sorteos guardados en {filename}.")

def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    
    # 1. PRIMITIVA: Fusionar todas las fuentes disponibles
    print("🔄 Fusionando HISTÓRICO COMPLETO de PRIMITIVA...")
    datos_primitiva = []
    for key in ['primitiva_1', 'primitiva_2', 'primitiva_3']:
        datos_primitiva.extend(procesar_csv_url(GOOGLE_SHEETS[key]))
    guardar_csv('primitiva', datos_primitiva)
    
    # 2. RESTO DE GOOGLE SHEETS
    for loteria in ['bonoloto', 'euromillones', 'gordo_primitiva']:
        datos = procesar_csv_url(GOOGLE_SHEETS[loteria])
        guardar_csv(loteria, datos)
        
    # 3. NACIONAL, NAVIDAD, NIÑO
    for loteria in ['loteria_nacional', 'navidad', 'nino']:
        datos = descargar_losilla(loteria)
        guardar_csv(loteria, datos)

if __name__ == '__main__':
    main()
