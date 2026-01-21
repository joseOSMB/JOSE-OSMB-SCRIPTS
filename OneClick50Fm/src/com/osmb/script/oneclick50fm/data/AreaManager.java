package com.osmb.script.oneclick50fm.data;

import com.osmb.api.location.area.Area;
import com.osmb.api.location.area.impl.PolyArea;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.location.position.types.WorldPosition;

import java.util.List;

public class AreaManager {
    public static final Area WILLOW_TREE_AREA_CGUILD = new PolyArea(List.of(new WorldPosition(2923, 3294, 0), new WorldPosition(2914, 3295, 0), new WorldPosition(2909, 3306, 0), new WorldPosition(2915, 3306, 0), new WorldPosition(2923, 3299, 0)));
    public static final Area OAK_TREE_AREA_CGUILD = new RectangleArea(2927, 3298, 15, 11, 0);
    public static final Area TREE_AREA_CGUILD = new RectangleArea(2911, 3308, 15, 11, 0);
    public static final Area BONFIRE_AREA_CGUILD = new PolyArea(List.of(new WorldPosition(2944, 3309, 0), new WorldPosition(2943, 3297, 0), new WorldPosition(2931, 3297, 0), new WorldPosition(2916, 3297, 0), new WorldPosition(2913, 3302, 0), new WorldPosition(2913, 3313, 0), new WorldPosition(2926, 3319, 0)));
    public static final Area WILLOW_TREE_AREA_LUMB = new PolyArea(List.of(new WorldPosition(3234, 3235, 0), new WorldPosition(3234, 3243, 0), new WorldPosition(3233, 3244, 0), new WorldPosition(3232, 3247, 0), new WorldPosition(3236, 3247, 0), new WorldPosition(3237, 3235, 0)));
    public static final Area OAK_TREE_AREA_LUMB = new RectangleArea(3203, 3239, 4, 12, 0);
    public static final Area TREE_AREA_LUMB = new PolyArea(List.of(new WorldPosition(3228, 3231, 0), new WorldPosition(3224, 3231, 0), new WorldPosition(3221, 3236, 0), new WorldPosition(3210, 3238, 0), new WorldPosition(3210, 3241, 0), new WorldPosition(3221, 3247, 0), new WorldPosition(3232, 3247, 0), new WorldPosition(3232, 3244, 0), new WorldPosition(3225, 3243, 0), new WorldPosition(3225, 3237, 0)));
    public static final Area BONFIRE_AREA_LUMB = new PolyArea(List.of(new WorldPosition(3201, 3250, 0),new WorldPosition(3201, 3237, 0),new WorldPosition(3240, 3234, 0),new WorldPosition(3235, 3247, 0),new WorldPosition(3223, 3247, 0),new WorldPosition(3235, 3243, 0),new WorldPosition(3235, 3235, 0),new WorldPosition(3224, 3235, 0),new WorldPosition(3224, 3247, 0),new WorldPosition(3210, 3239, 0),new WorldPosition(3203, 3250, 0)));
    public static final Area WILLOW_TREE_AREA_PORT = new RectangleArea(3055, 3250, 9, 6, 0);
    public static final Area OAK_TREE_AREA_PORT = new RectangleArea(3055, 3260, 3, 11, 0);
    public static final Area TREE_AREA_PORT = new PolyArea(List.of(new WorldPosition(3045, 3260, 0),new WorldPosition(3045, 3272, 0),new WorldPosition(3051, 3272, 0),new WorldPosition(3053, 3269, 0),new WorldPosition(3054, 3261, 0)));
    public static final Area BONFIRE_AREA_PORT = new PolyArea(List.of(new WorldPosition(3056, 3250, 0),new WorldPosition(3064, 3251, 0),new WorldPosition(3064, 3264, 0),new WorldPosition(3056, 3270, 0),new WorldPosition(3047, 3270, 0),new WorldPosition(3047, 3260, 0),new WorldPosition(3056, 3260, 0)));
}