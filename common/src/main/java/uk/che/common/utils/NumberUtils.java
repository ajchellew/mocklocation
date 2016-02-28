package uk.che.common.utils;

import java.text.DecimalFormat;

public class NumberUtils {

    public static String toFiveDecimalPlaces(double inValue) {
        DecimalFormat fiveDec = new DecimalFormat("0.00000");
        fiveDec.setGroupingUsed(false);
        return fiveDec.format(inValue);
    }

}
