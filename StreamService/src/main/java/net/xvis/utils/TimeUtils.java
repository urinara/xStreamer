package net.xvis.utils;

import android.os.Build;
import java.time.Instant;

public class TimeUtils {

    private static final long maxNtpMillis     = 4294967296000L; // 2^32 millis since 1900-01-01 00:00:00 UTC
    private static final long unixEpochInNtp   = 2208988800000L; // unix epoch since 1900-01-01 00:00:00 UTC, i.e. 70 years in millis
    private static final long unixTimeAtMaxNtp = 2085978496000L; // unix time at 2^32 millis since Ntp epoch, i.e. 7-Feb-2036 UTC

    public static final String NTP_DATE_FORMAT = "EEE, MMM dd yyyy HH:mm:ss.SSS";

    public static long currentTimeMillis() {
        if (Build.VERSION.SDK_INT >= 26) {
            return Instant.now().toEpochMilli();
        } else {
            return System.currentTimeMillis();
        }
    }

    public static long toNtpTimestamp(long timeInMillis) {
        boolean base1900 = timeInMillis < unixTimeAtMaxNtp; // time < 7-Feb-2036 6h28m16s
        long baseTime = (base1900) ? timeInMillis + unixEpochInNtp : timeInMillis - unixTimeAtMaxNtp;

        long seconds = baseTime / 1000L;
        long fraction = ((baseTime % 1000) << 32) / 1000L; // smallest unit in NTP = 2^(-32) sec

        if (base1900) { // 1970 <= timeInMillis <= 2036
            seconds |= 0x80000000L; // MSB is set on Jan-20 1968 in NTP (RFC-2030)
        }

        return (seconds << 32) | fraction;
    }

    // test
    public static long toNtpSeconds(long timeInMillis) {
        boolean base1900 = timeInMillis < unixTimeAtMaxNtp; // time < 7-Feb-2036 6h28m16s
        long baseTime = (base1900) ? timeInMillis + unixEpochInNtp : timeInMillis - unixTimeAtMaxNtp;
        return baseTime / 1000L;
    }

    public static long toTimeInMillis(long ntpTimestamp) {
        long seconds = (ntpTimestamp >>> 32) & 0xFFFFFFFFL;
        long fraction = ntpTimestamp & 0xFFFFFFFFL;

        // Use round-off on fractional part to preserve going to lower precision
        fraction = Math.round(1000.0 * fraction / 0x100000000L);
        //microsecs = ((unsigned long long) frac * 1000000 + (1LL<<31)) >> 32;

        boolean base1900 = (seconds & 0x800000000L) != 0;
        if (base1900) {
            // 1970 <= unixTime < 2036
            return (seconds * 1000L) + fraction - unixEpochInNtp; // -70 years
        } else {
            // 2036 <= unixTime < 216x use base: 7-Feb-2036 @ 06:28:16 UTC
            return unixTimeAtMaxNtp + (seconds * 1000) + fraction;
        }
    }
}
