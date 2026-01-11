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

# Páginas donde se encuentran los enlaces de descarga
LANDING_PAGES = {
    'primitiva': 'https://www.lotoideas.com/primitiva-resultados-historicos-de-todos-los-sorteos/',
    'bonoloto': 'https://www.lotoideas.com/bonoloto-resultados-historicos-de-todos-los-sorteos/',
    'euromillones': 'https://www.lotoideas.com/euromillones-resultados-historicos-de-todos-los-sorteos/',
    'gordo_primitiva': 'https://www.lotoideas.com/el-gordo-de-la-primitiva-resultados-historicos-de-todos-los-sorteos/',
    'loteria_nacional': 'https://www.lotoideas.com/loteria-nacional-resultados-historicos-de-todos-los-sorteos/',
    'navidad': 'https://www.lotoideas.com/loteria-de-navidad-resultados-historicos-de-todos-los-sorteos/',
    'nino': 'https://www.lotoideas.com/loteria-del-nino-resultados-historicos-de-todos-los-sorteos/'
}

# Cabeceras para evitar bloqueos
HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
}

def parse_date(date_str):
    date_str = date_str.strip()
    try:
        # Formato habitual DD/MM/YYYY
        if '/' in date_str:
            parts = date_str.split('/')
            if len(parts) == 3:
                d, m, y = parts
                return f"{y}-{m.zfill(2)}-{d.zfill(2)}"
        elif '-' in date_str and len(date_str) == 10:
            return date_str
    except:
        pass
    return date_str

def buscar_url_real(loteria, landing_url):
    print(f"🔍 Buscando enlace de descarga para {loteria.upper()}...")
    try:
        req = Request(landing_url, headers=HEADERS)
        with urlopen(req, timeout=15) as response:
            html = response.read().decode('utf-8')
            
            # Buscamos enlaces que contengan ".txt" o ".csv"
            # Priorizamos los que están en carpetas /txt/ o similares
            links = re.findall(r'href="([^"]+\.(?:txt|csv))"', html)
            
            for link in links:
                # Filtrar enlaces irrelevantes si los hay
                if 'historico' in link.lower() or loteria in link.lower() or 'el_gordo' in link.lower():
                    if not link.startswith('http'):
                        link = 'https://www.lotoideas.com' + (link if link.startswith('/') else '/' + link)
                    return link
            
            # Si no encontramos con el nombre exacto, devolvemos el primero que parezca de datos
            if links:
                link = links[0]
                if not link.startswith('http'): link = 'https://www.lotoideas.com' + link
                return link
                
    except Exception as e:
        print(f"   ⚠️ Error analizando la página: {e}")
    return None

def descargar_y_procesar(loteria, url):
    if not url: return False
    
    print(f"📡 Descargando desde: {url}")
    try:
        req = Request(url, headers=HEADERS)
        with urlopen(req, timeout=20) as response:
            content = response.read().decode('utf-8', errors='ignore')
            lineas = content.splitlines()
        
        resultados = []
        for line in lineas:
            line = line.strip()
            if not line or ';' not in line: continue
            parts = line.split(';')
            if len(parts) < 3: continue
            
            fecha = parse_date(parts[0])
            if not re.match(r'\d{4}-\d{2}-\d{2}', fecha): continue # Saltar cabeceras o basura
            
            if loteria in ['primitiva', 'bonoloto']:
                if len(parts) >= 9:
                    nums = [parts[i].strip() for i in range(1, 7)]
                    resultados.append([fecha] + nums + [parts[7].strip(), parts[8].strip()])
            
            elif loteria == 'euromillones':
                if len(parts) >= 8:
                    nums = [parts[i].strip() for i in range(1, 6)]
                    resultados.append([fecha] + nums + [parts[6].strip(), parts[7].strip()])

            elif loteria == 'gordo_primitiva':
                if len(parts) >= 7:
                    nums = [parts[i].strip() for i in range(1, 6)]
                    resultados.append([fecha] + nums + [parts[6].strip()])
            
            elif loteria in ['loteria_nacional', 'nino']:
                premios = [parts[1].strip(), parts[2].strip()]
                reintegros = [parts[i].strip() if i < len(parts) else "0" for i in range(3, 7)]
                resultados.append([fecha] + premios + reintegros)

            elif loteria == 'navidad':
                # Navidad suele tener 3 premios principales y reintegros
                premios = [parts[1].strip(), parts[2].strip(), parts[3].strip()]
                reintegros = [parts[i].strip() if i < len(parts) else "0" for i in range(4, 8)]
                resultados.append([fecha] + premios + reintegros)

        if resultados:
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
                elif loteria in ['loteria_nacional', 'nino']:
                    writer.writerow(['fecha', 'primer_premio', 'segundo_premio', 'reintegro1', 'reintegro2', 'reintegro3', 'reintegro4'])
                elif loteria == 'navidad':
                    writer.writerow(['fecha', 'gordo', 'segundo', 'tercero', 'reintegro1', 'reintegro2', 'reintegro3', 'reintegro4'])
                
                writer.writerows(resultados)
            print(f"   ✅ OK: {len(resultados)} sorteos guardados.")
            return True
    except Exception as e:
        print(f"   ❌ Error procesando {url}: {e}")
    return False

def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for loteria, landing_url in LANDING_PAGES.items():
        url_descarga = buscar_url_real(loteria, landing_url)
        if url_descarga:
            descargar_y_procesar(loteria, url_descarga)
        else:
            print(f"   ❌ No se encontró enlace de descarga en {landing_url}")

if __name__ == '__main__':
    main()
