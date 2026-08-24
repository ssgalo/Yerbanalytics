FROM python:3.11-slim

WORKDIR /app

# Instalar dependencias del sistema requeridas por PyTorch y Pillow
RUN apt-get update && apt-get install -y --no-install-recommends \
    libglib2.0-0 \
    libgl1 \
    && rm -rf /var/lib/apt/lists/*

# Copiar e instalar dependencias Python primero (optimiza cache de capas)
COPY requirements.txt .
# Forzamos la instalación de la versión ligera de CPU para PyTorch
RUN pip install --no-cache-dir torch torchvision --index-url https://download.pytorch.org/whl/cpu && \
    pip install --no-cache-dir -r requirements.txt

# Copiar código fuente
COPY src/ ./src/

WORKDIR /app/src

CMD ["python", "main.py"]
