"""
model.py — Carga del detector YOLO y traducción de sus cajas a detecciones de la plataforma.

Responsabilidades:
  - Cargar el checkpoint (YOLOv8 de detección) una única vez y mantenerlo en memoria.
  - Verificar al arrancar que MODEL_CLASSES tenga tantas clases como el checkpoint, y dejar en el
    log el mapeo índice → nombre del checkpoint → estado de la plataforma.
  - `detect(image)`: corre YOLO con el umbral global sobre una imagen en memoria (un tile) y
    devuelve TODAS las detecciones (estado, score y caja en píxeles de esa imagen). Unir los tiles
    es tarea de tiling.py, y elegir el diagnóstico, de diagnosis.py.
"""

from __future__ import annotations

import logging
from pathlib import Path

from PIL import Image
from ultralytics import YOLO

from tiling import PixelDetection

logger = logging.getLogger(__name__)


class InferenceModel:
    """Detector YOLO cargado en memoria. Instanciar una única vez al arranque del daemon."""

    def __init__(self, model_path: Path, classes: list[str], conf_global: float) -> None:
        if not model_path.is_file():
            raise FileNotFoundError(f"No se encontró el archivo del modelo en: {model_path}")

        logger.info("Cargando modelo YOLO desde %s …", model_path)
        self._model = YOLO(str(model_path))
        self._conf_global = conf_global

        names: dict[int, str] = dict(self._model.names)
        if len(classes) != len(names):
            raise ValueError(
                f"MODEL_CLASSES tiene {len(classes)} clases y el checkpoint {len(names)} "
                f"({names}). Ajustá MODEL_CLASSES al orden de model.names."
            )
        self._classes = list(classes)

        logger.info("Mapeo de clases (índice → checkpoint → estado de la plataforma):")
        for index in sorted(names):
            logger.info("  %d → %s → %s", index, names[index], self._classes[index])

    @property
    def classes(self) -> list[str]:
        return list(self._classes)

    def detect(self, image: Image.Image) -> list[PixelDetection]:
        """
        Corre el detector sobre una imagen en memoria (un tile, ya en RGB) y devuelve sus
        detecciones con score ≥ YOLO_CONF_GLOBAL, con cajas xyxy en píxeles de esa imagen.

        Las cajas no se redondean acá: la unión entre tiles decide en los bordes, y normalizar es
        el último paso (tiling.normalize).
        """
        results = self._model(image, conf=self._conf_global, verbose=False)
        if not results:
            return []
        boxes = results[0].boxes
        if boxes is None or len(boxes) == 0:
            return []

        width, height = image.size
        detections: list[PixelDetection] = []
        for cls, score, xyxy in zip(boxes.cls.tolist(), boxes.conf.tolist(), boxes.xyxy.tolist()):
            x1, y1, x2, y2 = (
                min(max(v, 0.0), size) for v, size in zip(xyxy, (width, height, width, height))
            )
            detections.append(
                PixelDetection(label=self._classes[int(cls)], score=float(score), box=(x1, y1, x2, y2))
            )
        return detections
