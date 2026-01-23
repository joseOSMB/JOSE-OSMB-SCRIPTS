package com.osmb.script.valetotemspro.data;

import com.osmb.api.location.area.Area;
import com.osmb.api.location.area.impl.PolyArea;
import com.osmb.api.location.area.impl.RectangleArea;
import com.osmb.api.location.position.types.WorldPosition;

import java.util.List;

public class AreaManager {
    public static final Area NEMUS_BANK = new PolyArea(List.of(new WorldPosition(1383, 3303, 0),new WorldPosition(1388, 3303, 0),new WorldPosition(1389, 3304, 0),new WorldPosition(1389, 3306, 0),new WorldPosition(1390, 3306, 0),new WorldPosition(1390, 3313, 0),new WorldPosition(1383, 3313, 0)));;
    public static final Area TOTEM_05 = new RectangleArea(1383, 3271, 7, 10, 0);
    public static final Area TOTEM_06 = new RectangleArea(1344, 3314, 10, 9, 0);
    public static final Area TOTEM_07 = new RectangleArea(1363, 3369, 11, 9, 0);
    public static final Area TOTEM_08 = new RectangleArea(1394, 3326, 10, 7, 0);
    public static final Area CLIMB_AREA = new RectangleArea(1388, 3320, 3, 3, 0);
    public static final Area WALL_ZONE_OAK = new RectangleArea(1391, 3307, 2, 8, 0);
    public static final Area TOTEM_04 = new RectangleArea(1409, 3284, 5, 9, 0);
    public static final Area WALK_ACROSS_TOTE04SIDE = new RectangleArea(1398, 3281, 5, 2, 0);
    public static final Area TOTEM_03 = new RectangleArea(1434, 3302, 8, 9, 0);
    public static final Area TOTEM_02 = new RectangleArea(1475, 3325, 8, 9, 0);
    public static final Area WALK_ACROSS_TOTE01SIDE = new RectangleArea(1450, 3327, 6, 2, 0);
    public static final Area TOTEM_01 = new RectangleArea(1445, 3340, 11, 5, 0);
    public static final Area AUBURNVALE_BANK = new RectangleArea(1408, 3346, 17, 13, 0);
    public static final Area WALL_ZONE_MAIN_ROUTE = new RectangleArea(1385, 3293, 4, 8, 0);

}