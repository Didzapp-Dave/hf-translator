package HibernateSupport;

import java.security.SecureRandom;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.StaleObjectStateException;
import org.hibernate.Transaction;
import org.hibernate.TypeMismatchException;
import org.hibernate.cfg.Configuration;
import org.hibernate.exception.LockAcquisitionException;
import org.hibernate.query.MutationQuery;
import org.hibernate.query.Query;

import didzapp.T_Log;
import didzapp.HF_Translator.Translator;
import didzapp.HF_Translator.Translator.DetectionUtils.Database;
import didzapp.HF_Translator.Translator.TranslatorDatabaseManagement;
import didzapp.HF_Translator.TranslatorContent.Translatable;
import didzapp.HF_Translator.TranslatorResourcePaths.ToConfigFiles;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.Version;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

/**
 * Represents a translation entity stored in the database. This class maps to a
 * table in the database and handles persistence using Hibernate.
 */
@Entity
public class HibernateTranslatorEntity implements TranslatorDatabaseManagement {
	/// Hibernate Mapping
	// Column names used for mapping to database columns
	protected static final String Column_id = "id"; //$NON-NLS-1$
	protected static final String Column_version = "version"; //$NON-NLS-1$
	private static final String Column_StringIN = "StringIN"; //$NON-NLS-1$
	private static final String Column_modleCode = "ModelCode"; //$NON-NLS-1$
	private static final String Column_StringOut = "StringOUT"; //$NON-NLS-1$
	private static final String Column_LastUsed = "LastUsed"; //$NON-NLS-1$
	// Entity Columns
	// Primary key field
	@Id
	@Column(name = Column_id, nullable = false, length = 50, columnDefinition = "VARCHAR(50) NOT NULL COLLATE utf8mb4_bin")
	private String id;
	// Version field for optimistic locking
	@Version
	@Column(name = Column_version, nullable = false)
	private int version;
	// Input string to be translated or looked for
	@Column(name = Column_StringIN, nullable = false, length = 5000, columnDefinition = "VARCHAR(5000) NOT NULL COLLATE utf8mb4_bin")
	private String StringIN;
	// Output string after translation
	@Column(name = Column_modleCode, nullable = false, length = 255, columnDefinition = "VARCHAR(50) NOT NULL COLLATE utf8mb4_bin")
	private String ModelCode;
	// Output string after translation
	@Column(name = Column_StringOut, nullable = false, length = 5000, columnDefinition = "VARCHAR(5000) NOT NULL COLLATE utf8mb4_bin")
	private String StringOUT;
	// Timestamp when the entity was last used
	@Column(name = Column_LastUsed, nullable = false)
	private Timestamp LastUsed;

	/// Instance Methods
	/**
	 * Default constructor required by Hibernate.
	 */
	public HibernateTranslatorEntity() {
	}

	/**
	 * Constructor with all fields.
	 */
	public HibernateTranslatorEntity(final String stringIn, final String modelCode, final String translation) {
		this.StringIN = stringIn;
		this.ModelCode = modelCode;
		this.StringOUT = translation;
	}

	/**
	 * Initiator for Hibernate Implementation.
	 */
	@Override
	public void init(final String configPathOrString) {
		HibernateUtil.initSessionFactory(configPathOrString);
	}

	/**
	 * Initiator bypass for Hibernate Implementation.
	 */
	@Override
	public void setFrameworkObject(final Object object) {
		if (object instanceof SessionFactory) {
			HibernateUtil.setSessionFactory((SessionFactory) object);
			return;
		}
		throw new TypeMismatchException(
				"Object Is Not Of Type: SessionFactory"); //$NON-NLS-1$
	}

	/**
	 * Shutdown method for Hibernate Implementation.
	 */
	@Override
	public void shutdown() {
		HibernateUtil.shutdown();
	}

