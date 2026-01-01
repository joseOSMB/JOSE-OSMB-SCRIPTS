package com.osmb.script.oneclick50fm.data;

import com.osmb.api.location.area.Area;
import com.osmb.api.location.area.impl.PolyArea;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.location.position.types.WorldPosition;

import java.util.List;

public class AreaManager {
    public static final Area WILLOW_TREE_AREA = new PolyArea(List.of(new WorldPosition(2923, 3294, 0),new WorldPosition(2914, 3295, 0),new WorldPosition(2909, 3306, 0),new WorldPosition(2915, 3306, 0),new WorldPosition(2923, 3299, 0)));
    public static final Area OAK_TREE_AREA = new RectangleArea(2927, 3298, 15, 11, 0);
    public static final Area TREE_AREA = new RectangleArea(2911, 3308, 15, 11, 0);
    public static final Area BONFIRE_AREA = new PolyArea(List.of(new WorldPosition(2944, 3309, 0),new WorldPosition(2943, 3297, 0),new WorldPosition(2931, 3297, 0),new WorldPosition(2916, 3297, 0),new WorldPosition(2913, 3302, 0),new WorldPosition(2913, 3313, 0),new WorldPosition(2926, 3319, 0)));
}