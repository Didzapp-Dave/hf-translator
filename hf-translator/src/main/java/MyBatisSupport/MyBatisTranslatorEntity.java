package MyBatisSupport;

import java.io.InputStream;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.hibernate.StaleObjectStateException;
import org.hibernate.exception.LockAcquisitionException;

import didzapp.T_Log;
import didzapp.HF_Translator.Translator;
import didzapp.HF_Translator.Translator.DetectionUtils.Database;
import didzapp.HF_Translator.TranslatorContent.Translatable;
import didzapp.HF_Translator.TranslatorResourcePaths.ToConfigFiles;
import jakarta.persistence.Entity;
import jakarta.persistence.OptimisticLockException;

/**
 * Represents a translation entity stored in the database. This class maps to a
 * table in the database and handles persistence using MyBatis.
 */
@Entity
public class MyBatisTranslatorEntity implements Translator.TranslatorDatabaseManagement {
	/// MyBatis Mapping
	// Column names used for mapping to database columns
	protected static final String Column_id = "id"; //$NON-NLS-1$
	protected static final String Column_version = "version"; //$NON-NLS-1$
	@SuppressWarnings("unused")
	private static final String Column_StringIN = "StringIN"; //$NON-NLS-1$
	@SuppressWarnings("unused")
	private static final String Column_modleCode = "ModelCode"; //$NON-NLS-1$
	@SuppressWarnings("unused")
	private static final String Column_StringOut = "StringOUT"; //$NON-NLS-1$
	@SuppressWarnings("unused")
	private static final String Column_LastUsed = "LastUsed"; //$NON-NLS-1$
	// Entity Fields
	// Primary key field
	private String id;
	// Version field for optimistic locking
	@SuppressWarnings("unused")
	private int version;
	// Input string to be translated or looked for
	private String StringIN;
	// Output string after translation
	private String ModelCode;
	// Output string after translation
	private String StringOUT;
	// Timestamp when the entity was last used
	@SuppressWarnings("unused")
	private Timestamp LastUsed;

	/// Instance Methods
	/**
	 * Default constructor required by MyBatis.
	 */
	public MyBatisTranslatorEntity() {
	}

	/**
	 * Constructor with all fields.
	 */
	public MyBatisTranslatorEntity(String stringIn, String modelCode, String stringOut) {
		this.StringIN = stringIn;
		this.ModelCode = modelCode;
		this.StringOUT = stringOut;
	}

	/**
	 * Initiator for MyBatis Implementation.
	 */
	@Override
	public void init(String configPathOrString) {
		MyBatisUtil.initSessionFactory(configPathOrString);
	}

	/**
	 * Initiator bypass for MyBatis Implementation.
	 */
	@Override
	public void setFrameworkObject(Object object) {
		if (object instanceof SqlSessionFactory) {
			MyBatisUtil.setSessionFactory((SqlSessionFactory) object);
			return;
		}
		throw new IllegalArgumentException(
				"Object Is Not Of Type: SqlSessionFactory"); //$NON-NLS-1$
	}

	/**
	 * Shutdown method for MyBatis Implementation.
	 */
	@Override
	public void shutdown() {
		MyBatisUtil.shutdown();
	}