	/**
	 * Drop table method for Hibernate Implementation.
	 */
	@Override
	public void dropTable() {
		HibernateUtil.createSessionAndExecuteTransactionWithRetry(hibernateSession -> {
			HibernateUtil.dropTable(hibernateSession);
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
	public HibernateTranslatorEntity setStringIN(final Object StringIN) {
		if ((StringIN instanceof String) || (StringIN instanceof Translatable)) {
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
	public HibernateTranslatorEntity setModelCode(final String ModelCode) {
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
	public HibernateTranslatorEntity setTranslation(final String StringOUT) {
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
			HibernateUtil.createSessionAndExecuteTransactionWithRetry(hibernateSession -> {
				this.LastUsed = Translator.quickTimestamp.timestamp();
				if (this.id == null) {
					final HibernateTranslatorEntity existing = this.getTranslation(this.ModelCode, this.StringIN);
					if (existing != null) {
						this.id = existing.id;
					} else {
						this.id = this.generateUniqueID(HibernateTranslatorEntity.class);
					}
					hibernateSession.persist(this);
				} else {
					hibernateSession.merge(this);
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
			HibernateUtil.createSessionAndExecuteTransactionWithRetry(hibernateSession -> {
				hibernateSession.remove(this);
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
	public HibernateTranslatorEntity getTranslation(final String id) {
		HibernateUtil.createSessionAndExecuteTransactionWithRetry(hibernateSession -> {
			final HibernateTranslatorEntity entity = hibernateSession.get(HibernateTranslatorEntity.class, id);
			T_Log.log("Entity Found With id: " + id); //$NON-NLS-1$
			return entity;
		}, 3);
		return null;
	}

	/**
	 * Retrieves a translation from the database and deletes duplicates.
	 *
	 * @param modelCode The model code to search for
	 * @param input     The input value to search for
	 * @return The matching entity or null if not found
	 */
	@Override
	public HibernateTranslatorEntity getTranslation(final String modelCode, final Object input) {
		return HibernateUtil.createSessionAndExecuteTransactionWithRetry(hibernateSession -> {
			if (input != null) {
				final boolean isString = input instanceof String;
				final CriteriaBuilder cb = hibernateSession.getCriteriaBuilder();
				final CriteriaQuery<HibernateTranslatorEntity> cq = cb.createQuery(HibernateTranslatorEntity.class);
				final Root<HibernateTranslatorEntity> root = cq.from(HibernateTranslatorEntity.class);
				cq.select(root)
						.where(
								cb.equal(root.get(Column_modleCode), modelCode),
								cb.equal(root.get(Column_StringIN), (isString ? (String) input : input.toString())));
				final List<HibernateTranslatorEntity> result = hibernateSession.createQuery(cq).getResultList();
				final List<HibernateTranslatorEntity> entitiesToRemove = new ArrayList<>();
				HibernateTranslatorEntity lastValidEntity = null;
				// First pass: identify what to keep and what to remove
				for (final HibernateTranslatorEntity entity : result) {
					if ((entity.getTranslation() == null) || entity.getTranslation().isEmpty()) {
						entitiesToRemove.add(entity);
					} else {
						if (lastValidEntity != null) {
							entitiesToRemove.add(lastValidEntity);
						}
						lastValidEntity = entity;
					}
				}
				for (final HibernateTranslatorEntity entityToRemove : entitiesToRemove) {
					hibernateSession.remove(entityToRemove);
				}
				if (lastValidEntity != null) {
					lastValidEntity.LastUsed = Translator.quickTimestamp.timestamp();
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
		new HibernateTranslatorEntity().setModelCode(modelCode)
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
		HibernateUtil.createSessionAndExecuteTransactionWithRetry(hibernateSession -> {
			final HibernateTranslatorEntity entity = hibernateSession.get(HibernateTranslatorEntity.class, id);
			if (entity != null) {
				hibernateSession.remove(entity);
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
	 * @return The result of the operation (currently unused)
	 */
	@Override
	public void delete(final String modelCode, final Object input) {
		HibernateUtil.createSessionAndExecuteTransactionWithRetry(hibernateSession -> {
			if (input != null) {
				final boolean isString = input instanceof String;
				final CriteriaBuilder cb = hibernateSession.getCriteriaBuilder();
				final CriteriaQuery<HibernateTranslatorEntity> cq = cb.createQuery(HibernateTranslatorEntity.class);
				final Root<HibernateTranslatorEntity> root = cq.from(HibernateTranslatorEntity.class);
				cq.select(root)
						.where(
								cb.equal(root.get(Column_modleCode), modelCode),
								cb.equal(root.get(Column_StringIN), (isString ? (String) input : input.toString())));
				final List<HibernateTranslatorEntity> result = hibernateSession.createQuery(cq).getResultList();
				for (final HibernateTranslatorEntity t : result) {
					hibernateSession.remove(t);
				}
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
		HibernateUtil.createSessionAndExecuteTransactionWithRetry(hibernateSession -> {
			final String hql = "DELETE FROM " + HibernateTranslatorEntity.class.getSimpleName() //$NON-NLS-1$
					+ " e " + "WHERE e." + Column_id + " NOT IN (" + "   SELECT MAX(e2." + Column_id + ") " + "   FROM " + HibernateTranslatorEntity.class //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
							.getSimpleName() + " e2 " + "   GROUP BY e2." + Column_modleCode + ", e2." + Column_StringIN + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			final MutationQuery query = hibernateSession.createMutationQuery(hql);
			final int deletedCount = query.executeUpdate();
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
		HibernateUtil.createSessionAndExecuteTransactionWithRetry(hibernateSession -> {
			final String hql = "DELETE FROM " + HibernateTranslatorEntity.class.getSimpleName() //$NON-NLS-1$
					+ " e WHERE e." + Column_LastUsed + " < :cutoff"; //$NON-NLS-1$ //$NON-NLS-2$
			final MutationQuery query = hibernateSession.createMutationQuery(hql);
			query.setParameter("cutoff", removeTime); //$NON-NLS-1$
			final int deletedCount = query.executeUpdate();
			T_Log.log("Deleted Old Translations: " + deletedCount); //$NON-NLS-1$
			return null;
		}, 3);
	}

	/**
	 * Deletes all translations from the database.
	 */
	@Override
	public void deleteAllTranslations() {
		HibernateUtil.createSessionAndExecuteTransactionWithRetry(hibernateSession -> {
			final String hql = "DELETE FROM " + HibernateTranslatorEntity.class.getSimpleName(); //$NON-NLS-1$
			final MutationQuery query = hibernateSession.createMutationQuery(hql);
			final int deletedCount = query.executeUpdate();
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
		return HibernateUtil.createSessionAndExecuteTransactionWithRetry(hibernateSession -> {
			final String entityName = HibernateTranslatorEntity.class.getSimpleName();
			final String hql = "SELECT COUNT(e) FROM " + entityName + " e WHERE e." + Column_id + " = :" + Column_id; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			final Query<Long> query = hibernateSession.createQuery(hql, Long.class);
			query.setParameter(Column_id, id);
			final Long count = query.uniqueResult();
			return Boolean.valueOf(count.longValue() > 0);
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
				final int randomIndex = HibernateUtil.secureRandom.nextInt(CHARACTERS.length());
				final char randomChar = CHARACTERS.charAt(randomIndex);
				sb.append(randomChar);
			}
			newId = sb.toString();
		} while (this.idExists(newId));
		return newId;
	}

	/**
	 * Utility class for managing Hibernate sessions and transactions and ids.
	 */
	private static class HibernateUtil {
		private static class HibernateConfigMapper {
			private record DbDetails(String dialect, String driver) {
			}

			private static final Map<Database, DbDetails> MAPPINGS = new HashMap<>();
			static {
				MAPPINGS.put(
						Database.MARIADB,
						new DbDetails(
								"org.hibernate.dialect.MariaDBDialect", //$NON-NLS-1$
								"org.mariadb.jdbc.Driver")); //$NON-NLS-1$
				MAPPINGS.put(
						Database.MYSQL,
						new DbDetails(
								"org.hibernate.dialect.MySQLDialect", //$NON-NLS-1$
								"com.mysql.cj.jdbc.Driver")); //$NON-NLS-1$
				MAPPINGS.put(
						Database.POSTGRESQL,
						new DbDetails(
								"org.hibernate.dialect.PostgreSQLDialect", //$NON-NLS-1$
								"org.postgresql.Driver")); //$NON-NLS-1$
				MAPPINGS.put(
						Database.H2,
						new DbDetails(
								"org.hibernate.dialect.H2Dialect", //$NON-NLS-1$
								"org.h2.Driver")); //$NON-NLS-1$
			}

			private static DbDetails getDetails() {
				return MAPPINGS.get(Translator.database);
			}
		}

		// Default config from jar resource paths
		private static String cfg = ToConfigFiles.libhibernate;
		// Session factory used to create sessions
		private static SessionFactory sessionFactory = null;
		// Secure random instance for generating unique IDs
		private static final SecureRandom secureRandom = new SecureRandom();

		/**
		 * Sets the Hibernate session factory.
		 *
		 */
		private static synchronized void setSessionFactory(final SessionFactory newSessionFactory) {
			if (sessionFactory == null) {
				sessionFactory = newSessionFactory;
			}
		}

		/**
		 * Initializes the Hibernate session factory.
		 *
		 * @param configPathOrString Path to config file
		 */
		private static synchronized void initSessionFactory(final String configPathOrString) {
			if (sessionFactory == null) {
				T_Log.log("Initializing Hibernate Session Factory"); //$NON-NLS-1$
				try {
					final Configuration configuration = new Configuration();
					// 1. Detect config file and apply to configuration
					if (configPathOrString != null) {
						cfg = configPathOrString;
						configuration.configure(Thread.currentThread().getContextClassLoader().getResource(cfg));
					} else {
						configuration.configure(cfg);
					}
					// 2. Get the correct Dialect and Driver strings for that DB
					final HibernateConfigMapper.DbDetails details = HibernateConfigMapper.getDetails();
					// 3. Programmatically configure Hibernate
					configuration.setProperty("hibernate.dialect", details.dialect()); //$NON-NLS-1$
					configuration.setProperty("hibernate.connection.driver_class", details.driver()); //$NON-NLS-1$
					configuration.addAnnotatedClass(HibernateTranslatorEntity.class);
					sessionFactory = configuration.buildSessionFactory();
					T_Log.log("Initialized successfully."); //$NON-NLS-1$
					return;
				} catch (ExceptionInInitializerError | Exception e) {
					T_Log.log(
							"Error initializing Hibernate Session Factory: " + HibernateTranslatorEntity.class.getSimpleName(), //$NON-NLS-1$
							e);
					return;
				}
			}
		}

		/**
		 * Drops a table from the database.
		 *
		 * @param hibernateSession The current Hibernate session
		 */
		private static synchronized void dropTable(final Session hibernateSession) {
			final String sql = "DROP TABLE IF EXISTS " + HibernateTranslatorEntity.class.getSimpleName(); //$NON-NLS-1$
			hibernateSession.createMutationQuery(sql).executeUpdate();
			T_Log.log("Table Deleted"); //$NON-NLS-1$
		}

		/**
		 * Shuts down all Hibernate session factories and deregisters the JDBC driver.
		 */
		private static synchronized void shutdown() {
			try {
				sessionFactory.close();
				T_Log.log("SessionFactories Shutdown"); //$NON-NLS-1$
			} catch (final Exception e) {
				T_Log.log("Error During SessionFactorys Shutdown: ", e); //$NON-NLS-1$
			}
			try {
				boolean driverFound = false;
				Driver targetDriver = null;
				final Enumeration<Driver> drivers = DriverManager.getDrivers();
				while (drivers.hasMoreElements()) {
					final Driver d = drivers.nextElement();
					if (d.acceptsURL("jdbc:mariadb://")) { //$NON-NLS-1$
						driverFound = true;
						targetDriver = d;
						break;
					}
				}
				if (driverFound && (targetDriver != null)) {
					final ClassLoader cl = Thread.currentThread().getContextClassLoader();
					if (targetDriver.getClass().getClassLoader() == cl) {
						DriverManager.deregisterDriver(targetDriver);
						T_Log.log("Unregistered JDBC Driver: " + targetDriver); //$NON-NLS-1$
					} else {
						T_Log.log("Driver Exists But Not Deregistered (Class Loader Mismatch)"); //$NON-NLS-1$
					}
				} else {
					T_Log.log("MariaDB Driver Not Found – Nothing To Deregister"); //$NON-NLS-1$
				}
			} catch (final Exception e) {
				T_Log.log("Unexpected Error While Checking/Deregistering MariaDB Driver", e); //$NON-NLS-1$
			}
		}

		/**
		 * Executes an action within a Hibernate session with retry logic for optimistic
		 * locking.
		 *
		 * @param action     The function to execute
		 * @param maxRetries Maximum number of retries
		 * @param <T>        Return type of the action
		 * @return Result of the action
		 * @throws IllegalStateException if all retries fail
		 */
		private static <T> T createSessionAndExecuteTransactionWithRetry(final Function<Session, T> action, final int maxRetries) {
			int retryCount = 0;
			while (retryCount <= maxRetries) {
				Session hibernateSession = null;
				Transaction transaction = null;
				try {
					hibernateSession = sessionFactory.openSession();
					transaction = hibernateSession.beginTransaction();
					final T result = action.apply(hibernateSession);
					transaction.commit();
					return result;
				} catch (final Exception e) {
					if (!(e instanceof StaleObjectStateException) && !(e instanceof LockAcquisitionException)) {
						if ((transaction != null) && transaction.isActive()) {
							try {
								transaction.rollback();
							} catch (final Exception rollbackException) {
								T_Log.log("Rollback failed: ", rollbackException); //$NON-NLS-1$
							}
						}
						if (e instanceof HibernateException) {
							T_Log.log(
									"Hibernate Error During Transaction: ", //$NON-NLS-1$
									e);
						} else {
							T_Log.log(
									"Unexpected Error During Transaction: ", //$NON-NLS-1$
									e);
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
					if ((transaction != null) && transaction.isActive()) {
						try {
							transaction.rollback();
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
					if (hibernateSession != null) {
						hibernateSession.close();
					}
				}
			}
			throw new IllegalStateException(
					"Transaction Failed After Retries Attempted"); //$NON-NLS-1$
		}
	}
}