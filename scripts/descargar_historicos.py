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

# URLs directas de Lotoideas
SOURCES = {
    'primitiva': 'https://www.lotoideas.com/txt/primitiva.txt',
    'bonoloto': 'https://www.lotoideas.com/txt/bonoloto.txt',
    'euromillones': 'https://www.lotoideas.com/txt/euromillones.txt',
    'gordo_primitiva': 'https://www.lotoideas.com/txt/el_gordo.txt',
}

# Cabeceras completas para saltar el error 406 (Simulación de Chrome Real)
HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Accept': 'text/plain,text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8',
    'Accept-Language': 'es-ES,es;q=0.9',
    'Connection': 'keep-alive',
    'Upgrade-Insecure-Requests': '1',
    'Cache-Control': 'max-age=0'
}

def parse_date(date_str):
    try:
        return datetime.strptime(date_str.strip(), '%d/%m/%Y').strftime('%Y-%m-%d')
    except:
        return date_str

def actualizar_loteria(loteria, url):
    print(f"📊 Actualizando {loteria.upper()} desde Lotoideas...")
    try:
        # Añadimos un pequeño retardo para no saturar el servidor
        time.sleep(1)
        req = Request(url, headers=HEADERS)
        with urlopen(req, timeout=20) as response:
            lineas = response.read().decode('utf-8', errors='ignore').splitlines()
        
        resultados = []
        for line in lineas:
            if not line or ';' not in line: continue
            parts = line.split(';')
            
            if loteria in ['primitiva', 'bonoloto'] and len(parts) >= 9:
                fecha = parse_date(parts[0])
                numeros = [parts[i].strip() for i in range(1, 7)]
                resultados.append([fecha] + numeros + [parts[7].strip(), parts[8].strip()])
            
            elif loteria == 'euromillones' and len(parts) >= 8:
                fecha = parse_date(parts[0])
                numeros = [parts[i].strip() for i in range(1, 6)]
                resultados.append([fecha] + numeros + [parts[6].strip(), parts[7].strip()])

            elif loteria == 'gordo_primitiva' and len(parts) >= 7:
                fecha = parse_date(parts[0])
                numeros = [parts[i].strip() for i in range(1, 6)]
                resultados.append([fecha] + numeros + [parts[6].strip()])

        if resultados:
            filename = f"historico_{loteria}.csv"
            filepath = OUTPUT_DIR / filename
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
        else:
            print(f"   ⚠️ No se encontraron datos en el archivo de {loteria}")

    except Exception as e:
        print(f"   ❌ Error con {loteria}: {e}")

def main():
    print("🎰 INICIANDO ACTUALIZACIÓN DE DATOS REALES (LOTOIDEAS)")
    if not OUTPUT_DIR.exists():
        OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for loteria, url in SOURCES.items():
        actualizar_loteria(loteria, url)
    print("🚀 Proceso terminado.")

if __name__ == '__main__':
    main()
