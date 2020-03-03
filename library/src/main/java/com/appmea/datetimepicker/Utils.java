package com.appmea.datetimepicker;

import android.text.format.DateFormat;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.Locale;

import timber.log.Timber;

public class Utils {
    public static void writeStarterError(Object ob) {
        Timber.e("%s: use the static starter/new instance method with appropriate parameters to create an instance of ", ob.getClass().getName());
    }

    /**
     * Check if 2 dates are the same day, ignoring their time
     *
     * @param date1 First date
     * @param date2 Second date
     * @return True if both dates are the same day; False otherwise
     */
    public static boolean isSameDay(DateTime date1, DateTime date2) {
        if (date1 == null || date2 == null) {
            return false;
        }

        return date1.getDayOfYear() == date2.getDayOfYear() && date1.getYear() == date2.getYear();
    }


    private static final String dayOfWeekDate4 = "E MMM dd yyyy HH:mm";

    public static DateTimeFormatter getDayOfWeekMonthAbbrYearTime() {
        return DateTimeFormat.forPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), dayOfWeekDate4));
    }

    private static final String dateYearShortFormat = "MMM dd yyyy";

    public static DateTimeFormatter getDayMonthAbbrYearFormatter() {
        return DateTimeFormat.forPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), dateYearShortFormat));
    }

    public static DateTimeFormatter getMonthAbbrFormatter() {
        return DateTimeFormat.forPattern("MMM");
    }
}
