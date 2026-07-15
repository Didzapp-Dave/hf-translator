package MongoDBSupport;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.Function;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import didzapp.T_Log;
import didzapp.HF_Translator.Translator;
import didzapp.HF_Translator.Translator.TranslatorDatabaseManagement;
import didzapp.HF_Translator.TranslatorContent.Translatable;

/**
 * Represents a translation entity stored in MongoDB. This class maps to a
 * collection in the database and handles persistence using MongoDB Java Driver.
 */
public class MongoDBTranslatorEntity implements TranslatorDatabaseManagement {
	// Collection name used for storing translation entities
	protected static final String COLLECTION_NAME = "translations"; //$NON-NLS-1$
	/// MongoDb Mapping
	// Field names used for mapping to document fields
	protected static final String Field_id = "_id"; //$NON-NLS-1$
	protected static final String Field_version = "version"; //$NON-NLS-1$
	protected static final String Field_StringIN = "StringIN"; //$NON-NLS-1$
	protected static final String Field_Model_Code = "ModelCode"; //$NON-NLS-1$
	protected static final String Field_StringOUT = "StringOUT"; //$NON-NLS-1$
	protected static final String Field_LastUsed = "LastUsed"; //$NON-NLS-1$
	// Document Fields
	// Primary key field
	private String id;
	// Version field for optimistic locking
	private int version;
	// Input string to be translated or looked for
	private String StringIN;
	// Output string after translation
	private String ModelCode;
	// Output string after translation
	private String StringOUT;
	// Timestamp when the entity was last used
	private Date LastUsed;

	// Instance Methods
	/**
	 * Default constructor required.
	 */
	public MongoDBTranslatorEntity() {
	}

	/**
	 * Constructor with all fields.
	 */
	private MongoDBTranslatorEntity(String id, int version, String stringIn, String modelCode, String stringOut) {
		this.id = id;
		this.version = version;
		this.StringIN = stringIn;
		this.ModelCode = modelCode;
		this.StringOUT = stringOut;
	}

	/**
	 * Initiator for MongoDB Implementation.
	 */
	@Override
	public void init(String configPathOrString) {
		MongoDBUtil.initMongoClient(configPathOrString);
	}

	/**
	 * Initiator bypass for MongoDB Implementation.
	 */
	@Override
	public void setFrameworkObject(Object object) {
		if (object instanceof MongoClient) {
			MongoDBUtil.setMongoClient((MongoClient) object);
			return;
		}
		throw new IllegalArgumentException(
				"Object Is Not Of Type: MongoClient"); //$NON-NLS-1$
	}

	/**
	 * Shutdown method for MongoDB Implementation.
	 */
	@Override
	public void shutdown() {
		MongoDBUtil.shutdown();
	}

	/**
	 * Drop table method for MongoDB Implementation.
	 */
	@Override
	public void dropTable() {
		MongoDBUtil.executeWithRetry(mongoDatabase -> {
			MongoDBUtil.dropTable(mongoDatabase);
			return null;
		}, 3);
	}

	/**
	 * Gets the entity id if it has been saved.
	 *
	 * @return The entity id if it has been saved
	 */
	@Override
	public String getId() {
		return this.id;
	}

	/**
	 * Sets the input string value. Accepts either a String or a Translatable.
	 *
	 * @param StringIN The input value to set
	 * @return This entity instance for chaining
	 */
	@Override
	public MongoDBTranslatorEntity setStringIN(final Object StringIN) {
		if (StringIN instanceof String || StringIN instanceof Translatable) {
			this.StringIN = (StringIN instanceof String ? (String) StringIN : StringIN.toString());
			return this;
		}
		return null;
	}

	/**
	 * Sets the model code, this is the local code from the language object. (e.g.,
	 * Language.ENGLISH.getLocale().getLanguage(), "en")
	 *
	 * @param ModelCode The model code to set
	 * @return This entity instance for chaining
	 */
	@Override
	public MongoDBTranslatorEntity setModelCode(final String ModelCode) {
		this.ModelCode = ModelCode;
		return this;
	}

