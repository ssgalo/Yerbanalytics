package com.yerbanalytics.backend.engine.traza;

import com.yerbanalytics.backend.engine.ActionType;

/** Acción que emitió una regla, tal como quedó en la evaluación. */
public record AccionTrazada(ActionType tipo, String motivo) {}
