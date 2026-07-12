package didzapp.HF_Translator;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import didzapp.T_Log;

/**
 * A utility class that defines paths to various types of resource files used in
 * the application. These include CSS, JS, downloadable files, images, videos,
 * fonts, Python scripts, configuration files, and flatpickr localization
 * resources.
 */
public class TranslatorResourcePaths {
	/**
	 * Inner static class for defining paths related to image files.
	 */
	public static class ToImageFiles {
		public static final String imagesPath = "images"; //$NON-NLS-1$
	}

	/**
	 * Inner static class for defining paths related to video files.
	 */
	public static class ToVideoFiles {
		public static final String videosPath = "videos"; //$NON-NLS-1$
	}

	/**
	 * Inner static class for defining paths related to font files. Provides methods
	 * to retrieve font names and all available font files using reflection.
	 */
	public static class ToFontFiles {
		public static final String fontsPath = "fonts"; //$NON-NLS-1$

		/**
		 * Extracts the name of a font from a full path string.
		 *
		 * @param font The full path to the font file (e.g., "/fonts/arial.ttf")
		 * @return The font name without the extension and directory (e.g., "arial")
		 */
		public static String getFontName(String font) {
			return font.split("/")[1].split("\\.")[0]; //$NON-NLS-1$ //$NON-NLS-2$
		}

		/**
		 * Retrieves all font file names from fields in this class using reflection.
		 *
		 * @return An array of strings representing the names of all font files
		 */
		public static String[] getAllFontFiles() {
			List<String> fontFiles = new ArrayList<>();
			Field[] fields = ToFontFiles.class.getDeclaredFields();
			for (Field field : fields) {
				if (Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers())
						&& field.getType().equals(String.class)) {
					try {
						fontFiles.add(((String) field.get(null)).split("/")[1]); //$NON-NLS-1$
					} catch (IllegalAccessException e) {
						T_Log.log("Font Collection Error", e); //$NON-NLS-1$
					}
				}
			}
			return fontFiles.toArray(new String[0]);
		}
	}

	/**
	 * Inner static class for grouping font constants.
	 */
	public static class Fonts {//
		public static final String auto = "auto"; //$NON-NLS-1$
		public static final String monospace = "monospace"; //$NON-NLS-1$
		public static final String serif = "serif"; //$NON-NLS-1$
		public static final String sansSerif = "sans-serif"; //$NON-NLS-1$
		public static final String cursive = "cursive"; //$NON-NLS-1$
		public static final String fantasy = "fantasy"; //$NON-NLS-1$
		public static final String systemUI = "system-ui"; //$NON-NLS-1$
		public static final String uIserif = "ui-serif"; //$NON-NLS-1$
		public static final String uIsansserif = "ui-sans-serif"; //$NON-NLS-1$
		public static final String uImonospace = "ui-monospace"; //$NON-NLS-1$
		public static final String uIrounded = "ui-rounded"; //$NON-NLS-1$
		public static final String emoji = "emoji"; //$NON-NLS-1$
		public static final String math = "math"; //$NON-NLS-1$
		public static final String fangsong = "fangsong"; //$NON-NLS-1$
	}

	/**
	 * Inner static class for defining paths related to Python (.py) files.
	 */
	public static class ToPyFiles {
		public static final String pythonPath = "/py"; //$NON-NLS-1$
		public static final String pythonLang = "python"; //$NON-NLS-1$
		public static final String pythonSuffix = ".py"; //$NON-NLS-1$
		//
		public static final String generatedModelFolder = "ctranslate2"; //$NON-NLS-1$
		//
		public static final String generateModelPY = "/py/generate_ct2.py"; //$NON-NLS-1$
		public static final String generateModelPYName = "generate_ct2"; //$NON-NLS-1$
		//
		public static final String translatePY = "/py/translate_ct2.py"; //$NON-NLS-1$
		public static final String translatePYName = "translate_ct2"; //$NON-NLS-1$
		//
		public static final String conLID_Folder = "/py/ConLID"; //$NON-NLS-1$
		public static final String conLIDPY = "/py/detectLanguage_conLID.py"; //$NON-NLS-1$
		public static final String conLIDPYName = "detectLanguage_conLID.py"; //$NON-NLS-1$
	}

	/**
	 * Inner static class for defining paths related to configuration files.
	 */
	public static class ToConfigFiles {
		public static final String configPath = "/"; //$NON-NLS-1$
		public static final String libhibernate = "/libhibernate.cfg.xml"; //$NON-NLS-1$
		public static final String libmybatis = "/libmybatis.cfg.xml"; //$NON-NLS-1$
				public static final String logback = "/logback.xml"; //$NON-NLS-1$

	}

	/**
	 * Inner static class for defining paths related to flatpickr UI components.
	 */
	public static class ToFlatpickr {
		/**
		 * Generates the path to a language resource file for flatpickr based on
		 * Language
		 *
		 * @param language The enum constant representing the target language for
		 *                 translation (e.g., Language.ENGLISH, Language.FRENCH)
		 * @return The full path to the language JS file (e.g., "flatpickr/en.js")
		 */
		public static final String languageRecources(Locale locale) {
			return "/flatpickr/" + locale.getLanguage() + ".js"; //$NON-NLS-1$ //$NON-NLS-2$
		}

		public static final String flatpickrPath = "/flatpickr"; //$NON-NLS-1$
		public static final String flatpickrRelativePath = "flatpickr"; //$NON-NLS-1$
		public static final String cssPath = "flatpickr.min.css"; //$NON-NLS-1$
		public static final String jsPath = "flatpickr.min.js"; //$NON-NLS-1$
		public static final String css = "/flatpickr/flatpickr.min.css"; //$NON-NLS-1$
		public static final String js = "/flatpickr/flatpickr.min.js"; //$NON-NLS-1$
	}
}
