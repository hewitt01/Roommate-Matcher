package com.roommatch.agent;

import com.roommatch.model.CompatibilityResult;
import com.roommatch.model.FrictionMap;

/** A friction map plus the result it was built from (which may have been re-scored just now). */
public record FrictionMapView(CompatibilityResult result, FrictionMap map, boolean rescored) { }
