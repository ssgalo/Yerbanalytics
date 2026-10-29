import sys
from pathlib import Path

# Los módulos del puente viven en la carpeta padre (sin paquete).
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
