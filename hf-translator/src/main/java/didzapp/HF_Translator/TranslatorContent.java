package didzapp.HF_Translator;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonValue;

import didzapp.HF_Translator.Translator.Language;

/**
 * This class defines content-related constants and an interface for objects
 * used for translation purposes.
 */
public class TranslatorContent {
	public static final class untranslated {
		/**
		 * A static nested class that holds untranslated string constants.
		 */
		public static final String DidzappCom = "Didzapp.com"; //$NON-NLS-1$
	}

	/**
	 * An interface that all content types must implement for translation.
	 * <p>
	 * <b>Implementation Note:</b> objects that define a constructor with a String
	 * value <b>MUST</b> override {@link #toString()} to return that internal value
	 * for translation instead of the constant name or stringified object structure.
	 * (e.g., Enum.Blank(""): ensures passing "" instead of "Blank").
	 * </p>
	 */
	public interface Translatable {
		// No methods - just a marker to group all translatable objects.
	}

	/**
	 * An enum representing folder names used in the application. Implements
	 * Translatable to support translation and value retrieval.
	 */
	public enum FolderName implements Translatable {
		/**
		 * Represents the "Language_Models" folder. Used for storing language model
		 * files.
		 */
		LanguageModels("Language_Models"); //$NON-NLS-1$

		// Instance variables
		private final String value;
		private final String path;
		private final String tempPath;

		/**
		 * Constructor initializes the folder name, builds paths based on configuration,
		 * and sets up temp path inside that folder.
		 *
		 * @param value The original string representation of the folder name.
		 */
		FolderName(final String value) {
			this.value = value;
			if (this.value.equals("Language_Models")) { //$NON-NLS-1$
				this.path = Translator.sharedPathString + this.value;
			} else {
				this.path = Translator.mainPathString + this.value;
			}
			this.tempPath = this.path + Translator.tempPathString;
		}

		/**
		 * Returns the original string value of the folder.
		 *
		 * @return The unmodified name of the folder.
		 */
		@JsonValue
		public String getValue() {
			return this.value;
		}

		/**
		 * Returns the original string value of the folder.
		 *
		 * @return The unmodified value of the folder name.
		 */
		@Override
		public String toString() {
			return this.value;
		}

		/**
		 * Translates the folder's name into the specified language.
		 *
		 * @param language The enum constant representing the target language for
		 *                 translation (e.g., Language.ENGLISH, Language.FRENCH)
		 * @return Translated string representation of the folder name.
		 */
		public String translate(final Language language) {
			return Translator.translate(Translator.defaultLanguage, language, this.value, Translator.redoTranslationsInTable);
		}

		/**
		 * Gets the full path to the folder, creating it if necessary.
		 *
		 * @return The absolute path string for the folder.
		 */
		public String getPath() {
			return Translator.create_Dir_If_Missing(this.path);
		}

		/**
		 * Gets the temporary path within this folder, creating it if necessary.
		 *
		 * @return The absolute path string for the temp directory.
		 */
		public String getTempPath() {
			return Translator.create_Dir_If_Missing(this.tempPath);
		}

		/**
		 * Converts a string into its corresponding FolderName enum value.
		 *
		 * @param str The string representation of a folder name.
		 * @return Matching FolderName enum, or null if not found.
		 */
		public static FolderName fromString(final String str) {
			for (final FolderName lang : FolderName.values()) {
				if (lang.value.equalsIgnoreCase(str)) {
					return lang;
				}
			}
			return null;
		}

		/**
		 * Returns an array of all folder names as strings.
		 *
		 * @return Array of original string representations.
		 */
		public static String[] stringValues() {
			return Arrays.stream(Language.values()).map(Language::toString).toArray(String[]::new);
		}

		/**
		 * Returns an array of translated folder names in the specified language.
		 *
		 * @param language The enum constant representing the target language for
		 *                 translation (e.g., Language.ENGLISH, Language.FRENCH)
		 * @return Array of Translated string representations of the folder names.
		 */
		public static String[] stringTranslatedValues(final Language language) {
			return Arrays.stream(FolderName.values()).map(f -> f.translate(language)).toArray(String[]::new);
		}
	}

	public enum TestContent implements Translatable {
		Hello("Hello"), //$NON-NLS-1$
		Blank(""), //$NON-NLS-1$
		one("one"), //$NON-NLS-1$
		two("2"), //$NON-NLS-1$
		three("three"), //$NON-NLS-1$
		four("4"), //$NON-NLS-1$
		five("five"), //$NON-NLS-1$
		testing("testing"), //$NON-NLS-1$
		test("Test, 1 two 3 four 5, mom, Dad, money, Pet"), //$NON-NLS-1$
		GoodBye("Good-bye"); //$NON-NLS-1$

		// Instance variables
		private final String value;

		/**
		 * Constructor initializes the Translatable enum with a word or phrase.
		 *
		 * @param value The original string representation of the word or phrase.
		 */
		TestContent(final String value) {
			this.value = value;
		}

		/**
		 * Returns the original string value of the word or phrase.
		 *
		 * @return The unmodified name of the folder.
		 */
		@JsonValue
		public String getValue() {
			return this.value;
		}

		/**
		 * Returns the original string value of the word or phrase.
		 *
		 * @return The unmodified value of the word or phrase.
		 */
		@Override
		public String toString() {
			return this.value;
		}

		/**
		 * Translates the word or phrase into the specified language.
		 *
		 * @param language The enum constant representing the target language for
		 *                 translation (e.g., Language.ENGLISH, Language.FRENCH)
		 * @return Translated string representation of the word or phrase.
		 */
		public String translate(final Language language) {
			return Translator.translate(language, this.value);
		}

		/**
		 * Returns an array of all words or phrases as strings.
		 *
		 * @return Array of original string representations.
		 */
		public static String[] stringValues() {
			return Arrays.stream(TestContent.values()).map(TestContent::toString).toArray(String[]::new);
		}

		/**
		 * Returns an array of translated words or phrases in the specified language.
		 *
		 * @param language The enum constant representing the target language for
		 *                 translation (e.g., Language.ENGLISH, Language.FRENCH)
		 * @return Array of Translated string representations of the words or phrases.
		 */
		public static String[] stringTranslatedValues(final Language language) {
			return Arrays.stream(TestContent.values()).map(f -> f.translate(language)).toArray(String[]::new);
		}
	}
}
