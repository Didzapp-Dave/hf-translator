How to use 'hf-translator' 


hibernate database default creds : 

user - hf-translator

password - hf-translator

database name - hf-translator

tables create themselves



Step 1 :  Init the Translator (returns a boolean)



if (Translator.init(
					
libhiberbernate_CFG_XML_Path,                                (Your custom CFG file path : from main application) (optional) 
					
Language.ENGLISH,                                            (OPTIONAL: Default language  /  null = English by default)
					
new Language[] {},                                           (OPTIONAL: Array[] of selected languages or Language.usableValues() for all*  /  null = all* by default)
					
("true" or "false"),                                         (If true: run language detector as service(faster). If false: it runs and turns off)
					
("true" or "false"),                                         (If true: support translations between all languages. If false: only translate between supported languages and default)
					
("true" or "false"),                                         (Re-download models and re-translate content on startup)
					
Translator.Platform.WINDOWS(or LINUX),                       (Platform main application is running on)
					
("true" or "false"),                                         (Perform full translator live test, iterates through each translation method in all settings for each language)
					
("true" or "false"),                                         (Debug Mode: shows logging in terminal)
					
("true" or "false"),                                         (Testing Mode: for when testing)
					
("true" or "false"),                                         (Shows serious errors, even when not in debug mode)
					
("true" or "false"),                                         (Shows ignored/expected errors, for details you wouldn't usually want in the terminal)
					
("true" or "false"),                                         (Site/Application identifier: for application specific folders)
					
"path/to/chosen/dir",                                        (OPTIONAL: User defined path to stor translation/detection models)
					
classes)) {(Array[] of class files containing Translatable objects)

} else {
	throw new Exception("Translator Failed Initiation"); 
}



Step 2 :  Use the Translator / detector (supported and selected languages only)



2A. Methods Paramaters

from:  Language enum representing the language being translated from 

to:  Language enum representing the language being translated to

input:  String or any object implementing Translator.Translatable (objects must @override .toString() to return value intended for translation)

recreateTableEntry:  ("true" or "false") : overwrites database entry

doAsList:  ("true" or "false") : process the whole list of strings in the model at once, or call the model once per string (very slow)



2B. Singular Text/Translatable objects


public static Language detectLanguage(final String input)

public static String translate(final Language to, final Object input)

public static String translate(final Language from, final Language to, final Object input)

public static String translate(final Language from, final Language to, final Object input, final boolean recreateTableEntry)


2C. Lists of texts/Translatable objects


public static <T> Map<T, String> translate(final Language to, final List<T> inputs)

public static <T> Map<T, String> translate(final Language to, final List<T> inputs, final boolean doAsList)

public static <T> Map<T, String> translate(final Language from, final Language to, final List<T> inputs)

public static <T> Map<T, String> translate(final Language from, final Language to, final List<T> inputs, final boolean doAsList)

public static <T> Map<T, String> translate(final Language from, final Language to, final List<T> inputs, final boolean doAsList, final boolean recreateTableEntrys)


2D. Singular and Lists of texts/Translatable objects (Model Only - No Database Lookup)  


public static String translate_OnlyUseModel(final Language to, final Object input)

public static String translate_OnlyUseModel(final Language from, final Language to, final Object input)

public static <T> Map<T, String> translate_OnlyUseModel(final Language to, final List<T> inputs)

public static <T> Map<T, String> translate_OnlyUseModel(final Language from, final Language to, final List<T> inputs, final boolean doAsList)


Step 3 :  Shutdown when your done .....  thats it ! ENJOY!     Thanks for looking at my project    :)


Translator.shutdown();
