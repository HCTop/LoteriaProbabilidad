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

# FUENTES REALES Y ESTABLES (Lotoideas)
SOURCES = {
    'primitiva': 'https://www.lotoideas.com/primitiva-resultados-historicos-de-todos-los-sorteos/',
    'bonoloto': 'https://www.lotoideas.com/bonoloto-resultados-historicos-de-todos-los-sorteos/',
    'euromillones': 'https://www.lotoideas.com/euromillones-resultados-historicos-de-todos-los-sorteos/',
    'gordo_primitiva': 'https://www.lotoideas.com/el-gordo-de-la-primitiva-resultados-historicos-de-todos-los-sorteos/',
}

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8',
    'Accept-Language': 'es-ES,es;q=0.9',
}

def parse_date(date_str):
    """Limpia y formatea la fecha."""
    try:
        # Lotoideas suele usar formato DD/MM/YYYY o nombres de mes
        # Intentamos DD/MM/YYYY primero
        match = re.search(r'(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})', date_str)
        if match:
            d, m, y = match.groups()
            if len(y) == 2: y = "20" + y
            return f"{y}-{m.zfill(2)}-{d.zfill(2)}"
    except:
        pass
    return date_str

def extraer_de_lotoideas(loteria, url):
    print(f"🚀 Extrayendo datos de Lotoideas para {loteria.upper()}...")
    try:
        req = Request(url, headers=HEADERS)
        with urlopen(req, timeout=30) as response:
            html = response.read().decode('utf-8', errors='ignore')
        
        # Lotoideas muestra los datos en tablas o bloques de texto
        # Buscamos el patrón: Fecha y números
        resultados = []
        
        if loteria in ['primitiva', 'bonoloto']:
            # Patrón para Primitiva/Bonoloto: Fecha + 6 números + C + R
            # Ejemplo: 08/01/2026 1 11 14 24 28 33 C:37 R:6
            bloques = re.findall(r'(\d{1,2}/\d{1,2}/\d{4})\s+(\d{1,2})\s+(\d{1,2})\s+(\d{1,2})\s+(\d{1,2})\s+(\d{1,2})\s+(\d{1,2})\s+C:?(\d{1,2})\s+R:?(\d{1,2})', html)
            for b in bloques:
                fecha = parse_date(b[0])
                nums = sorted([int(b[i]) for i in range(1, 7)])
                resultados.append([fecha] + nums + [int(b[7]), int(b[8])])
                
        elif loteria == 'euromillones':
            # Patrón para Euromillones: Fecha + 5 números + 2 estrellas
            bloques = re.findall(r'(\d{1,2}/\d{1,2}/\d{4})\s+(\d{1,2})\s+(\d{1,2})\s+(\d{1,2})\s+(\d{1,2})\s+(\d{1,2})\s+E:?(\d{1,2})\s+(\d{1,2})', html)
            for b in bloques:
                fecha = parse_date(b[0])
                nums = sorted([int(b[i]) for i in range(1, 6)])
                estrellas = sorted([int(b[6]), int(b[7])])
                resultados.append([fecha] + nums + estrellas)

        elif loteria == 'gordo_primitiva':
            # Patrón para El Gordo: Fecha + 5 números + Clave
            bloques = re.findall(r'(\d{1,2}/\d{1,2}/\d{4})\s+(\d{1,2})\s+(\d{1,2})\s+(\d{1,2})\s+(\d{1,2})\s+(\d{1,2})\s+N:?(\d{1,2})', html)
            for b in bloques:
                fecha = parse_date(b[0])
                nums = sorted([int(b[i]) for i in range(1, 6)])
                resultados.append([fecha] + nums + [int(b[6])])

        if not resultados:
            # Si el scraping por regex falla, intentamos buscar el enlace al CSV/TXT que suele tener Lotoideas
            match_txt = re.search(r'href="([^"]+\.txt)"', html)
            if match_txt:
                txt_url = match_txt.group(1)
                if not txt_url.startswith('http'):
                    txt_url = "https://www.lotoideas.com" + txt_url
                return descargar_txt_directo(loteria, txt_url)

        if resultados:
            guardar_resultados(loteria, resultados)
            return True
            
    except Exception as e:
        print(f"   ❌ Error en scraping de {loteria}: {e}")
    return False

def descargar_txt_directo(loteria, url):
    print(f"   🔗 Encontrado enlace directo: {url}")
    try:
        req = Request(url, headers=HEADERS)
        with urlopen(req, timeout=20) as response:
            lineas = response.read().decode('utf-8', errors='ignore').splitlines()
        
        resultados = []
        for line in lineas:
            if ';' not in line: continue
            parts = line.split(';')
            if len(parts) < 5: continue
            
            fecha = parse_date(parts[0])
            try:
                if loteria in ['primitiva', 'bonoloto'] and len(parts) >= 9:
                    nums = [int(parts[i]) for i in range(1, 7)]
                    resultados.append([fecha] + nums + [int(parts[7]), int(parts[8])])
                elif loteria == 'euromillones' and len(parts) >= 8:
                    nums = [int(parts[i]) for i in range(1, 6)]
                    resultados.append([fecha] + nums + [int(parts[6]), int(parts[7])])
                elif loteria == 'gordo_primitiva' and len(parts) >= 7:
                    nums = [int(parts[i]) for i in range(1, 6)]
                    resultados.append([fecha] + nums + [int(parts[6])])
            except: continue
            
        if resultados:
            guardar_resultados(loteria, resultados)
            return True
    except: pass
    return False

def guardar_resultados(loteria, resultados):
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
    print(f"   ✅ {loteria} actualizado con {len(resultados)} sorteos.")

def main():
    print("🎰 ACTUALIZACIÓN DESDE LOTOIDEAS (DATOS REALES)")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for loteria, url in SOURCES.items():
        extraer_de_lotoideas(loteria, url)
        time.sleep(2)
    print("🏁 Proceso finalizado.")

if __name__ == '__main__':
    main()