	/**
	 * Sets the output string.
	 *
	 * @param StringOUT The output string to set
	 * @return This entity instance for chaining
	 */
	@Override
	public MongoDBTranslatorEntity setTranslation(final String StringOUT) {
		this.StringOUT = StringOUT;
		return this;
	}

	/**
	 * Gets the output string value.
	 *
	 * @return The output string
	 */
	@Override
	public String getTranslation() {
		return this.StringOUT;
	}

	/**
	 * Saves this entity to the database. If no id exists, generates one. Otherwise,
	 * updates the existing record.
	 */
	@Override
	public void save() {
		if ((this.ModelCode != null) && (this.StringIN != null) && (this.StringOUT != null)) {
			MongoDBUtil.executeWithRetry(mongoDatabase -> {
				this.LastUsed = Date.from(Instant.now());
				MongoCollection<Document> collection = mongoDatabase.getCollection(COLLECTION_NAME);
				Document document = new Document().append(Field_version, Integer.valueOf(this.version))
						.append(Field_StringIN, this.StringIN)
						.append(Field_Model_Code, this.ModelCode)
						.append(Field_StringOUT, this.StringOUT)
						.append(Field_LastUsed, this.LastUsed);
				if (this.id == null) {
					MongoDBTranslatorEntity existing = getTranslation(this.ModelCode, this.StringIN);
					if (existing != null) {
						this.id = existing.id;
					} else {
						this.id = generateUniqueID(MongoDBTranslatorEntity.class);
					}
					this.version = 0;
					document.append(Field_id, this.id);
					document.append(Field_version, Integer.valueOf(this.version));
					collection.insertOne(document);
				} else {
					document.append(Field_id, this.id);
					int oldVersion = this.version;
					document.append(Field_version, Integer.valueOf(oldVersion + 1));
					UpdateResult result = collection.replaceOne(
							Filters.and(Filters.eq(Field_id, this.id), Filters.eq(Field_version, Integer.valueOf(oldVersion))),
							document);
					if (result.getMatchedCount() == 0) {
						throw new MongoDBUtil.OptimisticLockingException(
								"Document was modified by another process. Expected version: " + oldVersion); //$NON-NLS-1$
					}
				}
				T_Log.log("Entity Saved"); //$NON-NLS-1$
				return null;
			}, 3);
		} else {
			T_Log.log("Save Failed: Null Vars"); //$NON-NLS-1$
		}
	}

	/**
	 * Deletes this entity from the database.
	 */
	@Override
	public void delete() {
		if (this.id != null) {
			MongoDBUtil.executeWithRetry(mongoDatabase -> {
				MongoCollection<Document> collection = mongoDatabase.getCollection(COLLECTION_NAME);
				DeleteResult result = collection.deleteOne(Filters.eq(Field_id, this.id));
				if (result.getDeletedCount() > 0) {
					T_Log.log("Entity Deleted"); //$NON-NLS-1$
				} else {
					T_Log.log("Delete Failed: No Entity Found"); //$NON-NLS-1$
				}
				return null;
			}, 3);
		} else {
			T_Log.log("Delete Failed: No id / This Entity Was Never Saved"); //$NON-NLS-1$
		}
	}
	/// Static Acting Methods

	/**
	 * Gets a translation by its id.
	 *
	 * @param id The id to search for
	 * @return The entity if found, or null
	 */
	@SuppressWarnings("hiding")
	@Override
	public MongoDBTranslatorEntity getTranslation(final String id) {
		return MongoDBUtil.executeWithRetry(mongoDatabase -> {
			MongoCollection<Document> collection = mongoDatabase.getCollection(COLLECTION_NAME);
			Document doc = collection.find(Filters.eq(Field_id, id)).first();
			if (doc != null) {
				MongoDBTranslatorEntity entity = documentToEntity(doc);
				T_Log.log("Entity Found With id: " + id); //$NON-NLS-1$
				return entity;
			}
			return null;
		}, 3);
	}

