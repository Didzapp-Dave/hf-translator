package didzapp.HF_Translator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Currency;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.icu.util.ULocale;

import didzapp.LOGGER;
import didzapp.HF_Translator.TranslatorContent.FolderName;
import didzapp.HF_Translator.TranslatorContent.TestContent;
import didzapp.HF_Translator.TranslatorContent.Translatable;
import didzapp.HF_Translator.TranslatorEntity.HibernateUtil;
import didzapp.HF_Translator.TranslatorResourcePaths.ToFlatpickr;
import didzapp.HF_Translator.TranslatorResourcePaths.ToPyFiles;

public class Translator {
	private static boolean devTesting_DoFullClassfileTest = false;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Private
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Values
	// Python file locations and hash values
	private static File scriptFileGenerate;
	private static String hashFileGenerate;
	private static File scriptFileTranslate;
	private static String hashFileTranslate;
	private static File scriptFileDetectLanguage;
	private static String hashFileDetectLanguage;
	//
	// Helsinki-NLP/opus-mt-: char limit approx.
	private static final int modelCharLimit = 5000;
	// Helsinki-NLP/opus-mt-: model file names
	private static final String[] MODEL_FILES_TO_DOWNLOAD = { "config.json", "vocab.json", "tokenizer_config.json", "generation_config.json", "metadata.json", "pytorch_model.bin", "source.spm", "target.spm", "tf_model.h5", "flax_model.msgpack", "README.md", ".gitattributes" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$
	//
	// Language detector background service
	private static final Object processLock = new Object();
	private static boolean alwaysRunDetector = true;
	private static Process persistentProcess = null;
	private static BufferedWriter persistentWriter = null;
	private static BufferedReader persistentReader = null;
	private static Thread thread ;
	//
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Package
	// Values
	// Path and platform (id if duplicate file paths exist)
	static String modelPath = null;
	static String sharedPathString = null;
	static String mainPathString = null;
	static String tempPathString = "/temp_files"; //$NON-NLS-1$
	static String siteOrAppId = "hf-translator"; //$NON-NLS-1$
	static Platform platformRuningOn = null;
	//
	// Config, maintenance and global settings
	static boolean runningMaintenance = false;
	static boolean doModels = true;
	static boolean doFlatpickerFiles = true;
	static boolean redoTranslationsInTable = false;
	static boolean universalTranslations = false;
	//
	// External resource, hibernate config file path, "/libhibernate.cfg.xml"
	static String libhiberbernate;
	//
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Package
	// Values
	// Folder for model storage
	static FolderName languageModelFolder;
	// Default language
	static Language defaultLanguage;
	//
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Public
	// Values

	/**
	 * Creates a new timestamp as String or Timestamp.
	 *
	 */
	public static class quickTimestamp {
		public static Timestamp timestamp() {
			return new Timestamp(
					System.currentTimeMillis());
		}

		public static String string() {
			return new Timestamp(
					System.currentTimeMillis()).toString();
		}

		public static String string(final boolean addSubtractOneDay) {
			LocalDateTime now = new Timestamp(
					System.currentTimeMillis()).toLocalDateTime();
			if (addSubtractOneDay) {
				now = now.plusDays(1);
			} else {
				now = now.minusDays(1);
			}
			return Timestamp.valueOf(now).toString();
		}
	}

	/**
	 * Provides platform options.
	 *
	 */
	public static enum Platform {
		WINDOWS("windows"), LINUX("linux"); //$NON-NLS-1$ //$NON-NLS-2$

		protected final String value;

		Platform(final String value) {
			this.value = value;
		}

		/**
		 * Returns value .
		 *
		 * @return the internal value as a String
		 */
		public String getValue() {
			return this.value;
		}
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Languages
	public static enum Language {
		ENGLISH("english", new Locale.Builder().setLanguage("en").setRegion("GB").build(), ZoneId.of("Europe/London"), false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		USA("us", new Locale.Builder().setLanguage("en").setRegion("US").build(), ZoneId.of("America/New_York"), false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		CANADA("canada", new Locale.Builder().setLanguage("en").setRegion("CA").build(), ZoneId.of("America/Toronto"), false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		// European
		SPANISH("spanish", new Locale.Builder().setLanguage("es").setRegion("ES").build(), ZoneId.of("Europe/Madrid"), false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		FRENCH("french", new Locale.Builder().setLanguage("fr").setRegion("FR").build(), ZoneId.of("Europe/Paris"), false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		GERMAN("german/dutch", new Locale.Builder().setLanguage("de").setRegion("DE").build(), ZoneId.of("Europe/Berlin"), false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		ITALIAN("italian", new Locale.Builder().setLanguage("it").setRegion("IT").build(), ZoneId.of("Europe/Rome"), false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		NEDERLANDS("nederlands/dutch", new Locale.Builder().setLanguage("nl").setRegion("NL").build(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				ZoneId.of("Europe/Amsterdam"), false), //$NON-NLS-1$
		SWEDISH("swedish", new Locale.Builder().setLanguage("sv").setRegion("SE").build(), ZoneId.of("Europe/Stockholm"), false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		DANISH("danish", new Locale.Builder().setLanguage("da").setRegion("DK").build(), ZoneId.of("Europe/Copenhagen"), false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		FINNISH("finnish", new Locale.Builder().setLanguage("fi").setRegion("FI").build(), ZoneId.of("Europe/Helsinki"), false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		CZECH("czech", new Locale.Builder().setLanguage("cs").setRegion("CZ").build(), ZoneId.of("Europe/Prague"), false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		BULGARIAN("bulgarian", new Locale.Builder().setLanguage("bg").setRegion("BG").build(), ZoneId.of("Europe/Sofia"), false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		HUNGARIAN("hungarian", new Locale.Builder().setLanguage("hu").setRegion("HU").build(), ZoneId.of("Europe/Budapest"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				false),
		ICELANDIC("icelandic", new Locale.Builder().setLanguage("is").setRegion("IS").build(), ZoneId.of("Atlantic/Reykjavik"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				false),
		ESTONIAN("estonian", new Locale.Builder().setLanguage("et").setRegion("EE").build(), ZoneId.of("Europe/Tallinn"), false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		// Slavic & Cyrillic
		RUSSIAN("russian", new Locale.Builder().setLanguage("ru").setRegion("RU").build(), ZoneId.of("Europe/Moscow"), false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		UKRAINIAN("ukrainian", new Locale.Builder().setLanguage("uk").setRegion("UA").build(), ZoneId.of("Europe/Kyiv"), false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		// Asian
		JAPANESE("japanese", new Locale.Builder().setLanguage("ja").setRegion("JP").build(), ZoneId.of("Asia/Tokyo"), false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		CHINESE_SIMPLIFIED("chinese", new Locale.Builder().setLanguage("zh").setRegion("CN").build(), ZoneId.of("Asia/Shanghai"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				false),
		HINDI("hindi", new Locale.Builder().setLanguage("hi").setRegion("IN").build(), ZoneId.of("Asia/Kolkata"), false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		INDONESIAN("indonesian", new Locale.Builder().setLanguage("id").setRegion("ID").build(), ZoneId.of("Asia/Jakarta"), //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$ //$NON-NLS-4$
				false),
		MALAYALAM("malayalam", new Locale.Builder().setLanguage("ml").setRegion("IN").build(), ZoneId.of("Asia/Kolkata"), false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		// Middle Eastern (RTL)
		ARABIC("arabic", new Locale.Builder().setLanguage("ar").setRegion("SA").build(), ZoneId.of("Asia/Riyadh"), true), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		// Central Asian / Middle Eastern (Non-RTL)
		URDU("urdu", new Locale.Builder().setLanguage("ur").setRegion("PK").build(), ZoneId.of("Asia/Karachi"), true), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		// African
		HAUSA("hausa", new Locale.Builder().setLanguage("ha").setRegion("NG").build(), ZoneId.of("Africa/Lagos"), false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		IGBO("igbo", new Locale.Builder().setLanguage("ig").setRegion("NG").build(), ZoneId.of("Africa/Lagos"), false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		XHOSA("xhosa", new Locale.Builder().setLanguage("xh").setRegion("ZA").build(), ZoneId.of("Africa/Johannesburg"), false) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		//
		; // here so it doesnt get in the way adding languages
			//

		/**
		 * String value for Language
		 */
		private final String value;
		/**
		 * Locale vlaue for Language
		 */
		private final Locale locale;
		/**
		 * ZoneId vlaue for Language
		 */
		private final ZoneId zoneId;
		/**
		 * Reads from right to left vlaue for Language (left to right = false / right to
		 * left = true).
		 */
		private final boolean rtl;
		/**
		 * If usable vlaue for Language is False removes the language from use in every
		 * context at runtime Its set automatically if corresponding language model
		 * doesn't exist / is usable.
		 */
		private boolean usable = true;
		/**
		 * If selected vlaue for Language is False removes the language from use in
		 * every context at runtime Its set specifically to determine if this language
		 * is selected for use at runtime.
		 */
		private boolean selected = true;
		/**
		 * List containing String values for Languages usable with this Language
		 * (languages that this language can be directly translated to because the
		 * language > language model exists).
		 */
		private final List<String> compatLanguages = new ArrayList<>();

		/**
		 * Constructs a new Language instance with the specified display value, locale,
		 * time zone, and text direction.
		 *
		 * @param value  the string for this Language (e.g., "English", "French")
		 * @param locale the Java Locale object
		 * @param zoneId the time zone id (e.g., ZoneId.of("Europe/Paris"))
		 * @param rtl    true if the language uses right‑to‑left script (e.g., Arabic,
		 *               Hebrew); false for left‑to‑right languages
		 */
		Language(final String value, final Locale locale, final ZoneId zoneId, final boolean rtl) {
			this.value = value;
			this.locale = locale;
			this.zoneId = zoneId;
			this.rtl = rtl;
		}

		/**
		 * Returns value .
		 *
		 * @return the internal value as a String
		 */
		public String getValue() {
			return this.value;
		}

		/**
		 * Returns the locale.
		 *
		 * @return the Locale
		 */
		public Locale getLocale() {
			return this.locale;
		}

		/**
		 * Returns the time zone id (e.g., "Europe/London")
		 *
		 * @return the ZoneId
		 */
		public ZoneId getZoneId() {
			return this.zoneId;
		}

		/**
		 * Returns whether right‑to‑left (RTL) layout is enabled.
		 *
		 * @return true if RTL mode is active, false otherwise
		 */
		public boolean getRTL() {
			return this.rtl;
		}

		/**
		 * Checks whether this Language is in a usable state for translation.
		 *
		 * @return true if usable, false otherwise
		 */
		public boolean isUsable() {
			return this.usable;
		}

		/**
		 * Checks whether this selected for use when translating
		 *
		 * @return true if selected, false otherwise
		 */
		public boolean isSelected() {
			return this.selected;
		}

		/**
		 * Gets list of languages available for direct translation.
		 *
		 * @return List<String> of available languages as strings, use
		 *         {@link #fromString()} to convert when needed
		 */
		public List<String> getAvailableLanguages() {
			return this.compatLanguages;
		}

		/**
		 * Gets list of languages available for direct translation.
		 *
		 * 
		 */
		public void addToAvailableLanguages(Language language) {
			this.compatLanguages.add(language.toString());
		}

		/**
		 * Gets a boolean, true if the language is available. false otherwise.
		 *
		 * @return true if Language is available.
		 * 
		 */
		public boolean hasAvailableLanguage(Language language) {
			if (this.compatLanguages.contains(language.toString())) {
				return true;
			}
			return false;
		}

		/**
		 * Returns all languages that can be translated to the given language.
		 *
		 * @param language the target language (the one being translated into)
		 * @return a list of languages that support translation to the specified
		 *         language
		 */
		public static List<Language> getLanguagesThatTranslateTo(Language language) {
			List<Language> result = new ArrayList<>();
			for (Language lang : Language.usableValues()) {
				if (lang.hasAvailableLanguage(language)) {
					result.add(lang);
				}
			}
			return result;
		}

		/**
		 * Returns all languages that can be translated from the given language.
		 *
		 * @param language the source language (the one being translated from)
		 * @return a list of languages that the source language supports translation to
		 */
		public static List<Language> getLanguagesThatTranslateFrom(Language language) {
			List<Language> result = new ArrayList<>();
			for (String lang : language.getAvailableLanguages()) {
				result.add(Language.fromString(lang));
			}
			return result;
		}

		/**
		 * Returns any language using the locale code given.
		 *
		 * @param localeCode the source language code (ex.,
		 *                   Language.ENGLISH.getLocale().getLanguage() ; "en")
		 * @return a Language matching the locale code
		 */
		public static Language fromLocaleCode(String localeCode) {
			for (Language lang : Language.usableValues()) {
				if (lang.getLocale().getLanguage().equals(localeCode)) {
					return lang;
				}
			}
			return null;
		}

		/**
		 * Returns a string representation of this value (getName() is never used only
		 * getValue())
		 *
		 * @return the same as {@link #getValue()}
		 */
		@Override
		public String toString() {
			return this.value;
		}

		/**
		 * Obtains the currency symbol (e.g., "£", "$", "€", "¥") for the current
		 * locale. Uses the locale returned by {@link #getLocale()} and the system's
		 * default currency.
		 *
		 * @return the currency symbol as a String (e.g., "GBP" becomes "£" for Engligh
		 *         locale)
		 * @throws java.util.MissingResourceException if no currency is defined for the
		 *                                            locale
		 */
		public String getCurrencySymbol() {
			final Currency currency = Currency.getInstance(this.getLocale());
			return currency.getSymbol();
		}

		/**
		 * Looks up a Language enum constant by matching either: - the language's
		 * display value or name (case‑insensitive), or - the language's locale language
		 * code (e.g., "en", "fr").
		 *
		 * If no match is found, returns the default system language.
		 *
		 * @param str the search string (e.g., "ENGLISH", "English", "en", "FR")
		 * @return the matching Language, or the default system language
		 */
		public static Language fromString(final String str) {
			for (final Language lang : Language.values()) {
				if (lang.value.equalsIgnoreCase(str) || lang.getLocale().getLanguage().equalsIgnoreCase(str)
						|| lang.name().equalsIgnoreCase(str)) {
					return lang;
				}
			}
			return getDefaultSystemLanguage();
		}

		/**
		 * Returns an array of all Language display strings (as returned by toString()).
		 * Useful for populating dropdown lists with raw values. Combine with
		 * listOfTranslatedLanguageNames() for translated display names.
		 *
		 * 
		 * @return String[] of all language display names
		 */
		public static String[] stringValues() {
			return Arrays.stream(Language.values()).map(Language::toString).toArray(String[]::new);
		}

		/**
		 * Returns an array of Language constants that are both usable AND selected.
		 * Filters the full list using {@link Language#isUsable()} and
		 * {@link Language#isSelected()}.
		 *
		 * @return Language[] containing only usable+selected languages
		 */
		public static Language[] usableValues() {
			return Arrays.stream(values()).filter(Language::isUsable).filter(Language::isSelected).toArray(Language[]::new);
		}

		/**
		 * Returns an array of Language constants that are selected for use. Filters the
		 * full list using {@link Language#isSelected()}.
		 *
		 * @return Language[] containing only selected languages
		 */
		public static Language[] selectedValues() {
			return Arrays.stream(values()).filter(Language::isSelected).toArray(Language[]::new);
		}

		/**
		 * Returns an array of display strings for languages that are both usable AND
		 * selected. Each language's {@link Language#getValue()} is used, and duplicates
		 * are removed. Combine with listOfTranslatedLanguageNames() for translated
		 * display names.
		 * 
		 * @return String[] of unique display names for usable+selected languages
		 */
		public static String[] usableValuesAsStrings() {
			return Arrays.stream(values())
					.filter(Language::isUsable)
					.filter(Language::isSelected)
					.map(Language::getValue)
					.distinct()
					.toArray(String[]::new);
		}

		/**
		 * Converts an array of language identifiers (display names or codes) into an
		 * array of localized, translated language names.
		 *
		 * For each input string (e.g., "English", "fr"), it looks up the corresponding
		 * Language constant, retrieves its Locale, and then asks that Locale to display
		 * its own language name in that same locale.
		 *
		 * Example: input "fr" → Locale.FRENCH → "français" (when displayed in French).
		 *
		 * @param usedLanguages input language identifiers (if null then it uses
		 *                      Language.usableValuesAsStrings())
		 * @return String[] of unique translated language names, in the same order and
		 *         usedLanguages
		 */
		public static String[] listOfTranslatedLanguageNames(final String[] usedLanguages) {
			String[] source = (usedLanguages != null) ? usedLanguages : Language.usableValuesAsStrings();
			Set<String> uniqueNames = new LinkedHashSet<>();
			for (String code : source) {
				Locale locale = Language.fromString(code).getLocale();
				uniqueNames.add(locale.getDisplayName(locale));
			}
			return uniqueNames.toArray(new String[0]);
		}
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Paths
	/**
	 * Recursively deletes a folder and all its contents.
	 *
	 * @param folder Path to the directory to delete
	 * @return True if deletion succeeded, false otherwise
	 */
	static boolean delete_Folder(Path folder) {
		if (!Files.exists(folder)) {
			return true;
		}
		try (Stream<Path> paths = Files.walk(folder)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.delete(path);
				} catch (IOException e) {
					throw new RuntimeException(
							"Failed to delete: " + path, //$NON-NLS-1$
							e);
				}
			});
			return true;
		} catch (IOException e) {
			LOGGER.log("Failed to delete Folder", e); //$NON-NLS-1$
			return false;
		}
	}

	/**
	 * Deletes a single file if it exists.
	 *
	 * @param file Path to the file to delete
	 * @return True if deletion succeeded, false otherwise
	 */
	static boolean delete_File(Path file) {
		try {
			return Files.deleteIfExists(file);
		} catch (IOException e) {
			LOGGER.log("Failed to delete File", e); //$NON-NLS-1$
			return false;
		}
	}

	/**
	 * Creates a directory if it does not already exist.
	 *
	 * @param path Path of the directory to create
	 * @return The same path if created or already existed, null otherwise
	 */
	static String create_Dir_If_Missing(final String path) {
		if ((path != null) && !path.isEmpty()) {
			try {
				final File directory = Paths.get(path).toFile();
				if (directory.exists()) {
					return path;
				}
				final boolean created = directory.mkdirs();
				if (created) {
					LOGGER.log("Created Directory: " + path); //$NON-NLS-1$
					return path;
				}
				LOGGER.log("Failed To Create Directory: " + path); //$NON-NLS-1$
			} catch (final SecurityException e) {
				LOGGER.log("Permission Error While Creating Directory: " + path, e); //$NON-NLS-1$
			} catch (final Exception e) {
				LOGGER.log("Unexpected Error While Creating Directory: " + path, e); //$NON-NLS-1$
			}
		} else {
			LOGGER.log("Invalid Or Empty Directory Path: " + path); //$NON-NLS-1$
		}
		return null;
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Init/Maintain
	/**
	 * Initializes the translation system with default language settings, selected
	 * languages, Hibernate configuration, model storage path, and content classes.
	 * It prepares necessary resources for translation.
	 *
	 * @param defaultLang                   Default language to be used, can be
	 *                                      null: default (English)
	 * @param languages                     Array of supported languages; if null,
	 *                                      all languages are used
	 * 
	 * @param runLanguageDetectorService    If true, all models between supported
	 *                                      languages will be downloaded. If false
	 *                                      just default to supported languages &
	 *                                      supported languages to default
	 * @param universalTranslationMode      If true, the language detector model
	 *                                      will remain in memory and accessable for
	 *                                      faster detecting
	 * 
	 * @param redoTranslationsAndModelFiles If true, re-downloads all model files,
	 *                                      remakes models and re-translates all
	 *                                      content
	 * 
	 * @param platform                      Platform application is going to run on
	 * @param doFullTranslatorTest          Full test on all translate methods, 3
	 *                                      times over (uses 3 available languages,
	 *                                      long wait time)
	 * 
	 *                                      translations before proceeding
	 * @param debugMode                     Enables detailed logging during
	 * @param testingMode                   Enables test-specific logging or
	 *                                      behavior
	 * @param showCriticalErrors            Whether critical errors should be logged
	 *                                      when not debugging
	 * @param showIgnoredErrors             Whether ignored errors should be logged
	 *                                      when not debugging
	 * @param siteOrAppIdentifier           Identifier for the current
	 *                                      application/site
	 * 
	 * 
	 * @param modelStoragePath              Path where translation models will be
	 *                                      stored locally
	 * @param contentClasses                Classes containing content enums
	 *                                      (Translatable) that need translating
	 * @return Returns true after successfully completing initialization steps,
	 *         false otherwise
	 */
	public static boolean init(Language defaultLang, Language[] languages, boolean runLanguageDetectorService, boolean universalTranslationMode, boolean redoTranslationsAndModelFiles, Platform platform, boolean doFullTranslatorTest, boolean debugMode, boolean testingMode, boolean showCriticalErrors, boolean showIgnoredErrors, String siteOrAppIdentifier, String modelStoragePath, Class<?>... contentClasses) {
		return init(
				null,
				defaultLang,
				languages,
				runLanguageDetectorService,
				universalTranslationMode,
				redoTranslationsAndModelFiles,
				platform,
				doFullTranslatorTest,
				debugMode,
				testingMode,
				showCriticalErrors,
				showIgnoredErrors,
				siteOrAppIdentifier,
				modelStoragePath,
				contentClasses);
	}

	/**
	 * Initializes the translation system with default language settings, selected
	 * languages, Hibernate configuration, model storage path, and content classes.
	 * It prepares necessary resources for translation.
	 *
	 * @param servletContext                Servlet context
	 * @param libhiberbernate_CFG_XML_Path  Hibernate config file path
	 * 
	 * @param defaultLang                   Default language to be used (can be
	 *                                      null: default (English))
	 * @param languageSelection             Array of supported languages; if null,
	 *                                      all languages are used
	 * @param runLanguageDetectorService    If true, the language detector model
	 *                                      will remain in memory and accessable for
	 *                                      faster detecting
	 * @param universalTranslationMode      If true, the language detector model
	 *                                      will remain in memory and accessable for
	 *                                      faster detecting
	 * 
	 * @param redoTranslationsAndModelFiles If true, re-downloads all model files,
	 *                                      remakes models and re-translates all
	 *                                      content
	 * 
	 * @param debugMode                     Enables detailed logging during
	 * @param testingMode                   Enables test-specific logging or
	 *                                      behavior
	 * @param showCriticalErrors            Whether critical errors should be logged
	 *                                      when not debugging
	 * @param showIgnoredErrors             Whether ignored errors should be logged
	 *                                      when not debugging
	 * @param siteOrAppIdentifier           Identifier for the current
	 *                                      application/site
	 * @param platform                      Platform application is going to run on
	 * @param doFullTranslatorTest          Full test on all translate methods, 3
	 *                                      times over (uses 3 available languages,
	 *                                      long wait time)
	 * 
	 * @param modelStoragePath              Directory where translation models are
	 *                                      stored
	 * @param contentClasses                Classes containing enums with
	 *                                      translatable strings
	 * @return Returns true after successfully completing initialization steps,
	 *         false otherwise
	 */
	public static boolean init(String libhiberbernate_CFG_XML_Path, Language defaultLang, Language[] languageSelection, boolean runLanguageDetectorService, boolean universalTranslationMode, boolean redoTranslationsAndModelFiles, Platform platform, boolean doFullTranslatorTest, boolean debugMode, boolean testingMode, boolean showCriticalErrors, boolean showIgnoredErrors, String siteOrAppIdentifier, String modelStoragePath, Class<?>... contentClasses) {
		if (!runningMaintenance && doModels) {
			//
			runningMaintenance = true;
			//
			HibernateUtil.sessionFactory = HibernateUtil.initSessionFactory();
			//
			redoTranslationsInTable = redoTranslationsAndModelFiles;
			universalTranslations = universalTranslationMode;
			LOGGER.debug = debugMode;
			LOGGER.testing = testingMode;
			LOGGER.showCritical = showCriticalErrors;
			LOGGER.showIgnored = showIgnoredErrors;
			if (defaultLang != null) {
				defaultLanguage = defaultLang;
			}
			if (libhiberbernate_CFG_XML_Path != null) {
				libhiberbernate = libhiberbernate_CFG_XML_Path;
			}
			if (siteOrAppIdentifier != null) {
				siteOrAppId = siteOrAppIdentifier;
			}
			if (platform != null) {
				platformRuningOn = platform;
			}
			if (modelStoragePath != null) {
				modelPath = modelStoragePath;
			} else {
				if (platformRuningOn == Platform.WINDOWS) {
					modelPath = System.getProperty("user.home") + "\\AppData\\Local\\didzappsoftware\\"; //$NON-NLS-1$ //$NON-NLS-2$
				} else if (platformRuningOn == Platform.LINUX) {
					modelPath = System.getProperty("user.home") + "/.local/share/didzappsoftware/"; //$NON-NLS-1$ //$NON-NLS-2$
				}
				if (modelPath == null) {
					return false;
				}
			}
			// dont change this init order: shared > main > folder
			sharedPathString = modelPath + "/hf-translator_"; //$NON-NLS-1$
			mainPathString = modelPath + "/hf-translator_" + Translator.siteOrAppId; //$NON-NLS-1$
			languageModelFolder = FolderName.LanguageModels;
			//
			if (languageSelection != null) {
				for (Language availableLanguage : Language.values()) {
					availableLanguage.selected = false;
				}
				for (Language selectedLanguage : languageSelection) {
					selectedLanguage.selected = true;
				}
			} else {
				for (Language availableLanguage : Language.values()) {
					availableLanguage.selected = true;
				}
			}
			//
			//
			if (redoTranslationsInTable) {
				TranslatorEntity.deleteAllTranslations();
			} else {
				TranslatorEntity.deleteUnusedTranslations();
				TranslatorEntity.deleteDuplicateTranslations();
			}
			downloadFilesAndCreateModels();
			redoTranslationsInTable = false;
			//
			doModels = false;
			runningMaintenance = false;
			//
			//
			LOGGER.log("Starting Language Detection Model..."); //$NON-NLS-1$
			alwaysRunDetector = runLanguageDetectorService;
			if (alwaysRunDetector) {
				defaultLanguage = runLanguageDetectorService("Testing, testing, is this thing working?"); //$NON-NLS-1$
			} else {
				defaultLanguage = runLanguageDetectorOneTime("Testing, testing, is this thing working?"); //$NON-NLS-1$
			}
			if (defaultLanguage == null || !defaultLanguage.equals(Language.ENGLISH)) {
				LOGGER.log("Language Detection Not Working"); //$NON-NLS-1$
				return false;
			}
			LOGGER.log("Language Detection Working"); //$NON-NLS-1$
			//
			devTesting_DoFullClassfileTest = doFullTranslatorTest;
			if (testingMode && devTesting_DoFullClassfileTest) {
				if (!liveTest()) {
					return false;
				}
			}
			final TranslateStacker translateStacker = new TranslateStacker();
			if (contentClasses != null && contentClasses.length > 0) {
				for (Class<?> clazz : contentClasses) {
					for (final Class<?> nested : clazz.getDeclaredClasses()) {
						if (nested.isEnum() && Translatable.class.isAssignableFrom(nested)) {
							final Translatable[] enumValues = (Translatable[]) nested.getEnumConstants();
							translateStacker.addAll(enumValues);
						} else {
							try {
								Object instance = nested.getDeclaredConstructor().newInstance();
								if (instance instanceof Translatable translatable) {
									translateStacker.add(translatable);
								}
							} catch (Exception e) {
								LOGGER.log("Error While Determining If Object Is Translatable", e); //$NON-NLS-1$
							}
						}
					}
				}
			}
			for (final Class<?> nested : TranslatorContent.class.getDeclaredClasses()) {
				if (nested.isEnum() && Translatable.class.isAssignableFrom(nested)) {
					final Translatable[] enumValues = (Translatable[]) nested.getEnumConstants();
					translateStacker.addAll(enumValues);
				} else {
					try {
						Object instance = nested.getDeclaredConstructor().newInstance();
						if (instance instanceof Translatable translatable) {
							translateStacker.add(translatable);
						}
					} catch (Exception ignored) {
						LOGGER.log("Error While Determining If Object Is Translatable", ignored,true); //$NON-NLS-1$
					}
				}
			}
			translateStacker.feedTranslatorDatabase();
			return true;
		}
		return false;
	}

	/**
	 * Performs maintenance tasks like deleting unused/duplicate translations and
	 * re-downloading models.
	 *
	 * @return Returns true after successfully completing maintenance steps, false
	 *         otherwise
	 */
	public static boolean maintain() {
		if (!runningMaintenance) {
			//
			runningMaintenance = true;
			//
			TranslatorEntity.deleteUnusedTranslations();
			TranslatorEntity.deleteDuplicateTranslations();
			//
			downloadFilesAndCreateModels();
			//
			runningMaintenance = false;
		}
		return true;
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Shutdown
	/**
	 * Shuts Down Translator: Sessions, Executors and models...Everything.
	 * 
	 */
	public static synchronized void shutdown() {
		TranslatorEntity.HibernateUtil.shutdown();
		closeLanguageDetectorService();
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Settings
	/**
	 * Sets whether to re-download modelfiles at start-up and redo translations.
	 * delete, translate and re-save. If set after start-up it will redo translation
	 * as and when they are called untill turned off.
	 *
	 * @param reCreate Whether to delete existing models files and translation
	 *                 entries before adding new ones
	 */
	public static void setReCreateAll(boolean reCreate) {
		redoTranslationsInTable = reCreate;
	}

	/**
	 * Returns the default system language set during initialization.
	 *
	 * @return The default Language object
	 */
	public static Language getDefaultSystemLanguage() {
		return defaultLanguage;
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Get
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Url/Dir
	/**
	 * Constructs the path to a model directory for translation.
	 *
	 * @param toTranslate Whether this is the storage path or modle python path
	 *                    (e.g., `opus-mt-en-fr` or `opus-mt-en-fr/ctranslate2`)
	 * @param langIN      Source language code (like "en")
	 * @param langOUT     Target language code (like "fr")
	 * @return Path to the model storage folder or modle python path
	 */
	private static Path getModelDir(final boolean toTranslate, final String langIN, final String langOUT) {
		String finalLangIN = langIN;
		String finaLangOUT = langOUT;
		if (toTranslate) {
			return Paths.get(
					languageModelFolder
							.getPath() + "/opus-mt-" + finalLangIN + "-" + finaLangOUT + "/" + ToPyFiles.generatedModelFolder); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		return Paths.get(languageModelFolder.getPath() + "/opus-mt-" + finalLangIN + "-" + finaLangOUT); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Builds a URL to download an opus-mt model from Hugging Face.
	 *
	 * @param langIn   Source language code (e.g., "en")
	 * @param langOut  Target language code (e.g., "fr")
	 * @param fileName Name of the file to fetch
	 * @return Full URL for downloading the specified file
	 */
	private static String huggingURL(final Language langIn, final Language langOut, final String fileName) {
		String localeCodeIn = langIn.getLocale().getLanguage();
		String localeCodeOut = langOut.getLocale().getLanguage();
		return "https://huggingface.co/Helsinki-NLP/opus-mt-" + localeCodeIn + "-" + localeCodeOut + "/resolve/main/" + fileName; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Runtime
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Python
	/**
	 * Ensures that the CTranslate2 model exists for a given language pair. If not,
	 * generates it via Python script. If this fails, Language is marked un-usable.
	 *
	 * @param modelDir Root directory of the model folder
	 * @param langIN   Source language
	 * @param langOUT  Target language
	 * @throws Exception On failure to generate or validate model, Language is
	 *                   marked un-usable.
	 */
	private static void ensureCTranslate2(final Path modelDir, final Language langIN, final Language langOUT) throws Exception {
		try {
			exportPYFiles();
			String hashString = sha512(Files.readString(scriptFileGenerate.toPath(), StandardCharsets.UTF_8));
			int count = 0;
			while (!hashString.equals(hashFileGenerate)) {
				LOGGER.log(ToPyFiles.generateModelPYName + " : Failed Hash Check"); //$NON-NLS-1$
				exportPYFiles();
				count++;
				if (count > 10) {
					break;
				}
				hashString = sha512(Files.readString(scriptFileGenerate.toPath(), StandardCharsets.UTF_8));
			}
			final Path outDir = modelDir.resolve(ToPyFiles.generatedModelFolder);
			final Path modelBin = outDir.resolve("model.bin"); //$NON-NLS-1$
			if (Files.exists(modelBin)) {
				if (redoTranslationsInTable) {
					if (delete_Folder(outDir)) {
						LOGGER.log(modelDir + "Model Already Exists, Deleting And Re-creating."); //$NON-NLS-1$
					} else {
						LOGGER.log(modelDir + "Model Already Exists, Failed To Delete, Attempting Overwrite."); //$NON-NLS-1$
					}
				} else {
					LOGGER.log("CTranslate2 Model Already Exists: " + modelBin); //$NON-NLS-1$
					return;
				}
			}
			if (hashString.equals(hashFileGenerate)) {
				LOGGER.log("CTranslate2 Model Missing. Generating Via Python..."); //$NON-NLS-1$
				final ProcessBuilder pb = new ProcessBuilder(
						ToPyFiles.pythonLang,
						scriptFileGenerate.getAbsolutePath(),
						modelDir.toAbsolutePath().toString(),
						langIN.locale.getLanguage(),
						langOUT.locale.getLanguage(),
						outDir.toAbsolutePath().toString());
				pb.inheritIO();
				final Process p = pb.start();
				final int exit = p.waitFor();
				if (exit == 0) {
					LOGGER.log("CTranslate2 Model Generated Successfully: " + modelBin); //$NON-NLS-1$
					downloadFlatpickerFile(langIN);
					return;
				}
				LOGGER.log("Failed To Generate CTranslate2 Model, Deleting Model Folder,  Exit Code: " + exit); //$NON-NLS-1$
				throw new Exception(
						"EnsureCTranslate2 Failed, Throwing Error: Triggering Language Disable If Not In Universal Mode"); //$NON-NLS-1$
			}
		} catch (final Exception e) {
			LOGGER.log("EnsureCTranslate2 Or Flatpicker Failed, Throwing Error: Triggering Language Disable", e); //$NON-NLS-1$
			throw e;
		}
	}

	/**
	 * Runs CTranslate2 translation using a Python script.
	 *
	 * @param langIN  Source language code
	 * @param langOUT Target language code
	 * @param input   Text to translate
	 * @return List of translated texts or empty list on error
	 */
	private static List<String> runCTranslate2(final String langIN, final String langOUT, final String input) {
		try {
			exportPYFiles();
			String hashString = sha512(Files.readString(scriptFileTranslate.toPath(), StandardCharsets.UTF_8));
			int count = 0;
			while (!hashString.equals(hashFileTranslate)) {
				LOGGER.log(ToPyFiles.translatePYName + " : Failed Hash Check"); //$NON-NLS-1$
				exportPYFiles();
				count++;
				if (count > 10) {
					break;
				}
				hashString = sha512(Files.readString(scriptFileTranslate.toPath(), StandardCharsets.UTF_8));
			}
			if (hashString.equals(hashFileTranslate)) {
				Path modelDir = getModelDir(true, langIN, langOUT);
				String finalInput = input;
				String finalLangIN = langIN;
				if (!modelDir.toFile().exists()) {
					BridgeContainer bridge = bridgeLanguages(langIN, langOUT, input);
					if (bridge != null) {
						finalInput = bridge.input;
						finalLangIN = bridge.languageCode;
					} else {
						finalInput = null;
						finalLangIN = null;
					}
				}
				if (finalInput != null && !finalInput.isBlank()) {
					final ProcessBuilder pb = new ProcessBuilder(
							ToPyFiles.pythonLang,
							scriptFileTranslate.getAbsolutePath(),
							modelDir.toAbsolutePath().toString(),
							finalLangIN,
							langOUT,
							finalInput);
					pb.redirectErrorStream(false);
					final Process p = pb.start();
					final StringBuilder output = new StringBuilder();
					Thread stdoutThread = new Thread(
							() -> {
								try (BufferedReader reader = new BufferedReader(
										new InputStreamReader(
												p.getInputStream(),
												StandardCharsets.UTF_8))) {
									String line;
									while ((line = reader.readLine()) != null) {
										output.append(line);
									}
								} catch (IOException e) {
									LOGGER.log("CTranslate2 Translation Failed At STDOUT THREAD: ", e); //$NON-NLS-1$
								}
							});
					Thread stderrThread = new Thread(
							() -> {
								try (BufferedReader errReader = new BufferedReader(
										new InputStreamReader(
												p.getErrorStream(),
												StandardCharsets.UTF_8))) {
									String line;
									while ((line = errReader.readLine()) != null) {
										LOGGER.log(line);
									}
								} catch (IOException e) {
									LOGGER.log("CTranslate2 Translation Failed At STDERR THREAD: ", e); //$NON-NLS-1$
								}
							});
					stdoutThread.start();
					stderrThread.start();
					stdoutThread.join();
					stderrThread.join();
					final int exit = p.waitFor();
					if (exit != 0) {
						LOGGER.log("CTranslate2 Translation Failed, Exit Code: " + exit); //$NON-NLS-1$
						return Collections.emptyList();
					}
					return new ObjectMapper().readValue(output.toString(), new TypeReference<List<String>>() {
						/* null */});
				}
			}
			return Collections.emptyList();
		} catch (final Exception e) {
			LOGGER.log("Translation Error At PY File", e); //$NON-NLS-1$
			return Collections.emptyList();
		}
	}

	/**
	 * Container to carry bridged translations and the language code
	 *
	 * @param languageCode language code for input text or list
	 * @param input        translated text or list
	 */
	private static class BridgeContainer {
		private String languageCode;
		private String input;

		private BridgeContainer(final String languageCode, final String input) {
			this.languageCode = languageCode;
			this.input = input;
		}
	}

	/**
	 * Creates a bridge from one language to another using a third language, by
	 * translating to the third language and returning a BridgeContainer to be
	 * translated to the origional target language
	 *
	 * @param langIN  Source language code
	 * @param langOUT Target language code
	 * @param input   Text to translate
	 * @return BridgeContainer Containing translated input data and language code,
	 *         null if no bridge can be made
	 */
	private static BridgeContainer bridgeLanguages(String langIN, String langOUT, String input) throws Exception {
		Language origional = Language.fromLocaleCode(langIN);
		Language target = Language.fromLocaleCode(langOUT);
		for (String l : origional.getAvailableLanguages()) {
			Language potentialBridge = Language.fromString(l);
			if (potentialBridge.getAvailableLanguages().contains(target.toString())) {
				String langBridge = potentialBridge.getLocale().getLanguage();
				Path modelDir = getModelDir(true, langIN, langBridge);
				if (modelDir.toFile().exists()) {
					if (input != null && !input.isBlank()) {
						final ProcessBuilder pb = new ProcessBuilder(
								ToPyFiles.pythonLang,
								scriptFileTranslate.getAbsolutePath(),
								modelDir.toAbsolutePath().toString(),
								langIN,
								langBridge,
								input);
						pb.redirectErrorStream(false);
						final Process p = pb.start();
						final StringBuilder output = new StringBuilder();
						try (BufferedReader reader = new BufferedReader(
								new InputStreamReader(
										p.getInputStream(),
										StandardCharsets.UTF_8));
								BufferedReader errReader = new BufferedReader(
										new InputStreamReader(
												p.getErrorStream(),
												StandardCharsets.UTF_8));) {
							String line;
							while ((line = reader.readLine()) != null) {
								output.append(line);
							}
							while ((line = errReader.readLine()) != null) {
								LOGGER.log(line);
							}
						}
						final int exit = p.waitFor();
						if (exit != 0) {
							LOGGER.log("CTranslate2 Translation Failed, Exit Code: " + exit); //$NON-NLS-1$
							return null;
						}
						return new BridgeContainer(
								langBridge,
								output.toString());
					}
				}
			}
		}
		return null;
	}

	/**
	 * Runs language detector model using a Python script.
	 *
	 * @param input Text to detect the language of.
	 * @return List of translated texts or empty list on error
	 */
	private static Language runLanguageDetectorOneTime(final String input) {
		try {
			exportPYFiles();
			String hashString = sha512(Files.readString(scriptFileDetectLanguage.toPath(), StandardCharsets.UTF_8));
			int count = 0;
			while (!hashString.equals(hashFileDetectLanguage)) {
				LOGGER.log(ToPyFiles.conLIDPYName + " : Failed Hash Check"); //$NON-NLS-1$
				exportPYFiles();
				count++;
				if (count > 10) {
					break;
				}
				hashString = sha512(Files.readString(scriptFileDetectLanguage.toPath(), StandardCharsets.UTF_8));
			}
			if (hashString.equals(hashFileDetectLanguage)) {
				final ProcessBuilder pb = new ProcessBuilder(
						ToPyFiles.pythonLang,
						scriptFileDetectLanguage.getAbsolutePath(),
						"false", //$NON-NLS-1$
						input);
				pb.redirectErrorStream(false);
				final Process p = pb.start();
				final StringBuilder output = new StringBuilder();
				Thread stdoutThread = new Thread(
						() -> {
							try (BufferedReader reader = new BufferedReader(
									new InputStreamReader(
											p.getInputStream(),
											StandardCharsets.UTF_8))) {
								String line;
								while ((line = reader.readLine()) != null) {
									output.append(line);
								}
							} catch (IOException e) {
								LOGGER.log("LanguageDetector Failed At STDOUT THREAD: ", e); //$NON-NLS-1$
							}
						});
				Thread stderrThread = new Thread(
						() -> {
							try (BufferedReader errReader = new BufferedReader(
									new InputStreamReader(
											p.getErrorStream(),
											StandardCharsets.UTF_8))) {
								String line;
								while ((line = errReader.readLine()) != null) {
									LOGGER.log(line);
								}
							} catch (IOException e) {
								LOGGER.log("LanguageDetector Failed At STDERR THREAD: ", e); //$NON-NLS-1$
							}
						});
				stdoutThread.start();
				stderrThread.start();
				stdoutThread.join();
				stderrThread.join();
				final int exit = p.waitFor();
				if (exit != 0) {
					LOGGER.log("Language Detector Failed, Exit Code: " + exit); //$NON-NLS-1$
					return defaultLanguage;
				}
				JsonNode arr = new ObjectMapper().readTree(output.toString());
				String topLangCode = arr.get(0).get("language").asText(); //$NON-NLS-1$
				@SuppressWarnings("unused")
				double topConf = arr.get(0).get("confidence").asDouble(); //$NON-NLS-1$
				String secondLangCode = arr.get(0).get("language").asText(); //$NON-NLS-1$
				double secondConf = arr.get(0).get("confidence").asDouble(); //$NON-NLS-1$
				String thirdLangCode = arr.get(0).get("language").asText(); //$NON-NLS-1$
				double thirdConf = arr.get(0).get("confidence").asDouble(); //$NON-NLS-1$
				Language detected = Language.fromLocaleCode(toTwoLetteLanguagerCode(topLangCode));
				if (detected == null && secondConf > 0.4) {
					detected = Language.fromLocaleCode(toTwoLetteLanguagerCode(secondLangCode));
				}
				if (detected == null && thirdConf > 0.4) {
					detected = Language.fromLocaleCode(toTwoLetteLanguagerCode(thirdLangCode));
				}
				if (detected == null) {
					return defaultLanguage;
				}
				if (detected.isUsable() && detected.isSelected()) {
					return detected;
				}
			}
			return defaultLanguage;
		} catch (final Exception e) {
			LOGGER.log("Detection Error At PY File", e); //$NON-NLS-1$
			return defaultLanguage;
		}
	}

	/**
	 * Runs language detector model permanently, untill shutdown, using a Python
	 * script.
	 *
	 * @param input Text to detect the language of.
	 * @return List of translated texts or empty list on error
	 */
	private static Language runLanguageDetectorService(final String input) {
		synchronized (processLock) {
			if (persistentProcess == null || !persistentProcess.isAlive()) {
				try {
					exportPYFiles();
					String hashString = sha512(Files.readString(scriptFileDetectLanguage.toPath(), StandardCharsets.UTF_8));
					int count = 0;
					while (!hashString.equals(hashFileDetectLanguage)) {
						LOGGER.log(ToPyFiles.conLIDPYName + " : Failed Hash Check"); //$NON-NLS-1$
						exportPYFiles();
						count++;
						if (count > 10) {
							break;
						}
						hashString = sha512(Files.readString(scriptFileDetectLanguage.toPath(), StandardCharsets.UTF_8));
					}
					if (hashString.equals(hashFileDetectLanguage)) {
						ProcessBuilder pb = new ProcessBuilder(
								ToPyFiles.pythonLang,
								scriptFileDetectLanguage.getAbsolutePath(),
								"true", //$NON-NLS-1$
								input);
						pb.redirectErrorStream(false);
						persistentProcess = pb.start();
						persistentWriter = new BufferedWriter(
								new OutputStreamWriter(
										persistentProcess.getOutputStream(),
										StandardCharsets.UTF_8));
						persistentReader = new BufferedReader(
								new InputStreamReader(
										persistentProcess.getInputStream(),
										StandardCharsets.UTF_8));
						thread = new Thread(
								() -> {
									try (BufferedReader err = new BufferedReader(
											new InputStreamReader(
													persistentProcess.getErrorStream(),
													StandardCharsets.UTF_8))) {
										String line;
										while (persistentProcess.isAlive()&&(line = err.readLine()) != null) {
											LOGGER.log(line);
										}
									} catch (IOException ignored) {
										LOGGER.log("LanguageDetector Failed At STDERR THREAD: ", ignored, true); //$NON-NLS-1$
									}
								});
						thread.start();
					}
				} catch (Exception e) {
					LOGGER.log("Failed To Start Persistent Language Detector", e); //$NON-NLS-1$
					return null;
				}
			}
		}
		// ---- Now send the input and read the response ----
		try {
			synchronized (processLock) {
				persistentWriter.write(input);
				persistentWriter.newLine();
				persistentWriter.flush();
				String jsonLine = persistentReader.readLine();
				if (jsonLine == null) {
					persistentProcess.destroyForcibly();
					return detectLanguage(input);
				}
				JsonNode arr = new ObjectMapper().readTree(jsonLine);
				String topLangCode = arr.get(0).get("language").asText(); //$NON-NLS-1$
				@SuppressWarnings("unused")
				double topConf = arr.get(0).get("confidence").asDouble(); //$NON-NLS-1$
				String secondLangCode = arr.get(1).get("language").asText(); //$NON-NLS-1$
				double secondConf = arr.get(1).get("confidence").asDouble(); //$NON-NLS-1$
				String thirdLangCode = arr.get(2).get("language").asText(); //$NON-NLS-1$
				double thirdConf = arr.get(2).get("confidence").asDouble(); //$NON-NLS-1$
				Language detected = Language.fromLocaleCode(toTwoLetteLanguagerCode(topLangCode));
				if (detected == null && secondConf > 0.4) {
					detected = Language.fromLocaleCode(toTwoLetteLanguagerCode(secondLangCode));
				}
				if (detected == null && thirdConf > 0.4) {
					detected = Language.fromLocaleCode(toTwoLetteLanguagerCode(thirdLangCode));
				}
				if (detected == null) {
					return null;
				}
				if (detected.isUsable() && detected.isSelected()) {
					return detected;
				}
				return null;
			}
		} catch (Exception e) {
			LOGGER.log("Detection Error At PY File", e); //$NON-NLS-1$
			return null;
		}
	}

	private static void closeLanguageDetectorService() {
		synchronized (processLock) {
			try {
				if (persistentWriter != null)
					persistentWriter.close();
			} catch (IOException ignored) {
				LOGGER.log("Error Closing Persistent Writer: ", ignored, true); //$NON-NLS-1$
			}
			try {
				if (persistentReader != null)
					persistentReader.close();
			} catch (IOException ignored) {
				LOGGER.log("Error Closing Persistent Reader: ", ignored, true); //$NON-NLS-1$
			}
			if (persistentProcess != null){
				persistentProcess.destroyForcibly();
			}
			persistentProcess = null;
			persistentWriter = null;
			persistentReader = null;
			if (thread != null && thread.isAlive()) {
				try {
					thread.interrupt();
					thread.join();
				} catch (InterruptedException ignored) {
					Thread.currentThread().interrupt();
					LOGGER.log("Error Closing Thread: ", ignored, true); //$NON-NLS-1$
				}
			}
			thread = null;
		}
	}

	private static String toTwoLetteLanguagerCode(String conlidCode) {
		String iso639_3 = conlidCode.substring(0, 3);
		ULocale locale = ULocale.forLanguageTag(iso639_3);
		return locale.getLanguage(); // gives "en", "fr", etc. if mapping exists
	}

	/**
	 * Exports Python scripts and folfers needed for model generation and
	 * translation from the classpath. Checks hash integrity before overwriting, if
	 * they exist already.
	 */
	private static void exportPYFiles() {
		try {
			if ((scriptFileGenerate == null) || !scriptFileGenerate.exists() || (scriptFileTranslate == null)
					|| !scriptFileTranslate.exists()
					|| !sha512(Files.readString(scriptFileGenerate.toPath(), StandardCharsets.UTF_8)).equals(hashFileGenerate)
					|| !sha512(Files.readString(scriptFileTranslate.toPath(), StandardCharsets.UTF_8))
							.equals(hashFileTranslate)) {
				scriptFileGenerate = Extract.ToFileSystem.fromClassPathContext(
						TranslatorEntity.class,
						languageModelFolder.getTempPath(),
						ToPyFiles.generateModelPY,
						ToPyFiles.generateModelPYName,
						ToPyFiles.pythonSuffix);
				hashFileGenerate = sha512(Files.readString(scriptFileGenerate.toPath(), StandardCharsets.UTF_8));
				scriptFileTranslate = Extract.ToFileSystem.fromClassPathContext(
						TranslatorEntity.class,
						languageModelFolder.getTempPath(),
						ToPyFiles.translatePY,
						ToPyFiles.translatePYName,
						ToPyFiles.pythonSuffix);
				hashFileTranslate = sha512(Files.readString(scriptFileTranslate.toPath(), StandardCharsets.UTF_8));
				scriptFileDetectLanguage = Extract.ToFileSystem.fromClassPathContext(
						TranslatorEntity.class,
						Extract.ToFileSystem
								.fromClassPathContext(
										TranslatorEntity.class,
										languageModelFolder.getPath(),
										ToPyFiles.conLID_Folder)
								.getAbsolutePath(),
						ToPyFiles.conLIDPY,
						ToPyFiles.conLIDPYName,
						ToPyFiles.pythonSuffix);
				hashFileDetectLanguage = sha512(Files.readString(scriptFileDetectLanguage.toPath(), StandardCharsets.UTF_8));
				LOGGER.log(ToPyFiles.generateModelPYName + " And " + ToPyFiles.translatePYName + " Installed From Source Files"); //$NON-NLS-1$ //$NON-NLS-2$
			}
		} catch (final Exception e) {
			LOGGER.log("Failed To Download Generate And Translate Python Files", e); //$NON-NLS-1$
		}
	}

	private class Extract {
		private static class ToFileSystem {
			private static File fromClassPathContext(final Class<?> clazz, final String saveToPath, final String resourcePath, final String fileName, final String suffix) {
				try (InputStream in = clazz.getResourceAsStream(resourcePath)) {
					return exportandReturnFile(in, saveToPath, resourcePath, fileName, suffix);
				} catch (final Exception e) {
					LOGGER.log("Failed To Extraxt Resource", e); //$NON-NLS-1$
				}
				return null;
			}

			private static File exportandReturnFile(final InputStream in, final String saveToPath, final String resourcePath, final String fileName, final String suffix) throws FileNotFoundException, IOException {
				final File dir = Paths.get(saveToPath).toFile();
				if (!dir.exists()) {
					dir.mkdirs();
				}
				final File file = new File(
						dir,
						fileName + suffix);
				try (FileOutputStream fos = new FileOutputStream(
						file,
						false)) {
					final byte[] buffer = new byte[8192];
					int len;
					while ((len = in.read(buffer)) != -1) {
						fos.write(buffer, 0, len);
					}
				}
				LOGGER.log("Extracted Resource: " + resourcePath); //$NON-NLS-1$
				return file;
			}

			private static File fromClassPathContext(final Class<?> clazz, final String saveToPath, final String resourceFolderPath) {
				try {
					final URL folderUrl = clazz.getResource(resourceFolderPath);
					if (resourceFolderPath == null) {
						LOGGER.log("Resource Folder Not Found: " + resourceFolderPath); //$NON-NLS-1$
						return null;
					}
					return exportFolderAndReturnPath(folderUrl, saveToPath);
				} catch (final Exception e) {
					LOGGER.log("Failed To Extraxt Resource Folder", e); //$NON-NLS-1$
				}
				return null;
			}

			private static File exportFolderAndReturnPath(final URL folderUrl, final String saveToPath) {
				final File targetDir = new File(
						saveToPath,
						new File(
								folderUrl.getPath()).getName());
				targetDir.mkdirs();
				try {
					// Handle file system case (development)
					if (folderUrl.getProtocol().equals("file")) { //$NON-NLS-1$
						final File sourceFolder = new File(
								folderUrl.toURI());
						copyFileSystemFolder(sourceFolder, targetDir);
						LOGGER.log("Extracted folder from filesystem: " + folderUrl); //$NON-NLS-1$
						return targetDir;
					}
					// Handle JAR case (production)
					if (folderUrl.getProtocol().equals("jar")) { //$NON-NLS-1$
						final JarURLConnection conn = (JarURLConnection) folderUrl.openConnection();
						try (JarFile jarFile = conn.getJarFile()) {
							final String entryPath = conn.getEntryName();
							final Enumeration<JarEntry> entries = jarFile.entries();
							while (entries.hasMoreElements()) {
								final JarEntry entry = entries.nextElement();
								final String entryName = entry.getName();
								if (entryName.startsWith(entryPath) && !entryName.equals(entryPath)) {
									final File targetFile = new File(
											targetDir,
											entryName.substring(entryPath.length()));
									if (entry.isDirectory()) {
										targetFile.mkdirs();
									} else {
										targetFile.getParentFile().mkdirs();
										try (InputStream in = jarFile.getInputStream(entry);
												FileOutputStream out = new FileOutputStream(
														targetFile)) {
											final byte[] buffer = new byte[8192];
											int len;
											while ((len = in.read(buffer)) != -1) {
												out.write(buffer, 0, len);
											}
										}
									}
								}
							}
						}
						LOGGER.log("Extracted folder from JAR: " + folderUrl.getPath()); //$NON-NLS-1$
						return targetDir;
					}
					LOGGER.log("Unsupported protocol: " + folderUrl.getProtocol()); //$NON-NLS-1$
					return null;
				} catch (Exception e) {
					LOGGER.log("Failed to extract folder", e); //$NON-NLS-1$
					return null;
				}
			}

			private static void copyFileSystemFolder(File src, File dest) throws IOException {
				if (src.isDirectory()) {
					dest.mkdirs();
					for (File child : src.listFiles()) {
						copyFileSystemFolder(
								child,
								new File(
										dest,
										child.getName()));
					}
				} else {
					try (FileInputStream in = new FileInputStream(
							src);
							FileOutputStream out = new FileOutputStream(
									dest)) {
						byte[] buffer = new byte[8192];
						int len;
						while ((len = in.read(buffer)) != -1) {
							out.write(buffer, 0, len);
						}
					}
				}
			}
		}
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// SHA512
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// ALGO
	/**
	 * Computes the SHA-512 hash of a given string.
	 *
	 * @param string Input string to compute hash for
	 * @return Hex-encoded SHA-512 digest
	 */
	private static String sha512(final String string) {
		final StringBuilder hexString = new StringBuilder();
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-512"); //$NON-NLS-1$
			byte[] hash = digest.digest(string.getBytes(StandardCharsets.UTF_8));
			for (final byte b : hash) {
				final String hex = String.format("%02x", Byte.valueOf(b)); //$NON-NLS-1$
				hexString.append(hex);
			}
		} catch (final Exception e) {
			LOGGER.log("Authentication Error", e); //$NON-NLS-1$
		}
		return hexString.toString();
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Model
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Files
	/**
	 * Downloads model files for all language pairs and creates corresponding
	 * language models. This method iterates over all available languages, downloads
	 * the required model files, and handles errors by deleting the model directory
	 * if a download fails and marking the Language as un-usable.
	 *
	 * @throws Exception If an error occurs during the download or file handling
	 *                   process. Marks the Language as un-usable.
	 */
	private static void downloadFilesAndCreateModels() {
		for (final Language lang2 : Language.selectedValues()) {
			final Language lang1 = getDefaultSystemLanguage();
			boolean defaultToCleintSuccess = false;
			if ((!lang1.equals(lang2)) && (!lang1.getLocale().getLanguage().equals(lang2.getLocale().getLanguage()))) {
				try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()) {
					Path modelDir = getModelDir(false, lang1.getLocale().getLanguage(), lang2.getLocale().getLanguage());
					try {
						Files.createDirectories(modelDir);
						LOGGER.log("Downloading Model: " + lang1 + "-" + lang2); //$NON-NLS-1$ //$NON-NLS-2$
						downloadModelFiles(client, modelDir, lang1, lang2);
						defaultToCleintSuccess = true;
						defaultLanguage.addToAvailableLanguages(lang2);
					} catch (final Exception e) {
						if (!universalTranslations) {
							lang2.usable = false;
						}
						LOGGER.log(
								"Download Failed For " + lang1 + "-" + lang2 + ". No Models Stored At: " + modelDir //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
										.toAbsolutePath(),
								e);
						if (delete_Folder(modelDir)) {
							LOGGER.log("Model Folder Deleted"); //$NON-NLS-1$
						} else {
							LOGGER.log("Failed To Delete Model Folder"); //$NON-NLS-1$
						}
					}
					modelDir = getModelDir(false, lang2.getLocale().getLanguage(), lang1.getLocale().getLanguage());
					try {
						Files.createDirectories(modelDir);
						LOGGER.log("Downloading Model: " + lang2 + "-" + lang1); //$NON-NLS-1$ //$NON-NLS-2$
						downloadModelFiles(client, modelDir, lang2, lang1);
						if (defaultToCleintSuccess) {
							lang2.usable = true;
							lang2.addToAvailableLanguages(lang1);
							downloadFilesAndCreateInterClientModels(lang1, lang2);
						}
					} catch (final Exception e) {
						if (!universalTranslations) {
							lang2.usable = false;
						}
						LOGGER.log(
								"Download Failed For " + lang2 + "-" + lang1 + ". No Models Stored At: " + modelDir //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
										.toAbsolutePath(),
								e);
						if (delete_Folder(modelDir)) {
							LOGGER.log("Model Folder Deleted"); //$NON-NLS-1$
						} else {
							LOGGER.log("Failed To Delete Model Folder"); //$NON-NLS-1$
						}
					}
				}
			}
		}
	}

	/**
	 * Downloads model files between all language pairs and creates corresponding
	 * language models. This method iterates over all available languages, downloads
	 * the required model files, and handles errors by deleting the model directory
	 * if a download fails and marking the Language as un-usable if universal
	 * translation not set.
	 * 
	 * @param lang1 From downloadFilesAndCreateModels.
	 * @param lang2 From downloadFilesAndCreateModels
	 *
	 * @throws Exception If an error occurs during the download or file handling
	 *                   process. Marks the Language as un-usable.
	 */
	private static void downloadFilesAndCreateInterClientModels(Language lang1, Language lang2) {
		if (universalTranslations) {
			for (final Language lang3 : Language.selectedValues()) {
				if ((!lang3.equals(lang1)) && (!lang3.equals(lang2))
						&& (!lang3.getLocale().getLanguage().equals(lang1.getLocale().getLanguage()))
						&& (!lang3.getLocale().getLanguage().equals(lang2.getLocale().getLanguage()))) {
					try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()) {
						Path modelDir = getModelDir(false, lang2.getLocale().getLanguage(), lang3.getLocale().getLanguage());
						try {
							Files.createDirectories(modelDir);
							LOGGER.log("Downloading Model: " + lang2 + "-" + lang3); //$NON-NLS-1$ //$NON-NLS-2$
							downloadModelFiles(client, modelDir, lang2, lang3);
							lang2.addToAvailableLanguages(lang3);
						} catch (final Exception e) {
							if (!universalTranslations) {
								lang3.usable = false;
							}
							LOGGER.log(
									"Download Failed For " + lang2 + "-" + lang3 + ". No Models Stored At: " + modelDir //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
											.toAbsolutePath(),
									e);
							if (delete_Folder(modelDir)) {
								LOGGER.log("Model Folder Deleted"); //$NON-NLS-1$
							} else {
								LOGGER.log("Failed To Delete Model Folder"); //$NON-NLS-1$
							}
						}
						modelDir = getModelDir(false, lang3.getLocale().getLanguage(), lang2.getLocale().getLanguage());
						try {
							Files.createDirectories(modelDir);
							LOGGER.log("Downloading Model: " + lang3 + "-" + lang2); //$NON-NLS-1$ //$NON-NLS-2$
							downloadModelFiles(client, modelDir, lang3, lang2);
							lang3.addToAvailableLanguages(lang2);
						} catch (final Exception e) {
							if (!universalTranslations) {
								lang3.usable = false;
							}
							LOGGER.log(
									"Download Failed For " + lang3 + "-" + lang2 + ". No Models Stored At: " + modelDir //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
											.toAbsolutePath(),
									e);
							if (delete_Folder(modelDir)) {
								LOGGER.log("Model Folder Deleted"); //$NON-NLS-1$
							} else {
								LOGGER.log("Failed To Delete Model Folder"); //$NON-NLS-1$
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Downloads individual model files for a given language pair using the provided
	 * HTTP client. Checks for existing files and decides whether to re-download the
	 * files and overwrite based on the {@code redoTranslationsInTable} flag.
	 * Validates downloaded content to ensure it's not empty or invalid before
	 * saving.
	 *
	 * @param client   The HTTP client used for downloading files.
	 * @param modelDir The directory where the model files will be saved.
	 * @param langIn   The source Language for the translation model.
	 * @param langOut  The target Language for the translation model.
	 * @throws Exception If an error occurs during file download or validation.
	 */
	private static void downloadModelFiles(final HttpClient client, final Path modelDir, final Language langIn, final Language langOut) throws Exception {
		Path outPath = null;
		for (final String file : MODEL_FILES_TO_DOWNLOAD) {
			outPath = modelDir.resolve(file);
			if (Files.exists(outPath)) {
				if (redoTranslationsInTable) {
					if (delete_File(outPath)) {
						LOGGER.log(file + "File Already Exists, Deleting And Re-Downloading."); //$NON-NLS-1$
					} else {
						LOGGER.log(file + "File Already Exists, Failed To Delete, Attempting Overwrite."); //$NON-NLS-1$
					}
				} else {
					LOGGER.log(file + " Already Exists, Skipping."); //$NON-NLS-1$
					continue;
				}
			}
			LOGGER.log("Downloading " + file); //$NON-NLS-1$
			try {
				final String url = huggingURL(langIn, langOut, file);
				LOGGER.log("Connecting To: " + url); //$NON-NLS-1$
				downloadFile(client, url, outPath);
				if (!Files.exists(outPath)) {
					LOGGER.log(file + "Invalid, File Not Saved"); //$NON-NLS-1$
					continue;
				}
				final String chk = readFileContents(outPath);
				if (chk.isBlank() || chk.contains("Invalid username or password.") || chk.contains("Entry not found")) { //$NON-NLS-1$ //$NON-NLS-2$
					LOGGER.log(file + " Is Empty / Invalid, Deleted"); //$NON-NLS-1$
					delete_File(outPath);
					continue;
				}
				LOGGER.log("Download Complete. File stored at: " + outPath.toAbsolutePath()); //$NON-NLS-1$
			} catch (final Exception e) {
				delete_File(outPath);
				throw e;
			}
		}
		try {
			ensureCTranslate2(modelDir, langIn, langOut);
			downloadFlatpickerFile(langIn);
		} catch (final Exception e) {
			throw e;
		}
	}

	/**
	 * Downloads and saves Flatpickr resources (CSS, JS, and locale files) for a
	 * specified language. The method ensures the directory exists and downloads
	 * necessary files from CDN sources.
	 *
	 * @param language The Language for which to download Flatpickr resources.
	 */
	private static void downloadFlatpickerFile(final Language language) {
		if (language != Language.ENGLISH) {
			try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
				final String flatpickrResourcePath = ToFlatpickr.flatpickrRelativePath;
				// Get the directory where the JAR is located
				Path jarDir;
				try {
					jarDir = Path.of(TranslatorEntity.class.getProtectionDomain().getCodeSource().getLocation().toURI())
							.getParent();
					Path baseDir = jarDir.resolve(flatpickrResourcePath);
					// Now use baseDir for saving files
					Files.createDirectories(baseDir);
					// Download main files
					if (doFlatpickerFiles) {
						downloadFile(
								client,
								"https://cdn.jsdelivr.net/npm/flatpickr/dist/flatpickr.min.css", //$NON-NLS-1$
								baseDir.resolve(ToFlatpickr.cssPath)); // $NON-NLS-1$
						downloadFile(
								client,
								"https://cdn.jsdelivr.net/npm/flatpickr/dist/flatpickr.min.js", //$NON-NLS-1$
								baseDir.resolve(ToFlatpickr.jsPath)); // $NON-NLS-1$
						doFlatpickerFiles = false;
					}
					// Download locale file
					final String localeFile = language.getLocale().getLanguage() + ".js"; //$NON-NLS-1$
					downloadFile(
							client,
							"https://cdn.jsdelivr.net/npm/flatpickr@4.6.13/dist/l10n/" + localeFile, //$NON-NLS-1$
							baseDir.resolve(localeFile));
					LOGGER.log("Flatpickr Downloaded For " + language.toString()); //$NON-NLS-1$
				} catch (URISyntaxException e) {
					LOGGER.log("URISyntaxException While Downloading Flatpicker File", e); //$NON-NLS-1$
				}
			} catch (IOException e) {
				LOGGER.log("Flatpickr Downloaded Failed For " + language.toString(), e); //$NON-NLS-1$
			}
		}
	}

	/**
	 * Downloads a single file from a given URL to the specified destination. Checks
	 * if the file already exists before downloading and logs accordingly.
	 *
	 * @param client      The HTTP client used for the download request.
	 * @param url         The URL of the file to be downloaded.
	 * @param destination The local path where the file will be saved.
	 * @throws IOException          If an I/O error occurs during the download.
	 * @throws InterruptedException If the thread is interrupted during the
	 *                              download.
	 */
	private static void downloadFile(final HttpClient client, final String url, final Path destination) {
		if (Files.exists(destination)) {
			LOGGER.log("File already exists: " + destination.getFileName()); //$NON-NLS-1$
			return;
		}
		final HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(30)).build();
		try {
			HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination));
			if (response.statusCode() != 200) {
				delete_File(destination);
				LOGGER.log("Download Failed: " + destination.getFileName()); //$NON-NLS-1$
				return;
			}
			LOGGER.log("Downloaded: " + destination.getFileName()); //$NON-NLS-1$
		} catch (IOException | InterruptedException e) {
			LOGGER.log("Download Failed To Connect: " + destination.getFileName(), e); //$NON-NLS-1$
		}
	}

	/**
	 * Reads part of the contents of a downloaded file returns a String.
	 *
	 * @param filePath The path to the file whose content is to be read.
	 * @return A string containing the beginning of the file, used to validate
	 *         content.
	 * @throws IOException If an I/O error occurs while reading the file.
	 */
	private static String readFileContents(final Path filePath) throws IOException {
		try (InputStream in = Files.newInputStream(filePath)) {
			final StringBuilder firstPart = new StringBuilder();
			int whitespaceCount = 0;
			int b;
			while (((b = in.read()) != -1) && (whitespaceCount < 5)) {
				final char c = (char) (b & 0xFF); // safely cast byte to char
				firstPart.append(c);
				if (Character.isWhitespace(c)) {
					whitespaceCount++;
				}
			}
			return firstPart.toString();
		}
	}
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Public

	// MULTIPLE TRAANSLATION METHODS AND DATABASE FEEDING
	/**
	 * A utility class for managing and performing translations of strings and enum
	 * values (Translatable). Provides methods to add items to a translation stack,
	 * perform translations in various modes, and feed translation data into the
	 * database asynchronously.
	 */
	public static class TranslateStacker {
		private boolean redoTranslationsInTableStacker = redoTranslationsInTable;
		private boolean doAsList = true;

		/**
		 * Sets whether to redo translations in the database table at runtime for this
		 * stacker instance. delete, translate and re-save.
		 *
		 * @param reCreate Whether to delete existing translation entries before adding
		 *                 new ones
		 */
		public void setReCreate(boolean reCreate) {
			this.redoTranslationsInTableStacker = reCreate;
		}

		/**
		 * Sets how to process translations. true: list (faster), false: individual
		 * strings.
		 *
		 * @param redoTranslations Whether to delete existing translation entries before
		 *                         adding new ones
		 */
		public void setProcessAsList(boolean doAsList) {
			this.doAsList = doAsList;
		}

		private List<String> strings = new ArrayList<>();
		private List<Translatable> translatable = new ArrayList<>();

		/**
		 * Clears the lists of content to be translated
		 *
		 */
		public void clear() {
			this.strings = new ArrayList<>();
			this.translatable = new ArrayList<>();
		}

		public List<Object> getCombinedList() {
			List<Object> combined = new ArrayList<>();
			combined.addAll(this.strings);
			combined.addAll(this.translatable);
			return combined;
		}

		/**
		 * Adds a string to the translation stack.
		 *
		 * @param text The string to be added. If null, it is ignored.
		 * @return This TranslateStacker instance for chaining.
		 */
		public TranslateStacker add(final String text) {
			if (text != null) {
				this.strings.add(text);
			}
			return this;
		}

		/**
		 * Adds an Translatable enum value to the translation stack.
		 *
		 * @param text The Translatable enum value to be added. If null, it is ignored.
		 * @return This TranslateStacker instance for chaining.
		 */
		public TranslateStacker add(final Translatable text) {
			if (text != null) {
				this.translatable.add(text);
			}
			return this;
		}

		/**
		 * Adds all elements of an Translatable array to the translation stack.
		 *
		 * @param texts The array of Translatable enum values to be added. If null, it
		 *              is ignored.
		 * @return This TranslateStacker instance for chaining.
		 */
		public TranslateStacker addAll(final Translatable[] texts) {
			if (texts == null) {
				return this;
			}
			for (final Translatable item : texts) {
				if (item != null) {
					this.translatable.add(item);
				}
			}
			return this;
		}

		/**
		 * Adds all elements of a String array to the translation stack.
		 *
		 * @param texts The array of strings to be added. If null, it is ignored.
		 * @return This TranslateStacker instance for chaining.
		 */
		public TranslateStacker addAll(final String[] texts) {
			if (texts == null) {
				return this;
			}
			for (final String item : texts) {
				if (item != null) {
					this.strings.add(item);
				}
			}
			return this;
		}

		/**
		 * Translates all added Strings or Translatable using the database and model.
		 * 
		 * @param <T> The type of input objects to translate.
		 *
		 * @param to  The target language for translation. (translated from default
		 *            language)
		 * @return A map of translated Strings or Translatable to their translations.
		 */
		public <T> Map<T, String> translate(final Language to) {
			return translate(null, to);
		}

		/**
		 * Translates all added Strings or Translatable using the database and model.
		 * 
		 * @param <T>  The type of input objects to translate.
		 *
		 * @param from The source language for translation.
		 * @param to   The target language for translation.
		 * @return A map of translated Strings or Translatable to their translations.
		 */
		@SuppressWarnings("unchecked")
		public <T> Map<T, String> translate(final Language from, final Language to) {
			final Map<T, String> results = (Map<T, String>) Translator.translate(
					from != null ? from : defaultLanguage,
					to,
					this.getCombinedList(),
					this.doAsList,
					this.redoTranslationsInTableStacker);
			return results;
		}

		/**
		 * Translates all added Strings or Translatable using only the model (no
		 * database).
		 * 
		 * @param <T> The type of input objects to translate.
		 *
		 * @param to  The target language for translation. (translated from default
		 *            language)
		 * @return A map of translated Strings or Translatable to their translations.
		 */
		public <T> Map<T, String> translate_ModelOnly(final Language to) {
			return translate_ModelOnly(null, to);
		}

		/**
		 * Translates all added Strings or Translatable using only the model (no
		 * database).
		 * 
		 * @param <T>
		 *
		 * @param from The source language for translation.
		 * @param to   The target language for translation.
		 * @return A map of translated Strings or Translatable to their translations.
		 */
		@SuppressWarnings("unchecked")
		public <T> Map<T, String> translate_ModelOnly(final Language from, final Language to) {
			final Map<T, String> results = (Map<T, String>) Translator
					.translate_OnlyUseModel(from != null ? from : defaultLanguage, to, this.getCombinedList(), this.doAsList);
			return results;
		}

		/**
		 * Translates all added Strings or Translatable and feeds the translator
		 * database with translation data from all languages in a separate thread.
		 * Starts with the users language (not system defaut) and returns that set of
		 * translations then continues in a separate thread for the rest.
		 * 
		 * @param <T>
		 *
		 * @param to  The target language for translation. (translated from default
		 *            language)
		 * @return A map of translated Strings or Translatable to their translations.
		 */
		public <T> Map<T, String> translateAndFeedTranslatorDatabase(final Language to) {
			return translateAndFeedTranslatorDatabase(defaultLanguage, to);
		}

		/**
		 * Translates all added Strings or Translatable and feeds the translator
		 * database with translation data from all languages in a separate thread.
		 * Starts with the users language (not system defaut) and returns that set of
		 * translations then continues in a separate thread for the rest.
		 * 
		 * @param <T>
		 *
		 * @param from The source language for translation.
		 * @param to   The target language for translation.
		 * @return A map of translated Strings or Translatable to their translations.
		 */
		@SuppressWarnings("unchecked")
		public <T> Map<T, String> translateAndFeedTranslatorDatabase(final Language from, final Language to) {
			Map<T, String> result = new LinkedHashMap<>();
			result = (Map<T, String>) Translator.translate(
					from != null ? from : defaultLanguage,
					to,
					this.getCombinedList(),
					this.doAsList,
					this.redoTranslationsInTableStacker);
			new Thread(
					() -> {
						LOGGER.log("Feeding Translator In New Thread"); //$NON-NLS-1$
						for (final Language l : languageInUseFirst(Language.usableValues(), to)) {
							if ((!l.equals(getDefaultSystemLanguage()) && (!l.equals(to)))) {
								Translator.translate(
										from != null ? from : defaultLanguage,
										l,
										this.getCombinedList(),
										this.doAsList,
										this.redoTranslationsInTableStacker);
							}
						}
						LOGGER.log("Finished Feeding Translator, Thread Terminated"); //$NON-NLS-1$
					}).start();
			return result;
		}

		/**
		 * Feeds the translator database with translation data from all languages.
		 *
		 */
		public void feedTranslatorDatabase() {
			for (final Language l : Language.usableValues()) {
				if (!l.equals(getDefaultSystemLanguage())) {
					Translator.translate(
							defaultLanguage,
							l,
							this.getCombinedList(),
							this.doAsList,
							this.redoTranslationsInTableStacker);
				}
			}
		}

		/**
		 * Returns all Languages with the target Language first
		 *
		 * @param langauge The target language.
		 */
		private static Language[] languageInUseFirst(final Language[] languages, final Language languageInUse) {
			final List<Language> ordered = new ArrayList<>();
			ordered.add(languageInUse);
			for (final Language lang : languages) {
				if (lang != languageInUse) {
					ordered.add(lang);
				}
			}
			return ordered.toArray(new Language[0]);
		}
	}

	// INDIVIDUAL STRING AND LIST METHODS
	/**
	 * Detects the language a single text input.
	 *
	 * @param input Text needed to detect the language
	 * @return Language of text input
	 */
	public static Language detectLanguage(final String input) {
		if (alwaysRunDetector) {
			return runLanguageDetectorService(input);
		}
		return runLanguageDetectorOneTime(input);
	}

	/**
	 * Translates a single object using the specified language and retrieves its
	 * translation using the database and language model.
	 *
	 * @param to    The target language for translation. (translated from default
	 *              language)
	 * @param input The Translatable enum or String value to be translated.
	 * @return The translated string or the original object's string representation
	 *         if no translation is available.
	 */
	public static String translate(final Language to, final Object input) {
		return translate(null, to, input, redoTranslationsInTable);
	}

	/**
	 * Translates a single object using the specified language and retrieves its
	 * translation using the database and language model.
	 *
	 * @param from  The source language for translation.
	 * @param to    The target language for translation.
	 * @param input The Translatable enum or String value to be translated.
	 * @return The translated string or the original object's string representation
	 *         if no translation is available.
	 */
	public static String translate(final Language from, final Language to, final Object input) {
		return translate(from, to, input, redoTranslationsInTable);
	}

	/**
	 * Translates a single object using the specified language and retrieves its
	 * translation using the database and language model.
	 *
	 * @param from               The source language for translation.
	 * @param to                 The target language for translation.
	 * @param input              The Translatable enum or String value to be
	 *                           translated.
	 * @param recreateTableEntry If true, table entry will be deleted and re-made.
	 * 
	 * @return The translated string or the original object's string representation
	 *         if no translation is available.
	 */
	public static String translate(final Language from, final Language to, final Object input, final boolean recreateTableEntry) {
		if (input == null) {
			LOGGER.log("Null At Translate"); //$NON-NLS-1$
			return null;
		}
		if (!(input instanceof String) && !(input instanceof Translatable)) {
			LOGGER.log("Not Correct Input Type At Translate"); //$NON-NLS-1$
			return null;
		}
		if (to.isUsable()) {
			String langIN = defaultLanguage.getLocale().getLanguage();
			if (from != null) {
				langIN = from.getLocale().getLanguage();
			}
			String langOUT = to.getLocale().getLanguage();
			try {
				while (runningMaintenance) {
					Thread.sleep(1000);
				}
				if (!langIN.equals(langOUT)) {
					final String modelCode = langIN + "-" + langOUT; //$NON-NLS-1$
					final String translatedFromDatabase = searchDatabase(modelCode, input, recreateTableEntry);
					if (translatedFromDatabase != null) {
						return translatedFromDatabase;
					}
					final String translatedFromModel = doOneString(langIN, langOUT, modelCode, input, true);
					if (translatedFromModel != null) {
						return translatedFromModel;
					}
				}
			} catch (final Exception e) {
				LOGGER.log("Translation Failed For " + langIN + " - " + langOUT, e); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		return input.toString();
	}

	/**
	 * Translates a list of objects using the specified language and retrieves their
	 * translations using the database and language model.
	 *
	 * @param <T>    The type of input objects to translate.
	 * 
	 * @param to     The target language for translation. (translated from default
	 *               language)
	 * @param inputs The list of objects to be translated.
	 * @return A map of input objects to their translations or the original string
	 *         if no translation is available..
	 */
	public static <T> Map<T, String> translate(final Language to, final List<T> inputs) {
		return translate(null, to, inputs, true, redoTranslationsInTable);
	}

	/**
	 * Translates a list of objects using the specified language and retrieves their
	 * translations using the database and language model.
	 *
	 * @param <T>      The type of input objects to translate.
	 * 
	 * @param to       The target language for translation. (translated from default
	 *                 language)
	 * @param inputs   The list of objects to be translated.
	 * @param doAsList Whether to process translations as a list.
	 * @return A map of input objects to their translations or the original string
	 *         if no translation is available..
	 */
	public static <T> Map<T, String> translate(final Language to, final List<T> inputs, final boolean doAsList) {
		return translate(null, to, inputs, doAsList, redoTranslationsInTable);
	}

	/**
	 * Translates a list of objects using the specified language and retrieves their
	 * translations using the database and language model.
	 *
	 * @param <T>    The type of input objects to translate.
	 * 
	 * @param from   The source language for translation.
	 * @param to     The target language for translation.
	 * @param inputs The list of objects to be translated.
	 * @return A map of input objects to their translations or the original string
	 *         if no translation is available..
	 */
	public static <T> Map<T, String> translate(final Language from, final Language to, final List<T> inputs) {
		return translate(null, to, inputs, true, redoTranslationsInTable);
	}

	/**
	 * Translates a list of objects using the specified language and retrieves their
	 * translations using the database and language model.
	 *
	 * @param <T>      The type of input objects to translate.
	 * 
	 * @param from     The source language for translation.
	 * @param to       The target language for translation.
	 * @param inputs   The list of objects to be translated.
	 * @param doAsList Whether to process translations as a list.
	 * @return A map of input objects to their translations or the original string
	 *         if no translation is available..
	 */
	public static <T> Map<T, String> translate(final Language from, final Language to, final List<T> inputs, final boolean doAsList) {
		return translate(null, to, inputs, doAsList, redoTranslationsInTable);
	}

	/**
	 * Translates a list of objects using the specified language and retrieves their
	 * translations using the database and language model.
	 *
	 * @param <T>                 The type of input objects to translate.
	 * 
	 * @param from                The source language for translation.
	 * @param to                  The target language for translation.
	 * @param inputs              The list of objects to be translated.
	 * @param doAsList            Whether to process translations as a list.
	 * @param recreateTableEntrys If true, table entrys will be deleted and re-made.
	 * @return A map of input objects to their translations or the original string
	 *         if no translation is available..
	 */
	@SuppressWarnings("unchecked")
	public static <T> Map<T, String> translate(final Language from, final Language to, final List<T> inputs, final boolean doAsList, final boolean recreateTableEntrys) {
		if (inputs == null) {
			return null;
		}
		Map<T, String> results = new LinkedHashMap<>();
		if (to.isUsable()) {
			String langIN = defaultLanguage.getLocale().getLanguage();
			if (from != null) {
				langIN = from.getLocale().getLanguage();
			}
			String langOUT = to.getLocale().getLanguage();
			try {
				while (runningMaintenance) {
					Thread.sleep(1000);
				}
				if (!langIN.equals(langOUT)) {
					final String modelCode = langIN + "-" + langOUT; //$NON-NLS-1$
					final tSearchResult<T> tSearchResult = searchDatabase(modelCode, inputs, recreateTableEntrys);
					if (tSearchResult.missing.isEmpty()) {
						return tSearchResult.found;
					}
					final Map<T, String> foundResults = tSearchResult.found;
					if (doAsList) {
						final Map<T, String> missingResults = (Map<T, String>) doAsList(
								langIN,
								langOUT,
								modelCode,
								tSearchResult.missing,
								true);
						foundResults.putAll(missingResults);
						return foundResults;
					}
					final Map<T, String> missingResults = (Map<T, String>) doAsStrings(
							langIN,
							langOUT,
							modelCode,
							tSearchResult.missing,
							true);
					foundResults.putAll(missingResults);
					return foundResults;
				}
			} catch (final Exception e) {
				LOGGER.log("Translation Failed For " + langIN + " - " + langOUT, e); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		results = new LinkedHashMap<>();
		for (final T item : inputs) {
			results.put(item, item.toString());
		}
		return results;
	}

	/**
	 * Translates a single Translatable enum value using only the model (no
	 * database).
	 *
	 * @param from  The source language for translation.
	 * @param to    The target language for translation.
	 * @param input The Translatable enum or String value to be translated.
	 * @return The translated string or the original value's string representation
	 *         if no translation is available.
	 */
	public static String translate_OnlyUseModel(final Language to, final Object input) {
		return translate_OnlyUseModel(null, to, input);
	}

	/**
	 * Translates a single Translatable enum value using only the model (no
	 * database).
	 *
	 * @param from  The source language for translation.
	 * @param to    The target language for translation.
	 * @param input The Translatable enum or String value to be translated.
	 * @return The translated string or the original value's string representation
	 *         if no translation is available.
	 */
	public static String translate_OnlyUseModel(final Language from, final Language to, final Object input) {
		if (input == null) {
			LOGGER.log("Null At Translate"); //$NON-NLS-1$
			return null;
		}
		if (!(input instanceof String) && !(input instanceof Translatable)) {
			LOGGER.log("Not Correct Input Type At Translate"); //$NON-NLS-1$
			return null;
		}
		if (to.isUsable()) {
			String langIN = defaultLanguage.getLocale().getLanguage();
			if (from != null) {
				langIN = from.getLocale().getLanguage();
			}
			String langOUT = to.getLocale().getLanguage();
			try {
				while (runningMaintenance) {
					Thread.sleep(1000);
				}
				if (!langOUT.equals(langIN)) {
					final String modelCode = langIN + "-" + langOUT; //$NON-NLS-1$
					final String translatedFromModele = doOneString(langIN, langOUT, modelCode, input, false);
					if (translatedFromModele != null) {
						return translatedFromModele;
					}
				}
			} catch (final Exception e) {
				LOGGER.log("Translation Failed For " + langIN + " - " + langOUT, e); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		return input.toString();
	}

	/**
	 * Translates a list of Translatable enum values using only the model (no
	 * database).
	 * 
	 * @param <T>
	 *
	 * @param to     The target language for translation. (translated from default
	 *               language)
	 * @param inputs The list of Translatable enum values to be translated.
	 * @return A map of input Translatable values to their translations or the
	 *         original string if no translation is available..
	 */
	public static <T> Map<T, String> translate_OnlyUseModel(final Language to, final List<T> inputs) {
		return translate_OnlyUseModel(null, to, inputs, true);
	}

	/**
	 * Translates a list of Translatable enum values using only the model (no
	 * database).
	 * 
	 * @param <T>
	 *
	 * @param from     The source language for translation.
	 * @param to       The target language for translation.
	 * @param inputs   The list of Translatable enum values to be translated.
	 * @param doAsList Whether to process translations as a list.
	 * @return A map of input Translatable values to their translations or the
	 *         original string if no translation is available..
	 */
	public static <T> Map<T, String> translate_OnlyUseModel(final Language from, final Language to, final List<T> inputs, final boolean doAsList) {
		if (inputs == null) {
			return null;
		}
		Map<T, String> results = new LinkedHashMap<>();
		if (to.isUsable()) {
			String langIN = defaultLanguage.getLocale().getLanguage();
			if (from != null) {
				langIN = from.getLocale().getLanguage();
			}
			String langOUT = to.getLocale().getLanguage();
			try {
				while (runningMaintenance) {
					Thread.sleep(1000);
				}
				if (!langIN.equals(langOUT)) {
					final String modelCode = langIN + "-" + langOUT; //$NON-NLS-1$
					if (doAsList) {
						results = doAsList(langIN, langOUT, modelCode, inputs, false);
					}
					if (!results.isEmpty()) {
						return results;
					}
					results = doAsStrings(langIN, langOUT, modelCode, inputs, false);
					if (!results.isEmpty()) {
						return results;
					}
				}
			} catch (final Exception e) {
				LOGGER.log("Translation Failed For " + langIN + " - " + langOUT, e); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		results = new LinkedHashMap<>();
		for (final T item : inputs) {
			results.put(item, item.toString());
		}
		return results;
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Database
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Lookup
	/**
	 * Represents the results of a database search operation.
	 */
	private static class tSearchResult<T> {
		/**
		 * 
		 * A map containing successfully found translations, keyed by input objects and
		 * valued by their translations.
		 * 
		 * @param <T> The type of input objects to translate.
		 * 
		 */
		final Map<T, String> found;
		/**
		 * A list of inputs that were not found in the database.
		 */
		final List<String> missing;

		/**
		 * Constructs a new search result with the found and missing entries.
		 *
		 * @param found   The map of found translations.
		 * @param missing The list of missing entries.
		 */
		public tSearchResult(final Map<T, String> found, final List<String> missing) {
			this.found = found;
			this.missing = missing;
		}
	}

	/**
	 * Searches the database for a list translations of String or Translatable using
	 * the specified model code.
	 *
	 * @param <T>       The type of input objects to search for.
	 * @param modelCode The identifier of the translation model to use.
	 * @param inputs    A list of input String or Translatable to translate.
	 * @return A tSearchResult object containing found translations and missing
	 *         entries.
	 * @throws Exception If an error occurs during the database lookup or
	 *                   processing.
	 */
	private static <T> tSearchResult<T> searchDatabase(final String modelCode, final List<T> inputs, final boolean redoTranslations) throws Exception {
		if (inputs == null) {
			LOGGER.log("Null At Database Search"); //$NON-NLS-1$
			return null;
		}
		final Map<T, String> found = new LinkedHashMap<>();
		final List<String> missing = new ArrayList<>();
		final List<?> list = inputs;
		for (int i = 0; i < list.size(); i++) {
			if (!(list.get(i) instanceof String) && !(list.get(i) instanceof Translatable)) {
				LOGGER.log("Not Correct Input Type At Database Search, Removed"); //$NON-NLS-1$
				continue;
			}
			final String key = (list.get(i) instanceof String ? (String) list.get(i) : (list.get(i)).toString());
			if (key.isBlank()) {
				continue;
			}
			LOGGER.log("Searching Database For Listed Word/Phrase: " + modelCode + " : " + key); //$NON-NLS-1$ //$NON-NLS-2$
			final TranslatorEntity translated = TranslatorEntity.getTranslation(modelCode, key);
			if (translated == null || translated.getTranslation() == null) {
				missing.add(key);
			} else if (redoTranslations) {
				TranslatorEntity.delete(modelCode, key);
				LOGGER.log("Deleted Word/Phrase: " + modelCode + " : " + key); //$NON-NLS-1$ //$NON-NLS-2$
				missing.add(key);
			} else {
				LOGGER.log("Found Database Translation For Word/Phrase: " + modelCode + " : " + translated.getTranslation()); //$NON-NLS-1$ //$NON-NLS-2$
				found.put(inputs.get(i), translated.getTranslation());
			}
		}
		return new tSearchResult<>(
				found,
				missing);
	}

	/**
	 * Searches the database for translations of a String or Translatable using the
	 * specified model code.
	 *
	 * @param modelCode The identifier of the translation model to use.
	 * @param inputs    A list of input String or Translatable to translate.
	 * @return A tSearchResult object containing found translations and missing
	 *         entries.
	 * @throws Exception If an error occurs during the database lookup or
	 *                   processing.
	 */
	private static String searchDatabase(final String modelCode, final Object input, final boolean redoTranslations) throws Exception {
		if (input == null) {
			LOGGER.log("Null At Database Search"); //$NON-NLS-1$
			return null;
		}
		if (!(input instanceof String) && !(input instanceof Translatable)) {
			LOGGER.log("Not Correct Input Type At Database Search"); //$NON-NLS-1$
			return null;
		}
		final String key = (input instanceof String ? (String) input : (input).toString());
		LOGGER.log("Searching Database For Single Word/Phrase: " + modelCode + " : " + key); //$NON-NLS-1$ //$NON-NLS-2$
		if (key.isBlank()) {
			return null;
		}
		final TranslatorEntity translated = TranslatorEntity.getTranslation(modelCode, key);
		if (translated == null || translated.getTranslation() == null) {
			return null;
		} else if (redoTranslations) {
			LOGGER.log("Deleted Word/Phrase: " + modelCode + " : " + key); //$NON-NLS-1$ //$NON-NLS-2$
			TranslatorEntity.delete(modelCode, key);
			return null;
		}
		LOGGER.log("Found Database Translation For Word/Phrase: " + modelCode + " : " + translated.getTranslation()); //$NON-NLS-1$ //$NON-NLS-2$
		return translated.getTranslation();
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Private
	/**
	 * Translates a list of inputs using the specified translation model and
	 * language pair.
	 *
	 * @param <T>           The type of input objects to translate.
	 * @param langIN        The source language code.
	 * @param langOUT       The target language code.
	 * @param modelCode     The identifier of the translation model to use.
	 * @param inputs        A list of input objects (strings or Translatable
	 *                      instances) to translate.
	 * @param doTranslation Whether to save the translation result back into the
	 *                      database.
	 * @return A map containing translated strings or "Model_Translation_Failed" if
	 *         the translation fails, keyed by the original input objects.
	 * @throws Exception If an error occurs during translation or processing.
	 */
	private static <T> Map<T, String> doAsList(final String langIN, final String langOUT, final String modelCode, final List<T> inputs, final boolean doTranslation) throws Exception {
		final Map<T, String> results = new LinkedHashMap<>();
		final List<List<T>> chunks = splitIntoChunks(inputs, modelCharLimit);
		for (final List<T> chunk : chunks) {
			final List<T> chunkedList = chunk;
			if (chunkedList.size() > 0) {
				final String jsonInput = new ObjectMapper().writeValueAsString(chunkedList);
				LOGGER.log("Translating List From Model: " + modelCode + " " + jsonInput); //$NON-NLS-1$ //$NON-NLS-2$
				final String encoded = Base64.getEncoder().encodeToString(jsonInput.getBytes(StandardCharsets.UTF_8));
				final List<String> outputs = runCTranslate2(langIN, langOUT, encoded);
				LOGGER.log("Translation Results: " + new ObjectMapper().writeValueAsString(outputs)); //$NON-NLS-1$
				if (outputs.size() == chunkedList.size()) {
					for (int i = 0; i < chunkedList.size(); i++) {
						if (!(chunkedList.get(i) instanceof String) && !(chunkedList.get(i) instanceof Translatable)) {
							LOGGER.log("Not Correct Input Type At Do Translate, Removed"); //$NON-NLS-1$
							continue;
						}
						final String key = appContentOrStringAsString(chunkedList.get(i));
						if (key.isBlank()) {
							continue;
						}
						final String output = ((outputs.get(i) != null) && !outputs.get(i).isBlank()) ? outputs
								.get(i) : "Model_Translation_Failed"; //$NON-NLS-1$
						String formatted = output;
						results.putIfAbsent(inputs.get(i), formatted);
						if (doTranslation) {
							TranslatorEntity.save(modelCode, key, formatted);
						}
					}
				}
			}
		}
		return results;
	}

	/**
	 * Translates a list of inputs one by one using the specified translation model
	 * and language pair.
	 *
	 * @param <T>            The type of input objects to translate.
	 * @param langIN         The source language code.
	 * @param langOUT        The target language code.
	 * @param modelCode      The identifier of the translation model to use.
	 * @param inputs         A list of input objects (strings or Translatable
	 *                       instances) to translate.
	 * @param doTranslateion Whether to save the translation result back into the
	 *                       database.
	 * @return A map containing translated strings or "Model_Translation_Failed" if
	 *         the translation fails, keyed by the original input objects.
	 * @throws Exception If an error occurs during translation or processing.
	 */
	private static <T> Map<T, String> doAsStrings(final String langIN, final String langOUT, final String modelCode, final List<T> inputs, final boolean doTranslateion) throws Exception {
		final Map<T, String> results = new LinkedHashMap<>();
		final List<List<T>> chunks = splitIntoChunks(inputs, modelCharLimit);
		for (final List<T> chunk : chunks) {
			final List<T> chunkedList = chunk;
			if (chunkedList.size() > 0) {
				for (int i = 0; i < chunkedList.size(); i++) {
					if (!(chunkedList.get(i) instanceof String) && !(chunkedList.get(i) instanceof Translatable)) {
						LOGGER.log("Not Correct Input Type AtDo Translate, Removed"); //$NON-NLS-1$
						continue;
					}
					final String key = appContentOrStringAsString(chunkedList.get(i));
					if (key.isBlank()) {
						continue;
					}
					LOGGER.log("Translating String From Model: " + modelCode + " : " + key); //$NON-NLS-1$ //$NON-NLS-2$
					final List<String> result = runCTranslate2(langIN, langOUT, key);
					final String translatedFromModel = result.isEmpty() ? null : result.get(0);
					if ((translatedFromModel == null) || translatedFromModel.isBlank()) {
						results.put(inputs.get(i), "Model_Translation_Failed"); //$NON-NLS-1$
					} else {
						String formatted = translatedFromModel;
						results.putIfAbsent(inputs.get(i), formatted);
						if (doTranslateion) {
							TranslatorEntity.save(modelCode, key, formatted);
						}
						LOGGER.log("Translated Word/Phrase Output: " + modelCode + " : " + formatted); //$NON-NLS-1$ //$NON-NLS-2$
					}
				}
			}
		}
		return results;
	}

	/**
	 * Translates a single input using the specified translation model and language
	 * pair.
	 *
	 * @param langIN         The source language code.
	 * @param langOUT        The target language code.
	 * @param modelCode      The identifier of the translation model to use.
	 * @param input          An input object (string or Translatable instance) to
	 *                       translate.
	 * @param doTranslateion Whether to save the translation result back into the
	 *                       database.
	 * @return The translated string, or "Model_Translation_Failed" if the
	 *         translation fails.
	 * @throws Exception If an error occurs during translation or processing.
	 */
	private static String doOneString(final String langIN, final String langOUT, final String modelCode, final Object input, final boolean doTranslateion) throws Exception {
		if (input == null) {
			LOGGER.log("Null At Translate"); //$NON-NLS-1$
			return null;
		}
		if (!(input instanceof String) && !(input instanceof Translatable)) {
			LOGGER.log("Not Correct Input Type At Translate"); //$NON-NLS-1$
			return null;
		}
		final String key = appContentOrStringAsString(input);
		if (key.isBlank()) {
			return null;
		}
		String placeheld = key;
		LOGGER.log("Translating String From Model: " + modelCode + " : " + key); //$NON-NLS-1$ //$NON-NLS-2$
		final List<String> result = runCTranslate2(langIN, langOUT, placeheld);
		final String translatedFromModel = result.isEmpty() ? null : result.get(0);
		if ((translatedFromModel != null) && !translatedFromModel.isBlank()) {
			String formatted = translatedFromModel;
			if (doTranslateion) {
				TranslatorEntity.save(modelCode, key, formatted);
			}
			LOGGER.log("Translated Word/Phrase From Model: " + modelCode + " : " + formatted); //$NON-NLS-1$ //$NON-NLS-2$
			return formatted;
		}
		return "Model_Translation_Failed"; //$NON-NLS-1$
	}

	/**
	 * Splits a list of strings into chunks where total characters per chunk ≤
	 * maxChunkChars.
	 * 
	 * @param <T>
	 *
	 * @param list          list of strings
	 * @param maxChunkChars max characters per chunk
	 * @return list of chunks
	 */
	private static <T> List<List<T>> splitIntoChunks(List<T> list, int maxCharsPerChunk) {
		List<List<T>> chunks = new ArrayList<>();
		List<T> currentChunk = new ArrayList<>();
		int currentCharCount = 0;
		for (T item : list) {
			if (currentCharCount + item.toString().length() > maxCharsPerChunk && !currentChunk.isEmpty()) {
				chunks.add(
						new ArrayList<>(
								currentChunk));
				currentChunk.clear();
				currentCharCount = 0;
			}
			currentChunk.add(item);
			currentCharCount += item.toString().length();
		}
		if (!currentChunk.isEmpty()) {
			chunks.add(currentChunk);
		}
		return chunks;
	}

	/**
	 * Extracts a string value from either a String object or an Translatable
	 * instance.
	 *
	 * @param o The object to extract the string value from.
	 * @return The string value, or an empty string if the object is null or not a
	 *         valid type.
	 */
	private static String appContentOrStringAsString(final Object o) {
		if (o == null) {
			return ""; //$NON-NLS-1$
		}
		if (o instanceof String) {
			return (String) o;
		}
		return (o).toString();
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Format/Parse
	/**
	 * Formats a numeric value according to the specified language's locale.
	 *
	 * @param language The language whose locale will be used for formatting.
	 * @param value    The numeric string to format (can be integer or decimal).
	 * @return A formatted string representation of the number in the given locale.
	 * @throws Exception If parsing or formatting fails.
	 */
	public static String formatNumber(Language language, String value) throws Exception {
		Locale locale = language.getLocale();
		NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
		if (value.contains(".")) { //$NON-NLS-1$
			double num = Double.parseDouble(value);
			return numberFormat.format(num);
		}
		long num = Long.parseLong(value);
		return numberFormat.format(num);
	}

	/**
	 * Formats a numeric value according to the specified language's locale.
	 *
	 * @param language The language whose locale will be used for formatting.
	 * @param value    The numeric value to format.
	 * @return A formatted string representation of the number in the given locale.
	 * @throws Exception If formatting fails.
	 */
	public static String formatNumber(Language language, Number value) throws Exception {
		Locale locale = language.getLocale();
		NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
		return numberFormat.format(value);
	}

	/**
	 * Formats a timestamp string according to the specified language's locale.
	 *
	 * @param language  The language whose locale will be used for formatting.
	 * @param timestamp A timestamp.
	 * @return A formatted localized date and time string.
	 * @throws Exception If parsing or formatting fails.
	 */
	public static String formatTimestamp(Language language, Timestamp timestamp) {
		LocalDateTime dateTime = timestamp.toLocalDateTime(); // no string conversion
		return formatLocalDateTime(language, dateTime);
	}

	/**
	 * Formats a timestamp string according to the specified language's locale.
	 *
	 * @param language  The language whose locale will be used for formatting.
	 * @param timestamp A string representation of the timestamp (assumed to be in
	 *                  ISO format).
	 * @return A formatted localized date and time string.
	 * @throws Exception If parsing or formatting fails.
	 */
	public static String formatTimestamp(Language language, String timestamp) {
		// First, parse into LocalDateTime using the known format of
		// Timestamp.toString()
		DateTimeFormatter parser = DateTimeFormatter
				.ofPattern("yyyy-MM-dd HH:mm:ss[.[SSSSSSSSS][SSSSSSSS][SSSSSSS][SSSSSS][SSSSS][SSSS][SSS][SS][S]]"); //$NON-NLS-1$
		LocalDateTime dateTime = LocalDateTime.parse(timestamp, parser);
		return formatLocalDateTime(language, dateTime);
	}

	public static String formatLocalDateTime(Language language, LocalDateTime dateTime) {
		Locale locale = language.getLocale();
		DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale);
		return dateTime.format(formatter);
	}

	/**
	 * Formats a timestamp according to the specified language's locale.
	 *
	 * @param language  The language whose locale will be used for formatting.
	 * @param timestamp The timestamp to convert.
	 * @return A formatted localized month-year string.
	 */
	public static String formatTimestamp_MonthYear(final Language language, final Timestamp timestamp) {
		final YearMonth yearMonth = YearMonth.from(timestamp.toLocalDateTime());
		final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM yyyy").withLocale(language.getLocale()); //$NON-NLS-1$
		return yearMonth.format(formatter);
	}

	/**
	 * Formats a date string according to the specified language's locale.
	 *
	 * @param language The language whose locale will be used for formatting.
	 * @param dateStr  A date string, timestamp string, or any string containing a
	 *                 date.
	 * @return A formatted localized date string, or null if parsing fails.
	 */
	public static String formatDate(Language language, String dateStr) {
		if (dateStr == null || dateStr.isBlank())
			return null;
		// Extract the date part (before space or 'T')
		String datePart = dateStr;
		if (dateStr.contains(" ")) { //$NON-NLS-1$
			datePart = dateStr.substring(0, dateStr.indexOf(' '));
		} else if (dateStr.contains("T")) { //$NON-NLS-1$
			datePart = dateStr.substring(0, dateStr.indexOf('T'));
		}
		Locale locale = language.getLocale();
		DateTimeFormatter outputFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
		LocalDate localDate = null;
		// Add "yyyy-MM-dd" (standard for Timestamp date part) and keep others
		String[] formats = { "yyyy-MM-dd", "MM/dd/yyyy", "dd/MM/yyyy", "yyyy/MM/dd" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		for (String format : formats) {
			try {
				localDate = LocalDate.parse(datePart, DateTimeFormatter.ofPattern(format));
				return localDate.format(outputFormatter);
			} catch (DateTimeParseException e) {
				LOGGER.log("Parse Failed, Continuing", e, true); //$NON-NLS-1$
			}
		}
		return null;
	}

	/**
	 * Formats a time string according to the specified language's locale.
	 *
	 * @param language The language whose locale will be used for formatting.
	 * @param timeStr  A time string, timestamp string, or any string containing a
	 *                 time.
	 * @return A formatted localized time string, or null if parsing fails.
	 */
	public static String formatTime(Language language, String timeStr) {
		if (timeStr == null || timeStr.isBlank())
			return null;
		// Extract the time part (after first space or after 'T')
		String timePart = timeStr;
		if (timeStr.contains(" ")) { //$NON-NLS-1$
			int spaceIdx = timeStr.indexOf(' ');
			timePart = timeStr.substring(spaceIdx + 1);
		} else if (timeStr.contains("T")) { //$NON-NLS-1$
			int tIdx = timeStr.indexOf('T');
			timePart = timeStr.substring(tIdx + 1);
		}
		// Remove milliseconds if present (they cause issues with some formatters)
		if (timePart.contains(".")) { //$NON-NLS-1$
			timePart = timePart.substring(0, timePart.indexOf('.'));
		}
		Locale locale = language.getLocale();
		DateTimeFormatter outputFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale);
		LocalTime localTime = null;
		String[] formats = { "HH:mm:ss", "HH:mm", "HH:mm:ss.SSS", "HH:mm:ss.SSSSSS", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				"hh:mm a", "hh:mm:ss a", "KK:mm a", "KK:mm:ss a", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				"H:mm", "h:mm a" //$NON-NLS-1$ //$NON-NLS-2$
		};
		for (String format : formats) {
			try {
				localTime = LocalTime.parse(timePart, DateTimeFormatter.ofPattern(format));
				return localTime.format(outputFormatter);
			} catch (DateTimeParseException e) {
				LOGGER.log("Parse Failed, Continuing", e, true); //$NON-NLS-1$
			}
		}
		return null;
	}

	/**
	 * Formats a currency string according to the specified language's locale.
	 *
	 * @param language    The language whose locale will be used for formatting.
	 * @param currencyStr A string representing the currency value.
	 * @return A formatted localized currency string.
	 * @throws Exception If parsing or formatting fails.
	 */
	public static String formatCurrency(Language language, String currencyStr) throws Exception {
		Locale locale = language.getLocale();
		NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(locale);
		String numberPart = currencyStr.replaceAll("[^\\d.,]", "").trim(); //$NON-NLS-1$ //$NON-NLS-2$
		double amount = Double.parseDouble(numberPart.replace(",", "")); //$NON-NLS-1$ //$NON-NLS-2$
		return currencyFormat.format(amount);
	}

	/**
	 * Parses a date string according to the specified language's locale.
	 *
	 * @param language   The language whose locale will be used for parsing.
	 * @param dateString A string representation of the date to parse (may also
	 *                   contain time).
	 * @return A parsed Date object, or null if parsing fails.
	 */
	public static Date parseDate(final Language language, final String dateString) {
		if (dateString == null || dateString.isBlank())
			return null;
		// Extract the date part (before space or 'T')
		String datePart = dateString;
		if (dateString.contains(" ")) { //$NON-NLS-1$
			datePart = dateString.substring(0, dateString.indexOf(' '));
		} else if (dateString.contains("T")) { //$NON-NLS-1$
			datePart = dateString.substring(0, dateString.indexOf('T'));
		}
		final Locale locale = language.getLocale();
		final List<String> patterns = Arrays.asList(
				"yyyy-MM-dd", //$NON-NLS-1$
				"yyyy/MM/dd", //$NON-NLS-1$
				"yyyy_MM_dd", //$NON-NLS-1$
				"dd-MM-yyyy", //$NON-NLS-1$
				"dd/MM/yyyy", //$NON-NLS-1$
				"dd.MM.yyyy", //$NON-NLS-1$
				"MM/dd/yyyy", //$NON-NLS-1$
				"MM-dd-yyyy", //$NON-NLS-1$
				"MM.dd.yyyy", //$NON-NLS-1$
				"yyyyMMdd", //$NON-NLS-1$
				"dd MMM yyyy", //$NON-NLS-1$
				"dd MMMM yyyy", //$NON-NLS-1$
				"MMM dd, yyyy", //$NON-NLS-1$
				"MMMM dd, yyyy", //$NON-NLS-1$
				"EEE, dd MMM yyyy", //$NON-NLS-1$
				"EEEE, dd MMMM yyyy", //$NON-NLS-1$
				"dd/MM/yy", //$NON-NLS-1$
				"MM/dd/yy", //$NON-NLS-1$
				"yyMMdd", //$NON-NLS-1$
				"MMddyy", //$NON-NLS-1$
				"ddMMyy", //$NON-NLS-1$
				"d/M/yyyy", //$NON-NLS-1$
				"M/d/yyyy", //$NON-NLS-1$
				"d.M.yyyy", //$NON-NLS-1$
				"yyyy年M月d日", //$NON-NLS-1$
				"MMM yyyy", //$NON-NLS-1$
				"MMMM yyyy", //$NON-NLS-1$
				"yyyy-MM", //$NON-NLS-1$
				"QQQ yyyy"); //$NON-NLS-1$
		for (String pattern : patterns) {
			final SimpleDateFormat sdf = new SimpleDateFormat(
					pattern,
					locale);
			sdf.setLenient(false);
			try {
				return sdf.parse(datePart);
			} catch (final ParseException e) {
				LOGGER.log("Parse Failed, Continuing", e, true); //$NON-NLS-1$
			}
		}
		LOGGER.log("Parse Error: Unable to parse date: " + dateString); //$NON-NLS-1$
		return null;
	}

	/**
	 * /** Parses a time string using locale-aware formatters and returns a Time
	 * object.
	 *
	 * @param language The language whose locale will be used for parsing.
	 * @param timeStr  A time string to parse (may also contain date part).
	 * @return A parsed Time object, or null if parsing fails.
	 */
	public static Time parseTime(final Language language, String timeStr) {
		if (timeStr == null || timeStr.isBlank()) {
			LOGGER.log("Null or empty time string"); //$NON-NLS-1$
			return null;
		}
		// Extract the time part (after space or 'T')
		String timePart = timeStr;
		if (timeStr.contains(" ")) { //$NON-NLS-1$
			int spaceIdx = timeStr.indexOf(' ');
			timePart = timeStr.substring(spaceIdx + 1);
		} else if (timeStr.contains("T")) { //$NON-NLS-1$
			int tIdx = timeStr.indexOf('T');
			timePart = timeStr.substring(tIdx + 1);
		}
		// Remove milliseconds if present (they cause issues with some formatters)
		if (timePart.contains(".")) { //$NON-NLS-1$
			timePart = timePart.substring(0, timePart.indexOf('.'));
		}
		// Normalize: replace common delimiters with ':', remove non-time chars
		String cleaned = timePart.replaceAll("[~\\-.,;]+", ":") //$NON-NLS-1$ //$NON-NLS-2$
				.replaceAll("\\s*:\\s*", ":") //$NON-NLS-1$ //$NON-NLS-2$
				.replaceAll("[^0-9:APMapm]", "") //$NON-NLS-1$ //$NON-NLS-2$
				.trim()
				.toUpperCase();
		if (cleaned.isEmpty() || !cleaned.matches(".*\\d.*")) { //$NON-NLS-1$
			LOGGER.log("No time digits found in: " + timeStr); //$NON-NLS-1$
			return null;
		}
		List<DateTimeFormatter> formatters = buildLocaleAwareFormatters(language);
		// Add more fallback formatters for robustness
		formatters.addAll(
				Arrays.asList(
						DateTimeFormatter.ofPattern("HH:mm:ss"), //$NON-NLS-1$
						DateTimeFormatter.ofPattern("HH:mm"), //$NON-NLS-1$
						DateTimeFormatter.ofPattern("h:mm a"), //$NON-NLS-1$
						DateTimeFormatter.ofPattern("hh:mm:ss a") //$NON-NLS-1$
				));
		for (DateTimeFormatter fmt : formatters) {
			try {
				DateTimeFormatter strict = fmt.withResolverStyle(ResolverStyle.STRICT);
				TemporalAccessor parsed = strict.parse(cleaned);
				if (parsed.isSupported(ChronoField.HOUR_OF_DAY)) {
					LocalTime lt = LocalTime.from(parsed);
					return Time.valueOf(lt);
				}
			} catch (DateTimeException ignored) {
				LOGGER.log("Time Parse Error", ignored, true); //$NON-NLS-1$
			}
		}
		LOGGER.log("Could not parse time: " + cleaned + " (original: " + timeStr + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		return null;
	}

	/**
	 * Builds a list of locale-aware DateTimeFormatters for parsing times.
	 *
	 * @param locale The locale used to determine the order and pattern of
	 *               formatters.
	 * @return A list of DateTimeFormatter objects specific to the given locale.
	 */
	private static List<DateTimeFormatter> buildLocaleAwareFormatters(Language language) {
		final List<DateTimeFormatter> formatters = new ArrayList<>();
		// Locale-specific formatter order (12-hour formats first for US, 24-hour first
		// for others)
		if (Language.USA.equals(language) || Language.CANADA.equals(language)) {
			formatters.addAll(
					Arrays.asList(
							DateTimeFormatter.ofPattern("h:mm a", language.getLocale()), //$NON-NLS-1$
							DateTimeFormatter.ofPattern("hh:mm a", language.getLocale()), //$NON-NLS-1$
							DateTimeFormatter.ofPattern("h:mma", language.getLocale()), //$NON-NLS-1$
							DateTimeFormatter.ofPattern("hh:mma", language.getLocale()), //$NON-NLS-1$
							DateTimeFormatter.ofPattern("HH:mm", language.getLocale()), //$NON-NLS-1$
							DateTimeFormatter.ofPattern("H:mm", language.getLocale()))); //$NON-NLS-1$
		} else {
			formatters.addAll(
					Arrays.asList(
							DateTimeFormatter.ofPattern("HH:mm", language.getLocale()), //$NON-NLS-1$
							DateTimeFormatter.ofPattern("H:mm", language.getLocale()), //$NON-NLS-1$
							DateTimeFormatter.ofPattern("HH:mm:ss", language.getLocale()), //$NON-NLS-1$
							DateTimeFormatter.ofPattern("hh:mm a", language.getLocale()), //$NON-NLS-1$
							DateTimeFormatter.ofPattern("h:mm a", language.getLocale()))); //$NON-NLS-1$
		}
		return formatters;
	}

	/**
	 * Determines the time of day based on current system time in the specified
	 * language's zone.
	 *
	 * @param language The language whose time zone will be used.
	 * @return An integer representing the time of day: 0 for morning, 1 for
	 *         afternoon, 2 for evening.
	 */
	public static int determinTimeOfDay(final Language language) {
		final ZonedDateTime zonedDateTime = ZonedDateTime.now(language.getZoneId());
		final LocalTime now = zonedDateTime.toLocalTime();
		if (now.isBefore(LocalTime.NOON)) {
			return 0;
		}
		if (now.isBefore(LocalTime.of(18, 0))) {
			return 1;
		}
		return 2;
	}

	/**
	 * Tests every translation method using all settings, full translator live
	 * enviroment test.
	 * 
	 */
	private static boolean liveTest() {
		try {
			TranslateStacker tester = new TranslateStacker().addAll(TestContent.values());
			//
			tester.feedTranslatorDatabase();
			tester.setReCreate(true);
			tester.feedTranslatorDatabase();
			tester.setReCreate(false);
			tester.setProcessAsList(false);
			tester.feedTranslatorDatabase();
			tester.setProcessAsList(true);
			//
			tester.setReCreate(true);
			tester.setProcessAsList(false);
			tester.feedTranslatorDatabase();
			tester.setProcessAsList(true);
			tester.setReCreate(false);
			//
			int i = 0;
			for (Language l : Language.usableValues()) {
				if (i == 3) {
					break;
				}
				i++;
				//
				LOGGER.log(Translator.translate(l, TestContent.Hello));
				LOGGER.log(Translator.translate(l, TestContent.Hello.toString()));
				//
				LOGGER.log(
						Translator.translate(l, Arrays.asList(new Translatable[] { TestContent.Hello, TestContent.Hello }))
								.toString());
				LOGGER.log(
						Translator
								.translate(
										l,
										Arrays.asList(
												new String[] { TestContent.Hello.toString(), TestContent.Hello.toString() }))
								.toString());
				//
				LOGGER.log(Translator.translate_OnlyUseModel(l, TestContent.Hello));
				LOGGER.log(Translator.translate_OnlyUseModel(l, TestContent.Hello.toString()));
				//
				LOGGER.log(
						Translator
								.translate_OnlyUseModel(
										l,
										Arrays.asList(new Translatable[] { TestContent.Hello, TestContent.Hello }))
								.toString());
				LOGGER.log(
						Translator
								.translate_OnlyUseModel(
										l,
										Arrays.asList(
												new String[] { TestContent.Hello.toString(), TestContent.Hello.toString() }))
								.toString());
				//
				Map<Object, String> t = null;
				//
				tester.setProcessAsList(false);
				t = tester.translate(l);
				LOGGER.log(t.toString());
				tester.setProcessAsList(true);
				t = tester.translate(l);
				LOGGER.log(t.toString());
				tester.setProcessAsList(false);
				t = tester.translate_ModelOnly(l);
				LOGGER.log(t.toString());
				tester.setProcessAsList(true);
				t = tester.translate_ModelOnly(l);
				LOGGER.log(t.toString());
				tester.setProcessAsList(false);
				t = tester.translateAndFeedTranslatorDatabase(l);
				LOGGER.log(t.toString());
				tester.setProcessAsList(true);
				t = tester.translateAndFeedTranslatorDatabase(l);
				LOGGER.log(t.toString());
				//
				//
				tester.setReCreate(true);
				//
				tester.setProcessAsList(false);
				t = tester.translate(l);
				LOGGER.log(t.toString());
				tester.setProcessAsList(true);
				t = tester.translate(l);
				LOGGER.log(t.toString());
				tester.setProcessAsList(false);
				t = tester.translate_ModelOnly(l);
				LOGGER.log(t.toString());
				tester.setProcessAsList(true);
				t = tester.translate_ModelOnly(l);
				LOGGER.log(t.toString());
				tester.setProcessAsList(false);
				t = tester.translateAndFeedTranslatorDatabase(l);
				LOGGER.log(t.toString());
				tester.setProcessAsList(true);
				t = tester.translateAndFeedTranslatorDatabase(l);
				LOGGER.log(t.toString());
				//
				tester.setReCreate(false);
			}
			LOGGER.log("Test Complete"); //$NON-NLS-1$
		} catch (Exception e) {
			LOGGER.log("Test Failed", e); //$NON-NLS-1$
			return false;
		}
		return true;
	}
}
