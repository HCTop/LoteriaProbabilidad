import urllib.request
import re

urls = [
    "https://www.lotoideas.com/primitiva-resultados-historicos-de-todos-los-sorteos/",
    "https://www.lotoideas.com/bonoloto-resultados-historicos-de-todos-los-sorteos/",
    "https://www.lotoideas.com/euromillones-resultados-historicos-de-todos-los-sorteos/"
]

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
}

for url in urls:
    print(f"Buscando en {url}...")
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req) as response:
            html = response.read().decode('utf-8')
            # Buscar enlaces a archivos .csv o .txt
            links = re.findall(r'href="([^"]+\.(?:csv|txt))"', html)
            for link in links:
                print(f"  Encontrado: {link}")
    except Exception as e:
        print(f"  Error: {e}")
