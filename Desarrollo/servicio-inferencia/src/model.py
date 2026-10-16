"""
model.py — Carga del detector YOLO y traducción de sus cajas a detecciones de la plataforma.

Responsabilidades:
  - Cargar el checkpoint (YOLOv8 de detección) una única vez y mantenerlo en memoria.
  - Verificar al arrancar que MODEL_CLASSES tenga tantas clases como el checkpoint, y dejar en el
    log el mapeo índice → nombre del checkpoint → estado de la plataforma.
  - `detect(image_path)`: corre YOLO con el umbral global y devuelve TODAS las detecciones
    (estado, score y caja normalizada). Elegir el diagnóstico NO es tarea de este módulo: eso lo
    hace diagnosis.py.
"""

from __future__ import annotations

import logging
from pathlib import Path

from ultralytics import YOLO

from diagnosis import Detection

logger = logging.getLogger(__name__)

# Decimales con que viajan score y coordenadas (contrato D12).
_DECIMALS = 4


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

    def detect(self, image_path: str) -> list[Detection]:
        """
        Corre el detector sobre una imagen y devuelve sus detecciones con score ≥ YOLO_CONF_GLOBAL.

        Raises:
            FileNotFoundError: si el archivo no existe en disco.
            ValueError: si el archivo existe pero no se puede leer como imagen.
        """
        path = Path(image_path)
        if not path.is_file():
            raise FileNotFoundError(f"Imagen no encontrada en disco: {image_path}")

        results = self._model(str(path), conf=self._conf_global, verbose=False)
        if not results:
            # Ultralytics devuelve una lista vacía (con un WARNING propio) si no puede leer el archivo.
            raise ValueError(f"No se pudo leer la imagen (¿JPEG truncado o vacío?): {image_path}")
        boxes = results[0].boxes
        if boxes is None or len(boxes) == 0:
            return []

        detections: list[Detection] = []
        classes = boxes.cls.tolist()
        scores = boxes.conf.tolist()
        coords = boxes.xyxyn.tolist()
        for cls, score, xyxyn in zip(classes, scores, coords):
            x1, y1, x2, y2 = (round(min(max(v, 0.0), 1.0), _DECIMALS) for v in xyxyn)
            if not (x1 < x2 and y1 < y2):
                # Una caja de menos de una diezmilésima de la imagen no es una hoja, y el backend
                # rechazaría el alta entera por una caja degenerada.
                logger.warning("Se descarta una caja degenerada tras el redondeo: %s", xyxyn)
                continue
            detections.append(
                Detection(
                    label=self._classes[int(cls)],
                    score=round(float(score), _DECIMALS),
                    box=(x1, y1, x2, y2),
                )
            )
        return detections
