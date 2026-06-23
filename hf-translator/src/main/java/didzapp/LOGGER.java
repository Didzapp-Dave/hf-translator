package didzapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LOGGER {
	static final Logger LOGGER = LoggerFactory.getLogger(LOGGER.class);
	public static boolean debug = false;
	public static boolean testing = false;
	public static boolean showCritical = false;
	public static boolean showIgnored = false;

	public static void log(final String message) {
		if (LOGGER != null) {
			if (debug) {
				if (!testing) {
					doLog(message, null);
				} else {
					doSyncroLog(message, null);
				}
			}
		} else {
			System.out.println(message);
		}
	}

	public static void log(final String message, final Throwable throwable) {
		if (LOGGER != null) {
			if (debug || showCritical) {
				if (!testing) {
					doLog(message, throwable);
				} else {
					doSyncroLog(message, throwable);
				}
			}
		} else {
			System.out.println(message + " : " + throwable.getMessage()); //$NON-NLS-1$
		}
	}

	public static void log(final String message, final Throwable throwable, final boolean ignored) {
		if (LOGGER != null) {
			if (debug || showCritical) {
				if (showIgnored || !ignored) {
					if (!testing) {
						doLog(message, throwable);
					} else {
						doSyncroLog(message, throwable);
					}
				}
			}
		} else {
			System.out.println(message + " : " + throwable.getMessage()); //$NON-NLS-1$
		}
	}

	private static void doLog(final String message, final Throwable throwable) {
		LOGGER.info(message, throwable);
	}

	private static synchronized void doSyncroLog(final String message, final Throwable throwable) {
		LOGGER.info(message, throwable);
	}
}
