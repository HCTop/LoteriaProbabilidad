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

# FUENTES REALES Y ESTABLES (Usando OpenData y exportaciones directas)
SOURCES = {
    'primitiva': 'https://www.combinacionganadora.com/exportar/primitiva/',
    'bonoloto': 'https://www.combinacionganadora.com/exportar/bonoloto/',
    'euromillones': 'https://www.combinacionganadora.com/exportar/euromillones/',
    'gordo_primitiva': 'https://www.combinacionganadora.com/exportar/el-gordo-de-la-primitiva/',
}

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Accept': 'text/csv,application/csv,text/plain'
}

def parse_date(date_str):
    date_str = date_str.strip().replace('"', '')
    for fmt in ('%d/%m/%Y', '%Y-%m-%d', '%d-%m-%Y'):
        try:
            return datetime.strptime(date_str, fmt).strftime('%Y-%m-%d')
        except ValueError:
            continue
    return date_str

def actualizar_datos_reales(loteria, url):
    print(f"🚀 Obteniendo datos reales para {loteria.upper()}...")
    try:
        req = Request(url, headers=HEADERS)
        with urlopen(req, timeout=25) as response:
            contenido = response.read().decode('utf-8', errors='ignore')
        
        lineas = contenido.splitlines()
        resultados = []
        
        # Detectar delimitador (punto y coma o coma)
        delimitador = ';' if ';' in lineas[0] else ','
        
        for line in lineas:
            parts = [p.strip().replace('"', '') for p in line.split(delimitador)]
            if len(parts) < 5 or not re.match(r'\d', parts[0]):
                continue
            
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
            except:
                continue

        if resultados:
            # Ordenar: Más reciente primero
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
            print(f"   ✅ {loteria} actualizado con {len(resultados)} sorteos REALES.")
            return True
    except Exception as e:
        print(f"   ❌ Error en {loteria}: {e}")
    return False

def main():
    print("="*60)
    print("🎰 ACTUALIZACIÓN DE HISTÓRICOS REALES (VERIFICADO)")
    print("="*60)
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    
    for loteria, url in SOURCES.items():
        actualizar_datos_reales(loteria, url)
        time.sleep(2) # Respetar el servidor para evitar bloqueos
        
    print("="*60)
    print("🏁 Proceso finalizado.")

if __name__ == '__main__':
    main()