	/**
	 * Drop table method for MyBatis Implementation.
	 */
	@Override
	public void dropTable() {
		MyBatisUtil.executeInSessionWithRetry(sqlSession -> {
			MyBatisUtil.dropTable(sqlSession);
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
	 * Sets the input string value. Accepts either a String or a Translateable.
	 *
	 * @param StringIN The input value to set
	 * @return This entity instance for chaining
	 */
	@Override
	public MyBatisTranslatorEntity setStringIN(final Object StringIN) {
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
	public MyBatisTranslatorEntity setModelCode(final String ModelCode) {
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
	public MyBatisTranslatorEntity setTranslation(final String StringOUT) {
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
			MyBatisUtil.executeInSessionWithRetry(sqlSession -> {
				MyBatisUtil.TranslatorEntityMapper mapper = sqlSession.getMapper(MyBatisUtil.TranslatorEntityMapper.class);
				this.LastUsed = Translator.quickTimestamp.timestamp();
				if (this.id == null) {
					this.id = generateUniqueID(MyBatisTranslatorEntity.class);
					mapper.insert(this);
				} else {
					mapper.update(this);
					int rowsAffected = mapper.update(this);
					if (rowsAffected == 0) {
						throw new OptimisticLockException(
								"Entity was modified by another transaction"); //$NON-NLS-1$
					}
				}
				sqlSession.commit();
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
			MyBatisUtil.executeInSessionWithRetry(sqlSession -> {
				MyBatisUtil.TranslatorEntityMapper mapper = sqlSession.getMapper(MyBatisUtil.TranslatorEntityMapper.class);
				mapper.delete(this);
				sqlSession.commit();
				T_Log.log("Entity Deleted"); //$NON-NLS-1$
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
	public MyBatisTranslatorEntity getTranslation(final String id) {
		return MyBatisUtil.executeInSessionWithRetry(sqlSession -> {
			MyBatisUtil.TranslatorEntityMapper mapper = sqlSession.getMapper(MyBatisUtil.TranslatorEntityMapper.class);
			MyBatisTranslatorEntity entity = mapper.getById(id);
			if (entity != null) {
				T_Log.log("Entity Found With id: " + id); //$NON-NLS-1$
			}
			return entity;
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
	public MyBatisTranslatorEntity getTranslation(final String modelCode, final Object input) {
		return MyBatisUtil.executeInSessionWithRetry(sqlSession -> {
			if (input != null) {
				MyBatisUtil.TranslatorEntityMapper mapper = sqlSession.getMapper(MyBatisUtil.TranslatorEntityMapper.class);
				String inputValue = (input instanceof String ? (String) input : input.toString());
				List<MyBatisTranslatorEntity> result = mapper.getByModelCodeAndStringIN(modelCode, inputValue);
				List<MyBatisTranslatorEntity> entitiesToRemove = new ArrayList<>();
				MyBatisTranslatorEntity lastValidEntity = null;
				// First pass: identify what to keep and what to remove
				for (final MyBatisTranslatorEntity entity : result) {
					if ((entity.getTranslation() == null) || entity.getTranslation().isEmpty()) {
						entitiesToRemove.add(entity);
					} else {
						if (lastValidEntity != null) {
							entitiesToRemove.add(lastValidEntity);
						}
						lastValidEntity = entity;
					}
				}
				for (final MyBatisTranslatorEntity entityToRemove : entitiesToRemove) {
					mapper.delete(entityToRemove);
				}
				if (lastValidEntity != null) {
					lastValidEntity.LastUsed = Translator.quickTimestamp.timestamp();
					mapper.update(lastValidEntity);
					sqlSession.commit();
					return lastValidEntity;
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
		new MyBatisTranslatorEntity().setModelCode(modelCode)
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
		MyBatisUtil.executeInSessionWithRetry(sqlSession -> {
			MyBatisUtil.TranslatorEntityMapper mapper = sqlSession.getMapper(MyBatisUtil.TranslatorEntityMapper.class);
			MyBatisTranslatorEntity entity = mapper.getById(id);
			if (entity != null) {
				mapper.delete(entity);
				sqlSession.commit();
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
		MyBatisUtil.executeInSessionWithRetry(sqlSession -> {
			if (input != null) {
				MyBatisUtil.TranslatorEntityMapper mapper = sqlSession.getMapper(MyBatisUtil.TranslatorEntityMapper.class);
				String inputValue = (input instanceof String ? (String) input : input.toString());
				List<MyBatisTranslatorEntity> result = mapper.getByModelCodeAndStringIN(modelCode, inputValue);
				for (final MyBatisTranslatorEntity t : result) {
					mapper.delete(t);
				}
				sqlSession.commit();
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
		MyBatisUtil.executeInSessionWithRetry(sqlSession -> {
			MyBatisUtil.TranslatorEntityMapper mapper = sqlSession.getMapper(MyBatisUtil.TranslatorEntityMapper.class);
			int deletedCount = mapper.deleteDuplicates();
			sqlSession.commit();
			T_Log.log("Deleted Duplicate Translations: " + deletedCount); //$NON-NLS-1$
			return null;
		}, 3);
	}

	/**
	 * Deletes translations that haven't been used in the last 360 days.
	 */
	@Override
	public void deleteUnusedTranslations() {
		final Timestamp removeTime = Timestamp.from(Instant.now().minus(360, ChronoUnit.DAYS));
		MyBatisUtil.executeInSessionWithRetry(sqlSession -> {
			MyBatisUtil.TranslatorEntityMapper mapper = sqlSession.getMapper(MyBatisUtil.TranslatorEntityMapper.class);
			int deletedCount = mapper.deleteOlderThan(removeTime);
			sqlSession.commit();
			T_Log.log("Deleted Old Translations: " + deletedCount); //$NON-NLS-1$
			return null;
		}, 3);
	}

	/**
	 * Deletes all translations from the database.
	 */
	@Override
	public void deleteAllTranslations() {
		MyBatisUtil.executeInSessionWithRetry(sqlSession -> {
			MyBatisUtil.TranslatorEntityMapper mapper = sqlSession.getMapper(MyBatisUtil.TranslatorEntityMapper.class);
			int deletedCount = mapper.deleteAll();
			sqlSession.commit();
			T_Log.log("Deleted All Translations: " + deletedCount); //$NON-NLS-1$
			return null;
		}, 3);
	}

	/**
	 * Checks if an id already exists in the database.
	 *
	 * @param Id The id to look for
	 * @return True if the id exists, false otherwise
	 */
	@SuppressWarnings("hiding")
	@Override
	public boolean idExists(final String id) {
		return MyBatisUtil.executeInSessionWithRetry(sqlSession -> {
			MyBatisUtil.TranslatorEntityMapper mapper = sqlSession.getMapper(MyBatisUtil.TranslatorEntityMapper.class);
			int count = mapper.countById(id);
			return Boolean.valueOf(count > 0);
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
				final int randomIndex = MyBatisUtil.secureRandom.nextInt(CHARACTERS.length());
				final char randomChar = CHARACTERS.charAt(randomIndex);
				sb.append(randomChar);
			}
			newId = sb.toString();
		} while (idExists(newId));
		return newId;
	}

	/**
	 * Utility class for managing MyBatis sessions and transactions.
	 */
	private static class MyBatisUtil {
		private static class MyBatisConfigMapper {
			private record DbDetails(String driver, String url) {
			}

			private static final Map<Database, DbDetails> MAPPINGS = new HashMap<>();
			static {
				// These would need to be configured based on your actual database settings
				MAPPINGS.put(
						Database.MARIADB,
						new DbDetails(
								"org.mariadb.jdbc.Driver", //$NON-NLS-1$
								"jdbc:mariadb://localhost:3306/hf-translator")); //$NON-NLS-1$
				MAPPINGS.put(
						Database.MYSQL,
						new DbDetails(
								"com.mysql.cj.jdbc.Driver", //$NON-NLS-1$
								"jdbc:mysql://localhost:3306/hf-translator")); //$NON-NLS-1$
				MAPPINGS.put(
						Database.POSTGRESQL,
						new DbDetails(
								"org.postgresql.Driver", //$NON-NLS-1$
								"jdbc:postgresql://localhost:5432/hf-translator")); //$NON-NLS-1$
				MAPPINGS.put(
						Database.H2,
						new DbDetails(
								"org.h2.Driver", //$NON-NLS-1$
								"jdbc:h2:mem:hf-translator")); //$NON-NLS-1$
			}

			private static DbDetails getDetails() {
				return MAPPINGS.get(Translator.database);
			}
		}

		public interface TranslatorEntityMapper {
			@Select("SELECT * FROM MyBatisTranslatorEntity WHERE id = #{id}")
			MyBatisTranslatorEntity getById(@Param("id") String id);

			@Select("SELECT * FROM MyBatisTranslatorEntity WHERE ModelCode = #{modelCode} AND StringIN = #{stringIN}")
			List<MyBatisTranslatorEntity> getByModelCodeAndStringIN(@Param("modelCode") String modelCode, @Param("stringIN") String stringIN);

			@Insert("""
					INSERT INTO MyBatisTranslatorEntity
					    (id, version, StringIN, ModelCode, StringOUT, LastUsed)
					VALUES
					    (#{id}, 1, #{stringIN}, #{modelCode}, #{stringOUT}, #{lastUsed})
					""")
			@Options(useGeneratedKeys = true, keyProperty = "id")
			void insert(MyBatisTranslatorEntity entity);

			@Update("""
					UPDATE MyBatisTranslatorEntity
					SET
					    version = version + 1,
					    StringIN = #{stringIN},
					    ModelCode = #{modelCode},
					    StringOUT = #{stringOUT},
					    LastUsed = #{lastUsed}
					WHERE
					    id = #{id}
					    AND version = #{version}
					""")
			int update(MyBatisTranslatorEntity entity);

			@Delete("""
					DELETE FROM MyBatisTranslatorEntity
					WHERE id = #{id}
					  AND version = #{version}
					""")
			int delete(MyBatisTranslatorEntity entity);

			@Select("SELECT COUNT(*) FROM MyBatisTranslatorEntity WHERE id = #{id}")
			int countById(@Param("id") String id);

			@Delete("""
					DELETE FROM MyBatisTranslatorEntity
					WHERE id IN (
					    SELECT id FROM (
					        SELECT id, ROW_NUMBER() OVER (
					            PARTITION BY ModelCode, StringIN
					            ORDER BY id DESC
					        ) as rn
					        FROM MyBatisTranslatorEntity
					    ) t
					    WHERE rn > 1
					)
					""")
			int deleteDuplicates();

			@Delete("DELETE FROM MyBatisTranslatorEntity WHERE LastUsed < #{cutoff}")
			int deleteOlderThan(@Param("cutoff") Timestamp cutoff);

			@Delete("DELETE FROM MyBatisTranslatorEntity")
			int deleteAll();

			@Delete("DROP TABLE IF EXISTS MyBatisTranslatorEntity")
			void dropTable();
		}

		// Default config from jar resource paths
		private static String cfg = ToConfigFiles.libmybatis;
		// Session factory used to create sessions
		private static SqlSessionFactory sessionFactory;
		// Secure random instance for generating unique IDs
		private static final SecureRandom secureRandom = new SecureRandom();

		/**
		 * Sets the MyBatis session factory.
		 */
		private static synchronized void setSessionFactory(SqlSessionFactory newSessionFactory) {
			if (sessionFactory == null) {
				sessionFactory = newSessionFactory;
			}
		}

		/**
		 * Initializes the MyBatis session factory.
		 *
		 * @param configPathOrString Path to config file
		 */
		private static synchronized void initSessionFactory(String configPathOrString) {
			if (sessionFactory == null) {
				try {
					T_Log.log("Initializing MyBatis Session Factory: " + MyBatisTranslatorEntity.class.getSimpleName()); //$NON-NLS-1$
					MyBatisConfigMapper.DbDetails details = MyBatisConfigMapper.getDetails();
					PooledDataSource dataSource = new PooledDataSource();
					dataSource.setDriver(details.driver());
					dataSource.setUrl(details.url());
					if (configPathOrString != null) {
						cfg = configPathOrString;
						// Load the MyBatis configuration
						try (InputStream reader = Thread.currentThread().getContextClassLoader().getResourceAsStream(cfg)) {
							XMLConfigBuilder parser = new XMLConfigBuilder(
									reader);
							Configuration configuration = parser.parse();
							// Override the environment's datasource
							Environment environment = new Environment(
									configuration.getEnvironment().getId(),
									configuration.getEnvironment().getTransactionFactory(),
									dataSource);
							configuration.setEnvironment(environment);
							sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
						}
					} else {
						TransactionFactory transactionFactory = new JdbcTransactionFactory();
						Environment environment = new Environment(
								"development", //$NON-NLS-1$
								transactionFactory,
								dataSource);
						Configuration configuration = new Configuration(
								environment);
						configuration.addMapper(TranslatorEntityMapper.class);
						configuration.setMapUnderscoreToCamelCase(true);
						sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
					}
					T_Log.log("Initialized successfully."); //$NON-NLS-1$
				} catch (Exception e) {
					T_Log.log("Error initializing MyBatis Session Factory: " + MyBatisTranslatorEntity.class.getSimpleName(), e); //$NON-NLS-1$
				}
			}
		}

		/**
		 * Drops a table from the database.
		 *
		 * @param sqlSession The current SqlSession session
		 */
		private static synchronized void dropTable(final SqlSession sqlSession) {
			TranslatorEntityMapper mapper = sqlSession.getMapper(TranslatorEntityMapper.class);
			mapper.dropTable();
			T_Log.log("Table Deleted"); //$NON-NLS-1$
		}

		/**
		 * Shuts down all MyBatis session factories.
		 */
		private static synchronized void shutdown() {
			T_Log.log("MyBatis Has shutdown"); //$NON-NLS-1$
		}

		/**
		 * Executes an action within a MyBatis session with retry logic.
		 *
		 * @param action     The function to execute
		 * @param maxRetries Maximum number of retries
		 * @param <T>        Return type of the action
		 * @return Result of the action
		 */
		private static <T> T executeInSessionWithRetry(final Function<SqlSession, T> action, final int maxRetries) {
			int retryCount = 0;
			while (retryCount <= maxRetries) {
				SqlSession sqlSession = null;
				try {
					sqlSession = sessionFactory.openSession(TransactionIsolationLevel.READ_COMMITTED);
					final T result = action.apply(sqlSession);
					sqlSession.commit();
					return result;
				} catch (final Exception e) {
					if (!(e instanceof StaleObjectStateException) && !(e instanceof LockAcquisitionException)) {
						if (sqlSession != null) {
							try {
								sqlSession.rollback();
							} catch (final Exception rollbackException) {
								T_Log.log("Rollback failed: ", rollbackException); //$NON-NLS-1$
							}
						}
						if (e instanceof OptimisticLockException) {
							retryCount++;
							if (retryCount > maxRetries) {
								T_Log.log("Max retries reached for optimistic locking: ", e); //$NON-NLS-1$
								throw e;
							}
							T_Log.log(
									"Optimistic locking conflict. Retrying attempt " + retryCount, //$NON-NLS-1$
									e);
						}
						throw e;
					}
					retryCount++;
					if ((sqlSession != null)) {
						try {
							sqlSession.rollback();
						} catch (final Exception rollbackException) {
							T_Log.log(
									"Error During Transaction Rollback", //$NON-NLS-1$
									rollbackException);
							throw rollbackException;
						}
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
				} finally {
					if (sqlSession != null) {
						sqlSession.close();
					}
				}
			}
			throw new IllegalStateException(
					"Transaction failed after retries"); //$NON-NLS-1$
		}
	}
}