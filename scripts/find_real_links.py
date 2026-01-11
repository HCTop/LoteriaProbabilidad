import urllib.request
import re

urls = [
    "https://www.lotoideas.com/primitiva-resultados-historicos-de-todos-los-sorteos/",
    "https://www.lotoideas.com/bonoloto-resultados-historicos-de-todos-los-sorteos/",
    "https://www.lotoideas.com/euromillones-resultados-historicos-de-todos-los-sorteos/",
    "https://www.lotoideas.com/el-gordo-de-la-primitiva-resultados-historicos-de-todos-los-sorteos/",
    "https://www.lotoideas.com/loteria-nacional-resultados-historicos-de-todos-los-sorteos/",
    "https://www.lotoideas.com/loteria-de-navidad-resultados-historicos-de-todos-los-sorteos/",
    "https://www.lotoideas.com/loteria-del-nino-resultados-historicos-de-todos-los-sorteos/"
]

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
}

for url in urls:
    print(f"\n--- Analizando {url} ---")
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req) as response:
            html = response.read().decode('utf-8')
            # Buscar cualquier cosa que termine en .txt, .csv, .xls, .xlsx o que contenga "descargar" o "txt"
            links = re.findall(r'href="([^"]+)"', html)
            for link in links:
                if any(ext in link.lower() for ext in ['.txt', '.csv', '.xls']) or 'descargar' in link.lower():
                    print(f"  Posible enlace: {link}")
    except Exception as e:
        print(f"  Error en {url}: {e}")
