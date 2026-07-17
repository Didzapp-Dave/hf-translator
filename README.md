How to use 'hf-translator' 




Built-in static logging for testing and development purposes, usable by the developer

Example:

T_Log.log("Message Goes Here", exception (Optional), ignored (Optional : "true" or "false"));

Message : any message

Exception : any Exception

Ignored : If ignored is true, exception and message will not show in logs UNLESS debug and showIgnored are both true

Settings:

T_Log.debug =  ("true" or "false") (Default: false)                                   (Shows log messages and exceptions in terminal) 

T_Log.testing =  ("true" or "false") (Default: false)                                 (If debug is true, synchronizes logs, causes bottle-kneck, keeps logs readable) 

T_Log.showCritical =  ("true" or "false") (Default: false)                            (If debug is false, shows unecpected exceptions in logs)

T_Log.showIgnored =  ("true" or "false") (Default: false)                             (If debug is true, shows expected exceptions in logs) 




Hibernate database default creds : 

user - hf-translator

password - hf-translator

database name - hf-translator

tables create themselves

Public HibernateTranslatorEntity(final String stringIn, final String modelCode, final String translation)

Public HibernateTranslatorEntity()



Mongo database default creds : 

connection string  - mongodb://localhost:27017

database name - hf-translator

tables create themselves

Public MongoDBTranslatorEntity(final String stringIn, final String modelCode, final String translation)

Public MongoDBTranslatorEntity()




hf-translator will auto-detect MongoDB framework and its companion database 

or 

Hibernate framework combined with these databases: MariaDB, MySQL, PostgreSQL, H2





Step 1 :  Init the Translator (returns a boolean)



if (Translator.init(

framework_object, (Object)                                   (OPTIONAL: Your own database management object, SessionFactory or MongoClient) 
					
config_path_or_string, (String)                              (OPTIONAL: Your custom configuration file path or connection string) 
					
default_language, (Locale.forLanguageTag("en"))              (OPTIONAL: Default language  /  null = English by default)
					
language_selection, (List<Locale>)                           (OPTIONAL: List<Locale> of selected languages or Language.usableValues() for all*  /  null = all* by default)
					
run_language_detector, ("true" or "false")                   (If true: run language detector as service(faster). If false: it runs and turns off)
					
universal_translation_mode, ("true" or "false")              (If true: support translations between all languages. If false: only translate between supported languages and default)
					
model_reset, ("true" or "false")                             (Re-download models at startup)

translation_reset, ("true" or "false")                       (Re-translate content at startup)

feed_content, ("true" or "false")                            (Feed content to database at startup)
					
platform, (Translator.Platform.WINDOWS / LINUX)              (Platform main application is running on)
					
application_id, ("true" or "false")                          (Site/Application identifier: for application specific folder naming)
					
model_storage_path, ("path/to/chosen/dir")                   (NULLABLE: User defined path to store translation/detection models)
                                                        	 (DEFAULT for WINDOWS: System.getProperty("user.home") + "\\AppData\\Local\\didzappsoftware\\")
															 (DEFAULT for LINUX: System.getProperty("user.home") + "/.local/share/didzappsoftware/")

content_classes... (Class<?>[])                              (Class<?>[] of class files containing Translatable Objects, Enums or Strings)

} else {
	throw new Exception("Translator Failed Initiation"); 
}





Step 2 :  Use the Translator / detector (supported and selected languages only)



2A. Methods Paramaters

from:  Language enum representing the language being translated from 

to:  Language enum representing the language being translated to

input:  String or any object implementing Translator.Translatable (Objects must @override .toString() to return value intended for translation)

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



2E. Multiple Lists of texts/Translatable objects and single texts combined (same usage as the static methods, the boolean params are now setter methods)  


  
public static class TranslateStacker

2E.a. Setters / Add content

public void setReCreate(final boolean reCreate)

public void setProcessAsList(final boolean doAsList)

public TranslateStacker add(final String text)

public TranslateStacker add(final Translatable text)

public TranslateStacker addAll(final Translatable[] texts)

public TranslateStacker addAll(final String[] texts) 

2E.b. Translation methods (returns translated values)

public <T> Map<T, String> translate(final Language to)

public <T> Map<T, String> translate(final Language from, final Language to)

public <T> Map<T, String> translate_ModelOnly(final Language to)

public <T> Map<T, String> translate_ModelOnly(final Language from, final Language to)

public <T> Map<T, String> translateAndFeedTranslatorDatabase(final Language to)

public <T> Map<T, String> translateAndFeedTranslatorDatabase(final Language from, final Language to)

2E.c. Translate and store in database for future translation, (no return values)

public void feedTranslatorDatabase() 





Step 3 :  Shutdown when your done .....  thats it ! ENJOY!     Thanks for looking at my project    :)



Translator.shutdown();