	/**
	 * Retrieves a translation from the database and deletes duplicates.
	 *
	 * @param modelCode The model code to search for
	 * @param input     The input value to search for
	 * @return The matching entity or null if not found
	 */
	@Override
	public MongoDBTranslatorEntity getTranslation(final String modelCode, final Object input) {
		return MongoDBUtil.executeWithRetry(mongoDatabase -> {
			if (input != null) {
				String inputStr = input instanceof String ? (String) input : input.toString();
				MongoCollection<Document> collection = mongoDatabase.getCollection(COLLECTION_NAME);
				// Find all matching documents
				List<Bson> filters = new ArrayList<>();
				filters.add(Filters.eq(Field_Model_Code, modelCode));
				filters.add(Filters.eq(Field_StringIN, inputStr));
				List<Document> results = collection.find(Filters.and(filters)).into(new ArrayList<>());
				List<Document> documentsToRemove = new ArrayList<>();
				Document lastValidDocument = null;
				// First pass: identify what to keep and what to remove
				for (Document doc : results) {
					String translation = doc.getString(Field_StringOUT);
					if ((translation == null) || translation.isEmpty()) {
						documentsToRemove.add(doc);
					} else {
						if (lastValidDocument != null) {
							documentsToRemove.add(lastValidDocument);
						}
						lastValidDocument = doc;
					}
				}
				// Remove invalid documents
				for (Document docToRemove : documentsToRemove) {
					collection.deleteOne(Filters.eq(Field_id, docToRemove.getString(Field_id)));
				}
				if (lastValidDocument != null) {
					// Update last used timestamp
					Date now = Translator.quickTimestamp.timestamp();
					collection.updateOne(
							Filters.eq(Field_id, lastValidDocument.getString(Field_id)),
							new Document(
									"$set", //$NON-NLS-1$
									new Document(
											Field_LastUsed,
											now)));
					MongoDBTranslatorEntity entity = documentToEntity(lastValidDocument);
					entity.LastUsed = now;
					return entity;
				}
			}
			return null;
		}, 3);
	}

	/**
	 * Saves a new translation to the database.
	 *
	 * @param modelCode        The model code
	 * @param input            The input value
	 * @param translatedString The translated output string
	 */
	@Override
	public void save(final String modelCode, final Object input, final String translatedString) {
		new MongoDBTranslatorEntity().setModelCode(modelCode)
				.setStringIN((input instanceof String ? (String) input : input.toString()))
				.setTranslation(translatedString)
				.save();
		T_Log.log("Translation Added To Database"); //$NON-NLS-1$
	}

	/**
	 * Deletes an entity from the database by its id.
	 *
	 * @param id The id of the entity to delete
	 */
	@SuppressWarnings("hiding")
	@Override
	public void delete(final String id) {
		MongoDBUtil.executeWithRetry(mongoDatabase -> {
			MongoCollection<Document> collection = mongoDatabase.getCollection(COLLECTION_NAME);
			DeleteResult result = collection.deleteOne(Filters.eq(Field_id, id));
			if (result.getDeletedCount() > 0) {
				T_Log.log("Entity Deleted"); //$NON-NLS-1$
			} else {
				T_Log.log("Delete Failed: No Entity Found"); //$NON-NLS-1$
			}
			return null;
		}, 3);
	}

	/**
	 * Deletes all translations matching the given model code and input.
	 *
	 * @param modelCode The model code
	 * @param input     The input value
	 */
	@Override
	public void delete(final String modelCode, final Object input) {
		MongoDBUtil.executeWithRetry(mongoDatabase -> {
			if (input != null) {
				String inputStr = input instanceof String ? (String) input : input.toString();
				MongoCollection<Document> collection = mongoDatabase.getCollection(COLLECTION_NAME);
				List<Bson> filters = new ArrayList<>();
				filters.add(Filters.eq(Field_Model_Code, modelCode));
				filters.add(Filters.eq(Field_StringIN, inputStr));
				collection.deleteMany(Filters.and(filters));
			}
			return null;
		}, 3);
	}

