from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
required = [
    "pom.xml",
    "database/schema.sql",
    "src/main/java/br/com/launchcontrol/Database.java",
    "src/main/java/br/com/launchcontrol/HealthServlet.java",
    "src/main/java/br/com/launchcontrol/ServiceServlet.java",
    "src/main/webapp/index.html",
    "src/main/webapp/css/style.css",
    "src/main/webapp/js/app.js",
]
missing = [path for path in required if not (ROOT / path).is_file()]
if missing:
    print("Arquivos ausentes:")
    print("\n".join(missing))
    sys.exit(1)

pom = (ROOT / "pom.xml").read_text(encoding="utf-8")
page = (ROOT / "src/main/webapp/index.html").read_text(encoding="utf-8")
servlet = (ROOT / "src/main/java/br/com/launchcontrol/ServiceServlet.java").read_text(encoding="utf-8")
assert "jakarta.servlet-api" in pom
assert "mysql-connector-j" in pom
assert "text/html" not in servlet
assert "service-search" in page
assert "React" not in page and "vite" not in page.lower() and "typescript" not in page.lower()
print(f"Verificação estrutural concluída: {len(required)} arquivos essenciais presentes.")
