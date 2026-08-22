"""
model.py — Carga del modelo .pt y pipeline de inferencia.

Responsabilidades:
  - Leer MODEL_PATH y MODEL_CLASSES desde el entorno en la inicialización.
  - Cargar el modelo YOLO (Ultralytics) una única vez y mantenerlo en memoria.
  - Exponer predict(image_path) que devuelve (estado, confianza_pct, severidad).
"""

from __future__ import annotations

import logging
import os
from pathlib import Path

from ultralytics import YOLO

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Estados válidos de la plataforma (subset que el backend acepta).
# Se valida en tiempo de arranque contra MODEL_CLASSES para detectar errores
# de configuración antes del primer ciclo.
# ---------------------------------------------------------------------------
ESTADOS_VALIDOS = frozenset({
    "Sano", "Clorosis", "Estrés solar", "Daño biótico",
    "No concluyente", "Ácaro", "Plaga foliar", "Daño fúngico",
})

# Umbral de confianza (%) a partir del cual la severidad es "Alta".
# Puede externalizarse si se necesita ajuste fino, pero es un parámetro
# de negocio estable que no varía por modelo.
_UMBRAL_SEVERIDAD_ALTA = 80.0
_UMBRAL_SEVERIDAD_MEDIA = 50.0


def _parse_classes(raw: str) -> list[str]:
    """Parsea MODEL_CLASSES desde una cadena separada por comas."""
    clases = [c.strip() for c in raw.split(",") if c.strip()]
    if not clases:
        raise ValueError("MODEL_CLASSES está vacía o mal formateada.")
    invalidas = [c for c in clases if c not in ESTADOS_VALIDOS]
    if invalidas:
        raise ValueError(
            f"MODEL_CLASSES contiene estados fuera de la taxonomía del backend: {invalidas}. "
            f"Los válidos son: {sorted(ESTADOS_VALIDOS)}"
        )
    return clases


def _map_severidad(confianza_pct: float) -> str:
    """Mapea el confidence score a la severidad de la plataforma."""
    if confianza_pct >= _UMBRAL_SEVERIDAD_ALTA:
        return "Alta"
    if confianza_pct >= _UMBRAL_SEVERIDAD_MEDIA:
        return "Media"
    return "Baja"


class InferenceModel:
    """
    Singleton del modelo YOLO cargado en memoria.
    Instanciar una única vez al arranque del daemon.
    """

    def __init__(self) -> None:
        model_path = os.environ.get("MODEL_PATH", "").strip()
        if not model_path:
            raise EnvironmentError(
                "MODEL_PATH no está configurada. "
                "Completá el valor en el archivo .env antes de arrancar."
            )
        if not Path(model_path).is_file():
            raise FileNotFoundError(
                f"No se encontró el archivo del modelo en: {model_path}"
            )

        raw_classes = os.environ.get("MODEL_CLASSES", "").strip()
        if not raw_classes:
            raise EnvironmentError(
                "MODEL_CLASSES no está configurada. "
                "Definí el orden de clases del modelo en el archivo .env."
            )
        self._classes = _parse_classes(raw_classes)

        logger.info("Cargando modelo YOLO desde %s …", model_path)
        self._model = YOLO(model_path)
        
        # Opcional: mostrar las clases que traía el modelo original vs las nuestras
        logger.info(
            "Modelo listo. Clases configuradas (%d): %s",
            len(self._classes), self._classes,
        )

    def predict(self, image_path: str) -> tuple[str, float, str]:
        """
        Ejecuta la inferencia sobre una imagen JPEG local usando YOLO.

        Args:
            image_path: Ruta absoluta al archivo JPEG.

        Returns:
            Tupla (estado, confianza_pct, severidad) donde:
              - estado: string de la taxonomía del backend.
              - confianza_pct: float entre 0.0 y 100.0.
              - severidad: "Alta", "Media" o "Baja".

        Raises:
            FileNotFoundError: si el archivo no existe en disco.
        """
        path = Path(image_path)
        if not path.is_file():
            raise FileNotFoundError(f"Imagen no encontrada en disco: {image_path}")

        # Ejecuta la predicción (YOLO se encarga internamente del preprocesamiento y dispositivo)
        results = self._model(str(path), verbose=False)
        result = results[0]

        # Validación por si cargan un modelo de detección de objetos en vez de clasificación
        if not hasattr(result, 'probs') or result.probs is None:
            logger.error(
                "El modelo no devolvió probabilidades (probs). "
                "Esto suele pasar si se subió un modelo de detección de objetos (YOLO-Det) "
                "en lugar de uno de clasificación de imágenes (YOLO-Cls)."
            )
            return "No concluyente", 0.0, "Baja"

        # YOLO classification logic
        idx = int(result.probs.top1)
        confianza_pct = round(float(result.probs.top1conf) * 100, 2)

        if idx >= len(self._classes):
            logger.error(
                "El modelo YOLO devolvió el índice %d pero solo hay %d clases configuradas en MODEL_CLASSES.",
                idx, len(self._classes),
            )
            estado = "No concluyente"
            confianza_pct = 0.0
        else:
            estado = self._classes[idx]

        severidad = _map_severidad(confianza_pct)
        return estado, confianza_pct, severidad