	/// Static Acting Utility
	/**
	 * Deletes duplicate translations (keeps only the latest one).
	 */
	@Override
	public void deleteDuplicateTranslations() {
		MongoDBUtil.executeWithRetry(mongoDatabase -> {
			MongoCollection<Document> collection = mongoDatabase.getCollection(COLLECTION_NAME);
			// This is a simplified approach to removing duplicates in MongoDB
			// A more complex aggregation pipeline would be needed for exact behavior
			List<Document> allDocs = collection.find().into(new ArrayList<>());
			List<String> seenKeys = new ArrayList<>();
			int deletedCount = 0;
			for (Document doc : allDocs) {
				String key = doc.getString(Field_Model_Code) + "|" + doc.getString(Field_StringIN); //$NON-NLS-1$
				if (seenKeys.contains(key)) {
					// Delete this duplicate
					collection.deleteOne(Filters.eq(Field_id, doc.getString(Field_id)));
					deletedCount++;
				} else {
					seenKeys.add(key);
				}
			}
			T_Log.log("Deleted Duplicate Translations: " + deletedCount); //$NON-NLS-1$
			return null;
		}, 3);
	}

	/**
	 * Deletes translations that haven't been used in the last 360 days.
	 */
	@Override
	public void deleteUnusedTranslations() {
		Date removeTime = Date.from(Instant.now().minus(360, ChronoUnit.DAYS));
		MongoDBUtil.executeWithRetry(mongoDatabase -> {
			MongoCollection<Document> collection = mongoDatabase.getCollection(COLLECTION_NAME);
			DeleteResult result = collection.deleteMany(Filters.lt(Field_LastUsed, removeTime));
			T_Log.log("Deleted Old Translations: " + result.getDeletedCount()); //$NON-NLS-1$
			return null;
		}, 3);
	}

	/**
	 * Deletes all translations from the database.
	 */
	@Override
	public void deleteAllTranslations() {
		MongoDBUtil.executeWithRetry(mongoDatabase -> {
			MongoCollection<Document> collection = mongoDatabase.getCollection(COLLECTION_NAME);
			long count = collection.countDocuments();
			collection.deleteMany(new Document());
			T_Log.log("Deleted All Translations: " + count); //$NON-NLS-1$
			return null;
		}, 3);
	}

	/**
	 * Checks if an id already exists in the database.
	 *
	 * @param id The id to look for
	 * @return True if the id exists, false otherwise
	 */
	@SuppressWarnings("hiding")
	@Override
	public boolean idExists(final String id) {
		return MongoDBUtil.executeWithRetry(mongoDatabase -> {
			MongoCollection<Document> collection = mongoDatabase.getCollection(COLLECTION_NAME);
			Document doc = collection.find(Filters.eq(Field_id, id)).first();
			return Boolean.valueOf(doc != null);
		}, 3).booleanValue();
	}

	/**
	 * Generates a unique id for this entity type. Ensures the generated id does not
	 * already exist in the database.
	 *
	 * @param entityClass The class of the entity (used to check existence)
	 * @return A unique string id
	 */
	@Override
	public String generateUniqueID(final Class<?> entityClass) {
		final String CHARACTERS = "abcdefghijklmnopqrstuvqxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"; //$NON-NLS-1$
		final int id_Length = 7;
		String newId;
		do {
			final StringBuilder sb = new StringBuilder(
					id_Length);
			for (int i = 0; i < id_Length; i++) {
				final int randomIndex = MongoDBUtil.secureRandom.nextInt(CHARACTERS.length());
				final char randomChar = CHARACTERS.charAt(randomIndex);
				sb.append(randomChar);
			}
			newId = sb.toString();
		} while (idExists(newId));
		return newId;
	}

	/**
	 * Converts a MongoDB Document to a MongoDbTranslatorEntity.
	 *
	 * @param doc The document to convert
	 * @return The entity
	 */
	private static MongoDBTranslatorEntity documentToEntity(Document doc) {
		return new MongoDBTranslatorEntity(
				doc.getString(Field_id),
				doc.getInteger(Field_version).intValue(),
				doc.getString(Field_StringIN),
				doc.getString(Field_Model_Code),
				doc.getString(Field_StringOUT));
	}

	private class MongoDBUtil {
		// Default connection string (should be configured properly)
		private static String connectionString = "mongodb://localhost:27017"; //$NON-NLS-1$
		// Database name
		private static final String DATABASE_NAME = "hf-translator"; //$NON-NLS-1$
		// Mongo client used to connect to the database
		private static MongoClient mongoClient = null;
		// Secure random instance for generating unique IDs
		private static final SecureRandom secureRandom = new SecureRandom();

