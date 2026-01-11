import urllib.request
import re

url = "https://www.eduardolosilla.es/loterias/loteria-nacional/numeros-premiados"
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
}

try:
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req) as response:
        html = response.read().decode('utf-8')
        print("Buscando enlaces en Eduardo Losilla...")
        links = re.findall(r'href="([^"]+)"', html)
        for link in links:
            if any(ext in link.lower() for ext in ['.csv', '.txt', '.xls', 'descargar', 'historico']):
                print(f"  Posible enlace: {link}")
except Exception as e:
    print(f"Error: {e}")
