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
import java.lang.reflect.Field;
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
import java.text.Bidi;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import HibernateSupport.HibernateTranslatorEntity;
import MongoDBSupport.MongoDBTranslatorEntity;
import didzapp.T_Log;
import didzapp.HF_Translator.TranslatorContent.FolderName;
import didzapp.HF_Translator.TranslatorContent.Translatable;
import didzapp.HF_Translator.TranslatorResourcePaths.ToFlatpickr;
import didzapp.HF_Translator.TranslatorResourcePaths.ToPyFiles;

public class Translator {
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
	private static final String[] MODEL_FILES_TO_DOWNLOAD = { "config.json", "vocab.json", "tokenizer_config.json", "generation_config.json", "metadata.json", "pytorch_model.bin", "source.spm", "target.spm", "tf_model.h5", "flax_model.msgpack", "README.md", ".gitattributes" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$//$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$
	//
	// Language detector background service
	private static final Object processLock = new Object();
	private static boolean alwaysRunDetector = true;
	private static Process persistentProcess = null;
	private static BufferedWriter persistentWriter = null;
	private static BufferedReader persistentReader = null;
	private static Thread thread;
	//
	// Path and platform (id if duplicate file paths exist)
	static String modelPath = null;
	static String sharedPathString = null;
	static String mainPathString = null;
	static final String tempPathString = "/temp_files"; //$NON-NLS-1$
	static String siteOrAppId = "hf-translator"; //$NON-NLS-1$
	static Platform platformRuningOn = null;
	//
	// Config, maintenance and global settings
	static boolean runningMaintenance = false;
	static boolean doFlatpickerFiles = true;
	static boolean universalTranslations = false;
	public static DetectionUtils.Framework framework = null;
	public static DetectionUtils.Database database = null;
	//
	//
	// Values
	// Folder for model storage
	static FolderName languageModelFolder;
	// Default language
	static Locale defaultLanguage;
	// Working languages
	static List<Locale> disabledLanguages = new ArrayList<>();
	// Selected languages
	static List<Locale> selectedLanguages = new ArrayList<>();
	// Languages Links
	static Map<String, List<String>> languagesLinks = new HashMap<>();

	/**
	 * Gets the working languages as locales.
	 *
	 * @return all languages if universal translations is enabled, otherwise all
	 *         languages except the disabled ones.
	 */
	public static Locale[] getSelectedAndWorkingLanguages() {
		return selectedLanguages.stream().filter(locale -> !disabledLanguages.contains(locale)).toArray(Locale[]::new);
	}

	/**
	 * Gets the working language tags as strings.
	 *
	 * @return all languages if universal translations is enabled, otherwise all
	 *         languages except the disabled ones.
	 */
	public static String[] getSelectedAndWorkingLanguagesAsTags() {
		return selectedLanguages.stream()
				.filter(locale -> !disabledLanguages.contains(locale))
				.map(Locale::toLanguageTag)
				.toArray(String[]::new);
	}

	/**
	 * Gets the working language names as strings.
	 *
	 * @return all languages if universal translations is enabled, otherwise all
	 *         languages except the disabled ones.
	 */
	public static String[] getSelectedAndWorkingLanguageNames(final Locale displayLocale) {
		return selectedLanguages.stream()
				.filter(locale -> !disabledLanguages.contains(locale))
				.map(locale -> locale.getDisplayName(displayLocale))
				.toArray(String[]::new);
	}

	/**
	 * Gets the working language names as strings.
	 *
	 * @return all languages if universal translations is enabled, otherwise all
	 *         languages except the disabled ones.
	 */
	public static String[] getSelectedAndWorkingLanguageTranslatedNames() {
		return selectedLanguages.stream()
				.filter(locale -> !disabledLanguages.contains(locale))
				.map(locale -> locale.getDisplayName(locale))
				.toArray(String[]::new);
	}

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
	public enum Platform {
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

	/**
	 * Recursively deletes a folder and all its contents.
	 *
	 * @param folder Path to the directory to delete
	 * @return True if deletion succeeded, false otherwise
	 */
	static boolean delete_Folder(final Path folder) {
		if (!Files.exists(folder)) {
			return true;
		}
		try (Stream<Path> paths = Files.walk(folder)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.delete(path);
				} catch (final IOException e) {
					throw new RuntimeException(
							"Failed to delete: " + path, //$NON-NLS-1$
							e);
				}
			});
			return true;
		} catch (final IOException e) {
			T_Log.log("Failed to delete Folder", e); //$NON-NLS-1$
			return false;
		}
	}

	/**
	 * Deletes a single file if it exists.
	 *
	 * @param file Path to the file to delete
	 * @return True if deletion succeeded, false otherwise
	 */
	static boolean delete_File(final Path file) {
		try {
			return Files.deleteIfExists(file);
		} catch (final IOException e) {
			T_Log.log("Failed to delete File", e); //$NON-NLS-1$
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
					T_Log.log("Created Directory: " + path); //$NON-NLS-1$
					return path;
				}
				T_Log.log("Failed To Create Directory: " + path); //$NON-NLS-1$
			} catch (final SecurityException e) {
				T_Log.log("Permission Error While Creating Directory: " + path, e); //$NON-NLS-1$
			} catch (final Exception e) {
				T_Log.log("Unexpected Error While Creating Directory: " + path, e); //$NON-NLS-1$
			}
		} else {
			T_Log.log("Invalid Or Empty Directory Path: " + path); //$NON-NLS-1$
		}
		return null;
	}

	/**
	 * Initializes the translation system with default language settings, selected
	 * languages, Hibernate configuration, model storage path, and content classes.
	 * It prepares necessary resources for translation.
	 *
	 * @param frameworkObject            Object used to manage framework
	 *                                   interactions
	 *
	 * @param config_path_or_string      Framework config file path
	 *
	 * @param default_language           Default language to be used (can be null:
	 *                                   default (English))
	 *
	 * @param language_selection         Array of supported languages; if null, all
	 *                                   languages are used
	 *
	 * @param run_language_detector      If true, the language detector model will
	 *                                   remain in memory and accessable for faster
	 *                                   detecting
	 *
	 * @param universal_translation_mode If true, the language detector model will
	 *                                   remain in memory and accessable for faster
	 *                                   detecting
	 *
	 * @param model_reset                If true, re-downloads all model files,
	 *                                   remakes models
	 *
	 * @param translation_reset          If true, re-translates all content
	 *
	 * @param feed_content               If true, translates or checks availability
	 *                                   for all content in content_classes
	 *
	 * @param debugMode                  Enables detailed logging during
	 *
	 * @param testingMode                Enables test-specific logging or behavior
	 *
	 * @param showCriticalErrors         Whether critical errors should be logged
	 *                                   when not debugging
	 *
	 * @param showIgnoredErrors          Whether ignored errors should be logged
	 *                                   when not debugging
	 *
	 * @param application_id             Identifier for the current application/site
	 *
	 * @param platform                   Platform application is going to run on
	 *
	 * @param doFullTranslatorTest       Full test on all translate methods, 3 times
	 *                                   over (uses 3 available languages, long wait
	 *                                   time)
	 *
	 * @param model_storage_path         Directory where translation models are
	 *                                   stored
	 *
	 * @param content_classes            Classes containing enums with translatable
	 *                                   strings
	 *
	 * @return Returns true after successfully completing initialization steps,
	 *         false otherwise
	 */
	public static boolean init(final Locale default_language, final List<Locale> language_selection, final boolean run_language_detector, final boolean universal_translation_mode, final boolean model_reset, final boolean translation_reset, final boolean feed_content, final Platform platform, final String application_id, final String model_storage_path, final Class<?>... content_classes) {
		return init(
				null,
				null,
				default_language,
				language_selection,
				run_language_detector,
				universal_translation_mode,
				model_reset,
				translation_reset,
				feed_content,
				platform,
				application_id,
				model_storage_path,
				content_classes);
	}

	public static boolean init(final String config_path_or_string, final Locale default_language, final List<Locale> language_selection, final boolean run_language_detector, final boolean universal_translation_mode, final boolean model_reset, final boolean translation_reset, final boolean feed_content, final Platform platform, final String application_id, final String model_storage_path, final Class<?>... content_classes) {
		return init(
				null,
				config_path_or_string,
				default_language,
				language_selection,
				run_language_detector,
				universal_translation_mode,
				model_reset,
				translation_reset,
				feed_content,
				platform,
				application_id,
				model_storage_path,
				content_classes);
	}

	public static boolean init(final Object framework_object, final Locale default_language, final List<Locale> language_selection, final boolean run_language_detector, final boolean universal_translation_mode, final boolean model_reset, final boolean translation_reset, final boolean feed_content, final Platform platform, final String application_id, final String model_storage_path, final Class<?>... content_classes) {
		return init(
				framework_object,
				null,
				default_language,
				language_selection,
				run_language_detector,
				universal_translation_mode,
				model_reset,
				translation_reset,
				feed_content,
				platform,
				application_id,
				model_storage_path,
				content_classes);
	}

	public static boolean init(final Object framework_object, final String config_path_or_string, final Locale default_language, final List<Locale> language_selection, final boolean run_language_detector, final boolean universal_translation_mode, final boolean model_reset, final boolean translation_reset, final boolean feed_content, final Platform platform, final String application_id, final String model_storage_path, final Class<?>... content_classes) {
		if (!runningMaintenance && (framework == null)) {
			//
			runningMaintenance = true;
			//
			framework = DetectionUtils.detectFramework();
			if (!framework.equals(DetectionUtils.Framework.NONE)) {
				database = DetectionUtils.detectDatabase();
			}
			if (!framework.equals(DetectionUtils.Framework.NONE) && !database.equals(DetectionUtils.Database.NONE)) {
				if (framework_object == null) {
					getDatabaseManagement().init(config_path_or_string != null ? config_path_or_string : null);
				} else {
					getDatabaseManagement().setFrameworkObject(framework_object);
				}
			}
			//
			universalTranslations = universal_translation_mode;
			//
			if (application_id != null) {
				siteOrAppId = application_id;
			}
			if (platform != null) {
				platformRuningOn = platform;
			}
			if (model_storage_path != null) {
				modelPath = model_storage_path;
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
			mainPathString = modelPath + "/hf-translator_" + siteOrAppId; //$NON-NLS-1$
			languageModelFolder = FolderName.LanguageModels;
			//
			if (language_selection == null) {
				Collections.addAll(selectedLanguages, Locale.getAvailableLocales());
			} else {
				selectedLanguages.addAll(language_selection);
			}
			//
			T_Log.log("Starting Language Detection Model..."); //$NON-NLS-1$
			alwaysRunDetector = run_language_detector;
			if (alwaysRunDetector) {
				defaultLanguage = runLanguageDetectorService("Testing, testing, is this thing working?"); //$NON-NLS-1$
			} else {
				defaultLanguage = runLanguageDetectorOneTime("Testing, testing, is this thing working?"); //$NON-NLS-1$
			}
			if ((defaultLanguage == null) || !defaultLanguage.getLanguage().equals("en")) { //$NON-NLS-1$
				T_Log.log("Language Detection Not Working: locale: " + defaultLanguage.getLanguage()); //$NON-NLS-1$
				return false;
			}
			T_Log.log("Language Detection Working"); //$NON-NLS-1$
			//
			if (default_language != null) {
				defaultLanguage = default_language;
			}
			//
			//
			if (!framework.equals(DetectionUtils.Framework.NONE) && !database.equals(DetectionUtils.Database.NONE)) {
				if (translation_reset) {
					getDatabaseManagement().deleteAllTranslations();
				} else {
					getDatabaseManagement().deleteUnusedTranslations();
					getDatabaseManagement().deleteDuplicateTranslations();
				}
			}
			downloadFilesAndCreateModels(model_reset, defaultLanguage, true);
			//
			runningMaintenance = false;
			//
			if (feed_content && (framework_object != null) && (!database.equals(DetectionUtils.Database.NONE))) {
				final TranslateStacker translateStacker = new TranslateStacker();
				if ((content_classes != null) && (content_classes.length > 0)) {
					for (final Class<?> clazz : content_classes) {
						for (final Field field : clazz.getDeclaredFields()) {
							if (field.getType() == String.class) {
								try {
									translateStacker.add((String) field.get(null));
								} catch (final Exception e) {
									T_Log.log("Error While Determining If Object Is A String", e, true); //$NON-NLS-1$
								}
							}
						}
						for (final Class<?> nested : clazz.getDeclaredClasses()) {
							if (nested.isEnum() && Translatable.class.isAssignableFrom(nested)) {
								final Translatable[] enumValues = (Translatable[]) nested.getEnumConstants();
								translateStacker.addAll(enumValues);
							} else {
								try {
									final Object instance = nested.getDeclaredConstructor().newInstance();
									if (instance instanceof final Translatable translatable) {
										translateStacker.add(translatable);
									}
								} catch (final Exception e) {
									T_Log.log("Error While Determining If Object Is Translatable", e, true); //$NON-NLS-1$
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
							final Object instance = nested.getDeclaredConstructor().newInstance();
							if (instance instanceof final Translatable translatable) {
								translateStacker.add(translatable);
							}
						} catch (final Exception ignored) {
							T_Log.log("Error While Determining If Object Is Translatable", ignored, true); //$NON-NLS-1$
						}
					}
				}
				for (final Locale l : getSelectedAndWorkingLanguages()) {
					translateStacker.add(l.getDisplayLanguage(defaultLanguage));
				}
				translateStacker.feedTranslatorDatabase();
			}
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
		return maintain(false);
	}

	/**
	 * Performs maintenance tasks like deleting unused/duplicate translations and
	 * re-downloading models.
	 *
	 * @param model_reset Whether to re-download and re-create models
	 *
	 * @return Returns true after successfully completing maintenance steps, false
	 *         otherwise
	 */
	public static boolean maintain(final boolean model_reset) {
		if (!runningMaintenance) {
			//
			runningMaintenance = true;
			//
			if (!framework.equals(DetectionUtils.Framework.NONE) && !database.equals(DetectionUtils.Database.NONE)) {
				getDatabaseManagement().deleteUnusedTranslations();
				getDatabaseManagement().deleteDuplicateTranslations();
			}
			//
			downloadFilesAndCreateModels(model_reset, defaultLanguage, true);
			//
			runningMaintenance = false;
		}
		return true;
	}

	/**
	 * Shuts Down Translator: Sessions, Executors and models...Everything.
	 *
	 */
	public static synchronized void shutdown() {
		new HibernateTranslatorEntity().shutdown();
		closeLanguageDetectorService();
	}

	/**
	 * Returns the default system language set during initialization.
	 *
	 * @return The default language object
	 */
	public static Locale getDefaultSystemLanguage() {
		return defaultLanguage;
	}

	/**
	 * Constructs the path to a model directory for translation.
	 *
	 * @param toTranslate Whether this is the storage path or modle python path
	 *                    (e.g., `opus-mt-en-fr` or `opus-mt-en-fr/ctranslate2`)
	 * @param langIN      Source language code (like "en")
	 * @param langOUT     Target language code (like "fr")
	 * @return Path to the model storage folder or modle python path
	 */
	private static Path getModelDir(final boolean toTranslate, final Locale langIN, final Locale langOUT) {
		final String finalLangIN = langIN.getLanguage();
		final String finaLangOUT = langOUT.getLanguage();
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
	private static String getHuggingURL(final Locale langIn, final Locale langOut, final String fileName) {
		final String localeCodeIn = langIn.getLanguage();
		final String localeCodeOut = langOut.getLanguage();
		return "https://huggingface.co/Helsinki-NLP/opus-mt-" + localeCodeIn + "-" + localeCodeOut + "/resolve/main/" + fileName; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	/**
	 * Returns the entity for the database framework in use.
	 *
	 */
	private static TranslatorDatabaseManagement getDatabaseManagement() {
		if (framework.equals(DetectionUtils.Framework.HIBERNATE)) {
			return new HibernateTranslatorEntity();
		}
		if (framework.equals(DetectionUtils.Framework.MONGODB)) {
			return new MongoDBTranslatorEntity();
		}
		return null;
	}

	/**
	 * Ensures that the CTranslate2 model exists for a given language pair. If not,
	 * generates it via Python script. If this fails, language is marked un-usable.
	 *
	 * @param model_reset If true, re-downloads all model files, remakes models
	 * @param modelDir    Root directory of the model folder
	 * @param langIN      Source language
	 * @param langOUT     Target language
	 * @throws Exception On failure to generate or validate model, language is
	 *                   marked un-usable.
	 */
	private static void ensureCTranslate2(final boolean model_reset, final Path modelDir, final Locale langIN, final Locale langOUT) throws Exception {
		try {
			exportPYFiles();
			String hashString = sha512(Files.readString(scriptFileGenerate.toPath(), StandardCharsets.UTF_8));
			int count = 0;
			while (!hashString.equals(hashFileGenerate)) {
				T_Log.log(ToPyFiles.generateModelPYName + " : Failed Hash Check"); //$NON-NLS-1$
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
				if (!model_reset) {
					T_Log.log("CTranslate2 Model Already Exists: " + modelBin); //$NON-NLS-1$
					return;
				}
				if (delete_Folder(outDir)) {
					T_Log.log(modelDir + "Model Already Exists, Deleting And Re-creating."); //$NON-NLS-1$
				} else {
					T_Log.log(modelDir + "Model Already Exists, Failed To Delete, Attempting Overwrite."); //$NON-NLS-1$
				}
			}
			if (hashString.equals(hashFileGenerate)) {
				T_Log.log("CTranslate2 Model Missing. Generating Via Python..."); //$NON-NLS-1$
				final ProcessBuilder pb = new ProcessBuilder(
						ToPyFiles.pythonLang,
						scriptFileGenerate.getAbsolutePath(),
						modelDir.toAbsolutePath().toString(),
						langIN.getLanguage(),
						langOUT.getLanguage(),
						outDir.toAbsolutePath().toString());
				pb.inheritIO();
				final Process p = pb.start();
				final int exit = p.waitFor();
				if (exit == 0) {
					T_Log.log("CTranslate2 Model Generated Successfully: " + modelBin); //$NON-NLS-1$
					downloadFlatpickerFile(langIN);
					return;
				}
				T_Log.log("Failed To Generate CTranslate2 Model, Deleting Model Folder,  Exit Code: " + exit); //$NON-NLS-1$
				throw new Exception(
						"EnsureCTranslate2 Failed, Throwing Error: Triggering Language Disable If Not In Universal Mode"); //$NON-NLS-1$
			}
		} catch (final Exception e) {
			T_Log.log("EnsureCTranslate2 Or Flatpicker Failed, Throwing Error: Triggering Language Disable", e); //$NON-NLS-1$
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
	private static List<String> runCTranslate2(final Locale langIN, final Locale langOUT, final String input) {
		try {
			exportPYFiles();
			String hashString = sha512(Files.readString(scriptFileTranslate.toPath(), StandardCharsets.UTF_8));
			int count = 0;
			while (!hashString.equals(hashFileTranslate)) {
				T_Log.log(ToPyFiles.translatePYName + " : Failed Hash Check"); //$NON-NLS-1$
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
				Locale finalLangIN = langIN;
				if (!modelDir.toFile().exists()) {
					T_Log.log(
							"No Model File Found, Searching For Languages That Translate To " + langOUT //$NON-NLS-1$
									.getDisplayName(defaultLanguage));
					final BridgeContainer bridge = bridgeLanguages(langIN, langOUT, input);
					if (bridge != null) {
						finalInput = bridge.input;
						finalLangIN = bridge.locale;
						modelDir = getModelDir(true, finalLangIN, langOUT);
					} else {
						finalInput = null;
						finalLangIN = null;
						modelDir = null;
					}
				}
				if ((modelDir == null) || (finalLangIN == null) || (finalInput == null) || finalInput.isBlank()) {
					T_Log.log("No Translation Found For: " + langOUT.getDisplayName(defaultLanguage)); //$NON-NLS-1$
					return Collections.emptyList();
				}
				final ProcessBuilder pb = new ProcessBuilder(
						ToPyFiles.pythonLang,
						scriptFileTranslate.getAbsolutePath(),
						modelDir.toAbsolutePath().toString(),
						finalLangIN.getLanguage(),
						langOUT.getLanguage(),
						finalInput);
				pb.redirectErrorStream(false);
				final Process p = pb.start();
				final StringBuilder output = new StringBuilder();
				final Thread stdoutThread = new Thread(
						() -> {
							try (BufferedReader reader = new BufferedReader(
									new InputStreamReader(
											p.getInputStream(),
											StandardCharsets.UTF_8))) {
								String line;
								while ((line = reader.readLine()) != null) {
									output.append(line);
								}
							} catch (final IOException e) {
								T_Log.log("CTranslate2 Translation Failed At STDOUT THREAD: ", e); //$NON-NLS-1$
							}
						});
				final Thread stderrThread = new Thread(
						() -> {
							try (BufferedReader errReader = new BufferedReader(
									new InputStreamReader(
											p.getErrorStream(),
											StandardCharsets.UTF_8))) {
								String line;
								while ((line = errReader.readLine()) != null) {
									T_Log.log(line);
								}
							} catch (final IOException e) {
								T_Log.log("CTranslate2 Translation Failed At STDERR THREAD: ", e); //$NON-NLS-1$
							}
						});
				stdoutThread.start();
				stderrThread.start();
				stdoutThread.join();
				stderrThread.join();
				final int exit = p.waitFor();
				if (exit != 0) {
					T_Log.log("CTranslate2 Translation Failed, Exit Code: " + exit); //$NON-NLS-1$
					return Collections.emptyList();
				}
				return new ObjectMapper().readValue(output.toString(), new TypeReference<List<String>>() {
					/* null */});
			}
			return Collections.emptyList();
		} catch (final Exception e) {
			T_Log.log("Translation Error At PY File", e); //$NON-NLS-1$
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
		private final Locale locale;
		private final String input;

		private BridgeContainer(final Locale locale, final String input) {
			this.locale = locale;
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
	private static BridgeContainer bridgeLanguages(final Locale langIN, final Locale langOUT, final String input) throws Exception {
		if (languagesLinks.get(langIN.getLanguage()) != null) {
			T_Log.log("Found Compatable Languages For: " + langIN.getDisplayName(defaultLanguage)); //$NON-NLS-1$
			for (final String langBridgeCode : languagesLinks.get(langIN.getLanguage())) {
				T_Log.log(
						"Checking Compatablity For: " + Locale.forLanguageTag(langBridgeCode) //$NON-NLS-1$
								.getDisplayName(defaultLanguage) + " To " + langOUT //$NON-NLS-1$
										.getDisplayName(defaultLanguage));
				if ((languagesLinks.get(langBridgeCode) == null)
						|| !languagesLinks.get(langBridgeCode).contains(langOUT.getLanguage())) {
					T_Log.log(
							Locale.forLanguageTag(langBridgeCode)
									.getDisplayName(defaultLanguage) + " Is Not Compatable With " + langOUT //$NON-NLS-1$
											.getDisplayName(defaultLanguage));
					continue;
				}
				T_Log.log(
						Locale.forLanguageTag(langBridgeCode).getDisplayName(defaultLanguage) + " Is Compatable With " + langOUT //$NON-NLS-1$
								.getDisplayName(defaultLanguage));
				final Path modelDir = getModelDir(true, langIN, Locale.forLanguageTag(langBridgeCode));
				if (modelDir.toFile().exists()) {
					if ((input != null) && !input.isBlank()) {
						final ProcessBuilder pb = new ProcessBuilder(
								ToPyFiles.pythonLang,
								scriptFileTranslate.getAbsolutePath(),
								modelDir.toAbsolutePath().toString(),
								langIN.getLanguage(),
								langBridgeCode,
								input);
						pb.redirectErrorStream(false);
						final Process p = pb.start();
						final StringBuilder output = new StringBuilder();
						final Thread stdoutThread = new Thread(
								() -> {
									try (BufferedReader reader = new BufferedReader(
											new InputStreamReader(
													p.getInputStream(),
													StandardCharsets.UTF_8))) {
										String line;
										while ((line = reader.readLine()) != null) {
											output.append(line);
										}
									} catch (final IOException e) {
										T_Log.log("CTranslate2 Translation Bridge Failed At STDOUT THREAD: ", e); //$NON-NLS-1$
									}
								});
						final Thread stderrThread = new Thread(
								() -> {
									try (BufferedReader errReader = new BufferedReader(
											new InputStreamReader(
													p.getErrorStream(),
													StandardCharsets.UTF_8))) {
										String line;
										while ((line = errReader.readLine()) != null) {
											T_Log.log(line);
										}
									} catch (final IOException e) {
										T_Log.log("CTranslate2 Translation Bridge Failed At STDERR THREAD: ", e); //$NON-NLS-1$
									}
								});
						stdoutThread.start();
						stderrThread.start();
						stdoutThread.join();
						stderrThread.join();
						final int exit = p.waitFor();
						if (exit != 0) {
							T_Log.log("CTranslate2 Translation Bridge Failed, Exit Code: " + exit); //$NON-NLS-1$
							return null;
						}
						final String jsonInput = new ObjectMapper().writeValueAsString(
								new ObjectMapper().readValue(output.toString(), new TypeReference<List<String>>() {
									/* null */}));
						final String encoded = Base64.getEncoder().encodeToString(jsonInput.getBytes(StandardCharsets.UTF_8));
						return new BridgeContainer(
								Locale.forLanguageTag(langBridgeCode),
								encoded);
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
	private static Locale runLanguageDetectorOneTime(final String input) {
		try {
			exportPYFiles();
			String hashString = sha512(Files.readString(scriptFileDetectLanguage.toPath(), StandardCharsets.UTF_8));
			int count = 0;
			while (!hashString.equals(hashFileDetectLanguage)) {
				T_Log.log(ToPyFiles.conLIDPYName + " : Failed Hash Check"); //$NON-NLS-1$
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
						"false"); //$NON-NLS-1$
				pb.redirectErrorStream(false);
				final Process p = pb.start();
				final StringBuilder output = new StringBuilder();
				persistentWriter = new BufferedWriter(
						new OutputStreamWriter(
								p.getOutputStream(),
								StandardCharsets.UTF_8));
				final Thread stdoutThread = new Thread(
						() -> {
							try (BufferedReader reader = new BufferedReader(
									new InputStreamReader(
											p.getInputStream(),
											StandardCharsets.UTF_8))) {
								String line;
								while ((line = reader.readLine()) != null) {
									output.append(line);
								}
							} catch (final IOException e) {
								T_Log.log("LanguageDetector Failed At STDOUT THREAD: ", e); //$NON-NLS-1$
							}
						});
				final Thread stderrThread = new Thread(
						() -> {
							try (BufferedReader errReader = new BufferedReader(
									new InputStreamReader(
											p.getErrorStream(),
											StandardCharsets.UTF_8))) {
								String line;
								while ((line = errReader.readLine()) != null) {
									T_Log.log(line);
								}
							} catch (final IOException e) {
								T_Log.log("LanguageDetector Failed At STDERR THREAD: ", e); //$NON-NLS-1$
							}
						});
				final Thread stdinThread = new Thread(
						() -> {
							try (BufferedWriter writer = new BufferedWriter(
									new OutputStreamWriter(
											p.getOutputStream(),
											StandardCharsets.UTF_8))) {
								writer.write(input);
								writer.newLine();
								writer.flush();
							} catch (final IOException e) {
								T_Log.log("LanguageDetector Failed At STDOUT THREAD: ", e); //$NON-NLS-1$
							}
						});
				stdoutThread.start();
				stderrThread.start();
				stdinThread.start();
				stdoutThread.join();
				stderrThread.join();
				stdinThread.join();
				final int exit = p.waitFor();
				if (exit != 0) {
					T_Log.log("Language Detector Failed, Exit Code: " + exit); //$NON-NLS-1$
					return defaultLanguage;
				}
				final String topLangCode = output.toString().trim();
				T_Log.log("Top Language Code: " + topLangCode); //$NON-NLS-1$
				final Locale detected = Locale.forLanguageTag(topLangCode);
				if (detected == null) {
					return null;
				}
				if (!disabledLanguages.contains(detected) || universalTranslations) {
					return detected;
				}
			}
			return defaultLanguage;
		} catch (final Exception e) {
			T_Log.log("Detection Error At PY File", e); //$NON-NLS-1$
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
	private synchronized static Locale runLanguageDetectorService(final String input) {
		synchronized (processLock) {
			if ((persistentProcess == null) || !persistentProcess.isAlive()) {
				try {
					exportPYFiles();
					String hashString = sha512(Files.readString(scriptFileDetectLanguage.toPath(), StandardCharsets.UTF_8));
					int count = 0;
					while (!hashString.equals(hashFileDetectLanguage)) {
						T_Log.log(ToPyFiles.conLIDPYName + " : Failed Hash Check"); //$NON-NLS-1$
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
								"true"); //$NON-NLS-1$
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
										while (persistentProcess.isAlive() && ((line = err.readLine()) != null)) {
											T_Log.log(line);
										}
									} catch (final IOException ignored) {
										T_Log.log("LanguageDetector Failed At STDERR THREAD: ", ignored, true); //$NON-NLS-1$
									}
								});
						thread.start();
					}
				} catch (final Exception e) {
					T_Log.log("Failed To Start Persistent Language Detector", e); //$NON-NLS-1$
					return null;
				}
			}
		}
		try {
			synchronized (processLock) {
				persistentWriter.write(input);
				persistentWriter.newLine();
				persistentWriter.flush();
				final String jsonLine = persistentReader.readLine();
				if (jsonLine == null) {
					closeLanguageDetectorService();
					return detectLanguage(input);
				}
				final String topLangCode = jsonLine.trim();
				T_Log.log("Top Language Code: " + topLangCode); //$NON-NLS-1$
				final Locale detected = Locale.forLanguageTag(topLangCode);
				if (detected == null) {
					return null;
				}
				if (!disabledLanguages.contains(detected) || universalTranslations) {
					return detected;
				}
				return defaultLanguage;
			}
		} catch (final Exception e) {
			T_Log.log("Detection Error At PY File", e); //$NON-NLS-1$
			return defaultLanguage;
		}
	}

	/**
	 * Closes all resources relating to the language detector model.
	 *
	 *
	 */
	private static void closeLanguageDetectorService() {
		synchronized (processLock) {
			try {
				if (persistentWriter != null) {
					persistentWriter.close();
				}
			} catch (final IOException ignored) {
				T_Log.log("Error Closing Persistent Writer: ", ignored, true); //$NON-NLS-1$
			}
			try {
				if (persistentReader != null) {
					persistentReader.close();
				}
			} catch (final IOException ignored) {
				T_Log.log("Error Closing Persistent Reader: ", ignored, true); //$NON-NLS-1$
			}
			if (persistentProcess != null) {
				persistentProcess.destroyForcibly();
			}
			persistentProcess = null;
			persistentWriter = null;
			persistentReader = null;
			if ((thread != null) && thread.isAlive()) {
				try {
					thread.interrupt();
					thread.join();
				} catch (final InterruptedException ignored) {
					Thread.currentThread().interrupt();
					T_Log.log("Error Closing Thread: ", ignored, true); //$NON-NLS-1$
				}
			}
			thread = null;
		}
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
						HibernateTranslatorEntity.class,
						languageModelFolder.getTempPath(),
						ToPyFiles.generateModelPY,
						ToPyFiles.generateModelPYName,
						ToPyFiles.pythonSuffix);
				hashFileGenerate = sha512(Files.readString(scriptFileGenerate.toPath(), StandardCharsets.UTF_8));
				scriptFileTranslate = Extract.ToFileSystem.fromClassPathContext(
						HibernateTranslatorEntity.class,
						languageModelFolder.getTempPath(),
						ToPyFiles.translatePY,
						ToPyFiles.translatePYName,
						ToPyFiles.pythonSuffix);
				hashFileTranslate = sha512(Files.readString(scriptFileTranslate.toPath(), StandardCharsets.UTF_8));
				scriptFileDetectLanguage = Extract.ToFileSystem.fromClassPathContext(
						HibernateTranslatorEntity.class,
						Extract.ToFileSystem
								.fromClassPathContext(
										HibernateTranslatorEntity.class,
										languageModelFolder.getPath(),
										ToPyFiles.conLID_Folder)
								.getAbsolutePath(),
						ToPyFiles.conLIDPY,
						ToPyFiles.conLIDPYName,
						ToPyFiles.pythonSuffix);
				hashFileDetectLanguage = sha512(Files.readString(scriptFileDetectLanguage.toPath(), StandardCharsets.UTF_8));
				T_Log.log(ToPyFiles.generateModelPYName + " And " + ToPyFiles.translatePYName + " Installed From Source Files"); //$NON-NLS-1$ //$NON-NLS-2$
			}
		} catch (final Exception e) {
			T_Log.log("Failed To Download Generate And Translate Python Files", e); //$NON-NLS-1$
		}
	}

	private static class Extract {
		private static class ToFileSystem {
			private static File fromClassPathContext(final Class<?> clazz, final String saveToPath, final String resourcePath, final String fileName, final String suffix) {
				try (InputStream in = clazz.getResourceAsStream(resourcePath)) {
					return exportandReturnFile(in, saveToPath, resourcePath, fileName, suffix);
				} catch (final Exception e) {
					T_Log.log("Failed To Extraxt Resource", e); //$NON-NLS-1$
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
				T_Log.log("Extracted Resource: " + resourcePath); //$NON-NLS-1$
				return file;
			}

			private static File fromClassPathContext(final Class<?> clazz, final String saveToPath, final String resourceFolderPath) {
				try {
					final URL folderUrl = clazz.getResource(resourceFolderPath);
					if (resourceFolderPath == null) {
						T_Log.log("Resource Folder Not Found: " + resourceFolderPath); //$NON-NLS-1$
						return null;
					}
					return exportFolderAndReturnPath(folderUrl, saveToPath);
				} catch (final Exception e) {
					T_Log.log("Failed To Extraxt Resource Folder", e); //$NON-NLS-1$
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
						T_Log.log("Extracted folder from filesystem: " + folderUrl); //$NON-NLS-1$
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
						T_Log.log("Extracted folder from JAR: " + folderUrl.getPath()); //$NON-NLS-1$
						return targetDir;
					}
					T_Log.log("Unsupported protocol: " + folderUrl.getProtocol()); //$NON-NLS-1$
					return null;
				} catch (final Exception e) {
					T_Log.log("Failed to extract folder", e); //$NON-NLS-1$
					return null;
				}
			}

			private static void copyFileSystemFolder(final File src, final File dest) throws IOException {
				if (src.isDirectory()) {
					dest.mkdirs();
					for (final File child : src.listFiles()) {
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
			final byte[] hash = digest.digest(string.getBytes(StandardCharsets.UTF_8));
			for (final byte b : hash) {
				final String hex = String.format("%02x", Byte.valueOf(b)); //$NON-NLS-1$
				hexString.append(hex);
			}
		} catch (final Exception e) {
			T_Log.log("Authentication Error", e); //$NON-NLS-1$
		}
		return hexString.toString();
	}

	/**
	 * Downloads model files for all language pairs and creates corresponding
	 * language models. This method iterates over all available languages, downloads
	 * the required model files, and handles errors by deleting the model directory
	 * if a download fails and marking the language as un-usable.
	 *
	 * @param model_reset If true, re-downloads all model files, remakes models
	 *
	 * @param lang        The starting language in the chain
	 *
	 * @param firstCall   If this is the first time this method is called in the
	 *                    chain
	 *
	 * @throws Exception If an error occurs during the download or file handling
	 *                   process. Marks the language as un-usable.
	 */
	private static void downloadFilesAndCreateModels(final boolean model_reset, final Locale lang, final boolean firstCall) {
		for (final Locale lang2 : selectedLanguages) {
			boolean defaultToCleintSuccess = false;
			if ((!lang.equals(lang2)) && (!lang.getLanguage().equals(lang2.getLanguage()))) {
				try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()) {
					Path modelDir = getModelDir(false, lang, lang2);
					Path outDir = modelDir.resolve(ToPyFiles.generatedModelFolder);
					Path modelBin = outDir.resolve("model.bin"); //$NON-NLS-1$
					try {
						Files.createDirectories(modelDir);
						T_Log.log("Downloading Model: " + lang + "-" + lang2); //$NON-NLS-1$ //$NON-NLS-2$
						downloadModelFiles(model_reset, client, modelDir, lang, lang2);
						defaultToCleintSuccess = true;
						if (languagesLinks.get(lang.getLanguage()) == null) {
							languagesLinks.put(lang.getLanguage(), new ArrayList<>());
						}
						if (Files.exists(modelBin)) {
							final List<String> newList = languagesLinks.get(lang.getLanguage());
							newList.add(lang2.getLanguage());
							languagesLinks.put(lang.getLanguage(), newList);
						}
					} catch (final Exception e) {
						if (!universalTranslations) {
							disabledLanguages.add(lang2);
						}
						if (!Files.exists(modelBin)) {
							T_Log.log(
									"Download Failed For " + lang + "-" + lang2 + ". No Models Stored At: " + modelDir //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
											.toAbsolutePath(),
									e);
							if (delete_Folder(modelDir)) {
								T_Log.log("Model Folder Deleted"); //$NON-NLS-1$
							} else {
								T_Log.log("Failed To Delete Model Folder"); //$NON-NLS-1$
							}
						} else {
							T_Log.log(
									"Download Failed For " + lang2 + "-" + lang + ". Model Stored At: " + modelDir //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
											.toAbsolutePath(),
									e);
						}
					}
					if (defaultToCleintSuccess) {
						try {
							modelDir = getModelDir(false, lang2, lang);
							outDir = modelDir.resolve(ToPyFiles.generatedModelFolder);
							modelBin = outDir.resolve("model.bin"); //$NON-NLS-1$
							Files.createDirectories(modelDir);
							T_Log.log("Downloading Model: " + lang2 + "-" + lang); //$NON-NLS-1$ //$NON-NLS-2$
							downloadModelFiles(model_reset, client, modelDir, lang2, lang);
							if (languagesLinks.get(lang2.getLanguage()) == null) {
								languagesLinks.put(lang2.getLanguage(), new ArrayList<>());
							}
							if (Files.exists(modelBin)) {
								final List<String> newList = languagesLinks.get(lang2.getLanguage());
								newList.add(lang.getLanguage());
								languagesLinks.put(lang2.getLanguage(), newList);
							}
							if (firstCall && universalTranslations) {
								downloadFilesAndCreateModels(model_reset, lang2, false);
							}
						} catch (final Exception e) {
							if (!universalTranslations) {
								disabledLanguages.add(lang2);
							}
							if (!Files.exists(modelBin)) {
								T_Log.log(
										"Download Failed For " + lang2 + "-" + lang + ". No Models Stored At: " + modelDir //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
												.toAbsolutePath(),
										e);
								if (delete_Folder(modelDir)) {
									T_Log.log("Model Folder Deleted"); //$NON-NLS-1$
								} else {
									T_Log.log("Failed To Delete Model Folder"); //$NON-NLS-1$
								}
							} else {
								T_Log.log(
										"Download Failed For " + lang2 + "-" + lang + ". Model Stored At: " + modelDir //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
												.toAbsolutePath(),
										e);
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
	 * @param model_reset If true, re-downloads all model files
	 * @param client      The HTTP client used for downloading files.
	 * @param modelDir    The directory where the model files will be saved.
	 * @param langIn      The source language for the translation model.
	 * @param langOut     The target language for the translation model.
	 * @throws Exception If an error occurs during file download or validation.
	 */
	private static void downloadModelFiles(final boolean model_reset, final HttpClient client, final Path modelDir, final Locale langIn, final Locale langOut) throws Exception {
		Path outPath = null;
		for (final String file : MODEL_FILES_TO_DOWNLOAD) {
			outPath = modelDir.resolve(file);
			if (Files.exists(outPath)) {
				if (!model_reset) {
					T_Log.log(file + " Already Exists, Skipping."); //$NON-NLS-1$
					continue;
				}
				if (delete_File(outPath)) {
					T_Log.log(file + "File Already Exists, Deleting And Re-Downloading."); //$NON-NLS-1$
				} else {
					T_Log.log(file + "File Already Exists, Failed To Delete, Attempting Overwrite."); //$NON-NLS-1$
				}
			}
			T_Log.log("Downloading " + file); //$NON-NLS-1$
			try {
				final String url = getHuggingURL(langIn, langOut, file);
				T_Log.log("Connecting To: " + url); //$NON-NLS-1$
				downloadFile(client, url, outPath);
				if (!Files.exists(outPath)) {
					T_Log.log(file + "Invalid, File Not Saved"); //$NON-NLS-1$
					continue;
				}
				final String chk = readFileContents(outPath);
				if (chk.isBlank() || chk.contains("Invalid username or password.") || chk.contains("Entry not found")) { //$NON-NLS-1$ //$NON-NLS-2$
					T_Log.log(file + " Is Empty / Invalid, Deleted"); //$NON-NLS-1$
					delete_File(outPath);
					continue;
				}
				T_Log.log("Download Complete. File stored at: " + outPath.toAbsolutePath()); //$NON-NLS-1$
			} catch (final Exception e) {
				delete_File(outPath);
				throw e;
			}
		}
		try {
			ensureCTranslate2(model_reset, modelDir, langIn, langOut);
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
	 * @param language The language for which to download Flatpickr resources.
	 */
	private static void downloadFlatpickerFile(final Locale language) {
		if (!language.getLanguage().equals("en")) { //$NON-NLS-1$
			try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
				final String flatpickrResourcePath = ToFlatpickr.flatpickrRelativePath;
				// Get the directory where the JAR is located
				Path jarDir;
				try {
					jarDir = Path.of(HibernateTranslatorEntity.class.getProtectionDomain().getCodeSource().getLocation().toURI())
							.getParent();
					final Path baseDir = jarDir.resolve(flatpickrResourcePath);
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
					final String localeFile = language.getLanguage() + ".js"; //$NON-NLS-1$
					downloadFile(
							client,
							"https://cdn.jsdelivr.net/npm/flatpickr@4.6.13/dist/l10n/" + localeFile, //$NON-NLS-1$
							baseDir.resolve(localeFile));
					T_Log.log("Flatpickr Downloaded For " + language.toString()); //$NON-NLS-1$
				} catch (final URISyntaxException e) {
					T_Log.log("URISyntaxException While Downloading Flatpicker File", e); //$NON-NLS-1$
				}
			} catch (final IOException e) {
				T_Log.log("Flatpickr Downloaded Failed For " + language.toString(), e); //$NON-NLS-1$
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
			T_Log.log("File already exists: " + destination.getFileName()); //$NON-NLS-1$
			return;
		}
		final HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(30)).build();
		try {
			final HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination));
			if (response.statusCode() != 200) {
				delete_File(destination);
				T_Log.log("Download Failed: " + destination.getFileName()); //$NON-NLS-1$
				return;
			}
			T_Log.log("Downloaded: " + destination.getFileName()); //$NON-NLS-1$
		} catch (IOException | InterruptedException e) {
			T_Log.log("Download Failed To Connect: " + destination.getFileName(), e); //$NON-NLS-1$
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

	// MULTIPLE TRAANSLATION METHODS AND DATABASE FEEDING
	/**
	 * A utility class for managing and performing translations of strings and enum
	 * values (Translatable). Provides methods to add items to a translation stack,
	 * perform translations in various modes, and feed translation data into the
	 * database asynchronously.
	 */
	public static class TranslateStacker {
		private boolean redoTranslationsInTableStacker = false;
		private boolean doAsList = true;

		/**
		 * Sets whether to redo translations in the database table at runtime for this
		 * stacker instance. delete, translate and re-save.
		 *
		 * @param reCreate Whether to delete existing translation entries before adding
		 *                 new ones
		 */
		public void setReCreate(final boolean reCreate) {
			this.redoTranslationsInTableStacker = reCreate;
		}

		/**
		 * Sets how to process translations. true: list (faster), false: individual
		 * strings.
		 *
		 * @param redoTranslations Whether to delete existing translation entries before
		 *                         adding new ones
		 */
		public void setProcessAsList(final boolean doAsList) {
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
			final List<Object> combined = new ArrayList<>();
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
		public <T> Map<T, String> translate(final Locale to) {
			return this.translate(null, to);
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
		public <T> Map<T, String> translate(final Locale from, final Locale to) {
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
		public <T> Map<T, String> translate_ModelOnly(final Locale to) {
			return this.translate_ModelOnly(null, to);
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
		public <T> Map<T, String> translate_ModelOnly(final Locale from, final Locale to) {
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
		public <T> Map<T, String> translateAndFeedTranslatorDatabase(final Locale to) {
			return this.translateAndFeedTranslatorDatabase(defaultLanguage, to);
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
		public <T> Map<T, String> translateAndFeedTranslatorDatabase(final Locale from, final Locale to) {
			Map<T, String> result = new LinkedHashMap<>();
			result = (Map<T, String>) Translator.translate(
					from != null ? from : defaultLanguage,
					to,
					this.getCombinedList(),
					this.doAsList,
					this.redoTranslationsInTableStacker);
			new Thread(
					() -> {
						T_Log.log("Feeding Translator In New Thread"); //$NON-NLS-1$
						for (final Locale l : languageInUseFirst(getSelectedAndWorkingLanguages(), to)) {
							if ((!l.equals(getDefaultSystemLanguage()) && (!l.equals(to)))) {
								Translator.translate(
										from != null ? from : defaultLanguage,
										l,
										this.getCombinedList(),
										this.doAsList,
										this.redoTranslationsInTableStacker);
							}
						}
						T_Log.log("Finished Feeding Translator, Thread Terminated"); //$NON-NLS-1$
					}).start();
			return result;
		}

		/**
		 * Feeds the translator database with translation data from all languages.
		 *
		 */
		public void feedTranslatorDatabase() {
			for (final Locale l : getSelectedAndWorkingLanguages()) {
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
		 * Returns all Languages with the target Locale first
		 *
		 * @param langauge The target language.
		 */
		private static Locale[] languageInUseFirst(final Locale[] languages, final Locale languageInUse) {
			final List<Locale> ordered = new ArrayList<>();
			ordered.add(languageInUse);
			for (final Locale lang : languages) {
				if (lang != languageInUse) {
					ordered.add(lang);
				}
			}
			return ordered.toArray(new Locale[0]);
		}
	}

	// INDIVIDUAL STRING AND LIST METHODS
	/**
	 * Detects the language a single text input.
	 *
	 * @param input Text needed to detect the language
	 * @return language of text input
	 */
	public static Locale detectLanguage(final String input) {
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
	public static String translate(final Locale to, final Object input) {
		return translate(null, to, input, false);
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
	public static String translate(final Locale from, final Locale to, final Object input) {
		return translate(from, to, input, false);
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
	public static String translate(final Locale from, final Locale to, final Object input, final boolean recreateTableEntry) {
		if (input == null) {
			T_Log.log("Null At Translate"); //$NON-NLS-1$
			return null;
		}
		if (!(input instanceof String) && !(input instanceof Translatable)) {
			T_Log.log("Not Correct Input Type At Translate"); //$NON-NLS-1$
			return null;
		}
		if (!disabledLanguages.contains(to)) {
			Locale langIN = defaultLanguage;
			if (from != null) {
				langIN = from;
			}
			try {
				while (runningMaintenance) {
					Thread.sleep(1000);
				}
				if (!langIN.getLanguage().equals(to.getLanguage())) {
					final String modelCode = langIN.getLanguage() + "-" + to.getLanguage(); //$NON-NLS-1$
					final String translatedFromDatabase = searchDatabase(modelCode, input, recreateTableEntry);
					if (translatedFromDatabase != null) {
						return translatedFromDatabase;
					}
					final String translatedFromModel = doOneString(langIN, to, modelCode, input, true);
					if (translatedFromModel != null) {
						return translatedFromModel;
					}
				}
			} catch (final Exception e) {
				T_Log.log("Translation Failed For " + langIN.getLanguage() + " - " + to.getLanguage(), e); //$NON-NLS-1$ //$NON-NLS-2$
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
	public static <T> Map<T, String> translate(final Locale to, final List<T> inputs) {
		return translate(null, to, inputs, true, false);
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
	public static <T> Map<T, String> translate(final Locale to, final List<T> inputs, final boolean doAsList) {
		return translate(null, to, inputs, doAsList, false);
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
	public static <T> Map<T, String> translate(final Locale from, final Locale to, final List<T> inputs) {
		return translate(null, to, inputs, true, false);
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
	public static <T> Map<T, String> translate(final Locale from, final Locale to, final List<T> inputs, final boolean doAsList) {
		return translate(null, to, inputs, doAsList, false);
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
	public static <T> Map<T, String> translate(final Locale from, final Locale to, final List<T> inputs, final boolean doAsList, final boolean recreateTableEntrys) {
		if (inputs == null) {
			return null;
		}
		Map<T, String> results = new LinkedHashMap<>();
		if (!disabledLanguages.contains(to)) {
			Locale langIN;
			if (from != null) {
				langIN = from;
			} else {
				langIN = defaultLanguage;
			}
			try {
				while (runningMaintenance) {
					Thread.sleep(1000);
				}
				if (!langIN.getLanguage().equals(to.getLanguage())) {
					final String modelCode = langIN.getLanguage() + "-" + to.getLanguage(); //$NON-NLS-1$
					final tSearchResult<T> tSearchResult = searchDatabase(modelCode, inputs, recreateTableEntrys);
					if (tSearchResult.missing.isEmpty()) {
						return tSearchResult.found;
					}
					final Map<T, String> foundResults = tSearchResult.found;
					if (doAsList) {
						final Map<T, String> missingResults = (Map<T, String>) doAsList(
								langIN,
								to,
								modelCode,
								tSearchResult.missing,
								true);
						foundResults.putAll(missingResults);
						return foundResults;
					}
					final Map<T, String> missingResults = (Map<T, String>) doAsStrings(
							langIN,
							to,
							modelCode,
							tSearchResult.missing,
							true);
					foundResults.putAll(missingResults);
					return foundResults;
				}
			} catch (final Exception e) {
				T_Log.log("Translation Failed For " + langIN.getLanguage() + " - " + to.getLanguage(), e); //$NON-NLS-1$ //$NON-NLS-2$
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
	public static String translate_OnlyUseModel(final Locale to, final Object input) {
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
	public static String translate_OnlyUseModel(final Locale from, final Locale to, final Object input) {
		if (input == null) {
			T_Log.log("Null At Translate"); //$NON-NLS-1$
			return null;
		}
		if (!(input instanceof String) && !(input instanceof Translatable)) {
			T_Log.log("Not Correct Input Type At Translate"); //$NON-NLS-1$
			return null;
		}
		if (!disabledLanguages.contains(to)) {
			Locale langIN;
			if (from != null) {
				langIN = from;
			} else {
				langIN = defaultLanguage;
			}
			try {
				while (runningMaintenance) {
					Thread.sleep(1000);
				}
				if (!langIN.getLanguage().equals(to.getLanguage())) {
					final String modelCode = langIN.getLanguage() + "-" + to.getLanguage(); //$NON-NLS-1$
					final String translatedFromModele = doOneString(langIN, to, modelCode, input, false);
					if (translatedFromModele != null) {
						return translatedFromModele;
					}
				}
			} catch (final Exception e) {
				T_Log.log("Translation Failed For " + langIN.getLanguage() + " - " + to.getLanguage(), e); //$NON-NLS-1$ //$NON-NLS-2$
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
	public static <T> Map<T, String> translate_OnlyUseModel(final Locale to, final List<T> inputs) {
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
	public static <T> Map<T, String> translate_OnlyUseModel(final Locale from, final Locale to, final List<T> inputs, final boolean doAsList) {
		if (inputs == null) {
			return null;
		}
		Map<T, String> results = new LinkedHashMap<>();
		if (!disabledLanguages.contains(to)) {
			Locale langIN;
			if (from != null) {
				langIN = from;
			} else {
				langIN = defaultLanguage;
			}
			try {
				while (runningMaintenance) {
					Thread.sleep(1000);
				}
				if (!langIN.getLanguage().equals(to.getLanguage())) {
					final String modelCode = langIN.getLanguage() + "-" + to.getLanguage(); //$NON-NLS-1$
					if (doAsList) {
						results = doAsList(langIN, to, modelCode, inputs, false);
					}
					if (!results.isEmpty()) {
						return results;
					}
					results = doAsStrings(langIN, to, modelCode, inputs, false);
					if (!results.isEmpty()) {
						return results;
					}
				}
			} catch (final Exception e) {
				T_Log.log("Translation Failed For " + langIN + " - " + to, e); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		results = new LinkedHashMap<>();
		for (final T item : inputs) {
			results.put(item, item.toString());
		}
		return results;
	}

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
			T_Log.log("Null At Database Search"); //$NON-NLS-1$
			return null;
		}
		final Map<T, String> found = new LinkedHashMap<>();
		final List<String> missing = new ArrayList<>();
		final List<?> list = inputs;
		final TranslatorDatabaseManagement translator = getDatabaseManagement();
		for (int i = 0; i < list.size(); i++) {
			if (!(list.get(i) instanceof String) && !(list.get(i) instanceof Translatable)) {
				T_Log.log("Not Correct Input Type At Database Search, Removed"); //$NON-NLS-1$
				continue;
			}
			final String key = (list.get(i) instanceof String ? (String) list.get(i) : (list.get(i)).toString());
			if (key.isBlank()) {
				continue;
			}
			T_Log.log("Searching Database For Listed Word/Phrase: " + modelCode + " : " + key); //$NON-NLS-1$ //$NON-NLS-2$
			if (translator != null) {
				final TranslatorDatabaseManagement translated = translator.getTranslation(modelCode, key);
				if ((translated == null) || (translated.getTranslation() == null)) {
					missing.add(key);
				} else if (redoTranslations) {
					translated.delete(modelCode, key);
					T_Log.log("Deleted Word/Phrase: " + modelCode + " : " + key); //$NON-NLS-1$ //$NON-NLS-2$
					missing.add(key);
				} else {
					T_Log.log("Found Database Translation For Word/Phrase: " + modelCode + " : " + translated.getTranslation()); //$NON-NLS-1$ //$NON-NLS-2$
					found.put(inputs.get(i), translated.getTranslation());
				}
			} else {
				for (int s = 0; s < list.size(); s++) {
					missing.add((list.get(s) instanceof String ? (String) list.get(s) : (list.get(s)).toString()));
				}
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
			T_Log.log("Null At Database Search"); //$NON-NLS-1$
			return null;
		}
		if (!(input instanceof String) && !(input instanceof Translatable)) {
			T_Log.log("Not Correct Input Type At Database Search"); //$NON-NLS-1$
			return null;
		}
		final String key = (input instanceof String ? (String) input : (input).toString());
		T_Log.log("Searching Database For Single Word/Phrase: " + modelCode + " : " + key); //$NON-NLS-1$ //$NON-NLS-2$
		if (key.isBlank()) {
			return null;
		}
		final TranslatorDatabaseManagement translator = getDatabaseManagement();
		if (translator != null) {
			final TranslatorDatabaseManagement translated = translator.getTranslation(modelCode, key);
			if ((translated == null) || (translated.getTranslation() == null)) {
				return null;
			}
			if (redoTranslations) {
				T_Log.log("Deleted Word/Phrase: " + modelCode + " : " + key); //$NON-NLS-1$ //$NON-NLS-2$
				translator.delete(modelCode, key);
				return null;
			}
			T_Log.log("Found Database Translation For Word/Phrase: " + modelCode + " : " + translated.getTranslation()); //$NON-NLS-1$ //$NON-NLS-2$
			return translated.getTranslation();
		}
		return null;
	}

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
	private static <T> Map<T, String> doAsList(final Locale langIN, final Locale langOUT, final String modelCode, final List<T> inputs, final boolean doTranslation) throws Exception {
		final Map<T, String> results = new LinkedHashMap<>();
		final List<List<T>> chunks = splitIntoChunks(inputs, modelCharLimit);
		final TranslatorDatabaseManagement translator = getDatabaseManagement();
		for (final List<T> chunk : chunks) {
			final List<T> chunkedList = chunk;
			if (chunkedList.size() > 0) {
				final String jsonInput = new ObjectMapper().writeValueAsString(chunkedList);
				T_Log.log("Translating List From Model: " + modelCode + " " + jsonInput); //$NON-NLS-1$ //$NON-NLS-2$
				final String encoded = Base64.getEncoder().encodeToString(jsonInput.getBytes(StandardCharsets.UTF_8));
				final List<String> outputs = runCTranslate2(langIN, langOUT, encoded);
				T_Log.log("Translation Results: " + new ObjectMapper().writeValueAsString(outputs)); //$NON-NLS-1$
				if (outputs.size() == chunkedList.size()) {
					for (int i = 0; i < chunkedList.size(); i++) {
						if (!(chunkedList.get(i) instanceof String) && !(chunkedList.get(i) instanceof Translatable)) {
							T_Log.log("Not Correct Input Type At Do Translate, Removed"); //$NON-NLS-1$
							continue;
						}
						final String key = appContentOrStringAsString(chunkedList.get(i));
						if (key.isBlank()) {
							continue;
						}
						final String output = ((outputs.get(i) != null) && !outputs.get(i).isBlank()) ? outputs
								.get(i) : "Model_Translation_Failed"; //$NON-NLS-1$
						final String formatted = output;
						results.putIfAbsent(inputs.get(i), formatted);
						if (doTranslation) {
							if (translator != null) {
								translator.save(modelCode, key, formatted);
							}
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
	private static <T> Map<T, String> doAsStrings(final Locale langIN, final Locale langOUT, final String modelCode, final List<T> inputs, final boolean doTranslateion) throws Exception {
		final Map<T, String> results = new LinkedHashMap<>();
		final List<List<T>> chunks = splitIntoChunks(inputs, modelCharLimit);
		final TranslatorDatabaseManagement translator = getDatabaseManagement();
		for (final List<T> chunk : chunks) {
			final List<T> chunkedList = chunk;
			if (chunkedList.size() > 0) {
				for (int i = 0; i < chunkedList.size(); i++) {
					if (!(chunkedList.get(i) instanceof String) && !(chunkedList.get(i) instanceof Translatable)) {
						T_Log.log("Not Correct Input Type AtDo Translate, Removed"); //$NON-NLS-1$
						continue;
					}
					final String key = appContentOrStringAsString(chunkedList.get(i));
					if (key.isBlank()) {
						continue;
					}
					T_Log.log("Translating String From Model: " + modelCode + " : " + key); //$NON-NLS-1$ //$NON-NLS-2$
					final List<String> result = runCTranslate2(langIN, langOUT, key);
					final String translatedFromModel = result.isEmpty() ? null : result.get(0);
					if ((translatedFromModel == null) || translatedFromModel.isBlank()) {
						results.put(inputs.get(i), "Model_Translation_Failed"); //$NON-NLS-1$
					} else {
						final String formatted = translatedFromModel;
						results.putIfAbsent(inputs.get(i), formatted);
						if (doTranslateion) {
							if (translator != null) {
								translator.save(modelCode, key, formatted);
							}
						}
						T_Log.log("Translated Word/Phrase Output: " + modelCode + " : " + formatted); //$NON-NLS-1$ //$NON-NLS-2$
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
	private static String doOneString(final Locale langIN, final Locale langOUT, final String modelCode, final Object input, final boolean doTranslateion) throws Exception {
		if (input == null) {
			T_Log.log("Null At Translate"); //$NON-NLS-1$
			return null;
		}
		if (!(input instanceof String) && !(input instanceof Translatable)) {
			T_Log.log("Not Correct Input Type At Translate"); //$NON-NLS-1$
			return null;
		}
		final String key = appContentOrStringAsString(input);
		if (key.isBlank()) {
			return null;
		}
		final String placeheld = key;
		T_Log.log("Translating String From Model: " + modelCode + " : " + key); //$NON-NLS-1$ //$NON-NLS-2$
		final List<String> result = runCTranslate2(langIN, langOUT, placeheld);
		final String translatedFromModel = result.isEmpty() ? null : result.get(0);
		if ((translatedFromModel != null) && !translatedFromModel.isBlank()) {
			final String formatted = translatedFromModel;
			if (doTranslateion) {
				final TranslatorDatabaseManagement translator = getDatabaseManagement();
				if (translator != null) {
					translator.save(modelCode, key, formatted);
				}
			}
			T_Log.log("Translated Word/Phrase From Model: " + modelCode + " : " + formatted); //$NON-NLS-1$ //$NON-NLS-2$
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
	private static <T> List<List<T>> splitIntoChunks(final List<T> list, final int maxCharsPerChunk) {
		final List<List<T>> chunks = new ArrayList<>();
		final List<T> currentChunk = new ArrayList<>();
		int currentCharCount = 0;
		for (final T item : list) {
			if (((currentCharCount + item.toString().length()) > maxCharsPerChunk) && !currentChunk.isEmpty()) {
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

	/**
	 * Helper class for detecting frameworks and databases.
	 *
	 */
	public static class DetectionUtils {
		// Simple Enums for type safety
		public enum Framework {
			HIBERNATE, MONGODB, MYBATIS, NONE
		}

		public enum Database {
			MARIADB, MYSQL, POSTGRESQL, H2, NONE
		}

		// Map of Enum -> Marker Class Name
		private static final Map<Framework, String> FRAMEWORK_MARKERS = new LinkedHashMap<>();
		private static final Map<Database, String> DATABASE_MARKERS = new LinkedHashMap<>();
		static {
			FRAMEWORK_MARKERS.put(Framework.HIBERNATE, "org.hibernate.Session"); //$NON-NLS-1$
			FRAMEWORK_MARKERS.put(Framework.MONGODB, "com.mongodb.client.MongoClient"); //$NON-NLS-1$
			FRAMEWORK_MARKERS.put(Framework.MYBATIS, "org.apache.ibatis.session.SqlSession"); //$NON-NLS-1$
			//
			DATABASE_MARKERS.put(Database.MARIADB, "org.mariadb.jdbc.Driver"); //$NON-NLS-1$
			DATABASE_MARKERS.put(Database.MYSQL, "com.mysql.cj.jdbc.Driver"); //$NON-NLS-1$
			DATABASE_MARKERS.put(Database.POSTGRESQL, "org.postgresql.Driver"); //$NON-NLS-1$
			DATABASE_MARKERS.put(Database.H2, "org.h2.Driver"); //$NON-NLS-1$
		}

		/**
		 * Detects which management framework is present on the classpath.
		 *
		 * @return Name of the framework (e.g., "Hibernate") or null
		 */
		public static Framework detectFramework() {
			for (final var entry : FRAMEWORK_MARKERS.entrySet()) {
				if (isClassPresent(entry.getValue())) {
					T_Log.log("Detected: " + entry.getKey()); //$NON-NLS-1$
					return entry.getKey();
				}
			}
			return Framework.NONE;
		}

		/**
		 * Detects which database driver is present on the classpath.
		 *
		 * @return Name of the database (e.g., "MariaDB") or null
		 */
		public static Database detectDatabase() {
			for (final var entry : DATABASE_MARKERS.entrySet()) {
				if (isClassPresent(entry.getValue())) {
					T_Log.log("Detected: " + entry.getKey()); //$NON-NLS-1$
					return entry.getKey();
				}
			}
			return Database.NONE;
		}

		/**
		 * Helper method for detecting if class paths exist.
		 *
		 * @return boolean representing if the class path exists, true or false
		 */
		private static boolean isClassPresent(final String className) {
			try {
				Class.forName(className);
				return true;
			} catch (final ClassNotFoundException e) {
				T_Log.log("Class Detection Error", e, true); //$NON-NLS-1$
				return false;
			}
		}
	}

	/**
	 * Interface for framework entities.
	 *
	 */
	public interface TranslatorDatabaseManagement {
		void init(String config_path_or_string);

		void setFrameworkObject(Object sessionFactory);

		void shutdown();

		void dropTable();

		String getId();

		TranslatorDatabaseManagement setStringIN(final Object StringIN);

		TranslatorDatabaseManagement setModelCode(final String ModelCode);

		TranslatorDatabaseManagement setTranslation(final String StringOUT);

		String getTranslation();

		void save();

		void delete();

		TranslatorDatabaseManagement getTranslation(final String id);

		TranslatorDatabaseManagement getTranslation(final String modelCode, final Object input);

		void save(final String modelCode, final Object input, final String translatedString);

		void delete(final String id);

		void delete(final String modelCode, final Object input);

		void deleteDuplicateTranslations();

		void deleteUnusedTranslations();

		void deleteAllTranslations();

		boolean idExists(final String id);

		String generateUniqueID(final Class<?> entityClass);
	}

	/**
	 * Detects if a language is read right-to-left otherwise left-to-right.
	 *
	 * @param language The locale to be used.
	 * @return A boolean true if read right-to-left otherwise false.
	 */
	public static boolean isRightToLeft(final Locale language) {
		return Bidi.requiresBidi(language.getDisplayName(language).toCharArray(), 0, language.getDisplayName(language).length());
	}

	/**
	 * Returns the currency symbol for the locale provided.
	 *
	 * @param Locale The locale to be used.
	 * @return A String, the currency symbol the the locale.
	 */
	public static String getCurrencySymbol(final Locale locale) {
		return Currency.getInstance(locale).getSymbol();
	}

	/**
	 * Formats a numeric value according to the specified language's locale.
	 *
	 * @param language The language whose locale will be used for formatting.
	 * @param value    The numeric string to format (can be integer or decimal).
	 * @return A formatted string representation of the number in the given locale.
	 * @throws Exception If parsing or formatting fails.
	 */
	public static String formatNumber(final Locale language, final String value) throws Exception {
		final Locale locale = language;
		final NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
		if (value.contains(".")) { //$NON-NLS-1$
			final double num = Double.parseDouble(value);
			return numberFormat.format(num);
		}
		final long num = Long.parseLong(value);
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
	public static String formatNumber(final Locale language, final Number value) throws Exception {
		final Locale locale = language;
		final NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
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
	public static String formatTimestamp(final Locale language, final Timestamp timestamp) {
		final LocalDateTime dateTime = timestamp.toLocalDateTime(); // no string conversion
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
	public static String formatTimestamp(final Locale language, final String timestamp) {
		// First, parse into LocalDateTime using the known format of
		// Timestamp.toString()
		final DateTimeFormatter parser = DateTimeFormatter
				.ofPattern("yyyy-MM-dd HH:mm:ss[.[SSSSSSSSS][SSSSSSSS][SSSSSSS][SSSSSS][SSSSS][SSSS][SSS][SS][S]]"); //$NON-NLS-1$
		final LocalDateTime dateTime = LocalDateTime.parse(timestamp, parser);
		return formatLocalDateTime(language, dateTime);
	}

	public static String formatLocalDateTime(final Locale language, final LocalDateTime dateTime) {
		final Locale locale = language;
		final DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale);
		return dateTime.format(formatter);
	}

	/**
	 * Formats a timestamp according to the specified language's locale.
	 *
	 * @param language  The language whose locale will be used for formatting.
	 * @param timestamp The timestamp to convert.
	 * @return A formatted localized month-year string.
	 */
	public static String formatTimestamp_MonthYear(final Locale language, final Timestamp timestamp) {
		final YearMonth yearMonth = YearMonth.from(timestamp.toLocalDateTime());
		final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM yyyy").withLocale(language); //$NON-NLS-1$
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
	public static String formatDate(final Locale language, final String dateStr) {
		if ((dateStr == null) || dateStr.isBlank()) {
			return null;
		}
		// Extract the date part (before space or 'T')
		String datePart = dateStr;
		if (dateStr.contains(" ")) { //$NON-NLS-1$
			datePart = dateStr.substring(0, dateStr.indexOf(' '));
		} else if (dateStr.contains("T")) { //$NON-NLS-1$
			datePart = dateStr.substring(0, dateStr.indexOf('T'));
		}
		final Locale locale = language;
		final DateTimeFormatter outputFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
		LocalDate localDate = null;
		// Add "yyyy-MM-dd" (standard for Timestamp date part) and keep others
		final String[] formats = { "yyyy-MM-dd", "MM/dd/yyyy", "dd/MM/yyyy", "yyyy/MM/dd" }; //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$ //$NON-NLS-4$
		for (final String format : formats) {
			try {
				localDate = LocalDate.parse(datePart, DateTimeFormatter.ofPattern(format));
				return localDate.format(outputFormatter);
			} catch (final DateTimeParseException e) {
				T_Log.log("Parse Failed, Continuing", e, true); //$NON-NLS-1$
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
	public static String formatTime(final Locale language, final String timeStr) {
		if ((timeStr == null) || timeStr.isBlank()) {
			return null;
		}
		// Extract the time part (after first space or after 'T')
		String timePart = timeStr;
		if (timeStr.contains(" ")) { //$NON-NLS-1$
			final int spaceIdx = timeStr.indexOf(' ');
			timePart = timeStr.substring(spaceIdx + 1);
		} else if (timeStr.contains("T")) { //$NON-NLS-1$
			final int tIdx = timeStr.indexOf('T');
			timePart = timeStr.substring(tIdx + 1);
		}
		// Remove milliseconds if present (they cause issues with some formatters)
		if (timePart.contains(".")) { //$NON-NLS-1$
			timePart = timePart.substring(0, timePart.indexOf('.'));
		}
		final Locale locale = language;
		final DateTimeFormatter outputFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale);
		LocalTime localTime = null;
		final String[] formats = { "HH:mm:ss", "HH:mm", "HH:mm:ss.SSS", "HH:mm:ss.SSSSSS", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				"hh:mm a", "hh:mm:ss a", "KK:mm a", "KK:mm:ss a", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				"H:mm", "h:mm a" //$NON-NLS-1$ //$NON-NLS-2$
		};
		for (final String format : formats) {
			try {
				localTime = LocalTime.parse(timePart, DateTimeFormatter.ofPattern(format));
				return localTime.format(outputFormatter);
			} catch (final DateTimeParseException e) {
				T_Log.log("Parse Failed, Continuing", e, true); //$NON-NLS-1$
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
	public static String formatCurrency(final Locale language, final String currencyStr) throws Exception {
		final Locale locale = language;
		final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(locale);
		final String numberPart = currencyStr.replaceAll("[^\\d.,]", "").trim(); //$NON-NLS-1$ //$NON-NLS-2$
		final double amount = Double.parseDouble(numberPart.replace(",", "")); //$NON-NLS-1$ //$NON-NLS-2$
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
	public static Date parseDate(final Locale language, final String dateString) {
		if ((dateString == null) || dateString.isBlank()) {
			return null;
		}
		// Extract the date part (before space or 'T')
		String datePart = dateString;
		if (dateString.contains(" ")) { //$NON-NLS-1$
			datePart = dateString.substring(0, dateString.indexOf(' '));
		} else if (dateString.contains("T")) { //$NON-NLS-1$
			datePart = dateString.substring(0, dateString.indexOf('T'));
		}
		final Locale locale = language;
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
		for (final String pattern : patterns) {
			final SimpleDateFormat sdf = new SimpleDateFormat(
					pattern,
					locale);
			sdf.setLenient(false);
			try {
				return sdf.parse(datePart);
			} catch (final ParseException e) {
				T_Log.log("Parse Failed, Continuing", e, true); //$NON-NLS-1$
			}
		}
		T_Log.log("Parse Error: Unable to parse date: " + dateString); //$NON-NLS-1$
		return null;
	}

	/**
	 * Parses a time string using locale-aware formatters and returns a Time object.
	 *
	 * @param language The language whose locale will be used for parsing.
	 * @param timeStr  A time string to parse (may also contain date part).
	 * @return A parsed Time object, or null if parsing fails.
	 */
	public static Time parseTime(final Locale language, final String timeStr) {
		if ((timeStr == null) || timeStr.isBlank()) {
			T_Log.log("Null or empty time string"); //$NON-NLS-1$
			return null;
		}
		// Extract the time part (after space or 'T')
		String timePart = timeStr;
		if (timeStr.contains(" ")) { //$NON-NLS-1$
			final int spaceIdx = timeStr.indexOf(' ');
			timePart = timeStr.substring(spaceIdx + 1);
		} else if (timeStr.contains("T")) { //$NON-NLS-1$
			final int tIdx = timeStr.indexOf('T');
			timePart = timeStr.substring(tIdx + 1);
		}
		// Remove milliseconds if present (they cause issues with some formatters)
		if (timePart.contains(".")) { //$NON-NLS-1$
			timePart = timePart.substring(0, timePart.indexOf('.'));
		}
		// Normalize: replace common delimiters with ':', remove non-time chars
		final String cleaned = timePart.replaceAll("[~\\-.,;]+", ":") //$NON-NLS-1$ //$NON-NLS-2$
				.replaceAll("\\s*:\\s*", ":") //$NON-NLS-1$ //$NON-NLS-2$
				.replaceAll("[^0-9:APMapm]", "") //$NON-NLS-1$ //$NON-NLS-2$
				.trim()
				.toUpperCase();
		if (cleaned.isEmpty() || !cleaned.matches(".*\\d.*")) { //$NON-NLS-1$
			T_Log.log("No time digits found in: " + timeStr); //$NON-NLS-1$
			return null;
		}
		final List<DateTimeFormatter> formatters = buildLocaleAwareFormatters(language);
		// Add more fallback formatters for robustness
		formatters.addAll(
				Arrays.asList(
						DateTimeFormatter.ofPattern("HH:mm:ss"), //$NON-NLS-1$
						DateTimeFormatter.ofPattern("HH:mm"), //$NON-NLS-1$
						DateTimeFormatter.ofPattern("h:mm a"), //$NON-NLS-1$
						DateTimeFormatter.ofPattern("hh:mm:ss a"))); //$NON-NLS-1$
		for (final DateTimeFormatter fmt : formatters) {
			try {
				final DateTimeFormatter strict = fmt.withResolverStyle(ResolverStyle.STRICT);
				final TemporalAccessor parsed = strict.parse(cleaned);
				if (parsed.isSupported(ChronoField.HOUR_OF_DAY)) {
					final LocalTime lt = LocalTime.from(parsed);
					return Time.valueOf(lt);
				}
			} catch (final DateTimeException ignored) {
				T_Log.log("Time Parse Error", ignored, true); //$NON-NLS-1$
			}
		}
		T_Log.log("Could not parse time: " + cleaned + " (original: " + timeStr + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		return null;
	}

	/**
	 * Builds a list of locale-aware DateTimeFormatters for parsing times.
	 *
	 * @param locale The locale used to determine the order and pattern of
	 *               formatters.
	 * @return A list of DateTimeFormatter objects specific to the given locale.
	 */
	public static List<DateTimeFormatter> buildLocaleAwareFormatters(final Locale language) {
		final List<DateTimeFormatter> formatters = new ArrayList<>();
		// Locale-specific formatter order (12-hour formats first for US, 24-hour first
		// for others)
		if (Locale.US.equals(language) || Locale.CANADA.equals(language)) {
			formatters.addAll(
					Arrays.asList(
							DateTimeFormatter.ofPattern("h:mm a", language), //$NON-NLS-1$
							DateTimeFormatter.ofPattern("hh:mm a", language), //$NON-NLS-1$
							DateTimeFormatter.ofPattern("h:mma", language), //$NON-NLS-1$
							DateTimeFormatter.ofPattern("hh:mma", language), //$NON-NLS-1$
							DateTimeFormatter.ofPattern("HH:mm", language), //$NON-NLS-1$
							DateTimeFormatter.ofPattern("H:mm", language))); //$NON-NLS-1$
		} else {
			formatters.addAll(
					Arrays.asList(
							DateTimeFormatter.ofPattern("HH:mm", language), //$NON-NLS-1$
							DateTimeFormatter.ofPattern("H:mm", language), //$NON-NLS-1$
							DateTimeFormatter.ofPattern("HH:mm:ss", language), //$NON-NLS-1$
							DateTimeFormatter.ofPattern("hh:mm a", language), //$NON-NLS-1$
							DateTimeFormatter.ofPattern("h:mm a", language))); //$NON-NLS-1$
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
	public static int determinTimeOfDay(final Locale language, final ZoneId zoneId) {
		final ZonedDateTime zonedDateTime = ZonedDateTime.now(zoneId);
		final LocalTime now = zonedDateTime.toLocalTime();
		if (now.isBefore(LocalTime.NOON)) {
			return 0;
		}
		if (now.isBefore(LocalTime.of(18, 0))) {
			return 1;
		}
		return 2;
	}
}