		/**
		 * Sets the MongoDB client.
		 *
		 * @param client The MongoDB client to set
		 */
		private static synchronized void setMongoClient(MongoClient client) {
			if (mongoClient == null) {
				mongoClient = client;
			}
		}

		/**
		 * Initializes the MongoDB client.
		 *
		 * @param configPathOrString String to configure MongoDB connection
		 */
		private static synchronized void initMongoClient(String configPathOrString) {
			if (mongoClient == null) {
				T_Log.log("Initializing MongoDB Client"); //$NON-NLS-1$
				try {
					if (configPathOrString != null) {
						connectionString = configPathOrString;
					}
					mongoClient = MongoClients.create(connectionString);
					T_Log.log("Initialized successfully."); //$NON-NLS-1$
				} catch (Exception e) {
					T_Log.log("Error initializing MongoDB Client", e); //$NON-NLS-1$
				}
			}
		}

		/**
		 * Drops a table from the database.
		 *
		 * @param hibernateSession The current Hibernate session
		 */
		private static synchronized void dropTable(final MongoDatabase mongoDatabase) {
			mongoDatabase.getCollection(COLLECTION_NAME).drop();
			T_Log.log("Collection Dropped"); //$NON-NLS-1$
		}

		/**
		 * Shuts down the MongoDB client.
		 */
		private static synchronized void shutdown() {
			try {
				if (mongoClient != null) {
					mongoClient.close();
					T_Log.log("MongoClient Shutdown"); //$NON-NLS-1$
				}
			} catch (final Exception e) {
				T_Log.log("Error During MongoClient Shutdown: ", e); //$NON-NLS-1$
			}
		}

		/**
		 * Executes an action with retry logic.
		 *
		 * @param action     The function to execute
		 * @param maxRetries Maximum number of retries
		 * @param <T>        Return type of the action
		 * @return Result of the action
		 */
		private static <T> T executeWithRetry(final Function<MongoDatabase, T> action, final int maxRetries) {
			int retryCount = 0;
			while (retryCount <= maxRetries) {
				try {
					MongoDatabase database = mongoClient.getDatabase(DATABASE_NAME);
					return action.apply(database);
				} catch (final Exception e) {
					retryCount++;
					if (e instanceof OptimisticLockingException) {
						if (retryCount > maxRetries) {
							T_Log.log("Max Retries Reached for Optimistic Locking: ", e); //$NON-NLS-1$
							throw e;
						}
						T_Log.log("Optimistic locking conflict. Retrying... Attempt " + retryCount, e); //$NON-NLS-1$
						try {
							Thread.sleep(retryCount * 100L);
						} catch (final InterruptedException ie) {
							T_Log.log("Unexpected Error During Maintenance Thread Sleep: ", ie); //$NON-NLS-1$
							Thread.currentThread().interrupt();
							break;
						}
						continue; // Retry the operation
					}
					if (retryCount > maxRetries) {
						T_Log.log(
								"Max Retries Reached For Optimistic Lock Conflict: ", //$NON-NLS-1$
								e);
						throw e;
					}
					T_Log.log(
							"Optimistic Locking Conflict Detected. Retrying... Attempt " + retryCount, //$NON-NLS-1$
							e);
					try {
						Thread.sleep(retryCount * 100L);
					} catch (final InterruptedException ie) {
						T_Log.log(
								"Unexpected Error During Maintenance Thread Sleep: ", //$NON-NLS-1$
								ie);
						Thread.currentThread().interrupt();
						break;
					}
					throw e;
				}
			}
			throw new IllegalStateException(
					"Operation Failed After Retries Attempted"); //$NON-NLS-1$
		}

		private static class OptimisticLockingException extends RuntimeException {
			private static final long serialVersionUID = 1L;

			private OptimisticLockingException() {
				super();
			}

			private OptimisticLockingException(final String message) {
				super(
						message);
			}

			private OptimisticLockingException(final String message, final Throwable cause) {
				super(
						message,
						cause);
			}

			private OptimisticLockingException(final Throwable cause) {
				super(
						cause);
			}
		}
	}
}