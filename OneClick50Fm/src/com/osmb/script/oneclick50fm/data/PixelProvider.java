package com.osmb.script.oneclick50fm.data;

import com.osmb.api.visual.SearchablePixel;
import com.osmb.api.visual.color.ColorModel;
import com.osmb.api.visual.color.tolerance.impl.SingleThresholdComparator;

public class PixelProvider {

//pixel clusters for normal trees,oak trees and willow trees.
    static final SearchablePixel[] TREE_CLUSTER_1 = new SearchablePixel[] {
            new SearchablePixel(-14012413, new SingleThresholdComparator(2), ColorModel.HSL),
            new SearchablePixel(-12958706, new SingleThresholdComparator(2), ColorModel.HSL),
            new SearchablePixel(-11316685, new SingleThresholdComparator(2), ColorModel.HSL),
            new SearchablePixel(-13222121, new SingleThresholdComparator(2), ColorModel.HSL),
            new SearchablePixel(-11312366, new SingleThresholdComparator(2), ColorModel.HSL),
            new SearchablePixel(-13089777, new SingleThresholdComparator(2), ColorModel.HSL),
            new SearchablePixel(-12103646, new SingleThresholdComparator(2), ColorModel.HSL),
            new SearchablePixel(-15329787, new SingleThresholdComparator(2), ColorModel.HSL),
            new SearchablePixel(-15131126, new SingleThresholdComparator(2), ColorModel.HSL),
            new SearchablePixel(-10921390, new SingleThresholdComparator(2), ColorModel.RGB),
            new SearchablePixel(-10458320, new SingleThresholdComparator(2), ColorModel.RGB),
            new SearchablePixel(-10326735, new SingleThresholdComparator(2), ColorModel.RGB),
            new SearchablePixel(-13946858, new SingleThresholdComparator(2), ColorModel.RGB),
            new SearchablePixel(-15131639, new SingleThresholdComparator(2), ColorModel.RGB),
            new SearchablePixel(-14538225, new SingleThresholdComparator(2), ColorModel.RGB),
            new SearchablePixel(-12366817, new SingleThresholdComparator(2), ColorModel.RGB),
            new SearchablePixel(-13287914, new SingleThresholdComparator(2), ColorModel.RGB),
            new SearchablePixel(-9543614, new SingleThresholdComparator(2), ColorModel.RGB),
    };

}
