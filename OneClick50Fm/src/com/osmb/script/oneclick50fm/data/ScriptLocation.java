package com.osmb.script.oneclick50fm.data;

import com.osmb.api.location.area.Area;

public enum ScriptLocation {
    CRAFTING_GUILD(
            "North Crafting Guild",
            AreaManager.BONFIRE_AREA_CGUILD,
            AreaManager.TREE_AREA_CGUILD,
            AreaManager.OAK_TREE_AREA_CGUILD,
            AreaManager.WILLOW_TREE_AREA_CGUILD
    ),
    LUMBRIDGE(
            "Lumbridge",
            AreaManager.BONFIRE_AREA_LUMB,
            AreaManager.TREE_AREA_LUMB,
            AreaManager.OAK_TREE_AREA_LUMB,
            AreaManager.WILLOW_TREE_AREA_LUMB
    ),
    PORT_SARIM(
            "Port Sarim",
            AreaManager.BONFIRE_AREA_PORT,
            AreaManager.TREE_AREA_PORT,
            AreaManager.OAK_TREE_AREA_PORT,
            AreaManager.WILLOW_TREE_AREA_PORT
    );

    private final String uiName;
    private final Area bonfireArea;
    private final Area normalTreeArea;
    private final Area oakTreeArea;
    private final Area willowTreeArea;

    ScriptLocation(String uiName, Area bonfireArea, Area normalTreeArea, Area oakTreeArea, Area willowTreeArea) {
        this.uiName = uiName;
        this.bonfireArea = bonfireArea;
        this.normalTreeArea = normalTreeArea;
        this.oakTreeArea = oakTreeArea;
        this.willowTreeArea = willowTreeArea;
    }

    public Area getBonfireArea() {
        return bonfireArea;
    }

    public Area getAreaForTree(Tree tree) {
        if (tree == null) return normalTreeArea;

        return switch (tree) {
            case OAK -> oakTreeArea;
            case WILLOW -> willowTreeArea;
            default -> normalTreeArea;
        };
    }

    @Override
    public String toString() {
        return uiName;
    }
}